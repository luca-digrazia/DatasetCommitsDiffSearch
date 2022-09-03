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
package com.oracle.truffle.api.debug.test;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import java.util.Iterator;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test of {@link SuspendedEvent#prepareReenterFrame(com.oracle.truffle.api.debug.DebugStackFrame)}.
 */
public class ReenterStackFrameTest extends AbstractDebugTest {

    @Test
    public void testReenterCurrent() throws Throwable {
        final Source source = testSource("ROOT(DEFINE(a, ROOT(\n" +
                        "  STATEMENT(),\n" +
                        "  STATEMENT(EXPRESSION)\n" +
                        ")),\n" +
                        "CALL(a))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            final Exception[] exception = new Exception[1];
            final int[] suspendHits = new int[1];
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT()", currentFrame.getSourceSection().getCharacters());
                    event.prepareStepOver(1);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT(EXPRESSION)", currentFrame.getSourceSection().getCharacters());
                    event.prepareUnwindFrame(currentFrame);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("CALL(a)", currentFrame.getSourceSection().getCharacters());
                    Iterator<DebugStackFrame> frames = event.getStackFrames().iterator();
                    Assert.assertEquals("", frames.next().getName());
                    Assert.assertFalse(frames.hasNext());
                    // Enter into "a"
                    event.prepareStepInto(1);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT()", currentFrame.getSourceSection().getCharacters());
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectDone();
            if (exception[0] != null) {
                throw exception[0];
            }
            Assert.assertEquals(4, suspendHits[0]);
        }
    }

    @Test
    public void testReenterDeep() throws Throwable {
        final Source source = testSource("ROOT(DEFINE(a, ROOT(\n" +
                        " STATEMENT(),\n" +
                        " DEFINE(aa, ROOT(\n" +
                        "  STATEMENT(EXPRESSION),\n" +
                        "  DEFINE(aaa, ROOT(\n" +
                        "   STATEMENT(EXPRESSION, EXPRESSION))\n" +
                        "  ),\n" +
                        "  CALL(aaa))\n" +
                        " ),\n" +
                        " CALL(aa))\n" +
                        "), \n" +
                        "CALL(a))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            final Exception[] exception = new Exception[1];
            final int[] suspendHits = new int[1];
            final int[] firstStatementNumJavaFrames = new int[1];
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT()", currentFrame.getSourceSection().getCharacters());
                    event.prepareStepInto(2);
                    suspendHits[0]++;
                    firstStatementNumJavaFrames[0] = Thread.currentThread().getStackTrace().length;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT(EXPRESSION, EXPRESSION)", currentFrame.getSourceSection().getCharacters());
                    Iterator<DebugStackFrame> sfIter = event.getStackFrames().iterator();
                    sfIter.next(); // the top one (aaa)
                    event.prepareUnwindFrame(sfIter.next()); // the second one (aa)
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("CALL(aa)", currentFrame.getSourceSection().getCharacters());
                    event.prepareUnwindFrame(event.getStackFrames().iterator().next()); // "a"
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("CALL(a)", currentFrame.getSourceSection().getCharacters());
                    event.prepareStepInto(2);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT(EXPRESSION)", currentFrame.getSourceSection().getCharacters());
                    event.prepareStepInto(1);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT(EXPRESSION, EXPRESSION)", currentFrame.getSourceSection().getCharacters());
                    Iterator<DebugStackFrame> sfIter = event.getStackFrames().iterator();
                    sfIter.next(); // the top one (aaa)
                    sfIter.next(); // the second one (aa)
                    event.prepareUnwindFrame(sfIter.next()); // the third one (a)
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("CALL(a)", currentFrame.getSourceSection().getCharacters());
                    event.prepareStepInto(1);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame currentFrame = event.getTopStackFrame();
                try {
                    Assert.assertEquals("STATEMENT()", currentFrame.getSourceSection().getCharacters());
                    Assert.assertEquals("Same Java depth", firstStatementNumJavaFrames[0], Thread.currentThread().getStackTrace().length);
                    suspendHits[0]++;
                } catch (Exception ex) {
                    exception[0] = ex;
                }
            });
            expectDone();
            if (exception[0] != null) {
                throw exception[0];
            }
            Assert.assertEquals(8, suspendHits[0]);
        }
    }

    @Test
    public void testVariables() throws Throwable {
        // Test that after a re-enter, variables are cleared.
        final Source source = testSource("ROOT(DEFINE(a, ROOT(\n" +
                        "  STATEMENT(),\n" +
                        "  VARIABLE(x, 42),\n" +
                        "  VARIABLE(n, 100),\n" +
                        "  VARIABLE(m, 200),\n" +
                        "  STATEMENT()\n" +
                        ")),\n" +
                        "CALL(a))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            final Exception[] exception = new Exception[1];
            final int[] suspendHits = new int[1];
            final int numRepeats = 5;
            for (int i = numRepeats - 1; i >= 0; i--) {
                expectSuspended((SuspendedEvent event) -> {
                    DebugStackFrame currentFrame = event.getTopStackFrame();
                    try {
                        Assert.assertEquals("STATEMENT()", event.getSourceSection().getCharacters());
                        Assert.assertEquals(2, event.getSourceSection().getStartLine());
                        checkStack(currentFrame);
                        event.prepareStepOver(1);
                        suspendHits[0]++;
                    } catch (Exception ex) {
                        exception[0] = ex;
                    }
                });
                final boolean doUnwind = i > 0;
                expectSuspended((SuspendedEvent event) -> {
                    DebugStackFrame currentFrame = event.getTopStackFrame();
                    try {
                        Assert.assertEquals("STATEMENT()", event.getSourceSection().getCharacters());
                        Assert.assertEquals(6, event.getSourceSection().getStartLine());
                        checkStack(currentFrame, "n", "100", "m", "200", "x", "42");
                        if (doUnwind) {
                            event.prepareUnwindFrame(currentFrame);
                        }
                        suspendHits[0]++;
                    } catch (Exception ex) {
                        exception[0] = ex;
                    }
                });
                if (exception[0] != null) {
                    throw exception[0];
                }
                if (!doUnwind) {
                    break;
                }
                expectSuspended((SuspendedEvent event) -> {
                    DebugStackFrame currentFrame = event.getTopStackFrame();
                    try {
                        Assert.assertEquals("CALL(a)", currentFrame.getSourceSection().getCharacters());
                        Iterator<DebugStackFrame> frames = event.getStackFrames().iterator();
                        Assert.assertEquals("", frames.next().getName());
                        Assert.assertFalse(frames.hasNext());
                        checkStack(currentFrame);
                        // Enter into "a"
                        event.prepareStepInto(1);
                        suspendHits[0]++;
                    } catch (Exception ex) {
                        exception[0] = ex;
                    }
                });
                if (exception[0] != null) {
                    throw exception[0];
                }
            }
            expectDone();
            if (exception[0] != null) {
                throw exception[0];
            }
            Assert.assertEquals(3 * numRepeats - 1, suspendHits[0]);
        }
    }

}
