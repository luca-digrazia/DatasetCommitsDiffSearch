/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.polyglot;

/**
 *
 */
public final class Interop {

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @since 19.0
     */
    public native static boolean isNull(Object receiver);

    // region Boolean Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #asBoolean(Object)
     * @since 19.0
     */
    public native static boolean isBoolean(Object receiver);

    /**
     * Returns the Java boolean value if the receiver represents a {@link #isBoolean(Object)
     * boolean} like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isBoolean(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isBoolean(Object)
     * @since 19.0
     */
    public native static boolean asBoolean(Object receiver) throws UnsupportedMessageException;

    // endregion Boolean Messages

    // region String Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>string</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #asString(Object)
     * @since 19.0
     */
    public native static boolean isString(Object receiver);

    /**
     * Returns the Java string value if the receiver represents a {@link #isString(Object) string}
     * like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isString(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isString(Object)
     * @since 19.0
     */
    public native static String asString(Object receiver) throws UnsupportedMessageException;

    // endregion String Messages

    // region Number Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #fitsInByte(Object)
     * @see #fitsInShort(Object)
     * @see #fitsInInt(Object)
     * @see #fitsInLong(Object)
     * @see #fitsInFloat(Object)
     * @see #fitsInDouble(Object)
     * @see #asByte(Object)
     * @see #asShort(Object)
     * @see #asInt(Object)
     * @see #asLong(Object)
     * @see #asFloat(Object)
     * @see #asDouble(Object)
     * @since 19.0
     */
    public native static boolean isNumber(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asByte(Object)
     * @since 19.0
     */
    public native static boolean fitsInByte(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asShort(Object)
     * @since 19.0
     */
    public native static boolean fitsInShort(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asInt(Object)
     * @since 19.0
     */
    public native static boolean fitsInInt(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asLong(Object)
     * @since 19.0
     */
    public native static boolean fitsInLong(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asFloat(Object)
     * @since 19.0
     */
    public native static boolean fitsInFloat(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asDouble(Object)
     * @since 19.0
     */
    public native static boolean fitsInDouble(Object receiver);

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInByte(Object)
     * @since 19.0
     */
    public native static byte asByte(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java short primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInShort(Object)
     * @since 19.0
     */
    public native static short asShort(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java int primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInInt(Object)
     * @since 19.0
     */
    public native static int asInt(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java long primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInLong(Object)
     * @since 19.0
     */
    public native static long asLong(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java float primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInFloat(Object)
     * @since 19.0
     */
    public native static float asFloat(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java double primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInDouble(Object)
     * @since 19.0
     */
    public native static double asDouble(Object receiver) throws UnsupportedMessageException;

    // endregion Number Messages

    // region Exception Messages

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support {@link #throwException} as well.
     * If this method is implemented then also {@link #throwException(Object)} must be implemented.
     *
     * @see #throwException(Object)
     * @since 19.3
     */
    public static native boolean isException(Object receiver);

    /**
     * Throws the receiver object as an exception of the source language, as if it was thrown by the
     * source language itself. Allows rethrowing exceptions caught by another language. If this
     * method is implemented then also {@link #isException(Object)} must be implemented.
     * <p>
     * Any interop value can be an exception value and export {@link #throwException(Object)}.
     *
     * @throws UnsupportedMessageException if and only if {@link #isException(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isException(Object)
     * @since 19.3
     */
    public static native RuntimeException throwException(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@link ExceptionType exception type} of the receiver. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception}.
     *
     * @see #isException(Object)
     * @see ExceptionType
     * @since 20.3
     */
    public static native ExceptionType getExceptionType(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if receiver value represents an incomplete source exception. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception} or the exception is not a {@link ExceptionType#PARSE_ERROR}.
     *
     * @see #isException(Object)
     * @see #getExceptionType(Object)
     * @since 20.3
     */
    public static native boolean isExceptionIncompleteSource(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link #isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. A return value zero indicates that the execution of the
     * application was successful, a non-zero value that it failed. The individual interpretation of
     * non-zero values depends on the application.
     *
     * @see #isException(Object)
     * @see #getExceptionType(Object)
     * @see ExceptionType
     * @since 20.3
     */
    public static native int getExceptionExitStatus(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if the receiver is an exception with an attached internal cause.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see #isException(Object)
     * @see #getExceptionCause(Object)
     * @since 20.3
     */
    public static native boolean hasExceptionCause(Object receiver);

    /**
     * Returns the internal cause of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no internal cause. The
     * return value of this message is guaranteed to return <code>true</code> for
     * {@link #isException(Object)}.
     *
     *
     * @see #isException(Object)
     * @see #hasExceptionCause(Object)
     * @since 20.3
     */
    public static native Object getExceptionCause(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if the receiver is an exception that has an exception message. Invoking
     * this message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #isException(Object)
     * @see #getExceptionMessage(Object)
     * @since 20.3
     */
    public static native boolean hasExceptionMessage(Object receiver);

    /**
     * Returns exception message of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no exception message.
     * The return value of this message is guaranteed to return <code>true</code> for
     * {@link #isString(Object)}.
     *
     * @see #isException(Object)
     * @see #hasExceptionMessage(Object)
     * @since 20.3
     */
    public static native Object getExceptionMessage(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if the receiver is an exception and has a stack trace. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #isException(Object)
     * @see #getExceptionStackTrace(Object)
     * @since 20.3
     */
    public static native boolean hasExceptionStackTrace(Object receiver);

    /**
     * Returns the exception stack trace of the receiver that is of type exception. Returns an
     * {@link #hasArrayElements(Object) array} of objects with potentially
     * {@link #hasExecutableName(Object) executable name}, {@link #hasDeclaringMetaObject(Object)
     * declaring meta object} and {@link #hasSourceLocation(Object) source location} of the caller.
     * Throws {@code UnsupportedMessageException} when the receiver is not an
     * {@link #isException(Object) exception} or has no stack trace. Invoking this message or
     * accessing the stack trace elements array must not cause any observable side-effects.
     *
     * @see #isException(Object)
     * @see #hasExceptionStackTrace(Object)
     * @since 20.3
     */
    public static native Object getExceptionStackTrace(Object receiver) throws UnsupportedMessageException;

    // endregion Exception Messages

    // region Array Messages

    /**
     * Returns <code>true</code> if the receiver may have array elements. Therefore, At least one of
     * {@link #readArrayElement(Object, long)}, {@link #writeArrayElement(Object, long, Object)},
     * {@link #removeArrayElement(Object, long)} must not throw {#link
     * {@link UnsupportedMessageException}. For example, the contents of an array or list
     * datastructure could be interpreted as array elements. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getArraySize(Object)
     * @since 19.0
     */
    public static native boolean hasArrayElements(Object receiver);

    /**
     * Reads the value of an array element by index. This method must have not observable
     * side-effect.
     *
     * @throws UnsupportedMessageException when the receiver does not support reading at all. An
     *             empty receiver with no readable array elements supports the read operation (even
     *             though there is nothing to read), therefore it throws
     *             {@link UnknownIdentifierException} for all arguments instead.
     * @throws InvalidArrayIndexException if the given index is not
     *             {@link #isArrayElementReadable(Object, long) readable}, e.g. when the index is
     *             invalid or the index is out of bounds.
     * @since 19.0
     */
    public static native Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException;

    /**
     * Returns the array size of the receiver.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasArrayElements(Object)} returns
     *             <code>false</code>.
     * @since 19.0
     */
    public static native long getArraySize(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns <code>true</code> if a given array element is {@link #readArrayElement(Object, long)
     * readable}. This method may only return <code>true</code> if {@link #hasArrayElements(Object)}
     * returns <code>true</code> as well. Invoking this message does not cause any observable
     * side-effects. Returns <code>false</code> by default.
     *
     * @see #readArrayElement(Object, long)
     * @since 19.0
     */
    public static native boolean isArrayElementReadable(Object receiver, long index);

    /**
     * Writes the value of an array element by index. Writing an array element is allowed if is
     * existing and {@link #isArrayElementModifiable(Object, long) modifiable}, or not existing and
     * {@link #isArrayElementInsertable(Object, long) insertable}.
     *
     * This method must have not observable side-effects other than the changed array element.
     *
     * @throws UnsupportedMessageException when the receiver does not support writing at all, e.g.
     *             when it is immutable.
     * @throws InvalidArrayIndexException if the given index is not
     *             {@link #isArrayElementInsertable(Object, long) insertable} nor
     *             {@link #isArrayElementModifiable(Object, long) modifiable}, e.g. when the index
     *             is invalid or the index is out of bounds and the array does not support growing.
     * @throws UnsupportedTypeException if the provided value type is not allowed to be written.
     * @since 19.0
     */
    public static native void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException;

    /**
     * Remove an array element from the receiver object. Removing member is allowed if the array
     * element is {@link #isArrayElementRemovable(Object, long) removable}. This method may only
     * return <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as
     * well and {@link #isArrayElementInsertable(Object, long)} returns <code>false</code>.
     *
     * This method does not have observable side-effects other than the removed array element and
     * shift of remaining elements. If shifting is not supported then the array might allow only
     * removal of last element.
     *
     * @throws UnsupportedMessageException when the receiver does not support removing at all, e.g.
     *             when it is immutable.
     * @throws InvalidArrayIndexException if the given index is not
     *             {@link #isArrayElementRemovable(Object, long) removable}, e.g. when the index is
     *             invalid, the index is out of bounds, or the array does not support shifting of
     *             remaining elements.
     * @see #isArrayElementRemovable(Object, long)
     * @since 19.0
     */
    public static native void removeArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException;

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link #writeArrayElement(Object, long, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as well and
     * {@link #isArrayElementInsertable(Object, long)} returns <code>false</code>. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeArrayElement(Object, long, Object)
     * @see #isArrayElementInsertable(Object, long)
     * @since 19.0
     */
    public static native boolean isArrayElementModifiable(Object receiver, long index);

    /**
     * Returns <code>true</code> if a given array element index is not existing and
     * {@link #writeArrayElement(Object, long, Object) insertable}. This method may only return
     * <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as well and
     * {@link #isArrayElementExisting(Object, long)}} returns <code>false</code>. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeArrayElement(Object, long, Object)
     * @see #isArrayElementModifiable(Object, long)
     * @since 19.0
     */
    public static native boolean isArrayElementInsertable(Object receiver, long index);

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link #removeArrayElement(Object, long) removable}. This method may only return
     * <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as well and
     * {@link #isArrayElementInsertable(Object, long)}} returns <code>false</code>. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #removeArrayElement(Object, long)
     * @since 19.0
     */
    public static native boolean isArrayElementRemovable(Object receiver, long index);

    /**
     * Returns true if the array element is {@link #isArrayElementModifiable(Object, long)
     * modifiable} or {@link #isArrayElementInsertable(Object, long) insertable}.
     *
     * @since 19.0
     */
    public static boolean isArrayElementWritable(Object receiver, long index) {
        return isArrayElementModifiable(receiver, index) || isArrayElementInsertable(receiver, index);
    }

    /**
     * Returns true if the array element is existing. An array element is existing if it is,
     * {@link #isArrayElementModifiable(Object, long) modifiable},
     * {@link #isArrayElementReadable(Object, long) readable} or
     * {@link #isArrayElementRemovable(Object, long) removable}.
     *
     * @since 19.0
     */
    public static boolean isArrayElementExisting(Object receiver, long index) {
        return isArrayElementModifiable(receiver, index) || isArrayElementReadable(receiver, index) || isArrayElementRemovable(receiver, index);
    }

    // endregion Array Messages

    // region MetaObject Messages

    /**
     * Returns <code>true</code> if the receiver value has a metaobject associated. The metaobject
     * represents a description of the object, reveals its kind and its features. Some information
     * that a metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. Should return <code>false</code> when no metaobject is known for this type.
     * Returns <code>false</code> by default.
     * <p>
     * An example, for Java objects the returned metaobject is the {@link Object#getClass() class}
     * instance. In JavaScript this could be the function or class that is associated with the
     * object.
     * <p>
     * Metaobjects for primitive values or values of other languages may be provided using language
     * views. While an object is associated with a metaobject in one language, the metaobject might
     * be a different when viewed from another language.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaObject(Object)} must be implemented.
     *
     * @see #getMetaObject(Object)
     * @see #isMetaObject(Object)
     * @since 20.1
     */
    public static native boolean hasMetaObject(Object receiver);

    /**
     * Returns the metaobject that is associated with this value. The metaobject represents a
     * description of the object, reveals its kind and its features. Some information that a
     * metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. When no metaobject is known for this type. Throws
     * {@link UnsupportedMessageException} by default.
     * <p>
     * The returned object must return <code>true</code> for {@link #isMetaObject(Object)} and
     * provide implementations for {@link #getMetaSimpleName(Object)},
     * {@link #getMetaQualifiedName(Object)}, and {@link #isMetaInstance(Object, Object)}. For all
     * values with metaobjects it must at hold that
     * <code>isMetaInstance(getMetaObject(value), value) ==
     * true</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasMetaObject(Object)} must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     *
     * @see #hasMetaObject(Object)
     * @since 20.1
     */
    public static native Object getMetaObject(Object receiver) throws UnsupportedMessageException;

    /**
     * Converts the receiver to a human readable {@link #isString(Object) string}. Each language may
     * have special formating conventions - even primitive values may not follow the traditional
     * Java rules. The format of the returned string is intended to be interpreted by humans not
     * machines and should therefore not be relied upon by machines. By default the receiver class
     * name and its {@link System#identityHashCode(Object) identity hash code} is used as string
     * representation.
     * <p>
     * String representations for primitive values or values of other languages may be provided
     * using language views. It is common that languages provide different string representations
     * for primitive and foreign values. To convert the result value to a Java string use
     * {@link #asString(Object)}.
     *
     * @param allowSideEffects whether side-effects are allowed in the production of the string.
     * @since 20.1
     */
    public static native Object toDisplayString(Object receiver, boolean allowSideEffects);

    /**
     * Converts the receiver to a human readable {@link #isString(Object) string} of the language.
     * Short-cut for <code>{@link #toDisplayString(Object) toDisplayString}(true)</code>.
     *
     * @see #toDisplayString(Object, boolean)
     * @since 20.1
     */
    public static Object toDisplayString(Object receiver) {
        return toDisplayString(receiver, true);
    }

    /**
     * Returns <code>true</code> if the receiver value represents a metaobject. Metaobjects may be
     * values that naturally occur in a language or they may be returned by
     * {@link #getMetaObject(Object)}. A metaobject represents a description of the object, reveals
     * its kind and its features. If a receiver is a metaobject it is often also
     * {@link #isInstantiable(Object) instantiable}, but this is not a requirement.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * In JavaScript any function instance is a metaobject. For example, the metaobject of a
     * JavaScript class is the associated constructor function.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaQualifiedName(Object)}, {@link #getMetaSimpleName(Object)} and
     * {@link #isMetaInstance(Object, Object)} must be implemented as well.
     *
     * @since 20.1
     */
    public static native boolean isMetaObject(Object receiver);

    /**
     * Returns the qualified name of a metaobject as {@link #isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The qualified name of a Java class includes the package name
     * and its class name. JavaScript does not have the notion of qualified name and therefore
     * returns the {@link #getMetaSimpleName(Object) simple name} instead.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isMetaObject(Object)} must be implemented as well.
     *
     * @throws UnsupportedMessageException if and only if {@link #isMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     *
     * @since 20.1
     */
    public static native Object getMetaQualifiedName(Object metaObject) throws UnsupportedMessageException;

    /**
     * Returns the simple name of a metaobject as {@link #isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The simple name of a Java class is the class name.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isMetaObject(Object)} must be implemented as well.
     *
     * @throws UnsupportedMessageException if and only if {@link #isMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     *
     * @since 20.1
     */
    public static native Object getMetaSimpleName(Object metaObject) throws UnsupportedMessageException;

    /**
     * Returns <code>true</code> if the given instance is of the provided receiver metaobject, else
     * <code>false</code>.
     * <p>
     * <b>Sample interpretations:</b> A Java object is an instance of its returned
     * {@link Object#getClass() class}.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isMetaObject(Object)} must be implemented as well.
     *
     * @param instance the instance object to check.
     * @throws UnsupportedMessageException if and only if {@link #isMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     * @since 20.1
     */
    public static native boolean isMetaInstance(Object receiver, Object instance) throws UnsupportedMessageException;

    // endregion MetaObject Messages

    // region Identity Messages

    // isIdenticalOrUndefined is not exposed to keep the API tidy.

    /**
     * Returns <code>true</code> if two values represent the the identical value, else
     * <code>false</code>. Two values are identical if and only if they have specified identity
     * semantics in the target language and refer to the identical instance.
     * <p>
     * By default, an interop value does not support identical comparisons, and will return
     * <code>false</code> for any invocation of this method. Use {@link #hasIdentity(Object)} to
     * find out whether a receiver supports identity comparisons.
     * <p>
     * This method has the following properties:
     * <ul>
     * <li>It is <b>not</b> <i>reflexive</i>: for any value {@code x},
     * {@code Interop.isIdentical(x, x)} may return {@code false} if the object does not support
     * identity, else <code>true</code>. This method is reflexive if {@code x} supports identity. A
     * value supports identity if {@code Interop.isIdentical(x, x)} returns <code>true</code>. The
     * method {@link #hasIdentity(Object)} may be used to document this intent explicitly.
     * <li>It is <i>symmetric</i>: for any values {@code x} and {@code y},
     * {@code Interop.isIdentical(x, y)} returns {@code true} if and only if
     * {@code Interop.isIdentical(y, x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any values {@code x}, {@code y}, and {@code z}, if
     * {@code Interop.isIdentical(x, y)} returns {@code true} and {@code Interop.isIdentical(y, z)}
     * returns {@code true}, then {@code Interop.isIdentical(x, z)} returns {@code true}.
     * <li>It is <i>consistent</i>: for any values {@code x} and {@code y}, multiple invocations of
     * {@code Interop.isIdentical(x, y)} consistently returns {@code true} or consistently return
     * {@code false}.
     * </ul>
     * <p>
     * Note that the target language identical semantics typically does not map directly to interop
     * identical implementation. Instead target language identity is specified by the language
     * operation, may take multiple other rules into account and may only fallback to interop
     * identical for values without dedicated interop type. For example, in many languages
     * primitives like numbers or strings may be identical, in the target language sense, still
     * identity can only be exposed for objects and non-primitive values. Primitive values like
     * {@link Integer} can never be interop identical to other boxed language integers as this would
     * violate the symmetric property.
     * <p>
     * This method performs double dispatch by forwarding calls to isIdenticalOrUndefined with
     * receiver and other value first and then with reversed parameters if the result was undefined.
     * This allows the receiver and the other value to negotiate identity semantics.
     * <p>
     * This method must not cause any observable side-effects.
     *
     * @since 20.2
     */
    public static native boolean isIdentical(Object receiver, Object other);

    /**
     * Returns <code>true</code> if and only if the receiver specifies identity, else
     * <code>false</code>. This method is a short-cut for
     * <code>isIdentical(receiver, receiver)</code>. This message cannot be exported. To add
     * identity support to the receiver export isIdenticalOrUndefined(Object, Object) instead.
     *
     * @since 20.2
     */
    public static boolean hasIdentity(Object receiver) {
        return isIdentical(receiver, receiver);
    }

    /**
     * Returns an identity hash code for the receiver if it has {@link #hasIdentity(Object)
     * identity}. If the receiver has no identity then an {@link UnsupportedMessageException} is
     * thrown. The identity hash code may be used by languages to store foreign values with identity
     * in an identity hash map.
     * <p>
     * <ul>
     * <li>Whenever it is invoked on the same object more than once during an execution of a guest
     * context, the identityHashCode method must consistently return the same integer. This integer
     * need not remain consistent from one execution context of a guest application to another
     * execution context of the same application.
     * <li>If two objects are the same according to the {@link #isIdentical(Object, Object)}
     * message, then calling the identityHashCode method on each of the two objects must produce the
     * same integer result.
     * <li>As much as is reasonably practical, the identityHashCode message does return distinct
     * integers for objects that are not the same.
     * </ul>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also isIdenticalOrUndefined(Object, Object) must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasIdentity(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isIdentical(Object, Object)
     * @since 20.2
     */
    public static native int identityHashCode(Object receiver) throws UnsupportedMessageException;

    // endregion Identity Messages

    // region Member Messages

    /**
     * Returns <code>true</code> if the receiver may have members. Therefore, at least one of
     * {@link #readMember(Object, String)}, {@link #writeMember(Object, String, Object)},
     * {@link #removeMember(Object, String)}, {@link #invokeMember(Object, String, Object...)} must
     * not throw {@link UnsupportedMessageException}. Members are structural elements of a class.
     * For example, a method or field is a member of a class. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getMembers(Object, boolean)
     * @see #isMemberReadable(Object, String)
     * @see #isMemberModifiable(Object, String)
     * @see #isMemberInvocable(Object, String)
     * @see #isMemberInsertable(Object, String)
     * @see #isMemberRemovable(Object, String)
     * @see #readMember(Object, String)
     * @see #writeMember(Object, String, Object)
     * @see #removeMember(Object, String)
     * @see #invokeMember(Object, String, Object...)
     * @since 19.0
     */
    public static native boolean hasMembers(Object receiver);

    /**
     * Returns an array of member name strings. The returned value must return <code>true</code> for
     * {@link #hasArrayElements(Object)} and every array element must be of type
     * {@link #isString(Object) string}. The member elements may also provide additional information
     * like {@link #getSourceLocation(Object) source location} in case of {@link #isScope(Object)
     * scope} variables, etc.
     * <p>
     * If the includeInternal argument is <code>true</code> then internal member names are returned
     * as well. Internal members are implementation specific and should not be exposed to guest
     * language application. An example of internal members are internal slots in ECMAScript.
     *
     * @throws UnsupportedMessageException if and only if the receiver does not have any
     *             {@link #hasMembers(Object) members}.
     * @see #hasMembers(Object)
     * @since 19.0
     */
    public static native Object getMembers(Object receiver, boolean includeInternal) throws UnsupportedMessageException;

    /**
     * Short-cut for {@link #getMembers(Object) getMembers(receiver, false)}. Invoking this message
     * does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver has no
     *             {@link #hasMembers(Object) members}.
     * @see #getMembers(Object, boolean)
     * @since 19.0
     */
    public static Object getMembers(Object receiver) throws UnsupportedMessageException {
        return getMembers(receiver, false);
    }

    /**
     * Returns <code>true</code> if a given member is {@link #readMember(Object, String) readable}.
     * This method may only return <code>true</code> if {@link #hasMembers(Object)} returns
     * <code>true</code> as well and {@link #isMemberInsertable(Object, String)} returns
     * <code>false</code>. Invoking this message does not cause any observable side-effects. Returns
     * <code>false</code> by default.
     *
     * @see #readMember(Object, String)
     * @since 19.0
     */
    public static native boolean isMemberReadable(Object receiver, String member);

    /**
     * Reads the value of a given member. If the member is {@link #isMemberReadable(Object, String)
     * readable} and {@link #isMemberInvocable(Object, String) invocable} then the result of reading
     * the member is {@link #isExecutable(Object) executable} and is bound to this receiver. This
     * method must have not observable side-effects unless
     * {@link #hasMemberReadSideEffects(Object, String)} returns <code>true</code>.
     *
     * @throws UnsupportedMessageException if when the receiver does not support reading at all. An
     *             empty receiver with no readable members supports the read operation (even though
     *             there is nothing to read), therefore it throws {@link UnknownIdentifierException}
     *             for all arguments instead.
     * @throws UnknownIdentifierException if the given member is not
     *             {@link #isMemberReadable(Object, String) readable}, e.g. when the member with the
     *             given name does not exist.
     * @see #hasMemberReadSideEffects(Object, String)
     * @since 19.0
     */
    public static native Object readMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException;

    /**
     * Returns <code>true</code> if a given member is existing and
     * {@link #writeMember(Object, String, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    public static native boolean isMemberModifiable(Object receiver, String member);

    /**
     * Returns <code>true</code> if a given member is not existing and
     * {@link #writeMember(Object, String, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberExisting(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    public static native boolean isMemberInsertable(Object receiver, String member);

    /**
     * Writes the value of a given member. Writing a member is allowed if is existing and
     * {@link #isMemberModifiable(Object, String) modifiable}, or not existing and
     * {@link #isMemberInsertable(Object, String) insertable}.
     *
     * This method must have not observable side-effects other than the changed member unless
     * {@link #hasMemberWriteSideEffects(Object, String) side-effects} are allowed.
     *
     * @throws UnsupportedMessageException when the receiver does not support writing at all, e.g.
     *             when it is immutable.
     * @throws UnknownIdentifierException if the given member is not
     *             {@link #isMemberModifiable(Object, String) modifiable} nor
     *             {@link #isMemberInsertable(Object, String) insertable}.
     * @throws UnsupportedTypeException if the provided value type is not allowed to be written.
     * @see #hasMemberWriteSideEffects(Object, String)
     * @since 19.0
     */
    public static native void writeMember(Object receiver, String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;

    /**
     * Returns <code>true</code> if a given member is existing and removable. This method may only
     * return <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #removeMember(Object, String)
     * @since 19.0
     */
    public static native boolean isMemberRemovable(Object receiver, String member);

    /**
     * Removes a member from the receiver object. Removing member is allowed if is
     * {@link #isMemberRemovable(Object, String) removable}.
     *
     * This method does not have not observable side-effects other than the removed member.
     *
     * @throws UnsupportedMessageException when the receiver does not support removing at all, e.g.
     *             when it is immutable.
     * @throws UnknownIdentifierException if the given member is not
     *             {@link #isMemberRemovable(Object, String)} removable}, e.g. the receiver does not
     *             have a member with the given name.
     * @see #isMemberRemovable(Object, String)
     * @since 19.0
     */
    public static native void removeMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException;

    /**
     * Returns <code>true</code> if a given member is invocable. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #invokeMember(Object, String, Object...)
     * @since 19.0
     */
    public static native boolean isMemberInvocable(Object receiver, String member);

    /**
     * Invokes a member for a given receiver and arguments.
     *
     * @throws UnknownIdentifierException if the given member does not exist or is not
     *             {@link #isMemberInvocable(Object, String) invocable}.
     * @throws UnsupportedTypeException if one of the arguments is not compatible to the executable
     *             signature. The exception is thrown on best effort basis, dynamic languages may
     *             throw their own exceptions if the arguments are wrong.
     * @throws ArityException if the number of expected arguments does not match the number of
     *             actual arguments.
     * @throws UnsupportedMessageException when the receiver does not support invoking at all, e.g.
     *             when storing executable members is not allowed.
     * @see #isMemberInvocable(Object, String)
     * @since 19.0
     */
    public static native Object invokeMember(Object receiver, String member, Object... arguments)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException;

    /**
     * Returns true if a member is internal. Internal members are not enumerated by
     * {@link #getMembers(Object, boolean)} by default. Internal members are only relevant to guest
     * language implementations and tools, but not to guest applications or embedders. An example of
     * internal members are internal slots in ECMAScript. Invoking this message does not cause any
     * observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getMembers(Object, boolean)
     * @since 19.0
     */
    public static native boolean isMemberInternal(Object receiver, String member);

    /**
     * Returns true if the member is {@link #isMemberModifiable(Object, String) modifiable} or
     * {@link #isMemberInsertable(Object, String) insertable}.
     *
     * @since 19.0
     */
    public static boolean isMemberWritable(Object receiver, String member) {
        return isMemberModifiable(receiver, member) || isMemberInsertable(receiver, member);
    }

    /**
     * Returns true if the member is existing. A member is existing if it is
     * {@link #isMemberModifiable(Object, String) modifiable},
     * {@link #isMemberReadable(Object, String) readable}, {@link #isMemberRemovable(Object, String)
     * removable} or {@link #isMemberInvocable(Object, String) invocable}.
     *
     * @since 19.0
     */
    public static boolean isMemberExisting(Object receiver, String member) {
        return isMemberReadable(receiver, member) || isMemberModifiable(receiver, member) || isMemberRemovable(receiver, member) || isMemberInvocable(receiver, member);
    }

    /**
     * Returns <code>true</code> if reading a member may cause a side-effect. Invoking this message
     * does not cause any observable side-effects. A member read does not cause any side-effects by
     * default.
     * <p>
     * For instance in JavaScript a property read may have side-effects if the property has a getter
     * function.
     *
     * @see #readMember(Object, String)
     * @since 19.0
     */
    public static native boolean hasMemberReadSideEffects(Object receiver, String member);

    /**
     * Returns <code>true</code> if writing a member may cause a side-effect, besides the write
     * operation of the member. Invoking this message does not cause any observable side-effects. A
     * member write does not cause any side-effects by default.
     * <p>
     * For instance in JavaScript a property write may have side-effects if the property has a
     * setter function.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    public static native boolean hasMemberWriteSideEffects(Object receiver, String member);

    // endregion Member Messages
}
