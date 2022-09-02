/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher;

import java.io.BufferedOutputStream;
import static java.lang.Integer.max;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;

public abstract class Launcher {
    private static final boolean STATIC_VERBOSE = Boolean.getBoolean("org.graalvm.launcher.verbose");
    static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

    private Engine tempEngine;

    public enum VMType {
        Native,
        JVM
    }

    final Native nativeAccess;
    private final boolean verbose;

    private boolean help;
    private boolean helpDebug;
    private boolean helpExpert;
    private boolean helpTools;
    private boolean helpLanguages;
    private boolean seenPolyglot;
    private Path logFile;

    private VersionAction versionAction = VersionAction.None;

    protected enum VersionAction {
        None,
        PrintAndExit,
        PrintAndContinue
    }

    Launcher() {
        verbose = STATIC_VERBOSE || Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));
        if (IS_AOT) {
            nativeAccess = new Native();
        } else {
            nativeAccess = null;
        }
    }

    final boolean isPolyglot() {
        return seenPolyglot;
    }

    final void setPolyglot(boolean polyglot) {
        seenPolyglot = polyglot;
    }

    final Path getLogFile() {
        return logFile;
    }

    private Engine getTempEngine() {
        if (tempEngine == null) {
            tempEngine = Engine.create();
        }
        return tempEngine;
    }

    static void handleAbortException(AbortException e) {
        if (e.getMessage() != null) {
            System.err.println("ERROR: " + e.getMessage());
        }
        if (e.getCause() != null) {
            e.printStackTrace();
        }
        System.exit(e.getExitCode());
    }

    static void handlePolyglotException(PolyglotException e) {
        if (e.getMessage() != null) {
            System.err.println("ERROR: " + e.getMessage());
        }
        if (e.isInternalError()) {
            e.printStackTrace();
        }
        if (e.isExit()) {
            System.exit(e.getExitStatus());
        } else {
            System.exit(1);
        }
    }

    protected static class AbortException extends RuntimeException {
        static final long serialVersionUID = 4681646279864737876L;
        private final int exitCode;

        AbortException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        AbortException(Throwable cause, int exitCode) {
            super(null, cause);
            this.exitCode = exitCode;
        }

        int getExitCode() {
            return exitCode;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Exits the launcher, indicating success.
     *
     * This exits by throwing an {@link AbortException}.
     */
    protected final AbortException exit() {
        return exit(0);
    }

    /**
     * Exits the launcher with the provided exit code.
     *
     * This exits by throwing an {@link AbortException}.
     *
     * @param exitCode the exit code of the launcher process.
     */
    protected final AbortException exit(int exitCode) {
        return abort((String) null, exitCode);
    }

    /**
     * Exits the launcher, indicating failure.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param message an error message that will be printed to {@linkplain System#err stderr}. If
     *            null, nothing will be printed.
     */
    protected final AbortException abort(String message) {
        return abort(message, 1);
    }

    /**
     * Exits the launcher, with the provided exit code.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param message an error message that will be printed to {@linkplain System#err stderr}. If
     *            null, nothing will be printed.
     * @param exitCode the exit code of the launcher process.
     */
    @SuppressWarnings("static-method")
    protected final AbortException abort(String message, int exitCode) {
        throw new AbortException(message, exitCode);
    }

    /**
     * Exits the launcher, indicating failure because of the provided {@link Throwable}.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param t the exception that causes the launcher to abort.
     */
    protected final AbortException abort(Throwable t) {
        return abort(t, 255);
    }

    /**
     * Exits the launcher with the provided exit code because of the provided {@link Throwable}.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param t the exception that causes the launcher to abort.
     * @param exitCode the exit code of the launcher process.
     */
    protected final AbortException abort(Throwable t, int exitCode) {
        if (t.getCause() instanceof IOException && t.getClass() == RuntimeException.class) {
            String message = t.getMessage();
            if (message != null && !message.startsWith(t.getCause().getClass().getName() + ": ")) {
                System.err.println(message);
            }
            throw abort((IOException) t.getCause(), exitCode);
        }
        throw new AbortException(t, exitCode);
    }

    /**
     * Exits the launcher, indicating failure because of the provided {@link IOException}.
     *
     * This tries to build a helpful error message based on exception.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param e the exception that causes the launcher to abort.
     */
    protected final AbortException abort(IOException e) {
        return abort(e, 74);
    }

    /**
     * Exits the launcher with the provided exit code because of the provided {@link IOException}.
     *
     * This tries to build a helpful error message based on exception.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param e the exception that causes the launcher to abort.
     * @param exitCode the exit code of the launcher process
     */
    protected final AbortException abort(IOException e, int exitCode) {
        String message = e.getMessage();
        if (message != null) {
            if (e instanceof NoSuchFileException) {
                throw abort("Not such file: " + message, exitCode);
            } else if (e instanceof AccessDeniedException) {
                throw abort("Access denied: " + message, exitCode);
            } else {
                throw abort(message + " (" + e.getClass().getSimpleName() + ")", exitCode);
            }
        }
        throw abort((Throwable) e, exitCode);
    }

    /**
     * Exits the launcher, indicating failure because of an invalid argument.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param argument the problematic argument.
     * @param message an error message that is printed to {@linkplain System#err stderr}.
     */
    protected final AbortException abortInvalidArgument(String argument, String message) {
        return abortInvalidArgument(argument, message, 2);
    }

    /**
     * Exits the launcher with the provided exit code because of an invalid argument.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param argument the problematic argument.
     * @param message an error message that is printed to {@linkplain System#err stderr}.
     * @param exitCode the exit code of the launcher process.
     */
    protected final AbortException abortInvalidArgument(String argument, String message, int exitCode) {
        Set<String> allArguments = collectAllArguments();
        int equalIndex;
        String testString = argument;
        if ((equalIndex = argument.indexOf('=')) != -1) {
            testString = argument.substring(0, equalIndex);
        }

        List<String> matches = fuzzyMatch(allArguments, testString, 0.7f);
        if (matches.isEmpty()) {
            // try even harder
            matches = fuzzyMatch(allArguments, testString, 0.5f);
        }

        StringBuilder sb = new StringBuilder();
        if (message != null) {
            sb.append(message);
        }
        if (!matches.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append("Did you mean one of the following arguments?").append(System.lineSeparator());
            Iterator<String> iterator = matches.iterator();
            while (true) {
                String match = iterator.next();
                sb.append("  ").append(match);
                if (iterator.hasNext()) {
                    sb.append(System.lineSeparator());
                } else {
                    break;
                }
            }
        }
        if (sb.length() > 0) {
            throw abort(sb.toString(), exitCode);
        } else {
            throw exit(exitCode);
        }
    }

    /**
     * Prints a help message to {@linkplain System#out stdout}. This only prints options that belong
     * to categories {@code maxCategory or less}.
     *
     * @param maxCategory the maximum category of options that should be printed.
     */
    protected abstract void printHelp(OptionCategory maxCategory);

    /**
     * Prints version information on {@linkplain System#out stdout}.
     */
    protected abstract void printVersion();

    /**
     * Add all known arguments to the {@code options} list.
     *
     * @param options list to which valid arguments must be added.
     */
    protected abstract void collectArguments(Set<String> options);

    private String executableName(String basename) {
        switch (OS.current) {
            case Linux:
            case Darwin:
            case Solaris:
                return basename;
            default:
                throw abort("executableName: OS not supported: " + OS.current);
        }
    }

    /**
     * Prints version information about all known {@linkplain Language languages} and
     * {@linkplain Instrument instruments} on {@linkplain System#out stdout}.
     */
    protected static void printPolyglotVersions() {
        Engine engine = Engine.create();
        System.out.println("GraalVM Polyglot Engine Version " + engine.getVersion());
        Path graalVMHome = Engine.findHome();
        if (graalVMHome != null) {
            System.out.println("GraalVM Home " + graalVMHome);
        }
        printLanguages(engine, true);
        printInstruments(engine, true);
    }

    /**
     * Returns the name of the main class for this launcher.
     *
     * Typically:
     *
     * <pre>
     * return MyLauncher.class.getName();
     * </pre>
     */
    protected String getMainClass() {
        return this.getClass().getName();
    }

    /**
     * The return value specifies the default VM when none of --jvm, --native options is used.
     *
     * @return the default VMType
     */
    protected VMType getDefaultVMType() {
        return VMType.Native;
    }

    /**
     * Returns true if the current launcher was compiled ahead-of-time to native code.
     */
    public static boolean isAOT() {
        return IS_AOT;
    }

    private boolean isVerbose() {
        return verbose;
    }

    protected boolean isGraalVMAvailable() {
        return nativeAccess != null && nativeAccess.getGraalVMHome() != null;
    }

    @SuppressWarnings("fallthrough")
    final boolean runPolyglotAction() {
        OptionCategory helpCategory = helpDebug ? OptionCategory.INTERNAL : (helpExpert ? OptionCategory.EXPERT : OptionCategory.USER);

        switch (versionAction) {
            case PrintAndContinue:
                printPolyglotVersions();
                // fall through
            case None:
                break;
            case PrintAndExit:
                printPolyglotVersions();
                return true;
        }
        boolean printDefaultHelp = help || ((helpExpert || helpDebug) && !helpTools && !helpLanguages);
        if (printDefaultHelp) {
            printHelp(helpCategory);
            // @formatter:off
            System.out.println();
            System.out.println("Runtime options:");
            if (isGraalVMAvailable()) {
                printOption("--polyglot", "Run with all other guest languages accessible.");
            }
            printOption("--native", "Run using the native launcher with limited Java access" + (this.getDefaultVMType() == VMType.Native ? " (default)" : "") + ".");
            printOption("--native.[option]", "Pass options to the native image. To see available options, use '--native.help'.");
            if (isGraalVMAvailable()) {
                printOption("--jvm", "Run on the Java Virtual Machine with Java access" + (this.getDefaultVMType() == VMType.JVM ? " (default)" : "") + ".");
                printOption("--jvm.[option]", "Pass options to the JVM; for example, '--jvm.classpath=myapp.jar'. To see available options. use '--jvm.help'.");
            }
            printOption("--help",                        "Print this help message.");
            printOption("--help:languages",              "Print options for all installed languages.");
            printOption("--help:tools",                  "Print options for all installed tools.");
            printOption("--help:expert",                 "Print additional options for experts.");
            if (helpExpert || helpDebug) {
                printOption("--help:debug",              "Print additional options for debugging.");
            }
            printOption("--version:graalvm",             "Print GraalVM version information and exit.");
            printOption("--show-version:graalvm",        "Print GraalVM version information and continue execution.");
            printOption("--log.file=<String>",           "Redirect guest languages logging into a given file.");
            printOption("--log.[logger].level=<String>", "Set language log level. Can be 'OFF', 'SEVERE', 'WARNING', 'INFO', 'CONFIG', 'FINE', 'FINER', 'FINEST' or 'ALL'.");
            // @formatter:on
            List<PrintableOption> engineOptions = new ArrayList<>();
            for (OptionDescriptor descriptor : getTempEngine().getOptions()) {
                if (!descriptor.getName().startsWith("engine.") && !descriptor.getName().startsWith("compiler.")) {
                    continue;
                }
                if (!descriptor.isDeprecated() && sameCategory(descriptor, helpCategory)) {
                    engineOptions.add(asPrintableOption(descriptor));
                }
            }
            if (!engineOptions.isEmpty()) {
                printOptions(engineOptions, "Engine options:", 2);
            }
        }

        if (helpLanguages) {
            printLanguageOptions(getTempEngine(), helpCategory);
        }

        if (helpTools) {
            printInstrumentOptions(getTempEngine(), helpCategory);
        }

        if (printDefaultHelp || helpLanguages || helpTools) {
            System.out.println("\nSee http://www.graalvm.org for more information.");
            return true;
        }

        return false;
    }

    private static void printInstrumentOptions(Engine engine, OptionCategory optionCategory) {
        Map<Instrument, List<PrintableOption>> instrumentsOptions = new HashMap<>();
        List<Instrument> instruments = sortedInstruments(engine);
        for (Instrument instrument : instruments) {
            List<PrintableOption> options = new ArrayList<>();
            for (OptionDescriptor descriptor : instrument.getOptions()) {
                if (!descriptor.isDeprecated() && sameCategory(descriptor, optionCategory)) {
                    options.add(asPrintableOption(descriptor));
                }
            }
            if (!options.isEmpty()) {
                instrumentsOptions.put(instrument, options);
            }
        }
        if (!instrumentsOptions.isEmpty()) {
            System.out.println();
            System.out.println("Tool options:");
            for (Instrument instrument : instruments) {
                List<PrintableOption> options = instrumentsOptions.get(instrument);
                if (options != null) {
                    printOptions(options, "  " + instrument.getName() + ":", 4);
                }
            }
        }
    }

    private static void printLanguageOptions(Engine engine, OptionCategory optionCategory) {
        Map<Language, List<PrintableOption>> languagesOptions = new HashMap<>();
        List<Language> languages = sortedLanguages(engine);
        for (Language language : languages) {
            List<PrintableOption> options = new ArrayList<>();
            for (OptionDescriptor descriptor : language.getOptions()) {
                if (!descriptor.isDeprecated() && sameCategory(descriptor, optionCategory)) {
                    options.add(asPrintableOption(descriptor));
                }
            }
            if (!options.isEmpty()) {
                languagesOptions.put(language, options);
            }
        }
        if (!languagesOptions.isEmpty()) {
            System.out.println();
            System.out.println("Language options:");
            for (Language language : languages) {
                List<PrintableOption> options = languagesOptions.get(language);
                if (options != null) {
                    printOptions(options, "  " + language.getName() + ":", 4);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean sameCategory(OptionDescriptor descriptor, OptionCategory optionCategory) {
        return descriptor.getCategory().ordinal() == optionCategory.ordinal() ||
                        (optionCategory.ordinal() == OptionCategory.INTERNAL.ordinal() &&
                                        descriptor.getCategory().ordinal() == OptionCategory.DEBUG.ordinal());
    }

    boolean parsePolyglotOption(String defaultOptionPrefix, Map<String, String> options, String arg) {
        switch (arg) {
            case "--help":
                help = true;
                return true;
            case "--help:debug":
                helpDebug = true;
                return true;
            case "--help:expert":
                helpExpert = true;
                return true;
            case "--help:tools":
                helpTools = true;
                return true;
            case "--help:languages":
                helpLanguages = true;
                return true;
            case "--version:graalvm":
                versionAction = VersionAction.PrintAndExit;
                return true;
            case "--show-version:graalvm":
                versionAction = VersionAction.PrintAndContinue;
                return true;
            case "--polyglot":
                seenPolyglot = true;
                return true;
            default:
                if ((arg.startsWith("--jvm.") && arg.length() > "--jvm.".length()) || arg.equals("--jvm")) {
                    if (isAOT()) {
                        throw abort("should not reach here: jvm option failed to switch to JVM");
                    }
                    return true;
                } else if ((arg.startsWith("--native.") && arg.length() > "--native.".length()) || arg.equals("--native")) {
                    if (!isAOT()) {
                        throw abort("native options are not supported on the JVM");
                    }
                    return true;
                }
                // getLanguageId() or null?
                if (arg.length() <= 2 || !arg.startsWith("--")) {
                    return false;
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
                            options.put(key, value);
                            return true;
                        } catch (IllegalArgumentException e) {
                            throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                        }
                    } else if (key.equals("log.file")) {
                        logFile = Paths.get(value);
                        return true;
                    }
                }
                OptionDescriptor descriptor = findPolyglotOptionDescriptor(group, key);
                if (descriptor == null) {
                    if (defaultOptionPrefix != null) {
                        descriptor = findPolyglotOptionDescriptor(defaultOptionPrefix, defaultOptionPrefix + "." + key);
                    }
                    if (descriptor == null) {
                        return false;
                    }
                }
                try {
                    descriptor.getKey().getType().convert(value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
                }
                if (descriptor.isDeprecated()) {
                    System.err.println("Warning: Option '" + descriptor.getName() + "' is deprecated and might be removed from future versions.");
                }
                // use the full name of the found descriptor
                options.put(descriptor.getName(), value);
                return true;
        }
    }

    private OptionDescriptor findPolyglotOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        switch (group) {
            case "compiler":
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

    private Set<String> collectAllArguments() {
        Engine engine = getTempEngine();
        Set<String> options = new LinkedHashSet<>();
        collectArguments(options);
        if (isGraalVMAvailable()) {
            options.add("--polylgot");
            options.add("--jvm");
        }
        options.add("--native");
        options.add("--help");
        options.add("--help:languages");
        options.add("--help:tools");
        options.add("--help:expert");
        options.add("--version:graalvm");
        options.add("--show-version:graalvm");
        if (helpExpert || helpDebug) {
            options.add("--help:debug");
        }
        addOptions(engine.getOptions(), options);
        for (Language language : engine.getLanguages().values()) {
            addOptions(language.getOptions(), options);
        }
        for (Instrument instrument : engine.getInstruments().values()) {
            addOptions(instrument.getOptions(), options);
        }
        return options;
    }

    private static void addOptions(OptionDescriptors descriptors, Set<String> target) {
        for (OptionDescriptor descriptor : descriptors) {
            target.add("--" + descriptor.getName());
        }
    }

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    private static List<String> fuzzyMatch(Set<String> arguments, String argument, float threshold) {
        List<String> matches = new ArrayList<>();
        for (String arg : arguments) {
            float score = stringSimilarity(arg, argument);
            if (score >= threshold) {
                matches.add(arg);
            }
        }
        return matches;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     *
     * Ported from str_similar() in globals.cpp.
     */
    private static float stringSimilarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }

    static List<Language> sortedLanguages(Engine engine) {
        List<Language> languages = new ArrayList<>(engine.getLanguages().values());
        languages.sort(Comparator.comparing(Language::getId));
        return languages;
    }

    static List<Instrument> sortedInstruments(Engine engine) {
        List<Instrument> instruments = new ArrayList<>();
        for (Instrument instrument : engine.getInstruments().values()) {
            // no options not accessible to the user.
            if (!instrument.getOptions().iterator().hasNext()) {
                continue;
            }
            instruments.add(instrument);
        }
        instruments.sort(Comparator.comparing(Instrument::getId));
        return instruments;
    }

    static void printOption(OptionCategory optionCategory, OptionDescriptor descriptor) {
        if (!descriptor.isDeprecated() && sameCategory(descriptor, optionCategory)) {
            printOption(asPrintableOption(descriptor));
        }
    }

    private static PrintableOption asPrintableOption(OptionDescriptor descriptor) {
        StringBuilder key = new StringBuilder("--");
        key.append(descriptor.getName());
        Object defaultValue = descriptor.getKey().getDefaultValue();
        if (defaultValue instanceof Boolean && defaultValue == Boolean.FALSE) {
            // nothing to print
        } else {
            key.append("=<");
            key.append(descriptor.getKey().getType().getName());
            key.append(">");
        }
        return new PrintableOption(key.toString(), descriptor.getHelp());
    }

    static void printOption(String option, String description) {
        printOption(option, description, 2);
    }

    private static String spaces(int length) {
        return new String(new char[length]).replace('\0', ' ');
    }

    private static String wrap(String s) {
        final int width = 120;
        StringBuilder sb = new StringBuilder(s);
        int cursor = 0;
        while (cursor + width < sb.length()) {
            int i = sb.lastIndexOf(" ", cursor + width);
            if (i == -1 || i < cursor) {
                i = sb.indexOf(" ", cursor + width);
            }
            if (i != -1) {
                sb.replace(i, i + 1, System.lineSeparator());
                cursor = i;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static void printOption(String option, String description, int indentation) {
        String indent = spaces(indentation);
        String desc = wrap(description != null ? description : "");
        String nl = System.lineSeparator();
        String[] descLines = desc.split(nl);
        int optionWidth = 45;
        if (option.length() >= optionWidth && description != null) {
            System.out.println(indent + option + nl + indent + spaces(optionWidth) + descLines[0]);
        } else {
            System.out.println(indent + option + spaces(optionWidth - option.length()) + descLines[0]);
        }
        for (int i = 1; i < descLines.length; i++) {
            System.out.println(indent + spaces(optionWidth) + descLines[i]);
        }
    }

    private static void printOption(PrintableOption option) {
        printOption(option, 2);
    }

    private static void printOption(PrintableOption option, int indentation) {
        printOption(option.option, option.description, indentation);
    }

    private static final class PrintableOption implements Comparable<PrintableOption> {
        final String option;
        final String description;

        private PrintableOption(String option, String description) {
            this.option = option;
            this.description = description;
        }

        @Override
        public int compareTo(PrintableOption o) {
            return this.option.compareTo(o.option);
        }
    }

    private static void printOptions(List<PrintableOption> options, String title, int indentation) {
        Collections.sort(options);
        System.out.println(title);
        for (PrintableOption option : options) {
            printOption(option, indentation);
        }
    }

    enum OS {
        Darwin,
        Linux,
        Solaris,
        Windows;

        private static OS findCurrent() {
            final String name = System.getProperty("os.name");
            if (name.equals("Linux")) {
                return Linux;
            }
            if (name.equals("SunOS")) {
                return Solaris;
            }
            if (name.equals("Mac OS X") || name.equals("Darwin")) {
                return Darwin;
            }
            if (name.startsWith("Windows")) {
                return Windows;
            }
            throw new IllegalArgumentException("unknown OS: " + name);
        }

        private static final OS current = findCurrent();

        public static OS getCurrent() {
            return current;
        }
    }

    private static void serializePolyglotOptions(Map<String, String> polyglotOptions, List<String> args) {
        if (polyglotOptions == null) {
            return;
        }
        for (Entry<String, String> entry : polyglotOptions.entrySet()) {
            args.add("--" + entry.getKey() + '=' + entry.getValue());
        }
    }

    private static void printLanguages(Engine engine, boolean printWhenEmpty) {
        if (engine.getLanguages().isEmpty()) {
            if (printWhenEmpty) {
                System.out.println("  Installed Languages: none");
            }
        } else {
            System.out.println("  Installed Languages:");
            List<Language> languages = new ArrayList<>(engine.getLanguages().size());
            int nameLength = 0;
            for (Language language : engine.getLanguages().values()) {
                languages.add(language);
                nameLength = max(nameLength, language.getName().length());
            }
            languages.sort(Comparator.comparing(Language::getId));
            String langFormat = "    %-" + nameLength + "s%s version %s%n";
            for (Language language : languages) {
                String host;
                host = "";
                String version = language.getVersion();
                if (version == null || version.length() == 0) {
                    version = "";
                }
                System.out.printf(langFormat, language.getName().isEmpty() ? "Unnamed" : language.getName(), host, version);
            }
        }
    }

    private static void printInstruments(Engine engine, boolean printWhenEmpty) {
        if (engine.getInstruments().isEmpty()) {
            if (printWhenEmpty) {
                System.out.println("  Installed Tools: none");
            }
        } else {
            System.out.println("  Installed Tools:");
            List<Instrument> instruments = sortedInstruments(engine);
            int nameLength = 0;
            for (Instrument instrument : instruments) {
                nameLength = max(nameLength, instrument.getName().length());
            }
            String instrumentFormat = "    %-" + nameLength + "s version %s%n";
            for (Instrument instrument : instruments) {
                String version = instrument.getVersion();
                if (version == null || version.length() == 0) {
                    version = "";
                }
                System.out.printf(instrumentFormat, instrument.getName().isEmpty() ? instrument.getId() : instrument.getName(), version);
            }
        }
    }

    private static final String CLASSPATH = System.getProperty("org.graalvm.launcher.classpath");

    class Native {
        void maybeExec(List<String> args, boolean isPolyglot, Map<String, String> polyglotOptions, VMType defaultVmType) {
            assert isAOT();
            VMType vmType = null;
            boolean polyglot = false;
            List<String> jvmArgs = new ArrayList<>();
            List<String> remainingArgs = new ArrayList<>(args.size());

            // move jvm polyglot options to jvmArgs
            Iterator<Entry<String, String>> polyglotOptionsIterator = polyglotOptions.entrySet().iterator();
            while (polyglotOptionsIterator.hasNext()) {
                Map.Entry<String, String> entry = polyglotOptionsIterator.next();
                if (entry.getKey().startsWith("jvm.")) {
                    jvmArgs.add('-' + entry.getKey().substring(4));
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        jvmArgs.add(entry.getValue());
                    }
                    vmType = VMType.JVM;
                    polyglotOptionsIterator.remove();
                }
            }

            Iterator<String> iterator = args.iterator();
            while (iterator.hasNext()) {
                String arg = iterator.next();
                if ((arg.startsWith("--jvm.") && arg.length() > "--jvm.".length()) || arg.equals("--jvm")) {
                    if (vmType == VMType.Native) {
                        throw abort("`--jvm` and `--native` options can not be used together.");
                    }
                    if (!isGraalVMAvailable()) {
                        throw abort("--jvm.* options are only supported when this launcher is part of a GraalVM.");
                    }
                    if (arg.equals("--jvm.help")) {
                        printJvmHelp();
                        throw exit();
                    }
                    vmType = VMType.JVM;
                    if (arg.startsWith("--jvm.")) {
                        String jvmArg = arg.substring("--jvm.".length());
                        if (jvmArg.equals("classpath")) {
                            throw abort("--jvm.classpath argument must be of the form --jvm.classpath=<classpath>, not two separate arguments");
                        }
                        if (jvmArg.equals("cp")) {
                            throw abort("--jvm.cp argument must be of the form --jvm.cp=<classpath>, not two separate arguments");
                        }
                        if (jvmArg.startsWith("classpath=") || jvmArg.startsWith("cp=")) {
                            int eqIndex = jvmArg.indexOf('=');
                            jvmArgs.add('-' + jvmArg.substring(0, eqIndex));
                            jvmArgs.add(jvmArg.substring(eqIndex + 1));
                        } else {
                            jvmArgs.add('-' + jvmArg);
                        }
                    }
                    iterator.remove();
                } else if ((arg.startsWith("--native.") && arg.length() > "--native.".length()) || arg.equals("--native")) {
                    if (vmType == VMType.JVM) {
                        throw abort("`--jvm` and `--native` options can not be used together.");
                    }
                    vmType = VMType.Native;
                    if (arg.equals("--native.help")) {
                        printNativeHelp();
                        throw exit();
                    }
                    if (arg.startsWith("--native.")) {
                        setNativeOption(arg.substring("--native.".length()));
                    }
                    iterator.remove();
                } else if (arg.equals("--polyglot")) {
                    polyglot = true;
                } else {
                    remainingArgs.add(arg);
                }
            }

            /*
             * All options are processed, now we can run the startup hooks that can depend on the
             * option values.
             */
            VMRuntime.initialize();

            if (vmType == null) {
                vmType = defaultVmType;
            }
            if (vmType == VMType.JVM) {
                if (!isPolyglot && polyglot) {
                    remainingArgs.add(0, "--polyglot");
                }
                assert isGraalVMAvailable();
                execJVM(jvmArgs, remainingArgs, polyglotOptions);
            } else if (!isPolyglot && polyglot) {
                assert jvmArgs.isEmpty();
                if (!isGraalVMAvailable()) {
                    throw abort("--polyglot option is only supported when this launcher is part of a GraalVM.");
                }
                execNativePolyglot(remainingArgs, polyglotOptions);
            }
        }

        private void setNativeOption(String arg) {
            if (arg.startsWith("Dgraal.")) {
                setGraalStyleRuntimeOption(arg.substring("Dgraal.".length()));
            } else if (arg.startsWith("D")) {
                setSystemProperty(arg.substring("D".length()));
            } else if (arg.startsWith("XX:")) {
                setRuntimeOption(arg.substring("XX:".length()));
            } else if (arg.startsWith("X") && isXOption(arg)) {
                setXOption(arg.substring("X".length()));
            } else {
                throw abort("Unrecognized --native option: '--native." + arg + "'. Such arguments should start with '--native.D', '--native.XX:', or '--native.X'");
            }
        }

        private void setGraalStyleRuntimeOption(String arg) {
            if (arg.startsWith("+") || arg.startsWith("-")) {
                throw abort("Dgraal option must use <name>=<value> format, not +/- prefix");
            }
            int eqIdx = arg.indexOf('=');
            String key;
            String value;
            if (eqIdx < 0) {
                key = arg;
                value = "";
            } else {
                key = arg.substring(0, eqIdx);
                value = arg.substring(eqIdx + 1);
            }
            OptionDescriptor descriptor = RuntimeOptions.getOptions().get(key);
            if (descriptor == null) {
                throw unknownOption(key);
            }
            try {
                RuntimeOptions.set(key, descriptor.getKey().getType().convert(value));
            } catch (IllegalArgumentException iae) {
                throw abort("Invalid argument: '--native." + arg + "': " + iae.getMessage());
            }

        }

        public void setSystemProperty(String arg) {
            int eqIdx = arg.indexOf('=');
            String key;
            String value;
            if (eqIdx < 0) {
                key = arg;
                value = "";
            } else {
                key = arg.substring(0, eqIdx);
                value = arg.substring(eqIdx + 1);
            }
            System.setProperty(key, value);
        }

        public void setRuntimeOption(String arg) {
            int eqIdx = arg.indexOf('=');
            String key;
            Object value;
            if (arg.startsWith("+") || arg.startsWith("-")) {
                key = arg.substring(1);
                if (eqIdx >= 0) {
                    throw abort("Invalid argument: '--native." + arg + "': Use either +/- or =, but not both");
                }
                OptionDescriptor descriptor = RuntimeOptions.getOptions().get(key);
                if (descriptor == null) {
                    throw unknownOption(key);
                }
                if (!isBooleanOption(descriptor)) {
                    throw abort("Invalid argument: " + key + " is not a boolean option, set it with --native.XX:" + key + "=<value>.");
                }
                value = arg.startsWith("+");
            } else if (eqIdx > 0) {
                key = arg.substring(0, eqIdx);
                OptionDescriptor descriptor = RuntimeOptions.getOptions().get(key);
                if (descriptor == null) {
                    throw unknownOption(key);
                }
                if (isBooleanOption(descriptor)) {
                    throw abort("Boolean option '" + key + "' must be set with +/- prefix, not <name>=<value> format.");
                }
                try {
                    value = descriptor.getKey().getType().convert(arg.substring(eqIdx + 1));
                } catch (IllegalArgumentException iae) {
                    throw abort("Invalid argument: '--native." + arg + "': " + iae.getMessage());
                }
            } else {
                throw abort("Invalid argument: '--native." + arg + "'. Prefix boolean options with + or -, suffix other options with <name>=<value>");
            }
            RuntimeOptions.set(key, value);
        }

        /* Is an option that starts with an 'X' one of the recognized X options? */
        private boolean isXOption(String arg) {
            return (arg.startsWith("Xmn") || arg.startsWith("Xms") || arg.startsWith("Xmx") || arg.startsWith("Xss"));
        }

        /* Set a `-X` option, given something like "mx2g". */
        private void setXOption(String arg) {
            try {
                RuntimeOptions.set(arg, null);
            } catch (RuntimeException re) {
                throw abort("Invalid argument: '--native.X" + arg + "' does not specify a valid number.");
            }
        }

        private void helpXOption() {
            /* The default values are *copied* from com.oracle.svm.core.genscavenge.HeapPolicy */
            printOption("--native.Xmn<value>", "Sets the maximum size of the young generation, in bytes. Default: 256MB.");
            printOption("--native.Xmx<value>", "Sets the maximum size of the heap, in bytes. Default: MaximumHeapSizePercent * physical memory.");
            printOption("--native.Xms<value>", "Sets the minimum size of the heap, in bytes. Default: 2 * maximum young generation size.");
            printOption("--native.Xss<value>", "Sets the size of each thread stack, in bytes. Default: OS-dependent.");
        }

        private boolean isBooleanOption(OptionDescriptor descriptor) {
            return descriptor.getKey().getType().equals(OptionType.defaultType(Boolean.class));
        }

        private AbortException unknownOption(String key) {
            throw abort("Unknown native option: " + key + ". Use --native.help to list available options.");
        }

        private void printJvmHelp() {
            System.out.println("JVM options:");
            printOption("--jvm.classpath <...>", "A " + File.pathSeparator + " separated list of classpath entries that will be added to the JVM's classpath");
            printOption("--jvm.D<name>=<value>", "Set a system property");
            printOption("--jvm.esa", "Enable system assertions");
            printOption("--jvm.ea[:<packagename>...|:<classname>]", "Enable assertions with specified granularity");
            printOption("--jvm.agentlib:<libname>[=<options>]", "Load native agent library <libname>");
            printOption("--jvm.agentpath:<pathname>[=<options>]", "Load native agent library by full pathname");
            printOption("--jvm.javaagent:<jarpath>[=<options>]", "Load Java programming language agent");
            printOption("--jvm.Xbootclasspath/a:<...>", "A " + File.pathSeparator + " separated list of classpath entries that will be added to the JVM's boot classpath");
            printOption("--jvm.Xmx<size>", "Set maximum Java heap size");
            printOption("--jvm.Xms<size>", "Set initial Java heap size");
            printOption("--jvm.Xss<size>", "Set java thread stack size");
        }

        private void printNativeHelp() {
            System.out.println("Native VM options:");
            OptionDescriptors options = RuntimeOptions.getOptions();
            SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
            for (OptionDescriptor descriptor : options) {
                sortedOptions.put(descriptor.getName(), descriptor);
            }
            for (Entry<String, OptionDescriptor> entry : sortedOptions.entrySet()) {
                OptionDescriptor descriptor = entry.getValue();
                String helpMsg = descriptor.getHelp();
                int helpLen = helpMsg.length();
                if (helpLen > 0 && helpMsg.charAt(helpLen - 1) != '.') {
                    helpMsg += '.';
                }
                if (isBooleanOption(descriptor)) {
                    Boolean val = (Boolean) descriptor.getKey().getDefaultValue();
                    if (helpLen != 0) {
                        helpMsg += ' ';
                    }
                    if (val == null || !((boolean) val)) {
                        helpMsg += "Default: - (disabled).";
                    } else {
                        helpMsg += "Default: + (enabled).";
                    }
                    printOption("--native.XX:\u00b1" + entry.getKey(), helpMsg);
                } else {
                    Object def = descriptor.getKey().getDefaultValue();
                    if (def instanceof String) {
                        def = '"' + String.valueOf(def) + '"';
                    }
                    printOption("--native.XX:" + entry.getKey() + "=" + def, helpMsg);
                }
            }
            System.out.println("System properties:");
            printOption("--native.D<property>=<value>", "Sets a system property");
            helpXOption();
        }

        private void execNativePolyglot(List<String> args, Map<String, String> polyglotOptions) {
            List<String> command = new ArrayList<>(args.size() + (polyglotOptions == null ? 0 : polyglotOptions.size()) + 3);
            Path executable = getGraalVMBinaryPath("polyglot");
            command.add("--native");
            serializePolyglotOptions(polyglotOptions, command);
            command.add("--use-launcher");
            command.add(getMainClass());
            command.addAll(args);
            exec(executable, command);
        }

        private void execJVM(List<String> jvmArgs, List<String> args, Map<String, String> polyglotOptions) {
            // TODO use String[] for command to avoid a copy later
            List<String> command = new ArrayList<>(jvmArgs.size() + args.size() + (polyglotOptions == null ? 0 : polyglotOptions.size()) + 4);
            Path executable = getGraalVMBinaryPath("java");
            String classpath = getClasspath(jvmArgs);
            if (classpath != null) {
                command.add("-classpath");
                command.add(classpath);
            }
            command.addAll(jvmArgs);
            command.add(getMainClass());
            serializePolyglotOptions(polyglotOptions, command);
            command.addAll(args);
            exec(executable, command);
        }

        private String getClasspath(List<String> jvmArgs) {
            assert isAOT();
            assert CLASSPATH != null;
            StringBuilder sb = new StringBuilder();
            if (!CLASSPATH.isEmpty()) {
                Path graalVMHome = getGraalVMHome();
                if (graalVMHome == null) {
                    throw abort("Can not resolve classpath: could not get GraalVM home");
                }
                for (String entry : CLASSPATH.split(File.pathSeparator)) {
                    Path resolved = graalVMHome.resolve(entry);
                    if (isVerbose() && !Files.exists(resolved)) {
                        System.err.println(String.format("Warning: %s does not exit", resolved));
                    }
                    sb.append(resolved);
                    sb.append(File.pathSeparatorChar);
                }
            }
            String classpathFromArgs = null;
            Iterator<String> iterator = jvmArgs.iterator();
            while (iterator.hasNext()) {
                String jvmArg = iterator.next();
                if (jvmArg.equals("-cp") || jvmArg.equals("-classpath")) {
                    if (iterator.hasNext()) {
                        iterator.remove();
                        classpathFromArgs = iterator.next();
                        iterator.remove();
                        // no break, pick the last one
                    }
                }
                if (jvmArg.startsWith("-Djava.class.path=")) {
                    iterator.remove();
                    classpathFromArgs = jvmArg.substring("-Djava.class.path=".length());
                }
            }
            if (classpathFromArgs != null) {
                sb.append(classpathFromArgs);
                sb.append(File.pathSeparatorChar);
            }
            if (sb.length() == 0) {
                return null;
            }
            return sb.substring(0, sb.length() - 1);
        }

        private Path getGraalVMBinaryPath(String binaryName) {
            String executableName = executableName(binaryName);
            Path graalVMHome = getGraalVMHome();
            if (graalVMHome == null) {
                throw abort("Can not exec to GraalVM binary: could not find GraalVM home");
            }
            Path jdkBin = graalVMHome.resolve("bin").resolve(executableName);
            if (Files.exists(jdkBin)) {
                return jdkBin;
            }
            return graalVMHome.resolve("jre").resolve("bin").resolve(executableName);
        }

        Path getGraalVMHome() {
            return Engine.findHome();
        }

        private void exec(Path executable, List<String> command) {
            assert isAOT();
            if (isVerbose()) {
                System.out.println(String.format("exec(%s, %s)", executable, command));
            }
            String[] argv = new String[command.size() + 1];
            int i = 0;
            argv[i++] = executable.getFileName().toString();
            for (String arg : command) {
                argv[i++] = arg;
            }
            if (execv(executable.toString(), argv) != 0) {
                int errno = NativeInterface.errno();
                throw abort(String.format("exec(%s, %s) failed: %s", executable, command, CTypeConversion.toJavaString(NativeInterface.strerror(errno))));
            }
        }

        private int execv(String executable, String[] argv) {
            try (CTypeConversion.CCharPointerHolder pathHolder = CTypeConversion.toCString(executable);
                            CTypeConversion.CCharPointerPointerHolder argvHolder = CTypeConversion.toCStrings(argv)) {
                return NativeInterface.execv(pathHolder.get(), argvHolder.get());
            }
        }
    }

    static OutputStream newLogStream(Path path) throws IOException {
        Path usedPath = path;
        Path lockFile = null;
        FileChannel lockFileChannel = null;
        for (int unique = 0;; unique++) {
            StringBuilder lockFileNameBuilder = new StringBuilder();
            lockFileNameBuilder.append(path.toString());
            if (unique > 0) {
                lockFileNameBuilder.append(unique);
                usedPath = Paths.get(lockFileNameBuilder.toString());
            }
            lockFileNameBuilder.append(".lck");
            lockFile = Paths.get(lockFileNameBuilder.toString());
            Map.Entry<FileChannel, Boolean> openResult = openChannel(lockFile);
            if (openResult != null) {
                lockFileChannel = openResult.getKey();
                if (lock(lockFileChannel, openResult.getValue())) {
                    break;
                } else {
                    // Close and try next name
                    lockFileChannel.close();
                }
            }
        }
        assert lockFile != null && lockFileChannel != null;
        boolean success = false;
        try {
            OutputStream stream = new LockableOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(usedPath, WRITE, CREATE, APPEND)),
                            lockFile,
                            lockFileChannel);
            success = true;
            return stream;
        } finally {
            if (!success) {
                LockableOutputStream.unlock(lockFile, lockFileChannel);
            }
        }
    }

    private static Map.Entry<FileChannel, Boolean> openChannel(Path path) throws IOException {
        FileChannel channel = null;
        for (int retries = 0; channel == null && retries < 2; retries++) {
            try {
                channel = FileChannel.open(path, CREATE_NEW, WRITE);
                return new AbstractMap.SimpleImmutableEntry<>(channel, true);
            } catch (FileAlreadyExistsException faee) {
                // Maybe a FS race showing a zombie file, try to reuse it
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && isParentWritable(path)) {
                    try {
                        channel = FileChannel.open(path, WRITE, APPEND);
                        return new AbstractMap.SimpleImmutableEntry<>(channel, false);
                    } catch (NoSuchFileException x) {
                        // FS Race, next try we should be able to create with CREATE_NEW
                    } catch (IOException x) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean isParentWritable(Path path) {
        Path parentPath = path.getParent();
        if (parentPath == null && !path.isAbsolute()) {
            parentPath = path.toAbsolutePath().getParent();
        }
        return parentPath != null && Files.isWritable(parentPath);
    }

    private static boolean lock(FileChannel lockFileChannel, boolean newFile) {
        boolean available = false;
        try {
            available = lockFileChannel.tryLock() != null;
        } catch (OverlappingFileLockException ofle) {
            // VM already holds lock continue with available set to false
        } catch (IOException ioe) {
            // Locking not supported by OS
            available = newFile;
        }
        return available;
    }

    private static final class LockableOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final Path lockFile;
        private final FileChannel lockFileChannel;

        LockableOutputStream(OutputStream delegate, Path lockFile, FileChannel lockFileChannel) {
            this.delegate = delegate;
            this.lockFile = lockFile;
            this.lockFileChannel = lockFileChannel;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                unlock(lockFile, lockFileChannel);
            }
        }

        private static void unlock(Path lockFile, FileChannel lockFileChannel) {
            try {
                lockFileChannel.close();
            } catch (IOException ioe) {
                // Error while closing the channel, ignore.
            }
            try {
                Files.delete(lockFile);
            } catch (IOException ioe) {
                // Error while deleting the lock file, ignore.
            }
        }
    }
}
