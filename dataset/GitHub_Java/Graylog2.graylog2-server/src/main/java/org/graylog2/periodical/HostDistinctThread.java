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

package org.graylog2.periodical;

import org.graylog2.Log;
import org.graylog2.database.MongoBridge;

/**
 * SystemStatisticThread.java: May 21, 2010 6:42:25 PM
 *
 * Calls MongoBridge.distinctHosts() every 10 seconds.
 *
 * @author: Lennart Koopmann <lennart@socketfeed.com>
 */
public class HostDistinctThread extends Thread {

    /**
     * Start the thread. Runs forever.
     */
    @Override public void run() {
        // Run forever.
        while (true) {
            try {
                MongoBridge m = new MongoBridge();

                // Handled syslog events.
                m.distinctHosts();
            } catch (Exception e) {
                Log.warn("Error in HostDistinctThread: " + e.toString());
            }
            
           // Run every 10 seconds.
           try { Thread.sleep(10000); } catch(InterruptedException e) {}
        }
    }

}
