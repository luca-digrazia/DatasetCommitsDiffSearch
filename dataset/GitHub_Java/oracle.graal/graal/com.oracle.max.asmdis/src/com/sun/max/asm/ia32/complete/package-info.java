/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * An assembly system for (almost all of) the IA32 instruction set.
 * Our own restriction here: the default address and operand size is ALWAYS 32 bits.
 * 
 * We have the capability to include instructions with 16-bit addressing,
 * but by default we don't.
 * 
 * Once {@link IA32RawAssembler} and {@link IA32LabelAssembler} have been generated,
 * this package can be used separate from the framework
 * by importing the following assembler packages only:
 * 
 *     com.sun.max.asm
 *     com.sun.max.asm.x86
 *     com.sun.max.asm.ia32
 */
package com.sun.max.asm.ia32.complete;

