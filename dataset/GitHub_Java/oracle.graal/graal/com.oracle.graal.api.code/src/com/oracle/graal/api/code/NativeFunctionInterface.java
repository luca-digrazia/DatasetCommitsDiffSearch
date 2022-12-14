/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

/**
 * Interface to resolve pointers to native foreign functions.
 * 
 */
public interface NativeFunctionInterface {

    /**
     * Resolves and returns a library handle.
     * 
     * @param libPath the absolute path to the library
     * @return the resolved library handle
     */
    NativeLibraryHandle getLibraryHandle(String libPath);

    /**
     * Resolves the {@Code NativeFunctionHandle} of a native function that can be called. Use
     * a {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param libraryHandle the handle to a resolved library
     * @param functionName the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(NativeLibraryHandle libraryHandle, String functionName, Class returnType, Class[] argumentTypes);

    /**
     * Resolves the {@Code NativeFunctionHandle} of a native function that can be called. Use
     * a {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param functionPointer the function pointer
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(NativeFunctionPointer functionPointer, Class returnType, Class[] argumentTypes);

    /**
     * Resolves the function pointer {@Code NativeFunctionPointer} of a native function. A
     * {@code NativeFunctionPointer} wraps the raw pointer value.
     * 
     * @param libraryHandles the handles to a various resolved library, the first library containing
     *            the method wins
     * @param functionName the name of the function to be resolved
     * @return the function handle of the native foreign function
     */
    NativeFunctionPointer getFunctionPointer(NativeLibraryHandle[] libraryHandles, String functionName);

    /**
     * Resolves the {@Code NativeFunctionHandle} of a native function that can be called. Use
     * a {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param libraryHandles the handles to a various resolved library, the first library containing
     *            the method wins
     * @param functionName the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(NativeLibraryHandle[] libraryHandles, String functionName, Class returnType, Class[] argumentTypes);

    /**
     * Resolves the {@Code NativeFunctionHandle} of a native function that can be called. Use
     * a {@code NativeFunctionHandle} to invoke the native target function.
     * 
     * @param functionName the name of the function to be resolved
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the function handle of the native foreign function
     */
    NativeFunctionHandle getFunctionHandle(String functionName, Class returnType, Class[] argumentTypes);

    /**
     * Creates {@Code NativeFunctionPointer} from raw value. A {@code NativeFunctionPointer}
     * wraps the raw pointer value.
     * 
     * @param rawValue Raw pointer value
     * @return {@Code NativeFunctionPointer} of the raw pointer
     */
    NativeFunctionPointer getNativeFunctionPointerFromRawValue(long rawValue);
}
