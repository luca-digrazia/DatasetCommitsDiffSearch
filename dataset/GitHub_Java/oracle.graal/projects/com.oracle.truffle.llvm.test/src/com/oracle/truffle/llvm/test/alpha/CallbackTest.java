/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

@RunWith(Parameterized.class)
public final class CallbackTest extends BaseSulongOnlyHarness {

    private static final Path OTHER_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../cache/tests/other").toPath();
    private static final String testSuffix = "_clang_O0.bc";

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public RunConfiguration configuration;
    @Parameter(value = 2) public String name;

    @Parameters(name = "{2}")
    public static Collection<Object[]> data() {

        final Map<Path, RunConfiguration> runs = new HashMap<>();
        runs.put(new File(OTHER_DIR + "/callbackTest001/callbackTest001" + testSuffix).toPath(), new RunConfiguration(16, null));
        runs.put(new File(OTHER_DIR + "/callbackTest002/callbackTest002" + testSuffix).toPath(),
                        new RunConfiguration(14, null));

        return runs.keySet().stream().map(k -> new Object[]{k, runs.get(k), k.getFileName().toString()}).collect(Collectors.toList());
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public RunConfiguration getConfiguration() {
        return configuration;
    }

}
