/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.benchmark;

import static com.oracle.truffle.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.FORKS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.MEASUREMENT_ITERATIONS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.WARMUP_ITERATIONS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.wasm.utils.SystemProperties;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.wasm.predefined.testutil.TestutilModule;
import com.oracle.truffle.wasm.utils.Assert;
import com.oracle.truffle.wasm.utils.WasmBinaryTools;
import com.oracle.truffle.wasm.utils.WasmInitialization;
import com.oracle.truffle.wasm.utils.WasmResource;

@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public abstract class WasmBenchmarkSuiteBase {
    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 10;
        public static final int WARMUP_ITERATIONS = 10;
        public static final int FORKS = 1;
    }

    public abstract static class WasmBenchmarkState {
        private Value mainFunction;
        private Value resetContext;
        private Value customInitializer;
        private WasmInitialization initialization;

        @Setup(Level.Trial)
        public void setup() throws IOException, InterruptedException {
            final String wantedBenchmarkName = WasmBenchmarkOptions.BENCHMARK_NAME;

            if (wantedBenchmarkName == null || wantedBenchmarkName.equals("")) {
                Assert.fail("Please select a benchmark by setting -Dwasmbench.benchmarkName");
            }

            WasmBenchCase benchCase = getBenchCase(benchmarkResource(), WasmBenchmarkOptions.BENCHMARK_NAME);

            Assert.assertNotNull(String.format("Benchmark %s not found", benchmarkResource()), benchCase);

            Context.Builder contextBuilder = Context.newBuilder("wasm");
            contextBuilder.option("wasm.PredefinedModules", "testutil:testutil,env:emscripten");

            byte[] binary = benchCase.createBinary();
            Context context = contextBuilder.build();
            Source source = Source.newBuilder("wasm", ByteSequence.create(binary), "test").build();

            context.eval(source);

            Value wasmBindings = context.getBindings("wasm");
            mainFunction = wasmBindings.getMember("_main");
            resetContext = wasmBindings.getMember(TestutilModule.Names.RESET_CONTEXT);
            customInitializer = wasmBindings.getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
            initialization = benchCase.initialization();
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            if (initialization != null) {
                customInitializer.execute(initialization);
            }
        }

        @TearDown(Level.Iteration)
        public void teardownIteration() {
            // Reset context and zero out memory.
            resetContext.execute(true);
        }

        public Value mainFunction() {
            return mainFunction;
        }

        protected abstract String benchmarkResource();
    }

    private static WasmBenchCase getBenchCase(String benchmarkResource, String wantedBenchmarkName) throws IOException {
        Collection<WasmBenchCase> collectedCases = new ArrayList<>();

        // Open the wasm_bench_index file of the bench bundle.
        // The wasm_bench_index file contains the available benchmarks for that bundle.
        InputStream index = WasmBenchmarkSuiteBase.class.getResourceAsStream(String.format("/bench/%s/wasm_test_index", benchmarkResource));
        Assert.assertNotNull("Could not find resource: wasm_test_index", index);
        BufferedReader indexReader = new BufferedReader(new InputStreamReader(index));

        Set<String> allBenchNames = indexReader.lines().filter(name -> !name.equals("") && !name.startsWith("#")).collect(Collectors.toSet());

        if (!allBenchNames.contains(wantedBenchmarkName)) {
            return null;
        }

        Object mainContent = WasmResource.getResourceAsTest(String.format("/bench/%s/%s", benchmarkResource, wantedBenchmarkName), true);
        String initContent = WasmResource.getResourceAsString(String.format("/bench/%s/%s.init", benchmarkResource, wantedBenchmarkName), false);
        String optsContent = WasmResource.getResourceAsString(String.format("/bench/%s/%s.opts", benchmarkResource, wantedBenchmarkName), false);
        WasmInitialization initializer = WasmInitialization.create(initContent);
        Properties options = SystemProperties.createFromOptions(optsContent);

        if (mainContent instanceof String) {
            return benchCase(wantedBenchmarkName, (String) mainContent, initializer, options);
        } else if (mainContent instanceof byte[]) {
            return benchCase(wantedBenchmarkName, (byte[]) mainContent, initializer, options);
        } else {
            Assert.fail("Unknown content type: " + mainContent.getClass());
        }

        return null;
    }

    private static WasmStringBenchCase benchCase(String name, String program, WasmInitialization initialization, Properties options) {
        return new WasmStringBenchCase(name, program, initialization, options);
    }

    private static WasmBinaryBenchCase benchCase(String name, byte[] program, WasmInitialization initialization, Properties options) {
        return new WasmBinaryBenchCase(name, program, initialization, options);
    }

    private abstract static class WasmBenchCase {
        private final String name;
        private final WasmInitialization initialization;
        private final Properties options;

        WasmBenchCase(String name, WasmInitialization initialization, Properties options) {
            this.name = name;
            this.initialization = initialization;
            this.options = options;
        }

        public abstract byte[] createBinary() throws IOException, InterruptedException;

        public String name() {
            return name;
        }

        public WasmInitialization initialization() {
            return initialization;
        }

        public Properties options() {
            return options;
        }
    }

    private static class WasmStringBenchCase extends WasmBenchCase {
        private final String program;

        WasmStringBenchCase(String name, String program, WasmInitialization initialization, Properties options) {
            super(name, initialization, options);
            this.program = program;
        }

        @Override
        public byte[] createBinary() throws IOException, InterruptedException {
            return WasmBinaryTools.compileWat(program);
        }
    }

    private static class WasmBinaryBenchCase extends WasmBenchCase {
        private final byte[] binary;

        WasmBinaryBenchCase(String name, byte[] binary, WasmInitialization initialization, Properties options) {
            super(name, initialization, options);
            this.binary = binary;
        }


        @Override
        public byte[] createBinary() {
            return binary;
        }
    }
}
