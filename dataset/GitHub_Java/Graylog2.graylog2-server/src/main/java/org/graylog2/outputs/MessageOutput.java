/**
 * Copyright 2012 Lennart Koopmann <lennart@socketfeed.com>
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

package org.graylog2.outputs;

import java.util.List;
import org.graylog2.GraylogServer;
import org.graylog2.logmessage.LogMessage;

/**
 * MessageOutput.java: 29.04.2012 21:23:18
 *
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public interface MessageOutput {

    public void write(List<LogMessage> msg, GraylogServer server) throws Exception;

}
