/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.alpha;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

@RunWith(Parameterized.class)
public final class LLVMSuite extends BaseSuiteHarness {

    private static final Path LLVM_SUITE_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../cache/tests/llvm").toPath();
    private static final Path LLVM_SOURCE_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/llvm/test-suite-3.2.src/").toPath();
    private static final Path LLVM_CONFIG_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/llvm/configs").toPath();

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return collectTestCases(LLVM_CONFIG_DIR, LLVM_SUITE_DIR);
    }

    @Override
    protected Path getTestDirectory() {
        return path;
    }

    @Override
    protected Path getSuiteDirectory() {
        return LLVM_SUITE_DIR;
    }

    @AfterClass
    public static void printStatistics() {
        printStatistics("LLVM", LLVM_SOURCE_DIR, LLVM_CONFIG_DIR, f -> true);
    }
}
