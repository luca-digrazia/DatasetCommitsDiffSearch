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
package com.oracle.graal.truffle;

import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.truffle.phases.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of a cache for Truffle graphs for improving partial evaluation time.
 */
public final class TruffleCacheImpl implements TruffleCache {

    private final Providers providers;
    private final GraphBuilderConfiguration config;
    private final GraphBuilderConfiguration configForRootGraph;
    private final OptimisticOptimizations optimisticOptimizations;

    private final HashMap<List<Object>, StructuredGraph> cache = new HashMap<>();
    private final HashMap<List<Object>, Long> lastUsed = new HashMap<>();
    private final StructuredGraph markerGraph = new StructuredGraph();
    private final ResolvedJavaType stringBuilderClass;
    private final ResolvedJavaMethod executeHelperMethod;
    private long counter;

    public TruffleCacheImpl(Providers providers, GraphBuilderConfiguration config, GraphBuilderConfiguration configForRootGraph, OptimisticOptimizations optimisticOptimizations) {
        this.providers = providers;
        this.config = config;
        this.configForRootGraph = configForRootGraph;
        this.optimisticOptimizations = optimisticOptimizations;
        this.stringBuilderClass = providers.getMetaAccess().lookupJavaType(StringBuilder.class);
        try {
            executeHelperMethod = providers.getMetaAccess().lookupJavaMethod(OptimizedCallTarget.class.getDeclaredMethod("executeHelper", PackedFrame.class, Arguments.class));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    public StructuredGraph createRootGraph() {
        StructuredGraph graph = new StructuredGraph(executeHelperMethod);
        new GraphBuilderPhase.Instance(providers.getMetaAccess(), configForRootGraph, TruffleCompilerImpl.Optimizations).apply(graph);
        return graph;
    }

    @SuppressWarnings("unused")
    public StructuredGraph lookup(final ResolvedJavaMethod method, final NodeInputList<ValueNode> arguments, final Assumptions assumptions, final CanonicalizerPhase finalCanonicalizer,
                    boolean ignoreSlowPath) {

        if (!ignoreSlowPath && method.getAnnotation(CompilerDirectives.SlowPath.class) != null) {
            return null;
        }

        List<Object> key = new ArrayList<>(arguments.size() + 1);
        key.add(method);
        for (ValueNode v : arguments) {
            if (v.getKind() == Kind.Object) {
                key.add(v.stamp());
            }
        }
        StructuredGraph resultGraph = cache.get(key);
        if (resultGraph != null) {
            lastUsed.put(key, counter++);
            return resultGraph;
        }

        if (resultGraph == markerGraph) {
            // Avoid recursive inline.
            return null;
        }

        if (lastUsed.values().size() >= TruffleCompilerOptions.TruffleMaxCompilationCacheSize.getValue()) {
            List<Long> lastUsedList = new ArrayList<>();
            for (long l : lastUsed.values()) {
                lastUsedList.add(l);
            }
            Collections.sort(lastUsedList);
            long mid = lastUsedList.get(lastUsedList.size() / 2);

            List<List<Object>> toRemoveList = new ArrayList<>();
            for (Entry<List<Object>, Long> entry : lastUsed.entrySet()) {
                if (entry.getValue() < mid) {
                    toRemoveList.add(entry.getKey());
                }
            }

            for (List<Object> entry : toRemoveList) {
                cache.remove(entry);
                lastUsed.remove(entry);
            }
        }

        lastUsed.put(key, counter++);
        cache.put(key, markerGraph);
        try (Scope s = Debug.scope("TruffleCache", providers.getMetaAccess(), method)) {

            final StructuredGraph graph = new StructuredGraph(method);
            final PhaseContext phaseContext = new PhaseContext(providers, new Assumptions(false));
            Mark mark = graph.getMark();
            new GraphBuilderPhase.Instance(phaseContext.getMetaAccess(), config, optimisticOptimizations).apply(graph);

            for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
                if (param.getKind() == Kind.Object) {
                    ValueNode actualArgument = arguments.get(param.index());
                    param.setStamp(param.stamp().join(actualArgument.stamp()));
                }
            }

            // Intrinsify methods.
            new ReplaceIntrinsicsPhase(providers.getReplacements()).apply(graph);

            // Convert deopt to guards.
            new ConvertDeoptimizeToGuardPhase().apply(graph);

            CanonicalizerPhase canonicalizerPhase = new CanonicalizerPhase(!ImmutableCode.getValue());
            PartialEscapePhase partialEscapePhase = new PartialEscapePhase(false, canonicalizerPhase);

            while (true) {

                partialEscapePhase.apply(graph, phaseContext);

                // Conditional elimination.
                ConditionalEliminationPhase conditionalEliminationPhase = new ConditionalEliminationPhase(phaseContext.getMetaAccess());
                conditionalEliminationPhase.apply(graph);

                // Canonicalize / constant propagate.
                canonicalizerPhase.apply(graph, phaseContext);

                boolean inliningProgress = false;
                for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.class)) {
                    if (!graph.getMark().equals(mark)) {
                        // Make sure macro substitutions such as
                        // CompilerDirectives.transferToInterpreter get processed first.
                        for (Node newNode : graph.getNewNodes(mark)) {
                            if (newNode instanceof MethodCallTargetNode) {
                                MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) newNode;
                                Class<? extends FixedWithNextNode> macroSubstitution = providers.getReplacements().getMacroSubstitution(methodCallTargetNode.targetMethod());
                                if (macroSubstitution != null) {
                                    InliningUtil.inlineMacroNode(methodCallTargetNode.invoke(), methodCallTargetNode.targetMethod(), macroSubstitution);
                                } else {
                                    tryCutOffRuntimeExceptions(methodCallTargetNode);
                                }
                            }
                        }
                        mark = graph.getMark();
                    }
                    if (methodCallTarget.isAlive() && methodCallTarget.invoke() != null && shouldInline(methodCallTarget)) {
                        inliningProgress = true;
                        List<Node> canonicalizerUsages = new ArrayList<Node>();
                        for (Node n : methodCallTarget.invoke().asNode().usages()) {
                            if (n instanceof Canonicalizable) {
                                canonicalizerUsages.add(n);
                            }
                        }
                        List<ValueNode> argumentSnapshot = methodCallTarget.arguments().snapshot();
                        Mark beforeInvokeMark = graph.getMark();
                        expandInvoke(methodCallTarget);
                        for (Node arg : argumentSnapshot) {
                            if (arg != null && arg.recordsUsages()) {
                                for (Node argUsage : arg.usages()) {
                                    if (graph.isNew(beforeInvokeMark, argUsage) && argUsage instanceof Canonicalizable) {
                                        canonicalizerUsages.add(argUsage);
                                    }
                                }
                            }
                        }
                        canonicalizerPhase.applyIncremental(graph, phaseContext, canonicalizerUsages);
                    }
                }

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                new EarlyReadEliminationPhase(canonicalizerPhase).apply(graph, phaseContext);

                if (!inliningProgress) {
                    break;
                }
            }

            cache.put(key, graph);
            if (TruffleCompilerOptions.TraceTruffleCacheDetails.getValue()) {
                TTY.println(String.format("[truffle] added to graph cache method %s with %d nodes.", method, graph.getNodeCount()));
            }
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

    }

    private void expandInvoke(MethodCallTargetNode methodCallTargetNode) {
        StructuredGraph inlineGraph = providers.getReplacements().getMethodSubstitution(methodCallTargetNode.targetMethod());
        if (inlineGraph == null) {
            inlineGraph = TruffleCacheImpl.this.lookup(methodCallTargetNode.targetMethod(), methodCallTargetNode.arguments(), null, null, false);
        }
        if (inlineGraph == this.markerGraph) {
            // Can happen for recursive calls.
            throw GraphUtil.approxSourceException(methodCallTargetNode, new IllegalStateException("Found illegal recursive call to " + methodCallTargetNode.targetMethod() +
                            ", must annotate such calls with @CompilerDirectives.SlowPath!"));
        }
        Invoke invoke = methodCallTargetNode.invoke();
        InliningUtil.inline(invoke, inlineGraph, true);
    }

    private boolean tryCutOffRuntimeExceptions(MethodCallTargetNode methodCallTargetNode) {
        if (methodCallTargetNode.targetMethod().isConstructor()) {
            ResolvedJavaType runtimeException = providers.getMetaAccess().lookupJavaType(RuntimeException.class);
            ResolvedJavaType controlFlowException = providers.getMetaAccess().lookupJavaType(ControlFlowException.class);
            ResolvedJavaType exceptionType = Objects.requireNonNull(ObjectStamp.typeOrNull(methodCallTargetNode.receiver().stamp()));
            if (runtimeException.isAssignableFrom(methodCallTargetNode.targetMethod().getDeclaringClass()) && !controlFlowException.isAssignableFrom(exceptionType)) {
                DeoptimizeNode deoptNode = methodCallTargetNode.graph().add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode));
                FixedNode invokeNode = methodCallTargetNode.invoke().asNode();
                invokeNode.replaceAtPredecessor(deoptNode);
                GraphUtil.killCFG(invokeNode);
                return true;
            }
        }
        return false;
    }

    private boolean shouldInline(final MethodCallTargetNode methodCallTargetNode) {
        return (methodCallTargetNode.invokeKind() == InvokeKind.Special || methodCallTargetNode.invokeKind() == InvokeKind.Static) &&
                        !Modifier.isNative(methodCallTargetNode.targetMethod().getModifiers()) && methodCallTargetNode.targetMethod().getAnnotation(ExplodeLoop.class) == null &&
                        methodCallTargetNode.targetMethod().getAnnotation(CompilerDirectives.SlowPath.class) == null &&
                        !methodCallTargetNode.targetMethod().getDeclaringClass().equals(stringBuilderClass);
    }
}
