/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import static com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage.FILENAME_EXTENSION;
import static com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage.MIME_TYPE;

import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

final class TestSource {

    static Source createBlock8(String sourceName) {
        return Source.fromText("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n",
                        sourceName + FILENAME_EXTENSION).withMimeType(MIME_TYPE);
    }

    static Source createBlock12(String sourceName) {
        return Source.fromText("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n",
                        sourceName + FILENAME_EXTENSION).withMimeType(MIME_TYPE);
    }

    static Source createCallLoop3(String sourceName) {
        return Source.fromText("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n",
                        sourceName + FILENAME_EXTENSION).withMimeType(MIME_TYPE);
    }

    static File createCallLoop3File() throws IOException {
        String code = "ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n";
        File file = File.createTempFile("Loop3", FILENAME_EXTENSION);
        try (Writer w = new FileWriter(file)) {
            w.write(code);
        }
        file.deleteOnExit();
        return file;
    }

    private TestSource() {
    }

}
