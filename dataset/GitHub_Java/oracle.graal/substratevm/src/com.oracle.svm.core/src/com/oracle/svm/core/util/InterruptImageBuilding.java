/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.util;

import java.util.Optional;

/**
 * Used to report valid interruption of compilation.
 */
public class InterruptImageBuilding extends RuntimeException {
    static final long serialVersionUID = 754312906378380L;
    private final boolean hasMessage;

    /**
     * Print an error message upon exit.
     *
     * @param message reason for interruption.
     */
    public InterruptImageBuilding(String message) {
        super(message);
        this.hasMessage = true;
    }

    /**
     * Print nothing upon exit.
     */
    public InterruptImageBuilding() {
        this((Throwable) null);
    }

    public Optional<String> getReason() {
        return hasMessage ? Optional.of(getMessage()) : Optional.empty();
    }
}
