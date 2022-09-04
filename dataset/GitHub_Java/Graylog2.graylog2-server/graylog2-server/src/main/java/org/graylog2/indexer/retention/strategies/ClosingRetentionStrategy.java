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
package org.graylog2.indexer.retention.strategies;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import org.graylog2.audit.AuditActor;
import org.graylog2.audit.AuditEventSender;
import org.graylog2.indexer.indices.Indices;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.plugin.indexer.retention.RetentionStrategyConfig;
import org.graylog2.plugin.system.NodeId;
import org.graylog2.shared.system.activities.ActivityWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.graylog2.audit.AuditEventTypes.ES_INDEX_RETENTION_CLOSE;

public class ClosingRetentionStrategy extends AbstractIndexCountBasedRetentionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(ClosingRetentionStrategy.class);

    private final Indices indices;
    private final NodeId nodeId;
    private final ClusterConfigService clusterConfigService;
    private final AuditEventSender auditEventSender;

    @Inject
    public ClosingRetentionStrategy(Indices indices,
                                    ActivityWriter activityWriter,
                                    NodeId nodeId,
                                    ClusterConfigService clusterConfigService,
                                    AuditEventSender auditEventSender) {
        super(indices, activityWriter);
        this.indices = indices;
        this.nodeId = nodeId;
        this.clusterConfigService = clusterConfigService;
        this.auditEventSender = auditEventSender;
    }

    @Override
    protected Optional<Integer> getMaxNumberOfIndices() {
        // TODO 2.2: Retention strategy config is per write target, not global.
        final ClosingRetentionStrategyConfig config = clusterConfigService.get(ClosingRetentionStrategyConfig.class);

        if (config != null) {
            return Optional.of(config.maxNumberOfIndices());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void retain(String indexName) {
        final Stopwatch sw = Stopwatch.createStarted();

        indices.close(indexName);
        auditEventSender.success(AuditActor.system(nodeId), ES_INDEX_RETENTION_CLOSE, ImmutableMap.of(
                "index_name", indexName,
                "retention_strategy", this.getClass().getCanonicalName()
        ));

        LOG.info("Finished index retention strategy [close] for index <{}> in {}ms.", indexName,
                sw.stop().elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public Class<? extends RetentionStrategyConfig> configurationClass() {
        return ClosingRetentionStrategyConfig.class;
    }

    @Override
    public RetentionStrategyConfig defaultConfiguration() {
        return ClosingRetentionStrategyConfig.createDefault();
    }
}
