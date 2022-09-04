/**
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.bindings.providers;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.elasticsearch.common.settings.loader.YamlSettingsLoader;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.graylog2.configuration.ElasticsearchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

public class EsNodeProvider implements Provider<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(EsNodeProvider.class);

    private final ElasticsearchConfiguration configuration;

    @Inject
    public EsNodeProvider(ElasticsearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    @Singleton
    public Node get() {
        final NodeBuilder builder = nodeBuilder().client(true);
        Map<String, String> settings = readNodeSettings(configuration);

        builder.settings().put(settings);
        return builder.build();
    }

    public static Map<String, String> readNodeSettings(ElasticsearchConfiguration conf) {
        Map<String, String> settings = Maps.newHashMap();

        // Standard Configuration.
        settings.put("cluster.name", conf.getClusterName());

        settings.put("node.name", conf.getNodeName());
        settings.put("node.master", Boolean.toString(conf.isMasterNode()));
        settings.put("node.data", Boolean.toString(conf.isDataNode()));

        settings.put("action.auto_create_index", Boolean.toString(false));

        settings.put("http.enabled", Boolean.toString(conf.isHttpEnabled()));
        settings.put("transport.tcp.port", String.valueOf(conf.getTransportTcpPort()));

        settings.put("discovery.initial_state_timeout", conf.getInitialStateTimeout());
        settings.put("discovery.zen.ping.multicast.enabled", Boolean.toString(conf.isMulticastDiscovery()));

        if (conf.getUnicastHosts() != null && !conf.getUnicastHosts().isEmpty()) {
            final ImmutableList.Builder<String> trimmedHosts = ImmutableList.builder();
            for (String host : conf.getUnicastHosts()) {
                trimmedHosts.add(host.trim());
            }
            settings.put("discovery.zen.ping.unicast.hosts", Joiner.on(",").join(trimmedHosts.build()));
        }

        if (!isNullOrEmpty(conf.getNetworkHost())) {
            settings.put("network.host", conf.getNetworkHost());
        }
        if (!isNullOrEmpty(conf.getNetworkBindHost())) {
            settings.put("network.bind_host", conf.getNetworkBindHost());
        }
        if (!isNullOrEmpty(conf.getNetworkPublishHost())) {
            settings.put("network.publish_host", conf.getNetworkPublishHost());
        }

        // Overwrite from a custom ElasticSearch config file.
        final File esConfigFile = conf.getConfigFile();
        if (esConfigFile != null) {
            try {
                final byte[] esSettings = Files.readAllBytes(esConfigFile.toPath());
                settings.putAll(new YamlSettingsLoader().load(esSettings));
            } catch (IOException e) {
                LOG.warn("Cannot read Elasticsearch configuration.");
            }
        }

        return settings;
    }
}
