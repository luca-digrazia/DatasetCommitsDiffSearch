/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;

import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Keeps a type-hierarchy dependency graph for {@link AnalysisType}s from {@code universe}. Each
 * type carries the information if it {@link Safety#SAFE} or {@link Safety#UNSAFE} to execute during
 * native-image generation.
 *
 * The algorithm assigns all types ( {@link #initialTypeInitializerSafety}) and all methods (
 * {@link #initialMethodSafety}) with their initial safety.
 *
 * Then the information about unsafety is iteratively propagated through the graph in
 * {@link #computeInitializerSafety}.
 *
 * NOTE: the dependency between methods and type initializers is maintained by the
 * {@link SubstrateClassInitializationPlugin} that emits calls to
 * {@link DynamicHub#ensureInitialized()} for every load, store, call, and instantiation in the
 * bytecode. We extract those dependencies here by using the
 * {@link #getInitializerType(InvokeTypeFlow)} method.
 *
 */
public class TypeInitializerGraph {
    private final SVMHost hostVM;
    private ClassInitializationSupport classInitializationSupport;
    private AnalysisMethod ensureInitializedMethod;

    private enum Safety {
        SAFE,
        UNSAFE,
    }

    private final Map<AnalysisType, Safety> types = new HashMap<>();
    private final Map<AnalysisType, Set<AnalysisType>> dependencies = new HashMap<>();

    private final Map<AnalysisMethod, Safety> methodSafety = new HashMap<>();
    private final Collection<AnalysisMethod> methods;

    TypeInitializerGraph(AnalysisUniverse universe, AnalysisMethod ensureInitializedMethod) {
        assert universe.getMethods().contains(ensureInitializedMethod);

        this.ensureInitializedMethod = ensureInitializedMethod;
        hostVM = ((SVMHost) universe.hostVM());
        classInitializationSupport = hostVM.getClassInitializationSupport();

        universe.getTypes().forEach(this::addInitializer);
        universe.getTypes().forEach(this::addInitializerDependencies);
        /* initialize all methods with original safety data */
        methods = universe.getMethods();
        methods.forEach(m -> methodSafety.put(m, initialMethodSafety(m)));
    }

    /**
     * Iteratively propagate information about unsafety through the methods (
     * {@link #updateMethodSafety}) and the initializer graph (
     * {@link #updateTypeInitializerSafety()}).
     */
    void computeInitializerSafety() {
        boolean newPromotions;
        do {
            AtomicBoolean methodSafetyChanged = new AtomicBoolean(false);
            methods.stream().filter(m -> methodSafety.get(m) == Safety.SAFE)
                            .forEach(m -> {
                                if (updateMethodSafety(m)) {
                                    methodSafetyChanged.set(true);
                                }
                            });
            newPromotions = methodSafetyChanged.get() || updateTypeInitializerSafety();
        } while (newPromotions);
    }

    /**
     * A type initializer is initially unsafe only if it was marked by the user as such.
     */
    private Safety initialTypeInitializerSafety(AnalysisType t) {
        return classInitializationSupport.specifiedInitKindFor(t.getJavaClass()) == InitKind.RUN_TIME ? Safety.UNSAFE
                        : Safety.SAFE;
    }

    boolean isUnsafe(AnalysisType type) {
        return types.get(type) == Safety.UNSAFE;
    }

    public void setUnsafe(AnalysisType t) {
        types.put(t, Safety.UNSAFE);
    }

    private boolean updateTypeInitializerSafety() {
        List<AnalysisType> newUnsafeTypes = types.keySet().stream().filter(type -> shouldPromoteToUnsafe(type, methodSafety)).collect(Collectors.toList());
        newUnsafeTypes.forEach(this::setUnsafe);
        return !newUnsafeTypes.isEmpty();
    }

    private void addInitializerDependencies(AnalysisType t) {
        addInterfaceDependencies(t, t.getInterfaces());
        if (t.getSuperclass() != null) {
            addDependency(t, t.getSuperclass());
        }
    }

    private void addInterfaceDependencies(AnalysisType t, AnalysisType[] interfaces) {
        for (AnalysisType anInterface : interfaces) {
            if (ClassInitializationFeature.declaresDefaultMethods(anInterface)) {
                addDependency(t, anInterface);
            }
            addInterfaceDependencies(t, anInterface.getInterfaces());
        }
    }

    private void addDependency(AnalysisType dependent, AnalysisType dependee) {
        dependencies.get(dependent).add(dependee);
    }

    /**
     * Method is considered initially unsafe if (1) it is a substituted method, or (2) if any of the
     * invokes are unsafe {@link TypeInitializerGraph#isInvokeInitiallyUnsafe}.
     *
     * Substituted methods are unsafe because their execution at image-build time would initialize
     * types unknown to points-to analysis (which sees only the substituted version.
     */
    private Safety initialMethodSafety(AnalysisMethod m) {
        return m.getTypeFlow().getInvokes().stream().anyMatch(this::isInvokeInitiallyUnsafe) ||
                        hasStaticFieldAccess(m) ||
                        isSubstitutedMethod(m) ? Safety.UNSAFE : Safety.SAFE;
    }

    /**
     * Classes are only safe for automatic initialization if the class initializer has no side
     * effect on other classes and cannot be influenced by other classes. Otherwise there would be
     * observable side effects. For example, if a class initializer of class A writes a static field
     * B.f in class B, then someone could rely on reading the old value of B.f before triggering
     * initialization of A. Similarly, if a class initializer of class A reads a static field B.f,
     * then an early automatic initialization of class A could read a non-yet-set value of B.f.
     *
     * Note that it is not necessary to disallow instance field accesses: Objects allocated by the
     * class initializer itself can always be accessed because they are independent from other
     * initializers; all other objects must be loaded transitively from a static field.
     *
     * Currently, we are conservative and mark all methods that access static fields as unsafe for
     * automatic class initialization (unless the class initializer itself accesses a static field
     * of its own class - the common way of initializing static fields). The check could be relaxed
     * by tracking the call chain, i.e., allowing static field accesses when the root method of the
     * call chain is the class initializer. But this does not fit well into the current approach
     * where each method has a `Safety` flag.
     */
    private static boolean hasStaticFieldAccess(AnalysisMethod m) {
        StructuredGraph graph = m.getTypeFlow().getGraph();
        if (graph != null) {
            for (Node n : graph.getNodes()) {
                if (n instanceof AccessFieldNode) {
                    ResolvedJavaField field = ((AccessFieldNode) n).field();
                    if (field.isStatic() && (!m.isClassInitializer() || !field.getDeclaringClass().equals(m.getDeclaringClass()))) {
                        return true;
                    }
                } else if (n instanceof UnsafeAccessNode) {
                    /*
                     * Unsafe memory access nodes are rare, so it does not pay off to check what
                     * kind of field they are accessing.
                     */
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSubstitutedMethod(AnalysisMethod m) {
        return !classInitializationSupport.shouldInitializeAtRuntime(m.getDeclaringClass()) && m.getWrapped() instanceof SubstitutionMethod;
    }

    /**
     * Unsafe invokes (1) call native methods, (2) can't be statically bound, and/or (3) initialize
     * unknown classes programmatically.
     */
    private boolean isInvokeInitiallyUnsafe(InvokeTypeFlow i) {
        assert !ensureInitializedMethod.isNative();
        return i.getTargetMethod().isNative() ||
                        !i.canBeStaticallyBound() ||
                        (i.getTargetMethod().equals(ensureInitializedMethod) && !getInitializerType(i).isPresent());
    }

    /**
     * Type is promoted to unsafe when it is not already unsafe and it (1) depends on an unsafe
     * type, or (2) its class initializer was promoted to unsafe.
     */
    private boolean shouldPromoteToUnsafe(AnalysisType type, Map<AnalysisMethod, Safety> safeMethods) {
        if (types.get(type) == Safety.UNSAFE) {
            return false;
        } else if (dependencies.get(type).stream().anyMatch(t -> shouldPromoteToUnsafe(t, safeMethods))) {
            return true;
        } else {
            return type.getClassInitializer() != null && safeMethods.get(type.getClassInitializer()) == Safety.UNSAFE;
        }
    }

    /**
     * A method is unsafe if any of it's invokes (1) are unsafe or (2) they depend on an unsafe
     * class initializer.
     */
    private boolean updateMethodSafety(AnalysisMethod m) {
        assert methodSafety.get(m) == Safety.SAFE;
        Collection<InvokeTypeFlow> invokes = m.getTypeFlow().getInvokes();
        if (invokes.stream().anyMatch(this::isInvokeUnsafeIterative)) {
            methodSafety.put(m, Safety.UNSAFE);
            return true;
        }
        return false;
    }

    /**
     * Invoke becomes unsafe if (1) it calls unsafe static initialization, or (2) it calls other
     * unsafe methods.
     */
    private boolean isInvokeUnsafeIterative(InvokeTypeFlow i) {
        assert i.getTargetMethod() != null : "All methods can be statically bound.";
        return getInitializerType(i)
                        .map(this::isUnsafe)
                        .orElseGet(() -> methodSafety.get(i.getTargetMethod()) == Safety.UNSAFE);
    }

    /**
     * Gets a type that is being initalized with Class.ensureInitialized when {@code i} calls
     * {@link DynamicHub#ensureInitialized} and the argument is constant. Otherwise, this is a
     * regular call and we return an empty option.
     */
    private Optional<AnalysisType> getInitializerType(InvokeTypeFlow i) {
        if (i.getTargetMethod().equals(ensureInitializedMethod)) {
            assert i.getActualParameters().length == 1 : "ensureInitialized should have only one parameter, found " + i.getActualParameters().length;
            if (i.getActualParameters()[0].getSource() instanceof ConstantNode) {
                assert SubstrateObjectConstant
                                .asObject(((ConstantNode) i.getActualParameters()[0].getSource()).asConstant()) instanceof DynamicHub : "ensureInitialized must receive a constant dynamic hub";
                DynamicHub hub = (DynamicHub) SubstrateObjectConstant.asObject(((ConstantNode) i.getActualParameters()[0].getSource()).asConstant());
                return Optional.of(hostVM.lookupType(hub));
            }
        }
        return Optional.empty();
    }

    private void addInitializer(AnalysisType t) {
        types.put(t, initialTypeInitializerSafety(t));
        dependencies.put(t, new HashSet<>());
    }

    Set<AnalysisType> getDependencies(AnalysisType type) {
        return Collections.unmodifiableSet(dependencies.get(type));
    }

}
