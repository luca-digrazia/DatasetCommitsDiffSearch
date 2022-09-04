/*
 * Copyright 2013-2014 TORCH GmbH
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
 *
 */

package org.graylog2.shared.buffers.processors;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.Inject;
import com.lmax.disruptor.EventHandler;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.shared.ProcessingHost;
import org.graylog2.shared.filters.FilterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.buffers.MessageEvent;
import org.graylog2.plugin.filters.MessageFilter;
import org.graylog2.shared.filters.FilterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public abstract class ProcessBufferProcessor implements EventHandler<MessageEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessBufferProcessor.class);

    protected AtomicInteger processBufferWatermark;
    private final Meter incomingMessages;
    private final Timer processTime;
    private final Meter outgoingMessages;

    protected final MetricRegistry metricRegistry;

    private final long ordinal;
    private final long numberOfConsumers;

    public ProcessBufferProcessor(MetricRegistry metricRegistry,
                                  AtomicInteger processBufferWatermark,
                                  final long ordinal,
                                  final long numberOfConsumers) {
        this.metricRegistry = metricRegistry;
        this.ordinal = ordinal;
        this.numberOfConsumers = numberOfConsumers;
        this.processBufferWatermark = processBufferWatermark;

        incomingMessages = metricRegistry.meter(name(ProcessBufferProcessor.class, "incomingMessages"));
        outgoingMessages = metricRegistry.meter(name(ProcessBufferProcessor.class, "outgoingMessages"));
        processTime = metricRegistry.timer(name(ProcessBufferProcessor.class, "processTime"));
    }

    @Override
    public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) throws Exception {
        // Because Trisha said so. (http://code.google.com/p/disruptor/wiki/FrequentlyAskedQuestions)
        if ((sequence % numberOfConsumers) != ordinal) {
            return;
        }

        processBufferWatermark.decrementAndGet();
        
        incomingMessages.mark();
        final Timer.Context tcx = processTime.time();

        Message msg = event.getMessage();

        LOG.debug("Starting to process message <{}>.", msg.getId());

        try {
            handleMessage(msg);
        } catch (Exception e) {
            LOG.warn("Unable to process message <{}>: {}", msg.getId(), e);
        } finally {
            outgoingMessages.mark();
            tcx.stop();
        }
    }

    protected abstract void handleMessage(Message msg);
}
