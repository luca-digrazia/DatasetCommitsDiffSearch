/**
 * The MIT License
 * Copyright (c) 2012 TORCH GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graylog2.plugin.inputs.transports;

import com.codahale.metrics.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.graylog2.plugin.collections.Pair;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.inputs.MessageInput2;
import org.graylog2.plugin.inputs.MisfireException;
import org.graylog2.plugin.inputs.codecs.CodecAggregator;
import org.graylog2.plugin.inputs.util.PacketInformationDumper;
import org.graylog2.plugin.inputs.util.ThroughputCounter;
import org.graylog2.plugin.journal.RawMessage;
import org.jboss.netty.bootstrap.Bootstrap;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static com.codahale.metrics.MetricRegistry.name;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

public abstract class NettyTransport implements Transport {
    public static final String CK_BIND_ADDRESS = "bind_address";
    public static final String CK_PORT = "port";
    public static final String CK_RECV_BUFFER_SIZE = "recv_buffer_size";

    private static final Logger log = LoggerFactory.getLogger(NettyTransport.class);

    public static int DEFAULT_RECV_BUFFER_SIZE = 1024 * 1024;
    protected final MetricRegistry metricRegistry;

    private final InetSocketAddress socketAddress;
    protected final ThroughputCounter throughputCounter;
    private final ObjectMapper mapper;
    private final long recvBufferSize;

    @Nullable
    private CodecAggregator aggregator;

    private Bootstrap bootstrap;
    private Channel acceptChannel;

    public NettyTransport(Configuration configuration,
                          ThroughputCounter throughputCounter,
                          MetricRegistry metricRegistry,
                          ObjectMapper mapper) {
        this.throughputCounter = throughputCounter;
        this.mapper = mapper;

        if (configuration.stringIsSet(CK_BIND_ADDRESS) && configuration.intIsSet(CK_PORT)) {
            this.socketAddress = new InetSocketAddress(
                    configuration.getString(CK_BIND_ADDRESS),
                    (int) configuration.getInt(CK_PORT)
            );
        } else {
            this.socketAddress = null;
        }
        this.recvBufferSize = configuration.intIsSet(CK_RECV_BUFFER_SIZE)
                ? configuration.getInt(CK_RECV_BUFFER_SIZE)
                : DEFAULT_RECV_BUFFER_SIZE;
        this.metricRegistry = metricRegistry;
    }

    private ChannelPipelineFactory getPipelineFactory(final List<Pair<String, ? extends ChannelHandler>> handlerList) {
        return new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline p = Channels.pipeline();
                for (Pair<String, ? extends ChannelHandler> pair : handlerList) {
                    p.addLast(pair.first(), pair.second());
                }
                return p;
            }
        };
    }

    @Override
    public void setMessageAggregator(@Nullable CodecAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public void launch(final MessageInput2 input) throws MisfireException {
        final List<Pair<String, ? extends ChannelHandler>> handlerList = getBaseChannelHandlers(input);
        final List<Pair<String, ? extends ChannelHandler>> finalChannelHandlers = getFinalChannelHandlers(input);

        handlerList.addAll(finalChannelHandlers);

        try {
            bootstrap = getBootstrap();

            bootstrap.setPipelineFactory(getPipelineFactory(handlerList));

            // sigh, bindable bootstraps do not share a common interface
            if (bootstrap instanceof ConnectionlessBootstrap) {
                acceptChannel = ((ConnectionlessBootstrap) bootstrap).bind(socketAddress);
            } else if (bootstrap instanceof ServerBootstrap) {
                acceptChannel = ((ServerBootstrap) bootstrap).bind(socketAddress);
            } else {
                log.error("Unknown netty bootstrap class returned: {}. Cannot safely bind.", bootstrap);
                throw new IllegalStateException("Unknown netty bootstrap class returned: " + bootstrap + ". Cannot safely bind.");
            }
        } catch (Exception e) {
            throw new MisfireException(e);
        }
    }

    @Override
    public void stop() {
        if (acceptChannel != null && acceptChannel.isOpen()) {
            acceptChannel.close();
        }
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        final ConfigurationRequest r = new ConfigurationRequest();

        r.addField(ConfigurationRequest.Templates.bindAddress(CK_BIND_ADDRESS));
        r.addField(ConfigurationRequest.Templates.portNumber(CK_PORT, 5555));
        r.addField(ConfigurationRequest.Templates.recvBufferSize(CK_RECV_BUFFER_SIZE, 1024 * 1024));

        return r;
    }

    /**
     * Construct a {@link org.jboss.netty.bootstrap.ServerBootstrap} to use with this transport.
     * <p/>
     * Set all the options on it you need to have, but do not set a {@link org.jboss.netty.channel.ChannelPipelineFactory}, it will be replaced with the
     * augmented list of handlers returned by {@link #getBaseChannelHandlers(MessageInput2)}
     *
     * @return a configured ServerBootstrap for this transport
     */
    protected abstract Bootstrap getBootstrap();

    /**
     * Subclasses can override this to add additional ChannelHandlers to the pipeline to support additional features.
     * <p/>
     * Some common use cases are to add SSL/TLS, connection counters or throttling traffic shapers.
     *
     * @return the list of initial channelhandlers to add to the {@link org.jboss.netty.channel.ChannelPipelineFactory}
     * @param input
     */
    protected List<Pair<String, ? extends ChannelHandler>> getBaseChannelHandlers(MessageInput2 input) {
        List<Pair<String, ? extends ChannelHandler>> handlerList = Lists.newArrayList();

        handlerList.add(Pair.of("packet-meta-dumper", new PacketInformationDumper(input)));
        handlerList.add(Pair.of("traffic-counter", throughputCounter));

        return handlerList;
    }

    /**
     * Subclasses can override this to modify the {@link org.jboss.netty.channel.ChannelHandler channel handlers} at the end of the pipeline.
     * <p/>
     * The default handlers in this group are the aggregation handler (e.g. for chunked GELF via UDP), which can be missing, and the {@link NettyTransport.RawMessageHandler}.
     * <p/>
     * Usually this should not be necessary, only modify them if you have a codec that cannot create a {@link org.graylog2.plugin.journal.RawMessage} for
     * incoming messages at the end of the pipeline.
     * <p/>
     * One valid use case would be to insert debug handlers in the middle of the list, though.
     *
     * @return the list of channel handlers at the end of the pipeline
     * @param input
     */
    protected List<Pair<String, ? extends ChannelHandler>> getFinalChannelHandlers(MessageInput2 input) {
        List<Pair<String, ? extends ChannelHandler>> handlerList = Lists.newArrayList();

        if (aggregator != null) {
            log.debug("Adding codec aggregator {} to channel pipeline", aggregator);
            handlerList.add(Pair.of("codec-aggregator", new MessageAggregationHandler(input, aggregator)));
        }

        handlerList.add(Pair.of("rawmessage-handler", new RawMessageHandler(input)));
        return handlerList;
    }

    protected long getRecvBufferSize() {
        return recvBufferSize;
    }

    @Override
    public void setupMetrics(MessageInput2 input) {
        // Register throughput counter gauges.
        for(Map.Entry<String,Gauge<Long>> gauge : throughputCounter.gauges().entrySet()) {
            metricRegistry.register(name(input.getUniqueReadableId(), gauge.getKey()), gauge.getValue());
        }
    }

    private class MessageAggregationHandler extends SimpleChannelHandler {
        private final MessageInput2 input;
        private final CodecAggregator aggregator;
        private final Timer aggregationTimer;
        private final Meter invalidChunksMeter;

        public MessageAggregationHandler(MessageInput2 input, CodecAggregator aggregator) {
            this.input = input;
            this.aggregator = aggregator;
            aggregationTimer = metricRegistry.timer(name(input.getUniqueReadableId(), "aggregationTime"));
            invalidChunksMeter = metricRegistry.meter(name(input.getUniqueReadableId(), "invalidMessages"));
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            final Object message = e.getMessage();

            if (message instanceof ChannelBuffer) {
                final ChannelBuffer buf = (ChannelBuffer) message;
                final CodecAggregator.Result result;
                try (Timer.Context ignored = aggregationTimer.time()) {
                    result = aggregator.addChunk(buf);
                }
                final ChannelBuffer completeMessage = result.getMessage();
                if (completeMessage != null) {
                    log.debug("Message aggregation completion, forwarding {}", completeMessage);
                    fireMessageReceived(ctx, completeMessage);
                } else if(result.isValid()) {
                    log.debug("More chunks necessary to complete this message");
                } else {
                    invalidChunksMeter.mark();
                    log.debug("Message chunk was not valid and discarded.");
                }
            } else {
                log.debug("Could not handle netty message {}, sending further upstream.", e);
                fireMessageReceived(ctx, message);
            }
        }
    }

    private class RawMessageHandler extends SimpleChannelHandler {
        private final MessageInput2 input;

        public RawMessageHandler(MessageInput2 input) {
            this.input = input;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            final Object msg = e.getMessage();

            if (!(msg instanceof ChannelBuffer)) {
                log.error(
                        "Invalid message type received from transport pipeline. Should be ChannelBuffer but was {}. Discarding message.",
                        msg.getClass());
                return;

            }
            final ChannelBuffer buffer = (ChannelBuffer) msg;
            final byte[] payload = new byte[buffer.readableBytes()];
            buffer.toByteBuffer().get(payload, buffer.readerIndex(), buffer.readableBytes());

            final RawMessage raw = new RawMessage(input.getCodec().getName(), input.getId(),
                                                  (InetSocketAddress) e.getRemoteAddress(), payload);
            input.processRawMessage(raw);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            log.debug("Could not handle message, closing connection: {}", e);

            if (ctx.getChannel() != null && !(ctx.getChannel() instanceof DatagramChannel)) {
                ctx.getChannel().close();
            }
        }
    }
}
