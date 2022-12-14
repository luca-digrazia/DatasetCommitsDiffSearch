/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Test;

import org.graalvm.polyglot.Source;

import com.oracle.truffle.tools.chromeinspector.ScriptsHandler;
import com.oracle.truffle.tools.chromeinspector.TruffleDebugger;

public class SLInspectDebugTest {

    private static final String CODE1 = "function main() {\n" +
                    "  a = 10;\n" +
                    "  b = factorial(a/2) / 60;\n" +
                    "  while (b > 0) {\n" +
                    "    c = a + b;\n" +
                    "    b = b - c/10;\n" +
                    "  }\n" +
                    "  return b;\n" +
                    "}\n" +
                    "function factorial(n) {\n" +
                    "  f = 1;\n" +
                    "  i = 2;\n" +
                    "  while (i <= n) {\n" +
                    "    f2 = f * i;\n" +
                    "    i = i + 1;\n" +
                    "    f = f2;\n" +
                    "  }\n" +
                    "  return f;\n" +
                    "}";
    private static final String CODE2 = "function main() {\n" +
                    "  n = 10;\n" +
                    "  i = 0;\n" +
                    "  while (i < n) {\n" +
                    "    fceWithBP(i);\n" +
                    "    i = i + 1;\n" +
                    "  }\n" +
                    "}\n" +
                    "function fceWithBP(i) {\n" +
                    "  i2 = i*i;\n" +
                    "  return i2;\n" +
                    "}";
    private static final String SL_BUILTIN_URI = "truffle:8350e5a3e24c153df2275c9f80692773/SL builtin";

    private InspectorTester tester;

    @After
    public void tearDown() {
        tester = null;
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    @Test
    public void testInitialSuspendAndSource() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = ScriptsHandler.getNiceStringFromURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}}]}}\n"));

        // Get the script code:
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.getScriptSource\",\"params\":{\"scriptId\":\"1\"}}");
        String scriptSourceMessage = "{\"result\":{\"scriptSource\":\"" + source.getCharacters().toString().replace("\n", "\\n") + "\"},\"id\":3}";
        String messages = tester.getMessages(true).trim();
        assertEquals(scriptSourceMessage, messages);
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"
        ));
        tester.finish();
    }

    @Test
    public void testStepping() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = ScriptsHandler.getNiceStringFromURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}}]}}\n"
        ));

        // Step over:
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"3\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"4\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":2}}]}}\n"
        ));

        // Step into:
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"5\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"6\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":9}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":10}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":2}}]}}\n"
        ));

        // Step out:
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOut\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"9\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"10\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":2}}]}}\n"
        ));
        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.stepOut\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"
        ));
        tester.finish();
    }

    @Test
    public void testBreakpoints() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        String slTestURI = ScriptsHandler.getNiceStringFromURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));

        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":11,\"url\":\"" + slTestURI + "\",\"columnNumber\":0,\"condition\":\"\"}}");

        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":3}", tester.getMessages(true).trim());
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the breakpoint:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":11}]}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":9}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"3\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"4\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":2}}]}}\n"));
        // Continue to a location:
        tester.sendMessage("{\"id\":33,\"method\":\"Debugger.continueToLocation\",\"params\":{\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":5}}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":33}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"block\",\"type\":\"block\",\"object\":{\"description\":\"block\",\"type\":\"object\",\"objectId\":\"5\"}}," +
                                                                 "{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"6\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"7\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":5}}]}}\n"));
        // Resume to finish:
        tester.sendMessage("{\"id\":35,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":35}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testBreakpointDeactivation() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE2, "SLTest.sl").build();
        String slTestURI = ScriptsHandler.getNiceStringFromURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        String srcURL = ScriptsHandler.getNiceStringFromURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":9,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":3}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":10,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"2\",\"locations\":[]},\"id\":4}", tester.getMessages(true).trim());
        // Deactivate the breakpoints:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.setBreakpointsActive\",\"params\":{\"active\":false}}");
        assertEquals("{\"result\":{},\"id\":10}", tester.getMessages(true).trim());
        tester.eval(source);

        // Suspend at the beginning of the script:
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":11,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":144,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"ee148976fc7d6f36fc01da4bfba1c3f3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":9}]}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"2\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":10}]}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}}]}}\n"
        ));
        // Step over to while cycle and over the method with breakpoints:
        for (int numStep = 0; numStep < 4; numStep++) {
            tester.sendMessage("{\"id\":" + (20 + numStep) + ",\"method\":\"Debugger.stepOver\"}");
            int colNum = (numStep == 1) ? 9 : (numStep >= 2) ? 4 : 2;
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":" + (20 + numStep) + "}\n" +
                            "{\"method\":\"Debugger.resumed\"}\n" +
                            "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                    "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                     "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"" + (3 + 2 * numStep) + "\"}}," +
                                                                     "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"" + (4 + 2 * numStep) + "\"}}]," +
                                                     "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                     "\"location\":{\"scriptId\":\"1\",\"columnNumber\":" + colNum + ",\"lineNumber\":" + (2 + numStep) + "}}]}}\n"
            ));
        }
        // Step to while again
        tester.sendMessage("{\"id\":28,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":28}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"11\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"12\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":3}}]}}\n"
        ));
        // Step to the method call with breakpoints again:
        tester.sendMessage("{\"id\":29,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":29}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"13\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"14\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}}]}}\n"
        ));
        // Activate the breakpoints:
        tester.sendMessage("{\"id\":30,\"method\":\"Debugger.setBreakpointsActive\",\"params\":{\"active\":true}}");
        assertEquals("{\"result\":{},\"id\":30}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":31,\"method\":\"Debugger.stepOver\"}");
        // Step over not finished, the first breakpoint is hit:
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":31}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"fceWithBP\"," +
                                                 "\"scopeChain\":[{\"name\":\"fceWithBP\",\"type\":\"local\",\"object\":{\"description\":\"fceWithBP\",\"type\":\"object\",\"objectId\":\"15\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"16\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":8}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":9}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"17\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"18\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}}]}}\n"
        ));
        tester.sendMessage("{\"id\":32,\"method\":\"Debugger.stepOut\"}");
        // Step out not finished, the second breakpoint is hit:
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":32}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"2\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"fceWithBP\"," +
                                                 "\"scopeChain\":[{\"name\":\"fceWithBP\",\"type\":\"local\",\"object\":{\"description\":\"fceWithBP\",\"type\":\"object\",\"objectId\":\"19\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"20\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":8}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":10}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"21\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"22\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}}]}}\n"
        ));
        tester.sendMessage("{\"id\":33,\"method\":\"Debugger.stepOut\"}");
        // Step out finished now:
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":33}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"23\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"24\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}}]}}\n"
        ));
        // Deactivate the breakpoints again:
        tester.sendMessage("{\"id\":40,\"method\":\"Debugger.setBreakpointsActive\",\"params\":{\"active\":false}}");
        assertEquals("{\"result\":{},\"id\":40}", tester.getMessages(true).trim());
        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testScopes() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = ScriptsHandler.getNiceStringFromURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        tester.eval(source);
        long id = tester.getContextId();

        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}}]}}\n"));
        // Ask for the local scope variables at the beginning of main:
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertEquals("{\"result\":{\"result\":[],\"internalProperties\":[]},\"id\":5}", tester.getMessages(true).trim());

        // Continue to line 4:
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.continueToLocation\",\"params\":{\"location\":{\"scriptId\":\"1\",\"lineNumber\":4,\"columnNumber\":0}}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"3\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"4\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}}]}}\n"));
        tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        assertEquals("{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"10\",\"type\":\"Number\",\"value\":\"10\"},\"configurable\":true,\"writable\":true}," +
                                              "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"Number\",\"value\":\"2\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":7}",
                        tester.getMessages(true).trim());
        // Step over:
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"block\",\"type\":\"block\",\"object\":{\"description\":\"block\",\"type\":\"object\",\"objectId\":\"5\"}}," +
                                                                 "{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"6\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"7\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":5}}]}}\n"));
        tester.sendMessage("{\"id\":9,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"6\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"c\",\"value\":{\"description\":\"12\",\"type\":\"Number\",\"value\":\"12\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":9}\n" +
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"10\",\"type\":\"Number\",\"value\":\"10\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"Number\",\"value\":\"2\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":10}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testNotSuspended() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = ScriptsHandler.getNiceStringFromURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        tester.eval(source);
        long id = tester.getContextId();

        // Executed, no suspend
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n"));
        // Try to compile a script:
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.compileScript\",\"params\":{\"expression\":\"app\",\"sourceURL\":\"\",\"persistScript\":false,\"executionContextId\":" + id + "}}");
        assertEquals("{\"result\":{\"exceptionDetails\":{\"text\":\"<Not suspended>\"}},\"id\":3}", tester.getMessages(true).trim());
        // Try to evaluate:
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"app\",\"objectGroup\":\"watch-group\",\"includeCommandLineAPI\":false,\"silent\":true,\"contextId\":" + id + "}}");
        assertEquals("{\"result\":{\"exceptionDetails\":{\"text\":\"<Not suspended>\"}},\"id\":4}", tester.getMessages(true).trim());
        tester.finish();
    }

    @Test
    public void testNoInternalSources() throws Exception {
        tester = InspectorTester.start(true);
        String internFunction = "function intern(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return public(n);\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return 42;\n" +
                        "    } else {\n" +
                        "      return intern(0 - n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source internSource = Source.newBuilder("sl", internFunction, "InternalFunc.sl").internal(true).build();
        String publicFunction = "function public(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return 2 * n;\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return public(0 - 42);\n" +
                        "    } else {\n" +
                        "      return intern(10 * n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source publicSource = Source.newBuilder("sl", publicFunction, "PublicFunc.sl").internal(false).build();
        String mainFunction = "function main() {\n" +
                        "  a = intern(1);\n" +
                        "  b = public(0 - 1);\n" +
                        "  c = intern(0);\n" +
                        "}\n";
        Source publicMain = Source.newBuilder("sl", mainFunction, "PublicMain.sl").internal(false).build();
        Source internMain = Source.newBuilder("sl", mainFunction, "InternMain.sl").internal(true).build();
        String publicSourceURI = ScriptsHandler.getNiceStringFromURI(publicSource.getURI());
        String publicMainURI = ScriptsHandler.getNiceStringFromURI(publicMain.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        long id = tester.getContextId();
        tester.eval(internSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n"));
        // No scriptParsed message for the interanl source
        tester.eval(publicSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":10,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":166,\"executionContextId\":" + id + ",\"url\":\"" + publicSourceURI + "\",\"hash\":\"f16f032ee222dcfdfc01da4bfd731e49fc671217\"}}\n"));
        tester.eval(internMain);
        // at public:1 (main -> intern -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}}]}}\n"));
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepOver\"}");
        // at public:2
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"3\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"4\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":2}}]}}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        // at public:1 again (main -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"5\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"6\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}}]}}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.stepOver\"}");
        // at public:4
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":8,\"lineNumber\":4}}]}}\n"));
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.stepOver\"}");
        // at public:7
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":7}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"9\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"10\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":7}}]}}\n"));
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.stepInto\"}");
        // at public:1 (main -> public -> intern -> intern -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"11\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"12\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"13\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"14\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}}]}}\n"));
        tester.sendMessage("{\"id\":9,\"method\":\"Debugger.stepOver\"}");
        // at public:2
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":9}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"15\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"16\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":2}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"17\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"18\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}}]}}\n"));
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.stepOver\"}");
        // back at public:7
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"19\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"20\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}}]}}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Debugger.stepInto\"}");
        // No more suspension (we're in internal sources)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":11}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        assertEquals("", tester.getMessages(false));

        tester.eval(publicMain);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":4,\"scriptId\":\"2\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":75,\"executionContextId\":" + id + ",\"url\":\"" + publicMainURI + "\",\"hash\":\"f9120a07f176a91df047f02ffe94b4d3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"21\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"22\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"2\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"2\",\"columnNumber\":2,\"lineNumber\":1}}]}}\n"));
        tester.sendMessage("{\"id\":12,\"method\":\"Debugger.stepInto\"}");
        // at public:1 (main -> internal -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":12}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"23\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"24\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"25\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"26\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"2\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"2\",\"columnNumber\":6,\"lineNumber\":1}}]}}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testNoBlackboxedSources() throws Exception {
        tester = InspectorTester.start(true);
        String blackboxedFunction = "function black(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return public(n);\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return 42;\n" +
                        "    } else {\n" +
                        "      return black(0 - n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source blackboxedSource = Source.newBuilder("sl", blackboxedFunction, "BlackboxedFunc.sl").internal(false).build();
        String publicFunction = "function public(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return 2 * n;\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return public(0 - 42);\n" +
                        "    } else {\n" +
                        "      return black(10 * n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source publicSource = Source.newBuilder("sl", publicFunction, "PublicFunc.sl").internal(false).build();
        String mainFunction = "function main() {\n" +
                        "  a = black(1);\n" +
                        "  b = public(0 - 1);\n" +
                        "  c = black(0);\n" +
                        "}\n";
        Source publicMain = Source.newBuilder("sl", mainFunction, "PublicMain.sl").internal(false).build();
        String blackboxedSourceURI = ScriptsHandler.getNiceStringFromURI(blackboxedSource.getURI());
        String publicSourceURI = ScriptsHandler.getNiceStringFromURI(publicSource.getURI());
        String publicMainURI = ScriptsHandler.getNiceStringFromURI(publicMain.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBlackboxPatterns\",\"params\":{\"patterns\":[\"BlackboxedFunc.sl\"]}}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n"));
        long id = tester.getContextId();
        tester.eval(blackboxedSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":10,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":155,\"executionContextId\":" + id + ",\"url\":\"" + blackboxedSourceURI + "\",\"hash\":\"e6563ff5f01769c8f4f70d27f779b564fdaf5f20\"}}\n"));
        tester.eval(publicSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":10,\"scriptId\":\"2\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":165,\"executionContextId\":" + id + ",\"url\":\"" + publicSourceURI + "\",\"hash\":\"f16f032ee222dcfdfc01da4bfd731e49fc671217\"}}\n"));
        tester.eval(publicMain);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":4,\"scriptId\":\"3\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":73,\"executionContextId\":" + id + ",\"url\":\"" + publicMainURI + "\",\"hash\":\"f9120a07f176a91df047f02ffe94b4d3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"3\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"3\",\"columnNumber\":2,\"lineNumber\":1}}]}}\n"));
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepInto\"}");
        // at public:1 (main -> black -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"3\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"4\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"2\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"2\",\"columnNumber\":6,\"lineNumber\":1}}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"black\"," +
                                                 "\"scopeChain\":[{\"name\":\"black\",\"type\":\"local\",\"object\":{\"description\":\"black\",\"type\":\"object\",\"objectId\":\"5\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"6\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":11,\"lineNumber\":2}}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"functionLocation\":{\"scriptId\":\"3\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"3\",\"columnNumber\":6,\"lineNumber\":1}}]}}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.eval(publicMain);
        tester.finish();
    }

    @Test
    public void testSourceMatchesBlackboxPatterns() throws Exception {
        File tmp = Files.createTempDirectory("test").toFile();
        tmp.deleteOnExit();
        File parent = new File(tmp, "blackbox");
        parent.mkdir();
        parent.deleteOnExit();
        File file = new File(parent, "BlackboxTest.sl");
        file.createNewFile();
        file.deleteOnExit();

        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder(file).mimeType("application/x-sl").build();

        // Test name of a file
        assertTrue(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("BlackboxTest.sl")}));
        assertFalse(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("Test.sl")}));

        // Test regular expression that contain a specific name
        assertTrue(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("Test\\.sl$")}));
        assertFalse(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("Fest\\.sl$")}));

        // Test regular expression that contain certain types of files
        assertTrue(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("\\.sl$")}));
        assertFalse(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("\\.ssl$")}));

        // Test entire folder that contains scripts you to blackbox
        assertTrue(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("blackbox")}));
        assertFalse(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("tmp")}));

        // Test regular expression produced by Chrome Inspector's 'Blackbox Script' action.
        assertTrue(TruffleDebugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("^file://.*/BlackboxTest\\.sl$")}));
    }
    // @formatter:on
    // CheckStyle: resume line length check
}
