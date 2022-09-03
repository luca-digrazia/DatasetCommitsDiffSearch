/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

/**
 * Tests the use of {@link MessageTransport} with the Inspector.
 */
public class InspectorMessageTransportTest {

    private static final String PORT = "54367";
    private static final Pattern URI_PATTERN = Pattern.compile("ws://.*:" + PORT + "/[\\dA-Fa-f\\-]+");
    private static final String[] INITIAL_MESSAGES = {
                    "{\"id\":5,\"method\":\"Runtime.enable\"}",
                    "{\"id\":6,\"method\":\"Debugger.enable\"}",
                    "{\"id\":7,\"method\":\"Debugger.setPauseOnExceptions\",\"params\":{\"state\":\"none\"}}",
                    "{\"id\":8,\"method\":\"Debugger.setAsyncCallStackDepth\",\"params\":{\"maxDepth\":32}}",
                    "{\"id\":20,\"method\":\"Debugger.setBlackboxPatterns\",\"params\":{\"patterns\":[]}}",
                    "{\"id\":28,\"method\":\"Runtime.runIfWaitingForDebugger\"}"
    };
    private static final String[] MESSAGES = {
                    null, null, null, null, null, null,
                    "toClient({\"result\":{},\"id\":5})",
                    "toClient({\"result\":{},\"id\":6})",
                    "toClient({\"result\":{},\"id\":7})",
                    "toClient({\"result\":{},\"id\":8})",
                    "toClient({\"result\":{},\"id\":20})",
                    "toClient({\"result\":{},\"id\":28})",
                    "toClient({\"method\":\"Runtime.executionContextCreated\"",
                    "toClient({\"method\":\"Debugger.paused\",",
                    "toBackend({\"id\":100,\"method\":\"Debugger.resume\"})",
                    "toClient({\"result\":{},\"id\":100})",
                    "toClient({\"method\":\"Debugger.resumed\"})"
    };
    static {
        for (int i = 0; i < INITIAL_MESSAGES.length; i++) {
            MESSAGES[i] = "toBackend(" + INITIAL_MESSAGES[i] + ")";
        }
    }

    @Test
    public void inspectorEndpointDefaultPathTest() {
        inspectorEndpointTest(null);
    }

    @Test
    public void inspectorEndpointExplicitPathTest() {
        inspectorEndpointTest("simplePath");
        inspectorEndpointTest("/some/complex/path");
    }

    @Test
    public void inspectorEndpointRaceTest() {
        RaceControl rc = new RaceControl();
        inspectorEndpointTest(null, rc);
    }

    private static void inspectorEndpointTest(String path) {
        inspectorEndpointTest(path, null);
    }

    private static void inspectorEndpointTest(String path, RaceControl rc) {
        Session session = new Session(rc);
        DebuggerEndpoint endpoint = new DebuggerEndpoint(path, rc);
        Engine engine = endpoint.onOpen(session);

        Context context = Context.newBuilder().engine(engine).build();
        Value result = context.eval("sl", "function main() {\n  x = 1;\n  return x;\n}");
        Assert.assertEquals("Result", "1", result.toString());

        endpoint.onClose(session);
        Assert.assertEquals(session.messages.toString(), MESSAGES.length, session.messages.size());
        for (int i = 0; i < MESSAGES.length; i++) {
            if (!session.messages.get(i).startsWith(MESSAGES[i])) {
                Assert.assertTrue("i = " + i + ", Expected start with '" + MESSAGES[i] + "', got: '" + session.messages.get(i) + "'", false);
            }
        }
    }

    @Test
    public void inspectorVetoedTest() {
        Engine.Builder engineBuilder = Engine.newBuilder().serverTransport(new MessageTransport() {

            @Override
            public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                throw new MessageTransport.VetoException("Server vetoed.");
            }
        }).option("inspect", PORT);
        try {
            engineBuilder.build();
            Assert.fail("Veto not effective.");
        } catch (PolyglotException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.startsWith("Starting inspector on "));
            Assert.assertTrue(message, message.endsWith(":" + PORT + " failed: Server vetoed."));
        }
    }

    private static final class Session {

        private final RaceControl rc;
        final List<String> messages = new ArrayList<>(MESSAGES.length);
        private final BasicRemote remote = new BasicRemote(messages);
        private boolean opened = true;

        Session(RaceControl rc) {
            this.rc = rc;
        }

        BasicRemote getBasicRemote() {
            return remote;
        }

        public boolean isOpen() {
            return opened;
        }

        void addMessageHandler(MsgHandler handler) throws IOException {
            remote.handler = handler;
            sendInitialMessages(handler);
            if (rc != null) {
                rc.clientMessagesSent();
            }
        }

        private static void sendInitialMessages(final MsgHandler handler) throws IOException {
            for (String message : INITIAL_MESSAGES) {
                handler.onMessage(message);
            }
        }

        private void close() {
            opened = false;
        }

        interface MsgHandler {
            void onMessage(String message) throws IOException;
        }
    }

    private static final class BasicRemote {

        Session.MsgHandler handler;
        private final List<String> messages;

        BasicRemote(List<String> messages) {
            this.messages = messages;
        }

        void sendText(String text) throws IOException {
            if (!text.startsWith("{\"method\":\"Debugger.scriptParsed\"")) {
                messages.add("toClient(" + text + ")");
            }
            if (text.startsWith("{\"method\":\"Debugger.paused\"")) {
                handler.onMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
            }
        }

    }

    private static final class DebuggerEndpoint {

        private final String path;
        private final RaceControl rc;

        DebuggerEndpoint(String path, RaceControl rc) {
            this.path = path;
            this.rc = rc;
        }

        public Engine onOpen(final Session session) {
            assert this != null;
            Engine.Builder engineBuilder = Engine.newBuilder().serverTransport(new MessageTransport() {
                @Override
                public MessageEndpoint open(URI requestURI, MessageEndpoint peerEndpoint) throws IOException, MessageTransport.VetoException {
                    Assert.assertEquals("Invalid protocol", "ws", requestURI.getScheme());
                    String uriStr = requestURI.toString();
                    if (path == null) {
                        Assert.assertTrue(uriStr, URI_PATTERN.matcher(uriStr).matches());
                    } else {
                        Assert.assertTrue(uriStr, uriStr.startsWith("ws://"));
                        String absolutePath = path.startsWith("/") ? path : "/" + path;
                        Assert.assertTrue(uriStr, uriStr.endsWith(":" + PORT + absolutePath));
                    }
                    MessageEndpoint ourEndpoint = new ChromeDebuggingProtocolMessageHandler(session, requestURI, peerEndpoint);
                    if (rc != null) {
                        rc.waitTillClientDataAreSent();
                    }
                    return ourEndpoint;
                }
            }).option("inspect", PORT);
            if (path != null) {
                engineBuilder.option("inspect.Path", path);
            }
            Engine engine = engineBuilder.build();
            return engine;
        }

        public void onClose(Session session) {
            Assert.assertNotNull(session);
            assert this != null;
        }

    }

    private static final class ChromeDebuggingProtocolMessageHandler implements MessageEndpoint {

        private final Session session;

        ChromeDebuggingProtocolMessageHandler(Session session, URI uri, MessageEndpoint peerEndpoint) throws IOException {
            this.session = session;
            Assert.assertEquals("ws", uri.getScheme());

            /* Forward a JSON message from the client to the backend. */
            session.addMessageHandler(message -> {
                Assert.assertTrue(session.isOpen());
                session.messages.add("toBackend(" + message + ")");
                peerEndpoint.sendText(message);
            });
        }

        /* Forward a JSON message from the backend to the client. */
        @Override
        public void sendText(String text) throws IOException {
            Assert.assertTrue(session.isOpen());
            session.getBasicRemote().sendText(text);
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            throw new UnsupportedOperationException("sendBinary");
        }

        @Override
        public void sendPing(ByteBuffer data) throws IOException {
            throw new UnsupportedOperationException("onPing");
        }

        @Override
        public void sendPong(ByteBuffer data) throws IOException {
            throw new UnsupportedOperationException("onPong");
        }

        @Override
        public void sendClose() throws IOException {
            session.close();
        }

    }

    // Test a possible race between data sent and transport open.
    // The WSInterceptorServer needs to be able to deal with sendText() being called before opened()
    private static final class RaceControl {

        private boolean clientSent = false;

        private synchronized void clientMessagesSent() {
            clientSent = true;
            notifyAll();
        }

        private synchronized void waitTillClientDataAreSent() {
            try {
                while (!clientSent) {
                    wait();
                }
                // The data were sent, but it takes a while to process them till
                // WSInterceptorServer#sendText gets called.
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        }

    }
}
