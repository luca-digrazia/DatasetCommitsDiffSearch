/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.lambda;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This substitution replaces all lambda proxy types with types that have a stable names. The name
 * is formed from the signature of the target method that the lambda is calling.
 *
 * NOTE: there is a particular case in which names are not stable. If multiple lambda proxies have a
 * same target in a same class they are indistinguishable in bytecode. Then their stable names get
 * appended with a unique number for that class. To make this corner case truly stable, analysis
 * must be run in the single-threaded mode.
 */
public class LambdaProxyRenamingSubstitutionProcessor extends SubstitutionProcessor {

    private static final Pattern LAMBDA_PATTERN = Pattern.compile("\\$\\$Lambda\\$\\d+/\\d+");
    private static final GraphBuilderConfiguration LAMBDA_PARSER_CONFIG = GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins())).withEagerResolving(true);
    private static final GraphBuilderPhase LAMBDA_PARSER_PHASE = new GraphBuilderPhase(LAMBDA_PARSER_CONFIG);

    private final BigBang bb;

    private final ConcurrentHashMap<ResolvedJavaType, LambdaSubstitutionType> typeSubstitutions;
    private final Set<String> nameSet;

    static boolean isLambdaType(ResolvedJavaType type) {
        return type.isFinalFlagSet() &&
                        type.getName().contains("/") && /* isVMAnonymousClass */
                        lambdaMatcher(type.getName()).find();
    }

    LambdaProxyRenamingSubstitutionProcessor(BigBang bigBang) {
        this.typeSubstitutions = new ConcurrentHashMap<>();
        this.nameSet = new HashSet<>();
        this.bb = bigBang;
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (isLambdaType(type)) {
            return getSubstitution(type);
        } else {
            return type;
        }
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType type) {
        if (type instanceof LambdaSubstitutionType) {
            return ((LambdaSubstitutionType) type).getOriginal();
        } else {
            return type;
        }
    }

    private static String createStableLambdaName(ResolvedJavaType lambdaType, ResolvedJavaMethod targetMethod) {
        assert lambdaMatcher(lambdaType.getName()).find() : "Stable name should be created only for lambda types.";
        Matcher m = lambdaMatcher(lambdaType.getName());
        String stableTargetMethod = targetMethod.format("%H.%n(%P)%R").replaceAll("[$.()]", "_")
                        .replaceAll("\\[]", "_arr")
                        .replaceAll(", ", "_");
        return m.replaceFirst("\\$\\$Lambda\\$" + stableTargetMethod);
    }

    @SuppressWarnings("try")
    private LambdaSubstitutionType getSubstitution(ResolvedJavaType original) {
        return typeSubstitutions.computeIfAbsent(original, (key) -> {
            OptionValues options = bb.getOptions();
            DebugContext debug = DebugContext.create(options, new GraalDebugHandlersFactory(bb.getProviders().getSnippetReflection()));

            ResolvedJavaMethod[] lambdaProxyMethods = Arrays.stream(key.getDeclaredMethods()).filter(m -> !m.isBridge() && m.isPublic()).toArray(ResolvedJavaMethod[]::new);
            assert lambdaProxyMethods.length == 1 : "There must be only one method calling the target.";

            StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(lambdaProxyMethods[0]).build();
            try (DebugContext.Scope ignored = debug.scope("Lambda target method analysis", graph, key, this)) {
                HighTierContext context = new HighTierContext(GraalAccess.getOriginalProviders(), null, OptimisticOptimizations.NONE);
                LAMBDA_PARSER_PHASE.apply(graph, context);
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            Optional<Invoke> lambdaTargetInvokeOption = StreamSupport.stream(graph.getInvokes().spliterator(), false).findFirst();
            if (!lambdaTargetInvokeOption.isPresent()) {
                throw VMError.shouldNotReachHere("Lambda without a target invoke.");
            }
            String stableName = createStableLambdaName(key, lambdaTargetInvokeOption.get().getTargetMethod());
            return new LambdaSubstitutionType(key, findUniqueNameForSameTarget(stableName));
        });
    }

    /**
     * Finds a unique name for a lambda proxies with a same target originating from the same class.
     *
     * NOTE: this is stable only in a single threaded build.
     */
    private String findUniqueNameForSameTarget(String stableName) {
        synchronized (nameSet) {
            String newStableName = stableName;
            CharSequence stableNameBase = stableName.subSequence(0, stableName.length() - 1);
            int i = 1;
            while (nameSet.contains(newStableName)) {
                newStableName = stableNameBase + "_" + i + ";";
                i += 1;
            }
            nameSet.add(stableName);
            return newStableName;
        }
    }

    private static Matcher lambdaMatcher(String value) {
        return LAMBDA_PATTERN.matcher(value);
    }

}
