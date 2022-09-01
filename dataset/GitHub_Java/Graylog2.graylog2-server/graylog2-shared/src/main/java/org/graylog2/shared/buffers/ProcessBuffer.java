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

package org.graylog2.shared.buffers;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.graylog2.inputs.Cache;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.buffers.Buffer;
import org.graylog2.plugin.buffers.BufferOutOfCapacityException;
import org.graylog2.plugin.buffers.MessageEvent;
import org.graylog2.plugin.buffers.ProcessingDisabledException;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.shared.ServerStatus;
import org.graylog2.shared.buffers.processors.ProcessBufferProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class ProcessBuffer extends Buffer {
    public interface Factory {
        public ProcessBuffer create(Cache masterCache, AtomicInteger processBufferWatermark);
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProcessBuffer.class);

    public static String SOURCE_INPUT_ATTR_NAME;
    public static String SOURCE_NODE_ATTR_NAME;

    protected ExecutorService executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat("processbufferprocessor-%d")
                .build()
    );

    private final Cache masterCache;
    private final AtomicInteger processBufferWatermark;

    private final Meter incomingMessages;
    private final Meter rejectedMessages;
    private final Meter cachedMessages;

    private final MetricRegistry metricRegistry;
    private final ServerStatus serverStatus;

    @AssistedInject
    public ProcessBuffer(MetricRegistry metricRegistry,
                         ServerStatus serverStatus,
                         @Assisted Cache masterCache,
                         @Assisted AtomicInteger processBufferWatermark) {
        this.metricRegistry = metricRegistry;
        this.serverStatus = serverStatus;
        this.masterCache = masterCache;
        this.processBufferWatermark = processBufferWatermark;

        incomingMessages = metricRegistry.meter(name(ProcessBuffer.class, "incomingMessages"));
        rejectedMessages = metricRegistry.meter(name(ProcessBuffer.class, "rejectedMessages"));
        cachedMessages = metricRegistry.meter(name(ProcessBuffer.class, "cachedMessages"));

        if (serverStatus.hasCapability(ServerStatus.Capability.RADIO)) {
            SOURCE_INPUT_ATTR_NAME = "gl2_source_radio_input";
            SOURCE_NODE_ATTR_NAME = "gl2_source_radio";
        } else {
            SOURCE_INPUT_ATTR_NAME = "gl2_source_input";
            SOURCE_NODE_ATTR_NAME = "gl2_source_node";
        }
    }

    public Cache getMasterCache() {
        return masterCache;
    }

    public void initialize(ProcessBufferProcessor[] processors, int ringBufferSize, WaitStrategy waitStrategy, int processBufferProcessors) {
        Disruptor disruptor = new Disruptor<MessageEvent>(
                MessageEvent.EVENT_FACTORY,
                ringBufferSize,
                executor,
                ProducerType.MULTI,
                waitStrategy
        );
        
        LOG.info("Initialized ProcessBuffer with ring size <{}> "
                + "and wait strategy <{}>.", ringBufferSize,
                waitStrategy.getClass().getSimpleName());

        disruptor.handleEventsWith(processors);
        
        ringBuffer = disruptor.start();
    }
    
    @Override
    public void insertCached(Message message, MessageInput sourceInput) {
        message.setSourceInput(sourceInput);

        message.addField(SOURCE_INPUT_ATTR_NAME, sourceInput.getPersistId());
        message.addField(SOURCE_NODE_ATTR_NAME, serverStatus.getNodeId());

        if (!serverStatus.isProcessing()) {
            LOG.debug("Message processing is paused. Writing to cache.");
            cachedMessages.mark();
            masterCache.add(message);
            return;
        }

        if (!hasCapacity()) {
            LOG.debug("Out of capacity. Writing to cache.");
            cachedMessages.mark();
            masterCache.add(message);
            return;
        }

        insert(message);
    }

    @Override
    public void insertFailFast(Message message, MessageInput sourceInput) throws BufferOutOfCapacityException, ProcessingDisabledException {
        message.setSourceInput(sourceInput);

        message.addField(SOURCE_INPUT_ATTR_NAME, sourceInput.getId());
        message.addField(SOURCE_NODE_ATTR_NAME, serverStatus.getNodeId());

        if (!serverStatus.isProcessing()) {
            LOG.debug("Rejecting message, because message processing is paused.");
            throw new ProcessingDisabledException();
        }

        if (!hasCapacity()) {
            LOG.debug("Rejecting message, because I am full and caching was disabled by input. Raise my size or add more processors.");
            rejectedMessages.mark();
            throw new BufferOutOfCapacityException();
        }

        insert(message);
    }
    
    private void insert(Message message) {
        long sequence = ringBuffer.next();
        MessageEvent event = ringBuffer.get(sequence);
        event.setMessage(message);
        ringBuffer.publish(sequence);

        this.processBufferWatermark.incrementAndGet();
        incomingMessages.mark();
    }

}
