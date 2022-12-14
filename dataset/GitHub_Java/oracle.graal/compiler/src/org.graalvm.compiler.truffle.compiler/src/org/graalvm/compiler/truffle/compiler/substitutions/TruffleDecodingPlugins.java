/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.substitutions;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.ResolvedJavaSymbol;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides {@link InvocationPlugin}s for decoding of Truffle methods during partial evaluation.
 *
 * These plugins should be added exclusively to the PE decoder, and not the PE graph builder.
 */
public class TruffleDecodingPlugins {

    public static void registerInvocationPlugins(InvocationPlugins plugins, Providers providers) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        registerCompilerDirectivesPlugins(plugins, metaAccess);
    }

    private static void registerCompilerDirectivesPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
        final ResolvedJavaType compilerDirectivesType = TruffleCompilerRuntime.getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.CompilerDirectives");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(compilerDirectivesType));
        r.register0("inFirstTier", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (b.getGraph().getCancellable() instanceof TruffleCompilationTask) {
                    boolean isFirstTier = ((TruffleCompilationTask) b.getGraph().getCancellable()).isFirstTier();
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(isFirstTier));
                    return true;
                }
                return false;
            }
        });
    }
}
