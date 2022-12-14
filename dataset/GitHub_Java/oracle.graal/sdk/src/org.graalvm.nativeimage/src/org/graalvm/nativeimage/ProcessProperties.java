/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;

/**
 * Utility class to get and set properties of the OS process at run time.
 *
 * @since 1.0
 */
public final class ProcessProperties {
    /**
     * Return the name of the executable.
     *
     * @since 1.0
     */
    public static String getExecutableName() {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getExecutableName();
    }

    /**
     * Get the Process ID of the process executing the image.
     *
     * @since 1.0
     */
    public static long getProcessID() {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getProcessID();
    }

    /**
     * Return the path of the object file defining the symbol specified as a {@link String}
     * containing the symbol name.
     * 
     * @since 1.0
     */
    public static String getObjectFile(String symbol) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getObjectFile(symbol);
    }

    /**
     * Return the path of the object file defining the symbol specified as a
     * {@link CEntryPointLiteral} containing a function pointer to symbol.
     *
     * @since 1.0
     */
    public static String getObjectFile(CEntryPointLiteral<?> symbol) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getObjectFile(symbol);
    }

    /**
     * Set the program locale.
     *
     * @since 1.0
     */
    public static String setLocale(String category, String locale) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).setLocale(category, locale);
    }

    private ProcessProperties() {
    }
}
