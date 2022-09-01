/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.jdwp.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;

public final class HandshakeController {

    private static final String JDWP_HANDSHAKE = "JDWP-Handshake";

    /**
     * Initializes a Socket connection which serves as a transport for jdwp communication.
     * @param port the listening port that the debugger should attach to
     * @throws IOException
     */
    public static SocketConnection createSocketConnection(int port, Collection<Thread> activeThreads) throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setSoTimeout(0); // no timeout
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        // block until a debugger has accepted the socket
        Socket connectionSocket = serverSocket.accept();

        if (!handshake(connectionSocket)) {
            throw new IOException("Unable to handshake with debubgger");
        }
        SocketConnection connection = new SocketConnection(connectionSocket, serverSocket);
        Thread JDWPSender = new Thread(connection, "jdwp-transmitter");
        JDWPSender.setDaemon(true);
        JDWPSender.start();
        activeThreads.add(JDWPSender);
        return connection;
    }

    /**
     * Handshake with the debugger
     */
    private static boolean handshake(Socket s) throws IOException {

        byte[] hello = JDWP_HANDSHAKE.getBytes("UTF-8");

        byte[] b = new byte[hello.length];
        int received = 0;
        while (received < hello.length) {
            int n;
            try {
                n = s.getInputStream().read(b, received, hello.length-received);
            } catch (SocketTimeoutException x) {
                throw new IOException("handshake timeout");
            }
            if (n < 0) {
                s.close();
                throw new IOException("handshake failed - connection prematurally closed");
            }
            received += n;
        }
        for (int i=0; i<hello.length; i++) {
            if (b[i] != hello[i]) {
                throw new IOException("handshake failed - unrecognized message from the debugger");
            }
        }

        // handshake received, so return the gesture to establish the jdwp transport
        s.getOutputStream().write(hello);
        return true;
    }
}
