/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.expression;

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * SL node that performs the "+" operation, which performs addition on arbitrary precision numbers,
 * as well as String concatenation if one of the operands is a String.
 * <p>
 * Type specialization on the input values is essential for the performance. This is achieved via
 * node rewriting: specialized subclasses handle just a single type, so that the generic node that
 * can handle all types is used only in cases where different types were encountered. The subclasses
 * are automatically generated by the Truffle DSL. In addition, a {@link SLAddNodeGen factory class}
 * is generated that provides, e.g., {@link SLAddNodeGen#create node creation}.
 */
@NodeInfo(shortName = "+")
public abstract class SLAddNode extends SLBinaryNode {

    public SLAddNode(SourceSection src) {
        super(src);
    }

    /**
     * Specialization for primitive {@code long} values. This is the fast path of the
     * arbitrary-precision arithmetic. We need to check for overflows of the addition, and switch to
     * the {@link #add(BigInteger, BigInteger) slow path}. Therefore, we use an
     * {@link ExactMath#addExact(long, long) addition method that throws an exception on overflow}.
     * The {@code rewriteOn} attribute on the {@link Specialization} annotation automatically
     * triggers the node rewriting on the exception.
     * <p>
     * In compiled code, {@link ExactMath#addExact(long, long) addExact} is compiled to efficient
     * machine code that uses the processor's overflow flag. Therefore, this method is compiled to
     * only two machine code instructions on the fast path.
     * <p>
     * This specialization is automatically selected by the Truffle DSL if both the left and right
     * operand are {@code long} values.
     */
    @Specialization(rewriteOn = ArithmeticException.class)
    protected long add(long left, long right) {
        return ExactMath.addExact(left, right);
    }

    /**
     * This is the slow path of the arbitrary-precision arithmetic. The {@link BigInteger} type of
     * Java is doing everything we need.
     * <p>
     * This specialization is automatically selected by the Truffle DSL if both the left and right
     * operand are {@link BigInteger} values. Because the type system defines an
     * {@link ImplicitCast implicit conversion} from {@code long} to {@link BigInteger} in
     * {@link SLTypes#castBigInteger(long)}, this specialization is also taken if the left or the
     * right operand is a {@code long} value. Because the {@link #add(long, long) long}
     * specialization} has the {@code rewriteOn} attribute, this specialization is also taken if
     * both input values are {@code long} values but the primitive addition overflows.
     */
    @Specialization
    @TruffleBoundary
    protected BigInteger add(BigInteger left, BigInteger right) {
        return left.add(right);
    }

    /**
     * Specialization for String concatenation. The SL specification says that String concatenation
     * works if either the left or the right operand is a String. The non-string operand is
     * converted then automatically converted to a String.
     * <p>
     * To implement these semantics, we tell the Truffle DSL to use a custom guard. The guard
     * function is defined in {@link #isString this class}, but could also be in any superclass.
     */
    @Specialization(guards = "isString")
    @TruffleBoundary
    protected String add(Object left, Object right) {
        return left.toString() + right.toString();
    }

    /**
     * Guard for String concatenation: returns true if either the left or the right operand is a
     * {@link String}.
     */
    protected boolean isString(Object a, Object b) {
        return a instanceof String || b instanceof String;
    }
}
