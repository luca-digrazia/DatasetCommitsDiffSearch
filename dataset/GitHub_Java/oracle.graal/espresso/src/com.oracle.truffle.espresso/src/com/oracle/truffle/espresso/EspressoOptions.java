/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionMap;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;

@Option.Group(EspressoLanguage.ID)
public final class EspressoOptions {

    public static final boolean RUNNING_ON_SVM = ImageInfo.inImageCode();

    private static final Path EMPTY = Paths.get("");

    /**
     * File.pathSeparator-delimited list of paths.
     */
    private static final OptionType<List<Path>> PATHS_OPTION_TYPE = new OptionType<>("Paths",
                    new Function<String, List<Path>>() {
                        @Override
                        public List<Path> apply(String paths) {
                            try {
                                return Collections.unmodifiableList(Utils.parsePaths(paths));
                            } catch (InvalidPathException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });

    private static final OptionType<List<String>> STRINGS_OPTION_TYPE = new OptionType<>("Strings",
                    new Function<String, List<String>>() {
                        @Override
                        public List<String> apply(String strings) {
                            try {
                                return Collections.unmodifiableList(Utils.parseStrings(strings));
                            } catch (InvalidPathException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });

    private static final OptionType<Path> PATH_OPTION_TYPE = new OptionType<>("Path",
                    new Function<String, Path>() {
                        @Override
                        public Path apply(String path) {
                            try {
                                return Paths.get(path);
                            } catch (InvalidPathException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });

    @Option(help = "User-defined system properties.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<OptionMap<String>> Properties = OptionKey.mapOf(String.class);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR archives, and ZIP archives to search for class files.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> Classpath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "Specifies in which module the main class is located. Can also specify the main class name by appending it after a \\\"/\\\". Example: module/classname", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<String> Module = new OptionKey<>("");

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for modules.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> ModulePath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A comma-separated list of root modules beyond the initial module.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<String>> AddModules = new OptionKey<>(Collections.emptyList(), STRINGS_OPTION_TYPE);

    @Option(help = "A comma-separated list of root modules beyond the initial module.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<String>> AddReads = new OptionKey<>(Collections.emptyList(), STRINGS_OPTION_TYPE);

    @Option(help = "A comma-separated list of root modules beyond the initial module.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<String>> AddExports = new OptionKey<>(Collections.emptyList(), STRINGS_OPTION_TYPE);

    @Option(help = "A comma-separated list of root modules beyond the initial module.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<String>> AddOpens = new OptionKey<>(Collections.emptyList(), STRINGS_OPTION_TYPE);

    @Option(help = "Installation directory for Java Runtime Environment (JRE).", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<Path> JavaHome = new OptionKey<>(EMPTY, PATH_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for Espresso's (lib)?jvm.(so|dll|dylib).", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<List<Path>> JVMLibraryPath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to search for boot class files. These are used in place of the boot class files included in the JDK.", //
                    category = OptionCategory.EXPERT) //
    public static final OptionKey<List<Path>> BootClasspath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to append to the front of the default bootstrap class path.", //
                    category = OptionCategory.EXPERT) //
    public static final OptionKey<List<Path>> BootClasspathAppend = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to prepend to the end of the default bootstrap class path.", //
                    category = OptionCategory.EXPERT) //
    public static final OptionKey<List<Path>> BootClasspathPrepend = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for user libraries.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> JavaLibraryPath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for system libraries.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> BootLibraryPath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for extensions.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> ExtDirs = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "Enable assertions.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> EnableAssertions = new OptionKey<>(false);

    @Option(help = "Enable system assertions.", //
                    category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> EnableSystemAssertions = new OptionKey<>(false);

    public enum SpecCompliancyMode {
        STRICT,
        HOTSPOT
    }

    private static final OptionType<SpecCompliancyMode> SPEC_COMPLIANCY_OPTION_TYPE = new OptionType<>("SpecCompliancy",
                    new Function<String, SpecCompliancyMode>() {
                        @Override
                        public SpecCompliancyMode apply(String s) {
                            try {
                                return SpecCompliancyMode.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("--java.SpecCompliancy: Mode can be 'strict' or 'hotspot'.");
                            }
                        }
                    });

    @Option(help = "Force mimicking of hotspot behavior on unrespected specs points", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<SpecCompliancyMode> SpecCompliancy = new OptionKey<>(SpecCompliancyMode.HOTSPOT, SPEC_COMPLIANCY_OPTION_TYPE);

    public enum VerifyMode {
        NONE,
        REMOTE, // Verifies all bytecodes not loaded by the bootstrap class loader.
        ALL
    }

    private static final OptionType<VerifyMode> VERIFY_MODE_OPTION_TYPE = new OptionType<>("VerifyMode",
                    new Function<String, VerifyMode>() {
                        @Override
                        public VerifyMode apply(String s) {
                            try {
                                return VerifyMode.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("-Xverify: Mode can be 'none', 'remote' or 'all'.");
                            }
                        }
                    });

    @Option(help = "Sets the mode of the bytecode verifier.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<VerifyMode> Verify = new OptionKey<>(VerifyMode.REMOTE, VERIFY_MODE_OPTION_TYPE);

    @Option(help = "Speculatively inline field accessors.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> InlineFieldAccessors = new OptionKey<>(false);

    @Option(help = "Enable inlining through method handle calls.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> InlineMethodHandle = new OptionKey<>(true);

    @Option(help = "All method handle call site have a different inline cache.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> SplitMethodHandles = new OptionKey<>(false);

    @Option(help = "Enable string representation sharing between host and guest (If both have the same string representation). When enabled, reflective modifications to the underlying array of guest strings reflect on host strings. ", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> StringSharing = new OptionKey<>(true);

    public enum LivenessAnalysisMode {
        DISABLED,
        ENABLED, // Also apply liveness analysis in interpreter
        COMPILED // Only apply liveness analysis in compiled code.
    }

    private static final OptionType<LivenessAnalysisMode> LIVENESS_ANALYSIS_MODE_OPTION_TYPE = new OptionType<>("LivenessAnalysisMode",
                    new Function<String, LivenessAnalysisMode>() {
                        @Override
                        public LivenessAnalysisMode apply(String s) {
                            try {
                                return LivenessAnalysisMode.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("--java.LivenessAnalysis: Mode can be 'DISABLED', 'ENABLED' or 'COMPILED'.");
                            }
                        }
                    });

    @Option(help = "Controls the static liveness analysis of bytecodes. Liveness analysis nulls out local variables during bytecode execution if it is detected they are stale." +
                    "Options values are:" +
                    "\t- Disabled: disables liveness analysis." +
                    "\t- Enabled: performs full liveness analysis, nulling out non-live local variables even in interpreter." +
                    "\t- Compiled: performs liveness analysis, and nulls out local variables only in compiled code.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<LivenessAnalysisMode> LivenessAnalysis = new OptionKey<>(LivenessAnalysisMode.DISABLED, LIVENESS_ANALYSIS_MODE_OPTION_TYPE);

    @Option(help = "Load native libraries on a per-context, isolated linking namespace; by default enabled on the JVM, disabled on SVM.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> UseTruffleNFIIsolatedNamespace = new OptionKey<>(!RUNNING_ON_SVM);

    private static final OptionType<com.oracle.truffle.espresso.jdwp.api.JDWPOptions> JDWP_OPTIONS_OPTION_TYPE = new OptionType<>("JDWPOptions",
                    new Function<String, JDWPOptions>() {

                        private boolean yesOrNo(String key, String value) {
                            if (!"y".equals(value) && !"n".equals(value)) {
                                throw new IllegalArgumentException("Invalid JDWP option value: " + key + " can be only 'y' or 'n'.");
                            }
                            return "y".equals(value);
                        }

                        @Override
                        public JDWPOptions apply(String s) {
                            final String[] options = s.split(",");
                            String transport = null;
                            String host = null;
                            String port = null;
                            String logLevel = null;
                            boolean server = false;
                            boolean suspend = true;

                            for (String keyValue : options) {
                                String[] parts = keyValue.split("=");
                                if (parts.length != 2) {
                                    throw new IllegalArgumentException("JDWP options must be a comma separated list of key=value pairs.");
                                }
                                String key = parts[0];
                                String value = parts[1];
                                switch (key) {
                                    case "address":
                                        parts = value.split(":");
                                        String inputHost = null;
                                        String inputPort;
                                        if (parts.length == 1) {
                                            inputPort = parts[0];
                                        } else if (parts.length == 2) {
                                            inputHost = parts[0];
                                            inputPort = parts[1];
                                        } else {
                                            throw new IllegalArgumentException("Invalid JDWP option, address: " + value + ". Not a 'host:port' pair.");
                                        }
                                        long realValue;
                                        try {
                                            realValue = Long.valueOf(inputPort);
                                            if (realValue < 0 || realValue > 65535) {
                                                throw new IllegalArgumentException("Invalid JDWP option, address: " + value + ". Must be in the 0 - 65535 range.");
                                            }
                                        } catch (NumberFormatException ex) {
                                            throw new IllegalArgumentException("Invalid JDWP option, address is not a number. Must be a number in the 0 - 65535 range.");
                                        }
                                        host = inputHost;
                                        port = inputPort;
                                        break;
                                    case "transport":
                                        transport = value;
                                        break;
                                    case "server":
                                        server = yesOrNo(key, value);
                                        break;
                                    case "suspend":
                                        suspend = yesOrNo(key, value);
                                        break;
                                    case "logLevel":
                                        logLevel = value;
                                        break;
                                    default:
                                        throw new IllegalArgumentException("Invalid JDWP option: " + key + ". Supported options: 'transport', 'address', 'server' and 'suspend'.");
                                }
                            }
                            return new JDWPOptions(transport, host, port, server, suspend, logLevel);
                        }
                    });

    @Option(help = "JDWP agent Options. e.g. -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8000,suspend=y", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<JDWPOptions> JDWPOptions = new OptionKey<>(null, JDWP_OPTIONS_OPTION_TYPE);

    @Option(help = "Enable experimental java.lang.management APIs. Incur a bookkeeping overhead.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> EnableManagement = new OptionKey<>(true);

    @Option(help = "Enable support for threads. " +
                    "In single-threaded mode, Thread.start is disabled, weak references and finalizers won't be processed. " +
                    "Lock operations may be optimized away.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> MultiThreaded = new OptionKey<>(true);

    @Option(help = "Enables graceful teardown of the VM on exit. " +
                    "Rather than abruptly terminating execution, gives all leftover non-daemon thread some leeway to finish executing in guest.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> SoftExit = new OptionKey<>(false);

    @Option(help = "Guest VM exit causes the host VM to exit. This should not be used in most cases as it will take down the whole host VM abruptly.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> ExitHost = new OptionKey<>(false);

    @Option(help = "Enables espresso runtime timers.", //
                    category = OptionCategory.INTERNAL, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> EnableTimers = new OptionKey<>(false);

    @Option(help = "Enable polyglot support in Espresso.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> Polyglot = new OptionKey<>(true);

    @Option(help = "Expose the <JavaVM> binding.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
    public static final OptionKey<Boolean> ExposeNativeJavaVM = new OptionKey<>(false);

    private static final OptionType<Long> SIZE_OPTION_TYPE = new OptionType<>("Size",
                    new Function<String, Long>() {
                        private static final int K = 1024;

                        @Override
                        public Long apply(String size) {
                            int idx = 0;
                            int len = size.length();
                            for (int i = 0; i < len; i++) {
                                if (Character.isDigit(size.charAt(i))) {
                                    idx++;
                                } else {
                                    break;
                                }
                            }

                            if (idx == 0) {
                                throw new IllegalArgumentException("Not starting with digits: " + size);
                            }
                            if (len - idx > 1) {
                                throw new IllegalArgumentException("Unit prefix can be at most one character: " + size);
                            }

                            long result = Long.parseLong(size.substring(0, idx));

                            if (idx < len) {
                                switch (size.charAt(idx)) {
                                    case 'T': // fallthrough
                                    case 't':
                                        return result * K * K * K * K;
                                    case 'G': // fallthrough
                                    case 'g':
                                        return result * K * K * K;
                                    case 'M': // fallthrough
                                    case 'm':
                                        return result * K * K;
                                    case 'K': // fallthrough
                                    case 'k':
                                        return result * K;
                                    default:
                                        throw new IllegalArgumentException("Unrecognized unit prefix: " + size + " use `T`, `G`, `M`, or `k`.");
                                }
                            }
                            return result;

                        }
                    });

    @Option(help = "Maximum total size of NIO direct-buffer allocations.", //
                    category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<Long> MaxDirectMemorySize = new OptionKey<>(-1L, SIZE_OPTION_TYPE);

    @Option(help = "Stores command-line defined agents.", //
                    category = OptionCategory.INTERNAL, stability = OptionStability.STABLE) //
    public static final OptionKey<OptionMap<String>> Agents = OptionKey.mapOf(String.class);

    public static final String INCEPTION_NAME = System.getProperty("espresso.inception.name", "#");
}
