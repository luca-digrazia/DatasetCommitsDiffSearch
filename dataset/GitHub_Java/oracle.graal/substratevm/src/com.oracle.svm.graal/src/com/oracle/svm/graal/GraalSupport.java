/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.graal;

import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.MatchStatement;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.CompositeValueClass;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.GraphDecoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.Feature.CompilationAccess;
import org.graalvm.nativeimage.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.amd64.SubstrateAMD64Backend;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

/**
 * Holds data that is pre-computed during native image generation and accessed at run time during a
 * Graal compilation.
 */
public class GraalSupport {

    private RuntimeConfiguration runtimeConfig;
    private Suites suites;
    private LIRSuites lirSuites;

    private SubstrateMethod[] methodsToCompile;
    private byte[] graphEncoding;
    private Object[] graphObjects;
    private NodeClass<?>[] graphNodeTypes;

    public final Map<Class<?>, NodeClass<?>> nodeClasses = new HashMap<>();
    public final Map<Class<?>, LIRInstructionClass<?>> instructionClasses = new HashMap<>();
    public final Map<Class<?>, CompositeValueClass<?>> compositeValueClasses = new HashMap<>();
    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry;

    protected Map<Class<?>, BasePhase.BasePhaseStatistics> basePhaseStatistics;
    protected Map<Class<?>, LIRPhase.LIRPhaseStatistics> lirPhaseStatistics;
    protected Map<Class<?>, TraceAllocationPhase.AllocationStatistics> traceAllocationPhaseStatistics;
    protected Function<Providers, Backend> runtimeBackendProvider;

    protected final GlobalMetrics metricValues = new GlobalMetrics();
    protected final List<DebugHandlersFactory> debugHandlersFactories = new ArrayList<>();
    protected final DiagnosticsOutputDirectory outputDirectory = new DiagnosticsOutputDirectory(RuntimeOptionValues.singleton());
    protected final Map<ExceptionAction, Integer> compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);

    public DebugContext openDebugContext(OptionValues options, CompilationIdentifier compilationId, Object compilable, PrintStream logStream) {
        Description description = new Description(compilable, compilationId.toString(CompilationIdentifier.Verbosity.ID));
        return DebugContext.create(options, description, metricValues, logStream, runtimeConfig.getDebugHandlersFactories());
    }

    public DiagnosticsOutputDirectory getDebugOutputDirectory() {
        return outputDirectory;
    }

    public Map<ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return compilationProblemsPerAction;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public GraalSupport() {
        /* By default the backend configuration is the same as for the native image. */
        runtimeBackendProvider = (providers) -> new SubstrateAMD64Backend(providers);
        for (DebugHandlersFactory c : GraalServices.load(DebugHandlersFactory.class)) {
            debugHandlersFactories.add(c);
        }
    }

    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> getMatchRuleRegistry() {
        return matchRuleRegistry;
    }

    public void setMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry) {
        this.matchRuleRegistry = matchRuleRegistry;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setRuntimeConfig(RuntimeConfiguration runtimeConfig, Suites suites, LIRSuites lirSuites) {
        get().runtimeConfig = runtimeConfig;
        get().suites = suites;
        get().lirSuites = lirSuites;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setMethodsToCompile(SubstrateMethod[] methodsToCompile) {
        boolean result = false;
        if (!Arrays.equals(get().methodsToCompile, methodsToCompile)) {
            get().methodsToCompile = methodsToCompile;
            result = true;
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setGraphEncoding(byte[] graphEncoding, Object[] graphObjects, NodeClass<?>[] graphNodeTypes) {
        boolean result = false;
        if (!Arrays.equals(get().graphEncoding, graphEncoding)) {
            get().graphEncoding = graphEncoding;
            result = true;
        }
        if (!Arrays.deepEquals(get().graphObjects, graphObjects)) {
            get().graphObjects = graphObjects;
            result = true;
        }
        if (!Arrays.equals(get().graphNodeTypes, graphNodeTypes)) {
            get().graphNodeTypes = graphNodeTypes;
            result = true;
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerImmutableObjects(CompilationAccess access) {
        access.registerAsImmutable(get().graphEncoding);
        access.registerAsImmutable(get().graphObjects);
        access.registerAsImmutable(get().graphNodeTypes);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void allocatePhaseStatisticsCache() {
        GraalSupport.get().basePhaseStatistics = new HashMap<>();
        GraalSupport.get().lirPhaseStatistics = new HashMap<>();
        GraalSupport.get().traceAllocationPhaseStatistics = new HashMap<>();
    }

    /* Invoked once for every class that is reachable in the native image. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerPhaseStatistics(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (!Modifier.isAbstract(newlyReachableClass.getModifiers())) {
            if (BasePhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalSupport.get().basePhaseStatistics, new BasePhase.BasePhaseStatistics(newlyReachableClass), access);

            } else if (LIRPhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalSupport.get().lirPhaseStatistics, new LIRPhase.LIRPhaseStatistics(newlyReachableClass), access);

            } else if (TraceAllocationPhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalSupport.get().traceAllocationPhaseStatistics, new TraceAllocationPhase.AllocationStatistics(newlyReachableClass), access);
            }
        }
    }

    private static <S> void registerStatistics(Class<?> phaseSubClass, Map<Class<?>, S> cache, S newStatistics, DuringAnalysisAccessImpl access) {
        assert !cache.containsKey(phaseSubClass);

        cache.put(phaseSubClass, newStatistics);
        access.requireAnalysisIteration();
    }

    public static GraalSupport get() {
        return ImageSingletons.lookup(GraalSupport.class);
    }

    public static RuntimeConfiguration getRuntimeConfig() {
        return get().runtimeConfig;
    }

    public static Suites getSuites() {
        return get().suites;
    }

    public static LIRSuites getLIRSuites() {
        return get().lirSuites;
    }

    public static SubstrateMethod[] getMethodsToCompile() {
        return get().methodsToCompile;
    }

    public static EncodedGraph encodedGraph(SharedRuntimeMethod method, boolean trackNodeSourcePosition) {
        int startOffset = method.getEncodedGraphStartOffset();
        if (startOffset == -1) {
            return null;
        }
        return new EncodedGraph(get().graphEncoding, startOffset, get().graphObjects, get().graphNodeTypes, null, null, null, false, trackNodeSourcePosition);
    }

    public static StructuredGraph decodeGraph(DebugContext debug, String name, CompilationIdentifier compilationId, SharedRuntimeMethod method) {
        EncodedGraph encodedGraph = encodedGraph(method, false);
        if (encodedGraph == null) {
            return null;
        }

        boolean isSubstitution = method.getAnnotation(Snippet.class) != null || method.getAnnotation(MethodSubstitution.class) != null;
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug).name(name).method(method).compilationId(compilationId).setIsSubstitution(isSubstitution).build();
        GraphDecoder decoder = new GraphDecoder(ConfigurationValues.getTarget().arch, graph);
        decoder.decode(encodedGraph);
        return graph;
    }

    public static class GraalShutdownHook implements Runnable {
        @Override
        public void run() {
            GraalSupport graalSupport = GraalSupport.get();
            graalSupport.metricValues.print(RuntimeOptionValues.singleton());
            graalSupport.outputDirectory.close();
        }
    }

    public static Function<Providers, Backend> getRuntimeBackendProvider() {
        return get().runtimeBackendProvider;
    }

    public static void setRuntimeBackendProvider(Function<Providers, Backend> backendProvider) {
        get().runtimeBackendProvider = backendProvider;
    }
}
