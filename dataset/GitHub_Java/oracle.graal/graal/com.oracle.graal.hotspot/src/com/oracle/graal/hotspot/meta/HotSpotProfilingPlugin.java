/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.hotspot.nodes.profiling.ProfileBranchNode;
import com.oracle.graal.hotspot.nodes.profiling.ProfileInvokeNode;
import com.oracle.graal.hotspot.nodes.profiling.ProfileNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.ProfilingPlugin;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class HotSpotProfilingPlugin implements ProfilingPlugin {
    public static class Options {
        @Option(help = "Emit profiling of invokes", type = OptionType.Expert)//
        public static final OptionValue<Boolean> ProfileInvokes = new OptionValue<>(true);
        @Option(help = "Emit profiling of backedges", type = OptionType.Expert)//
        public static final OptionValue<Boolean> ProfileBackedges = new OptionValue<>(true);
    }

    public abstract int invokeNotifyFreqLog();

    public abstract int invokeInlineeNotifyFreqLog();

    public abstract int invokeProfilePobabilityLog();

    public abstract int backedgeNotifyFreqLog();

    public abstract int backedgeProfilePobabilityLog();

    @Override
    public boolean shouldProfile(GraphBuilderContext builder, ResolvedJavaMethod method) {
        return !builder.parsingIntrinsic();
    }

    @Override
    public void profileInvoke(GraphBuilderContext builder, ResolvedJavaMethod method, FrameState frameState) {
        assert shouldProfile(builder, method);
        if (Options.ProfileInvokes.getValue() && !method.isClassInitializer()) {
            ProfileNode p = builder.append(new ProfileInvokeNode(method, invokeNotifyFreqLog(), invokeProfilePobabilityLog()));
            p.setStateBefore(frameState);
        }
    }

    @Override
    public void profileGoto(GraphBuilderContext builder, ResolvedJavaMethod method, int bci, int targetBci, FrameState frameState) {
        assert shouldProfile(builder, method);
        if (Options.ProfileBackedges.getValue() && targetBci <= bci) {
            ProfileNode p = builder.append(new ProfileBranchNode(method, backedgeNotifyFreqLog(), backedgeProfilePobabilityLog(), bci, targetBci));
            p.setStateBefore(frameState);
        }
    }

    @Override
    public void profileIf(GraphBuilderContext builder, ResolvedJavaMethod method, int bci, LogicNode condition, int trueBranchBci, int falseBranchBci, FrameState frameState) {
        assert shouldProfile(builder, method);
        if (Options.ProfileBackedges.getValue() && (falseBranchBci <= bci || trueBranchBci <= bci)) {
            boolean negate = false;
            int targetBci = trueBranchBci;
            if (falseBranchBci <= bci) {
                assert trueBranchBci > bci;
                negate = true;
                targetBci = falseBranchBci;
            } else {
                assert trueBranchBci <= bci && falseBranchBci > bci;
            }
            ValueNode trueValue = builder.append(ConstantNode.forBoolean(!negate));
            ValueNode falseValue = builder.append(ConstantNode.forBoolean(negate));
            ConditionalNode branchCondition = builder.append(new ConditionalNode(condition, trueValue, falseValue));
            ProfileNode p = builder.append(new ProfileBranchNode(method, backedgeNotifyFreqLog(), backedgeProfilePobabilityLog(), branchCondition, bci, targetBci));
            p.setStateBefore(frameState);
        }
    }
}
