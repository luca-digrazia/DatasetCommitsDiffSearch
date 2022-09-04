/**
 * Copyright 2011 Lennart Koopmann <lennart@socketfeed.com>
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

package org.graylog2.forwarders;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.graylog2.Log;
import org.graylog2.messagehandlers.gelf.GELFMessage;

/**
 * LogglyForwarder.java: Mar 18, 2011 9:32:24 PM
 *
 * Forwards messages to Logg.ly. (via HTTP/S API(
 *
 * @author: Lennart Koopmann <lennart@socketfeed.com>
 */
public class LogglyForwarder implements MessageForwarderIF {

    private boolean succeeded = false;
    private String url = null;

    /**
     * Forward a GELF (or converted syslog) message to Logg.ly
     *
     * @param where URL API endpoint
     * @param message The message to forward
     * @return true in case of success, otherwise false
     * @throws MessageForwarderConfigurationException
     */
    public boolean forward(String where, GELFMessage message) throws MessageForwarderConfigurationException {
        if (url == null || url.length() == 0) {
            throw new MessageForwarderConfigurationException("No endpoint URL configured.");
        }

        this.url = where;

        this.succeeded = this.send(message.toOneLiner());
        return this.succeeded;
    }

    private boolean send(String what) {
        HttpURLConnection connection = null;

        try {
            URL endpoint = new URL(this.getUrl());
            connection = (HttpURLConnection) endpoint.openConnection();

            connection.setRequestMethod("POST");
            connection.setUseCaches (false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream (
            connection.getOutputStream ());
            wr.writeBytes(what);
            wr.flush();
            wr.close();

            if(connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.crit("Could not forward message to Logg.ly: Expected HTTP 200 but was " + connection.getResponseCode());
                return false;
            }
        } catch (Exception e) {
            Log.crit("Could not forward message to Logg.ly: " + e.toString());
            return false;
        } finally {
            // Make sure to close connection.
            if(connection != null) {
                connection.disconnect();
            }
        }

        return true;
    }

    /**
     * Indicates if the last forward has succeeded.
     *
     * @return
     */
    public boolean succeeded() {
        return this.succeeded;
    }

    /**
     * @return the URL API endpoint
     */
    public String getUrl() {
        return url;
    }

}