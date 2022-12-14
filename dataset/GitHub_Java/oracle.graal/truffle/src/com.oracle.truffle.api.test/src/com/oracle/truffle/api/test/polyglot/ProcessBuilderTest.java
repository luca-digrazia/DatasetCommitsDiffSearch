/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleProcessBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.ProcessHandler;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class ProcessBuilderTest extends AbstractPolyglotTest {

    @Test
    public void testProcessCreationDenied() throws Exception {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        setupEnv(Context.newBuilder().build());
        try {
            languageEnv.newProcessBuilder(javaExecutable.toString()).start();
            Assert.fail("SecurityException expected.");
        } catch (SecurityException se) {
            // Expected
        }
    }

    @Test
    public void testProcessCreationAllowed() throws Exception {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        setupEnv(Context.newBuilder().allowCreateProcess(true).build());
        Process p = languageEnv.newProcessBuilder(javaExecutable.toString()).start();
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroy();
        }
    }

    @Test
    public void testProcessCreationAllAccess() throws Exception {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Process p = languageEnv.newProcessBuilder(javaExecutable.toString()).start();
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroy();
        }
    }

    @Test
    public void testCustomHandlerProcessCreationDenied() throws Exception {
        MockProcessHandler testHandler = new MockProcessHandler();
        setupEnv(Context.newBuilder().processHandler(testHandler).build());
        try {
            languageEnv.newProcessBuilder("process").start();
            Assert.fail("SecurityException expected.");
        } catch (SecurityException se) {
            // Expected
        }
    }

    @Test
    public void testCustomHandlerProcessCreationAllowed() throws Exception {
        MockProcessHandler testHandler = new MockProcessHandler();
        setupEnv(Context.newBuilder().allowCreateProcess(true).processHandler(testHandler).build());
        languageEnv.newProcessBuilder("process").start();
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(1, command.getCommand().size());
        Assert.assertEquals("process", command.getCommand().get(0));
    }

    @Test
    public void testCommands() throws Exception {
        MockProcessHandler testHandler = new MockProcessHandler();
        setupEnv(Context.newBuilder().allowCreateProcess(true).processHandler(testHandler).build());
        languageEnv.newProcessBuilder("process", "param1", "param2").start();
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(Arrays.asList("process", "param1", "param2"), command.getCommand());

        languageEnv.newProcessBuilder().command("process", "param1", "param2").start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(Arrays.asList("process", "param1", "param2"), command.getCommand());

        languageEnv.newProcessBuilder().command(Arrays.asList("process", "param1", "param2")).start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(Arrays.asList("process", "param1", "param2"), command.getCommand());

        TruffleProcessBuilder builder = languageEnv.newProcessBuilder();
        builder.command().add("process");
        builder.command().add("param1");
        builder.command().add("param2");
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(Arrays.asList("process", "param1", "param2"), command.getCommand());

        builder = languageEnv.newProcessBuilder("process", "paramA", "param2");
        builder.command().set(1, "param1");
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(Arrays.asList("process", "param1", "param2"), command.getCommand());
    }

    @Test
    public void testCurrentWorkingDirectory() throws Exception {
        MockProcessHandler testHandler = new MockProcessHandler();
        setupEnv(Context.newBuilder().allowCreateProcess(true).allowIO(true).processHandler(testHandler).build());
        String workdirPath = Paths.get("/workdir").toString();
        TruffleFile workDir = languageEnv.getTruffleFile(workdirPath);
        languageEnv.newProcessBuilder("process").directory(workDir).start();
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(workdirPath, command.getDirectory());

        languageEnv.newProcessBuilder("process").start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertNull(command.getDirectory());
    }

    @Test
    public void testEnvironment() throws Exception {
        MockProcessHandler testHandler = new MockProcessHandler("k1", "v1", "k2", "v2");
        setupEnv(Context.newBuilder().allowCreateProcess(true).processHandler(testHandler).build());
        languageEnv.newProcessBuilder("process").start();
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(pairsAsMap("k1", "v1", "k2", "v2"), command.getEnvironment());

        languageEnv.newProcessBuilder("process").addToEnvironment(pairsAsMap("k3", "v3", "k4", "v4")).start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(pairsAsMap("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"), command.getEnvironment());

        TruffleProcessBuilder builder = languageEnv.newProcessBuilder("process");
        Map<String, String> env = builder.environment();
        env.put("k3", "v3");
        env.put("k4", "v4");
        builder.start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(pairsAsMap("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"), command.getEnvironment());

        builder = languageEnv.newProcessBuilder("process");
        env = builder.environment();
        env.put("k1", "vA");
        env.put("k2", "vB");
        builder.start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(pairsAsMap("k1", "vA", "k2", "vB"), command.getEnvironment());

        builder = languageEnv.newProcessBuilder("process");
        env = builder.environment();
        env.clear();
        builder.start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(Collections.emptyMap(), command.getEnvironment());
    }

    @Test
    public void testRedirects() throws Exception {
        MockProcessHandler testHandler = new MockProcessHandler();
        setupEnv(Context.newBuilder().allowCreateProcess(true).processHandler(testHandler).build());
        languageEnv.newProcessBuilder("process").start();
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertFalse(command.isRedirectErrorStream());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getInputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getOutputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getErrorRedirect());

        languageEnv.newProcessBuilder("process").redirectErrorStream(true).start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertTrue(command.isRedirectErrorStream());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getInputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getOutputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getErrorRedirect());

        languageEnv.newProcessBuilder("process").inheritIO().start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertFalse(command.isRedirectErrorStream());
        Assert.assertEquals(ProcessHandler.Redirect.INHERIT, command.getInputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.INHERIT, command.getOutputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.INHERIT, command.getErrorRedirect());

        languageEnv.newProcessBuilder("process").redirectInput(ProcessHandler.Redirect.INHERIT).start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertFalse(command.isRedirectErrorStream());
        Assert.assertEquals(ProcessHandler.Redirect.INHERIT, command.getInputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getOutputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getErrorRedirect());

        languageEnv.newProcessBuilder("process").redirectOutput(ProcessHandler.Redirect.INHERIT).start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertFalse(command.isRedirectErrorStream());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getInputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.INHERIT, command.getOutputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getErrorRedirect());

        languageEnv.newProcessBuilder("process").redirectError(ProcessHandler.Redirect.INHERIT).start();
        command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertFalse(command.isRedirectErrorStream());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getInputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.PIPE, command.getOutputRedirect());
        Assert.assertEquals(ProcessHandler.Redirect.INHERIT, command.getErrorRedirect());
    }

    private static Path getJavaExecutable() {
        String value = System.getProperty("java.home");
        if (value == null) {
            return null;
        }
        Path bin = Paths.get(value).resolve("bin");
        Path java = bin.resolve(isWindows() ? "java.exe" : "java");
        return Files.exists(java) ? java.toAbsolutePath() : null;
    }

    private static boolean isWindows() {
        String name = System.getProperty("os.name");
        return name.startsWith("Windows");
    }

    private static Map<String, String> pairsAsMap(String... keyValuePairs) {
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("envKeyValuePairs must have even length");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    private static class MockProcessHandler implements ProcessHandler {

        private final Map<String, String> env;
        private ProcessCommand lastCommand;

        MockProcessHandler(String... envKeyValuePairs) {
            env = Collections.unmodifiableMap(pairsAsMap(envKeyValuePairs));
        }

        ProcessCommand getAndCleanLastCommand() {
            return lastCommand;
        }

        @Override
        public Map<String, String> getEnvironment() {
            return env;
        }

        @Override
        public Process start(ProcessCommand command) throws IOException {
            lastCommand = command;
            return new MockProcess();
        }

        private static final class MockProcess extends Process {

            @Override
            public OutputStream getOutputStream() {
                return EmptyOutputStream.INSTANCE;
            }

            @Override
            public InputStream getInputStream() {
                return EmptyInputStream.INSTANCE;
            }

            @Override
            public InputStream getErrorStream() {
                return EmptyInputStream.INSTANCE;
            }

            @Override
            public int waitFor() throws InterruptedException {
                return exitValue();
            }

            @Override
            public int exitValue() {
                return 0;
            }

            @Override
            public void destroy() {
            }

            private static final class EmptyInputStream extends InputStream {

                static final InputStream INSTANCE = new EmptyInputStream();

                private EmptyInputStream() {
                }

                @Override
                public int read() throws IOException {
                    return -1;
                }
            }

            private static final class EmptyOutputStream extends OutputStream {

                static final OutputStream INSTANCE = new EmptyOutputStream();

                private EmptyOutputStream() {
                }

                @Override
                public void write(int b) throws IOException {
                    throw new IOException("Closed stream");
                }
            }
        }
    }
}
