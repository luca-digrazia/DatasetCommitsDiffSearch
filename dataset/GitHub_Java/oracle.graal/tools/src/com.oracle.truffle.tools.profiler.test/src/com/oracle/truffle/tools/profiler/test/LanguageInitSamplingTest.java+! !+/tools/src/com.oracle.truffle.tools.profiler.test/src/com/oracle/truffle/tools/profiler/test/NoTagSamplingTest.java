/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.test;

import java.io.IOException;
import java.util.Collection;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.ProfilerNode;

public class LanguageInitSamplingTest {

    @Test
    public void testLanguageInit() {
        Context context = Context.create(LongInitLanguage.ID);
        CPUSampler sampler = CPUSampler.find(context.getEngine());
        sampler.setCollecting(true);
        try {
            context.eval(Source.newBuilder(LongInitLanguage.ID, "", "").build());
        } catch (IOException e) {
            Assert.fail();
        }
        sampler.setCollecting(false);
        Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes = sampler.getThreadToNodesMap().get(Thread.currentThread());
        Assert.assertEquals(1, profilerNodes.size());

    }

    public static class LILRootNode extends RootNode {

        protected LILRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            for (int i = 0; i < 1000; i++) {
                sleepSomeTime();
                TruffleSafepoint.poll(this);
            }
            return 42;
        }

        @TruffleBoundary
        private static void sleepSomeTime() {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }
    }

    @TruffleLanguage.Registration(id = LongInitLanguage.ID, name = LongInitLanguage.ID, version = "0.0.1")
    public static class LongInitLanguage extends ProxyLanguage {
        static final String ID = "LongInitLanguage";

        @Override
        protected void initializeContext(ProxyLanguage.LanguageContext context) throws Exception {
// newTarget().call();
        }

        @Override
        protected LanguageContext createContext(Env env) {
            return super.createContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return newTarget();
        }

        private RootCallTarget newTarget() {
            return Truffle.getRuntime().createCallTarget(new LILRootNode(this));
        }
    }
}
