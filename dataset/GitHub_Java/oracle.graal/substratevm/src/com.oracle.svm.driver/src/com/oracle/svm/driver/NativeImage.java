/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.posix.PosixExecutableName;
import com.oracle.svm.driver.MacroOption.EnabledOption;
import com.oracle.svm.driver.MacroOption.MacroOptionKind;
import com.oracle.svm.driver.MacroOption.Registry;
import com.oracle.svm.hosted.image.AbstractBootImage.NativeImageKind;

class NativeImage {

    abstract static class OptionHandler<T extends NativeImage> {
        protected final T nativeImage;

        OptionHandler(T nativeImage) {
            this.nativeImage = nativeImage;
        }

        abstract boolean consume(Queue<String> args);
    }

    static final String oH = "-H:";
    static final String oR = "-R:";

    static final String oHClass = oH + "Class=";
    static final String oHName = oH + "Name=";
    static final String oHPath = oH + "Path=";
    static final String oHKind = oH + "Kind=";
    static final String oHCLibraryPath = oH + "CLibraryPath=";
    static final String oHOptimize = oH + "Optimize=";
    static final String oHDebug = oH + "Debug=";
    static final String oHRuntimeAssertions = oH + "RuntimeAssertions=";

    /* List arguments */
    static final String oHFeatures = oH + "Features=";
    static final String oHSubstitutionResources = oH + "SubstitutionResources=";
    static final String oHIncludeResourceBundles = oH + "IncludeResourceBundles=";
    static final String oHInterfacesForJNR = oH + "InterfacesForJNR=";
    static final String oHReflectionConfigurationFiles = oH + "ReflectionConfigurationFiles=";
    static final String oHReflectionConfigurationResources = oH + "ReflectionConfigurationResources=";
    static final String oHJNIConfigurationFiles = oH + "JNIConfigurationFiles=";

    static final String oHMaxRuntimeCompileMethods = oH + "MaxRuntimeCompileMethods=";
    static final String oHInspectServerContentPath = oH + "InspectServerContentPath=";

    static final String oRYoungGenerationSize = oR + "YoungGenerationSize=";
    static final String oROldGenerationSize = oR + "OldGenerationSize=";

    static final String oXmx = "-Xmx";
    static final String oXms = "-Xms";

    private final LinkedHashSet<String> imageBuilderArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderBootClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<String> imageBuilderJavaArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<String> customJavaArgs = new LinkedHashSet<>();
    private final LinkedHashSet<String> customImageBuilderArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> customImageClasspath = new LinkedHashSet<>();
    private final ArrayList<OptionHandler<? extends NativeImage>> optionHandlers = new ArrayList<>();

    private final Path executablePath;
    private final Path workDir;
    private final Path rootDir;
    private final Path homeDir;

    private boolean verbose = Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));

    final Registry optionRegistry;
    private final MacroOption truffleOption;

    protected NativeImage() {
        workDir = Paths.get(".").toAbsolutePath().normalize();
        assert workDir != null;
        executablePath = Paths.get((String) Compiler.command(new Object[]{PosixExecutableName.getKey()}));
        assert executablePath != null;
        Path binDir = executablePath.getParent();
        Path rootDirCandidate = binDir.getParent();
        if (rootDirCandidate.endsWith(buildContext().platform)) {
            rootDirCandidate = rootDirCandidate.getParent();
        }
        rootDir = rootDirCandidate;
        assert rootDir != null;
        String homeDirString = System.getProperty("user.home");
        homeDir = Paths.get(homeDirString);
        assert homeDir != null;

        // Default javaArgs needed for image building
        addImageBuilderJavaArgs("-server", "-d64", "-noverify");
        addImageBuilderJavaArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI");

        // Same as GRAAL_COMPILER_FLAGS in mx.substratevm/mx_substratevm.py
        int ciCompilerCount = Runtime.getRuntime().availableProcessors() <= 4 ? 2 : 4;
        addImageBuilderJavaArgs("-XX:-UseJVMCIClassLoader", "-XX:+UseJVMCICompiler", "-Dgraal.CompileGraalWithC1Only=false", "-XX:CICompilerCount=" + ciCompilerCount);
        addImageBuilderJavaArgs("-Dgraal.VerifyGraalGraphs=false", "-Dgraal.VerifyGraalGraphEdges=false", "-Dgraal.VerifyGraalPhasesSize=false", "-Dgraal.VerifyPhases=false");

        addImageBuilderJavaArgs("-Dgraal.EagerSnippets=true");

        addImageBuilderJavaArgs("-Xss10m");
        addImageBuilderJavaArgs(oXms + getXmsValue());
        addImageBuilderJavaArgs(oXmx + getXmxValue());

        addImageBuilderJavaArgs("-Duser.country=US", "-Duser.language=en");

        addImageBuilderJavaArgs("-Dsubstratevm.version=" + buildContext().svmVersion);
        if (buildContext().graalvmVersion != null) {
            addImageBuilderJavaArgs("-Dgraalvm.version=" + buildContext().graalvmVersion);
            addImageBuilderJavaArgs("-Dorg.graalvm.version=" + buildContext().graalvmVersion);
        }

        addImageBuilderJavaArgs("-Dcom.oracle.graalvm.isaot=true");

        // Generate images into the current directory
        addImageBuilderArg(oHPath + workDir);

        /* Discover supported MacroOptions */
        optionRegistry = new MacroOption.Registry(getRootDir());
        truffleOption = optionRegistry.addBuiltin("truffle");

        /* Default handler needs to be fist */
        registerOptionHandler(new DefaultOptionHandler(this));
        registerOptionHandler(new MacroOptionHandler(this));
    }

    protected void registerOptionHandler(OptionHandler<? extends NativeImage> handler) {
        optionHandlers.add(handler);
    }

    Path getRootDir() {
        return rootDir;
    }

    Path getHomeDir() {
        return homeDir;
    }

    static void ensureDirectoryExists(Path dir) {
        if (Files.exists(dir)) {
            if (!Files.isDirectory(dir)) {
                throw showError("File " + dir + " is not a directory");
            }
        } else {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw showError("Could not create directory " + dir);
            }
        }
    }

    private void prepareImageBuildArgs() {
        Path svmDir = getRootDir().resolve("lib/svm");
        getJars(svmDir.resolve("builder")).forEach(this::addImageBuilderClasspath);
        getJars(svmDir).forEach(this::addImageClasspath);
        Path clibrariesDir = svmDir.resolve("clibraries").resolve(buildContext().platform);
        addImageBuilderArg(oHCLibraryPath + clibrariesDir);
        if (Files.isDirectory(svmDir.resolve("inspect"))) {
            addImageBuilderArg(oHInspectServerContentPath + svmDir.resolve("inspect"));
        }

        Path jvmciDir = getRootDir().resolve("lib/jvmci");
        getJars(jvmciDir).forEach((Consumer<? super Path>) this::addImageBuilderClasspath);
        try {
            addImageBuilderJavaArgs(Files.list(jvmciDir)
                            .filter(f -> f.getFileName().toString().toLowerCase().endsWith("graal.jar"))
                            .map(this::canonicalize)
                            .map(Path::toString)
                            .collect(Collectors.joining(":", "-Djvmci.class.path.append=", "")));
        } catch (IOException e) {
            showError("Unable to use jar-files from directory " + jvmciDir, e);
        }

        Path bootDir = getRootDir().resolve("lib/boot");
        getJars(bootDir).forEach((Consumer<? super Path>) this::addImageBuilderBootClasspath);
    }

    private void applyOptionArgs() {
        optionRegistry.applyOptions(this);

        /* Determine if truffle is needed- any MacroOption of kind Language counts */
        LinkedHashSet<EnabledOption> enabledLanguages = optionRegistry.getEnabledOptions(MacroOptionKind.Language);
        for (EnabledOption enabledOption : optionRegistry.getEnabledOptions()) {
            if (!MacroOptionKind.Language.equals(enabledOption.getOption().kind) && enabledOption.getProperty("LauncherClass") != null) {
                /* Also identify non-Language MacroOptions as Language if LauncherClass is set */
                enabledLanguages.add(enabledOption);
            }
        }

        if (!enabledLanguages.isEmpty() || optionRegistry.getEnabledOption(truffleOption) != null) {
            enableTruffle();
        }

        /* Create a polyglot image if we have more than one LauncherClass. */
        Set<String> launcherClasses = enabledLanguages.stream()
                        .map(lang -> lang.getProperty("LauncherClass"))
                        .filter(Objects::nonNull).collect(Collectors.toSet());
        if (launcherClasses.size() > 1) {
            /* Use polyglot as image name */
            replaceArg(imageBuilderArgs, oHName, "polyglot");
            /* and the PolyglotLauncher as main class */
            replaceArg(imageBuilderArgs, oHClass, "org.graalvm.launcher.PolyglotLauncher");
            /* Collect the launcherClasses for enabledLanguages. */
            addImageBuilderJavaArgs("-Dcom.oracle.graalvm.launcher.launcherclasses=" + launcherClasses.stream().collect(Collectors.joining(",")));
        }

        /* Provide more memory for image building if we have more than one language. */
        if (enabledLanguages.size() > 1) {
            long baseMemRequirements = parseSize("4g");
            long memRequirements = baseMemRequirements + enabledLanguages.size() * parseSize("1g");
            /* Add mem-requirement for polyglot building - gets further consolidated (use max) */
            addImageBuilderJavaArgs(oXmx + memRequirements);
        }
    }

    private void enableTruffle() {
        Path truffleDir = getRootDir().resolve("lib/truffle");
        addImageBuilderBootClasspath(truffleDir.resolve("truffle-api.jar"));
        addImageBuilderJavaArgs("-Dgraalvm.locatorDisabled=true");
        addImageBuilderJavaArgs("-Dtruffle.TrustAllTruffleRuntimeProviders=true"); // GR-7046

        Path graalvmDir = getRootDir().resolve("lib/graalvm");
        getJars(graalvmDir).forEach((Consumer<? super Path>) this::addImageClasspath);
    }

    protected static boolean replaceArg(Collection<String> args, String argPrefix, String argSuffix) {
        boolean elementsRemoved = args.removeIf(arg -> arg.startsWith(argPrefix));
        args.add(argPrefix + argSuffix);
        return elementsRemoved;
    }

    private static <T> T consolidateArgs(Collection<String> args, String argPrefix,
                    Function<String, T> fromSuffix, Function<T, String> toSuffix,
                    Supplier<T> init, BiFunction<T, T, T> combiner) {
        T consolidatedValue = null;
        for (String arg : args) {
            if (arg.startsWith(argPrefix)) {
                if (consolidatedValue == null) {
                    consolidatedValue = init.get();
                }
                consolidatedValue = combiner.apply(consolidatedValue, fromSuffix.apply(arg.substring(argPrefix.length())));
            }
        }
        if (consolidatedValue != null) {
            replaceArg(args, argPrefix, toSuffix.apply(consolidatedValue));
        }
        return consolidatedValue;
    }

    private static LinkedHashSet<String> collectListArgs(Collection<String> args, String argPrefix, String delimiter) {
        LinkedHashSet<String> allEntries = new LinkedHashSet<>();
        for (String arg : args) {
            if (arg.startsWith(argPrefix)) {
                String argEntriesRaw = arg.substring(argPrefix.length());
                if (!argEntriesRaw.isEmpty()) {
                    allEntries.addAll(Arrays.asList(argEntriesRaw.split(delimiter)));
                }
            }
        }
        return allEntries;
    }

    private static void consolidateListArgs(Collection<String> args, String argPrefix, String delimiter, Function<String, String> mapFunc) {
        LinkedHashSet<String> allEntries = collectListArgs(args, argPrefix, delimiter);
        if (!allEntries.isEmpty()) {
            replaceArg(args, argPrefix, allEntries.stream().map(mapFunc).collect(Collectors.joining(delimiter)));
        }
    }

    private void completeImageBuildArgs(String[] args) {
        List<String> leftoverArgs = processNativeImageArgs(args);

        applyOptionArgs();

        /* If no customImageClasspath was specified put "." on classpath */
        if (customImageClasspath.isEmpty()) {
            addCustomImageClasspath(Paths.get("."));
        }
        imageClasspath.addAll(customImageClasspath);

        /* Perform JavaArgs consolidation - take the maximum of -Xmx, minimum of -Xms */
        consolidateArgs(imageBuilderJavaArgs, oXmx, NativeImage::parseSize, String::valueOf, () -> 0L, Math::max);
        consolidateArgs(imageBuilderJavaArgs, oXms, NativeImage::parseSize, String::valueOf, () -> parseSize(getXmsValue()), Math::min);

        /* After JavaArgs consolidation add the user provided JavaArgs */
        addImageBuilderJavaArgs(customJavaArgs.toArray(new String[0]));
        /* Append user provided imageBuilderArgs to imageBuilderArgs */
        imageBuilderArgs.addAll(customImageBuilderArgs);

        /* Perform option consolidation of imageBuilderArgs */
        consolidateArgs(imageBuilderArgs, oHMaxRuntimeCompileMethods, Integer::parseInt, String::valueOf, () -> 0, Integer::sum);
        consolidateArgs(imageBuilderArgs, oRYoungGenerationSize, NativeImage::parseSize, String::valueOf, () -> 0L, Math::max);
        consolidateArgs(imageBuilderArgs, oROldGenerationSize, NativeImage::parseSize, String::valueOf, () -> 0L, Math::max);
        consolidateListArgs(imageBuilderArgs, oHCLibraryPath, ",", s -> canonicalize(Paths.get(s)).toString());
        consolidateListArgs(imageBuilderArgs, oHSubstitutionResources, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHIncludeResourceBundles, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHInterfacesForJNR, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHReflectionConfigurationFiles, ",", s -> canonicalize(Paths.get(s)).toString());
        consolidateListArgs(imageBuilderArgs, oHReflectionConfigurationResources, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHJNIConfigurationFiles, ",", s -> canonicalize(Paths.get(s)).toString());
        consolidateListArgs(imageBuilderArgs, oHFeatures, ",", Function.identity());

        BiFunction<String, String, String> takeLast = (a, b) -> b;
        consolidateArgs(imageBuilderArgs, oHPath, Function.identity(), Function.identity(), () -> null, takeLast);
        consolidateArgs(imageBuilderArgs, oHName, Function.identity(), Function.identity(), () -> null, takeLast);
        String mainClass = consolidateArgs(imageBuilderArgs, oHClass, Function.identity(), Function.identity(), () -> null, takeLast);
        String imageKind = consolidateArgs(imageBuilderArgs, oHKind, Function.identity(), Function.identity(), () -> null, takeLast);
        boolean buildExecutable = !NativeImageKind.SHARED_LIBRARY.name().equals(imageKind);

        if (buildExecutable) {
            List<String> extraImageArgs = new ArrayList<>();
            ListIterator<String> leftoverArgsItr = leftoverArgs.listIterator();
            while (leftoverArgsItr.hasNext()) {
                String leftoverArg = leftoverArgsItr.next();
                if (!leftoverArg.startsWith("-")) {
                    leftoverArgsItr.remove();
                    extraImageArgs.add(leftoverArg);
                }
            }

            /* Main-class from customImageBuilderArgs counts as explicitMainClass */
            boolean explicitMainClass = customImageBuilderArgs.stream().anyMatch(arg -> arg.startsWith(oHClass));

            if (extraImageArgs.isEmpty()) {
                if (mainClass == null || mainClass.isEmpty()) {
                    showError("Please specify class containing the main entry point method. (see -help)");
                }
            } else {
                /* extraImageArgs main-class overrules previous main-class specification */
                explicitMainClass = true;
                mainClass = extraImageArgs.remove(0);
                replaceArg(imageBuilderArgs, oHClass, mainClass);
            }

            if (extraImageArgs.isEmpty()) {
                /* No explicit image name, define image name by other means */
                if (customImageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHName))) {
                    /* Also no explicit image name given as customImageBuilderArgs */
                    if (explicitMainClass) {
                        /* Use main-class lower case as image name */
                        replaceArg(imageBuilderArgs, oHName, mainClass.toLowerCase());
                    } else if (imageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHName))) {
                        /* Although very unlikely, report missing image-name if needed. */
                        showError("Missing image-name. Use " + oHName + "<imagename> to provide one.");
                    }
                }
            } else {
                /* extraImageArgs executable name overrules previous specification */
                replaceArg(imageBuilderArgs, oHName, extraImageArgs.remove(0));
            }
        }

        if (!leftoverArgs.isEmpty()) {
            showError(leftoverArgs.stream().collect(Collectors.joining(", ", "Unhandled leftover args: [", "]")));
        }

        buildImage(imageBuilderJavaArgs, imageBuilderBootClasspath, imageBuilderClasspath, imageBuilderArgs, imageClasspath);
    }

    protected void buildImage(LinkedHashSet<String> javaArgs, LinkedHashSet<Path> bcp, LinkedHashSet<Path> cp, LinkedHashSet<String> imageArgs, LinkedHashSet<Path> imagecp) {
        /* Construct ProcessBuilder command from final arguments */
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = pb.command();
        command.add(getJavaHome().resolve("bin/java").toString());
        if (!bcp.isEmpty()) {
            command.add(bcp.stream().map(Path::toString).collect(Collectors.joining(":", "-Xbootclasspath/a:", "")));
        }
        command.addAll(Arrays.asList("-cp", cp.stream().map(Path::toString).collect(Collectors.joining(":"))));
        command.addAll(javaArgs);
        command.add("com.oracle.svm.hosted.NativeImageGeneratorRunner");
        LinkedHashSet<Path> fullimagecp = new LinkedHashSet<>(cp);
        fullimagecp.addAll(imagecp);
        command.addAll(Arrays.asList("-imagecp", fullimagecp.stream().map(Path::toString).collect(Collectors.joining(":"))));
        command.addAll(imageArgs);

        showVerboseMessage(verbose, "Executing [");
        showVerboseMessage(verbose, command.stream().collect(Collectors.joining(" \\\n")));
        showVerboseMessage(verbose, "]");

        try {
            Process p = pb.inheritIO().start();
            int exitStatus = p.waitFor();
            if (exitStatus != 0) {
                showError("Image building with exit status " + exitStatus);
            }
        } catch (IOException | InterruptedException e) {
            showError(e.getMessage());
        }
    }

    public static void main(String[] args) {
        NativeImage nativeImage = new NativeImageServer();

        try {
            if (args.length == 0) {
                nativeImage.showMessage(buildContext().usageText);
                System.exit(0);
            }

            nativeImage.prepareImageBuildArgs();
            nativeImage.completeImageBuildArgs(args);
        } catch (NativeImageError e) {
            // Checkstyle: stop
            nativeImage.show(System.err::println, "Error: " + e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                nativeImage.show(System.err::println, "Caused by: " + cause);
                cause = cause.getCause();
            }
            // Checkstyle: resume
            System.exit(1);
        }
    }

    @Fold
    static NativeImageBuildContext buildContext() {
        return ImageSingletons.lookup(NativeImageBuildContext.class);
    }

    private Path canonicalize(Path path) {
        Path absolutePath = path.isAbsolute() ? path : workDir.resolve(path);
        try {
            Path realPath = absolutePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isReadable(realPath)) {
                showError("Path entry " + path.toString() + " is not readable");
            }
            return realPath;
        } catch (IOException e) {
            throw showError("Invalid Path entry " + path.toString(), e);
        }
    }

    Path getJavaHome() {
        Path javaHomePath = getRootDir().getParent();
        if (Files.isExecutable(javaHomePath.resolve(Paths.get("bin/java")))) {
            return javaHomePath;
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw showError("Environment variable JAVA_HOME is not set");
        }
        javaHomePath = Paths.get(javaHome);
        if (!Files.isExecutable(javaHomePath.resolve(Paths.get("bin/java")))) {
            throw showError("Environment variable JAVA_HOME does not refer to a directory with a bin/java executable");
        }
        return javaHomePath;
    }

    void addImageBuilderClasspath(Path classpath) {
        imageBuilderClasspath.add(canonicalize(classpath));
    }

    void addImageBuilderBootClasspath(Path classpath) {
        imageBuilderBootClasspath.add(canonicalize(classpath));
    }

    void addImageBuilderJavaArgs(String... javaArgs) {
        addImageBuilderJavaArgs(Arrays.asList(javaArgs));
    }

    void addImageBuilderJavaArgs(Collection<String> javaArgs) {
        imageBuilderJavaArgs.addAll(javaArgs);
    }

    void addImageBuilderArg(String arg) {
        imageBuilderArgs.add(arg);
    }

    void addImageClasspath(Path classpath) {
        imageClasspath.add(canonicalize(classpath));
    }

    void addCustomImageClasspath(Path classpath) {
        customImageClasspath.add(canonicalize(classpath));
    }

    void addCustomJavaArgs(String javaArg) {
        customJavaArgs.add(javaArg);
    }

    void addCustomImageBuilderArgs(String arg) {
        customImageBuilderArgs.add(arg);
    }

    void setVerbose(boolean val) {
        verbose = val;
    }

    boolean isVerbose() {
        return verbose;
    }

    void showVerboseMessage(boolean show, String message) {
        // Checkstyle: stop
        if (show) {
            show(System.out::println, message);
        }
        // Checkstyle: resume
    }

    void showMessage(String message) {
        // Checkstyle: stop
        show(System.out::println, message);
        // Checkstyle: resume
    }

    void showMessagePart(String message) {
        // Checkstyle: stop
        show(s -> {
            System.out.print(s);
            System.out.flush();
        }, message);
        // Checkstyle: resume
    }

    void showWarning(String message) {
        // Checkstyle: stop
        show(System.err::println, "Warning: " + message);
        // Checkstyle: resume
    }

    @SuppressWarnings("serial")
    static final class NativeImageError extends Error {
        private NativeImageError(String message) {
            super(message);
        }

        private NativeImageError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Error showError(String message) {
        throw new NativeImageError(message);
    }

    static Error showError(String message, Throwable cause) {
        throw new NativeImageError(message, cause);
    }

    private void show(Consumer<String> printFunc, String message) {
        String result = message;
        result = result.replaceAll("\\$\\{TOOL_NAME\\}", executablePath.getFileName().toString());
        printFunc.accept(result);
    }

    static List<Path> getJars(Path dir) {
        try {
            return Files.list(dir).filter(f -> f.getFileName().toString().toLowerCase().endsWith(".jar")).collect(Collectors.toList());
        } catch (IOException e) {
            showError("Unable to use jar-files from directory " + dir, e);
        }
        return Collections.emptyList();
    }

    private List<String> processNativeImageArgs(String[] args) {
        List<String> leftoverArgs = new ArrayList<>();
        Queue<String> arguments = new ArrayDeque<>(Arrays.asList(args));
        while (!arguments.isEmpty()) {
            boolean consumed = false;
            for (int index = optionHandlers.size() - 1; index >= 0; --index) {
                OptionHandler<? extends NativeImage> handler = optionHandlers.get(index);
                int numArgs = arguments.size();
                if (handler.consume(arguments)) {
                    assert arguments.size() < numArgs : "OptionHandler pretends to consume argument(s) but isn't: " + handler.getClass().getName();
                    consumed = true;
                    break;
                }
            }
            if (!consumed) {
                leftoverArgs.add(arguments.poll());
            }
        }
        return leftoverArgs;
    }

    protected String getXmsValue() {
        return "1g";
    }

    protected String getXmxValue() {
        UnsignedWord memMax = PhysicalMemory.size().unsignedDivide(10).multiply(8);
        String maxXmx = "14g";
        if (memMax.aboveOrEqual(Word.unsigned(parseSize(maxXmx)))) {
            return maxXmx;
        }
        return Long.toUnsignedString(memMax.rawValue());
    }

    /* Taken from org.graalvm.compiler.options.OptionsParser.parseLong(String) */
    private static long parseSize(String v) {
        String valueString = v.toLowerCase();
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }

    static Map<String, String> loadProperties(Path propertiesPath) {
        Properties properties = new Properties();
        File propertiesFile = propertiesPath.toFile();
        if (propertiesFile.canRead()) {
            try (FileReader reader = new FileReader(propertiesFile)) {
                properties.load(reader);
            } catch (Exception e) {
                showError("Could not read properties-file: " + propertiesFile, e);
            }
        }
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return Collections.unmodifiableMap(map);
    }

    protected void deleteAllFiles(Path toDelete) {
        try {
            Files.walk(toDelete).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            if (isVerbose()) {
                showMessage("Could not recursively delete path: " + toDelete);
                e.printStackTrace();
            }
        }
    }
}
