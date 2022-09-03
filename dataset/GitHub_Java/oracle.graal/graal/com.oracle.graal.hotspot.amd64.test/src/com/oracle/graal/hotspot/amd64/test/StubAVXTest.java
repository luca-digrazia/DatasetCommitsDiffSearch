/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.amd64.test;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.api.replacements.Snippet;
import com.oracle.graal.asm.amd64.AMD64Address;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.type.DataPointerConstant;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.stubs.SnippetStub;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.amd64.AMD64LIRInstruction;
import com.oracle.graal.lir.asm.ArrayDataPointerConstant;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.jtt.LIRTest;
import com.oracle.graal.lir.jtt.LIRTestSpecification;
import com.oracle.graal.nodes.extended.ForeignCallNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class StubAVXTest extends LIRTest {

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
        Assume.assumeTrue("skipping AVX test", ((AMD64) getTarget().arch).getFeatures().contains(CPUFeature.AVX));
    }

    private static final DataPointerConstant avxConstant = new ArrayDataPointerConstant(new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f}, 32);

    private static class LoadAVXConstant extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LoadAVXConstant> TYPE = LIRInstructionClass.create(LoadAVXConstant.class);

        @Def({REG}) AllocatableValue result;

        LoadAVXConstant(AllocatableValue result) {
            super(TYPE);
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.vmovdqu(ValueUtil.asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(avxConstant));
        }
    }

    private static final LIRTestSpecification loadAVXConstant = new LIRTestSpecification() {

        @Override
        public void generate(LIRGeneratorTool gen) {
            Variable ret = gen.newVariable(LIRKind.value(AMD64Kind.V256_SINGLE));
            gen.append(new LoadAVXConstant(ret));
            setResult(ret);
        }
    };

    @LIRIntrinsic
    public static Object loadAVXConstant(@SuppressWarnings("unused") LIRTestSpecification spec) {
        return null;
    }

    private static class CompareAVXRegister extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompareAVXRegister> TYPE = LIRInstructionClass.create(CompareAVXRegister.class);

        @Def({REG}) AllocatableValue result;
        @Use({REG}) AllocatableValue left;
        @Use({REG}) AllocatableValue right;
        @Temp({REG}) AllocatableValue temp;

        CompareAVXRegister(AllocatableValue result, AllocatableValue left, AllocatableValue right, AllocatableValue temp) {
            super(TYPE);
            this.result = result;
            this.left = left;
            this.right = right;
            this.temp = temp;
        }

        private static int getRXB(Register reg, Register rm) {
            int rxb = (reg.encoding & 0x08) >> 1;
            rxb |= (rm.encoding & 0x08) >> 3;
            return rxb;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register res = ValueUtil.asRegister(result);
            Register x = ValueUtil.asRegister(left);
            Register y = ValueUtil.asRegister(right);
            Register tmp = ValueUtil.asRegister(temp);

            // VEX.NDS.256.0F.WIG C2 /r ib(0)
            // VCMPPS tmp, x, y, EQ
            masm.emitByte(0xC4);                                   // VEX 3-byte
            masm.emitByte((~getRXB(tmp, y) & 0x7) << 5 | 0x01);    // RXB m-mmmmm (0F)
            masm.emitByte(((~x.encoding & 0x0f) << 3) | 0b1_00);   // W(0) vvvv L(1) pp(0)
            masm.emitByte(0xC2);
            masm.emitByte(0xC0 | ((tmp.encoding & 0x07) << 3) | (y.encoding & 0x07));
            masm.emitByte(0);

            // VEX.256.0F.WIG 50 /r
            // VMOVMSKPS res, tmp
            masm.emitByte(0xC4);                                   // VEX 3-byte
            masm.emitByte((~getRXB(res, tmp) & 0x7) << 5 | 0x01);  // RXB m-mmmmm (0F)
            masm.emitByte(0b0_1111_1_00);                          // W(0) vvvv L(1) pp(0)
            masm.emitByte(0x50);
            masm.emitByte(0xC0 | ((res.encoding & 0x07) << 3) | (tmp.encoding & 0x07));
        }
    }

    private static final LIRTestSpecification compareAVXRegister = new LIRTestSpecification() {

        @Override
        public void generate(LIRGeneratorTool gen, Value arg0, Value arg1) {
            Variable ret = gen.newVariable(LIRKind.value(AMD64Kind.DWORD));
            gen.append(new CompareAVXRegister(ret, gen.asAllocatable(arg0), gen.asAllocatable(arg1), gen.newVariable(LIRKind.value(AMD64Kind.V256_QWORD))));
            setResult(ret);
        }
    };

    private static class TestStub extends SnippetStub {

        TestStub(HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
            super("testStub", providers, linkage);
        }

        @Snippet
        static void testStub() {
        }
    }

    public static final ForeignCallDescriptor TEST_STUB = new ForeignCallDescriptor("test_stub", void.class);

    @LIRIntrinsic
    public static int compareAVXRegister(@SuppressWarnings("unused") LIRTestSpecification spec, Object left, Object right) {
        return left == right ? 0xff : 0;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, TestStub.class);
        r.register0("testStub", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
                b.add(new ForeignCallNode(getProviders().getForeignCalls(), TEST_STUB));
                return true;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }

    public static int testStub() {
        Object preStub = loadAVXConstant(loadAVXConstant);

        // do something to potentially destroy the value
        TestStub.testStub();

        Object postStub = loadAVXConstant(loadAVXConstant);
        return compareAVXRegister(compareAVXRegister, preStub, postStub);
    }

    @Test
    public void test() {
        HotSpotProviders providers = (HotSpotProviders) getProviders();
        HotSpotForeignCallsProviderImpl foreignCalls = (HotSpotForeignCallsProviderImpl) providers.getForeignCalls();
        HotSpotForeignCallLinkage linkage = foreignCalls.registerStubCall(TEST_STUB, true, HotSpotForeignCallLinkage.Transition.LEAF_NOFP);
        linkage.setCompiledStub(new TestStub(providers, linkage));
        runTest("testStub");
    }
}
