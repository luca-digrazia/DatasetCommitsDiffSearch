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
package com.oracle.svm.core.c;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.Utf8;

/**
 * A factory for pre-allocating and pre-initializing chunks of static global data that are located
 * outside of the heap, or for creating references to symbols. The {@linkplain CGlobalData returned
 * objects} can be used to access the data by address at runtime. In order for the data to be
 * actually allocated in the native image, it must be reachable during the static analysis.
 */
public final class CGlobalDataFactory {
    /**
     * Create a reference to the symbol with the specified name. Calling {@link CGlobalData#get()}
     * on the returned object at runtime returns the referenced symbol's address.
     */
    public static <T extends PointerBase> CGlobalData<T> forSymbol(String symbolName) {
        return new CGlobalDataImpl<>(symbolName);
    }

    /**
     * Create a chunk of data that is dimensioned and initialized to contain the provided string's
     * contents as {@linkplain Utf8#stringToUtf8(String, boolean) zero-terminated modified UTF-8}.
     */
    public static <T extends PointerBase> CGlobalData<T> createCString(String content) {
        return createCString(content, null);
    }

    /**
     * Same as {@link #createCString(String)}, and additionally creates a symbol with the provided
     * name for the allocated string.
     */
    public static <T extends PointerBase> CGlobalData<T> createCString(String content, String symbolName) {
        return new CGlobalDataImpl<>(symbolName, () -> Utf8.stringToUtf8(content, true));
    }

    /**
     * Create a chunk of zero-initialized bytes with at least the length that is provided by the
     * specified supplier.
     */
    public static <T extends PointerBase> CGlobalData<T> createBytes(IntSupplier sizeSupplier) {
        return createBytes(sizeSupplier, null);
    }

    /**
     * Same as {@link #createBytes(IntSupplier)}, and additionally creates a symbol with the
     * provided name for the allocated bytes.
     */
    public static <T extends PointerBase> CGlobalData<T> createBytes(IntSupplier sizeSupplier, String symbolName) {
        return new CGlobalDataImpl<>(symbolName, sizeSupplier);
    }

    /**
     * Create a chunk of bytes that is dimensioned and initialized to contain the bytes provided by
     * the specified supplier.
     */
    public static <T extends PointerBase> CGlobalData<T> createBytes(Supplier<byte[]> contentSupplier) {
        return createBytes(contentSupplier, null);
    }

    /**
     * Same as {@link #createBytes(Supplier)}, and additionally creates a symbol with the provided
     * name for the allocated bytes.
     */
    public static <T extends PointerBase> CGlobalData<T> createBytes(Supplier<byte[]> contentSupplier, String symbolName) {
        return new CGlobalDataImpl<>(symbolName, contentSupplier);
    }

    /**
     * Create a single word that is initialized to the specified value.
     */
    public static <T extends PointerBase> CGlobalData<T> createWord(WordBase initialValue) {
        return createWord(initialValue, null);
    }

    /**
     * Same as {@link #createWord(WordBase)}, and additionally creates a symbol with the provided
     * name for the allocated word.
     */
    public static <T extends PointerBase> CGlobalData<T> createWord(WordBase initialValue, String symbolName) {
        assert ConfigurationValues.getTarget().wordSize == Long.BYTES;
        Supplier<byte[]> supplier = () -> ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder()).putLong(initialValue.rawValue()).array();
        return new CGlobalDataImpl<>(symbolName, supplier);
    }

    /**
     * Create a single word that is initialized to zero.
     */
    public static <T extends PointerBase> CGlobalData<T> createWord() {
        return createWord((String) null);
    }

    /**
     * Same as {@link #createWord()}, and additionally creates a symbol with the provided name for
     * the allocated word.
     */
    public static <T extends PointerBase> CGlobalData<T> createWord(String symbolName) {
        return new CGlobalDataImpl<>(symbolName, () -> ConfigurationValues.getTarget().wordSize);
    }

    private CGlobalDataFactory() {
    }
}
