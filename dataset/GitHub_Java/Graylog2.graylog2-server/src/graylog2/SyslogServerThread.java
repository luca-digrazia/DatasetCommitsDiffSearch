/**
 * Copyright 2010 Lennart Koopmann <lennart@scopeport.org>
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

/**
 * SyslogServerThread.java: Lennart Koopmann <lennart@scopeport.org> | May 17, 2010 9:23:33 PM
 */

package graylog2;

import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerIF;

public class SyslogServerThread extends Thread {

    private int port = 0;

    public SyslogServerThread(int port) {
        this.port = port;
    }

    public void run() {
        SyslogServerIF syslogServer = SyslogServer.getInstance("udp");
        
        syslogServer.getConfig().setPort(port);
        syslogServer.getConfig().addEventHandler(new SyslogEventHandler());

        syslogServer = SyslogServer.getThreadedInstance("udp");
        Main.syslogCoreThread = syslogServer.getThread();
    }

}
