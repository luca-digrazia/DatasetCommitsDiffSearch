/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.ri;

/**
 * Represents a compiled instance of a method. It may have been invalidated or removed in the meantime.
 */
public interface RiCompiledMethod {

    public abstract class MethodInvalidatedException extends RuntimeException {

        private static final long serialVersionUID = -3540232440794244844L;
    }

    /**
     * Returns the method to which the compiled code belongs.
     * @return the method to which the compiled code belongs.
     */
    RiResolvedMethod method();

    /**
     * @return true if the code represented by this object is still valid, false otherwise (may happen due to deopt, etc.)
     */
    boolean isValid();

    Object execute(Object arg1, Object arg2, Object arg3);
}
