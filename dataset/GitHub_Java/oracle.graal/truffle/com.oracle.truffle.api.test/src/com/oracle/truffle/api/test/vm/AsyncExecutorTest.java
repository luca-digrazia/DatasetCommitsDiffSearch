/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;

public class AsyncExecutorTest {

    @Test
    public void testSynchronousLanguageAccess() {
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        Source s = Source.newBuilder("").mimeType("application/x-test-async").name("").build();
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            values.add(engine.eval(s));
        }
        for (int i = 0; i < 100; i++) {
            Assert.assertEquals(i, (int) values.get(i).as(Integer.class));
        }
    }

    @Test
    public void testFailingAsyncLanguageAccess() {
        ExecutorService service = Executors.newFixedThreadPool(10);
        PolyglotEngine engine = PolyglotEngine.newBuilder().executor(service).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-async").name("").build();
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            values.add(engine.eval(s));
        }
        for (int i = 0; i < 100; i++) {
            // fails because the execution is depending on a side-effect.
            // this way you can crash arbitrary languages.
            Assert.assertEquals(i, (int) values.get(i).as(Integer.class));
        }
    }

    private static class AsyncContext {

        private int value;

        public int operationWithSideEffects() {
            return value++;
        }

    }

    @TruffleLanguage.Registration(name = "Async", mimeType = "application/x-test-async", version = "1.0")
    public static class AsyncLanguage extends TruffleLanguage<AsyncContext> {
        public static final AsyncLanguage INSTANCE = new AsyncLanguage();

        @Override
        protected AsyncContext createContext(Env env) {
            return new AsyncContext();
        }

        @Override
        protected CallTarget parse(Source code, Node context, String... argumentNames) {
            return Truffle.getRuntime().createCallTarget(new RootNode(AsyncLanguage.class, null, null) {

                @Child Node findContext = createFindContextNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    return findContext(findContext).operationWithSideEffects();
                }
            });
        }

        @Override
        protected Object findExportedSymbol(AsyncContext context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(AsyncContext context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) {
            throw new UnsupportedOperationException();
        }

    }

}
