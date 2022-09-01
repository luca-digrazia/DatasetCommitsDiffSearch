/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

public class SourceLocation {

    private final int lineNumber;
    private final JDWPContext context;
    private final String slashName;

    public SourceLocation(String slashName, int lineNumber, JDWPContext context) {
        this.lineNumber = lineNumber;
        this.context = context;
        this.slashName = slashName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Source getSource() throws NoSuchSourceLineException {
        return new SourceLocator(context).lookupSource(slashName, lineNumber);
    }

    @Override
    public String toString() {
        return "Location: " + slashName + ":" + lineNumber;
    }
}
