/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.amd64.test;

import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.amd64.AMD64BinaryConsumer.MemoryConstOp;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.jtt.LIRTest;
import com.oracle.graal.lir.phases.LIRPhase;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;

public class MatchRuleTest extends LIRTest {
    private static LIR lir;

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
        getLIRSuites().getPreAllocationOptimizationStage().appendPhase(new CheckPhase());
        compile(getResolvedJavaMethod("test1Snippet"), null);
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder().get(0))) {
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

    public static class CheckPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, List<? extends AbstractBlockBase<?>> codeEmittingOrder, List<? extends AbstractBlockBase<?>> linearScanOrder,
                        PreAllocationOptimizationContext context) {
            lir = lirGenRes.getLIR();
        }
    }
}
