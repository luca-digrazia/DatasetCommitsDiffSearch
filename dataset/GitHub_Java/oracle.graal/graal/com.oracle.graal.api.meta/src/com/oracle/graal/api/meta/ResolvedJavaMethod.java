/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Represents resolved Java methods. Methods, like fields and types, are resolved through
 * {@link ConstantPool constant pools}.
 */
public interface ResolvedJavaMethod extends JavaMethod {

    /**
     * Gets the bytecode of the method, if the method has code.
     * The returned byte array does not contain breakpoints or non-Java bytecodes.
     * @return the bytecode of the method or {@code null} if none is available
     */
    byte[] code();

    /**
     * Gets the size of the bytecode of the method, if the method has code.
     * @return the size of the bytecode in bytes, or 0 if no bytecode is available
     */
    int codeSize();

    /**
     * Gets the size of the compiled machine code.
     * @return the size of the compiled machine code in bytes, or 0 if no compiled code exists.
     */
    int compiledCodeSize();

    /**
     * Gets an estimate how complex it is to compile this method.
     * @return A value >= 0, where higher means more complex.
     */
    int compilationComplexity();

    /**
     * Gets the symbol used to link this method if it is native, otherwise {@code null}.
     */
    String jniSymbol();

    /**
     * Gets the type in which this method is declared.
     * @return the type in which this method is declared
     */
    ResolvedJavaType holder();

    /**
     * Gets the maximum number of locals used in this method's bytecode.
     * @return the maximum number of locals
     */
    int maxLocals();

    /**
     * Gets the maximum number of stack slots used in this method's bytecode.
     * @return the maximum number of stack slots
     */
    int maxStackSize();

    /**
     * Checks whether this method has balanced monitor operations.
     * @return {@code true} if the method has balanced monitor operations
     */
    boolean hasBalancedMonitors();

    /**
     * Gets the access flags for this method. Only the flags specified in the JVM specification
     * will be included in the returned mask. The utility methods in the {@link Modifier} class
     * should be used to query the returned mask for the presence/absence of individual flags.
     * @return the mask of JVM defined method access flags defined for this method
     */
    int accessFlags();

    /**
     * Checks whether this method is a leaf method.
     * @return {@code true} if the method is a leaf method (that is, is final or private)
     */
    boolean isLeafMethod();

    /**
     * Checks whether this method is a class initializer.
     * @return {@code true} if the method is a class initializer
     */
    boolean isClassInitializer();

    /**
     * Checks whether this method is a constructor.
     * @return {@code true} if the method is a constructor
     */
    boolean isConstructor();

    /**
     * Checks whether this method can be statically bound (that is, it is final or private or static).
     * @return {@code true} if this method can be statically bound
     */
    boolean canBeStaticallyBound();

    /**
     * Gets the list of exception handlers for this method.
     * @return the list of exception handlers
     */
    ExceptionHandler[] exceptionHandlers();

    /**
     * Gets a stack trace element for this method and a given bytecode index.
     */
    StackTraceElement toStackTraceElement(int bci);

    /**
     * Provides an estimate of how often this method has been executed.
     * @return The number of invocations, or -1 if this information isn't available.
     */
    int invocationCount();

    /**
     * Returns an object that provides access to the method's profiling information.
     * @return The profiling information recorded for this method.
     */
    ProfilingInfo profilingInfo();

    /**
     * Returns a map that the compiler can use to store objects that should survive the current compilation.
     */
    Map<Object, Object> compilerStorage();

    /**
     * Returns a pointer to the method's constant pool.
     * @return the constant pool
     */
    ConstantPool getConstantPool();

    /**
     * Returns this method's annotation of a specified type.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @return the annotation of type {@code annotationClass} for this method if present, else null
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * Returns an array of arrays that represent the annotations on the formal
     * parameters, in declaration order, of this method.
     *
     * @see Method#getParameterAnnotations()
     */
    Annotation[][] getParameterAnnotations();

    /**
     * Returns an array of {@link Type} objects that represent the formal
     * parameter types, in declaration order, of this method.
     *
     * @see Method#getGenericParameterTypes()
     */
    Type[] getGenericParameterTypes();

    /**
     * @return {@code true} if this method can be inlined
     */
    boolean canBeInlined();
}
