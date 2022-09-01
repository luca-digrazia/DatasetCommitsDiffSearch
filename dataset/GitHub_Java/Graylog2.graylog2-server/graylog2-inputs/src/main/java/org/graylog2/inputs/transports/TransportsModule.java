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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import org.graylog2.plugin.inputs.transports.Transport;
import org.graylog2.plugin.inputs.transports.TransportFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TransportsModule extends AbstractModule {
    protected void configure() {

        install(new FactoryModuleBuilder().implement(Transport.class, UdpTransport.class).build(UdpTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, TcpTransport.class).build(TcpTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, HttpTransport.class).build(HttpTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, RandomMessageTransport.class).build(RandomMessageTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, KafkaTransport.class).build(KafkaTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, RadioKafkaTransport.class).build(RadioKafkaTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, AmqpTransport.class).build(AmqpTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, RadioAmqpTransport.class).build(RadioAmqpTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, HttpPollTransport.class).build(HttpPollTransport.Factory.class));
        install(new FactoryModuleBuilder().implement(Transport.class, LocalMetricsTransport.class).build(LocalMetricsTransport.Factory.class));

        // lol generics
        final MapBinder<String, TransportFactory<? extends Transport>> mapBinder =
                MapBinder.newMapBinder(binder(),
                                       TypeLiteral.get(String.class),
                                       new TypeLiteral<TransportFactory<? extends Transport>>() {
                                       });

        mapBinder.addBinding("udp").to(Key.get(UdpTransport.Factory.class));
        mapBinder.addBinding("tcp").to(Key.get(TcpTransport.Factory.class));
        mapBinder.addBinding("http").to(Key.get(HttpTransport.Factory.class));
        mapBinder.addBinding("randomhttp").to(Key.get(RandomMessageTransport.Factory.class));
        mapBinder.addBinding("kafka").to(Key.get(KafkaTransport.Factory.class));
        mapBinder.addBinding("radiokafka").to(Key.get(RadioKafkaTransport.Factory.class));
        mapBinder.addBinding("amqp").to(Key.get(AmqpTransport.Factory.class));
        mapBinder.addBinding("radioamqp").to(Key.get(RadioAmqpTransport.Factory.class));
        mapBinder.addBinding("httppoll").to(Key.get(HttpPollTransport.Factory.class));
        mapBinder.addBinding("localmetrics").to(Key.get(LocalMetricsTransport.Factory.class));

        bind(Executor.class)
                .annotatedWith(Names.named("bossPool"))
                .toInstance(Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                                                                  .setNameFormat("transport-boss-%d")
                                                                  .build()));

        bind(Executor.class)
                .annotatedWith(Names.named("cached"))
                .toProvider(new Provider<Executor>() {
                    @Override
                    public Executor get() {
                        return Executors.newCachedThreadPool();
                    }
                });
    }

}
