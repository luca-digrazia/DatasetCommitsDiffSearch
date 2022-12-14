/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import static org.graalvm.compiler.options.OptionValues.GLOBAL;

import java.util.Map;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

/**
 * Options for the Truffle compiler.
 */
public class TruffleCompilerOptions {

    static class Lazy {
        private static final ThreadLocal<TruffleOptionsOverrideScope> overrideScope = new ThreadLocal<>();
    }

    /**
     * Gets the object holding the values of Truffle options, taking into account any active
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) overrides}.
     */
    public static OptionValues getOptions() {
        TruffleOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : GLOBAL;
    }

    /**
     * Gets the options defined in the current option
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) override} scope or {@code null} if
     * there is no override scope active for the current thread.
     */
    public static OptionValues getCurrentOptionOverrides() {
        TruffleOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : null;
    }

    public static class TruffleOptionsOverrideScope implements AutoCloseable {
        private final TruffleOptionsOverrideScope outer;
        private final OptionValues options;

        TruffleOptionsOverrideScope(Map<OptionKey<?>, Object> overrides) {
            outer = Lazy.overrideScope.get();
            options = new OptionValues(outer == null ? GLOBAL : outer.options, overrides);
            Lazy.overrideScope.set(this);
        }

        @Override
        public void close() {
            Lazy.overrideScope.set(outer);
        }
    }

    /**
     * Forces specified values in the object returned by {@link #getOptions()} until
     * {@link TruffleOptionsOverrideScope#close()} is called on the object returned by this method.
     * The values forced while the override is active are taken from the key/value pairs in
     * {@code overrides}. The override is thread local.
     * <p>
     * The returned object should be used with the try-with-resource construct:
     *
     * <pre>
     * try (OverrideScope s = overrideOptions(option1, value1, option2, value2)) {
     *     ...
     * }
     * </pre>
     *
     * NOTE: This feature is only intended for testing. The caller must be aware whether or not the
     * options being overridden are accessed inside the new override scope.
     *
     * @param extraOverrides overrides in the form {@code [key1, value2, key3, value3, ...]}
     */
    public static TruffleOptionsOverrideScope overrideOptions(OptionKey<?> key1, Object value1, Object... extraOverrides) {
        return new TruffleOptionsOverrideScope(OptionValues.asMap(key1, value1, extraOverrides));
    }

    public static TruffleOptionsOverrideScope overrideOptions(Map<OptionKey<?>, Object> overrides) {
        return new TruffleOptionsOverrideScope(overrides);
    }

    /**
     * Gets the value of a given Truffle option key taking into account any active
     * {@linkplain #overrideOptions overrides}.
     */
    public static <T> T getValue(OptionKey<T> key) {
        return key.getValue(getOptions());
    }

    // @formatter:off
    // configuration
    /**
     * Instructs the Truffle Compiler to compile call targets only if their name contains at least one element of a comma-separated list of includes.
     * Excludes are prefixed with a tilde (~).
     *
     * The format in EBNF:
     * <pre>
     * CompileOnly = Element, { ',', Element } ;
     * Element = Include | '~' Exclude ;
     * </pre>
     */
    @Option(help = "Restrict compilation to comma-separated list of includes (or excludes prefixed with tilde)", type = OptionType.Debug)
    public static final OptionKey<String> TruffleCompileOnly = new OptionKey<>(null);

    @Option(help = "Compile immediately to test truffle compiler", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCompileImmediately = new OptionKey<>(false);

    @Option(help = "Exclude assertion code from Truffle compilations", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleExcludeAssertions = new OptionKey<>(true);

    @Option(help = "Compile call target when call count exceeds this threshold", type = OptionType.User)
    public static final OptionKey<Integer> TruffleCompilationThreshold = new OptionKey<>(1000);

    @Option(help = "Defines the maximum timespan in milliseconds that is required for a call target to be queued for compilation.", type = OptionType.User)
    public static final OptionKey<Integer> TruffleTimeThreshold = new OptionKey<>(25000);

    @Option(help = "Minimum number of calls before a call target is compiled", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleMinInvokeThreshold = new OptionKey<>(3);

    @Option(help = "Delay compilation after an invalidation to allow for reprofiling", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleInvalidationReprofileCount = new OptionKey<>(3);

    @Option(help = "Delay compilation after a node replacement", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleReplaceReprofileCount = new OptionKey<>(10);

    @Option(help = "Enable automatic inlining of call targets", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleFunctionInlining = new OptionKey<>(true);

    @Option(help = "Stop inlining if caller's cumulative tree size would exceed this limit", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleInliningMaxCallerSize = new OptionKey<>(2250);

    @Option(help = "Maximum level of recursive inlining", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleMaximumRecursiveInlining = new OptionKey<>(4);

    @Option(help = "Enable call target splitting", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleSplitting = new OptionKey<>(true);

    @Option(help = "Enable on stack replacement for Truffle loops.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleOSR = new OptionKey<>(true);

    @Option(help = "Number of loop iterations until on-stack-replacement compilation is triggered.", type = OptionType.Debug)
    public static final OptionKey<Integer> TruffleOSRCompilationThreshold = new OptionKey<>(100000);

    @Option(help = "Disable call target splitting if tree size exceeds this limit", type = OptionType.Debug)
    public static final OptionKey<Integer> TruffleSplittingMaxCalleeSize = new OptionKey<>(100);

    @Option(help = "Enable asynchronous truffle compilation in background thread", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleBackgroundCompilation = new OptionKey<>(true);

    @Option(help = "Manually set the number of compiler threads", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleCompilerThreads = new OptionKey<>(0);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleInlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleReturnTypeSpeculation = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleArgumentTypeSpeculation = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleUseFrameWithoutBoxing = new OptionKey<>(true);

    // tracing
    @Option(help = "Print potential performance problems", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTrufflePerformanceWarnings = new OptionKey<>(false);

    @Option(help = "Print information for compilation results", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleCompilation = new OptionKey<>(false);

    @Option(help = "Compile time benchmarking: repeat Truffle compilation n times and then exit the VM", type = OptionType.Debug)
    public static final OptionKey<Integer> TruffleCompilationRepeats = new OptionKey<>(0);

    @Option(help = "Print information for compilation queuing", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleCompilationDetails = new OptionKey<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleCompilationPolymorphism = new OptionKey<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleCompilationAST = new OptionKey<>(false);

    @Option(help = "Print the inlined call tree for each compiled method", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleCompilationCallTree = new OptionKey<>(false);

    @Option(help = "Print source secions for printed expansion trees", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleExpansionSource = new OptionKey<>(false);

    @Option(help = "Prints a histogram of all expanded Java methods.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintTruffleExpansionHistogram = new OptionKey<>(false);

    @Option(help = "Treat compilation exceptions as fatal exceptions that will exit the application", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCompilationExceptionsAreFatal = new OptionKey<>(false);

    @Option(help = "Prints the exception stack trace for compilation exceptions", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCompilationExceptionsArePrinted = new OptionKey<>(true);

    @Option(help = "Treat compilation exceptions as thrown runtime exceptions", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCompilationExceptionsAreThrown = new OptionKey<>(false);

    @Option(help = "Print information for inlining for each compilation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleInlining = new OptionKey<>(false);

    @Option(help = "Print information for each splitted call site.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleSplitting = new OptionKey<>(false);

    @Option(help = "Print stack trace on transfer to interpreter", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleTransferToInterpreter = new OptionKey<>(false);

    @Option(help = "Print stack trace on assumption invalidation", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTruffleAssumptions = new OptionKey<>(false);

    @Option(help = "Number of stack trace elements printed by TraceTruffleTransferToInterpreter and TraceTruffleAssumptions", type = OptionType.Debug)
    public static final OptionKey<Integer> TraceTruffleStackTraceLimit = new OptionKey<>(20);

    @Option(help = "Print a summary of execution counts for all executed CallTargets. Introduces counter overhead for each call.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCallTargetProfiling = new OptionKey<>(false);

    @Option(help = "Print Truffle compilation statistics at the end of a run.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCompilationStatistics = new OptionKey<>(false);

    @Option(help = "Print additional more verbose Truffle compilation statistics at the end of a run.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleCompilationStatisticDetails = new OptionKey<>(false);

    @Option(help = "Enable support for simple infopoints in truffle partial evaluations.", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleEnableInfopoints = new OptionKey<>(false);

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleIterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Enable/disable builtin profiles in com.oracle.truffle.api.profiles.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleProfilingEnabled = new OptionKey<>(true);

    @Option(help = "Instrument branches and output profiling information to the standard output.")
    public static final OptionKey<Boolean> TruffleInstrumentBranches = new OptionKey<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.")
    public static final OptionKey<Boolean> TruffleInstrumentBranchesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Method filter for methods in which to add branch instrumentation.")
    public static final OptionKey<String> TruffleInstrumentBranchesFilter = new OptionKey<>(null);

    @Option(help = "Prettify stack traces for branch-instrumented callsites.")
    public static final OptionKey<Boolean> TruffleInstrumentBranchesPretty = new OptionKey<>(true);

    @Option(help = "Maximum number of instrumentation counters available.")
    public static final OptionKey<Integer> TruffleInstrumentBranchesCount = new OptionKey<>(10000);

    // @formatter:on
}
