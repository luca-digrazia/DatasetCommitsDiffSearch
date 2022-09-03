/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import com.oracle.truffle.api.source.Source;
import static com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.L3;
import com.oracle.truffle.api.vm.*;
import java.io.*;
import java.util.concurrent.Executors;
import org.junit.*;
import static org.junit.Assert.*;

public class GlobalSymbolTest {
    @Test
    public void globalSymbolFoundByLanguage() throws IOException {
        PolyglotEngine vm = PolyglotEngine.buildNew().globalSymbol("ahoj", "42").executor(Executors.newSingleThreadExecutor()).build();
        // @formatter:off
        Object ret = vm.eval(
            Source.fromText("return=ahoj", "Return").withMimeType(L3)
        ).get();
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void globalSymbolFoundByVMUser() throws IOException {
        PolyglotEngine vm = PolyglotEngine.buildNew().globalSymbol("ahoj", "42").build();
        PolyglotEngine.Value ret = vm.findGlobalSymbol("ahoj");
        assertNotNull("Symbol found", ret);
        assertEquals("42", ret.get());
    }
}
