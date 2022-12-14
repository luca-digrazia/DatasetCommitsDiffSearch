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
package org.graylog2.inputs.transports;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.graylog2.plugin.collections.Pair;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.BooleanField;
import org.graylog2.plugin.inputs.MessageInput2;
import org.graylog2.plugin.inputs.transports.TransportFactory;
import org.graylog2.plugin.inputs.util.ConnectionCounter;
import org.graylog2.plugin.inputs.util.ThroughputCounter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import java.util.List;
import java.util.concurrent.Executor;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;

public class HttpTransport extends AbstractTcpTransport {
    private static final Logger log = LoggerFactory.getLogger(HttpTransport.class);

    public static final String CK_ENABLE_CORS = "enable_cors";

    private final boolean enableCors;

    @AssistedInject
    public HttpTransport(@Assisted Configuration configuration,
                         @Named("bossPool") Executor bossPool,
                         @Named("cached") Provider<Executor> workerPoolProvider,
                         ThroughputCounter throughputCounter,
                         ConnectionCounter connectionCounter,
                         MetricRegistry metricRegistry,
                         ObjectMapper mapper) {
        super(configuration,
              throughputCounter,
              metricRegistry,
              mapper,
              bossPool,
              workerPoolProvider,
              connectionCounter);

        enableCors = configuration.getBoolean(CK_ENABLE_CORS);
    }

    @Override
    protected List<Pair<String, ? extends ChannelHandler>> getBaseChannelHandlers(MessageInput2 input) {
        final List<Pair<String, ? extends ChannelHandler>> baseChannelHandlers = super.getBaseChannelHandlers(input);

        baseChannelHandlers.add(Pair.of("decoder", new HttpRequestDecoder()));
        baseChannelHandlers.add(Pair.of("encoder", new HttpRequestEncoder()));
        baseChannelHandlers.add(Pair.of("decompressor", new HttpContentDecompressor()));

        return baseChannelHandlers;
    }

    @Override
    protected List<Pair<String, ? extends ChannelHandler>> getFinalChannelHandlers(MessageInput2 input) {
        final List<Pair<String, ? extends ChannelHandler>> handlers = Lists.newArrayList();

        handlers.add(Pair.of("http-handler", new Handler(enableCors)));

        handlers.addAll(super.getFinalChannelHandlers(input));
        return handlers;
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        final ConfigurationRequest r = super.getRequestedConfiguration();
        r.addField(new BooleanField(CK_ENABLE_CORS,
                                    "Enable CORS",
                                    true,
                                    "Input sends CORS headers to satisfy browser security policies"));
        return r;
    }

    public interface Factory extends TransportFactory<HttpTransport> {
        @Override
        HttpTransport create(Configuration configuration);
    }

    private class Handler extends SimpleChannelHandler {

        private final boolean enableCors;

        public Handler(boolean enableCors) {
            this.enableCors = enableCors;
        }

        @Override
        public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
            final Channel channel = e.getChannel();
            final HttpRequest request = (HttpRequest) e.getMessage();
            final boolean keepAlive = isKeepAlive(request);
            final HttpVersion httpRequestVersion = request.getProtocolVersion();
            final String origin = request.headers().get(Names.ORIGIN);

            // to allow for future changes, let's be at least a little strict in what we accept here.
            if (request.getMethod() != HttpMethod.POST) {
                writeResponse(channel, keepAlive, httpRequestVersion, METHOD_NOT_ALLOWED, origin);
                return;
            }

            final ChannelBuffer buffer = request.getContent();

            if ("/gelf".equals(request.getUri())) {
                // send on to raw message handler
                Channels.fireMessageReceived(ctx, buffer);
            } else {
                writeResponse(channel, keepAlive, httpRequestVersion, NOT_FOUND, origin);
                return;
            }
            writeResponse(channel, keepAlive, httpRequestVersion, ACCEPTED, origin);
        }

        private void writeResponse(Channel channel,
                                   boolean keepAlive,
                                   HttpVersion httpRequestVersion,
                                   HttpResponseStatus status,
                                   String origin) {
            final HttpResponse response =
                    new DefaultHttpResponse(httpRequestVersion, status);

            response.headers().set(Names.CONTENT_LENGTH, 0);
            response.headers().set(Names.CONNECTION,
                                   keepAlive ? Values.KEEP_ALIVE : Values.CLOSE);

            if (enableCors) {
                if (origin != null && !origin.isEmpty()) {
                    response.headers().set(Names.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                    response.headers().set(Names.ACCESS_CONTROL_ALLOW_CREDENTIALS, true);
                    response.headers().set(Names.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization");
                }
            }

            final ChannelFuture channelFuture = channel.write(response);
            if (!keepAlive) {
                channelFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

}
