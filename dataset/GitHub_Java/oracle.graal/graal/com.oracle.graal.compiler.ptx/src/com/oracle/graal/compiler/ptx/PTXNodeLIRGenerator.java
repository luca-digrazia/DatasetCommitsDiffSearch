/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.ptx;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.*;

/**
 * This class implements the PTX specific portion of the LIR generator.
 */
public class PTXNodeLIRGenerator extends NodeLIRGenerator {

    // Number of the predicate register that can be used when needed.
    // This value will be recorded and incremented in the LIR instruction
    // that sets a predicate register. (e.g., CompareOp)
    private int nextPredRegNum;

    public static final ForeignCallDescriptor ARITHMETIC_FREM = new ForeignCallDescriptor("arithmeticFrem", float.class, float.class, float.class);
    public static final ForeignCallDescriptor ARITHMETIC_DREM = new ForeignCallDescriptor("arithmeticDrem", double.class, double.class, double.class);

    public static class PTXSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            throw GraalInternalError.unimplemented("PTXSpillMoveFactory.createMove()");
        }
    }

    public PTXNodeLIRGenerator(StructuredGraph graph, LIRGenerationResult lirGenRes, LIRGenerator lirGen) {
        super(graph, lirGenRes, lirGen);
    }

    public int getNextPredRegNumber() {
        return nextPredRegNum;
    }

    @Override
    public void emitPrologue(StructuredGraph graph) {
        // Need to emit .param directives based on incoming arguments and return value
        CallingConvention incomingArguments = gen.getCallingConvention();
        Object returnObject = incomingArguments.getReturn();
        AllocatableValue[] params = incomingArguments.getArguments();
        int argCount = incomingArguments.getArgumentCount();

        if (returnObject.equals(Value.ILLEGAL)) {
            params = incomingArguments.getArguments();
            append(new PTXParameterOp(params, false));
        } else {
            argCount = incomingArguments.getArgumentCount();
            params = new Variable[argCount + 1];
            for (int i = 0; i < argCount; i++) {
                params[i] = incomingArguments.getArgument(i);
            }
            params[argCount] = (Variable) returnObject;
            append(new PTXParameterOp(params, true));
        }

        for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
            int localIndex = param.index();
            Value paramValue = params[param.index()];
            int parameterIndex = localIndex;
            if (!Modifier.isStatic(graph.method().getModifiers())) {
                parameterIndex--;
            }
            Warp warpAnnotation = parameterIndex >= 0 ? MetaUtil.getParameterAnnotation(Warp.class, parameterIndex, graph.method()) : null;
            if (warpAnnotation != null) {
                setResult(param, getGen().emitWarpParam(paramValue.getKind().getStackKind(), warpAnnotation));
            } else {
                setResult(param, getGen().emitLoadParam(paramValue.getKind().getStackKind(), paramValue, null));
            }
        }
    }

    private PTXLIRGenerator getGen() {
        return (PTXLIRGenerator) gen;
    }

    @Override
    protected <T extends AbstractBlock<T>> void emitPrologue(ResolvedJavaMethod method, BytecodeParser<T> parser) {
        // Need to emit .param directives based on incoming arguments and return value
        CallingConvention incomingArguments = gen.getCallingConvention();
        Object returnObject = incomingArguments.getReturn();
        AllocatableValue[] params = incomingArguments.getArguments();
        int argCount = incomingArguments.getArgumentCount();

        if (returnObject.equals(Value.ILLEGAL)) {
            params = incomingArguments.getArguments();
            append(new PTXParameterOp(params, false));
        } else {
            argCount = incomingArguments.getArgumentCount();
            params = new Variable[argCount + 1];
            for (int i = 0; i < argCount; i++) {
                params[i] = incomingArguments.getArgument(i);
            }
            params[argCount] = (Variable) returnObject;
            append(new PTXParameterOp(params, true));
        }

        Signature sig = method.getSignature();
        boolean isStatic = Modifier.isStatic(method.getModifiers());

        for (int i = 0; i < sig.getParameterCount(!isStatic); i++) {
            Value paramValue = params[i];
            int parameterIndex = i;
            if (!isStatic) {
                parameterIndex--;
            }
            Warp warpAnnotation = parameterIndex >= 0 ? MetaUtil.getParameterAnnotation(Warp.class, parameterIndex, method) : null;
            if (warpAnnotation != null) {
                // setResult(param, emitWarpParam(paramValue.getKind().getStackKind(),
                // warpAnnotation));
                parser.setParameter(i, getGen().emitWarpParam(paramValue.getKind().getStackKind(), warpAnnotation));
            } else {
                // setResult(param, emitLoadParam(paramValue.getKind().getStackKind(), paramValue,
                // null));
                parser.setParameter(i, getGen().emitLoadParam(paramValue.getKind().getStackKind(), paramValue, null));
            }
        }
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now
        return false;
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitDirectCall()");
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitIndirectCall()");
    }

    @Override
    public void visitCompareAndSwap(LoweredCompareAndSwapNode node, Value address) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.visitCompareAndSwap()");
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.visitBreakpointNode()");
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        // LIRFrameState info = state(i);
        // append(new PTXSafepointOp(info, runtime().config, this));
        Debug.log("visitSafePointNode unimplemented");
    }

    @Override
    public void emitNullCheck(ValueNode v, DeoptimizingNode deopting) {
        assert v.getKind() == Kind.Object;
        append(new PTXMove.NullCheckOp(gen.load(operand(v)), gen.state(deopting)));
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.visitInfopointNode()");
    }

    @Override
    public void visitReturn(ReturnNode x) {
        AllocatableValue operand = Value.ILLEGAL;
        if (x.result() != null) {
            operand = gen.resultOperandFor(x.result().getKind());
            // Load the global memory address from return parameter
            Variable loadVar = getGen().emitLoadReturnAddress(operand.getKind(), operand, null);
            // Store result in global memory whose location is loadVar
            getGen().emitStoreReturnValue(operand.getKind(), loadVar, operand(x.result()), null);
        }
        getGen().emitReturnNoVal();
    }
}
