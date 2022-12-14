/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.impl.NativeArgumentBuffer.TypeTag;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.BooleanArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.ByteArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.CharArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.CharConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.DefaultConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.DoubleArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.FloatArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.IntArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.LongArrayConversion;
import com.oracle.truffle.nfi.impl.SerializeArgumentLibrary.ShortArrayConversion;

@GenerateLibrary
@DefaultExport(CharConversion.class)
@DefaultExport(BooleanArrayConversion.class)
@DefaultExport(ByteArrayConversion.class)
@DefaultExport(ShortArrayConversion.class)
@DefaultExport(CharArrayConversion.class)
@DefaultExport(IntArrayConversion.class)
@DefaultExport(LongArrayConversion.class)
@DefaultExport(FloatArrayConversion.class)
@DefaultExport(DoubleArrayConversion.class)
@DefaultExport(DefaultConversion.class)
@SuppressWarnings("unused")
abstract class SerializeArgumentLibrary extends Library {

    public void putByte(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putShort(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putInt(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putLong(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putFloat(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putDouble(Object arg, NativeArgumentBuffer buffer) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putPointer(Object arg, NativeArgumentBuffer buffer, int ptrSize) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public void putString(Object arg, NativeArgumentBuffer buffer, int ptrSize) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{arg});
    }

    public static LibraryFactory<SerializeArgumentLibrary> getFactory() {
        return FACTORY;
    }

    public static SerializeArgumentLibrary getUncached() {
        return FACTORY.getUncached();
    }

    private static final LibraryFactory<SerializeArgumentLibrary> FACTORY = LibraryFactory.resolve(SerializeArgumentLibrary.class);

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = Object.class)
    abstract static class DefaultConversion {

        @ExportMessage
        static void putByte(Object arg, NativeArgumentBuffer buffer,
                        @Shared("exception") @Cached BranchProfile exception,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                buffer.putInt8(interop.asByte(arg));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                try {
                    buffer.putInt8(interop.asBoolean(arg) ? (byte) 1 : 0);
                } catch (UnsupportedMessageException ex2) {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }

        @ExportMessage
        static void putShort(Object arg, NativeArgumentBuffer buffer,
                        @Shared("exception") @Cached BranchProfile exception,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                buffer.putInt16(interop.asShort(arg));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                try {
                    buffer.putInt16(interop.asBoolean(arg) ? (short) 1 : 0);
                } catch (UnsupportedMessageException ex2) {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }

        @ExportMessage
        static void putInt(Object arg, NativeArgumentBuffer buffer,
                        @Shared("exception") @Cached BranchProfile exception,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                buffer.putInt32(interop.asInt(arg));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                try {
                    buffer.putInt32(interop.asBoolean(arg) ? 1 : 0);
                } catch (UnsupportedMessageException ex2) {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }

        @ExportMessage
        static void putLong(Object arg, NativeArgumentBuffer buffer,
                        @Shared("exception") @Cached BranchProfile exception,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                buffer.putInt64(interop.asLong(arg));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                try {
                    buffer.putInt64(interop.asBoolean(arg) ? 1L : 0L);
                } catch (UnsupportedMessageException ex2) {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }

        @ExportMessage
        static void putFloat(Object arg, NativeArgumentBuffer buffer,
                        @Shared("exception") @Cached BranchProfile exception,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                buffer.putFloat(interop.asFloat(arg));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                try {
                    buffer.putFloat(interop.asBoolean(arg) ? 1.0f : 0.0f);
                } catch (UnsupportedMessageException ex2) {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }

        @ExportMessage
        static void putDouble(Object arg, NativeArgumentBuffer buffer,
                        @Shared("exception") @Cached BranchProfile exception,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                buffer.putDouble(interop.asDouble(arg));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                try {
                    buffer.putDouble(interop.asBoolean(arg) ? 1.0 : 0.0);
                } catch (UnsupportedMessageException ex2) {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }

        @ExportMessage
        static void putPointer(Object arg, NativeArgumentBuffer buffer, int ptrSize,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Shared("exception") @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                interop.toNative(arg);
                buffer.putPointer(interop.asPointer(arg), ptrSize);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                if (interop.isNull(arg)) {
                    buffer.putPointer(0, ptrSize);
                    return;
                } else {
                    try {
                        buffer.putPointer(interop.asLong(arg), ptrSize);
                        return;
                    } catch (UnsupportedMessageException ex2) {
                    }
                }
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @ExportMessage
        static void putString(Object arg, NativeArgumentBuffer buffer, int ptrSize,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Shared("exception") @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                buffer.putObject(TypeTag.STRING, interop.asString(arg), ptrSize);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                if (interop.isNull(arg)) {
                    buffer.putPointer(0, ptrSize);
                } else {
                    throw UnsupportedTypeException.create(new Object[]{arg});
                }
            }
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = Character.class)
    abstract static class CharConversion {

        static short zero() {
            return 0;
        }

        @ExportMessage
        static void putByte(Character ch, NativeArgumentBuffer buffer,
                        @CachedLibrary("zero()") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            serialize.putByte((short) ch.charValue(), buffer);
        }

        @ExportMessage
        static void putShort(Character ch, NativeArgumentBuffer buffer,
                        @CachedLibrary("zero()") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            serialize.putShort((short) ch.charValue(), buffer);
        }

        @ExportMessage
        static void putInt(Character ch, NativeArgumentBuffer buffer,
                        @CachedLibrary("zero()") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            serialize.putInt((short) ch.charValue(), buffer);
        }

        @ExportMessage
        static void putLong(Character ch, NativeArgumentBuffer buffer,
                        @CachedLibrary("zero()") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            serialize.putLong((short) ch.charValue(), buffer);
        }

        @ExportMessage
        static void putFloat(Character ch, NativeArgumentBuffer buffer,
                        @CachedLibrary("zero()") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            serialize.putFloat((short) ch.charValue(), buffer);
        }

        @ExportMessage
        static void putDouble(Character ch, NativeArgumentBuffer buffer,
                        @CachedLibrary("zero()") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            serialize.putDouble((short) ch.charValue(), buffer);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = boolean[].class)
    abstract static class BooleanArrayConversion {

        @ExportMessage
        static void putPointer(boolean[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.BOOLEAN_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = byte[].class)
    abstract static class ByteArrayConversion {

        @ExportMessage
        static void putPointer(byte[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.BYTE_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = short[].class)
    abstract static class ShortArrayConversion {

        @ExportMessage
        static void putPointer(short[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.SHORT_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = char[].class)
    abstract static class CharArrayConversion {

        @ExportMessage
        static void putPointer(char[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.CHAR_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = int[].class)
    abstract static class IntArrayConversion {

        @ExportMessage
        static void putPointer(int[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.INT_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = long[].class)
    abstract static class LongArrayConversion {

        @ExportMessage
        static void putPointer(long[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.LONG_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = float[].class)
    abstract static class FloatArrayConversion {

        @ExportMessage
        static void putPointer(float[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.FLOAT_ARRAY, receiver, ptrSize);
        }
    }

    @ExportLibrary(value = SerializeArgumentLibrary.class, receiverType = double[].class)
    abstract static class DoubleArrayConversion {

        @ExportMessage
        static void putPointer(double[] receiver, NativeArgumentBuffer buffer, int ptrSize) {
            buffer.putObject(TypeTag.DOUBLE_ARRAY, receiver, ptrSize);
        }
    }
}
