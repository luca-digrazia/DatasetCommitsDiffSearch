/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;

public class StringAsInterfaceNFITest {
    private static StdLib stdlib;
    private static PolyglotEngine engine;

    @BeforeClass
    public static void loadLibraries() {
        engine = PolyglotEngine.newBuilder().build();
        stdlib = engine.eval(Source.newBuilder("default {\n" + //
                        "  strdup(string):string;\n" + //
                        "  malloc(UINT32):pointer;\n" + //
                        "  free(pointer):void;\n" + //
                        "}" //
        ).name("(load default)").mimeType("application/x-native").build()).as(StdLib.class);
    }

    @AfterClass
    public static void cleanUp() {
        engine.dispose();
    }

    interface StdLib {
        long malloc(int size);

        void free(long pointer);

        String strdup(String orig);
    }

    interface Strndup {
        String strndup(String orig, int len);
    }

    @Test
    public void testDuplicateAString() {
        String copy = stdlib.strdup("Ahoj");
        assertEquals("Ahoj", copy);
    }

    @Test
    public void testAllocAndRelease() {
        long mem = stdlib.malloc(512);
        stdlib.free(mem);
    }

    @Test
    public void canViewDefaultLibraryAsAnotherInterface() {
        Strndup second = engine.eval(Source.newBuilder("default {\n" + //
                        "  strndup(string, UINT32):string;\n" + //
                        "}" //
        ).name("(load default 2nd time)").mimeType("application/x-native").build()).as(Strndup.class);

        String copy = stdlib.strdup("Hello World!");
        String hello = second.strndup(copy, 5);
        assertEquals("Hello", hello);
    }

}
