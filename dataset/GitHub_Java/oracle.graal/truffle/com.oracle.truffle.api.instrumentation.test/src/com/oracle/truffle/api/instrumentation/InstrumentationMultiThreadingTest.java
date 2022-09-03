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
package com.oracle.truffle.api.instrumentation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

public class InstrumentationMultiThreadingTest {

    @Test
    public void testAsyncAttachement() throws InterruptedException, ExecutionException {
        // unfortunately we don't support multiple evals yet
        int nEvals = 1;
        int nInstruments = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(nEvals + nInstruments);
        List<Future<?>> futures = new ArrayList<>();
        final AtomicBoolean terminated = new AtomicBoolean(false);
        final AtomicReference<PolyglotEngine> engineRef = new AtomicReference<>();
        for (int i = 0; i < nEvals; i++) {
            futures.add(executorService.submit(new Runnable() {
                public void run() {
                    final PolyglotEngine engine = PolyglotEngine.newBuilder().build();
                    engineRef.set(engine);
                    while (!terminated.get()) {
                        try {
                            engine.eval(Source.fromAppendableText("ROOT(BLOCK(STATEMENT(EXPRESSION, EXPRESSION), STATEMENT(EXPRESSION))").withMimeType(InstrumentationTestLanguage.MIME_TYPE));
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }));
        }

        for (int i = 0; i < nInstruments; i++) {
            final int index = i;
            futures.add(executorService.submit(new Runnable() {
                public void run() {
                    while (!terminated.get()) {
                        PolyglotEngine engine = engineRef.get();
                        if (engine != null) {
                            engine.getInstruments().get("testAsyncAttachement1").setEnabled(index % 2 == 0);
                            engine.getInstruments().get("testAsyncAttachement2").setEnabled(index % 2 == 1);
                        }
                    }
                }
            }));
        }
        Thread.sleep(4000);
        terminated.set(true);
        for (Future<?> future : futures) {
            future.get();
        }
    }

    @Registration(id = "testAsyncAttachement1")
    public static class TestAsyncAttachement1 extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            ExecutionEventListener dummyListener = new ExecutionEventListener() {
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                }
            };
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), dummyListener);
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), dummyListener);
        }
    }

    @Registration(id = "testAsyncAttachement2")
    public static class TestAsyncAttachement2 extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            ExecutionEventListener dummyListener = new ExecutionEventListener() {
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                }
            };
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), dummyListener);
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), dummyListener);
        }
    }

}
