/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.oracle.truffle.object.debug.GraphvizShapeVisitor;
import com.oracle.truffle.object.debug.JSONShapeVisitor;

class Debug {
    private static Collection<ShapeImpl> allShapes;

    static void registerShape(ShapeImpl newShape) {
        allShapes.add(newShape);
    }

    static {
        if (ObjectStorageOptions.DumpShapes) {
            allShapes = new ConcurrentLinkedQueue<>();
        }

        if (ObjectStorageOptions.DumpShapes) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    try {
                        if (ObjectStorageOptions.DumpShapesDOT) {
                            dumpDOT();
                        }
                        if (ObjectStorageOptions.DumpShapesJSON) {
                            dumpJSON();
                        }
                    } catch (FileNotFoundException | UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }

                private void dumpDOT() throws FileNotFoundException, UnsupportedEncodingException {
                    try (PrintWriter out = new PrintWriter(getOutputFile("dot"), "UTF-8")) {
                        GraphvizShapeVisitor visitor = new GraphvizShapeVisitor();
                        for (ShapeImpl shape : allShapes) {
                            shape.accept(visitor);
                        }
                        out.println(visitor);
                    }
                }

                private void dumpJSON() throws FileNotFoundException, UnsupportedEncodingException {
                    try (PrintWriter out = new PrintWriter(getOutputFile("json"), "UTF-8")) {
                        out.println("{\"shapes\": [");
                        boolean first = true;
                        for (ShapeImpl shape : allShapes) {
                            if (!first) {
                                out.println(",");
                            }
                            first = false;
                            out.print(shape.accept(new JSONShapeVisitor()));
                        }
                        if (!first) {
                            out.println();
                        }
                        out.println("]}");
                    }
                }

                private File getOutputFile(String extension) {
                    return Paths.get(ObjectStorageOptions.DumpShapesPath, "shapes." + extension).toFile();
                }
            }));
        }
    }
}
