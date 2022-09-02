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

import static com.oracle.truffle.api.interop.AssertUtils.ASSERTIONS_ENABLED;
import static com.oracle.truffle.api.interop.AssertUtils.notThrows;
import static com.oracle.truffle.api.interop.AssertUtils.preCondition;
import static com.oracle.truffle.api.interop.AssertUtils.validArgument;
import static com.oracle.truffle.api.interop.AssertUtils.validArguments;
import static com.oracle.truffle.api.interop.AssertUtils.validReturn;
import static com.oracle.truffle.api.interop.AssertUtils.violationInvariant;
import static com.oracle.truffle.api.interop.AssertUtils.violationPost;

import com.oracle.truffle.api.interop.InteropLibrary.Asserts;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.ResolvedLibrary;

/**
 * Subclasses of this class represent guest language interoperability libraries. This class is only
 * a marker base class used for documentation purposes.
 * <p>
 * With interop libraries only certain values are allowed to be passed. The following Java types are
 * allowed to be passed as receiver or argument value:
 * <ul>
 * <li>{@link String} and {@link Character} are interpreted as {@link StringLibrary#isString(Object)
 * string} value.
 * <li>{@link Boolean} is interpreted as {@link BooleanLibrary#isBoolean(Object) boolean} value.
 * <li>{@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float} and {@link Double}
 * are interpreted as {@link NumberLibrary#isNumber(Object) number} values.
 * <li>{@link TruffleObject}: A value that optionally exports implementations of some of the interop
 * libraries. Any value, even if not implementing any libraries is allowed to be passed.
 * </ul>
 * Values are only verified if assertions are enabled. No other values are allowed to be passed in
 * order to allow the introduction of new types in future revisions of interop.
 * <p>
 * Across interop libraries checked exceptions are thrown to indicate error states. The interop
 * caller is supposed to catch those exceptions directly and translate them into guest language
 * errors of the target language. Interop errors include:
 *
 * TODO examples.
 *
 * <h3>Implementing</h3>
 *
 * @since 1.0
 */
@GenerateLibrary(assertions = Asserts.class, receiverType = TruffleObject.class)
@DefaultExport(DefaultBooleanExports.class)
@DefaultExport(DefaultIntegerExports.class)
@DefaultExport(DefaultByteExports.class)
@DefaultExport(DefaultShortExports.class)
@DefaultExport(DefaultLongExports.class)
@DefaultExport(DefaultFloatExports.class)
@DefaultExport(DefaultDoubleExports.class)
@DefaultExport(DefaultCharacterExports.class)
@DefaultExport(DefaultStringExports.class)
@DefaultExport(DefaultTruffleObjectExports.class)
@SuppressWarnings("unused")
public abstract class InteropLibrary extends Library {

    protected InteropLibrary() {
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. If a receiver represents a null then cannot represent any of the following types:
     * <ul>
     * <li>{@link #isString(Object) String}
     * <li>{@link #isBoolean(Object) Boolean}
     * <li>{@link #isExecutable(Object) Executable}
     * <li>{@link #isInstantiable(Object) Instantiable}
     * <li>{@link #isNumber(Object) Number}
     * <li>{@link #isPointer(Object) Pointer}
     * </ul>
     * Null values may have {@link #hasMembers(Object) members} or {@link #hasArrayElements(Object)
     * array elements}.
     * <p>
     * <b>Example Interpretations</b>:
     *
     * @since 1.0
     */
    public boolean isNull(Object receiver) {
        return false;
    }

    // Boolean Messages
    @Abstract(ifExported = "asBoolean")
    public boolean isBoolean(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isBoolean")
    public boolean asBoolean(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // Executable Messages

    @Abstract(ifExported = "execute")
    public boolean isExecutable(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isExecutable")
    public Object execute(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // Instantiable Messages

    @Abstract(ifExported = "instantiate")
    public boolean isInstantiable(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isInstantiable")
    public Object instantiate(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // String Messages

    @Abstract(ifExported = "asString")
    public boolean isString(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isString")
    public String asString(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // Number Messages

    @Abstract(ifExported = {"fitsInByte", "fitsInShort", "fitsInInt", "fitsInLong", "fitsInFloat", "fitsInDouble", "asByte", "asShort", "asInt", "asLong", "asFloat", "asDouble"})
    public boolean isNumber(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public boolean fitsInByte(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public boolean fitsInShort(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public boolean fitsInInt(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public boolean fitsInLong(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public boolean fitsInFloat(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public boolean fitsInDouble(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "isNumber")
    public byte asByte(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "isNumber")
    public short asShort(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "isNumber")
    public int asInt(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "isNumber")
    public long asLong(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "isNumber")
    public float asFloat(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "isNumber")
    public double asDouble(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // Object Messages

    @Abstract(ifExported = {"getMembers", "isMemberReadable", "readMember", "isMemberModifiable", "isMemberInsertable", "writeMember", "isMemberRemovable", "removeMember", "isMemberInvokable",
                    "invokeMember", "isMemberInternal", "hasMemberReadSideEffects", "hasMemberWriteSideEffects"})
    public boolean hasMembers(Object receiver) {
        return false;
    }

    @Abstract(ifExported = "hasMembers")
    public Object getMembers(Object receiver, boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    public final Object getMembers(Object receiver) throws UnsupportedMessageException {
        return getMembers(receiver, false);
    }

    @Abstract(ifExported = "readMember")
    public boolean isMemberReadable(Object receiver, String member) {
        return false;
    }

    @Abstract(ifExported = "isMemberReadable")
    public Object readMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "writeMember")
    public boolean isMemberModifiable(Object receiver, String member) {
        return false;
    }

    @Abstract(ifExported = "writeMember")
    public boolean isMemberInsertable(Object receiver, String member) {
        return false;
    }

    @Abstract(ifExported = {"isMemberModifiable", "isMemberInsertable"})
    public void writeMember(Object receiver, String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "removeMember")
    public boolean isMemberRemovable(Object receiver, String member) {
        return false;
    }

    @Abstract(ifExported = "isMemberRemovable")
    public void removeMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "invokeMember")
    public boolean isMemberInvokable(Object receiver, String member) {
        return false;
    }

    @Abstract(ifExported = "isMemberInvokable")
    public Object invokeMember(Object receiver, String member, Object... arguments)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        throw UnsupportedMessageException.create();
    }

    public boolean isMemberInternal(Object receiver, String member) {
        return false;
    }

    public final boolean isMemberWritable(Object receiver, String member) {
        return isMemberModifiable(receiver, member) || isMemberInsertable(receiver, member);
    }

    public final boolean isMemberExisting(Object receiver, String member) {
        return isMemberReadable(receiver, member) || isMemberModifiable(receiver, member) || isMemberRemovable(receiver, member) || isMemberInvokable(receiver, member);
    }

    public boolean hasMemberReadSideEffects(Object receiver, String member) {
        return false;
    }

    public boolean hasMemberWriteSideEffects(Object receiver, String member) {
        return false;
    }

    // Array Messages

    @Abstract(ifExported = {"readArrayElement", "writeArrayElement", "removeArrayElement", "isArrayElementModifiable", "isArrayElementRemovable", "isArrayElementReadable"})
    public boolean hasArrayElements(Object receiver) {
        return false;
    }

    @Abstract(ifExported = {"hasArrayElements"})
    public Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = {"hasArrayElements"})
    public long getArraySize(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = {"hasArrayElements"})
    public boolean isArrayElementReadable(Object receiver, long index) {
        return false;
    }

    @Abstract(ifExported = {"isArrayElementModifiable", "isArrayElementInsertable"})
    public void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "isArrayElementRemovable")
    public void removeArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @Abstract(ifExported = "writeArrayElement")
    public boolean isArrayElementModifiable(Object receiver, long index) {
        return false;
    }

    @Abstract(ifExported = "writeArrayElement")
    public boolean isArrayElementInsertable(Object receiver, long index) {
        return false;
    }

    @Abstract(ifExported = "removeArrayElement")
    public boolean isArrayElementRemovable(Object receiver, long index) {
        return false;
    }

    public final boolean isArrayElementWritable(Object receiver, long index) {
        return isArrayElementModifiable(receiver, index) || isArrayElementInsertable(receiver, index);
    }

    public final boolean isArrayElementExisting(Object receiver, long index) {
        return isArrayElementModifiable(receiver, index) || isArrayElementReadable(receiver, index) || isArrayElementRemovable(receiver, index);
    }

    @Abstract(ifExported = {"asPointer"})
    public boolean isPointer(Object receiver) {
        return false;
    }

    @Abstract(ifExported = {"isPointer"})
    public long asPointer(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    public void toNative(Object receiver) {
    }

    /**
     * Returns the resolved language interoperability library that allows to instantiate libraries
     * and inspect its messages. Short-cut for {@link ResolvedLibrary#resolve(Class)
     * ResolvedLibrary.resolve(InteropLibrary.class)}.
     *
     * @see ResolvedLibrary#resolve(Class)
     */
    public static ResolvedLibrary<InteropLibrary> resolve() {
        return INTEROP_LIBRARY;
    }

    static final ResolvedLibrary<InteropLibrary> INTEROP_LIBRARY = ResolvedLibrary.resolve(InteropLibrary.class);

    static class Asserts extends InteropLibrary {

        @Child private InteropLibrary delegate;

        Asserts(InteropLibrary delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean accepts(Object receiver) {
            assert preCondition(receiver);
            return delegate.accepts(receiver);
        }

        @Override
        public boolean isNull(Object receiver) {
            assert preCondition(receiver);
            return delegate.isNull(receiver);
        }

        @Override
        public boolean isBoolean(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isBoolean(receiver);
            assert !result || notThrows(() -> delegate.asBoolean(receiver)) : violationInvariant(receiver);
            return result;
        }

        @Override
        public boolean asBoolean(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasBoolean = ASSERTIONS_ENABLED && delegate.isBoolean(receiver);
            try {
                boolean result = delegate.asBoolean(receiver);
                assert wasBoolean : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasBoolean : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isExecutable(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isExecutable(receiver);
            return result;
        }

        @Override
        public Object execute(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            assert preCondition(receiver);
            assert validArguments(receiver, arguments);
            boolean wasExecutable = ASSERTIONS_ENABLED && delegate.isExecutable(receiver);
            try {
                Object result = delegate.execute(receiver, arguments);
                assert wasExecutable : violationInvariant(receiver, arguments);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnsupportedTypeException : violationInvariant(receiver, arguments);
                assert !(e instanceof UnsupportedMessageException) || !wasExecutable : violationInvariant(receiver, arguments);
                throw e;
            }
        }

        @Override
        public boolean isInstantiable(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isInstantiable(receiver);
            return result;
        }

        @Override
        public Object instantiate(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            assert preCondition(receiver);
            assert validArguments(receiver, arguments);
            boolean wasInstantiable = ASSERTIONS_ENABLED && delegate.isInstantiable(receiver);
            try {
                Object result = delegate.instantiate(receiver, arguments);
                assert wasInstantiable : violationInvariant(receiver, arguments);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnsupportedTypeException : violationInvariant(receiver, arguments);
                assert !(e instanceof UnsupportedMessageException) || !wasInstantiable : violationInvariant(receiver, arguments);
                throw e;
            }
        }

        @Override
        public boolean isString(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isString(receiver);
            assert !result || notThrows(() -> delegate.asString(receiver)) : violationInvariant(receiver);
            return result;
        }

        @Override
        public String asString(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasString = ASSERTIONS_ENABLED && delegate.isString(receiver);
            try {
                String result = delegate.asString(receiver);
                assert wasString : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasString : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isNumber(Object receiver) {
            assert preCondition(receiver);
            return delegate.isNumber(receiver);
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            assert preCondition(receiver);
            boolean fits = delegate.fitsInByte(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInShort(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInInt(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInLong(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInFloat(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInDouble(receiver) : violationInvariant(receiver);
            assert !fits || notThrows(() -> delegate.asByte(receiver)) : violationInvariant(receiver);
            return fits;
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            assert preCondition(receiver);

            boolean fits = delegate.fitsInShort(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInInt(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInLong(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInFloat(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInDouble(receiver) : violationInvariant(receiver);
            assert !fits || notThrows(() -> delegate.asShort(receiver)) : violationInvariant(receiver);
            return fits;
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            assert preCondition(receiver);

            boolean fits = delegate.fitsInInt(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInLong(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInDouble(receiver) : violationInvariant(receiver);
            assert !fits || notThrows(() -> delegate.asInt(receiver)) : violationInvariant(receiver);
            return fits;
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            assert preCondition(receiver);

            boolean fits = delegate.fitsInLong(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || notThrows(() -> delegate.asLong(receiver)) : violationInvariant(receiver);
            return fits;
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            assert preCondition(receiver);
            boolean fits = delegate.fitsInFloat(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || notThrows(() -> delegate.asFloat(receiver)) : violationInvariant(receiver);
            return fits;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            assert preCondition(receiver);
            boolean fits = delegate.fitsInDouble(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || notThrows(() -> delegate.asDouble(receiver)) : violationInvariant(receiver);
            return fits;
        }

        @Override
        public byte asByte(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);

            try {
                byte result = delegate.asByte(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInByte(receiver) : violationInvariant(receiver);
                assert result == delegate.asShort(receiver) : violationInvariant(receiver);
                assert result == delegate.asInt(receiver) : violationInvariant(receiver);
                assert result == delegate.asLong(receiver) : violationInvariant(receiver);
                assert result == delegate.asFloat(receiver) : violationInvariant(receiver);
                assert result == delegate.asDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public short asShort(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                short result = delegate.asShort(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInShort(receiver) : violationInvariant(receiver);
                assert result == delegate.asInt(receiver) : violationInvariant(receiver);
                assert result == delegate.asLong(receiver) : violationInvariant(receiver);
                assert result == delegate.asFloat(receiver) : violationInvariant(receiver);
                assert result == delegate.asDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public int asInt(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                int result = delegate.asInt(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInInt(receiver) : violationInvariant(receiver);
                assert result == delegate.asLong(receiver) : violationInvariant(receiver);
                assert result == delegate.asDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public long asLong(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                long result = delegate.asLong(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInLong(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public float asFloat(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                float result = delegate.asFloat(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInFloat(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public double asDouble(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                double result = delegate.asDouble(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasMembers(Object receiver) {
            assert preCondition(receiver);
            return delegate.hasMembers(receiver);
        }

        @Override
        public Object readMember(Object receiver, String identifier) throws UnsupportedMessageException, UnknownIdentifierException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean wasReadable = ASSERTIONS_ENABLED && delegate.isMemberReadable(receiver, identifier);
            try {
                Object result = delegate.readMember(receiver, identifier);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasReadable : violationInvariant(receiver, identifier);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeMember(Object receiver, String identifier, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            assert validArgument(receiver, value);
            boolean wasWritable = ASSERTIONS_ENABLED && (delegate.isMemberModifiable(receiver, identifier) || delegate.isMemberInsertable(receiver, identifier));
            try {
                delegate.writeMember(receiver, identifier, value);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasWritable : violationInvariant(receiver, identifier);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException || e instanceof UnsupportedTypeException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void removeMember(Object receiver, String identifier) throws UnsupportedMessageException, UnknownIdentifierException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean wasRemovable = ASSERTIONS_ENABLED && delegate.isMemberRemovable(receiver, identifier);
            try {
                delegate.removeMember(receiver, identifier);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasRemovable : violationInvariant(receiver, identifier);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public Object invokeMember(Object receiver, String identifier, Object... arguments) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            assert validArguments(receiver, arguments);
            boolean wasInvocable = ASSERTIONS_ENABLED && delegate.isMemberInvokable(receiver, identifier);
            try {
                Object result = delegate.invokeMember(receiver, identifier, arguments);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasInvocable : violationInvariant(receiver, identifier);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnknownIdentifierException ||
                                e instanceof UnsupportedTypeException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public Object getMembers(Object receiver, boolean internal) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                Object result = delegate.getMembers(receiver, internal);
                assert validReturn(receiver, result);
                assert assertMemberKeys(receiver, result, internal);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        private static boolean assertMemberKeys(Object receiver, Object result, boolean internal) {
            assert result != null : violationPost(receiver, result);
            InteropLibrary uncached = InteropLibrary.resolve().getUncached(result);
            assert uncached.hasArrayElements(result) : violationPost(receiver, result);
            long arraySize;
            try {
                arraySize = uncached.getArraySize(result);
            } catch (UnsupportedMessageException e) {
                assert false : violationPost(receiver, e);
                return true;
            }
            for (int i = 0; i < arraySize; i++) {
                assert uncached.isArrayElementReadable(result, i) : violationPost(receiver, result);
                Object element;
                try {
                    element = uncached.readArrayElement(result, i);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    assert false : violationPost(receiver, result);
                    return true;
                }
                assert InteropLibrary.resolve().getUncachedDispatch().isString(element) : violationPost(receiver, element);
                try {
                    InteropLibrary.resolve().getUncachedDispatch().asString(element);
                } catch (UnsupportedMessageException e) {
                    assert false : violationInvariant(result, i);
                }
            }
            return true;
        }

        @Override
        public boolean hasMemberReadSideEffects(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.hasMemberReadSideEffects(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert !result || delegate.isMemberReadable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean hasMemberWriteSideEffects(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.hasMemberWriteSideEffects(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert !result || delegate.isMemberWritable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberReadable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberReadable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberModifiable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberModifiable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberInsertable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberInsertable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberRemovable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberRemovable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberInvokable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberInvokable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberInternal(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberInternal(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            assert preCondition(receiver);
            return delegate.hasArrayElements(receiver);
        }

        @Override
        public Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            assert preCondition(receiver);
            boolean wasReadable = ASSERTIONS_ENABLED && delegate.isArrayElementReadable(receiver, index);
            try {
                Object result = delegate.readArrayElement(receiver, index);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasReadable : violationInvariant(receiver, index);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
            assert preCondition(receiver);
            assert validArgument(receiver, value);
            boolean wasWritable = ASSERTIONS_ENABLED && delegate.isArrayElementModifiable(receiver, index) || delegate.isArrayElementInsertable(receiver, index);
            try {
                delegate.writeArrayElement(receiver, index, value);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasWritable : violationInvariant(receiver, index);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnsupportedTypeException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void removeArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            assert preCondition(receiver);
            boolean wasRemovable = ASSERTIONS_ENABLED && delegate.isArrayElementRemovable(receiver, index);
            try {
                delegate.removeArrayElement(receiver, index);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasRemovable : violationInvariant(receiver, index);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public long getArraySize(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                long result = delegate.getArraySize(receiver);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public boolean isArrayElementReadable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementReadable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isArrayElementModifiable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementModifiable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isArrayElementInsertable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementInsertable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isArrayElementRemovable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementRemovable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isPointer(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isPointer(receiver);
            return result;
        }

        @Override
        public void toNative(Object receiver) {
            assert preCondition(receiver);
            boolean wasPointer = delegate.isPointer(receiver);
            delegate.toNative(receiver);
            assert !wasPointer || delegate.isPointer(receiver) : violationInvariant(receiver);
        }

        @Override
        public long asPointer(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasPointer = ASSERTIONS_ENABLED && delegate.isPointer(receiver);
            try {
                long result = delegate.asPointer(receiver);
                assert wasPointer : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasPointer : violationInvariant(receiver);
                throw e;
            }
        }
    }

}
