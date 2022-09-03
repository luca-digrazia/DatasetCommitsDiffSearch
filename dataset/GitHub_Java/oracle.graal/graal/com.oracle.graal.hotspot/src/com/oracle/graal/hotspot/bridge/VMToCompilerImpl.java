/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.bridge;

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.hotspot.CompileTheWorld.Options.*;

import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.CompilerThreadFactory.DebugConfigAccess;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.CompileTheWorld.Config;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.replacements.*;

/**
 * Exits from the HotSpot VM into Java code.
 */
public class VMToCompilerImpl implements VMToCompiler {

    //@formatter:off
    @Option(help = "File to which compiler logging is sent")
    private static final OptionValue<String> LogFile = new OptionValue<>(null);

    @Option(help = "Print compilation queue activity periodically")
    private static final OptionValue<Boolean> PrintQueue = new OptionValue<>(false);

    @Option(help = "Interval in milliseconds at which to print compilation rate periodically. " +
                   "The compilation statistics are reset after each print out so this option " +
                   "is incompatible with -XX:+CITime and -XX:+CITimeEach.")
    public static final OptionValue<Integer> PrintCompRate = new OptionValue<>(0);

    @Option(help = "Print bootstrap progress and summary")
    private static final OptionValue<Boolean> PrintBootstrap = new OptionValue<>(true);

    @Option(help = "Time limit in milliseconds for bootstrap (-1 for no limit)")
    private static final OptionValue<Integer> TimedBootstrap = new OptionValue<>(-1);

    @Option(help = "Number of compilation threads to use")
    private static final StableOptionValue<Integer> Threads = new StableOptionValue<Integer>() {

        @Override
        public Integer initialValue() {
            return Runtime.getRuntime().availableProcessors();
        }
    };

    //@formatter:on

    private final HotSpotGraalRuntime runtime;

    private ThreadPoolExecutor compileQueue;

    private volatile boolean bootstrapRunning;

    private PrintStream log = System.out;

    private long compilerStartTime;

    public VMToCompilerImpl(HotSpotGraalRuntime runtime) {
        this.runtime = runtime;
    }

    public int allocateCompileTaskId(HotSpotResolvedJavaMethod method, int entryBCI) {
        return runtime.getCompilerToVM().allocateCompileId(method, entryBCI);
    }

    public void startCompiler(boolean bootstrapEnabled) throws Throwable {

        FastNodeClassRegistry.initialize();

        bootstrapRunning = bootstrapEnabled;

        if (LogFile.getValue() != null) {
            try {
                final boolean enableAutoflush = true;
                log = new PrintStream(new FileOutputStream(LogFile.getValue()), enableAutoflush);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("couldn't open log file: " + LogFile.getValue(), e);
            }
        }

        TTY.initialize(log);

        if (Log.getValue() == null && Meter.getValue() == null && Time.getValue() == null && Dump.getValue() == null) {
            if (MethodFilter.getValue() != null) {
                TTY.println("WARNING: Ignoring MethodFilter option since Log, Meter, Time and Dump options are all null");
            }
        }

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(log);

            String summary = DebugValueSummary.getValue();
            if (summary != null) {
                switch (summary) {
                    case "Name":
                    case "Partial":
                    case "Complete":
                    case "Thread":
                        break;
                    default:
                        throw new GraalInternalError("Unsupported value for DebugSummaryValue: %s", summary);
                }
            }
        }

        HotSpotBackend hostBackend = runtime.getHostBackend();
        final HotSpotProviders hostProviders = hostBackend.getProviders();
        assert VerifyOptionsPhase.checkOptions(hostProviders.getMetaAccess());

        // Complete initialization of backends
        hostBackend.completeInitialization();
        for (HotSpotBackend backend : runtime.getBackends().values()) {
            if (backend != hostBackend) {
                backend.completeInitialization();
            }
        }

        // Create compilation queue.
        CompilerThreadFactory factory = new CompilerThreadFactory("GraalCompilerThread", new DebugConfigAccess() {
            public GraalDebugConfig getDebugConfig() {
                return Debug.isEnabled() ? DebugEnvironment.initialize(log) : null;
            }
        });
        compileQueue = new ThreadPoolExecutor(Threads.getValue(), Threads.getValue(), 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory);

        // Create queue status printing thread.
        if (PrintQueue.getValue()) {
            Thread t = new Thread() {

                @Override
                public void run() {
                    while (true) {
                        TTY.println(compileQueue.toString());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }

        if (PrintCompRate.getValue() != 0) {
            if (runtime.getConfig().ciTime || runtime.getConfig().ciTimeEach) {
                throw new GraalInternalError("PrintCompRate is incompatible with CITime and CITimeEach");
            }
            Thread t = new Thread() {

                @Override
                public void run() {
                    while (true) {
                        runtime.getCompilerToVM().printCompilationStatistics(true, false);
                        runtime.getCompilerToVM().resetCompilationStatistics();
                        try {
                            Thread.sleep(PrintCompRate.getValue());
                        } catch (InterruptedException e) {
                        }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }

        BenchmarkCounters.initialize(runtime.getCompilerToVM());

        compilerStartTime = System.nanoTime();
    }

    /**
     * Take action related to entering a new execution phase.
     * 
     * @param phase the execution phase being entered
     */
    protected void phaseTransition(String phase) {
        CompilationStatistics.clear(phase);
    }

    /**
     * This method is the first method compiled during bootstrapping. Put any code in there that
     * warms up compiler paths that are otherwise not exercised during bootstrapping and lead to
     * later deoptimization when application code is compiled.
     */
    @SuppressWarnings("unused")
    @Deprecated
    private synchronized void compileWarmup() {
        // Method is synchronized to exercise the synchronization code in the compiler.
    }

    public void bootstrap() throws Throwable {
        if (PrintBootstrap.getValue()) {
            TTY.print("Bootstrapping Graal");
            TTY.flush();
        }

        long startTime = System.currentTimeMillis();

        boolean firstRun = true;
        do {
            // Initialize compile queue with a selected set of methods.
            Class<Object> objectKlass = Object.class;
            if (firstRun) {
                enqueue(getClass().getDeclaredMethod("compileWarmup"));
                enqueue(objectKlass.getDeclaredMethod("equals", Object.class));
                enqueue(objectKlass.getDeclaredMethod("toString"));
                firstRun = false;
            } else {
                for (int i = 0; i < 100; i++) {
                    enqueue(getClass().getDeclaredMethod("bootstrap"));
                }
            }

            // Compile until the queue is empty.
            int z = 0;
            while (true) {
                try {
                    assert !CompilationTask.withinEnqueue.get();
                    CompilationTask.withinEnqueue.set(Boolean.TRUE);
                    if (compileQueue.getCompletedTaskCount() >= Math.max(3, compileQueue.getTaskCount())) {
                        break;
                    }
                } finally {
                    CompilationTask.withinEnqueue.set(Boolean.FALSE);
                }

                Thread.sleep(100);
                while (z < compileQueue.getCompletedTaskCount() / 100) {
                    ++z;
                    if (PrintBootstrap.getValue()) {
                        TTY.print(".");
                        TTY.flush();
                    }
                }

                // Are we out of time?
                final int timedBootstrap = TimedBootstrap.getValue();
                if (timedBootstrap != -1) {
                    if ((System.currentTimeMillis() - startTime) > timedBootstrap) {
                        break;
                    }
                }
            }
        } while ((System.currentTimeMillis() - startTime) <= TimedBootstrap.getValue());

        if (ResetDebugValuesAfterBootstrap.getValue()) {
            printDebugValues("bootstrap", true);
            runtime.getCompilerToVM().resetCompilationStatistics();
        }
        phaseTransition("bootstrap");

        bootstrapRunning = false;

        if (PrintBootstrap.getValue()) {
            TTY.println(" in %d ms (compiled %d methods)", System.currentTimeMillis() - startTime, compileQueue.getCompletedTaskCount());
        }

        if (runtime.getGraphCache() != null) {
            runtime.getGraphCache().clear();
        }
        System.gc();
        phaseTransition("bootstrap2");
    }

    public void compileTheWorld() throws Throwable {
        int iterations = CompileTheWorld.Options.CompileTheWorldIterations.getValue();
        for (int i = 0; i < iterations; i++) {
            runtime.getCompilerToVM().resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);
            CompileTheWorld ctw = new CompileTheWorld(CompileTheWorldClasspath.getValue(), new Config(CompileTheWorldConfig.getValue()), CompileTheWorldStartAt.getValue(),
                            CompileTheWorldStopAt.getValue(), CompileTheWorldVerbose.getValue());
            ctw.compile();
        }
        System.exit(0);
    }

    private void enqueue(Method m) throws Throwable {
        JavaMethod javaMethod = runtime.getHostProviders().getMetaAccess().lookupJavaMethod(m);
        assert !Modifier.isAbstract(((HotSpotResolvedJavaMethod) javaMethod).getModifiers()) && !Modifier.isNative(((HotSpotResolvedJavaMethod) javaMethod).getModifiers()) : javaMethod;
        compileMethod((HotSpotResolvedJavaMethod) javaMethod, StructuredGraph.INVOCATION_ENTRY_BCI, false);
    }

    private static void shutdownCompileQueue(ThreadPoolExecutor queue) throws InterruptedException {
        if (queue != null) {
            queue.shutdown();
            if (Debug.isEnabled() && Dump.getValue() != null) {
                // Wait 2 seconds to flush out all graph dumps that may be of interest
                queue.awaitTermination(2, TimeUnit.SECONDS);
            }
        }
    }

    public void shutdownCompiler() throws Exception {
        try {
            assert !CompilationTask.withinEnqueue.get();
            CompilationTask.withinEnqueue.set(Boolean.TRUE);
            // We have to use a privileged action here because shutting down the compiler might be
            // called from user code which very likely contains unprivileged frames.
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                public Void run() throws Exception {
                    shutdownCompileQueue(compileQueue);
                    return null;
                }
            });
        } finally {
            CompilationTask.withinEnqueue.set(Boolean.FALSE);
        }

        printDebugValues(ResetDebugValuesAfterBootstrap.getValue() ? "application" : null, false);
        phaseTransition("final");

        SnippetCounter.printGroups(TTY.out().out());
        BenchmarkCounters.shutdown(runtime.getCompilerToVM(), compilerStartTime);
    }

    private void printDebugValues(String phase, boolean reset) throws GraalInternalError {
        if (Debug.isEnabled() && areMetricsOrTimersEnabled()) {
            TTY.println();
            if (phase != null) {
                TTY.println("<DebugValues:" + phase + ">");
            } else {
                TTY.println("<DebugValues>");
            }
            List<DebugValueMap> topLevelMaps = DebugValueMap.getTopLevelMaps();
            List<DebugValue> debugValues = KeyRegistry.getDebugValues();
            if (debugValues.size() > 0) {
                try {
                    ArrayList<DebugValue> sortedValues = new ArrayList<>(debugValues);
                    Collections.sort(sortedValues);

                    String summary = DebugValueSummary.getValue();
                    if (summary == null) {
                        summary = "Complete";
                    }
                    switch (summary) {
                        case "Name":
                            printSummary(topLevelMaps, sortedValues);
                            break;
                        case "Partial": {
                            DebugValueMap globalMap = new DebugValueMap("Global");
                            for (DebugValueMap map : topLevelMaps) {
                                flattenChildren(map, globalMap);
                            }
                            globalMap.normalize();
                            printMap(new DebugValueScope(null, globalMap), sortedValues);
                            break;
                        }
                        case "Complete": {
                            DebugValueMap globalMap = new DebugValueMap("Global");
                            for (DebugValueMap map : topLevelMaps) {
                                globalMap.addChild(map);
                            }
                            globalMap.group();
                            globalMap.normalize();
                            printMap(new DebugValueScope(null, globalMap), sortedValues);
                            break;
                        }
                        case "Thread":
                            for (DebugValueMap map : topLevelMaps) {
                                TTY.println("Showing the results for thread: " + map.getName());
                                map.group();
                                map.normalize();
                                printMap(new DebugValueScope(null, map), sortedValues);
                            }
                            break;
                        default:
                            throw new GraalInternalError("Unknown summary type: %s", summary);
                    }
                    if (reset) {
                        for (DebugValueMap topLevelMap : topLevelMaps) {
                            topLevelMap.reset();
                        }
                    }
                } catch (Throwable e) {
                    // Don't want this to change the exit status of the VM
                    PrintStream err = System.err;
                    err.println("Error while printing debug values:");
                    e.printStackTrace();
                }
            }
            if (phase != null) {
                TTY.println("</DebugValues:" + phase + ">");
            } else {
                TTY.println("</DebugValues>");
            }
        }
    }

    private void flattenChildren(DebugValueMap map, DebugValueMap globalMap) {
        globalMap.addChild(map);
        for (DebugValueMap child : map.getChildren()) {
            flattenChildren(child, globalMap);
        }
        map.clearChildren();
    }

    private static void printSummary(List<DebugValueMap> topLevelMaps, List<DebugValue> debugValues) {
        DebugValueMap result = new DebugValueMap("Summary");
        for (int i = debugValues.size() - 1; i >= 0; i--) {
            DebugValue debugValue = debugValues.get(i);
            int index = debugValue.getIndex();
            long total = collectTotal(topLevelMaps, index);
            result.setCurrentValue(index, total);
        }
        printMap(new DebugValueScope(null, result), debugValues);
    }

    static long collectTotal(DebugValue value) {
        List<DebugValueMap> maps = DebugValueMap.getTopLevelMaps();
        long total = 0;
        for (int i = 0; i < maps.size(); i++) {
            DebugValueMap map = maps.get(i);
            int index = value.getIndex();
            total += map.getCurrentValue(index);
            total += collectTotal(map.getChildren(), index);
        }
        return total;
    }

    private static long collectTotal(List<DebugValueMap> maps, int index) {
        long total = 0;
        for (int i = 0; i < maps.size(); i++) {
            DebugValueMap map = maps.get(i);
            total += map.getCurrentValue(index);
            total += collectTotal(map.getChildren(), index);
        }
        return total;
    }

    /**
     * Tracks the scope when printing a {@link DebugValueMap}, allowing "empty" scopes to be
     * omitted. An empty scope is one in which there are no (nested) non-zero debug values.
     */
    static class DebugValueScope {

        final DebugValueScope parent;
        final int level;
        final DebugValueMap map;
        private boolean printed;

        public DebugValueScope(DebugValueScope parent, DebugValueMap map) {
            this.parent = parent;
            this.map = map;
            this.level = parent == null ? 0 : parent.level + 1;
        }

        public void print() {
            if (!printed) {
                printed = true;
                if (parent != null) {
                    parent.print();
                }
                printIndent(level);
                TTY.println("%s", map.getName());
            }
        }
    }

    private static void printMap(DebugValueScope scope, List<DebugValue> debugValues) {

        for (DebugValue value : debugValues) {
            long l = scope.map.getCurrentValue(value.getIndex());
            if (l != 0 || !SuppressZeroDebugValues.getValue()) {
                scope.print();
                printIndent(scope.level + 1);
                TTY.println(value.getName() + "=" + value.toString(l));
            }
        }

        for (DebugValueMap child : scope.map.getChildren()) {
            printMap(new DebugValueScope(scope, child), debugValues);
        }
    }

    private static void printIndent(int level) {
        for (int i = 0; i < level; ++i) {
            TTY.print("    ");
        }
        TTY.print("|-> ");
    }

    @Override
    public void compileMethod(long metaspaceMethod, final int entryBCI, final boolean blocking) {
        final HotSpotResolvedJavaMethod method = HotSpotResolvedJavaMethod.fromMetaspace(metaspaceMethod);
        // We have to use a privileged action here because compilations are enqueued from user code
        // which very likely contains unprivileged frames.
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                compileMethod(method, entryBCI, blocking);
                return null;
            }
        });
    }

    /**
     * Compiles a method to machine code.
     */
    void compileMethod(final HotSpotResolvedJavaMethod method, final int entryBCI, final boolean blocking) {
        boolean osrCompilation = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;
        if (osrCompilation && bootstrapRunning) {
            // no OSR compilations during bootstrap - the compiler is just too slow at this point,
            // and we know that there are no endless loops
            return;
        }

        if (CompilationTask.withinEnqueue.get()) {
            // This is required to avoid deadlocking a compiler thread. The issue is that a
            // java.util.concurrent.BlockingQueue is used to implement the compilation worker
            // queues. If a compiler thread triggers a compilation, then it may be blocked trying
            // to add something to its own queue.
            return;
        }

        CompilationTask.withinEnqueue.set(Boolean.TRUE);
        try {
            if (method.tryToQueueForCompilation()) {
                assert method.isQueuedForCompilation();

                int id = allocateCompileTaskId(method, entryBCI);
                HotSpotBackend backend = runtime.getHostBackend();
                CompilationTask task = new CompilationTask(backend, method, entryBCI, id);

                if (blocking) {
                    task.runCompilation(true);
                } else {
                    try {
                        method.setCurrentTask(task);
                        compileQueue.execute(task);
                    } catch (RejectedExecutionException e) {
                        // The compile queue was already shut down.
                    }
                }
            }
        } finally {
            CompilationTask.withinEnqueue.set(Boolean.FALSE);
        }
    }

    @Override
    public JavaMethod createUnresolvedJavaMethod(String name, String signature, JavaType holder) {
        return new HotSpotMethodUnresolved(name, signature, holder);
    }

    @Override
    public JavaField createJavaField(JavaType holder, String name, JavaType type, int offset, int flags, boolean internal) {
        if (offset != -1) {
            HotSpotResolvedObjectType resolved = (HotSpotResolvedObjectType) holder;
            return resolved.createField(name, type, offset, flags, internal);
        }
        return new HotSpotUnresolvedField(holder, name, type);
    }

    @Override
    public ResolvedJavaMethod createResolvedJavaMethod(JavaType holder, long metaspaceMethod) {
        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) holder;
        return type.createMethod(metaspaceMethod);
    }

    @Override
    public ResolvedJavaType createPrimitiveJavaType(int basicType) {
        Class<?> javaClass;
        switch (basicType) {
            case 4:
                javaClass = boolean.class;
                break;
            case 5:
                javaClass = char.class;
                break;
            case 6:
                javaClass = float.class;
                break;
            case 7:
                javaClass = double.class;
                break;
            case 8:
                javaClass = byte.class;
                break;
            case 9:
                javaClass = short.class;
                break;
            case 10:
                javaClass = int.class;
                break;
            case 11:
                javaClass = long.class;
                break;
            case 14:
                javaClass = void.class;
                break;
            default:
                throw new IllegalArgumentException("Unknown basic type: " + basicType);
        }
        return HotSpotResolvedPrimitiveType.fromClass(javaClass);
    }

    @Override
    public HotSpotUnresolvedJavaType createUnresolvedJavaType(String name) {
        int dims = 0;
        int startIndex = 0;
        while (name.charAt(startIndex) == '[') {
            startIndex++;
            dims++;
        }

        // Decode name if necessary.
        if (name.charAt(name.length() - 1) == ';') {
            assert name.charAt(startIndex) == 'L';
            return new HotSpotUnresolvedJavaType(name, name.substring(startIndex + 1, name.length() - 1), dims);
        } else {
            return new HotSpotUnresolvedJavaType(HotSpotUnresolvedJavaType.getFullName(name, dims), name, dims);
        }
    }

    @Override
    public ResolvedJavaType createResolvedJavaType(Class javaMirror) {
        return HotSpotResolvedObjectType.fromClass(javaMirror);
    }

    @Override
    public PrintStream log() {
        return log;
    }
}
