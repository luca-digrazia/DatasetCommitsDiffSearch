/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
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
 *
 */
package org.graylog2.inputs.raw;

import org.graylog2.Core;
import org.graylog2.plugin.configuration.Configuration;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class RawPipelineFactory implements ChannelPipelineFactory {

    private final Core server;
    private final Configuration config;
    private final String inputId;

    public RawPipelineFactory(Core server, Configuration config, String inputId) {
        this.server = server;
        this.config = config;
        this.inputId = inputId;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = Channels.pipeline();
        p.addLast("handler", new RawDispatcher(server, config, inputId));

        return p;
    }

}
