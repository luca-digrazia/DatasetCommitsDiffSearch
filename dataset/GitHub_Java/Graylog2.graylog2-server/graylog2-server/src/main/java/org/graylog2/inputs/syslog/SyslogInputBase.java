/**
 * Copyright 2013 Lennart Koopmann <lennart@socketfeed.com>
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
package org.graylog2.inputs.syslog;

import org.graylog2.Core;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.BooleanField;
import org.graylog2.plugin.configuration.fields.NumberField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.MisfireException;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class SyslogInputBase extends MessageInput {

    public static final String CK_BIND_ADDRESS = "bind_address";
    public static final String CK_PORT = "port";
    public static final String CK_FORCE_RDNS = "force_rdns";
    public static final String CK_ALLOW_OVERRIDE_DATE = "allow_override_date";
    public static final String CK_STORE_FULL_MESSAGE = "store_full_message";

    protected ConnectionlessBootstrap bootstrap;
    protected Channel channel;

    protected Core core;
    protected Configuration config;
    protected InetSocketAddress socketAddress;

    @Override
    public void stop() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

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

    public ConfigurationRequest getRequestedConfiguration() {
        ConfigurationRequest r = new ConfigurationRequest();

        r.addField(
                new TextField(
                        CK_BIND_ADDRESS,
                        "Bind address",
                        "0.0.0.0",
                        "Address to listen on. For example 0.0.0.0 or 127.0.0.1."
                )
        );

        r.addField(
                new NumberField(
                        CK_PORT,
                        "Port",
                        514,
                        "Port to listen on.",
                        NumberField.Attribute.IS_PORT_NUMBER
                )
        );

        r.addField(
                new BooleanField(
                        CK_FORCE_RDNS,
                        "Force rDNS?",
                        false,
                        "Force rDNS resolution of hostname? Use if hostname cannot be parsed."
                )
        );

        r.addField(
                new BooleanField(
                        CK_ALLOW_OVERRIDE_DATE,
                        "Allow overriding date?",
                        true,
                        "Allow to override with current date if date could not be parsed?"
                )
        );

        r.addField(
                new BooleanField(
                        CK_STORE_FULL_MESSAGE,
                        "Store full message?",
                        false,
                        "Store the full original syslog message as full_message?"
                )
        );

        return r;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return config.getSource();
    }

    @Override
    public boolean isExclusive() {
        throw new RuntimeException("Must be overridden in syslog input classes.");
    }

    @Override
    public String getName() {
        throw new RuntimeException("Must be overridden in syslog input classes.");
    }

    @Override
    public void launch() throws MisfireException {
        throw new RuntimeException("Must be overridden in syslog input classes.");
    }

}
