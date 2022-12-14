/**
 * Copyright 2010 Lennart Koopmann <lennart@socketfeed.com>
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

package org.graylog2.messagehandlers.gelf;

/**
 * GELFClientIF.java: Sep 14, 2010 6:35:13 PM
 *
 * Representing a GELF client. Allows i.e. decoding of sent data.
 *
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public interface GELFClientHandlerIF {


    /**
     * Handles the client: Decodes JSON, Stores in MongoDB, ReceiveHooks
     *
     * @return boolean
     */
    public boolean handle();

}
