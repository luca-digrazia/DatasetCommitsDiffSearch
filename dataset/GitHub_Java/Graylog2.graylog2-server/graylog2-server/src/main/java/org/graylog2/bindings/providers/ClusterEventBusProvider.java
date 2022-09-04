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
package org.graylog2.bindings.providers;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.graylog2.plugin.BaseConfiguration;
import org.graylog2.shared.events.DeadEventLoggingListener;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.codahale.metrics.MetricRegistry.name;

public class ClusterEventBusProvider implements Provider<EventBus> {
    private final int asyncEventbusProcessors;
    private final MetricRegistry metricRegistry;

    @Inject
    public ClusterEventBusProvider(@Named("async_eventbus_processors") final int asyncEventbusProcessors,
                                   final MetricRegistry metricRegistry) {
        this.asyncEventbusProcessors = asyncEventbusProcessors;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public EventBus get() {
        final EventBus eventBus = new AsyncEventBus("cluster-eventbus", executorService(asyncEventbusProcessors));
        eventBus.register(new DeadEventLoggingListener());

        return eventBus;
    }

    private ExecutorService executorService(int nThreads) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("cluster-eventbus-handler-%d").build();
        return new InstrumentedExecutorService(
                Executors.newFixedThreadPool(nThreads, threadFactory),
                metricRegistry,
                name("cluster-eventbus", "executor-service"));
    }
}
