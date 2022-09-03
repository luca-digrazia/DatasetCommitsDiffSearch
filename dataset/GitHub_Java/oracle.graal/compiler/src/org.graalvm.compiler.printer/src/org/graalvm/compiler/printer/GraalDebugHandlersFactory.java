/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.printer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugHandler;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugOptions;
import static org.graalvm.compiler.debug.DebugOptions.PrintBinaryGraphs;
import static org.graalvm.compiler.debug.DebugOptions.ShowDumpFiles;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugHandlersFactory.class)
public class GraalDebugHandlersFactory implements DebugHandlersFactory {

    private final SnippetReflectionProvider snippetReflection;

    public GraalDebugHandlersFactory() {
        this.snippetReflection = null;
    }

    public GraalDebugHandlersFactory(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    @Override
    public List<DebugHandler> createHandlers(Function<Supplier<Path>, WritableByteChannel> sharedOutput, OptionValues options) {
        List<DebugHandler> handlers = new ArrayList<>();
        handlers.add(new GraphPrinterDumpHandler((graph) -> createPrinter(graph, sharedOutput, options)));
        if (DebugOptions.PrintCanonicalGraphStrings.getValue(options)) {
            handlers.add(new GraphPrinterDumpHandler((graph) -> createStringPrinter(snippetReflection)));
        }
        handlers.add(new NodeDumper());
        if (DebugOptions.PrintCFG.getValue(options) || DebugOptions.PrintBackendCFG.getValue(options)) {
            if (DebugOptions.PrintBinaryGraphs.getValue(options) && DebugOptions.PrintCFG.getValue(options)) {
                TTY.out.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -Dgraal.PrintCFG=false to disable it");
            }
            handlers.add(new CFGPrinterObserver());
        }
        handlers.add(new NoDeadCodeVerifyHandler());
        return handlers;
    }

    private GraphPrinter createPrinter(Graph graph, Function<Supplier<Path>, WritableByteChannel> outputSupplier, OptionValues options) throws IOException {
        WritableByteChannel channel = outputSupplier.apply(() -> createDumpPath(options, graph, PrintBinaryGraphs.getValue(options) ? "bgv" : "gv.xml", false));
        if (DebugOptions.PrintBinaryGraphs.getValue(options)) {
            return new BinaryGraphPrinter(channel, snippetReflection);
        } else {
            OutputStream out = Channels.newOutputStream(channel);
            return new IdealGraphPrinter(out, true, snippetReflection);
        }
    }

    /**
     * Creates a new file or directory for dumping based on a given graph and a file extension.
     *
     * @param graph a base path name is derived from {@code graph}
     * @param extension a suffix which if non-null and non-empty added to the end of the returned
     *            path separated by a {@code "."}
     * @param createDirectory specifies if this is a request to create a directory instead of a file
     * @return the created directory or file
     */
    static Path createDumpPath(OptionValues options, Graph graph, String extension, boolean createDirectory) {
        CompilationIdentifier compilationId = CompilationIdentifier.INVALID_COMPILATION_ID;
        String id = null;
        String label = null;
        if (graph instanceof StructuredGraph) {
            StructuredGraph sgraph = (StructuredGraph) graph;
            label = getGraphName(sgraph);
            compilationId = sgraph.compilationId();
            if (compilationId == CompilationIdentifier.INVALID_COMPILATION_ID) {
                id = graph.getClass().getSimpleName() + "-" + sgraph.graphId();
            } else {
                id = compilationId.toString(CompilationIdentifier.Verbosity.ID);
            }
        } else {
            label = graph == null ? null : graph.name != null ? graph.name : graph.toString();
            id = "UnknownCompilation-" + unknownCompilationId.incrementAndGet();
        }
        String ext = PathUtilities.formatExtension(extension);
        Path result;
        try {
            result = createUnique(DebugOptions.getDumpDirectory(options), id, label, ext, createDirectory);
        } catch (IOException ex) {
            throw rethrowSilently(RuntimeException.class, ex);
        }
        if (ShowDumpFiles.getValue(options) || Assertions.assertionsEnabled()) {
            TTY.println("Dumping debug output to %s", result.toAbsolutePath().toString());
        }
        return result;
    }

    private static final AtomicInteger unknownCompilationId = new AtomicInteger();

    /**
     * A maximum file name length supported by most file systems. There is no platform independent
     * way to get this in Java.
     */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final String ELLIPSIS = "...";

    private static Path createUnique(Path dumpDir, String id, String label, String ext, boolean createDirectory) {
        String timestamp = "";
        for (;;) {
            int fileNameLengthWithoutLabel = timestamp.length() + ext.length() + id.length() + "[]".length();
            int labelLengthLimit = MAX_FILE_NAME_LENGTH - fileNameLengthWithoutLabel;
            String fileName;
            if (labelLengthLimit < ELLIPSIS.length()) {
                // This means `id` is very long
                String suffix = timestamp + ext;
                int idLengthLimit = Math.min(MAX_FILE_NAME_LENGTH - suffix.length(), id.length());
                fileName = sanitizedFileName(id.substring(0, idLengthLimit) + suffix);
            } else {
                if (label == null) {
                    fileName = sanitizedFileName(id + timestamp + ext);
                } else {
                    String adjustedLabel = label;
                    if (label.length() > labelLengthLimit) {
                        adjustedLabel = label.substring(0, labelLengthLimit - ELLIPSIS.length()) + ELLIPSIS;
                    }
                    fileName = sanitizedFileName(id + '[' + adjustedLabel + ']' + timestamp + ext);
                }
            }
            Path result = dumpDir.resolve(fileName);
            try {
                if (createDirectory) {
                    return Files.createDirectory(result);
                } else {
                    return Files.createFile(result);
                }
            } catch (FileAlreadyExistsException e) {
                timestamp = "_" + Long.toString(System.currentTimeMillis());
            } catch (IOException ex) {
                throw rethrowSilently(RuntimeException.class, ex);
            }
        }
    }

    private static String getGraphName(StructuredGraph graph) {
        if (graph.name != null) {
            return graph.name;
        } else if (graph.method() != null) {
            return graph.method().format("%h.%n(%p)").replace(" ", "");
        } else {
            return graph.toString();
        }
    }

    public static String sanitizedFileName(String n) {
        /*
         * First ensure that the name does not contain the directory separator (which would be
         * considered a valid path).
         */
        String name = n.replace(File.separatorChar, '_');

        try {
            Paths.get(name);
            return name;
        } catch (InvalidPathException e) {
            // fall through
        }
        StringBuilder buf = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            try {
                Paths.get(String.valueOf(c));
            } catch (InvalidPathException e) {
                buf.append('_');
            }
            buf.append(c);
        }
        return buf.toString();
    }

    private static class NodeDumper implements DebugDumpHandler {
        @Override
        public void dump(DebugContext debug, Object object, String format, Object... arguments) {
            if (object instanceof Node) {
                Node node = (Node) object;
                String location = GraphUtil.approxSourceLocation(node);
                String nodeName = node.toString(Verbosity.Debugger);
                if (location != null) {
                    debug.log("Context obj %s (approx. location: %s)", nodeName, location);
                } else {
                    debug.log("Context obj %s", nodeName);
                }
            }
        }
    }

    private static CanonicalStringGraphPrinter createStringPrinter(SnippetReflectionProvider snippetReflection) {
        return new CanonicalStringGraphPrinter(snippetReflection);
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static <E extends Exception> E rethrowSilently(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }

}
