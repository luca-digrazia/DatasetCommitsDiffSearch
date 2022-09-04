/*
 * Copyright 2012-2014 TORCH GmbH
 *
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

package org.graylog2.buffers.processors;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.graylog2.buffers.OutputBuffer;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.filters.MessageFilter;
import org.graylog2.shared.buffers.processors.ProcessBufferProcessor;
import org.graylog2.shared.filters.FilterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class ServerProcessBufferProcessor extends ProcessBufferProcessor {
    public interface Factory {
        public ServerProcessBufferProcessor create(
                OutputBuffer outputBuffer,
                GraylogServer graylogServer,
                AtomicInteger processBufferWatermark,
                @Assisted("ordinal") final long ordinal,
                @Assisted("numberOfConsumers") final long numberOfConsumers
        );
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServerProcessBufferProcessor.class);
    private final GraylogServer graylogServer;
    private final OutputBuffer outputBuffer;
    private final Meter filteredOutMessages;
    private final FilterRegistry filterRegistry;


    @AssistedInject
    public ServerProcessBufferProcessor(MetricRegistry metricRegistry,
                                  FilterRegistry filterRegistry,
                                  @Assisted GraylogServer graylogServer,
                                  @Assisted AtomicInteger processBufferWatermark,
                                  @Assisted("ordinal") final long ordinal,
                                  @Assisted("numberOfConsumers") final long numberOfConsumers,
                                  @Assisted OutputBuffer outputBuffer) {
        super(metricRegistry, processBufferWatermark, ordinal, numberOfConsumers);
        this.filterRegistry = filterRegistry;
        this.graylogServer = graylogServer;
        this.outputBuffer = outputBuffer;
        this.filteredOutMessages = metricRegistry.meter(name(ProcessBufferProcessor.class, "filteredOutMessages"));
    }

    @Override
    protected void handleMessage(Message msg) {

        for (MessageFilter filter : filterRegistry.all()) {
            Timer timer = metricRegistry.timer(name(filter.getClass(), "executionTime"));
            final Timer.Context timerContext = timer.time();

            try {
                LOG.debug("Applying filter [{}] on message <{}>.", filter.getName(), msg.getId());

                if (filter.filter(msg, graylogServer)) {
                    LOG.debug("Filter [{}] marked message <{}> to be discarded. Dropping message.", filter.getName(), msg.getId());
                    filteredOutMessages.mark();
                    return;
                }
            } catch (Exception e) {
                LOG.error("Could not apply filter [" + filter.getName() +"] on message <" + msg.getId() +">: ", e);
            } finally {
                timerContext.stop();
            }
        }

        LOG.debug("Finished processing message. Writing to output buffer.");
        outputBuffer.insertCached(msg, null);
    }
}
