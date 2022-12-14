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
package com.oracle.graal.api.code;

import com.oracle.graal.api.meta.*;

/**
 * Represents a compiled instance of a method. It may have been invalidated or removed in the
 * meantime.
 */
public interface InstalledCode {

    /**
     * Exception thrown by the runtime in case an invalidated machine code is called.
     */
    public abstract class MethodInvalidatedException extends RuntimeException {

        private static final long serialVersionUID = -3540232440794244844L;
    }

    /**
     * Returns the method to which the compiled code belongs.
     */
    ResolvedJavaMethod getMethod();

    /**
     * @return true if the code represented by this object is still valid, false otherwise (may
     *         happen due to deopt, etc.)
     */
    boolean isValid();

    /**
     * Executes the installed code with three object arguments.
     * 
     * @param arg1 the first argument
     * @param arg2 the second argument
     * @param arg3 the third argument
     * @return the value returned by the executed code
     */
    Object execute(Object arg1, Object arg2, Object arg3);

    /**
     * Executes the installed code with a variable number of arguments.
     * 
     * @param args the array of object arguments
     * @return the value returned by the executed code
     */
    Object executeVarargs(Object... args);
}
