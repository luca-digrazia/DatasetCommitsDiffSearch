package com.codahale.dropwizard.server;

import com.codahale.dropwizard.jersey.setup.JerseyEnvironment;
import com.codahale.dropwizard.jetty.ConnectorFactory;
import com.codahale.dropwizard.jetty.HttpConnectorFactory;
import com.codahale.dropwizard.jetty.RoutingHandler;
import com.codahale.dropwizard.lifecycle.setup.LifecycleEnvironment;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.ThreadPool;

import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * An object representation of the {@code server} section of the YAML configuration file.
 */
@JsonTypeName("default")
public class DefaultServerFactory extends AbstractServerFactory {

    @Valid
    @NotNull
    private List<ConnectorFactory> applicationConnectors =
            Lists.<ConnectorFactory>newArrayList(HttpConnectorFactory.application());

    @Valid
    @NotNull
    private List<ConnectorFactory> adminConnectors =
            Lists.<ConnectorFactory>newArrayList(HttpConnectorFactory.admin());

    @JsonProperty
    public List<ConnectorFactory> getApplicationConnectors() {
        return applicationConnectors;
    }

    @JsonProperty
    public void setApplicationConnector(List<ConnectorFactory> connectors) {
        this.applicationConnectors = connectors;
    }

    @JsonProperty
    public List<ConnectorFactory> getAdminConnectors() {
        return adminConnectors;
    }

    @JsonProperty
    public void setAdminConnectors(List<ConnectorFactory> connectors) {
        this.adminConnectors = connectors;
    }

    @Override
    public Server build(String name,
                        MetricRegistry metricRegistry,
                        HealthCheckRegistry healthChecks,
                        LifecycleEnvironment lifecycle,
                        ServletContextHandler applicationContext,
                        ServletContainer jerseyContainer,
                        ServletContextHandler adminContext,
                        JerseyEnvironment jersey,
                        ObjectMapper objectMapper,
                        Validator validator) {
        final ThreadPool threadPool = createThreadPool(metricRegistry);
        final Server server = buildServer(lifecycle, threadPool);

        final ServletContextHandler applicationHandler = createExternalServlet(jersey,
                                                                               objectMapper,
                                                                               validator,
                                                                               applicationContext,
                                                                               jerseyContainer);
        final ServletContextHandler adminHandler = createInternalServlet(adminContext,
                                                                         metricRegistry,
                                                                         healthChecks);

        final List<Connector> builtApplicationConnectors = Lists.newArrayList();
        for (ConnectorFactory factory : applicationConnectors) {
            builtApplicationConnectors.add(factory.build(server, metricRegistry, "application"));
        }

        final List<Connector> builtAdminConnectors = Lists.newArrayList();
        for (ConnectorFactory factory : adminConnectors) {
            builtAdminConnectors.add(factory.build(server, metricRegistry, "admin"));
        }

        final Map<Connector, Handler> handlerMap = Maps.newLinkedHashMap();
        for (Connector connector : builtApplicationConnectors) {
            server.addConnector(connector);
            handlerMap.put(connector, applicationHandler);
        }
        for (Connector connector : builtAdminConnectors) {
            server.addConnector(connector);
            handlerMap.put(connector, adminHandler);
        }

        final Handler gzipHandler = getGzipHandlerFactory().wrapHandler(new RoutingHandler(
                handlerMap));
        final Handler handler = new InstrumentedHandler(metricRegistry, gzipHandler);

        if (getRequestLogFactory().isEnabled()) {
            final RequestLogHandler requestLogHandler = getRequestLogFactory().build(name);
            requestLogHandler.setHandler(handler);
            server.setHandler(requestLogHandler);
        } else {
            server.setHandler(handler);
        }

        return server;
    }


}
