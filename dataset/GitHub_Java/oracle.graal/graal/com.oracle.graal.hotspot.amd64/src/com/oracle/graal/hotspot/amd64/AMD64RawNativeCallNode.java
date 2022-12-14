/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

public class AMD64RawNativeCallNode extends FixedWithNextNode implements LIRGenLowerable {

    private final Constant functionPointer;
    @Input private final NodeInputList<ValueNode> args;

    public AMD64RawNativeCallNode(Kind returnType, Constant functionPointer, ValueNode[] args) {
        super(StampFactory.forKind(returnType));
        this.functionPointer = functionPointer;
        this.args = new NodeInputList<>(this, args);
    }

    @Override
    public void generate(LIRGenerator generator) {
        AMD64LIRGenerator gen = (AMD64LIRGenerator) generator;
        Value[] parameter = new Value[args.count()];
        JavaType[] parameterTypes = new JavaType[args.count()];
        for (int i = 0; i < args.count(); i++) {
            parameter[i] = generator.operand(args.get(i));
            parameterTypes[i] = args.get(i).stamp().javaType(gen.getMetaAccess());
        }
        ResolvedJavaType returnType = stamp().javaType(gen.getMetaAccess());
        CallingConvention cc = generator.getCodeCache().getRegisterConfig().getCallingConvention(Type.NativeCall, returnType, parameterTypes, generator.target(), false);
        gen.emitCCall(functionPointer.asLong(), cc, parameter, countFloatingTypeArguments(args));
        if (this.getKind() != Kind.Void) {
            generator.setResult(this, gen.emitMove(cc.getReturn()));
        }
    }

    private static int countFloatingTypeArguments(NodeInputList<ValueNode> args) {
        int count = 0;
        for (ValueNode n : args) {
            if (n.getKind() == Kind.Double || n.getKind() == Kind.Float) {
                count++;
            }
        }
        if (count > 8) {
            return 8;
        }
        return count;
    }

}
