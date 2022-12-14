/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.OptimizedTypeCheckViolated;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.PRIMARY_SUPERS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.SECONDARY_SUPER_CACHE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadHubIntrinsic;
import static org.graalvm.compiler.hotspot.replacements.HotspotSnippetsOptions.TypeCheckMaxHints;
import static org.graalvm.compiler.hotspot.replacements.HotspotSnippetsOptions.TypeCheckMinProfileHitProbability;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.checkSecondarySubType;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.checkUnknownSubType;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.createHints;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.displayHit;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.displayMiss;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.exactHit;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.exactMiss;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.hintsHit;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.hintsMiss;
import static org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.isNull;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.replacements.TypeCheckSnippetUtils.Hints;
import org.graalvm.compiler.hotspot.replacements.aot.ResolveConstantSnippets;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.TypeCheckHints;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.InstanceOfSnippetsTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.TriState;

/**
 * Snippets used for implementing the type test of an instanceof instruction. Since instanceof is a
 * floating node, it is lowered separately for each of its usages.
 *
 * The type tests implemented are described in the paper
 * <a href="http://dl.acm.org/citation.cfm?id=583821"> Fast subtype checking in the HotSpot JVM</a>
 * by Cliff Click and John Rose.
 */
public class InstanceOfSnippets implements Snippets {

    /**
     * A test against a set of hints derived from a profile with 100% precise coverage of seen
     * types. This snippet deoptimizes on hint miss paths.
     */
    @Snippet
    public static Object instanceofWithProfile(Object object, @VarargsParameter KlassPointer[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue, Object falseValue,
                    @ConstantParameter boolean nullSeen) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            if (!nullSeen) {
                // See comment below for other deoptimization path; the
                // same reasoning applies here.
                DeoptimizeNode.deopt(InvalidateReprofile, OptimizedTypeCheckViolated);
            }
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            KlassPointer hintHub = hints[i];
            boolean positive = hintIsPositive[i];
            if (probability(LIKELY_PROBABILITY, hintHub.equal(objectHub))) {
                hintsHit.inc();
                return positive ? trueValue : falseValue;
            }
            hintsMiss.inc();
        }
        // This maybe just be a rare event but it might also indicate a phase change
        // in the application. Ideally we want to use DeoptimizationAction.None for
        // the former but the cost is too high if indeed it is the latter. As such,
        // we defensively opt for InvalidateReprofile.
        DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, OptimizedTypeCheckViolated);
        return falseValue;
    }

    /**
     * A test against a final type.
     */
    @Snippet
    public static Object instanceofExact(Object object, KlassPointer exactHub, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        if (probability(LIKELY_PROBABILITY, objectHub.notEqual(exactHub))) {
            exactMiss.inc();
            return falseValue;
        }
        exactHit.inc();
        return trueValue;
    }

    @Snippet
    public static Object instanceofExactPIC(Object object, KlassPointer exactHub, Object trueValue, Object falseValue) {
        KlassPointer exactHubPIC = ResolveConstantSnippets.resolveKlassConstant(exactHub);
        return instanceofExact(object, exactHubPIC, trueValue, falseValue);
    }

    /**
     * A test against a primary type.
     */
    @Snippet
    public static Object instanceofPrimary(KlassPointer hub, Object object, @ConstantParameter int superCheckOffset, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        if (probability(NOT_LIKELY_PROBABILITY, objectHub.readKlassPointer(superCheckOffset, PRIMARY_SUPERS_LOCATION).notEqual(hub))) {
            displayMiss.inc();
            return falseValue;
        }
        displayHit.inc();
        return trueValue;
    }

    @Snippet
    public static Object instanceofPrimaryPIC(KlassPointer hub, Object object, @ConstantParameter int superCheckOffset, Object trueValue, Object falseValue) {
        KlassPointer resolvedHub = ResolveConstantSnippets.resolveKlassConstant(hub);
        return instanceofPrimary(resolvedHub, object, superCheckOffset, trueValue, falseValue);
    }

    /**
     * A test against a restricted secondary type type.
     */
    @Snippet
    public static Object instanceofSecondary(KlassPointer hub, Object object, @VarargsParameter KlassPointer[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            KlassPointer hintHub = hints[i];
            boolean positive = hintIsPositive[i];
            if (probability(NOT_FREQUENT_PROBABILITY, hintHub.equal(objectHub))) {
                hintsHit.inc();
                return positive ? trueValue : falseValue;
            }
        }
        hintsMiss.inc();
        if (!checkSecondarySubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    @Snippet
    public static Object instanceofSecondaryPIC(KlassPointer hub, Object object, @VarargsParameter KlassPointer[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue,
                    Object falseValue) {
        KlassPointer resolvedHub = ResolveConstantSnippets.resolveKlassConstant(hub);
        return instanceofSecondary(resolvedHub, object, hints, hintIsPositive, trueValue, falseValue);
    }

    /**
     * Type test used when the type being tested against is not known at compile time.
     */
    @Snippet
    public static Object instanceofDynamic(KlassPointer hub, Object object, Object trueValue, Object falseValue, @ConstantParameter boolean allowNull) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            if (allowNull) {
                return trueValue;
            } else {
                return falseValue;
            }
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer nonNullObjectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        // The hub of a primitive type can be null => always return false in this case.
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, !hub.isNull())) {
            if (checkUnknownSubType(hub, nonNullObjectHub)) {
                return trueValue;
            }
        }
        return falseValue;
    }

    @Snippet
    public static Object isAssignableFrom(Class<?> thisClassNonNull, Class<?> otherClass, Object trueValue, Object falseValue) {
        if (otherClass == null) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
            return false;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        Class<?> otherClassNonNull = PiNode.piCastNonNullClass(otherClass, anchorNode);

        if (BranchProbabilityNode.probability(BranchProbabilityNode.NOT_LIKELY_PROBABILITY, thisClassNonNull == otherClassNonNull)) {
            return trueValue;
        }

        KlassPointer thisHub = ClassGetHubNode.readClass(thisClassNonNull);
        KlassPointer otherHub = ClassGetHubNode.readClass(otherClassNonNull);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, !thisHub.isNull())) {
            if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, !otherHub.isNull())) {
                GuardingNode guardNonNull = SnippetAnchorNode.anchor();
                KlassPointer nonNullOtherHub = ClassGetHubNode.piCastNonNull(otherHub, guardNonNull);
                if (TypeCheckSnippetUtils.checkUnknownSubType(thisHub, nonNullOtherHub)) {
                    return trueValue;
                }
            }
        }

        // If either hub is null, one of them is a primitive type and given that the class is not
        // equal, return false.
        return falseValue;
    }

    public static class Templates extends InstanceOfSnippetsTemplates {

        private final SnippetInfo instanceofWithProfile = snippet(InstanceOfSnippets.class, "instanceofWithProfile");
        private final SnippetInfo instanceofExact = snippet(InstanceOfSnippets.class, "instanceofExact");
        private final SnippetInfo instanceofExactPIC = snippet(InstanceOfSnippets.class, "instanceofExactPIC");
        private final SnippetInfo instanceofPrimary = snippet(InstanceOfSnippets.class, "instanceofPrimary");
        private final SnippetInfo instanceofPrimaryPIC = snippet(InstanceOfSnippets.class, "instanceofPrimaryPIC");
        private final SnippetInfo instanceofSecondary = snippet(InstanceOfSnippets.class, "instanceofSecondary", SECONDARY_SUPER_CACHE_LOCATION);
        private final SnippetInfo instanceofSecondaryPIC = snippet(InstanceOfSnippets.class, "instanceofSecondaryPIC", SECONDARY_SUPER_CACHE_LOCATION);
        private final SnippetInfo instanceofDynamic = snippet(InstanceOfSnippets.class, "instanceofDynamic", SECONDARY_SUPER_CACHE_LOCATION);
        private final SnippetInfo isAssignableFrom = snippet(InstanceOfSnippets.class, "isAssignableFrom", SECONDARY_SUPER_CACHE_LOCATION);

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        @Override
        protected Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            if (replacer.instanceOf instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) replacer.instanceOf;
                ValueNode object = instanceOf.getValue();
                Assumptions assumptions = instanceOf.graph().getAssumptions();

                OptionValues options = instanceOf.getOptions();
                JavaTypeProfile profile = instanceOf.profile();
                if (GeneratePIC.getValue(options)) {
                    // FIXME: We can't embed constants in hints. We can't really load them from GOT
                    // either. Hard problem.
                    profile = null;
                }
                TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.type(), profile, assumptions, TypeCheckMinProfileHitProbability.getValue(options),
                                TypeCheckMaxHints.getValue(options));
                final HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) instanceOf.type().getType();
                ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), providers.getMetaAccess(), instanceOf.graph());

                Arguments args;

                StructuredGraph graph = instanceOf.graph();
                if (hintInfo.hintHitProbability >= 1.0 && hintInfo.exact == null) {
                    Hints hints = createHints(hintInfo, providers.getMetaAccess(), false, graph);
                    args = new Arguments(instanceofWithProfile, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("object", object);
                    args.addVarargs("hints", KlassPointer.class, KlassPointerStamp.klassNonNull(), hints.hubs);
                    args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(JavaKind.Boolean), hints.isPositive);
                } else if (hintInfo.exact != null) {
                    SnippetInfo snippet = GeneratePIC.getValue(options) ? instanceofExactPIC : instanceofExact;
                    args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("object", object);
                    args.add("exactHub", ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), ((HotSpotResolvedObjectType) hintInfo.exact).klass(), providers.getMetaAccess(), graph));
                } else if (type.isPrimaryType()) {
                    SnippetInfo snippet = GeneratePIC.getValue(options) ? instanceofPrimaryPIC : instanceofPrimary;
                    args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("hub", hub);
                    args.add("object", object);
                    args.addConst("superCheckOffset", type.superCheckOffset());
                } else {
                    Hints hints = createHints(hintInfo, providers.getMetaAccess(), false, graph);
                    SnippetInfo snippet = GeneratePIC.getValue(options) ? instanceofSecondaryPIC : instanceofSecondary;
                    args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("hub", hub);
                    args.add("object", object);
                    args.addVarargs("hints", KlassPointer.class, KlassPointerStamp.klassNonNull(), hints.hubs);
                    args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(JavaKind.Boolean), hints.isPositive);
                }
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                if (hintInfo.hintHitProbability >= 1.0 && hintInfo.exact == null) {
                    args.addConst("nullSeen", hintInfo.profile.getNullSeen() != TriState.FALSE);
                }
                return args;
            } else if (replacer.instanceOf instanceof InstanceOfDynamicNode) {
                InstanceOfDynamicNode instanceOf = (InstanceOfDynamicNode) replacer.instanceOf;
                ValueNode object = instanceOf.getObject();

                Arguments args = new Arguments(instanceofDynamic, instanceOf.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("hub", instanceOf.getMirrorOrHub());
                args.add("object", object);
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.addConst("allowNull", instanceOf.allowsNull());
                return args;
            } else if (replacer.instanceOf instanceof ClassIsAssignableFromNode) {
                ClassIsAssignableFromNode isAssignable = (ClassIsAssignableFromNode) replacer.instanceOf;
                Arguments args = new Arguments(isAssignableFrom, isAssignable.graph().getGuardsStage(), tool.getLoweringStage());
                assert ((ObjectStamp) isAssignable.getThisClass().stamp()).nonNull();
                args.add("thisClassNonNull", isAssignable.getThisClass());
                args.add("otherClass", isAssignable.getOtherClass());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                return args;
            } else {
                throw GraalError.shouldNotReachHere();
            }
        }
    }
}
