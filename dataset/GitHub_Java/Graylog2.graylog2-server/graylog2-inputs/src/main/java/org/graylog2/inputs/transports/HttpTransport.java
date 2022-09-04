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

import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.graylog2.plugin.ConfigClass;
import org.graylog2.plugin.FactoryClass;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.collections.Pair;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.BooleanField;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.transports.AbstractTcpTransport;
import org.graylog2.plugin.inputs.transports.Transport;
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

import static org.jboss.netty.channel.Channels.fireMessageReceived;
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
                         LocalMetricRegistry localRegistry) {
        super(configuration,
              throughputCounter,
              localRegistry,
              bossPool,
              workerPoolProvider,
              connectionCounter);

        enableCors = configuration.getBoolean(CK_ENABLE_CORS);
    }

    @Override
    protected List<Pair<String, ? extends ChannelHandler>> getBaseChannelHandlers(MessageInput input) {
        final List<Pair<String, ? extends ChannelHandler>> baseChannelHandlers = super.getBaseChannelHandlers(input);

        baseChannelHandlers.add(Pair.of("decoder", new HttpRequestDecoder()));
        baseChannelHandlers.add(Pair.of("encoder", new HttpResponseEncoder()));
        baseChannelHandlers.add(Pair.of("decompressor", new HttpContentDecompressor()));

        return baseChannelHandlers;
    }

    @Override
    protected List<Pair<String, ? extends ChannelHandler>> getFinalChannelHandlers(MessageInput input) {
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

    @FactoryClass
    public interface Factory extends Transport.Factory<HttpTransport> {
        @Override
        HttpTransport create(Configuration configuration);

        @Override
        Config getConfig();
    }

    @ConfigClass
    public static class Config extends AbstractTcpTransport.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest r = super.getRequestedConfiguration();
            r.addField(new BooleanField(CK_ENABLE_CORS,
                                        "Enable CORS",
                                        true,
                                        "Input sends CORS headers to satisfy browser security policies"));
            return r;
        }
    }
    public static class Handler extends SimpleChannelHandler {

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
                writeResponse(channel, keepAlive, httpRequestVersion, ACCEPTED, origin);
                fireMessageReceived(ctx, buffer);
            } else {
                writeResponse(channel, keepAlive, httpRequestVersion, NOT_FOUND, origin);
            }
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
