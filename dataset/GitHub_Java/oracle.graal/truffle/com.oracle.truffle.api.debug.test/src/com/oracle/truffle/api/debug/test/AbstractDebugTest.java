/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Framework for testing the Truffle {@linkplain Debugger Debugging API}.
 */
public abstract class AbstractDebugTest {
    private Debugger debugger;
    private final LinkedList<Runnable> eventResponders = new LinkedList<>();
    private SuspendedEvent suspendedEvent;
    private Throwable ex;
    private ExecutionEvent executionEvent;
    private PolyglotEngine engine;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    AbstractDebugTest() {
    }

    @Before
    public void before() {
        suspendedEvent = null;
        executionEvent = null;
        engine = PolyglotEngine.newBuilder().setOut(out).setErr(err).onEvent(new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
            @Override
            protected void on(ExecutionEvent event) {
                executionEvent = event;
                runNextResponder();
                executionEvent = null;
            }
        }).onEvent(new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
            @Override
            protected void on(SuspendedEvent event) {
                suspendedEvent = event;
                runNextResponder();
                suspendedEvent = null;
            }
        }).build();
        debugger = Debugger.find(engine);
        eventResponders.clear();
    }

    @After
    public void dispose() {
        if (engine != null) {
            engine.dispose();
            engine = null;
        }
    }

    protected final String getOut() {
        return new String(out.toByteArray());
    }

    protected final String getErr() {
        try {
            err.flush();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return new String(err.toByteArray());
    }

    protected final PolyglotEngine getEngine() {
        return engine;
    }

    protected final Debugger getDebugger() {
        return debugger;
    }

    /**
     * Creates a new builder that creates a work list to be performed upon receipt of the next event
     * which is asserted to be {@link SuspendedEvent}.
     * <p>
     * The work list is completed and added to the queue of responders only when a navigation is
     * specified, e.g. {@linkplain SuspendedEventResponder#stepInto(int) stepInto()} or
     * {@linkplain SuspendedEventResponder#resume resume()}.
     */
    protected final SuspendedEventResponder expectSuspendedEvent() {
        return new SuspendedEventResponder();
    }

    /**
     * Creates a new builder that creates a work list to be performed upon receipt of the next event
     * which is asserted to be {@link ExecutionEvent}.
     * <p>
     * The work list is completed and added to the queue of responders only when a navigation is
     * specified, e.g. {@linkplain SuspendedEventResponder#stepInto(int) stepInto()} or
     * {@linkplain SuspendedEventResponder#resume resume()}.
     */
    protected final ExecutionEventResponder expectExecutionEvent() {
        return new ExecutionEventResponder();
    }

    protected final class ExecutionEventResponder {

        private final List<Runnable> workList = new ArrayList<>();
        private boolean isComplete = false;

        ExecutionEventResponder() {
            workList.add(new Runnable() {

                public void run() {
                    assertNull(suspendedEvent);
                    assertNotNull(executionEvent);
                }
            });
        }

        /** Perform a task while responding to a {@link ExecutionEvent}. */
        ExecutionEventResponder run(Runnable work) {
            assert !isComplete : "responder has been completed";
            assert work != null;
            workList.add(work);
            return this;
        }

        /** Return from handling a {@link SuspendedEvent} by stepping. */
        void stepInto() {
            assert !isComplete : "responder has been completed";
            workList.add(new Runnable() {
                public void run() {
                    executionEvent.prepareStepInto();
                }
            });
            complete();
        }

        /** Return from handling a {@link SuspendedEvent}, allowing execution to continue. */
        void resume() {
            complete();
        }

        private void complete() {
            assert !isComplete : "responder has been completed";
            eventResponders.add(new Runnable() {
                public void run() {
                    for (Runnable work : workList) {
                        work.run();
                    }
                }
            });
            isComplete = true;
        }
    }

    protected final class SuspendedEventResponder {

        private final List<Runnable> workList = new ArrayList<>();
        private boolean isComplete = false;

        SuspendedEventResponder() {
            workList.add(new Runnable() {

                public void run() {
                    assertNotNull(suspendedEvent);
                    assertNull(executionEvent);
                }
            });
        }

        /** Assert facts about the execution state while responding to a {@link SuspendedEvent}. */
        SuspendedEventResponder checkState(final int expectedLineNumber, final boolean expectedIsBefore, final String expectedCode, final Object... expectedFrame) {
            assert !isComplete : "responder has been completed";
            workList.add(new Runnable() {
                public void run() {
                    final int actualLineNumber = suspendedEvent.getNode().getSourceSection().getLineLocation().getLineNumber();
                    Assert.assertEquals(expectedLineNumber, actualLineNumber);
                    final String actualCode = suspendedEvent.getNode().getSourceSection().getCode();
                    Assert.assertEquals(expectedCode, actualCode);
                    final boolean actualIsBefore = suspendedEvent.isHaltedBefore();
                    Assert.assertEquals(expectedIsBefore, actualIsBefore);
                    final MaterializedFrame frame = suspendedEvent.getFrame();

                    Assert.assertEquals(expectedFrame.length / 2, frame.getFrameDescriptor().getSize());

                    for (int i = 0; i < expectedFrame.length; i = i + 2) {
                        String expectedIdentifier = (String) expectedFrame[i];
                        Object expectedValue = expectedFrame[i + 1];
                        FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(expectedIdentifier);
                        Assert.assertNotNull(slot);
                        final Object actualValue = frame.getValue(slot);
                        Assert.assertEquals(expectedValue, actualValue);
                    }
                }
            });
            return this;
        }

        /** Perform a task while responding to a {@link SuspendedEvent}. */
        SuspendedEventResponder run(Runnable work) {
            assert !isComplete : "responder has been completed";
            assert work != null;
            workList.add(work);
            return this;
        }

        /** Return from handling a {@link SuspendedEvent} by stepping. */
        void stepInto(final int size) {
            assert !isComplete : "responder has been completed";
            workList.add(new Runnable() {
                public void run() {
                    suspendedEvent.prepareStepInto(size);
                }
            });
            complete();
        }

        /** Return from handling a {@link SuspendedEvent} by stepping. */
        void stepOver(final int size) {
            assert !isComplete : "responder has been completed";
            workList.add(new Runnable() {
                public void run() {
                    suspendedEvent.prepareStepOver(size);
                }
            });
            complete();
        }

        /** Return from handling a {@link SuspendedEvent} by stepping. */
        void stepOut() {
            assert !isComplete : "responder has been completed";
            workList.add(new Runnable() {
                public void run() {
                    suspendedEvent.prepareStepOut();
                }
            });
            complete();
        }

        /** Return from handling a {@link SuspendedEvent}, allowing execution to continue. */
        void resume() {
            complete();
        }

        private void complete() {
            assert !isComplete : "responder has been completed";
            eventResponders.add(new Runnable() {
                public void run() {
                    for (Runnable work : workList) {
                        work.run();
                    }
                }
            });
            isComplete = true;
        }
    }

    protected final void assertExecutedOK() throws Throwable {
        Assert.assertTrue(getErr(), getErr().isEmpty());
        if (ex != null) {
            if (ex instanceof AssertionError) {
                throw ex;
            } else {
                throw new AssertionError("Error during execution", ex);
            }
        }
        assertTrue("Assuming all requests processed: " + eventResponders, eventResponders.isEmpty());
    }

    private void runNextResponder() {
        try {
            if (ex == null && !eventResponders.isEmpty()) {
                Runnable c = eventResponders.removeFirst();
                c.run();
            }
        } catch (Throwable e) {
            ex = e;
        }
    }

}
