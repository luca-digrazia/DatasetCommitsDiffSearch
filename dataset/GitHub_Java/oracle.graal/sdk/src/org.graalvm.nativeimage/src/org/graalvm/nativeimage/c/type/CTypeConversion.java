/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.type;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.CTypeConversionSupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

public final class CTypeConversion {

    /**
     * An auto-closable that holds a Java {@link CharSequence} as a null-terminated C char[]. The C
     * pointer is only valid as long as the auto-closeable has not been closed.
     */
    public interface CCharPointerHolder extends AutoCloseable {
        CCharPointer get();

        @Override
        void close();
    }

    private CTypeConversion() {
    }

    public static CCharPointerHolder toCString(CharSequence javaString) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toCString(javaString);
    }

    /**
     * Allocates memory for the CString and copies bytes from the Java string into it.
     *
     * @param javaString managed Java string
     */
    public static void toCString(CharSequence javaString, CCharPointer buffer, UnsignedWord bufferSize) {
        ImageSingletons.lookup(CTypeConversionSupport.class).toCString(javaString, buffer, bufferSize);
    }

    /**
     * Decode a 0 terminated C {@code char*} to a Java string using the platform's default charset.
     *
     * @param cString the pointer to a 0 terminated C string
     * @return a Java string
     */
    public static String toJavaString(CCharPointer cString) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toJavaString(cString);
    }

    /**
     * Decode a C {@code char*} of length {@code length} to a Java string using the platform's
     * default charset.
     *
     * @param cString the pointer to a 0 terminated C string
     * @return a Java string
     */
    public static String toJavaString(CCharPointer cString, UnsignedWord length) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toJavaString(cString, length);
    }

    /**
     * Turn a C int into a Java boolean. E.g., for
     *
     * <pre>
     * &lt; if (value) ....
     * ----
     * &gt; if (CTypeConversion.toBoolean(value)) ....
     * </pre>
     *
     * because I always forget how to write this.
     */
    public static boolean toBoolean(int value) {
        return value != 0;
    }

    /**
     * Turn a SystemJava pointer into a Java boolean. E.g., for
     *
     * <pre>
     * &lt; if (pointer) ....
     * ----
     * &gt; if (CTypeConversion.toBoolean(pointer)) ....
     * </pre>
     *
     * because I always forget how to write this.
     */
    public static boolean toBoolean(PointerBase pointer) {
        return pointer.isNonNull();
    }
}
