/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.graalvm.compiler.core.GraalCompilerOptions;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;

public class ExceptionActionTest extends TestWithPolyglotOptions {

    private static final String[] DEFAULT_OPTIONS = {
                    "engine.CompileImmediately", "true",
                    "engine.BackgroundCompilation", "false",
    };

    static Object nonConstant;

    @BeforeClass
    public static void setUp() {
        permanentBailoutTestImpl();
    }

    @Test
    public void testPermanentBailoutSilent() throws Exception {
        executeForked((log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        },
                        "engine.CompilationExceptionsArePrinted", "false",
                        "engine.CompilationFailureAction", "Silent");
    }

    @Test
    public void testPermanentBailoutPrint() throws Exception {
        executeForked((log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        },
                        "engine.CompilationExceptionsArePrinted", "false",
                        "engine.CompilationFailureAction", "Print");
    }

    @Test
    public void testPermanentBailoutExceptionsArePrinted() throws Exception {
        executeForked((log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        },
                        "engine.CompilationExceptionsArePrinted", "true",
                        "engine.CompilationFailureAction", "Silent");
    }

    @Test
    public void testPermanentBailoutExitVM() throws Exception {
        executeForked((log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertTrue(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        },
                        "engine.CompilationFailureAction", "ExitVM");
    }

    @Test
    public void testPermanentBailoutExceptionsAreFatal() throws Exception {
        executeForked((log) -> {
            Assert.assertTrue(hasBailout(log));
            Assert.assertTrue(hasExit(log));
            Assert.assertFalse(hasOptFailedException(log));
        },
                        "engine.CompilationExceptionsAreFatal", "true");
    }

    @Test
    public void testPermanentBailoutThrow() throws Exception {
        executeForked((log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertTrue(hasOptFailedException(log));
        },
                        "engine.CompilationFailureAction", "Throw");
    }

    @Test
    public void testPermanentBailoutCompilationExceptionsAreThrown() throws Exception {
        executeForked((log) -> {
            Assert.assertFalse(hasBailout(log));
            Assert.assertFalse(hasExit(log));
            Assert.assertTrue(hasOptFailedException(log));

        },
                        "engine.CompilationExceptionsAreThrown", "true");
    }

    private void executeForked(Consumer<? super Path> verifier, String... options) throws IOException, InterruptedException {
        if (!isConfigured()) {
            Path log = File.createTempFile("compiler", ".log").toPath();
            String testName = Thread.currentThread().getStackTrace()[2].getMethodName();
            execute(testName, false, log);
            verifier.accept(log);
        } else {
            setupContext(options);
            try {
                permanentBailoutTestImpl();
            } catch (RuntimeException e) {
                OptimizationFailedException optFailedException = isOptimizationFailed(e);
                if (optFailedException != null) {
                    TruffleCompilerRuntime.getRuntime().log(optFailedException.getClass().getName());
                }
            }
        }
    }

    private static OptimizationFailedException isOptimizationFailed(Throwable t) {
        if (t == null) {
            return null;
        } else if (t instanceof OptimizationFailedException) {
            return (OptimizationFailedException) t;
        }
        return isOptimizationFailed(t.getCause());
    }

    @Override
    protected Context setupContext(String... keyValuePairs) {
        String[] newKeyValuePairs = Arrays.copyOf(keyValuePairs, keyValuePairs.length + DEFAULT_OPTIONS.length);
        System.arraycopy(DEFAULT_OPTIONS, 0, newKeyValuePairs, keyValuePairs.length, DEFAULT_OPTIONS.length);
        return super.setupContext(newKeyValuePairs);
    }

    private static boolean isConfigured() {
        return Boolean.getBoolean(String.format("%s", ExceptionActionTest.class.getSimpleName()));
    }

    private static void execute(String testName, boolean addCompilationBailoutAsFailure, Path logFile) throws IOException, InterruptedException {
        SubprocessUtil.java(
                        configure(getVmArgs(), addCompilationBailoutAsFailure, logFile),
                        "com.oracle.mxtool.junit.MxJUnitWrapper",
                        String.format("%s#%s", ExceptionActionTest.class.getName(), testName));
    }

    private static List<String> configure(List<String> vmArgs, boolean addCompilationBailoutAsFailure, Path logFile) {
        List<String> newVmArgs = new ArrayList<>();
        newVmArgs.addAll(vmArgs.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String vmArg) {
                return !vmArg.contains(GraalCompilerOptions.CompilationFailureAction.getName()) && !vmArg.contains(GraalCompilerOptions.CompilationBailoutAsFailure.getName());
            }
        }).collect(Collectors.toList()));
        if (addCompilationBailoutAsFailure) {
            newVmArgs.add(1, String.format("-Dgraal.%s=%s", GraalCompilerOptions.CompilationBailoutAsFailure.getName(), true));
        }
        newVmArgs.add(1, String.format("-Dgraal.LogFile=%s", logFile.toAbsolutePath().toString()));
        newVmArgs.add(1, String.format("-D%s=true", ExceptionActionTest.class.getSimpleName()));
        return newVmArgs;
    }

    private static List<String> getVmArgs() {
        List<String> vmArgs = SubprocessUtil.getVMCommandLine();
        vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
        return vmArgs;
    }

    private static boolean hasExit(Path logFile) {
        return contains(logFile, "Exiting VM");
    }

    private static boolean hasBailout(Path logFile) {
        return contains(logFile, "BailoutException");
    }

    private static boolean hasOptFailedException(Path logFile) {
        return contains(logFile, "OptimizationFailedException");
    }

    private static boolean contains(Path logFile, String substr) {
        try {
            for (String line : Files.readAllLines(logFile)) {
                if (line.contains(substr)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ioe) {
            throw sthrow(ioe, RuntimeException.class);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T sthrow(Throwable t, Class<T> type) throws T {
        throw (T) t;
    }

    private static void permanentBailoutTestImpl() {
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(createPermanentBailoutNode());
        target.call();
    }

    private static RootNode createPermanentBailoutNode() {
        FrameDescriptor fd = new FrameDescriptor();
        return new RootTestNode(fd, "test-node", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                CompilerAsserts.partialEvaluationConstant(nonConstant);
                return 0;
            }
        });
    }
}
