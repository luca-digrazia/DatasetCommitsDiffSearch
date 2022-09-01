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
package com.oracle.graal.graph;


/**
 * This error represents a failed verification of a node . It must only be used for conditions that should never occur during normal operation.
 */
public class VerificationError extends GraalInternalError {

    /**
     * 
     */
    private static final long serialVersionUID = 8459607567446819822L;

    /**
     * This constructor creates a {@link VerificationError} with a message assembled via {@link String#format(String, Object...)}.
     * It always uses the ENGLISH locale in order to always generate the same output.
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be expanded into a [x, x, ...] representation.
     */
    public VerificationError(String msg, Object... args) {
        super(msg, args);
    }
}
