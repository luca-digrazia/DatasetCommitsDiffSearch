/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.lir.test;

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.jtt.LIRTest;
import org.graalvm.compiler.lir.jtt.LIRTestSpecification;
import org.graalvm.compiler.test.SubprocessUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BenchmarkCounterOverflowTest extends LIRTest {
    private static final String SUBPROCESS_PROPERTY = BenchmarkCounterOverflowTest.class.getSimpleName() + ".subprocess.call";
    private static final boolean VERBOSE = Boolean.getBoolean(BenchmarkCounterOverflowTest.class.getSimpleName() + ".verbose");

    private static LIRKind intKind;

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
        Assume.assumeTrue("skipping HotSpot specific test", getBackend() instanceof HotSpotBackend);
    }

    @Before
    public void setUp() {
        intKind = LIRKind.fromJavaKind(getBackend().getTarget().arch, JavaKind.Long);
    }

    private static final LIRTestSpecification constCounterIncrement = new LIRTestSpecification() {
        @Override
        public void generate(LIRGeneratorTool gen) {
            gen.append(gen.createBenchmarkCounter("counter", "test", new ConstantValue(intKind, JavaConstant.forLong(Integer.MAX_VALUE))));
        }
    };

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static void counterInc(LIRTestSpecification spec) {
    }

    public static void test(long count) {
        for (long i = 0; i < count; i++) {
            counterInc(constCounterIncrement);
            GraalDirectives.blackhole(i);
        }
    }

    @Test
    public void incrementCounter() {
        Assume.assumeTrue("not a subprocess -> skip", Boolean.getBoolean(SUBPROCESS_PROPERTY));
        BenchmarkCounters.enabled = true;

        Object[] args = new Object[]{Integer.MAX_VALUE * 4L};
        ResolvedJavaMethod method = getResolvedJavaMethod("test");
        executeActualCheckDeopt(getInitialOptions(), method, EMPTY, null, args);
    }

    @Test
    public void spawnSubprocess() throws Throwable {
        Assume.assumeFalse("subprocess already spawned -> skip", Boolean.getBoolean(SUBPROCESS_PROPERTY));
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.add("-XX:JVMCICounterSize=1");
        vmArgs.add("-Dgraal." + BenchmarkCounters.Options.BenchmarkCountersDetectOverflow.getName() + "=true");
        vmArgs.add("-D" + SUBPROCESS_PROPERTY + "=true");

        List<String> mainClassAndArgs = new ArrayList<>();
        mainClassAndArgs.add("com.oracle.mxtool.junit.MxJUnitWrapper");
        mainClassAndArgs.add(BenchmarkCounterOverflowTest.class.getName());

        SubprocessUtil.Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);

        if (VERBOSE) {
            System.out.println(proc);
        }

        Assert.assertNotEquals("Expected non-zero exit status", 0, proc.exitCode);

        Iterator<String> it = proc.output.iterator();
        while (it.hasNext()) {
            String line = it.next();
            if (line.contains("Problematic frame:")) {
                if (!it.hasNext()) {
                    // no more line
                    break;
                }
                line = it.next();
                if (line.contains(BenchmarkCounterOverflowTest.class.getName() + ".test")) {
                    return;
                }
                Assert.fail("Unexpected stack trace: " + line);
            }
        }
        Assert.fail("Could not find method in error message");
    }
}
