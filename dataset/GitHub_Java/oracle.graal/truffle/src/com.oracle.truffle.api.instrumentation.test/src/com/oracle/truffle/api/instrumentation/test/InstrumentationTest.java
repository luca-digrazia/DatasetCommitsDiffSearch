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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotRuntime;
import com.oracle.truffle.api.vm.PolyglotRuntime.Instrument;
import com.oracle.truffle.api.instrumentation.AllocationListener;

public class InstrumentationTest extends AbstractInstrumentationTest {

    /*
     * Test that metadata is properly propagated to Instrument handles.
     */
    @Test
    public void testMetadata() {
        PolyglotRuntime.Instrument instrumentHandle1 = engine.getRuntime().getInstruments().get("testMetadataType1");

        Assert.assertEquals("name", instrumentHandle1.getName());
        Assert.assertEquals("version", instrumentHandle1.getVersion());
        Assert.assertEquals("testMetadataType1", instrumentHandle1.getId());
        Assert.assertFalse(instrumentHandle1.isEnabled());
    }

    @Registration(name = "name", version = "version", id = "testMetadataType1")
    public static class MetadataInstrument extends TruffleInstrument {
        @Override
        protected void onCreate(Env env) {
        }
    }

    @Registration(name = "name", version = "version", id = "testBrokenRegistration", services = Runnable.class)
    public static class BrokenRegistrationInstrument extends TruffleInstrument {
        @Override
        protected void onCreate(Env env) {
        }
    }

    @Test
    public void forgetsToRegisterADeclaredService() throws Exception {
        PolyglotRuntime.Instrument handle = engine.getRuntime().getInstruments().get("testBrokenRegistration");
        assertNotNull(handle);
        handle.setEnabled(true);
        Runnable r = handle.lookup(Runnable.class);
        assertNull("The service isn't there", r);
        if (!err.toString().contains("declares service java.lang.Runnable but doesn't register it")) {
            fail(err.toString());
        }
    }

    @Registration(name = "name", version = "version", id = "beforeUse", services = Runnable.class)
    public static class BeforeUseInstrument extends TruffleInstrument implements Runnable {
        private Env env;

        @Override
        protected void onCreate(Env anEnv) {
            this.env = anEnv;
            this.env.registerService(this);
        }

        @Override
        public void run() {
            LanguageInfo info = env.getLanguages().get(InstrumentationTestLanguage.MIME_TYPE);
            SpecialService ss = env.lookup(info, SpecialService.class);
            assertNotNull("Service found", ss);
            assertEquals("The right extension", ss.fileExtension(), InstrumentationTestLanguage.FILENAME_EXTENSION);

            assertNull("Can't query object", env.lookup(info, Object.class));
            assertNull("Can't query language", env.lookup(info, TruffleLanguage.class));
        }

    }

    @Test
    public void queryInstrumentsBeforeUseAndObtainSpecialService() throws Exception {
        final PolyglotRuntime runtime = PolyglotRuntime.newBuilder().setErr(err).build();
        Runnable start = null;
        for (PolyglotRuntime.Instrument instr : runtime.getInstruments().values()) {
            Runnable r = instr.lookup(Runnable.class);
            if (r != null) {
                start = r;
                start.run();
                assertTrue("Now enabled: " + instr, instr.isEnabled());
            }
        }
        assertNotNull("At least one Runnable found", start);
    }

    @Test
    public void queryInstrumentsAfterDisposeDoesnotEnable() throws Exception {
        final PolyglotRuntime runtime = PolyglotRuntime.newBuilder().build();
        runtime.dispose();
        Runnable start = null;
        for (PolyglotRuntime.Instrument instr : runtime.getInstruments().values()) {
            assertFalse("Instrument is disabled", instr.isEnabled());
            instr.setEnabled(true);
            assertFalse("Instrument cannot be enabled", instr.isEnabled());

            Runnable r = instr.lookup(Runnable.class);
            if (r != null) {
                start = r;
                start.run();
                assertTrue("Now enabled: " + instr, instr.isEnabled());
            }
            assertFalse("Instrument left disabled", instr.isEnabled());
        }
        assertNull("No Runnable found", start);
    }

    /*
     * Test that metadata is properly propagated to Instrument handles.
     */
    @Test
    public void testDefaultId() {
        PolyglotRuntime.Instrument descriptor1 = engine.getRuntime().getInstruments().get(MetadataInstrument2.class.getSimpleName());
        Assert.assertEquals("", descriptor1.getName());
        Assert.assertEquals("", descriptor1.getVersion());
        Assert.assertEquals(MetadataInstrument2.class.getSimpleName(), descriptor1.getId());
        Assert.assertFalse(descriptor1.isEnabled());
    }

    @Registration
    public static class MetadataInstrument2 extends TruffleInstrument {
        @Override
        protected void onCreate(Env env) {
        }
    }

    /*
     * Test onCreate and onDispose invocations for multiple instrument instances.
     */
    @Test
    public void testMultipleInstruments() throws IOException {
        run(""); // initialize

        MultipleInstanceInstrument.onCreateCounter = 0;
        MultipleInstanceInstrument.onDisposeCounter = 0;
        MultipleInstanceInstrument.constructor = 0;
        PolyglotRuntime.Instrument instrument1 = engine.getRuntime().getInstruments().get("testMultipleInstruments");
        instrument1.setEnabled(true);
        Assert.assertEquals(1, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(1, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(0, MultipleInstanceInstrument.onDisposeCounter);

        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testMultipleInstruments");
        instrument.setEnabled(true);
        Assert.assertEquals(1, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(1, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(0, MultipleInstanceInstrument.onDisposeCounter);

        instrument.setEnabled(false);
        Assert.assertEquals(1, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(1, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(1, MultipleInstanceInstrument.onDisposeCounter);

        instrument.setEnabled(true);
        Assert.assertEquals(2, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(2, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(1, MultipleInstanceInstrument.onDisposeCounter);

        instrument.setEnabled(false);
        Assert.assertEquals(2, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(2, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(2, MultipleInstanceInstrument.onDisposeCounter);
    }

    @Registration(id = "testMultipleInstruments")
    public static class MultipleInstanceInstrument extends TruffleInstrument {

        private static int onCreateCounter = 0;
        private static int onDisposeCounter = 0;
        private static int constructor = 0;

        public MultipleInstanceInstrument() {
            constructor++;
        }

        @Override
        protected void onCreate(Env env) {
            onCreateCounter++;
        }

        @Override
        protected void onDispose(Env env) {
            onDisposeCounter++;
        }
    }

    /*
     * Test exceptions from language instrumentation are not wrapped into InstrumentationExceptions.
     * Test that one language cannot instrument another.
     */
    @Test
    public void testLanguageInstrumentationAndExceptions() throws IOException {
        TestLanguageInstrumentationLanguage.installInstrumentsCounter = 0;
        TestLanguageInstrumentationLanguage.createContextCounter = 0;
        try {
            engine.eval(Source.newBuilder("ROOT(EXPRESSION)").name("unknown").mimeType("testLanguageInstrumentation").build());
            Assert.fail("expected exception");
        } catch (MyLanguageException e) {
            // we assert that MyLanguageException is not wrapped
        }
        Assert.assertEquals(1, TestLanguageInstrumentationLanguage.installInstrumentsCounter);
        Assert.assertEquals(1, TestLanguageInstrumentationLanguage.createContextCounter);

        // this should run isolated from the language instrumentation.
        run("STATEMENT");
    }

    @SuppressWarnings("serial")
    private static class MyLanguageException extends RuntimeException {

    }

    @TruffleLanguage.Registration(name = "", version = "", mimeType = "testLanguageInstrumentation")
    @ProvidedTags({InstrumentationTestLanguage.ExpressionNode.class, StandardTags.StatementTag.class})
    public static class TestLanguageInstrumentationLanguage extends InstrumentationTestLanguage {

        static int installInstrumentsCounter = 0;
        static int createContextCounter = 0;

        public TestLanguageInstrumentationLanguage() {
        }

        private static void installInstruments(Instrumenter instrumenter) {
            installInstrumentsCounter++;
            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    // since we are a language instrumentation we can throw exceptions
                    // without getting wrapped into Instrumentation exception.
                    throw new MyLanguageException();
                }
            });

            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventListener() {
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    throw new AssertionError();
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    throw new AssertionError();
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    throw new AssertionError();
                }
            });
        }

        @Override
        protected Context createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            createContextCounter++;
            Instrumenter instrumenter = env.lookup(Instrumenter.class);
            Assert.assertNotNull("Instrumenter found", instrumenter);
            installInstruments(instrumenter);
            return super.createContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Child private BaseNode base = parse(request.getSource());

                @Override
                public Object execute(VirtualFrame frame) {
                    return base.execute(frame);
                }
            });
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

    @Test
    public void testInstrumentException1() {
        engine.getRuntime().getInstruments().get("testInstrumentException1").setEnabled(true);

        Assert.assertTrue(getErr().contains("MyLanguageException"));
    }

    @Registration(name = "", version = "", id = "testInstrumentException1")
    public static class TestInstrumentException1 extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            throw new MyLanguageException();
        }

        @Override
        protected void onDispose(Env env) {
        }
    }

    /*
     * We test that instrumentation exceptions are wrapped, onReturnExceptional is invoked properly
     * and not onReturnValue,
     */
    @Test
    public void testInstrumentException2() throws IOException {
        TestInstrumentException2.returnedExceptional = 0;
        TestInstrumentException2.returnedValue = 0;
        engine.getRuntime().getInstruments().get("testInstrumentException2").setEnabled(true);
        run("ROOT(EXPRESSION)");
        Assert.assertTrue(getErr().contains("MyLanguageException"));

        Assert.assertEquals(0, TestInstrumentException2.returnedExceptional);
        Assert.assertEquals(1, TestInstrumentException2.returnedValue);
    }

    @Registration(name = "", version = "", id = "testInstrumentException2")
    public static class TestInstrumentException2 extends TruffleInstrument {

        static int returnedExceptional = 0;
        static int returnedValue = 0;

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    returnedValue++;
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    returnedExceptional++;
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    throw new MyLanguageException();
                }
            });
        }

        @Override
        protected void onDispose(Env env) {
        }
    }

    /*
     * Test that instrumentation exceptions in the onReturnExceptional are attached as suppressed
     * exceptions.
     */
    @Test
    public void testInstrumentException3() throws IOException {
        TestInstrumentException3.returnedExceptional = 0;
        TestInstrumentException3.onEnter = 0;
        engine.getRuntime().getInstruments().get("testInstrumentException3").setEnabled(true);
        run("ROOT(EXPRESSION)");
        Assert.assertTrue(getErr().contains("MyLanguageException"));
        Assert.assertEquals(0, TestInstrumentException3.returnedExceptional);
        Assert.assertEquals(1, TestInstrumentException3.onEnter);
    }

    @Registration(name = "", version = "", id = "testInstrumentException3")
    public static class TestInstrumentException3 extends TruffleInstrument {

        static int returnedExceptional = 0;
        static int onEnter = 0;

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    throw new MyLanguageException();
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    returnedExceptional++;
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    onEnter++;
                }
            });
        }

    }

    /*
     * Test that event nodes are created lazily on first execution.
     */
    @Test
    public void testLazyProbe1() throws IOException {
        TestLazyProbe1.createCalls = 0;
        TestLazyProbe1.onEnter = 0;
        TestLazyProbe1.onReturnValue = 0;
        TestLazyProbe1.onReturnExceptional = 0;

        engine.getRuntime().getInstruments().get("testLazyProbe1").setEnabled(true);
        run("ROOT(DEFINE(foo, EXPRESSION))");
        run("ROOT(DEFINE(bar, ROOT(EXPRESSION,EXPRESSION)))");

        Assert.assertEquals(0, TestLazyProbe1.createCalls);
        Assert.assertEquals(0, TestLazyProbe1.onEnter);
        Assert.assertEquals(0, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(foo))");

        Assert.assertEquals(1, TestLazyProbe1.createCalls);
        Assert.assertEquals(1, TestLazyProbe1.onEnter);
        Assert.assertEquals(1, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(bar))");

        Assert.assertEquals(3, TestLazyProbe1.createCalls);
        Assert.assertEquals(3, TestLazyProbe1.onEnter);
        Assert.assertEquals(3, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(bar))");

        Assert.assertEquals(3, TestLazyProbe1.createCalls);
        Assert.assertEquals(5, TestLazyProbe1.onEnter);
        Assert.assertEquals(5, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(foo))");

        Assert.assertEquals(3, TestLazyProbe1.createCalls);
        Assert.assertEquals(6, TestLazyProbe1.onEnter);
        Assert.assertEquals(6, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

    }

    @Registration(name = "", version = "", id = "testLazyProbe1")
    public static class TestLazyProbe1 extends TruffleInstrument {

        static int createCalls = 0;
        static int onEnter = 0;
        static int onReturnValue = 0;
        static int onReturnExceptional = 0;

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    createCalls++;
                    return new ExecutionEventNode() {
                        @Override
                        public void onReturnValue(VirtualFrame frame, Object result) {
                            onReturnValue++;
                        }

                        @Override
                        public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                            onReturnExceptional++;
                        }

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onEnter++;
                        }
                    };
                }
            });
        }
    }

    /*
     * Test that parsing and executing foreign languages work.
     */
    @Test
    public void testEnvParse1() throws IOException {
        TestEnvParse1.onExpression = 0;
        TestEnvParse1.onStatement = 0;

        engine.getRuntime().getInstruments().get("testEnvParse1").setEnabled(true);
        run("STATEMENT");

        Assert.assertEquals(1, TestEnvParse1.onExpression);
        Assert.assertEquals(1, TestEnvParse1.onStatement);

        run("STATEMENT");

        Assert.assertEquals(2, TestEnvParse1.onExpression);
        Assert.assertEquals(2, TestEnvParse1.onStatement);
    }

    @Registration(name = "", version = "", id = "testEnvParse1")
    public static class TestEnvParse1 extends TruffleInstrument {

        static int onExpression = 0;
        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {

                    final CallTarget target;
                    try {
                        target = env.parse(Source.newBuilder("EXPRESSION").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build());
                    } catch (Exception e) {
                        throw new AssertionError();
                    }

                    return new ExecutionEventNode() {
                        @Child private DirectCallNode directCall = Truffle.getRuntime().createDirectCallNode(target);

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onStatement++;
                            directCall.call(new Object[0]);
                        }

                    };
                }
            });

            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    onExpression++;
                }
            });

        }
    }

    /*
     * Test that parsing and executing foreign languages with context work.
     */
    @Test
    public void testEnvParse2() throws IOException {
        TestEnvParse2.onExpression = 0;
        TestEnvParse2.onStatement = 0;

        engine.getRuntime().getInstruments().get("testEnvParse2").setEnabled(true);
        run("STATEMENT");

        Assert.assertEquals(1, TestEnvParse2.onExpression);
        Assert.assertEquals(1, TestEnvParse2.onStatement);

        run("STATEMENT");

        Assert.assertEquals(2, TestEnvParse2.onExpression);
        Assert.assertEquals(2, TestEnvParse2.onStatement);
    }

    @Registration(name = "", version = "", id = "testEnvParse2")
    public static class TestEnvParse2 extends TruffleInstrument {

        static int onExpression = 0;
        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {

                    final CallTarget target;
                    try {
                        target = context.parseInContext(Source.newBuilder("EXPRESSION").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build());
                    } catch (IOException e) {
                        throw new AssertionError();
                    }

                    return new ExecutionEventNode() {
                        @Child private DirectCallNode directCall = Truffle.getRuntime().createDirectCallNode(target);

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onStatement++;
                            directCall.call(new Object[0]);
                        }

                    };
                }
            });

            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    onExpression++;
                }
            });

        }
    }

    /*
     * Test instrument all with any filter. Ensure that root nodes are not tried to be instrumented.
     */
    @Test
    public void testInstrumentAll() throws IOException {
        TestInstrumentAll1.onStatement = 0;

        engine.getRuntime().getInstruments().get("testInstrumentAll").setEnabled(true);
        run("STATEMENT");

        Assert.assertEquals(1, TestInstrumentAll1.onStatement);
    }

    @Registration(id = "testInstrumentAll")
    public static class TestInstrumentAll1 extends TruffleInstrument {

        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    onStatement++;
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }
            });
        }
    }

    /*
     * Define is not instrumentable but has a source section.
     */
    @Test
    public void testInstrumentNonInstrumentable() throws IOException {
        TestInstrumentNonInstrumentable1.onStatement = 0;

        engine.getRuntime().getInstruments().get("testInstrumentNonInstrumentable").setEnabled(true);
        run("DEFINE(foo, ROOT())");

        Assert.assertEquals(0, TestInstrumentNonInstrumentable1.onStatement);
    }

    @Registration(id = "testInstrumentNonInstrumentable")
    public static class TestInstrumentNonInstrumentable1 extends TruffleInstrument {

        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    onStatement++;
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }
            });
        }
    }

    @Test
    public void testOutputConsumer() throws IOException {
        // print without instruments
        String rout = run("PRINT(OUT, InitialToStdOut)");
        Assert.assertEquals("InitialToStdOut", rout);
        run("PRINT(ERR, InitialToStdErr)");
        Assert.assertEquals("InitialToStdErr", err.toString());
        err.reset();

        // turn instruments on
        engine.getRuntime().getInstruments().get("testOutputConsumerArray").setEnabled(true);
        engine.getRuntime().getInstruments().get("testOutputConsumerPiped").setEnabled(true);
        engine.eval(lines("PRINT(OUT, OutputToStdOut)"));
        engine.eval(lines("PRINT(ERR, OutputToStdErr)"));
        // test that the output goes eveywhere
        Assert.assertEquals("OutputToStdOut", getOut());
        Assert.assertEquals("OutputToStdOut", TestOutputConsumerArray.getOut());
        Assert.assertEquals("OutputToStdErr", getErr());
        Assert.assertEquals("OutputToStdErr", TestOutputConsumerArray.getErr());
        CharBuffer buff = CharBuffer.allocate(100);
        TestOutputConsumerPiped.fromOut.read(buff);
        buff.flip();
        Assert.assertEquals("OutputToStdOut", buff.toString());
        buff.rewind();
        TestOutputConsumerPiped.fromErr.read(buff);
        buff.flip();
        Assert.assertEquals("OutputToStdErr", buff.toString());
        buff.rewind();

        // close piped err stream and test that print still works
        TestOutputConsumerPiped.fromErr.close();
        engine.eval(lines("PRINT(OUT, MoreOutputToStdOut)"));
        engine.eval(lines("PRINT(ERR, MoreOutputToStdErr)"));
        Assert.assertEquals("OutputToStdOutMoreOutputToStdOut", out.toString());
        Assert.assertEquals("OutputToStdOutMoreOutputToStdOut", TestOutputConsumerArray.getOut());
        String errorMsg = "java.lang.Exception: Output operation write(B[II) failed for java.io.PipedOutputStream";
        Assert.assertTrue(err.toString(), err.toString().startsWith("OutputToStdErr" + errorMsg));
        Assert.assertTrue(err.toString(), err.toString().endsWith("MoreOutputToStdErr"));
        Assert.assertEquals("OutputToStdErrMoreOutputToStdErr", TestOutputConsumerArray.getErr());
        buff.limit(buff.capacity());
        TestOutputConsumerPiped.fromOut.read(buff);
        buff.flip();
        Assert.assertEquals("MoreOutputToStdOut", buff.toString());
        out.reset();
        err.reset();

        // the I/O error is not printed again
        engine.eval(lines("PRINT(ERR, EvenMoreOutputToStdErr)"));
        Assert.assertEquals("EvenMoreOutputToStdErr", err.toString());
        Assert.assertEquals("OutputToStdErrMoreOutputToStdErrEvenMoreOutputToStdErr", TestOutputConsumerArray.getErr());

        // instruments disabled
        engine.getRuntime().getInstruments().get("testOutputConsumerArray").setEnabled(false);
        engine.getRuntime().getInstruments().get("testOutputConsumerPiped").setEnabled(false);
        out.reset();
        err.reset();
        engine.eval(lines("PRINT(OUT, FinalOutputToStdOut)"));
        engine.eval(lines("PRINT(ERR, FinalOutputToStdErr)"));
        Assert.assertEquals("FinalOutputToStdOut", out.toString());
        Assert.assertEquals("FinalOutputToStdErr", err.toString());
        // nothing more printed to the disabled instrument
        Assert.assertEquals("OutputToStdOutMoreOutputToStdOut", TestOutputConsumerArray.getOut());
        Assert.assertEquals("OutputToStdErrMoreOutputToStdErrEvenMoreOutputToStdErr", TestOutputConsumerArray.getErr());
    }

    @Registration(id = "testOutputConsumerArray")
    public static class TestOutputConsumerArray extends TruffleInstrument {

        static ByteArrayOutputStream out = new ByteArrayOutputStream();
        static ByteArrayOutputStream err = new ByteArrayOutputStream();

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachOutConsumer(out);
            env.getInstrumenter().attachErrConsumer(err);
        }

        static String getOut() {
            return new String(out.toByteArray());
        }

        static String getErr() {
            return new String(err.toByteArray());
        }
    }

    @Registration(id = "testOutputConsumerPiped")
    public static class TestOutputConsumerPiped extends TruffleInstrument {

        static PipedOutputStream out = new PipedOutputStream();
        static Reader fromOut;
        static PipedOutputStream err = new PipedOutputStream();
        static Reader fromErr;

        public TestOutputConsumerPiped() throws IOException {
            fromOut = new InputStreamReader(new PipedInputStream(out));
            fromErr = new InputStreamReader(new PipedInputStream(err));
        }

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachOutConsumer(out);
            env.getInstrumenter().attachErrConsumer(err);
        }

        Reader fromOut() {
            return fromOut;
        }

        Reader fromErr() {
            return fromErr;
        }
    }

    /*
     * Tests for debugger or any other clients that cancel execution while halted
     */

    @Test
    public void testKillExceptionOnEnter() throws IOException {
        engine.getRuntime().getInstruments().get("testKillQuitException").setEnabled(true);
        TestKillQuitException.exceptionOnEnter = new MyKillException();
        TestKillQuitException.exceptionOnReturnValue = null;
        TestKillQuitException.returnExceptionalCount = 0;
        try {
            run("STATEMENT");
            Assert.fail("KillException in onEnter() cancels engine execution");
        } catch (MyKillException ex) {
        }
        Assert.assertEquals("KillException is not an execution event", 0, TestKillQuitException.returnExceptionalCount);
    }

    @Test
    public void testKillExceptionOnReturnValue() throws IOException {
        engine.getRuntime().getInstruments().get("testKillQuitException").setEnabled(true);
        TestKillQuitException.exceptionOnEnter = null;
        TestKillQuitException.exceptionOnReturnValue = new MyKillException();
        TestKillQuitException.returnExceptionalCount = 0;
        try {
            run("STATEMENT");
            Assert.fail("KillException in onReturnValue() cancels engine execution");
        } catch (MyKillException ex) {
        }
        Assert.assertEquals("KillException is not an execution event", 0, TestKillQuitException.returnExceptionalCount);
    }

    @Registration(id = "testKillQuitException")
    public static class TestKillQuitException extends TruffleInstrument {

        static Error exceptionOnEnter = null;
        static Error exceptionOnReturnValue = null;
        static int returnExceptionalCount = 0;

        @Override
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    if (exceptionOnEnter != null) {
                        throw exceptionOnEnter;
                    }
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    if (exceptionOnReturnValue != null) {
                        throw exceptionOnReturnValue;
                    }
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    returnExceptionalCount++;
                }
            });
        }
    }

    /*
     * Use tags that are not declarded as required.
     */
    @Test
    public void testUsedTagNotRequired1() throws IOException {
        TestInstrumentNonInstrumentable1.onStatement = 0;

        engine.getRuntime().getInstruments().get("testUsedTagNotRequired1").setEnabled(true);
        run("ROOT()");

        Assert.assertEquals(0, TestInstrumentNonInstrumentable1.onStatement);
    }

    @Registration(id = "testUsedTagNotRequired1")
    public static class TestUsedTagNotRequired1 extends TruffleInstrument {

        private static class Foobar {

        }

        @Override
        protected void onCreate(final Env env) {
            try {
                env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(Foobar.class).build(), new ExecutionEventListener() {
                    public void onEnter(EventContext context, VirtualFrame frame) {
                    }

                    public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    }

                    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    }
                });
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertEquals(
                                "The attached filter SourceSectionFilter[tag is one of [foobar0]] references the " +
                                                "following tags [foobar0] which are not declared as required by the instrument. To fix " +
                                                "this annotate the instrument class com.oracle.truffle.api.instrumentation." +
                                                "InstrumentationTest$TestUsedTagNotRequired1 with @RequiredTags({foobar0}).",
                                e.getMessage());
            }
        }
    }

    /*
     * Test behavior of queryTags when used with instruments
     */
    @Test
    public void testQueryTags1() throws IOException {
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testIsNodeTaggedWith1");
        instrument.setEnabled(true);
        Instrumenter instrumenter = instrument.lookup(Instrumenter.class);

        TestIsNodeTaggedWith1.expressionNode = null;
        TestIsNodeTaggedWith1.statementNode = null;

        Assert.assertTrue(instrumenter.queryTags(new Node() {
        }).isEmpty());

        run("STATEMENT(EXPRESSION)");

        assertTags(instrumenter.queryTags(TestIsNodeTaggedWith1.expressionNode), InstrumentationTestLanguage.EXPRESSION);
        assertTags(instrumenter.queryTags(TestIsNodeTaggedWith1.statementNode), InstrumentationTestLanguage.STATEMENT);

        try {
            instrumenter.queryTags(null);
            Assert.fail();
        } catch (NullPointerException e) {
        }
    }

    private static void assertTags(Set<Class<?>> tags, Class<?>... expectedTags) {
        Assert.assertEquals(expectedTags.length, tags.size());
        for (Class<?> clazz : expectedTags) {
            Assert.assertTrue("Tag: " + clazz, tags.contains(clazz));
        }
    }

    /*
     * Test behavior of queryTags when used with languages
     */
    @Test
    public void testQueryTags2() throws IOException {
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testIsNodeTaggedWith1");
        instrument.setEnabled(true);
        TestIsNodeTaggedWith1.expressionNode = null;
        TestIsNodeTaggedWith1.statementNode = null;
        TestIsNodeTaggedWith1Language.instrumenter = null;

        Source otherLanguageSource = Source.newBuilder("STATEMENT(EXPRESSION)").name("unknown").mimeType("testIsNodeTaggedWith1").build();
        run(otherLanguageSource);

        Instrumenter instrumenter = TestIsNodeTaggedWith1Language.instrumenter;

        Node languageExpression = TestIsNodeTaggedWith1.expressionNode;
        Node languageStatement = TestIsNodeTaggedWith1.statementNode;

        assertTags(instrumenter.queryTags(languageExpression), InstrumentationTestLanguage.EXPRESSION);
        assertTags(instrumenter.queryTags(languageStatement), InstrumentationTestLanguage.STATEMENT);

        TestIsNodeTaggedWith1.expressionNode = null;
        TestIsNodeTaggedWith1.statementNode = null;

        run("EXPRESSION");

        // fail if called with nodes from a different language
        Node otherLanguageExpression = TestIsNodeTaggedWith1.expressionNode;
        try {
            instrumenter.queryTags(otherLanguageExpression);
            Assert.fail();
        } catch (IllegalArgumentException e) {
        }

    }

    @Test
    public void testInstrumentsWhenForked() throws IOException {
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testIsNodeTaggedWith1");
        instrument.setEnabled(true);
        TestIsNodeTaggedWith1 service = instrument.lookup(TestIsNodeTaggedWith1.class);

        assertEquals(1, service.onCreateCalls);

        Source otherLanguageSource = Source.newBuilder("STATEMENT(EXPRESSION)").name("unknown").mimeType("testIsNodeTaggedWith1").build();
        run(otherLanguageSource);

        PolyglotEngine forked = createEngine(langMimeType);
        assertEquals(1, service.onCreateCalls);

        final Map<String, ? extends PolyglotRuntime.Instrument> instruments = forked.getRuntime().getInstruments();
        assertSame(instrument, instruments.get("testIsNodeTaggedWith1"));
        assertSame(service, instruments.get("testIsNodeTaggedWith1").lookup(TestIsNodeTaggedWith1.class));

        assertEquals(instruments.size(), engine.getRuntime().getInstruments().size());
        for (String key : instruments.keySet()) {
            assertSame(engine.getRuntime().getInstruments().get(key), instruments.get(key));
        }

        assertEquals(0, service.onDisposeCalls);
        engine.dispose();
        assertEquals(0, service.onDisposeCalls);
        forked.dispose();
        forked.getRuntime().dispose();
        // dispose if all engines are disposed
        assertEquals(1, service.onDisposeCalls);
        engine = null; // avoid disposal in @After event
    }

    @TruffleLanguage.Registration(name = "", version = "", mimeType = "testIsNodeTaggedWith1")
    @ProvidedTags({InstrumentationTestLanguage.ExpressionNode.class, StandardTags.StatementTag.class})
    public static class TestIsNodeTaggedWith1Language extends InstrumentationTestLanguage {

        static Instrumenter instrumenter;

        public TestIsNodeTaggedWith1Language() {
        }

        @Override
        protected Context createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            instrumenter = env.lookup(Instrumenter.class);
            return super.createContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Child private BaseNode base = parse(request.getSource());

                @Override
                public Object execute(VirtualFrame frame) {
                    return base.execute(frame);
                }
            });
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

    @Registration(id = "testIsNodeTaggedWith1")
    public static class TestIsNodeTaggedWith1 extends TruffleInstrument {

        static Node expressionNode;
        static Node statementNode;

        int onCreateCalls = 0;
        int onDisposeCalls = 0;

        @Override
        protected void onCreate(final Env env) {
            onCreateCalls++;
            env.registerService(this);
            env.registerService(env.getInstrumenter());
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventNodeFactory() {

                public ExecutionEventNode create(EventContext context) {
                    expressionNode = context.getInstrumentedNode();
                    return new ExecutionEventNode() {
                    };
                }
            });

            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {

                public ExecutionEventNode create(EventContext context) {
                    statementNode = context.getInstrumentedNode();
                    return new ExecutionEventNode() {
                    };
                }
            });
        }

        @Override
        protected void onDispose(Env env) {
            onDisposeCalls++;
        }

    }

    private void setupEngine(Source initSource, boolean runInitAfterExec) {
        PolyglotEngine.Builder builder = PolyglotEngine.newBuilder();
        builder.runtime(getRuntime());
        builder.config(InstrumentationTestLanguage.MIME_TYPE, "initSource", initSource);
        builder.config(InstrumentationTestLanguage.MIME_TYPE, "runInitAfterExec", runInitAfterExec);
        engine = builder.build();
    }

    @Test
    public void testAccessInstruments() {
        Instrument instrument = engine.getRuntime().getInstruments().get("testAccessInstruments");
        TestAccessInstruments access = instrument.lookup(TestAccessInstruments.class);

        InstrumentInfo info = access.env.getInstruments().get("testAccessInstruments");
        assertNotNull(info);
        assertEquals("testAccessInstruments", info.getId());
        assertEquals("name", info.getName());
        assertEquals("version", info.getVersion());

        try {
            access.env.lookup(info, TestAccessInstruments.class);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        TestAccessInstrumentsOther.initializedCount = 0;

        InstrumentInfo other = access.env.getInstruments().get("testAccessInstrumentsOther");
        assertNotNull(other);
        assertEquals("testAccessInstrumentsOther", other.getId());
        assertEquals("otherName", other.getName());
        assertEquals("otherVersion", other.getVersion());

        assertEquals(0, TestAccessInstrumentsOther.initializedCount);

        // invalid service, should not trigger onCreate
        assertNull(access.env.lookup(other, Object.class));
        assertEquals(0, TestAccessInstrumentsOther.initializedCount);

        // valide service, should trigger onCreate
        assertNotNull(access.env.lookup(other, TestAccessInstrumentsOther.class));
        assertEquals(1, TestAccessInstrumentsOther.initializedCount);
    }

    @Test
    public void testAccessLanguages() {
        Instrument instrument = engine.getRuntime().getInstruments().get("testAccessInstruments");
        TestAccessInstruments access = instrument.lookup(TestAccessInstruments.class);

        LanguageInfo info = access.env.getLanguages().get(InstrumentationTestLanguage.MIME_TYPE);
        assertNotNull(info);
        assertTrue(info.getMimeTypes().contains(InstrumentationTestLanguage.MIME_TYPE));
        assertEquals("InstrumentTestLang", info.getName());
        assertEquals("2.0", info.getVersion());

        assertNotNull(access.env.lookup(info, SpecialService.class));
        assertEquals(InstrumentationTestLanguage.FILENAME_EXTENSION, access.env.lookup(info, SpecialService.class).fileExtension());
    }

    @Registration(id = "testAccessInstruments", name = "name", version = "version", services = TestAccessInstruments.class)
    @SuppressWarnings("hiding")
    public static class TestAccessInstruments extends TruffleInstrument {

        Env env;

        @Override
        protected void onCreate(final Env env) {
            this.env = env;
            env.registerService(this);
        }

        @Override
        protected void onDispose(Env env) {
        }

    }

    @Registration(id = "testAccessInstrumentsOther", name = "otherName", version = "otherVersion", services = TestAccessInstrumentsOther.class)
    public static class TestAccessInstrumentsOther extends TruffleInstrument {

        static int initializedCount = 0;

        @Override
        protected void onCreate(final Env env) {
            env.registerService(this);
            initializedCount++;
        }

        @Override
        protected void onDispose(Env env) {
        }

    }

    public class ReturnLanguageEnv {

        public static final String KEY = "envReturner";

        public TruffleLanguage.Env env;

    }

    @Test
    public void testAccessInstrumentFromLanguage() {
        ReturnLanguageEnv envReturner = new ReturnLanguageEnv();
        PolyglotEngine e = PolyglotEngine.newBuilder().setErr(err).config(InstrumentationTestLanguage.MIME_TYPE, ReturnLanguageEnv.KEY, envReturner).build();
        e.eval(Source.newBuilder("").mimeType(InstrumentationTestLanguage.MIME_TYPE).name("").build());
        assertNotNull(envReturner.env);

        TruffleLanguage.Env env = envReturner.env;
        LanguageInfo langInfo = env.getLanguages().get(InstrumentationTestLanguage.MIME_TYPE);
        assertNotNull(langInfo);
        assertTrue(langInfo.getMimeTypes().contains(InstrumentationTestLanguage.MIME_TYPE));
        assertEquals("InstrumentTestLang", langInfo.getName());
        assertEquals("2.0", langInfo.getVersion());

        InstrumentInfo instrInfo = env.getInstruments().get("testAccessInstruments");
        assertNotNull(instrInfo);
        assertEquals("testAccessInstruments", instrInfo.getId());
        assertEquals("name", instrInfo.getName());
        assertEquals("version", instrInfo.getVersion());

        assertNotNull(env.lookup(instrInfo, TestAccessInstruments.class));
        assertNull(env.lookup(instrInfo, SpecialService.class));

        try {
            // cannot load services from current languages to avoid cycles.
            env.lookup(langInfo, SpecialService.class);
            fail();
        } catch (Exception e1) {
            // expected
        }
    }

    @Test
    public void testLanguageInitializedOrNot() throws Exception {
        Source initSource = Source.newBuilder("STATEMENT(EXPRESSION, EXPRESSION)").name("<init>").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        setupEngine(initSource, false);

        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testLangInitialized");

        // Events during language initialization phase are included:
        TestLangInitialized.initializationEvents = true;
        instrument.setEnabled(true);
        TestLangInitialized service = instrument.lookup(TestLangInitialized.class);
        run("LOOP(2, STATEMENT())");
        assertEquals("[StatementNode, false, ExpressionNode, false, ExpressionNode, false, LoopNode, true, StatementNode, true, StatementNode, true]", service.getEnteredNodes());
        instrument.setEnabled(false);
    }

    @Test
    public void testLanguageInitializedOnly() throws Exception {
        Source initSource = Source.newBuilder("STATEMENT(EXPRESSION, EXPRESSION)").name("<init>").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        setupEngine(initSource, false);
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testLangInitialized");

        // Events during language initialization phase are excluded:
        TestLangInitialized.initializationEvents = false;
        instrument.setEnabled(true);
        TestLangInitialized service = instrument.lookup(TestLangInitialized.class);
        run("LOOP(2, STATEMENT())");
        assertEquals("[LoopNode, true, StatementNode, true, StatementNode, true]", service.getEnteredNodes());
        instrument.setEnabled(false);
    }

    @Test
    public void testLanguageInitializedOrNotAppend() throws Exception {
        Source initSource = Source.newBuilder("STATEMENT(EXPRESSION, EXPRESSION)").name("<init>").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        setupEngine(initSource, true);
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testLangInitialized");

        // Events during language initialization phase are prepended and appended:
        TestLangInitialized.initializationEvents = true;
        instrument.setEnabled(true);
        TestLangInitialized service = instrument.lookup(TestLangInitialized.class);
        run("LOOP(2, STATEMENT())");
        assertEquals("[StatementNode, false, ExpressionNode, false, ExpressionNode, false, LoopNode, true, StatementNode, true, StatementNode, true, StatementNode, true, ExpressionNode, true, ExpressionNode, true]",
                        service.getEnteredNodes());
        instrument.setEnabled(false);
    }

    @Test
    public void testLanguageInitializedOnlyAppend() throws Exception {
        Source initSource = Source.newBuilder("STATEMENT(EXPRESSION, EXPRESSION)").name("<init>").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        setupEngine(initSource, true);
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testLangInitialized");

        // Events during language initialization phase are excluded,
        // but events from the same nodes used for initialization are appended:
        TestLangInitialized.initializationEvents = false;
        instrument.setEnabled(true);
        TestLangInitialized service = instrument.lookup(TestLangInitialized.class);
        run("LOOP(2, STATEMENT())");
        assertEquals("[LoopNode, true, StatementNode, true, StatementNode, true, StatementNode, true, ExpressionNode, true, ExpressionNode, true]", service.getEnteredNodes());
        instrument.setEnabled(false);
    }

    @Registration(id = "testLangInitialized")
    public static class TestLangInitialized extends TruffleInstrument implements ExecutionEventListener {

        static boolean initializationEvents;
        private final List<String> enteredNodes = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, this);
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            if (!initializationEvents && !context.isLanguageContextInitialized()) {
                // Skipt language context initialization if initializationEvents is false
                return;
            }
            enteredNodes.add(context.getInstrumentedNode().getClass().getSimpleName());
            enteredNodes.add(Boolean.toString(context.isLanguageContextInitialized()));
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }

        String getEnteredNodes() {
            return enteredNodes.toString();
        }
    }

    @Test
    public void testAllocation() throws Exception {
        PolyglotRuntime.Instrument instrument = engine.getRuntime().getInstruments().get("testAllocation");
        instrument.setEnabled(true);
        TestAllocation allocation = instrument.lookup(TestAllocation.class);
        run("LOOP(3, VARIABLE(a, 10))");
        instrument.setEnabled(false);
        assertEquals("[W 4 null, A 4 10, W 4 null, A 4 10, W 4 null, A 4 10]", allocation.getAllocations());

    }

    @Registration(id = "testAllocation")
    public static class TestAllocation extends TruffleInstrument implements AllocationListener {

        private final List<String> allocations = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            LanguageInfo testLanguage = env.getLanguages().get(InstrumentationTestLanguage.MIME_TYPE);
            env.getInstrumenter().attachAllocationListener(AllocationEventFilter.newBuilder().languages(testLanguage).build(), this);
        }

        String getAllocations() {
            return allocations.toString();
        }

        @Override
        @TruffleBoundary
        public void onEnter(AllocationEvent event) {
            allocations.add("W " + event.getNewSize() + " " + event.getValue());
        }

        @Override
        @TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            allocations.add("A " + event.getNewSize() + " " + event.getValue());
        }
    }

    private static final class MyKillException extends ThreadDeath {
        static final long serialVersionUID = 1;
    }
}
