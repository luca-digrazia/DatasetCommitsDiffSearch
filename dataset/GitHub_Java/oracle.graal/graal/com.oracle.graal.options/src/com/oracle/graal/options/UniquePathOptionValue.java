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
package com.oracle.graal.options;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class UniquePathOptionValue extends OptionValue<String> {

    static final long globalTimeStamp = System.currentTimeMillis();

    private final AtomicInteger dumpId = new AtomicInteger();

    private final OptionValue<String> defaultDirectory;

    private final String extension;

    /**
     * Option to generate unique path names for dump files. The option defines a base prefix for the
     * file and an option extension. The resulting file name is generated by calling
     * {@link #getPath} and will include a {@link #globalTimeStamp timestamp} and a {@link #dumpId
     * sequence number} as part of the name. The same timestamp is used by all instances to indicate
     * the correspondence between different dump files from the same VM and the sequence number is
     * per instance.
     *
     * @param value the base of the name, which can be absolute
     * @param extension an optional file extension
     * @param defaultDirectory an {@link OptionValue} providing a default directory for the file
     */
    public UniquePathOptionValue(String value, String extension, OptionValue<String> defaultDirectory) {
        super(value);
        this.defaultDirectory = defaultDirectory;
        assert extension == null || extension.length() == 0 || extension.charAt(0) != '.' : "extension shouldn't include '.'";
        this.extension = extension;
    }

    public UniquePathOptionValue(String value, OptionValue<String> defaultDirectory) {
        this(value, null, defaultDirectory);
    }

    /**
     * Provides extension point useful when the extension is controlled by other flags.
     *
     * @return the extension without the a leading '.'
     */
    protected String getExtension() {
        return extension;
    }

    private String formatExtension() {
        String ext = getExtension();
        assert Objects.equals(ext, extension) || extension == null : "extension should be null if getExtension is overridden";
        if (ext == null || ext.length() == 0) {
            return "";
        }
        return "." + ext;
    }

    /**
     * Generate a {@link Path} using the format "%s-%d_%d%s" with the {@link #getValue() base
     * filename}, a {@link #globalTimeStamp global timestamp}, {@link #dumpId a per instance unique
     * id} and an option {@link #formatExtension extension}.
     *
     * @return the output file path or null if the flag is null
     */
    public Path getPath() {
        if (getValue() == null) {
            return null;
        }
        String name = String.format("%s-%d_%d%s", getValue(), globalTimeStamp, dumpId.incrementAndGet(), formatExtension());
        Path result = Paths.get(name);
        if (result.isAbsolute() || defaultDirectory == null) {
            return result;
        }
        return Paths.get(defaultDirectory.getValue(), name);
    }

}
