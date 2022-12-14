/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode.EnterAction;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CEntryPointPrologueBailoutNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode.UtilityAction;
import com.oracle.svm.core.graal.nodes.CurrentVMThreadFixedNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class CEntryPointSupport implements GraalFeature {
    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean hosted) {
        registerEntryPointActionsPlugins(invocationPlugins);
        registerEntryPointContextPlugins(invocationPlugins);
    }

    private static void registerEntryPointActionsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CEntryPointActions.class);
        r.register1("enterCreateIsolate", CEntryPointCreateIsolateParameters.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode parameters) {
                b.addPush(JavaKind.Int, new CEntryPointEnterNode(EnterAction.CreateIsolate, parameters));
                return true;
            }
        });
        r.register1("enterAttachThread", Isolate.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Int, new CEntryPointEnterNode(EnterAction.AttachThread, isolate));
                return true;
            }
        });
        r.register1("enter", IsolateThread.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode thread) {
                b.addPush(JavaKind.Int, new CEntryPointEnterNode(EnterAction.Enter, thread));
                return true;
            }
        });
        r.register1("enterIsolate", Isolate.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Int, new CEntryPointEnterNode(EnterAction.EnterIsolate, isolate));
                return true;
            }
        });
        InvocationPlugin bailoutPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new CEntryPointPrologueBailoutNode(value));
                return true;
            }
        };
        r.register1("bailoutInPrologue", WordBase.class, bailoutPlugin);
        r.register1("bailoutInPrologue", long.class, bailoutPlugin);
        r.register1("bailoutInPrologue", double.class, bailoutPlugin);
        r.register1("bailoutInPrologue", boolean.class, bailoutPlugin);
        r.register0("bailoutInPrologue", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new CEntryPointPrologueBailoutNode(null));
                return true;
            }
        });
        r.register0("leave", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.Leave));
                return true;
            }
        });
        r.register0("leaveDetachThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.DetachThread));
                return true;
            }
        });
        r.register0("leaveTearDownIsolate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Int, new CEntryPointLeaveNode(LeaveAction.TearDownIsolate));
                return true;
            }
        });
    }

    private static void registerEntryPointContextPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CEntryPointContext.class);
        r.register0("getCurrentIsolateThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (SubstrateOptions.MultiThreaded.getValue()) {
                    b.addPush(JavaKind.Object, new CurrentVMThreadFixedNode());
                } else {
                    b.addPush(JavaKind.Object, ConstantNode.forIntegerKind(FrameAccess.getWordKind(), 0));
                }
                return true;
            }
        });
        r.register0("getCurrentIsolate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, ConstantNode.forIntegerKind(FrameAccess.getWordKind(), 0));
                return true;
            }
        });
        r.register1("isCurrentThreadAttachedTo", Isolate.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode isolate) {
                b.addPush(JavaKind.Boolean, new CEntryPointUtilityNode(UtilityAction.IsAttached, isolate));
                return true;
            }
        });
    }
}
