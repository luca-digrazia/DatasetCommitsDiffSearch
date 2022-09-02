package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Interop {

    private static final InteropLibrary UNCACHED = InteropLibrary.getUncached();

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isNull(Object) 
     */
    @Substitution
    public static boolean isNull(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isNull(unwrap(receiver));
    }

    // region Boolean Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isBoolean(Object)
     */
    @Substitution
    public static boolean isBoolean(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isBoolean(unwrap(receiver));
    }

    /**
     * Returns the Java boolean value if the receiver represents a {@link #isBoolean(StaticObject)
     * boolean} like value.
     *
     * @see InteropLibrary#asBoolean(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static boolean asBoolean(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asBoolean(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Boolean Messages

    // region String Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>string</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isString(Object)  
     */
    public static boolean isString(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isString(unwrap(receiver));
    }

    /**
     * Returns the Java string value if the receiver represents a {@link #isString(StaticObject) string}
     * like value.
     *
     * @see InteropLibrary#asString(Object) 
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(String.class) StaticObject asString(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return meta.toGuestString(UNCACHED.asString(unwrap(receiver)));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion String Messages

    // region Number Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     * 
     * @see InteropLibrary#isNumber(Object) 
     */
    @Substitution
    public static boolean isNumber(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isNumber(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInByte(Object)
     */
    @Substitution
    public static boolean fitsInByte(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInByte(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInShort(Object)
     */
    @Substitution
    public static boolean fitsInShort(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInShort(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInInt(Object)
     */
    @Substitution
    public static boolean fitsInInt(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInInt(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInLong(Object)
     */
    @Substitution
    public static boolean fitsInLong(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInLong(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInFloat(Object) 
     */
    @Substitution
    public static boolean fitsInFloat(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInFloat(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInDouble(Object)
     */
    @Substitution
    public static boolean fitsInDouble(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInDouble(unwrap(receiver));
    }

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asByte(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static byte asByte(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asByte(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java short primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     * 
     * @see InteropLibrary#asShort(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static short asShort(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asShort(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java int primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asInt(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static int asInt(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asInt(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java long primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asLong(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static long asLong(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asLong(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java float primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asFloat(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static float asFloat(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asFloat(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java double primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asDouble(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static double asDouble(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asDouble(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Number Messages

    // region Exception Messages

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error}.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support {@link #throwException} as well.
     * If this method is implemented then also {@link #throwException(Object)} must be implemented.
     *
     * The following simplified {@code TryCatchNode} shows how the exceptions should be handled by
     * languages.
     *
     * @see InteropLibrary#isException(Object)
     * @since 19.3
     */
    @Substitution
    public static boolean isException(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isException(unwrap(receiver));
    }

    /**
     * Throws the receiver object as an exception of the source language, as if it was thrown by the
     * source language itself. Allows rethrowing exceptions caught by another language. If this
     * method is implemented then also {@link #isException(Object)} must be implemented.
     * <p>
     * Any interop value can be an exception value and export {@link #throwException(Object)}. The
     * exception thrown by this message must extend
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException}. In future versions this
     * contract will be enforced using an assertion.
     * 
     * @see InteropLibrary#throwException(Object) 
     * @since 19.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(RuntimeException.class) StaticObject throwException(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            throw UNCACHED.throwException(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@link ExceptionType exception type} of the receiver. Throws
     * {@code UnsupportedMessageException} when the receiver is not an exception.
     *
     * @see InteropLibrary#getExceptionType(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(typeName = "Lcom/oracle/truffle/espresso/polyglot/ExceptionType;") StaticObject getExceptionType(
            @Host(Object.class) StaticObject receiver,
            @InjectMeta Meta meta) {
        try {
            ExceptionType exceptionType = UNCACHED.getExceptionType(unwrap(receiver));
            StaticObject staticStorage = meta.com_oracle_truffle_espresso_polyglot_ExceptionType.tryInitializeAndGetStatics();
            // @formatter:off
            switch (exceptionType) {
                case EXIT          : return (StaticObject) meta.com_oracle_truffle_espresso_polyglot_ExceptionType_EXIT.get(staticStorage);
                case INTERRUPT     : return (StaticObject) meta.com_oracle_truffle_espresso_polyglot_ExceptionType_INTERRUPT.get(staticStorage);
                case RUNTIME_ERROR : return (StaticObject) meta.com_oracle_truffle_espresso_polyglot_ExceptionType_RUNTIME_ERROR.get(staticStorage);
                case PARSE_ERROR   : return (StaticObject) meta.com_oracle_truffle_espresso_polyglot_ExceptionType_PARSE_ERROR.get(staticStorage);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("Unexpected ExceptionType: ", exceptionType);
            }
            // @formatter:on
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if receiver value represents an incomplete source exception. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception} or the exception is not a {@link ExceptionType#PARSE_ERROR}.
     *
     * @see InteropLibrary#isExceptionIncompleteSource(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static boolean isExceptionIncompleteSource(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.isExceptionIncompleteSource(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link #isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. A return value zero indicates that the execution of the
     * application was successful, a non-zero value that it failed. The individual interpretation of
     * non-zero values depends on the application.
     *
     * @see InteropLibrary#getExceptionExitStatus(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static int getExceptionExitStatus(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.getExceptionExitStatus(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception with an attached internal cause.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see InteropLibrary#hasExceptionCause(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionCause(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionCause(unwrap(receiver));
    }

    /**
     * Returns the internal cause of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no internal cause. The
     * return value of this message is guaranteed to return <code>true</code> for
     * {@link #isException(Object)}.
     *
     * @see InteropLibrary#getExceptionCause(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionCause(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object cause = UNCACHED.getExceptionCause(unwrap(receiver));
            assert UNCACHED.isException(cause);
            assert !UNCACHED.isNull(cause);
            if (cause instanceof StaticObject) {
                return (StaticObject) cause; // Already typed, do not re-type.
            }
            // Wrap foreign object as ForeignException.
            return StaticObject.createForeign(meta.com_oracle_truffle_espresso_polyglot_ForeignException, cause, InteropLibrary.getUncached(cause));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }
    /**
     * Returns {@code true} if the receiver is an exception that has an exception message. Invoking
     * this message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#hasExceptionMessage(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionMessage(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionMessage(unwrap(receiver));
    }

    /**
     * Returns exception message of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an exception or has no exception message.
     * The return value of this message is guaranteed to return <code>true</code> for
     * {@link #isString(Object)}.
     *
     * @see InteropLibrary#getExceptionMessage(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionMessage(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object message = UNCACHED.getExceptionMessage(unwrap(receiver));
            assert UNCACHED.isString(message);
            // TODO(peterssen): Maybe do not convert to String right away.
            return meta.toGuestString(UNCACHED.asString(message));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception and has a stack trace. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#hasExceptionStackTrace(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionStackTrace(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionStackTrace(unwrap(receiver));
    }

    /**
     * Returns the exception stack trace of the receiver that is of type exception. Returns an
     * {@link #hasArrayElements(Object) array} of objects with potentially
     * {@link #hasExecutableName(Object) executable name}, {@link #hasDeclaringMetaObject(Object)
     * declaring meta object} and {@link #hasSourceLocation(Object) source location} of the caller.
     * Throws {@code UnsupportedMessageException} when the receiver is not an
     * {@link #isException(Object) exception} or has no stack trace. Invoking this message or
     * accessing the stack trace elements array must not cause any observable side-effects.
     *
     * @see InteropLibrary#getExceptionStackTrace(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionStackTrace(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        throw new UnsupportedOperationException("unimplemented");
//        try {
//            return UNCACHED.getExceptionStackTrace(unwrap(receiver));
//        } catch (InteropException e) {
//            throw throwInteropException(e, meta);
//        }
    }

    // endregion Exception Messages

    private static Object unwrap(StaticObject receiver) {
        return receiver.isForeignObject() ? receiver.rawForeignObject() : receiver;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static RuntimeException createInteropException(ObjectKlass exceptionKlass, InteropException cause) {
        StaticObject exception = Meta.initExceptionWithMessage(exceptionKlass, cause.getMessage());
        EspressoException espressoException = EspressoException.wrap(exception);
        espressoException.initCause(cause);
        return espressoException;
    }

    private static RuntimeException throwInteropException(InteropException e, Meta meta) {
        if (e instanceof UnsupportedMessageException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_UnsupportedMessageException, e);
        }
        if (e instanceof UnknownIdentifierException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_UnknownIdentifierException, e);
        }
        if (e instanceof ArityException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_ArityException, e);
        }
        if (e instanceof UnsupportedTypeException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_UnsupportedTypeException, e);
        }
        if (e instanceof InvalidArrayIndexException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException, e);
        }
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.unexpected("Unexpected interop exception: ", e);
    }
}
