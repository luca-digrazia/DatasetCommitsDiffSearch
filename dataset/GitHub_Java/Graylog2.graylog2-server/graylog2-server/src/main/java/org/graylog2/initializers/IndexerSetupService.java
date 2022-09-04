/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.initializers;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.graylog2.UI;
import org.graylog2.configuration.ElasticsearchConfiguration;
import org.graylog2.plugin.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.codahale.metrics.MetricRegistry.name;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Singleton
public class IndexerSetupService extends AbstractIdleService {
    private static final Logger LOG = LoggerFactory.getLogger(IndexerSetupService.class);
    private static final Version MINIMUM_ES_VERSION = Version.V_1_3_4;
    private static final Version MAXIMUM_ES_VERSION = Version.fromString("1.4.99");

    private final Node node;
    private final ElasticsearchConfiguration configuration;
    private final BufferSynchronizerService bufferSynchronizerService;
    private final AsyncHttpClient httpClient;

    @Inject
    public IndexerSetupService(final Node node,
                               final ElasticsearchConfiguration configuration,
                               final BufferSynchronizerService bufferSynchronizerService,
                               final AsyncHttpClient httpClient,
                               final MetricRegistry metricRegistry) {
        this.node = node;
        this.configuration = configuration;
        this.bufferSynchronizerService = bufferSynchronizerService;
        this.httpClient = httpClient;

        // Shutdown after the BufferSynchronizerServer has stopped to avoid shutting down ES too early.
        bufferSynchronizerService.addListener(new Listener() {
            @Override
            public void terminated(State from) {
                LOG.debug("Shutting down ES client after buffer synchronizer has terminated.");
                // Properly close ElasticSearch node.
                IndexerSetupService.this.node.close();
            }
        }, executorService(metricRegistry));
    }

    private ExecutorService executorService(MetricRegistry metricRegistry) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("indexer-setup-service-%d").build();
        return new InstrumentedExecutorService(
                Executors.newSingleThreadExecutor(threadFactory),
                metricRegistry,
                name(this.getClass(), "executor-service"));
    }

    @Override
    protected void startUp() throws Exception {
        Tools.silenceUncaughtExceptionsInThisThread();

        LOG.debug("Starting indexer");
        try {
            node.start();

            final Client client = node.client();
            try {
                /* try to determine the cluster health. if this times out we could not connect and try to determine if there's
                   anything listening at all. if that happens this usually has these reasons:
                    1. cluster.name is different
                    2. network.publish_host is not reachable
                    3. wrong address configured
                    4. multicast in use but broken in this environment
                   we handle a red cluster state differently because if we can get that result it means the cluster itself
                   is reachable, which is a completely different problem from not being able to join at all.
                 */
                final ClusterHealthRequest atLeastRed = new ClusterHealthRequest().waitForStatus(ClusterHealthStatus.RED);
                final ClusterHealthResponse health = client.admin()
                        .cluster()
                        .health(atLeastRed)
                        .actionGet(configuration.getClusterDiscoveryTimeout(), MILLISECONDS);
                // we don't get here if we couldn't join the cluster. just check for red cluster state
                if (ClusterHealthStatus.RED.equals(health.getStatus())) {
                    UI.exitHardWithWall(
                            "The Elasticsearch cluster state is RED which means shards are unassigned. " +
                                    "This usually indicates a crashed and corrupt cluster and needs to be investigated. Graylog will shut down.", "http://www.graylog2.org/resources/documentation/setup/elasticsearch");

                }
            } catch (ElasticsearchTimeoutException e) {
                final String hosts = node.settings().get("discovery.zen.ping.unicast.hosts");

                if (!isNullOrEmpty(hosts)) {
                    final Iterable<String> hostList = Splitter.on(',').omitEmptyStrings().trimResults().split(hosts);

                    // if no elasticsearch running
                    for (String host : hostList) {
                        // guess that Elasticsearch http is listening on port 9200
                        final HostAndPort hostAndPort = HostAndPort.fromString(host);
                        LOG.info("Checking Elasticsearch HTTP API at http://{}:9200/", hostAndPort.getHostText());

                        try {
                            // Try the HTTP API endpoint
                            final ListenableFuture<Response> future = httpClient.prepareGet("http://" + hostAndPort.getHostText() + ":9200/_nodes").execute();
                            final Response response = future.get();

                            final JsonNode resultTree = new ObjectMapper().readTree(response.getResponseBody());
                            final String clusterName = resultTree.get("cluster_name").textValue();
                            final JsonNode nodesList = resultTree.get("nodes");

                            final Iterator<String> nodes = nodesList.fieldNames();
                            while (nodes.hasNext()) {
                                final String id = nodes.next();
                                final Version clusterVersion = Version.fromString(nodesList.get(id).get("version").textValue());

                                if (!configuration.isDisableVersionCheck()) {
                                    checkClusterVersion(clusterVersion);
                                }
                            }

                            checkClusterName(clusterName);
                        } catch (IOException ioException) {
                            LOG.error("Could not connect to Elasticsearch.", ioException);
                        } catch (InterruptedException ignore) {
                        } catch (ExecutionException e1) {
                            // could not find any server on that address
                            LOG.error("Could not connect to Elasticsearch at http://" + hostAndPort.getHostText() + ":9200/, is it running?",
                                    e1.getCause());
                        }
                    }
                }

                UI.exitHardWithWall(
                        "Could not successfully connect to Elasticsearch, if you use multicast check that it is working in your network" +
                                " and that Elasticsearch is running properly and is reachable. Also check that the cluster.name setting is correct.",
                        "http://www.graylog2.org/resources/documentation/setup/elasticsearch");
            }
        } catch (Exception e) {
            bufferSynchronizerService.setIndexerUnavailable();
            throw e;
        }
    }

    private void checkClusterVersion(Version clusterVersion) {
        if (!clusterVersion.onOrAfter(MINIMUM_ES_VERSION) && !clusterVersion.onOrBefore(MAXIMUM_ES_VERSION)) {
            LOG.error("Elasticsearch node is of the wrong version {}, it must be between {} and {}! "
                            + "Please make sure you are running the correct version of Elasticsearch.",
                    clusterVersion, MINIMUM_ES_VERSION, MAXIMUM_ES_VERSION);
        }
    }

    private void checkClusterName(String clusterName) {
        if (!node.settings().get("cluster.name").equals(clusterName)) {
            LOG.error("Elasticsearch cluster name is different, Graylog2 uses `{}`, Elasticsearch cluster uses `{}`. "
                            + "Please check the `cluster.name` setting of both Graylog2 and Elasticsearch.",
                    node.settings().get("cluster.name"),
                    clusterName);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        // See constructor. Actual shutdown happens after BufferSynchronizerServer has stopped.
    }
}
