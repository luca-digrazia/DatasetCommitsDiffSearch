/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

import com.oracle.svm.core.c.CHeader.Header;
import com.oracle.svm.core.c.function.GraalIsolateHeader;

public class PolyglotAPIHeader implements Header {
    @Override
    public String name() {
        return "polyglot_api";
    }

    @Override
    public List<Class<? extends Header>> dependsOn() {
        return Collections.singletonList(GraalIsolateHeader.class);
    }

    @Override
    public void writePreamble(PrintWriter writer) {
        writer.println("#include <polyglot_types.h>");
        writer.println("#include <polyglot_isolate.h>");
        writer.println("/**");
        writer.println(" * Polyglot Native API is in experimental phase of development and should not be used in production environments.");
        writer.println(" *");
        writer.println(" * Future versions will introduce modifications to the API in backward incompatible ways. Feel free to use the API");
        writer.println(" * for examples and experiments and keep us posted about the features that you need or you feel are awkward.");
        writer.println(" */");
    }
}
