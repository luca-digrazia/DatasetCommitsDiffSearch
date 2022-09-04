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
package org.graylog2.inputs.util;

import com.codahale.metrics.Gauge;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class ConnectionCounter extends SimpleChannelHandler {

    private final AtomicInteger connections;
    private long totalConnections;

    public ConnectionCounter() {
        connections = new AtomicInteger();
        totalConnections = 0;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        connections.incrementAndGet();
        totalConnections++;
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        connections.decrementAndGet();
    }

    public int getConnectionCount() {
        return connections.get();
    }

    public long getTotalConnections() {
        return totalConnections;
    }

    public Gauge<Integer> gaugeCurrent() {
        return new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return getConnectionCount();
            }
        };
    }

    public Gauge<Long> gaugeTotal() {
        return new Gauge<Long>() {
            @Override
            public Long getValue() {
                return getTotalConnections();
            }
        };
    }

}
