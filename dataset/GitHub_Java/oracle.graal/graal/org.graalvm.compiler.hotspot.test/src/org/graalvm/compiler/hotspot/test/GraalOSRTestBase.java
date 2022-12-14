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

package org.graalvm.compiler.hotspot.test;

import java.util.Arrays;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationTask;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.java.BciBlockMapping.BciBlock;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Assert;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class GraalOSRTestBase extends GraalCompilerTest {

    protected void testOSR(String methodName) {
        testOSR(methodName, null);
    }

    protected void testOSR(String methodName, Object receiver, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        testOSR(method, receiver, args);
    }

    protected void testOSR(ResolvedJavaMethod method, Object receiver, Object... args) {
        // invalidate any existing compiled code
        method.reprofile();
        compileOSR(method);
        Result result = executeExpected(method, receiver, args);
        checkResult(result);
    }

    protected static void compile(ResolvedJavaMethod method, int bci) {
        HotSpotJVMCIRuntimeProvider runtime = HotSpotJVMCIRuntime.runtime();
        long jvmciEnv = 0L;
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) method, bci, jvmciEnv);
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime.getCompiler();
        CompilationTask task = new CompilationTask(runtime, compiler, request, true, true);
        HotSpotCompilationRequestResult result = task.runCompilation();
        if (result.getFailure() != null) {
            throw new GraalError(result.getFailureMessage());
        }
    }

    /**
     * Returns the target BCI of the first bytecode backedge. This is where HotSpot triggers
     * on-stack-replacement in case the backedge counter overflows.
     */
    private static int getBackedgeBCI(ResolvedJavaMethod method) {
        Bytecode code = new ResolvedJavaMethodBytecode(method);
        BytecodeStream stream = new BytecodeStream(code.getCode());
        BciBlockMapping bciBlockMapping = BciBlockMapping.create(stream, code);

        for (BciBlock block : bciBlockMapping.getBlocks()) {
            if (block.startBci != -1) {
                int bci = block.startBci;
                for (BciBlock succ : block.getSuccessors()) {
                    if (succ.startBci != -1) {
                        int succBci = succ.startBci;
                        if (succBci < bci) {
                            // back edge
                            return succBci;
                        }
                    }
                }
            }
        }
        TTY.println("Cannot find loop back edge with bytecode loops at:%s", Arrays.toString(bciBlockMapping.getLoopHeaders()));
        TTY.println(new BytecodeDisassembler().disassemble(code));
        return -1;
    }

    private static void checkResult(Result result) {
        Assert.assertNull("Unexpected exception", result.exception);
        Assert.assertNotNull(result.returnValue);
        Assert.assertTrue(result.returnValue instanceof ReturnValue);
        Assert.assertEquals(ReturnValue.SUCCESS, result.returnValue);
    }

    private void compileOSR(ResolvedJavaMethod method) {
        // ensure eager resolving
        parseEager(method, AllowAssumptions.YES);
        int bci = getBackedgeBCI(method);
        assert bci != -1;
        // ensure eager resolving
        parseEager(method, AllowAssumptions.YES);
        compile(method, bci);
    }

    protected enum ReturnValue {
        SUCCESS,
        FAILURE,
        SIDE
    }

    public GraalOSRTestBase() {
        super();
    }

    public GraalOSRTestBase(Class<? extends Architecture> arch) {
        super(arch);
    }

    public GraalOSRTestBase(Backend backend) {
        super(backend);
    }

}
