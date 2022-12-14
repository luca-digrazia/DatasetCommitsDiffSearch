package com.codahale.dropwizard.setup;

import com.codahale.dropwizard.jetty.JettyManaged;
import com.codahale.dropwizard.lifecycle.Managed;
import com.codahale.dropwizard.lifecycle.ServerLifecycleListener;
import com.google.common.collect.Lists;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class LifecycleEnvironment {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleEnvironment.class);

    private final List<LifeCycle> managedObjects;
    private final List<LifeCycle.Listener> lifecycleListeners;

    public LifecycleEnvironment() {
        this.managedObjects = Lists.newArrayList();
        this.lifecycleListeners = Lists.newArrayList();
    }

    /**
     * Adds the given {@link Managed} instance to the set of objects managed by the server's
     * lifecycle. When the server starts, {@code managed} will be started. When the server stops,
     * {@code managed} will be stopped.
     *
     * @param managed a managed object
     */
    public void manage(Managed managed) {
        managedObjects.add(new JettyManaged(checkNotNull(managed)));
    }

    /**
     * Adds the given Jetty {@link LifeCycle} instances to the server's lifecycle.
     *
     * @param managed a Jetty-managed object
     */
    public void manage(LifeCycle managed) {
        managedObjects.add(checkNotNull(managed));
    }

    public ExecutorServiceBuilder executorService(String nameFormat) {
        return new ExecutorServiceBuilder(this, nameFormat);
    }

    public ScheduledExecutorServiceBuilder scheduledExecutorService(String nameFormat) {
        return new ScheduledExecutorServiceBuilder(this, nameFormat);
    }

    public void addServerLifecycleListener(ServerLifecycleListener listener) {
        lifecycleListeners.add(new ServerListener(listener));
    }

    public void addLifeCycleListener(LifeCycle.Listener listener) {
        lifecycleListeners.add(listener);
    }

    public void attach(ContainerLifeCycle container) {
        for (LifeCycle object : managedObjects) {
            container.addBean(object);
        }
        container.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarting(LifeCycle event) {
                LOGGER.debug("managed objects = {}", managedObjects);
            }
        });
        for (LifeCycle.Listener listener : lifecycleListeners) {
            container.addLifeCycleListener(listener);
        }
    }

    private static class ServerListener extends AbstractLifeCycle.AbstractLifeCycleListener {
        private final ServerLifecycleListener listener;

        private ServerListener(ServerLifecycleListener listener) {
            this.listener = listener;
        }

        @Override
        public void lifeCycleStarted(LifeCycle event) {
            if (event instanceof Server) {
                listener.serverStarted((Server) event);
            }
        }
    }
}
