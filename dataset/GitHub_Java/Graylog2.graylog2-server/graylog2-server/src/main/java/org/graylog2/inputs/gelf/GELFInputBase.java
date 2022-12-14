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
package org.graylog2.inputs.gelf;

import org.graylog2.Core;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.MisfireException;
import org.jboss.netty.bootstrap.Bootstrap;
import org.jboss.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class GELFInputBase extends MessageInput {

    public static final String CK_BIND_ADDRESS = "bind_address";
    public static final String CK_PORT = "port";

    protected Bootstrap bootstrap;
    protected Channel channel;

    protected Core core;
    protected Configuration config;
    protected InetSocketAddress socketAddress;

    @Override
    public void configure(Configuration config, GraylogServer graylogServer) throws ConfigurationException {
        this.core = (Core) graylogServer;
        this.config = config;

        if (!checkConfig(config)) {
            throw new ConfigurationException(config.getSource().toString());
        }

        this.socketAddress = new InetSocketAddress(
                config.getString(CK_BIND_ADDRESS),
                (int) config.getInt(CK_PORT)
        );
    }

    protected boolean checkConfig(Configuration config) {
        return config.stringIsSet(CK_BIND_ADDRESS)
                && config.intIsSet(CK_PORT);
    }

    @Override
    public void stop() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (bootstrap != null) {
            bootstrap.releaseExternalResources();
            bootstrap.shutdown();
        }
    }

    public ConfigurationRequest getRequestedConfiguration() {
        ConfigurationRequest r = new ConfigurationRequest();

        r.addField(ConfigurationRequest.Templates.bindAddress(CK_BIND_ADDRESS));
        r.addField(ConfigurationRequest.Templates.portNumber(CK_PORT));

        return r;
    }
    @Override
    public boolean isExclusive() {
        return false;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return config.getSource();
    }

    @Override
    public String getName() {
        throw new RuntimeException("Must be overridden in GELF input classes.");
    }

    @Override
    public void launch() throws MisfireException {
        throw new RuntimeException("Must be overridden in GELF input classes.");
    }

    @Override
    public String linkToDocs() {
        return "";
    }

}
