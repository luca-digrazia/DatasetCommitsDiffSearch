package com.oracle.truffle.wasm.benchmark;

import static com.oracle.truffle.wasm.benchmark.WasmBenchmark.Defaults.FORKS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmark.Defaults.MEASUREMENT_ITERATIONS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmark.Defaults.WARMUP_ITERATIONS;

import com.oracle.truffle.wasm.benchmark.options.WasmBenchmarkOptions;
import com.oracle.truffle.wasm.benchmark.util.WasmBenchmarkToolkit;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public class WasmBenchmark {
    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 10;
        public static final int WARMUP_ITERATIONS = 10;
        public static final int FORKS = 1;
    }

    private static Path testDirectory() {
        return Paths.get(WasmBenchmarkOptions.TEST_SOURCE_PATH, "emcc");
    }

    public static byte[] getBinary(String testName) throws IOException, InterruptedException {
        File watFile = Paths.get(testDirectory().toString(), testName + ".wat").toFile();
        return WasmBenchmarkToolkit.compileWatFile(watFile);
    }

    public static class WasmBenchmarkState {

        {

        }

        @Setup
        public void setup() {

        }
    }
}
