/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.core.test.MatchRuleTest;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer.MemoryConstOp;
import org.graalvm.compiler.lir.amd64.AMD64Unary;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;

public class AMD64MatchRuleTest extends MatchRuleTest {
    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    public static int test1Snippet(TestClass o, TestClass b, TestClass c) {
        if (o.x == 42) {
            return b.z;
        } else {
            return c.y;
        }
    }

    /**
     * Verifies, if the match rules in AMD64NodeMatchRules do work on the graphs by compiling and
     * checking if the expected LIR instruction show up.
     */
    @Test
    public void test1() {
        compile(getResolvedJavaMethod("test1Snippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof MemoryConstOp && ((MemoryConstOp) ins).getOpcode().toString().equals("CMP")) {
                assertFalse("MemoryConstOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("Memory compare must be in the LIR", found);
    }

    public static class TestClass {
        public int x;
        public int y;
        public int z;

        public TestClass(int x) {
            super();
            this.x = x;
        }
    }

    static volatile short shortValue;

    public static long testVolatileExtensionSnippet() {
        return shortValue;
    }

    @Test
    public void testVolatileExtension() {
        compile(getResolvedJavaMethod("testVolatileExtensionSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AMD64Unary.MemoryOp) {
                AMD64Unary.MemoryOp op = (AMD64Unary.MemoryOp) ins;
                ins.visitEachOutput((value, mode, flags) -> assertTrue(value.getPlatformKind().toString(), value.getPlatformKind().equals(AMD64Kind.QWORD)));
                assertFalse("MemoryOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("sign extending load must be in the LIR", found);
    }
}
