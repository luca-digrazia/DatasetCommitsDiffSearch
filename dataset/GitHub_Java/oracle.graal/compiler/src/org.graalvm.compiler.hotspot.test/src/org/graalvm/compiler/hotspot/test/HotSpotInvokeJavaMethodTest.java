/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotInvokeJavaMethodTest extends HotSpotGraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
                ForeignCallNode node = new ForeignCallNode(HotSpotHostForeignCallsProvider.TestForeignCalls.BYTE_RETURNS_BYTE, arg);
                b.addPush(JavaKind.Byte, node);
                return true;
            }
        }, HotSpotInvokeJavaMethodTest.class, "byteReturnsByte", byte.class);
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
                ForeignCallNode node = new ForeignCallNode(HotSpotHostForeignCallsProvider.TestForeignCalls.INT_RETURNS_INT, arg);
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, HotSpotInvokeJavaMethodTest.class, "intReturnsInt", int.class);
        super.registerInvocationPlugins(invocationPlugins);
    }

    static byte byteReturnsByte(byte arg) {
        return arg;
    }

    public static int byteReturnsByteSnippet(byte arg) {
        return byteReturnsByte(arg);
    }

    @Test
    public void testByteReturnsByte() {
        test("byteReturnsByteSnippet", (byte) 255);
    }

    static int intReturnsInt(int arg) {
        return arg;
    }

    public static int intReturnsIntSnippet(int arg) {
        return intReturnsInt(arg);
    }

    @Test
    public void testIntReturnsInt() {
        test("intReturnsIntSnippet", 255);
    }
}
