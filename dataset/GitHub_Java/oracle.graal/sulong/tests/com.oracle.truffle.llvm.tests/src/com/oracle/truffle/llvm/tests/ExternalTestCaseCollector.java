/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.options.TestOptions;

public abstract class ExternalTestCaseCollector {

    public static Collection<Object[]> collectTestCases(Path configPath, Path suiteDir, Path sourceDir) throws AssertionError {
        String testDiscoveryPath = TestOptions.TEST_DISCOVERY_PATH;
        if (testDiscoveryPath == null) {
            return collectRegularRun(configPath, suiteDir);
        } else {
            System.err.println("Running in discovery mode...");
            return collectDiscoverRun(configPath, suiteDir, sourceDir, testDiscoveryPath);
        }
    }

    private static Collection<Object[]> collectRegularRun(Path configPath, Path suiteDir) throws AssertionError {
        Map<Path, Path> tests = getWhiteListTestFolders(configPath, suiteDir);

        // assert that all files on the whitelist exist
        List<Path> missingTests = tests.keySet().stream().filter(p -> !tests.get(p).toFile().exists()).collect(Collectors.toList());
        if (!missingTests.isEmpty()) {
            throw new AssertionError("The following tests are on the white list but not found:\n" + missingTests.stream().map(p -> p.toString()).collect(Collectors.joining("\n")));
        } else {
            System.err.println(String.format("Collected %d test folders.", tests.size()));
        }

        return tests.keySet().stream().sorted().map(f -> new Object[]{tests.get(f), f.toString()}).collect(Collectors.toList());
    }

    private static Collection<Object[]> collectDiscoverRun(Path configPath, Path suiteDir, Path sourceDir, String testDiscoveryPath) throws AssertionError {
        // rel --> abs
        Map<Path, Path> tests = getWhiteListTestFolders(configPath, suiteDir);
        // abs
        Set<Path> availableSourceFiles = BaseTestHarness.getFiles(sourceDir);
        // abs
        Set<Path> compiledTests = collectTestCases(testDiscoveryPath);

        // abs
        Set<Path> greyList = compiledTests.stream().filter(t -> !tests.values().contains(t)).collect(Collectors.toSet());

        // rel
        Set<Path> availableSourceFilesRelative = availableSourceFiles.stream().map(e -> getRelative(sourceDir.getParent().toUri(), e.toUri())).collect(Collectors.toSet());

        return greyList.stream().sorted().map(
                        t -> new Object[]{t, availableSourceFilesRelative.stream().filter(s -> {
                            return s.toString().startsWith(getRelative(suiteDir.toUri(), t.toUri()).toString());
                        }).findAny().get().toString()}).collect(
                                        Collectors.toList());
    }

    private static Path getRelative(URI base, URI abs) {
        return Paths.get(base.relativize(abs).toString());
    }

    private static Set<Path> collectTestCases(String testDiscoveryPath) throws AssertionError {
        try (Stream<Path> files = Files.walk(Paths.get(testDiscoveryPath))) {
            return files.filter(BaseTestHarness.isExecutable).map(f -> f.getParent()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    /**
     * Returns a Map whitelistEntry (relative path) -> testFolder (absolute path).
     */
    private static Map<Path, Path> getWhiteListTestFolders(Path configDir, Path suiteDirectory) {
        return getWhiteListEntries(configDir).stream().collect(Collectors.toMap(wl -> wl, wl -> Paths.get(suiteDirectory.toString(), sourceFileNameToSuiteDirectory(wl.toString())).normalize()));
    }

    private static Set<Path> getWhiteListEntries(Path configDir) {
        Predicate<Path> filter = new Predicate<Path>() {

            @Override
            public boolean test(Path f) {
                if (TestOptions.FILE_EXTENSION_FILTER.length == 0) {
                    return true;
                }
                for (String e : TestOptions.FILE_EXTENSION_FILTER) {
                    String fileName = f.toString().trim();
                    if (fileName.endsWith(e)) {
                        return true;
                    }
                }
                return false;
            }
        };
        try (Stream<Path> files = Files.walk(configDir)) {
            return files.filter(BaseTestHarness.isIncludeFile).flatMap(f -> {
                try {
                    return Files.lines(f).filter(file -> file.length() > 0);
                } catch (IOException e) {
                    throw new AssertionError("Error creating whitelist.", e);
                }
            }).map(s -> Paths.get(s)).filter(filter).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error creating whitelist.", e);
        }
    }

    /**
     * Turns a test source file name (e.g. a *.c file) into the directory file name containing the
     * compiled test binaries.
     */
    private static String sourceFileNameToSuiteDirectory(String s) {
        return s + ".dir";
    }

}
