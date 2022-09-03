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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class ExecutionListenerTest extends AbstractPolyglotTest {

    final Deque<ExecutionEvent> events = new ArrayDeque<>();

    private void add(ExecutionEvent event) {
        assertNotNull(event.toString()); // does not crash
        events.add(event);
    }

    @Test
    public void testSourceEquality() {
        ExecutionEvent event;
        Source source;

        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true));
        source = eval("EXPRESSION");

        event = events.pop();
        assertEquals("EXPRESSION", event.getLocation().getCharacters());
        assertEquals(source, event.getLocation().getSource()); // events should share instances

        Source otherSource = eval("EXPRESSION");
        ExecutionEvent otherEvent = events.pop();
        assertSame(event, otherEvent); // events should share instances
        assertEquals(source, otherEvent.getLocation().getSource());
        assertEquals(otherSource, otherEvent.getLocation().getSource());

        assertTrue(events.isEmpty());
    }

    @Test
    public void testOnEnterExpression() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true));
        eval("EXPRESSION");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertFalse(event.isRoot());
        assertFalse(event.isStatement());
        assertTrue(event.isExpression());
        assertEquals("EXPRESSION", event.getLocation().getCharacters());

        eval("EXPRESSION");
        assertSame(event, events.pop()); // events should share instances

        eval("EXPRESSION(EXPRESSION, STATEMENT, EXPRESSION)");

        assertEquals(0, events.pop().getLocation().getCharIndex());
        assertEquals("EXPRESSION", events.pop().getLocation().getCharacters());
        assertEquals("EXPRESSION", events.pop().getLocation().getCharacters());
    }

    @Test
    public void testOnEnterStatement() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).statements(true));
        eval("STATEMENT");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertFalse(event.isRoot());
        assertTrue(event.isStatement());
        assertFalse(event.isExpression());
        assertEquals("STATEMENT", event.getLocation().getCharacters());

        eval("STATEMENT");
        assertSame(event, events.pop()); // events should share instances

        eval("STATEMENT(STATEMENT, EXPRESSION, STATEMENT)");

        assertEquals(0, events.pop().getLocation().getCharIndex());
        assertEquals("STATEMENT", events.pop().getLocation().getCharacters());
        assertEquals("STATEMENT", events.pop().getLocation().getCharacters());
    }

    @Test
    public void testOnEnterRoots() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).roots(true));
        eval("ROOT");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
        assertEquals("ROOT", event.getLocation().getCharacters());

        eval("ROOT");
        assertSame(event, events.pop()); // events should share instances

        eval("ROOT(ROOT, EXPRESSION, ROOT)");

        assertEquals(0, events.pop().getLocation().getCharIndex());
        assertEquals("ROOT", events.pop().getLocation().getCharacters());
        assertEquals("ROOT", events.pop().getLocation().getCharacters());
    }

    @Test
    public void testOnReturnExpressions() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true));
        eval("EXPRESSION");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertFalse(event.isRoot());
        assertFalse(event.isStatement());
        assertTrue(event.isExpression());
        assertEquals("EXPRESSION", event.getLocation().getCharacters());

        eval("EXPRESSION");
        assertSame(event, events.pop()); // events should share instances

        eval("EXPRESSION(EXPRESSION, STATEMENT, EXPRESSION)");

        assertEquals("EXPRESSION", events.pop().getLocation().getCharacters());
        assertEquals("EXPRESSION", events.pop().getLocation().getCharacters());
        assertEquals(0, events.pop().getLocation().getCharIndex());
    }

    @Test
    public void testOnReturnStatements() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).statements(true));
        eval("STATEMENT");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertFalse(event.isRoot());
        assertTrue(event.isStatement());
        assertFalse(event.isExpression());
        assertEquals("STATEMENT", event.getLocation().getCharacters());

        eval("STATEMENT");
        assertSame(event, events.pop()); // events should share instances

        eval("STATEMENT(STATEMENT, EXPRESSION, STATEMENT)");

        assertEquals("STATEMENT", events.pop().getLocation().getCharacters());
        assertEquals("STATEMENT", events.pop().getLocation().getCharacters());
        assertEquals(0, events.pop().getLocation().getCharIndex());
    }

    @Test
    public void testOnReturnRoots() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).roots(true));
        eval("ROOT");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
        assertEquals("ROOT", event.getLocation().getCharacters());

        eval("ROOT");
        assertSame(event, events.pop()); // events should share instances

        eval("ROOT(ROOT, EXPRESSION, ROOT)");

        assertEquals("ROOT", events.pop().getLocation().getCharacters());
        assertEquals("ROOT", events.pop().getLocation().getCharacters());
        assertEquals(0, events.pop().getLocation().getCharIndex());
    }

    @Test
    public void testOnErrorExpressions() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true));
        eval("TRY(EXPRESSION(THROW(error, message)), CATCH(error))");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertFalse(event.isRoot());
        assertFalse(event.isStatement());
        assertTrue(event.isExpression());
        assertEquals("EXPRESSION(THROW(error, message))", event.getLocation().getCharacters());

        String s = "EXPRESSION(TRY(EXPRESSION(THROW(error0, message)), CATCH(error0)), " +
                        "TRY(STATEMENT(THROW(error1, message)), CATCH(error1)), " +
                        "TRY(EXPRESSION(THROW(error2, message)), CATCH(error2)))";
        eval(s);

        assertEquals("EXPRESSION(THROW(error0, message))", events.pop().getLocation().getCharacters());
        assertEquals("EXPRESSION(THROW(error2, message))", events.pop().getLocation().getCharacters());
        assertEquals(s, events.pop().getLocation().getCharacters());
    }

    @Test
    public void testOnErrorStatements() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).statements(true));
        eval("TRY(STATEMENT(THROW(error, message)), CATCH(error))");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertFalse(event.isRoot());
        assertTrue(event.isStatement());
        assertFalse(event.isExpression());
        assertEquals("STATEMENT(THROW(error, message))", event.getLocation().getCharacters());

        eval("EXPRESSION(TRY(STATEMENT(THROW(error0, message)), CATCH(error0)), " +
                        "TRY(EXPRESSION(THROW(error1, message)), CATCH(error1)), " +
                        "TRY(STATEMENT(THROW(error2, message)), CATCH(error2)))");

        assertEquals("STATEMENT(THROW(error0, message))", events.pop().getLocation().getCharacters());
        assertEquals("STATEMENT(THROW(error2, message))", events.pop().getLocation().getCharacters());

    }

    @Test
    public void testOnErrorRoots() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).roots(true));
        eval("TRY(ROOT(THROW(error, message)), CATCH(error))");

        event = events.pop();
        assertNull(event.getException());
        assertNull(event.getInputValues());
        assertNull(event.getReturnValue());
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
        assertEquals("ROOT(THROW(error, message))", event.getLocation().getCharacters());
        assertEquals("TRY(ROOT(THROW(error, message)), CATCH(error))", events.pop().getLocation().getCharacters());

        String s = "EXPRESSION(TRY(ROOT(THROW(error0, message)), CATCH(error0)), " +
                        "TRY(EXPRESSION(THROW(error1, message)), CATCH(error1)), " +
                        "TRY(ROOT(THROW(error2, message)), CATCH(error2)))";
        eval(s);

        assertEquals("ROOT(THROW(error0, message))", events.pop().getLocation().getCharacters());
        assertEquals("ROOT(THROW(error2, message))", events.pop().getLocation().getCharacters());
        assertEquals(s, events.pop().getLocation().getCharacters());
    }

    @Test
    public void testCollectInputValues() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectInputValues(true));
        eval("EXPRESSION");

        event = events.pop();
        assertTrue(event.getInputValues().isEmpty());

        eval("EXPRESSION(EXPRESSION(CONSTANT(1)), EXPRESSION(EXPRESSION, EXPRESSION))");

        List<Value> inputs;
        assertTrue(events.pop().getInputValues().isEmpty());
        assertTrue(events.pop().getInputValues().isEmpty());
        assertTrue(events.pop().getInputValues().isEmpty());

        inputs = events.pop().getInputValues();
        assertEquals(2, inputs.size());
        assertEquals("()", inputs.get(0).asString());
        assertEquals("()", inputs.get(1).asString());

        inputs = events.pop().getInputValues();
        assertEquals(2, inputs.size());
        assertEquals("(1)", inputs.get(0).asString());
        assertEquals("(()+())", inputs.get(1).asString());
    }

    @Test
    public void testCollectInputValuesDisabled() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectInputValues(false));
        eval("EXPRESSION");

        event = events.pop();
        assertNull(event.getInputValues());
    }

    @Test
    public void testCollectInputValuesOnEnter() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true).collectInputValues(true));
        eval("EXPRESSION");

        event = events.pop();
        assertTrue(event.getInputValues().isEmpty());
    }

    @Test
    public void testCollectInputValuesOnError() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectInputValues(true));
        try {
            eval(" EXPRESSION(EXPRESSION, THROW(error, msg), EXPRESSION)");
        } catch (PolyglotException e) {
        }

        event = events.pop();
        assertEquals(0, event.getInputValues().size());

        event = events.pop();
        assertEquals(2, event.getInputValues().size());
        assertEquals("()", event.getInputValues().get(0).asString());
        assertNull(event.getInputValues().get(1));
    }

    @Test
    public void testCollectReturnValues() {
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectReturnValue(true));
        eval("EXPRESSION");

        assertEquals("()", events.pop().getReturnValue().asString());

        eval("EXPRESSION(EXPRESSION(CONSTANT(1)), EXPRESSION(EXPRESSION, EXPRESSION))");

        assertEquals("(1)", events.pop().getReturnValue().asString());
        assertEquals("()", events.pop().getReturnValue().asString());
        assertEquals("()", events.pop().getReturnValue().asString());
        assertEquals("(()+())", events.pop().getReturnValue().asString());
        assertEquals("((1)+(()+()))", events.pop().getReturnValue().asString());
    }

    @Test
    public void testCollectReturnValuesDisabled() {
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectReturnValue(false));
        eval("EXPRESSION");
        assertNull(events.pop().getReturnValue());
    }

    @Test
    public void testCollectReturnValuesOnEnter() {
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true).collectReturnValue(true));
        eval("EXPRESSION");
        assertNull(events.pop().getReturnValue());
    }

    @Test
    public void testCollectReturnValuesOnError() {
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectReturnValue(true));
        PolyglotException thrownError = null;
        try {
            eval("EXPRESSION(THROW(foo, msg))");
        } catch (PolyglotException e) {
            thrownError = e;
        }
        assertNotNull(thrownError);
        assertNull(events.pop().getReturnValue());
    }

    @Test
    public void testCollectErrors() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectExceptions(true));
        PolyglotException thrownError = null;
        try {
            eval("EXPRESSION(THROW(foo, msg))");
        } catch (PolyglotException e) {
            thrownError = e;
        }

        assertNotNull(thrownError);
        event = events.pop();
        assertNotNull(event.getException());
        assertNotSame(event.getException(), thrownError);
        assertEquals(event.getException(), thrownError);
        assertNull(event.getException().getCause());
        assertEquals("msg", event.getException().getMessage());
        assertEquals("THROW(foo, msg)", event.getException().getSourceLocation().getCharacters());
        assertEquals("foo: msg", event.getException().getGuestObject().asString());

        List<PolyglotException.StackFrame> stackFrames = new ArrayList<>();
        event.getException().getPolyglotStackTrace().forEach(stackFrames::add);
        assertEquals(context.getEngine().getLanguages().get(InstrumentationTestLanguage.ID), stackFrames.get(0).getLanguage());
        assertFalse(stackFrames.get(0).isHostFrame());
        assertEquals(event.getException().getSourceLocation(), stackFrames.get(0).getSourceLocation());
        assertTrue(stackFrames.get(0).isGuestFrame());
        // assert trailing host frames
        for (int i = 1; i < stackFrames.size(); i++) {
            assertTrue(stackFrames.get(i).isHostFrame());
        }

        try {
            eval("EXPRESSION(THROW(internal, msg))");
        } catch (PolyglotException e) {
            thrownError = e;
        }

        event = events.pop();
        assertNotNull(event.getException());
        assertNotSame(event.getException(), thrownError);
        assertEquals(event.getException(), thrownError);
        assertNull(event.getException().getCause());
        assertTrue(event.getException().isInternalError());
        assertEquals("internal: msg", event.getException().getGuestObject().asString());
    }

    @Test
    public void testCollectErrorsOnReturn() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectExceptions(true));
        eval("EXPRESSION");
        event = events.pop();
        assertNull(event.getException());
    }

    @Test
    public void testCollectErrorsOnEnter() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true).collectExceptions(true));
        PolyglotException thrownError = null;
        try {
            eval("EXPRESSION(THROW(foo, msg))");
        } catch (PolyglotException e) {
            thrownError = e;
        }
        assertNotNull(thrownError);
        event = events.pop();
        assertNull(event.getException());
    }

    @Test
    public void testCollectErrorsDisabled() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onReturn(this::add).expressions(true).collectExceptions(false));
        PolyglotException thrownError = null;
        try {
            eval("EXPRESSION(THROW(foo, msg))");
        } catch (PolyglotException e) {
            thrownError = e;
        }
        assertNotNull(thrownError);
        event = events.pop();
        assertNull(event.getException());
    }

    @Test
    public void testCombinedElements() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true));
        eval("MULTIPLE[EXPRESSION, STATEMENT, ROOT]");
        event = events.pop();
        assertTrue(event.isExpression());
        assertTrue(event.isStatement());
        assertTrue(event.isRoot());

        setupListener(ExecutionListener.newBuilder().onEnter(this::add).statements(true));
        eval("MULTIPLE[EXPRESSION, STATEMENT, ROOT]");
        event = events.pop();
        assertTrue(event.isExpression());
        assertTrue(event.isStatement());
        assertTrue(event.isRoot());

        setupListener(ExecutionListener.newBuilder().onEnter(this::add).roots(true));
        eval("ROOT(MULTIPLE[EXPRESSION, STATEMENT, ROOT])");
        assertTrue(events.pop().isRoot()); // discard first root event
        event = events.pop();
        assertTrue(event.isExpression());
        assertTrue(event.isStatement());
        assertTrue(event.isRoot());

        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true).statements(true).roots(true));
        eval("ROOT(MULTIPLE[EXPRESSION, STATEMENT, ROOT])");
        assertTrue(events.pop().isRoot()); // discard first root event
        event = events.pop();
        assertTrue(event.isExpression());
        assertTrue(event.isStatement());
        assertTrue(event.isRoot());
    }

    @Test
    public void testSourceFilter() {
        ExecutionEvent event;
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).expressions(true).sourceFilter((s) -> s.getName().equals("test1")));

        Source source0 = Source.newBuilder(InstrumentationTestLanguage.ID, "EXPRESSION", "test0").buildLiteral();
        Source source1 = Source.newBuilder(InstrumentationTestLanguage.ID, "EXPRESSION", "test1").buildLiteral();

        context.eval(source0);
        context.eval(source1);
        event = events.pop();
        assertTrue(event.isExpression());
        assertEquals("test1", event.getLocation().getSource().getName());

        assertTrue(events.isEmpty());
    }

    @Test
    public void testDispose() {
        ExecutionListener listener = setupListener(ExecutionListener.newBuilder().onEnter(events::add).expressions(true));

        eval("EXPRESSION");

        assertTrue(events.pop().isExpression());
        listener.close();

        eval("EXPRESSION");

        assertTrue(events.isEmpty());
    }

    @Test
    public void testParallelAttachDispose() throws InterruptedException, ExecutionException {
        setupEnv(Context.create());
        ExecutorService service = Executors.newFixedThreadPool(10);
        context.leave();
        Future<?> code = service.submit(() -> eval("LOOP(infinity, EXPRESSION)"));

        List<Future<ExecutionListener>> attachTasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Future<ExecutionListener> attachTask = service.submit(() -> ExecutionListener.newBuilder().onEnter((e) -> {
            }).expressions(true).attach(context.getEngine()));
            attachTasks.add(attachTask);
        }

        Thread.sleep(100);

        List<Future<?>> closingTasks = new ArrayList<>();
        for (Future<ExecutionListener> attachTask : attachTasks) {
            ExecutionListener listener = attachTask.get();
            closingTasks.add(service.submit(() -> listener.close()));
        }

        for (Future<?> task : closingTasks) {
            task.get();
        }
        ExecutionListener.newBuilder().onEnter((e) -> {
            throw new RuntimeException("");
        }).expressions(true).attach(context.getEngine());

        try {
            code.get();
            fail("exception expected");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof PolyglotException);
            assertTrue(((PolyglotException) e.getCause()).asHostException() instanceof RuntimeException);
        }

        service.shutdown();

        context.enter();
    }

    @Test
    public void testRootName() {
        setupListener(ExecutionListener.newBuilder().onEnter(this::add).rootNameFilter(
                        (s) -> s.equals("foo") || s.equals("bar")).expressions(true));
        eval("DEFINE(foo, EXPRESSION)");
        eval("DEFINE(bar, EXPRESSION)");
        eval("DEFINE(baz, EXPRESSION)");

        eval("CALL(foo)");
        assertEquals("foo", events.pop().getRootName());
        eval("CALL(bar)");
        assertEquals("bar", events.pop().getRootName());
        eval("CALL(baz)");
    }

    @Test
    public void testErrorInRootName() {
        ExecutionListener listener = setupListener(ExecutionListener.newBuilder().onEnter(this::add).rootNameFilter(
                        (s) -> {
                            throw new RuntimeException();
                        }).expressions(true));
        try {
            eval("DEFINE(foo, EXPRESSION)");
            eval("CALL(foo)");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }

        // listener must be closable even though the filter has errors.
        listener.close();

        ExecutionListener.Builder builder = ExecutionListener.newBuilder().onEnter(this::add).expressions(true).rootNameFilter((s) -> {
            throw new RuntimeException();
        });
        try {
            builder.attach(context.getEngine());
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }
    }

    @Test
    public void testErrorInSourceFilter() {
        ExecutionListener listener = setupListener(ExecutionListener.newBuilder().onEnter(this::add).sourceFilter(
                        (s) -> {
                            throw new RuntimeException();
                        }).expressions(true));
        try {
            eval("DEFINE(foo, EXPRESSION)");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }

        // listener must be closable even though the filter has errors.
        listener.close();

        ExecutionListener.Builder builder = ExecutionListener.newBuilder().onEnter(this::add).expressions(true).sourceFilter((s) -> {
            throw new RuntimeException();
        });
        try {
            builder.attach(context.getEngine());
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }
    }

    @Test
    public void testErrorInOnEnter() {
        setupListener(ExecutionListener.newBuilder().onEnter((e) -> {
            throw new RuntimeException();
        }).expressions(true));

        try {
            eval("EXPRESSION");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }

        setupListener(ExecutionListener.newBuilder().onEnter((e) -> {
            throw new RuntimeException();
        }).expressions(true).collectExceptions(true).collectInputValues(true).collectReturnValue(true));

        try {
            eval("EXPRESSION");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }
    }

    @Test
    public void testErrorInOnReturn() {
        setupListener(ExecutionListener.newBuilder().onReturn((e) -> {
            throw new RuntimeException();
        }).expressions(true));

        try {
            eval("EXPRESSION");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }

        setupListener(ExecutionListener.newBuilder().onReturn((e) -> {
            throw new RuntimeException();
        }).expressions(true).collectExceptions(true).collectInputValues(true).collectReturnValue(true));

        try {
            eval("EXPRESSION");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }
    }

    @Test
    public void testErrorInOnError() {
        setupListener(ExecutionListener.newBuilder().onReturn((e) -> {
            throw new RuntimeException();
        }).expressions(true));

        try {
            eval("TRY(EXPRESSION(THROW(error, message)), CATCH(error))");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
        }

        setupListener(ExecutionListener.newBuilder().onReturn((e) -> {
            throw new RuntimeException(e.getException());
        }).expressions(true).collectExceptions(true).collectInputValues(true).collectReturnValue(true));

        try {
            eval("TRY(EXPRESSION(THROW(error, message)), CATCH(error))");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.asHostException() instanceof RuntimeException);
            assertTrue(e.asHostException().getCause() instanceof PolyglotException);
            assertEquals("message", ((PolyglotException) e.asHostException().getCause()).getMessage());
        }
    }

    @Test
    public void testInvalidBuilder() {
        Context ctx = Context.create();
        try {
            ExecutionListener.newBuilder().attach(ctx.getEngine());
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            ExecutionListener.newBuilder().onEnter((e) -> {
            }).attach(ctx.getEngine());
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            ExecutionListener.newBuilder().statements(true).attach(ctx.getEngine());
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            ExecutionListener.newBuilder().expressions(true).attach(ctx.getEngine());
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            ExecutionListener.newBuilder().roots(true).attach(ctx.getEngine());
            fail();
        } catch (IllegalArgumentException e) {
        }
        ctx.close();
    }

    private Source eval(String code) {
        assertTrue("Previous events are not consumed", events.isEmpty());

        Source source = Source.create(InstrumentationTestLanguage.ID, code);
        context.eval(source);
        return source;
    }

    private ExecutionListener setupListener(ExecutionListener.Builder listenerBuilder) {
        Context c = Context.create();
        ExecutionListener listener = listenerBuilder.attach(c.getEngine());
        setupEnv(c);
        return listener;
    }

    @After
    public void tearDown() {
        assertTrue("Previous events are not consumed", events.isEmpty());
    }

}
