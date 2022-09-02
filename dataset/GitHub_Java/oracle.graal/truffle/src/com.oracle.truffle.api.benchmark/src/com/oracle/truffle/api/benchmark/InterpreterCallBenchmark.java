/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.benchmark;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgsAppend = "-Dgraal.TruffleCompilation=false")
public class InterpreterCallBenchmark extends TruffleBenchmark {

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class CallTargetCallState {
        final Integer argument = 42;
        final CallTarget callee;
        final DirectCallNode directCall;
        final IndirectCallNode indirectCall;

        {
            callee = Truffle.getRuntime().createCallTarget(new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerAsserts.neverPartOfCompilation("do not compile");
                    return frame.getArguments()[0];
                }

                @Override
                public String toString() {
                    return "callee";
                }
            });
            directCall = Truffle.getRuntime().createDirectCallNode(callee);
            indirectCall = Truffle.getRuntime().createIndirectCallNode();
        }

        @Setup
        public void setup() {
            // Ensure call boundary method is compiled.
            ensureTruffleCompilerInitialized();
        }
    }

    @Benchmark
    public Object directCall(CallTargetCallState state) {
        return state.directCall.call(new Object[]{state.argument});
    }

    @Benchmark
    public Object indirectCall(CallTargetCallState state) {
        return state.indirectCall.call(state.callee, new Object[]{state.argument});
    }

    @Benchmark
    public Object call(CallTargetCallState state) {
        return state.callee.call(state.argument);
    }

    static void ensureTruffleCompilerInitialized() {
        if (TruffleOptions.AOT) {
            return;
        }
        try {
            Method getTruffleCompiler = Truffle.getRuntime().getClass().getMethod("getTruffleCompiler");
            getTruffleCompiler.invoke(Truffle.getRuntime());
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
            System.err.println("Could not invoke getTruffleCompiler(): " + e);
        }
    }
}
