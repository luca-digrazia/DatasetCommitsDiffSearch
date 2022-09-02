/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libespresso;

import static com.oracle.truffle.espresso.libespresso.jniapi.JNIErrors.JNI_ERR;

import java.io.File;
import java.io.PrintStream;
import java.util.logging.Level;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.word.Pointer;

import com.oracle.truffle.espresso.libespresso.jniapi.JNIErrors;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMInitArgs;
import com.oracle.truffle.espresso.libespresso.jniapi.JNIJavaVMOption;

public final class Arguments {
    private static final PrintStream STDERR = System.err;

    private static final String JAVA_PROPS = "java.Properties.";

    private Arguments() {
    }

    private static class ModulePropertyCounter {
        ModulePropertyCounter(Context.Builder builder) {
            this.builder = builder;
        }

        private static final String JDK_MODULES_PREFIX = "jdk.module.";

        private static final String ADD_MODULES = JDK_MODULES_PREFIX + "addmods";
        private static final String ADD_EXPORTS = JDK_MODULES_PREFIX + "addexports";
        private static final String ADD_OPENS = JDK_MODULES_PREFIX + "addopens";
        private static final String ADD_READS = JDK_MODULES_PREFIX + "addreads";

        private static final String MODULE_PATH = JDK_MODULES_PREFIX + "path";
        private static final String UPGRADE_PATH = JDK_MODULES_PREFIX + "upgrade.path";
        private static final String LIMIT_MODS = JDK_MODULES_PREFIX + "limitmods";

        private static final String[] KNOWN_OPTIONS = new String[]{
                        ADD_MODULES,
                        ADD_EXPORTS,
                        ADD_OPENS,
                        ADD_READS,
                        MODULE_PATH,
                        UPGRADE_PATH,
                        LIMIT_MODS,
        };

        private final Context.Builder builder;

        private int addModules = 0;
        private int addExports = 0;
        private int addOpens = 0;
        private int addReads = 0;

        void addModules(String value) {
            addNumbered(ADD_MODULES, value, addModules++);
        }

        void addExports(String value) {
            addNumbered(ADD_EXPORTS, value, addExports++);
        }

        void addOpens(String value) {
            addNumbered(ADD_OPENS, value, addOpens++);
        }

        void addReads(String value) {
            addNumbered(ADD_READS, value, addReads++);
        }

        void addNumbered(String prop, String value, int count) {
            String key = JAVA_PROPS + prop + "." + count;
            builder.option(key, value);
        }

        boolean isModulesOption(String prop) {
            if (prop.startsWith(JDK_MODULES_PREFIX)) {
                for (String known : KNOWN_OPTIONS) {
                    if (prop.equals(known)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static int setupContext(Context.Builder builder, JNIJavaVMInitArgs args) {
        Pointer p = (Pointer) args.getOptions();
        int count = args.getNOptions();
        String classpath = null;
        String bootClasspathPrepend = null;
        String bootClasspathAppend = null;

        Native nativeAccess = new Native();
        ModulePropertyCounter modulePropHandler = new ModulePropertyCounter(builder);
        boolean experimentalOptions = checkExperimental(args);
        boolean ignoreUnrecognized = false;

        for (int i = 0; i < count; i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            try {
                if (str.isNonNull()) {
                    String optionString = CTypeConversion.toJavaString(option.getOptionString());
                    if (optionString.startsWith("-Xbootclasspath:")) {
                        bootClasspathPrepend = null;
                        bootClasspathAppend = null;
                        builder.option("java.BootClasspath", optionString.substring("-Xbootclasspath:".length()));
                    } else if (optionString.startsWith("-Xbootclasspath/a:")) {
                        bootClasspathAppend = appendPath(bootClasspathAppend, optionString.substring("-Xbootclasspath/a:".length()));
                    } else if (optionString.startsWith("-Xbootclasspath/p:")) {
                        bootClasspathPrepend = prependPath(optionString.substring("-Xbootclasspath/p:".length()), bootClasspathPrepend);
                    } else if (optionString.startsWith("-Xverify:")) {
                        String mode = optionString.substring("-Xverify:".length());
                        builder.option("java.Verify", mode);
                    } else if (optionString.startsWith("-Xrunjdwp:")) {
                        String value = optionString.substring("-Xrunjdwp:".length());
                        builder.option("java.JDWPOptions", value);
                    } else if (optionString.startsWith("-XX:MaxDirectMemorySize=")) {
                    String value = optionString.substring("-XX:MaxDirectMemorySize=".length());
                    builder.option("java.MaxDirectMemorySize", value);
                } else if (optionString.startsWith("-agentlib:jdwp=")) {
                        String value = optionString.substring("-agentlib:jdwp=".length());
                        builder.option("java.JDWPOptions", value);
                    } else if (optionString.startsWith("-D")) {
                        String key = optionString.substring("-D".length());
                        int splitAt = key.indexOf("=");
                        String value = "";
                        if (splitAt >= 0) {
                            value = key.substring(splitAt + 1);
                            key = key.substring(0, splitAt);
                        }
                        if (modulePropHandler.isModulesOption(key)) {
                            warn("Ignoring system property -D" + key + " that is reserved for internal use.");
                            continue;
                        }
                        switch (key) {
                            case "espresso.library.path":
                                builder.option("java.EspressoLibraryPath", value);
                                break;
                            case "java.library.path":
                                builder.option("java.JavaLibraryPath", value);
                                break;
                            case "java.class.path":
                                classpath = value;
                                break;
                            case "java.ext.dirs":
                                builder.option("java.ExtDirs", value);
                                break;
                            case "sun.boot.class.path":
                                builder.option("java.BootClasspath", value);
                                break;
                            case "sun.boot.library.path":
                                builder.option("java.BootLibraryPath", value);
                                break;
                        }
                        builder.option(JAVA_PROPS + key, value);
                    } else if (optionString.equals("-ea") || optionString.equals("-enableassertions")) {
                        builder.option("java.EnableAssertions", "true");
                    } else if (optionString.equals("-esa") || optionString.equals("-enablesystemassertions")) {
                        builder.option("java.EnableSystemAssertions", "true");
                    } else if (optionString.startsWith("--add-reads=")) {
                        modulePropHandler.addReads(optionString.substring("--add-reads=".length()));
                    } else if (optionString.startsWith("--add-exports=")) {
                        modulePropHandler.addExports(optionString.substring("--add-exports=".length()));
                    } else if (optionString.startsWith("--add-opens=")) {
                        modulePropHandler.addOpens(optionString.substring("--add-opens=".length()));
                    } else if (optionString.startsWith("--add-modules=")) {
                        modulePropHandler.addModules(optionString.substring("--add-modules=".length()));
                    } else if (optionString.startsWith("--module-path=")) {
                        builder.option(JAVA_PROPS + "jdk.module.path", optionString.substring("--module-path=".length()));
                    } else if (optionString.startsWith("--upgrade-module-path=")) {
                        builder.option(JAVA_PROPS + "jdk.module.upgrade.path", optionString.substring("--upgrade-module-path=".length()));
                    } else if (optionString.startsWith("--limit-modules=")) {
                        builder.option(JAVA_PROPS + "jdk.module.limitmods", optionString.substring("--limit-modules=".length()));
                    } else if (isXOption(optionString)) {
                        RuntimeOptions.set(optionString.substring("-X".length()), null);
                    } else if (optionString.equals("-XX:+IgnoreUnrecognizedVMOptions")) {
                        ignoreUnrecognized = true;
                    } else if (optionString.equals("-XX:-IgnoreUnrecognizedVMOptions")) {
                        ignoreUnrecognized = false;
                    } else if (optionString.startsWith("--vm.")) {
                        handleVMOption(nativeAccess, optionString);
                    } else if (optionString.startsWith("-XX:")) {
                        handleXXArg(builder, optionString, experimentalOptions, nativeAccess);
                    } else if (isExperimentalFlag(optionString)) {
                        // skip: previously handled
                    } else if (optionString.equals("--polyglot")) {
                        // skip: handled by mokapot
                    } else {
                        parsePolyglotOption(builder, optionString, experimentalOptions);
                    }
                }
            } catch (ArgumentException e) {
                if (!ignoreUnrecognized) {
                    // Failed to parse
                    warn(e.getMessage());
                    return JNI_ERR();
                }
            }
        }

        if (bootClasspathPrepend != null) {
            builder.option("java.BootClasspathPrepend", bootClasspathPrepend);
        }
        if (bootClasspathAppend != null) {
            builder.option("java.BootClasspathAppend", bootClasspathAppend);
        }

        // classpath provenance order:
        // (1) the java.class.path property
        if (classpath == null) {
            // (2) the environment variable CLASSPATH
            classpath = System.getenv("CLASSPATH");
            if (classpath == null) {
                // (3) the current working directory only
                classpath = ".";
            }
        }

        builder.option("java.Classpath", classpath);
        argumentProcessingDone();
        return JNIErrors.JNI_OK();
    }

    public static void handleVMOption(Native nativeAccess, String optionString) {
        nativeAccess.init(false);
        nativeAccess.setNativeOption(optionString.substring("--vm.".length()));
    }

    public static void handleXXArg(Context.Builder builder, String optionString, boolean experimental, Native nativeAccess) {
        String toPolyglot = optionString.substring("-XX:".length());
        if (toPolyglot.length() >= 1 && (toPolyglot.charAt(0) == '+' || toPolyglot.charAt(0) == '-')) {
            String value = Boolean.toString(toPolyglot.charAt(0) == '+');
            toPolyglot = "--java." + toPolyglot.substring(1) + "=" + value;
        } else {
            toPolyglot = "--java." + toPolyglot;
        }
        try {
            parsePolyglotOption(builder, toPolyglot, experimental);
            return;
        } catch (ArgumentException e) {
            if (e.isExperimental()) {
                throw abort(e.getMessage().replace(toPolyglot, optionString));
            }
            /* Ignore, and try to pass it as a vm arg */
        }
        // Pass as host vm arg
        nativeAccess.init(true);
        nativeAccess.setNativeOption(optionString.substring(1));
    }

    private static boolean checkExperimental(JNIJavaVMInitArgs args) {
        Pointer p = (Pointer) args.getOptions();
        for (int i = 0; i < args.getNOptions(); i++) {
            JNIJavaVMOption option = (JNIJavaVMOption) p.add(i * SizeOf.get(JNIJavaVMOption.class));
            CCharPointer str = option.getOptionString();
            if (str.isNonNull()) {
                String optionString = CTypeConversion.toJavaString(option.getOptionString());
                switch (optionString) {
                    case "--experimental-options":
                    case "--experimental-options=true":
                        return true;
                    case "--experimental-options=false":
                        return false;
                    default:
                }
            }
        }
        return false;
    }

    private static boolean isExperimentalFlag(String optionString) {
        // return false for "--experimental-options=[garbage]
        return optionString.equals("--experimental-options") || optionString.equals("--experimental-options=true") || optionString.equals("--experimental-options=false");
    }

    private static boolean isXOption(String optionString) {
        return optionString.startsWith("-Xms") || optionString.startsWith("-Xmx") || optionString.startsWith("-Xmn") || optionString.startsWith("-Xss");
    }

    private static void parsePolyglotOption(Context.Builder builder, String arg, boolean experimentalOptions) {
        if (arg.length() <= 2 || !arg.startsWith("--")) {
            throw abort(String.format("Unrecognized option: %s%n", arg));
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg.substring(2);
            value = null;
        } else {
            key = arg.substring(2, eqIdx);
            value = arg.substring(eqIdx + 1);
        }

        if (value == null) {
            value = "true";
        }
        int index = key.indexOf('.');
        String group = key;
        if (index >= 0) {
            group = group.substring(0, index);
        }
        if ("log".equals(group)) {
            if (key.endsWith(".level")) {
                try {
                    Level.parse(value);
                    builder.option(key, value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                }
                return;
            } else if (key.equals("log.file")) {
                throw abort("Unsupported log.file option");
            }
        }
        OptionDescriptor descriptor = findOptionDescriptor(group, key);
        if (descriptor == null) {
            descriptor = findOptionDescriptor("java", "java" + "." + key);
            if (descriptor == null) {
                throw abort(String.format("Unrecognized option: %s%n", arg));
            }
        }
        try {
            descriptor.getKey().getType().convert(value);
        } catch (IllegalArgumentException e) {
            throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
        }
        if (!experimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw abortExperimental(String.format("Option '%s' is experimental and must be enabled via '--experimental-options'%n" +
                            "Do not use experimental options in production environments.", arg));
        }
        // use the full name of the found descriptor
        builder.option(descriptor.getName(), value);
    }

    private static OptionDescriptor findOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        switch (group) {
            case "engine":
                descriptors = getTempEngine().getOptions();
                break;
            default:
                Engine engine = getTempEngine();
                if (engine.getLanguages().containsKey(group)) {
                    descriptors = engine.getLanguages().get(group).getOptions();
                } else if (engine.getInstruments().containsKey(group)) {
                    descriptors = engine.getInstruments().get(group).getOptions();
                }
                break;
        }
        if (descriptors == null) {
            return null;
        }
        return descriptors.get(key);
    }

    private static Engine tempEngine;

    private static Engine getTempEngine() {
        if (tempEngine == null) {
            tempEngine = Engine.newBuilder().useSystemProperties(false).build();
        }
        return tempEngine;
    }

    private static void argumentProcessingDone() {
        if (tempEngine != null) {
            tempEngine.close();
            tempEngine = null;
        }
    }

    private static String appendPath(String paths, String toAppend) {
        if (paths != null && paths.length() != 0) {
            return toAppend != null && toAppend.length() != 0 ? paths + File.pathSeparator + toAppend : paths;
        } else {
            return toAppend;
        }
    }

    private static String prependPath(String toPrepend, String paths) {
        if (paths != null && paths.length() != 0) {
            return toPrepend != null && toPrepend.length() != 0 ? toPrepend + File.pathSeparator + paths : paths;
        } else {
            return toPrepend;
        }
    }

    static class ArgumentException extends RuntimeException {
        private static final long serialVersionUID = 5430103471994299046L;

        private final boolean isExperimental;

        ArgumentException(String message, boolean isExperimental) {
            super(message);
            this.isExperimental = isExperimental;
        }

        public boolean isExperimental() {
            return isExperimental;
        }
    }

    static ArgumentException abort(String message) {
        throw new Arguments.ArgumentException(message, false);
    }

    static ArgumentException abortExperimental(String message) {
        throw new Arguments.ArgumentException(message, true);
    }

    static void warn(String message) {
        STDERR.println(message);
    }
}
