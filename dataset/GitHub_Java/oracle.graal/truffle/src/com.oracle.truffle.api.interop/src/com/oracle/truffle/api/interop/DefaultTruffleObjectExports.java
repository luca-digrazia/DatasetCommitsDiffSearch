/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings("deprecation")
@ExportLibrary(value = InteropLibrary.class, receiverClass = TruffleObject.class)
@ImportStatic(Message.class)
class DefaultTruffleObjectExports {

    @ExportMessage
    static boolean isBoolean(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return LibraryToLegacy.sendUnbox(unbox, receiver) instanceof Boolean;
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean asBoolean(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox) throws UnsupportedMessageException {
        Object value = LibraryToLegacy.sendUnbox(unbox, receiver);
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean isString(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                Object unboxed = LibraryToLegacy.sendUnbox(unbox, receiver);
                return unboxed instanceof String || unboxed instanceof Character;
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static String asString(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox)
                    throws UnsupportedMessageException {
        try {
            Object value = (LibraryToLegacy.sendUnbox(unbox, receiver));
            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof Character) {
                return ((Character) value).toString();
            }
        } catch (UnsupportedMessageException e) {
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean isNull(TruffleObject receiver,
                    @Cached(parameters = "IS_NULL") InteropAccessNode isNull) {
        return LibraryToLegacy.sendIsNull(isNull, receiver);
    }

    @ExportMessage
    static boolean isObject(TruffleObject receiver,
                    @Cached(parameters = "HAS_KEYS") InteropAccessNode hasKeys) {
        return LibraryToLegacy.sendHasKeys(hasKeys, receiver);
    }

    @ExportMessage
    static Object readMember(TruffleObject receiver, String identifier,
                    @Cached(parameters = "READ") InteropAccessNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        return LibraryToLegacy.sendRead(read, receiver, identifier);
    }

    @ExportMessage
    static boolean isMemberReadable(TruffleObject receiver, String identifier,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isReadable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, identifier));
    }

    @ExportMessage
    static boolean isMemberModifiable(TruffleObject receiver, String identifier,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isModifiable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, identifier));
    }

    @ExportMessage
    static boolean isMemberInsertable(TruffleObject receiver, String identifier,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isInsertable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, identifier));
    }

    @ExportMessage
    static boolean isMemberRemovable(TruffleObject receiver, String identifier,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isRemovable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, identifier));
    }

    @ExportMessage
    static boolean isMemberInternal(TruffleObject receiver, String identifier,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isInternal(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, identifier));
    }

    @ExportMessage
    static boolean isMemberInvokable(TruffleObject receiver, String member,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isInvocable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, member));
    }

    @ExportMessage
    static boolean hasMemberReadSideEffects(TruffleObject receiver, String member,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.hasReadSideEffects(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, member));
    }

    @ExportMessage
    static boolean hasMemberWriteSideEffects(TruffleObject receiver, String member,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.hasWriteSideEffects(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, member));
    }

    @ExportMessage
    static void writeMember(TruffleObject receiver, String member, Object value,
                    @Cached(parameters = "WRITE") InteropAccessNode write) throws UnsupportedMessageException, UnsupportedTypeException, UnknownIdentifierException {
        LibraryToLegacy.sendWrite(write, receiver, member, value);
    }

    @ExportMessage
    static void removeMember(TruffleObject receiver, String member,
                    @Cached(parameters = "REMOVE") InteropAccessNode remove)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        boolean returnedValue = LibraryToLegacy.sendRemove(remove, receiver, member);
        if (!returnedValue) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static Object getMembers(TruffleObject receiver, boolean internal,
                    @Cached(parameters = "KEYS") InteropAccessNode keys) throws UnsupportedMessageException {
        return LibraryToLegacy.sendKeys(keys, receiver, internal);
    }

    @ExportMessage
    static Object invokeMember(TruffleObject receiver, String identifier, Object[] arguments,
                    @Cached(parameters = "INVOKE") InteropAccessNode invoke)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        return LibraryToLegacy.sendInvoke(invoke, receiver, identifier, arguments);
    }

    @ExportMessage
    static boolean isInstantiable(TruffleObject receiver,
                    @Cached(parameters = "IS_INSTANTIABLE") InteropAccessNode isInstantiable) {
        return LibraryToLegacy.sendIsInstantiable(isInstantiable, receiver);
    }

    @ExportMessage
    static Object instantiate(TruffleObject receiver, Object[] arguments,
                    @Cached(parameters = "NEW") InteropAccessNode newNode) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return LibraryToLegacy.sendNew(newNode, receiver, arguments);
    }

    @ExportMessage
    static boolean isExecutable(TruffleObject receiver,
                    @Cached(parameters = "IS_EXECUTABLE") InteropAccessNode isExecutable) {
        return LibraryToLegacy.sendIsExecutable(isExecutable, receiver);
    }

    @ExportMessage
    static Object execute(TruffleObject receiver, Object[] arguments,
                    @Cached(parameters = "EXECUTE") InteropAccessNode execute) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return LibraryToLegacy.sendExecute(execute, receiver, arguments);
    }

    @ExportMessage
    static boolean isArray(TruffleObject receiver,
                    @Cached(parameters = "HAS_SIZE") InteropAccessNode hasSize) {
        return LibraryToLegacy.sendHasSize(hasSize, receiver);
    }

    @ExportMessage
    static boolean isElementReadable(TruffleObject receiver, long index,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isReadable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, (int) index));
    }

    @ExportMessage
    static boolean isElementModifiable(TruffleObject receiver, long index,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isModifiable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, (int) index));
    }

    @ExportMessage
    static boolean isElementInsertable(TruffleObject receiver, long index,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isInsertable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, (int) index));
    }

    @ExportMessage
    static boolean isElementRemovable(TruffleObject receiver, long index,
                    @Cached(parameters = "KEY_INFO") InteropAccessNode keyInfo) {
        return KeyInfo.isRemovable(LibraryToLegacy.sendKeyInfo(keyInfo, receiver, (int) index));
    }

    @ExportMessage
    static Object readElement(TruffleObject receiver, long index,
                    @Cached(parameters = "READ") InteropAccessNode read) throws UnsupportedMessageException, InvalidArrayIndexException {
        try {
            return LibraryToLegacy.sendRead(read, receiver, (int) index);
        } catch (UnknownIdentifierException e) {
            throw mapInvalidIdentifierException(e);
        }
    }

    private static InvalidArrayIndexException mapInvalidIdentifierException(UnknownIdentifierException e) throws InvalidArrayIndexException {
        CompilerDirectives.transferToInterpreter();
        long invalidIndex = -1;
        try {
            invalidIndex = Long.parseLong(e.getUnknownIdentifier());
        } catch (NumberFormatException e1) {
        }
        throw InvalidArrayIndexException.create(invalidIndex);
    }

    @ExportMessage
    static void writeElement(TruffleObject receiver, long index, Object value,
                    @Cached(parameters = "WRITE") InteropAccessNode write) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
        try {
            LibraryToLegacy.sendWrite(write, receiver, (int) index, value);
        } catch (UnknownIdentifierException e) {
            throw mapInvalidIdentifierException(e);
        }
    }

    @ExportMessage
    static void removeElement(TruffleObject receiver, long index,
                    @Cached(parameters = "REMOVE") InteropAccessNode remove) throws UnsupportedMessageException, InvalidArrayIndexException {
        try {
            boolean returnedValue = LibraryToLegacy.sendRemove(remove, receiver, (int) index);
            if (!returnedValue) {
                throw UnsupportedMessageException.create();
            }
        } catch (UnknownIdentifierException e) {
            throw mapInvalidIdentifierException(e);
        }
    }

    @ExportMessage
    static long getArraySize(TruffleObject receiver,
                    @Cached(parameters = "GET_SIZE") InteropAccessNode getSize) throws UnsupportedMessageException {
        return ((Number) LibraryToLegacy.sendGetSize(getSize, receiver)).longValue();
    }

    @ExportMessage
    static boolean isPointer(TruffleObject receiver,
                    @Cached(parameters = "IS_POINTER") InteropAccessNode isPointer) {
        return LibraryToLegacy.sendIsPointer(isPointer, receiver);
    }

    @ExportMessage
    static long asPointer(TruffleObject receiver,
                    @Cached(parameters = "AS_POINTER") InteropAccessNode asPointer) throws UnsupportedMessageException {
        return LibraryToLegacy.sendAsPointer(asPointer, receiver);
    }

    @ExportMessage
    static Object toNative(TruffleObject receiver,
                    @Cached(parameters = "TO_NATIVE") InteropAccessNode toNative) throws UnsupportedMessageException {
        return LibraryToLegacy.sendToNative(toNative, receiver);
    }

    @ExportMessage
    static boolean isNumber(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.isNumber(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInByte(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.fitsInByte(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInShort(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.fitsInShort(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInInt(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.fitsInInt(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInLong(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.fitsInLong(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInFloat(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.fitsInFloat(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInDouble(TruffleObject receiver,
                    @Cached(parameters = "IS_BOXED") InteropAccessNode isBoxed,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) {
        if (LibraryToLegacy.sendIsBoxed(isBoxed, receiver)) {
            try {
                return numbers.fitsInDouble(LibraryToLegacy.sendUnbox(unbox, receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReach(e);
            }
        }
        return false;
    }

    @ExportMessage
    static byte asByte(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) throws UnsupportedMessageException {
        return numbers.asByte(LibraryToLegacy.sendUnbox(unbox, receiver));
    }

    @ExportMessage
    static short asShort(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) throws UnsupportedMessageException {
        return numbers.asShort(LibraryToLegacy.sendUnbox(unbox, receiver));
    }

    @ExportMessage
    static int asInt(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) throws UnsupportedMessageException {
        return numbers.asInt(LibraryToLegacy.sendUnbox(unbox, receiver));
    }

    @ExportMessage
    static long asLong(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) throws UnsupportedMessageException {
        return numbers.asLong(LibraryToLegacy.sendUnbox(unbox, receiver));
    }

    @ExportMessage
    static float asFloat(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) throws UnsupportedMessageException {
        return numbers.asFloat(LibraryToLegacy.sendUnbox(unbox, receiver));
    }

    @ExportMessage
    static double asDouble(TruffleObject receiver,
                    @Cached(parameters = "UNBOX") InteropAccessNode unbox,
                    @CachedLibrary(limit = "5") InteropLibrary numbers) throws UnsupportedMessageException {
        return numbers.asDouble(LibraryToLegacy.sendUnbox(unbox, receiver));
    }

    private static RuntimeException shouldNotReach(Throwable cause) {
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError(cause);
    }

}
