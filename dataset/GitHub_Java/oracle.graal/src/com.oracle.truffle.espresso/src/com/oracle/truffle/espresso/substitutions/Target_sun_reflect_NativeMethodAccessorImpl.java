/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

/**
 * This substitution is merely for performance reasons, to avoid the deep-dive to native. libjava
 * hardwires {@link #invoke0} to JVM_InvokeMethod in libjvm.
 */
@EspressoSubstitutions
public final class Target_sun_reflect_NativeMethodAccessorImpl {

    /**
     * Checks argument for reflection, checking type matches and widening for primitives. Throws
     * guest IllegalArgumentException if any of the arguments does not match.
     */
    public static Object checkAndWiden(Meta meta, StaticObject arg, Klass targetKlass) {
        if (targetKlass.isPrimitive()) {
            if (StaticObject.isNull(arg)) {
                throw meta.throwExWithMessage(meta.IllegalArgumentException, meta.toGuestString("argument type mismatch"));
            }
            Klass argKlass = arg.getKlass();
            switch (targetKlass.getJavaKind()) {
                case Boolean:
                    if (argKlass == meta.Boolean) {
                        return meta.unboxBoolean(arg);
                    }
                    break; // fail

                case Byte:
                    if (argKlass == meta.Byte) {
                        return meta.unboxByte(arg);
                    }
                    break; // fail

                case Char:
                    if (argKlass == meta.Character) {
                        return meta.unboxCharacter(arg);
                    }
                    break; // fail

                case Short:
                    if (argKlass == meta.Short) {
                        return meta.unboxShort(arg);
                    }
                    if (argKlass == meta.Byte) {
                        return (short) meta.unboxByte(arg);
                    }
                    break; // fail

                case Int:
                    if (argKlass == meta.Integer) {
                        return meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.Byte) {
                        return (int) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.Character) {
                        return (int) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.Short) {
                        return (int) meta.unboxShort(arg);
                    }
                    break; // fail

                case Float:
                    if (argKlass == meta.Float) {
                        return meta.unboxFloat(arg);
                    }
                    if (argKlass == meta.Byte) {
                        return (float) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.Character) {
                        return (float) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.Short) {
                        return (float) meta.unboxShort(arg);
                    }
                    if (argKlass == meta.Integer) {
                        return (float) meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.Long) {
                        return (float) meta.unboxLong(arg);
                    }
                    break; // fail

                case Long:
                    if (argKlass == meta.Long) {
                        return meta.unboxLong(arg);
                    }
                    if (argKlass == meta.Integer) {
                        return (long) meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.Byte) {
                        return (long) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.Character) {
                        return (long) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.Short) {
                        return (long) meta.unboxShort(arg);
                    }
                    break; // fail

                case Double:
                    if (argKlass == meta.Double) {
                        return meta.unboxDouble(arg);
                    }
                    if (argKlass == meta.Float) {
                        return (double) meta.unboxFloat(arg);
                    }
                    if (argKlass == meta.Integer) {
                        return (double) meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.Byte) {
                        return (double) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.Character) {
                        return (double) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.Short) {
                        return (double) meta.unboxShort(arg);
                    }
                    if (argKlass == meta.Long) {
                        return (double) meta.unboxLong(arg);
                    }
                    break; // fail
            }
            throw meta.throwExWithMessage(meta.IllegalArgumentException, meta.toGuestString("argument type mismatch"));
        } else {
            if (StaticObject.notNull(arg) && !targetKlass.isAssignableFrom(arg.getKlass())) {
                throw meta.throwExWithMessage(meta.IllegalArgumentException, meta.toGuestString("argument type mismatch"));
            }
            return arg;
        }
    }

    /**
     * Invokes the underlying method represented by this {@code Method} object, on the specified
     * object with the specified parameters. Individual parameters are automatically unwrapped to
     * match primitive formal parameters, and both primitive and reference parameters are subject to
     * method invocation conversions as necessary.
     *
     * <p>
     * If the underlying method is static, then the specified {@code receiver} argument is ignored.
     * It may be null.
     *
     * <p>
     * If the number of formal parameters required by the underlying method is 0, the supplied
     * {@code args} array may be of length 0 or null.
     *
     * <p>
     * If the underlying method is an instance method, it is invoked using dynamic method lookup as
     * documented in The Java Language Specification, Second Edition, section 15.12.4.4; in
     * particular, overriding based on the runtime type of the target object will occur.
     *
     * <p>
     * If the underlying method is static, the class that declared the method is initialized if it
     * has not already been initialized.
     *
     * <p>
     * If the method completes normally, the value it returns is returned to the caller of invoke;
     * if the value has a primitive type, it is first appropriately wrapped in an object. However,
     * if the value has the type of an array of a primitive type, the elements of the array are
     * <i>not</i> wrapped in objects; in other words, an array of primitive type is returned. If the
     * underlying method return type is void, the invocation returns null.
     *
     * @param receiver the object the underlying method is invoked from
     * @param args the arguments used for the method call
     * @return the result of dispatching the method represented by this object on {@code receiver}
     *         with parameters {@code args}
     *
     *         IllegalAccessException if this {@code Method} object is enforcing Java language
     *         access control and the underlying method is inaccessible.
     *
     *
     *         IllegalArgumentException if the method is an instance method and the specified object
     *         argument is not an instance of the class or interface declaring the underlying method
     *         (or of a subclass or implementor thereof); if the number of actual and formal
     *         parameters differ; if an unwrapping conversion for primitive arguments fails; or if,
     *         after possible unwrapping, a parameter value cannot be converted to the corresponding
     *         formal parameter type by a method invocation conversion. // @exception
     *         InvocationTargetException if the underlying method throws an exception.
     *
     * 
     *         NullPointerException if the specified object is null and the method is an instance
     *         method. exception ExceptionInInitializerError if the initialization provoked by this
     *         method fails.
     */
    @Substitution
    public static @Host(Object.class) StaticObject invoke0(@Host(java.lang.reflect.Method.class) StaticObject guestMethod, @Host(Object.class) StaticObject receiver,
                    @Host(Object[].class) StaticObject args) {

        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        StaticObject curMethod = guestMethod;

        Method reflectedMethod = null;
        while (reflectedMethod == null) {
            reflectedMethod = (Method) ((StaticObjectImpl) curMethod).getHiddenField(Target_java_lang_Class.HIDDEN_METHOD_KEY);
            if (reflectedMethod == null) {
                curMethod = (StaticObject) meta.Method_root.get(curMethod);
            }
        }

        Klass klass = ((StaticObjectClass) meta.Method_clazz.get(guestMethod)).getMirrorKlass();
        StaticObjectArray parameterTypes = (StaticObjectArray) meta.Method_parameterTypes.get(guestMethod);
        // System.err.println(EspressoOptions.INCEPTION_NAME + " Reflective method for " +
        // reflectedMethod.getName());
        StaticObject result = callMethodReflectively(meta, receiver, args, reflectedMethod, klass, parameterTypes);
        // System.err.println(EspressoOptions.INCEPTION_NAME + " DONE Reflective method for " +
        // reflectedMethod.getName());
        return result;
    }

    public static @Host(Object.class) StaticObject callMethodReflectively(Meta meta, @Host(Object.class) StaticObject receiver, @Host(Object[].class) StaticObject args, Method reflectedMethod,
                    Klass klass, StaticObjectArray parameterTypes) {
        klass.safeInitialize();

        Method method;      // actual method to invoke
        Klass targetKlass; // target klass, receiver's klass for non-static

        if (reflectedMethod.isStatic()) {
            // Ignore receiver argument;.
            method = reflectedMethod;
            targetKlass = klass;
        } else {

            if (StaticObject.isNull(receiver)) {
                throw meta.throwEx(meta.NullPointerException);
            }

            // Check class of receiver against class declaring method.
            if (!klass.isAssignableFrom(receiver.getKlass())) {
                throw meta.throwExWithMessage(meta.IllegalArgumentException, meta.toGuestString("object is not an instance of declaring class"));
            }

            // target klass is receiver's klass
            targetKlass = receiver.getKlass();
            // no need to resolve if method is private or <init>
            if (reflectedMethod.isPrivate() || Name.INIT.equals(reflectedMethod.getName())) {
                method = reflectedMethod;
            } else {
                // resolve based on the receiver
                if (reflectedMethod.getDeclaringKlass().isInterface()) {
                    // resolve interface call
                    // Match resolution errors with those thrown due to reflection inlining
                    // Linktime resolution & IllegalAccessCheck already done by Class.getMethod()
                    method = reflectedMethod;
                    method = targetKlass.itableLookup(method.getDeclaringKlass(), method.getITableIndex());
                    if (method != null) {
                        // Check for abstract methods as well
                        if (!method.hasCode()) {
                            // new default: 65315
                            throw meta.throwExWithCause(meta.InvocationTargetException, Meta.initEx(meta.AbstractMethodError));
                        }
                    }
                    // throw EspressoError.unimplemented("reflective interface calls");
                    // This is what it should look like for interfaces.
                    // try {
                    // method = resolveInterfaceCall(klass, reflectedMethod, targetKlass, receiver);
                    // } catch (EspressoException e) {
                    // // Method resolution threw an exception; wrap it in an
                    // InvocationTargetException
                    // throw meta.throwExWithCause(meta.InvocationTargetException,
                    // e.getException());
                    // }
                } else {
                    // if the method can be overridden, we resolve using the vtable index.
                    method = reflectedMethod;
                    // VTable is live, use it
                    method = targetKlass.vtableLookup(method.getVTableIndex());
                    if (method != null) {
                        // Check for abstract methods as well
                        if (method.isAbstract()) {
                            // new default: 65315
                            throw meta.throwExWithCause(meta.InvocationTargetException, Meta.initEx(meta.AbstractMethodError));
                        }
                    }
                }
            }
        }

        // Comment from HotSpot:
        // I believe this is a ShouldNotGetHere case which requires
        // an internal vtable bug. If you ever get this please let Karen know.
        if (method == null) {
            throw meta.throwExWithMessage(meta.NoSuchMethodError, meta.toGuestString("please let Karen know"));
        }

        int argsLen = StaticObject.isNull(args) ? 0 : ((StaticObjectArray) args).length();
        final Symbol<Type>[] signature = method.getParsedSignature();

        // Check number of arguments.
        if (Signatures.parameterCount(signature, false) != argsLen) {
            throw meta.throwExWithMessage(meta.IllegalArgumentException, meta.toGuestString("wrong number of arguments !"));
        }

        Object[] adjustedArgs = new Object[argsLen];
        for (int i = 0; i < argsLen; ++i) {
            StaticObject arg = ((StaticObjectArray) args).get(i);
            StaticObject paramTypeMirror = parameterTypes.get(i);
            Klass paramKlass = ((StaticObjectClass) paramTypeMirror).getMirrorKlass();
            // Throws guest IllegallArgumentException if the parameter cannot be casted or widened.
            adjustedArgs[i] = checkAndWiden(meta, arg, paramKlass);
        }

        Object result;
        try {
            result = method.invokeDirect(receiver, adjustedArgs);
        } catch (EspressoException e) {
            if (e.getException() == null) {
                throw EspressoError.shouldNotReachHere("no wrapped exception???");
            }
            throw meta.throwExWithCause(meta.InvocationTargetException, e.getException());
        }

        if (reflectedMethod.getReturnKind() == JavaKind.Void) {
            return StaticObject.NULL;
        }
        if (reflectedMethod.getReturnKind().isPrimitive()) {
            return Meta.box(meta, result);
        }

        // Result is not void nor primitive, pass through.
        return (StaticObject) result;
    }

}
