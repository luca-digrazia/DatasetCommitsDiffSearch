/**
 * This file is part of Graylog Pipeline Processor.
 *
 * Graylog Pipeline Processor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog Pipeline Processor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog Pipeline Processor.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.pipelineprocessor.functions.ips;

import java.net.InetAddress;

/**
 * Graylog's rule language wrapper for InetAddress.
 * <br/>
 * The purpose of this class is to guard against accidentally accessing properties which can trigger name resolutions
 * and to provide a known interface to deal with IP addresses.
 * <br/>
 * Almost all of the logic is in the actual InetAddress delegate object.
 */
public class IpAddress {

    private InetAddress address;

    public IpAddress(InetAddress address) {
        this.address = address;
    }


    public InetAddress inetAddress() {
        return address;
    }
}
