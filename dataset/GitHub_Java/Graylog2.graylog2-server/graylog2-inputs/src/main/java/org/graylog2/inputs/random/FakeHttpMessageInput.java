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
package org.graylog2.inputs.random;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.graylog2.inputs.codecs.RandomHttpMessageCodec;
import org.graylog2.inputs.transports.RandomMessageTransport;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.MessageInput;

public class FakeHttpMessageInput extends MessageInput {

    private static final String NAME = "Random HTTP message generator";

    @AssistedInject
    public FakeHttpMessageInput(@Assisted Configuration configuration,
                                RandomMessageTransport.Factory transportFactory,
                                RandomHttpMessageCodec.Factory codecFactory,
                                MetricRegistry metricRegistry, LocalMetricRegistry localRegistry, Config config, Descriptor descriptor) {
        super(metricRegistry,
              transportFactory.create(configuration),
              localRegistry, codecFactory.create(configuration),
              config, descriptor);
    }

    public interface Factory extends MessageInput.Factory<FakeHttpMessageInput> {
        @Override
        FakeHttpMessageInput create(Configuration configuration);

        @Override
        Config getConfig();

        @Override
        Descriptor getDescriptor();
    }

    public static class Descriptor extends MessageInput.Descriptor {
        public Descriptor() {
            super(NAME, false, "");
        }
    }

    public static class Config extends MessageInput.Config {
        public Config() { /* required by guice */ }
        @AssistedInject
        public Config(RandomMessageTransport.Factory transport, RandomHttpMessageCodec.Factory codec) {
            super(transport.getConfig(), codec.getConfig());
        }
    }
}
