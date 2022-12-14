/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleArgumentTypeSpeculation;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleCompilationThreshold;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleCompileImmediately;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleInvalidationReprofileCount;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleMinInvokeThreshold;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleReplaceReprofileCount;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleReturnTypeSpeculation;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleTimeThreshold;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.graal.options.OptionValues;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class OptimizedCompilationProfile {

    /**
     * Number of times an installed code for this tree was seen invalidated.
     */
    private int invalidationCount;
    private int deferredCount;

    private int interpreterCallCount;
    private int interpreterCallAndLoopCount;
    private int compilationCallThreshold;
    private int compilationCallAndLoopThreshold;

    private long timestamp;

    @CompilationFinal(dimensions = 1) private Class<?>[] profiledArgumentTypes;
    @CompilationFinal private Assumption profiledArgumentTypesAssumption;
    @CompilationFinal private Class<?> profiledReturnType;
    @CompilationFinal private Assumption profiledReturnTypeAssumption;
    @CompilationFinal private Class<?> exceptionType;

    private volatile boolean compilationFailed;
    private final OptionValues options;

    public OptimizedCompilationProfile(OptionValues options) {
        compilationCallThreshold = TruffleMinInvokeThreshold.getValue(options);
        compilationCallAndLoopThreshold = TruffleCompilationThreshold.getValue(options);
        this.options = options;
    }

    @Override
    public String toString() {
        return String.format("CompilationProfile(callCount=%d/%d, callAndLoopCount=%d/%d)", interpreterCallCount, compilationCallThreshold, interpreterCallAndLoopCount,
                        compilationCallAndLoopThreshold);
    }

    @ExplodeLoop
    void profileDirectCall(Object[] args) {
        Assumption typesAssumption = profiledArgumentTypesAssumption;
        if (CompilerDirectives.inInterpreter() && typesAssumption == null) {
            initializeProfiledArgumentTypes(args);
        } else {
            Class<?>[] types = profiledArgumentTypes;
            if (types != null) {
                if (types.length != args.length) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    typesAssumption.invalidate();
                    profiledArgumentTypes = null;
                } else if (typesAssumption.isValid()) {
                    for (int i = 0; i < types.length; i++) {
                        Class<?> type = types[i];
                        Object value = args[i];
                        if (type != null && (value == null || value.getClass() != type)) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            updateProfiledArgumentTypes(args, types);
                            break;
                        }
                    }
                }
            }
        }
    }

    void profileIndirectCall() {
        Assumption argumentTypesAssumption = profiledArgumentTypesAssumption;
        if (argumentTypesAssumption != null && argumentTypesAssumption.isValid()) {
            // Argument profiling is not possible for targets of indirect calls.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            argumentTypesAssumption.invalidate();
            profiledArgumentTypes = null;
        }
    }

    void profileInlinedCall() {
        // nothing to profile for inlined calls by default
    }

    final void profileReturnValue(Object result) {
        Assumption returnTypeAssumption = profiledReturnTypeAssumption;
        if (CompilerDirectives.inInterpreter() && returnTypeAssumption == null) {
            // we only profile return values in the interpreter as we don't want to deoptimize
            // for immediate compiles.
            if (TruffleReturnTypeSpeculation.getValue(options)) {
                profiledReturnType = classOf(result);
                profiledReturnTypeAssumption = Truffle.getRuntime().createAssumption("Profiled Return Type");
            }
        } else if (profiledReturnType != null) {
            if (result == null || profiledReturnType != result.getClass()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profiledReturnType = null;
                returnTypeAssumption.invalidate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    final <E extends Throwable> E profileExceptionType(E ex) {
        Class<?> cachedClass = exceptionType;
        // if cachedClass is null and we are not in the interpreter we don't want to deoptimize
        // This usually happens only if the call target was compiled using compile without ever
        // calling it.
        if (cachedClass != Object.class) {
            if (cachedClass != null && cachedClass == ex.getClass()) {
                return (E) cachedClass.cast(ex);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (cachedClass == null) {
                exceptionType = ex.getClass();
            } else {
                // object class is not reachable for exceptions
                exceptionType = Object.class;
            }
        }
        return ex;
    }

    final Object[] injectArgumentProfile(Object[] originalArguments) {
        Assumption argumentTypesAssumption = profiledArgumentTypesAssumption;
        Object[] args = originalArguments;
        if (argumentTypesAssumption != null && argumentTypesAssumption.isValid()) {
            args = OptimizedCallTarget.unsafeCast(OptimizedCallTarget.castArrayFixedLength(args, profiledArgumentTypes.length), Object[].class, true, true);
            args = castArgumentsImpl(args);
        }
        return args;
    }

    @ExplodeLoop
    private Object[] castArgumentsImpl(Object[] originalArguments) {
        Class<?>[] types = profiledArgumentTypes;
        Object[] castArguments = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            castArguments[i] = types[i] != null ? OptimizedCallTarget.unsafeCast(originalArguments[i], types[i], true, true) : originalArguments[i];
        }
        return castArguments;
    }

    final Object injectReturnValueProfile(Object result) {
        Class<?> klass = profiledReturnType;
        if (klass != null && CompilerDirectives.inCompiledCode() && profiledReturnTypeAssumption.isValid()) {
            return OptimizedCallTarget.unsafeCast(result, klass, true, true);
        }
        return result;
    }

    final void reportCompilationFailure() {
        compilationFailed = true;
    }

    final void reportLoopCount(int count) {
        interpreterCallAndLoopCount += count;

        int callsMissing = compilationCallAndLoopThreshold - interpreterCallAndLoopCount;
        if (callsMissing <= getTimestampThreshold(options) && callsMissing + count > getTimestampThreshold(options)) {
            timestamp = System.nanoTime();
        }
    }

    final void reportInvalidated() {
        invalidationCount++;
        int reprofile = TruffleInvalidationReprofileCount.getValue(options);
        ensureProfiling(reprofile, reprofile);
    }

    final void reportNodeReplaced() {
        // delay compilation until tree is deemed stable enough
        int replaceBackoff = TruffleReplaceReprofileCount.getValue(options);
        ensureProfiling(1, replaceBackoff);
    }

    final void interpreterCall(OptimizedCallTarget callTarget) {
        int intCallCount = ++interpreterCallCount;
        int intAndLoopCallCount = ++interpreterCallAndLoopCount;

        int callsMissing = compilationCallAndLoopThreshold - interpreterCallAndLoopCount;
        if (callsMissing == getTimestampThreshold(options)) {
            timestamp = System.nanoTime();
        }
        if (!callTarget.isCompiling() && !compilationFailed) {
            // check if call target is hot enough to get compiled, but took not too long to get hot
            if ((intAndLoopCallCount >= compilationCallAndLoopThreshold && intCallCount >= compilationCallThreshold && !isDeferredCompile(callTarget)) ||
                            TruffleCompileImmediately.getValue(options)) {
                callTarget.compile();
            }
        }
    }

    private boolean isDeferredCompile(OptimizedCallTarget target) {
        long threshold = TruffleTimeThreshold.getValue(options);

        CompilerOptions compilerOptions = target.getCompilerOptions();
        if (compilerOptions instanceof GraalCompilerOptions) {
            threshold = Math.max(threshold, ((GraalCompilerOptions) compilerOptions).getMinTimeThreshold());
        }

        long time = getTimestamp();
        if (time == 0) {
            throw new AssertionError();
        }
        long timeElapsed = System.nanoTime() - time;
        if (timeElapsed > threshold * 1_000_000) {
            // defer compilation
            ensureProfiling(0, getTimestampThreshold(options) + 1);
            timestamp = 0;
            deferredCount++;
            return true;
        }
        return false;
    }

    private static int getTimestampThreshold(OptionValues options) {
        return Math.max(TruffleCompilationThreshold.getValue(options) / 2, 1);
    }

    private void initializeProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption = Truffle.getRuntime().createAssumption("Profiled Argument Types");
        if (TruffleArgumentTypeSpeculation.getValue(options)) {
            Class<?>[] result = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = classOf(args[i]);
            }
            profiledArgumentTypes = result;
        }
    }

    private void updateProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption.invalidate();
        for (int j = 0; j < types.length; j++) {
            types[j] = joinTypes(types[j], classOf(args[j]));
        }
        profiledArgumentTypesAssumption = Truffle.getRuntime().createAssumption("Profiled Argument Types");
    }

    private static Class<?> classOf(Object arg) {
        return arg != null ? arg.getClass() : null;
    }

    private static Class<?> joinTypes(Class<?> class1, Class<?> class2) {
        if (class1 == class2) {
            return class1;
        } else {
            return null;
        }
    }

    private void ensureProfiling(int calls, int callsAndLoop) {
        int increaseCallAndLoopThreshold = callsAndLoop - (this.compilationCallAndLoopThreshold - this.interpreterCallAndLoopCount);
        if (increaseCallAndLoopThreshold > 0) {
            this.compilationCallAndLoopThreshold += increaseCallAndLoopThreshold;
        }

        int increaseCallsThreshold = calls - (this.compilationCallThreshold - this.interpreterCallCount);
        if (increaseCallsThreshold > 0) {
            this.compilationCallThreshold += increaseCallsThreshold;
        }
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        String callsThreshold = String.format("%7d/%5d", getInterpreterCallCount(), getCompilationCallThreshold());
        String loopsThreshold = String.format("%7d/%5d", getInterpreterCallAndLoopCount(), getCompilationCallAndLoopThreshold());
        String invalidations = String.format("%5d", invalidationCount);
        properties.put("Calls/Thres", callsThreshold);
        properties.put("CallsAndLoop/Thres", loopsThreshold);
        properties.put("Inval#", invalidations);
        return properties;
    }

    public int getInvalidationCount() {
        return invalidationCount;
    }

    public int getInterpreterCallAndLoopCount() {
        return interpreterCallAndLoopCount;
    }

    public int getInterpreterCallCount() {
        return interpreterCallCount;
    }

    public int getDeferredCount() {
        return deferredCount;
    }

    public int getCompilationCallAndLoopThreshold() {
        return compilationCallAndLoopThreshold;
    }

    public int getCompilationCallThreshold() {
        return compilationCallThreshold;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static OptimizedCompilationProfile create(OptionValues options) {
        return new OptimizedCompilationProfile(options);
    }

}
