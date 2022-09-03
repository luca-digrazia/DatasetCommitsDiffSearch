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
package org.graalvm.compiler.truffle.test;

import java.lang.reflect.Field;

import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.builtins.SLIsOptimizedBuiltinFactory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.sl.builtins.SLBuiltinNode;
import com.oracle.truffle.sl.nodes.call.SLDispatchNode;
import com.oracle.truffle.sl.runtime.SLContext;

public class PolyglotEngineOptionsTest extends TestWithSynchronousCompiling {

    private static final String COMPILATION_THRESHOLD_OPTION = "compiler.CompilationThreshold";

    @Test
    public void testVisibleOptions() {
        Engine engine = Engine.create();
        OptionDescriptor compilationThreshold = engine.getOptions().get(COMPILATION_THRESHOLD_OPTION);
        OptionDescriptor queueTimeThreshold = engine.getOptions().get("compiler.QueueTimeThreshold");
        Assert.assertNotNull(compilationThreshold);
        Assert.assertNotNull(queueTimeThreshold);
        engine.close();
    }

    @Test
    public void testCompilationThreshold() {
        // does not work with a different inline cache size.
        Assert.assertEquals(2, SLDispatchNode.INLINE_CACHE_SIZE);

        // doWhile must run isolated and should not affect other compilation thresholds
        Runnable doWhile = () -> testCompilationThreshold(50, "50", null);
        testCompilationThreshold(42, "42", doWhile); // test default value
        testCompilationThreshold(TruffleCompilerOptions.TruffleCompilationThreshold.getValue(TruffleCompilerOptions.getOptions()), null, doWhile);
        testCompilationThreshold(2, "2", doWhile); // test default value
    }

    private static void testCompilationThreshold(int iterations, String compilationThresholdOption, Runnable doWhile) {
        Context.Builder builder = Context.newBuilder("sl");
        if (compilationThresholdOption != null) {
            builder.option(COMPILATION_THRESHOLD_OPTION, compilationThresholdOption);
        }
        Context context = builder.build();

        context.eval("sl", "function test() {}");

        Value test = context.getBindings("sl").getMember("test");
        Assert.assertFalse(isExecuteCompiled(test));
        for (int i = 0; i < iterations - 1; i++) {
            Assert.assertFalse(isExecuteCompiled(test));
            test.execute();
        }
        if (doWhile != null) {
            doWhile.run();
        }
        Assert.assertFalse(isExecuteCompiled(test));
        test.execute();
        Assert.assertTrue(isExecuteCompiled(test));
        test.execute();
        Assert.assertTrue(isExecuteCompiled(test));
        context.close();
    }

    private static boolean isExecuteCompiled(Value value) {
        try {
            Field implField = value.getClass().getDeclaredField("impl");
            implField.setAccessible(true);
            Object impl = implField.get(value);
            Field execute = impl.getClass().getDeclaredField("executeNoArgs");
            execute.setAccessible(true);
            return ((OptimizedCallTarget) execute.get(impl)).isValid();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

}
