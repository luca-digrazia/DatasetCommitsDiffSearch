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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Test;

import com.oracle.truffle.llvm.test.options.TestOptions;

public abstract class BaseTestHarness {

    public static final Set<String> supportedFiles = new HashSet<>(Arrays.asList("f90", "f", "f03", "c", "cpp", "cc", "C", "m"));

    public static final Predicate<? super Path> isExecutable = f -> f.getFileName().toString().endsWith(".out");
    public static final Predicate<? super Path> isIncludeFile = f -> f.getFileName().toString().endsWith(".include");
    public static final Predicate<? super Path> isExcludeFile = f -> f.getFileName().toString().endsWith(".exclude");
    public static final Predicate<? super Path> isSulong = f -> f.getFileName().toString().endsWith(".bc");
    public static final Predicate<? super Path> isFile = f -> f.toFile().isFile();

    protected abstract Path getTestDirectory();

    protected abstract String getTestName();

    @Test
    public abstract void test() throws Exception;

    /**
     * This function can be overwritten to specify a filter on test file names. E.g. if one wants to
     * only run unoptimized files on Sulong, use <code> s.endsWith("O0.bc") </code>
     *
     * @return a filter predicate
     */
    protected Predicate<String> filterFileName() {
        if (TestOptions.TEST_FILTER != null && !TestOptions.TEST_FILTER.isEmpty()) {
            return s -> s.endsWith(TestOptions.TEST_FILTER);
        } else {
            return s -> true;
        }
    }

    public static final Collection<Object[]> collectTestCases(Path configPath, Path suiteDir, Path sourceDir) throws AssertionError {
        String testDiscoveryPath = TestOptions.TEST_DISCOVERY_PATH;
        if (testDiscoveryPath == null) {
            return collectRegularRun(configPath, suiteDir);
        } else {
            System.err.println("Running in discovery mode...");
            return collectDiscoverRun(configPath, suiteDir, sourceDir, testDiscoveryPath);
        }
    }

    public static final Collection<Object[]> collectRegularRun(Path configPath, Path suiteDir) throws AssertionError {
        Map<Path, Path> tests = getWhiteListTestFolders(configPath, suiteDir);

        // assert that all files on the whitelist exist
        List<Path> missingTests = tests.keySet().stream().filter(p -> !tests.get(p).toFile().exists()).collect(Collectors.toList());
        if (!missingTests.isEmpty()) {
            throw new AssertionError("The following tests are on the white list but not found:\n" + missingTests.stream().map(p -> p.toString()).collect(Collectors.joining("\n")));
        } else {
            System.err.println(String.format("Collected %d test folders.", tests.size()));
        }

        return tests.keySet().stream().map(f -> new Object[]{tests.get(f), f.toString()}).collect(Collectors.toList());
    }

    private static Collection<Object[]> collectDiscoverRun(Path configPath, Path suiteDir, Path sourceDir, String testDiscoveryPath) throws AssertionError {
        // rel --> abs
        Map<Path, Path> tests = getWhiteListTestFolders(configPath, suiteDir);
        // abs
        Set<Path> availableSourceFiles = getFiles(sourceDir);
        // abs
        Set<Path> compiledTests = collectTestCases(suiteDir, testDiscoveryPath);

        // abs
        Set<Path> greyList = compiledTests.stream().filter(t -> !tests.values().contains(t)).collect(Collectors.toSet());

        // rel
        Set<Path> availableSourceFilesRelative = availableSourceFiles.stream().map(e -> getRelative(sourceDir.getParent().toUri(), e.toUri())).collect(Collectors.toSet());

        List<Object[]> collectedTests = greyList.stream().map(
                        t -> new Object[]{t, availableSourceFilesRelative.stream().filter(s -> {
                            return s.toString().startsWith(getRelative(suiteDir.toUri(), t.toUri()).toString());
                        }).findAny().get().toString()}).collect(
                                        Collectors.toList());
        return collectedTests;
    }

    private static Path getRelative(URI base, URI abs) {
        return Paths.get(base.relativize(abs).toString());
    }

    private static Set<Path> collectTestCases(Path suiteDir, String testDiscoveryPath) throws AssertionError {
        try {
            return Files.walk(suiteDir).filter(isExecutable).map(f -> f.getParent()).filter(p -> p.startsWith(Paths.get(suiteDir.toString(), testDiscoveryPath))).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    /**
     * Returns a Map whitelistEntry (relative path) -> testFolder (absolute path).
     */
    public static final Map<Path, Path> getWhiteListTestFolders(Path configDir, Path suiteDirectory) {
        return getWhiteListEntries(configDir).stream().collect(Collectors.toMap(wl -> wl, wl -> Paths.get(suiteDirectory.toString(), removeFileEnding(wl.toString()))));
    }

    private static Set<Path> getWhiteListEntries(Path configDir) {
        Predicate<Path> fortranFilter;
        if (TestOptions.IGNORE_FORTRAN) {
            fortranFilter = f -> (!f.toString().trim().endsWith(".f90") && !f.toString().trim().endsWith(".F90"));
        } else {
            fortranFilter = f -> true;
        }
        try {
            return Files.walk(configDir).filter(isIncludeFile).flatMap(f -> {
                try {
                    return Files.lines(f);
                } catch (IOException e) {
                    throw new AssertionError("Error creating whitelist.", e);
                }
            }).map(s -> Paths.get(s)).filter(fortranFilter).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error creating whitelist.", e);
        }
    }

    private static String removeFileEnding(String s) {
        return s.substring(0, s.lastIndexOf('.'));
    }

    public static Set<Path> getFiles(Path source) {
        try {
            return Files.walk(source).filter(f -> supportedFiles.contains(getFileEnding(f.getFileName().toString()))).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error getting files.", e);
        }
    }

    public static String getFileEnding(String s) {
        return s.substring(s.lastIndexOf('.') + 1);
    }
}
