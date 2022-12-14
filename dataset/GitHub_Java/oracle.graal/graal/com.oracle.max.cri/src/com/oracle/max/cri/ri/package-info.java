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
/**
 * The runtime-provided part of the bi-directional interface between the compiler and the runtime system of a virtual machine for the
 * instruction set defined in {@link com.oracle.max.graal.compiler.graphbuilder.Bytecodes}.
 * <p>
 * Unlike the {@link com.oracle.max.cri.ci compiler-provided interface}, the runtime-provided interface is specified largely
 * using interfaces, that must be implemented by classes provided by a specific runtime implementation.
 * <p>
 * {@link com.oracle.max.cri.ri.RiRuntime} encapsulates the main functionality of the runtime for the compiler.
 * <p>
 * Types (i.e., primitives, classes and interfaces}, fields and methods are represented by {@link com.oracle.max.cri.ri.RiType},
 * {@link com.oracle.max.cri.ri.RiField} and {@link com.oracle.max.cri.ri.RiMethod}, respectively, with additional support from
 * {@link com.oracle.max.cri.ri.RiSignature} and {@link com.oracle.max.cri.ri.RiExceptionHandler}. Access to the runtime constant pool
 * is through {@link com.oracle.max.cri.ri.RiConstantPool}.
 */
package com.oracle.max.cri.ri;
