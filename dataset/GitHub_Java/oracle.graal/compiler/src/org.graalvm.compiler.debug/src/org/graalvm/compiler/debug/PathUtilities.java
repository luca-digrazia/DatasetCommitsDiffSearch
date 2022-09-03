/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

/**
 * Miscellaneous methods for modifying and generating file system paths.
 */
public class PathUtilities {

    private static final AtomicLong globalTimeStamp = new AtomicLong();
    /**
     * This generates a per thread persistent id to aid mapping related dump files with each other.
     */
    private static final ThreadLocal<PerThreadSequence> threadDumpId = new ThreadLocal<>();
    private static final AtomicInteger dumpId = new AtomicInteger();

    static class PerThreadSequence {
        final int threadID;
        HashMap<String, Integer> sequences = new HashMap<>(2);

        PerThreadSequence(int threadID) {
            this.threadID = threadID;
        }

        String generateID(String extension) {
            Integer box = sequences.get(extension);
            if (box == null) {
                sequences.put(extension, 1);
                return Integer.toString(threadID);
            } else {
                sequences.put(extension, box + 1);
                return Integer.toString(threadID) + '-' + box;
            }
        }
    }

    private static String getThreadDumpId(String extension) {
        PerThreadSequence id = threadDumpId.get();
        if (id == null) {
            id = new PerThreadSequence(dumpId.incrementAndGet());
            threadDumpId.set(id);
        }
        return id.generateID(extension);
    }

    /**
     * Prepends a period (i.e., {@code '.'}) to an non-null, non-empty string representation a file
     * extension if the string does not already start with a period.
     *
     * @return {@code ext} unmodified if it is null, empty or already starts with a period other
     *         {@code "." + ext}
     */
    public static String formatExtension(String ext) {
        if (ext == null || ext.length() == 0) {
            return "";
        }
        return "." + ext;
    }

    /**
     * Gets a time stamp for the current process. This method will always return the same value for
     * the current VM execution.
     */
    public static long getGlobalTimeStamp() {
        if (globalTimeStamp.get() == 0) {
            globalTimeStamp.compareAndSet(0, System.currentTimeMillis());
        }
        return globalTimeStamp.get();
    }

    /**
     * Generates a {@link Path} using the format "%s-%d_%d%s" with the {@code baseNameOption}, a
     * {@link #getGlobalTimeStamp() global timestamp} , {@link #getThreadDumpId a per thread unique
     * id} and an optional {@code extension}.
     *
     * @return the output file path or null if the flag is null
     */
    public static Path getPath(OptionValues options, OptionKey<String> baseNameOption, String extension) throws IOException {
        return getPath(options, baseNameOption, extension, true);
    }

    /**
     * Generate a {@link Path} using the format "%s-%d_%s" with the {@code baseNameOption}, a
     * {@link #getGlobalTimeStamp() global timestamp} and an optional {@code extension} .
     *
     * @return the output file path or null if the flag is null
     */
    public static Path getPathGlobal(OptionValues options, OptionKey<String> baseNameOption, String extension) throws IOException {
        return getPath(options, baseNameOption, extension, false);
    }

    private static Path getPath(OptionValues options, OptionKey<String> baseNameOption, String extension, boolean includeThreadId) throws IOException {
        if (baseNameOption.getValue(options) == null) {
            return null;
        }
        String ext = formatExtension(extension);
        final String name = includeThreadId
                        ? String.format("%s-%d_%s%s", baseNameOption.getValue(options), getGlobalTimeStamp(), getThreadDumpId(ext), ext)
                        : String.format("%s-%d%s", baseNameOption.getValue(options), getGlobalTimeStamp(), ext);
        Path result = Paths.get(name);
        if (result.isAbsolute()) {
            return result;
        }
        Path dumpDir = DebugOptions.getDumpDirectory(options);
        final Path fileName = Paths.get(dumpDir.toString(), name);
        return fileName.normalize();
    }

    /**
     * Gets a value based on {@code name} that can be passed to {@link Paths#get(String, String...)}
     * without causing an {@link InvalidPathException}.
     *
     * @return {@code name} with all characters invalid for the current file system replaced by
     *         {@code '_'}
     */
    public static String sanitizeFileName(String name) {
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

    /**
     * A maximum file name length supported by most file systems. There is no platform independent
     * way to get this in Java.
     */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final String ELLIPSIS = "...";

    static Path createUnique(OptionValues options, OptionKey<String> baseNameOption, String id, String label, String ext, boolean createDirectory) throws IOException {
        String timestamp = "";
        String prefix;
        if (id == null) {
            prefix = baseNameOption.getValue(options);
            int slash = prefix.lastIndexOf(File.separatorChar);
            prefix = prefix.substring(slash + 1);
        } else {
            prefix = id;
        }
        for (;;) {
            int fileNameLengthWithoutLabel = timestamp.length() + ext.length() + prefix.length() + "[]".length();
            int labelLengthLimit = MAX_FILE_NAME_LENGTH - fileNameLengthWithoutLabel;
            String fileName;
            if (labelLengthLimit < ELLIPSIS.length()) {
                // This means `id` is very long
                String suffix = timestamp + ext;
                int idLengthLimit = Math.min(MAX_FILE_NAME_LENGTH - suffix.length(), prefix.length());
                fileName = sanitizeFileName(prefix.substring(0, idLengthLimit) + suffix);
            } else {
                if (label == null) {
                    fileName = sanitizeFileName(prefix + timestamp + ext);
                } else {
                    String adjustedLabel = label;
                    if (label.length() > labelLengthLimit) {
                        adjustedLabel = label.substring(0, labelLengthLimit - ELLIPSIS.length()) + ELLIPSIS;
                    }
                    fileName = sanitizeFileName(prefix + '[' + adjustedLabel + ']' + timestamp + ext);
                }
            }
            Path dumpDir = DebugOptions.getDumpDirectory(options);
            Path result = Paths.get(dumpDir.toString(), fileName);
            try {
                if (createDirectory) {
                    return Files.createDirectory(result);
                } else {
                    return Files.createFile(result);
                }
            } catch (FileAlreadyExistsException e) {
                if (timestamp.length() == 0) {
                    timestamp = "_" + getGlobalTimeStamp();
                } else {
                    timestamp = "_" + System.currentTimeMillis();
                }
            }
        }
    }

}
