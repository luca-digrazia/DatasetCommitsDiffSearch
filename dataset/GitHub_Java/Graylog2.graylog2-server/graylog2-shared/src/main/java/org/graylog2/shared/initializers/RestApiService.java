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

package org.graylog2.shared.initializers;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.internal.util.$Nullable;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.glassfish.jersey.server.internal.scanning.PackageNamesScanner;
import org.graylog2.jersey.container.netty.NettyContainer;
import org.graylog2.jersey.container.netty.SecurityContextFactory;
import org.graylog2.shared.BaseConfiguration;
import org.graylog2.shared.metrics.jersey2.MetricsDynamicBinding;
import org.graylog2.plugin.rest.AnyExceptionClassMapper;
import org.graylog2.plugin.rest.JacksonPropertyExceptionMapper;
import org.graylog2.shared.rest.CORSFilter;
import org.graylog2.shared.rest.ObjectMapperProvider;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.DynamicFeature;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class RestApiService extends AbstractIdleService {
    private final Logger LOG = LoggerFactory.getLogger(RestApiService.class);
    private final BaseConfiguration configuration;
    private final SecurityContextFactory securityContextFactory;
    private final Set<Class<? extends DynamicFeature>> dynamicFeatures;
    private final Set<Class<? extends ContainerResponseFilter>> containerResponseFilters;
    private ServerBootstrap bootstrap;

    @Inject
    public RestApiService(BaseConfiguration configuration,
                          @$Nullable SecurityContextFactory securityContextFactory,
                          Set<Class<? extends DynamicFeature>> dynamicFeatures,
                          Set<Class<? extends ContainerResponseFilter>> containerResponseFilters) {
        this.configuration = configuration;
        this.securityContextFactory = securityContextFactory;
        this.dynamicFeatures = dynamicFeatures;
        this.containerResponseFilters = containerResponseFilters;
    }

    @Override
    protected void startUp() throws Exception {
        final ExecutorService bossExecutor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setNameFormat("restapi-boss-%d")
                        .build());

        final ExecutorService workerExecutor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setNameFormat("restapi-worker-%d")
                        .build());

        bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                bossExecutor,
                workerExecutor
        ));

        ResourceConfig rc = new ResourceConfig()
                .property(NettyContainer.PROPERTY_BASE_URI, configuration.getRestListenUri())
                .registerClasses(JacksonPropertyExceptionMapper.class,
                        AnyExceptionClassMapper.class);

        for (Class<? extends DynamicFeature> dynamicFeatureClass : dynamicFeatures)
            rc.registerClasses(dynamicFeatureClass);

        for (Class<? extends ContainerResponseFilter> responseFilter : containerResponseFilters)
            rc.registerClasses(responseFilter);

        rc.register(ObjectMapperProvider.class)
            .register(JacksonJsonProvider.class)
            .registerFinder(new PackageNamesScanner(new String[]{"org.graylog2.rest.resources",
                    "org.graylog2.radio.rest.resources", "org.graylog2.shared.rest.resources"}, true));

        if (configuration.isRestEnableGzip())
            EncodingFilter.enableFor(rc, GZipEncoder.class);

        if (configuration.isRestEnableCors()) {
            LOG.info("Enabling CORS for REST API");
            rc.register(CORSFilter.class);
        }

        /*rc = rc.registerFinder(new PackageNamesScanner(new String[]{"org.graylog2.rest.resources"}, true));*/

        final NettyContainer jerseyHandler = ContainerFactory.createContainer(NettyContainer.class, rc);
        if (securityContextFactory != null) {
            LOG.info("Adding security context factory: <{}>", securityContextFactory);
            jerseyHandler.setSecurityContextFactory(securityContextFactory);
        } else {
            LOG.info("Not adding security context factory.");
        }

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("chunks", new ChunkedWriteHandler());
                pipeline.addLast("jerseyHandler", jerseyHandler);
                return pipeline;
            }
        }) ;
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.bind(new InetSocketAddress(
                configuration.getRestListenUri().getHost(),
                configuration.getRestListenUri().getPort()
        ));

        LOG.info("Started REST API at <{}>", configuration.getRestListenUri());
    }

    @Override
    protected void shutDown() throws Exception {
        LOG.info("Shutting down REST API at <{}>", configuration.getRestListenUri());
        bootstrap.releaseExternalResources();
        bootstrap.shutdown();
    }
}
