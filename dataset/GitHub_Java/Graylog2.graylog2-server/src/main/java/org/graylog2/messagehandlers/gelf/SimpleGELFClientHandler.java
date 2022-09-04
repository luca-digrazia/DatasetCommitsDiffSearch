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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.util.zip.DataFormatException;
import org.graylog2.Log;
import org.graylog2.Tools;
import org.graylog2.database.MongoBridge;
import org.graylog2.messagehandlers.common.GELFMessageFilterHook;
import org.graylog2.messagehandlers.common.HostUpsertHook;
import org.graylog2.messagehandlers.common.MessageCounterHook;
import org.graylog2.messagehandlers.common.MessageFilterHook;
import org.graylog2.messagehandlers.common.ReceiveHookManager;
import org.productivity.java.syslog4j.Syslog;

/**
 * GELFClient.java: Jun 23, 2010 7:15:12 PM
 *
 * Handling a GELF client message consisting of only one UDP message.
 *
 * @author: Lennart Koopmann <lennart@socketfeed.com>
 */
public class SimpleGELFClientHandler extends GELFClientHandlerBase implements GELFClientHandlerIF {

    /**
     * Representing a GELF client consisting of only one UDP message.
     * 
     * @param clientMessage The raw data the GELF client sent. (JSON string)
     * @throws DataFormatException
     * @throws UnsupportedEncodingException
     * @throws InvalidGELFCompressionMethodException
     * @throws IOException
     */
    public SimpleGELFClientHandler(DatagramPacket clientMessage) throws DataFormatException, UnsupportedEncodingException, InvalidGELFCompressionMethodException, IOException {

        // Determine compression type.
        int type = GELF.getGELFType(clientMessage.getData());

        // Decompress.
        switch (type) {
            // Decompress ZLIB
            case GELF.TYPE_ZLIB:
                Log.info("Handling ZLIB compressed SimpleGELFClient");
                this.clientMessage = Tools.decompressZlib(clientMessage.getData());
                break;

            // Decompress GZIP
            case GELF.TYPE_GZIP:
                Log.info("Handling GZIP compressed SimpleGELFClient");
                this.clientMessage = Tools.decompressGzip(clientMessage.getData());
                break;

            // Unsupported encoding if not handled by prior cases.
            default:
                throw new UnsupportedEncodingException();
        }
        
    }
    
    /**
     * Handles the client: Decodes JSON, Stores in MongoDB, ReceiveHooks
     * 
     * @return boolean
     */
    public boolean handle() {
        try {
             // Fills properties with values from JSON.
            try { this.parse(); } catch(Exception e) {
                Log.warn("Could not parse GELF JSON: " + e.toString() + " - clientMessage was: " + this.clientMessage);
                return false;
            }

            // Store in MongoDB.
            // Connect to database.
            MongoBridge m = new MongoBridge();

            // Log if we are in debug mode.
            Log.info("Got GELF message: " + message.toString());

            // Insert message into MongoDB.
            boolean filterOut = ReceiveHookManager.preProcess(new GELFMessageFilterHook(), message);
            if( filterOut ) {
            	Syslog.getInstance("udp").debug("Not inserting event into database.");
            } else {
                m.insertGelfMessage(message);
                // This is doing the upcounting for statistics.
                ReceiveHookManager.postProcess(new MessageCounterHook(), message);

                // Counts up host in hosts collection.
                ReceiveHookManager.postProcess(new HostUpsertHook(), message);
            }

        } catch(Exception e) {
            Log.warn("Could not handle GELF client: " + e.toString());
            e.printStackTrace();
            return false;
        }

        return true;
    }

}
