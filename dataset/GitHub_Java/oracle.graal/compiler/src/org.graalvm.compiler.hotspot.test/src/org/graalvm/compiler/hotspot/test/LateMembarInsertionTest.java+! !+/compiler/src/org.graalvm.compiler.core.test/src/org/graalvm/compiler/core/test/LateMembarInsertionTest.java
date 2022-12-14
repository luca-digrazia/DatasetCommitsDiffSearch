/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.compiler.core.common.GraalOptions.StressTestEarlyReads;

public class LateMembarInsertionTest extends GraalCompilerTest {
    static class VolatileAccess {
        static volatile int field;
    }

    static class VolatileAccess2 {
        static volatile int field;
    }

    static class RegularAccess {
        static int field;
    }

    public static int volatileFieldLoadFieldLoad() {
        int v1 = VolatileAccess.field;
        int v2 = RegularAccess.field;
        return v1 + v2;
    }

    @Test
    public void test01() {
        List<TypePair> accesses = compile("volatileFieldLoadFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), VolatileAccess.class);
        Assert.assertEquals(accesses.get(1).getType(), RegularAccess.class);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int volatileFieldLoadVolatileFieldLoad() {
        int v1 = VolatileAccess.field;
        int v2 = VolatileAccess2.field;
        return v1 + v2;
    }

    @Test
    public void test02() {
        List<TypePair> accesses = compile("volatileFieldLoadVolatileFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), VolatileAccess.class);
        Assert.assertEquals(accesses.get(1).getType(), VolatileAccess2.class);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int volatileFieldLoadVolatileFieldStore(int v2) {
        int v1 = VolatileAccess.field;
        VolatileAccess2.field = v2;
        return v1;
    }

    @Test
    public void test03() {
        List<TypePair> accesses = compile("volatileFieldLoadVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), VolatileAccess.class);
        Assert.assertEquals(accesses.get(1).getType(), VolatileAccess2.class);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static int volatileFieldStoreVolatileFieldLoad(int v2) {
        VolatileAccess.field = v2;
        return VolatileAccess2.field;
    }

    @Test
    public void test04() {
        List<TypePair> accesses = compile("volatileFieldStoreVolatileFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), VolatileAccess.class);
        Assert.assertEquals(accesses.get(1).getType(), VolatileAccess2.class);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int fieldLoadVolatileFieldStore(int v2) {
        int v1 = RegularAccess.field;
        VolatileAccess2.field = v2;
        return v1;
    }

    @Test
    public void test05() {
        List<TypePair> accesses = compile("fieldLoadVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), RegularAccess.class);
        Assert.assertEquals(accesses.get(1).getType(), VolatileAccess2.class);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static void volatileFieldStoreVolatileFieldStore(int v1, int v2) {
        VolatileAccess.field = v1;
        VolatileAccess2.field = v2;
    }

    @Test
    public void test06() {
        List<TypePair> accesses = compile("volatileFieldStoreVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), VolatileAccess.class);
        Assert.assertEquals(accesses.get(1).getType(), VolatileAccess2.class);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    private static OptionValues stressTestEarlyReads() {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(StressTestEarlyReads, true);
        return new OptionValues(getInitialOptions(), overrides);
    }

    static class TypePair {
        private boolean isRead;
        private Class<?> type;

        TypePair(boolean isRead, Class<?> type) {
            this.isRead = isRead;
            this.type = type;
        }

        public boolean isRead() {
            return isRead;
        }

        public boolean isWrite() {
            return !isRead;
        }

        public Class<?> getType() {
            return type;
        }
    }

    private List<TypePair> compile(String test, OptionValues options) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(test), options);
        return getAccesses(graph);
    }

    private static List<TypePair> getAccesses(StructuredGraph graph) {
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        ControlFlowGraph cfg = schedule.getCFG();
        Block[] blocks = cfg.getBlocks();

        return Arrays.stream(blocks).flatMap(b -> schedule.nodesFor(b).stream()).filter(n -> n instanceof MemoryAccess).map(
                        n -> new TypePair(n instanceof ReadNode, classForAccess((FixedAccessNode) n))).collect(Collectors.toList());
    }

    private List<TypePair> compile(String test) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(test));
        return getAccesses(graph);
    }

    private static Class<?> classForAccess(FixedAccessNode n) {
        AddressNode address = n.getAddress();
        ValueNode base = address.getBase();
        Stamp stamp = base.stamp(NodeView.DEFAULT);
        JVMCIBackend providers = JVMCI.getRuntime().getHostJVMCIBackend();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType javaType = stamp.javaType(metaAccess);
        Class<?> mirror = ((HotSpotJVMCIRuntime) JVMCI.getRuntime()).getMirror(javaType);
        if (mirror == Class.class && base instanceof ConstantNode) {
            ConstantReflectionProvider constantReflection = providers.getConstantReflection();
            javaType = constantReflection.asJavaType(base.asConstant());
            mirror = ((HotSpotJVMCIRuntime) JVMCI.getRuntime()).getMirror(javaType);
        }
        return mirror;
    }

}
