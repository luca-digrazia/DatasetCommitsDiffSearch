/*
 * Copyright 2014 TORCH GmbH
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
package org.graylog2.inputs.raw.file;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.graylog2.inputs.codecs.RawCodec;
import org.graylog2.inputs.transports.FileSlurpTransport;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.MessageInput;

public class RawFileInput extends MessageInput {

    private static final String NAME = "Raw/Plaintext File";

    @AssistedInject
    public RawFileInput(@Assisted final Configuration configuration,
                        final FileSlurpTransport.Factory tcpTransportFactory,
                        final RawCodec.Factory rawCodecFactory,
                        final MetricRegistry metricRegistry,
                        LocalMetricRegistry localRegistry,
                        Config config,
                        Descriptor descriptor,
                        ServerStatus serverStatus) {
        super(metricRegistry,
              configuration,
              tcpTransportFactory.create(configuration),
              localRegistry, rawCodecFactory.create(configuration),
              config, descriptor, serverStatus);
    }

    public interface Factory extends MessageInput.Factory<RawFileInput> {
        @Override
        RawFileInput create(Configuration configuration);

        @Override
        Config getConfig();

        @Override
        Descriptor getDescriptor();
    }

    public static class Descriptor extends MessageInput.Descriptor {
        @Inject
        public Descriptor() {
            super(NAME, false, "");
        }
    }

    public static class Config extends MessageInput.Config {
        @Inject
        public Config(FileSlurpTransport.Factory transport, RawCodec.Factory codec) {
            super(transport.getConfig(), codec.getConfig());
        }
    }
}