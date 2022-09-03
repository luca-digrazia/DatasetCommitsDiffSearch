/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

final class JavaInteropReflect {
    static final Object[] EMPTY = {};

    private JavaInteropReflect() {
    }

    @CompilerDirectives.TruffleBoundary
    static Class<?> findInnerClass(Class<?> clazz, String name) {
        if (Modifier.isPublic(clazz.getModifiers())) {
            for (Class<?> t : clazz.getClasses()) {
                // no support for non-static type members now
                if (isStaticTypeOrInterface(t) && t.getSimpleName().equals(name)) {
                    return t;
                }
            }
        }
        return null;
    }

    static boolean isField(JavaObject object, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        final boolean onlyStatic = object.isClass();
        return classDesc.lookupField(name, onlyStatic) != null;
    }

    static boolean isMethod(JavaObject object, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        final boolean onlyStatic = object.isClass();
        return classDesc.lookupMethod(name, onlyStatic) != null;
    }

    static boolean isInternalMethod(JavaObject object, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        final boolean onlyStatic = object.isClass();
        JavaMethodDesc method = classDesc.lookupMethod(name, onlyStatic);
        return method != null && method.isInternal();
    }

    static boolean isMemberType(JavaObject object, String name) {
        final boolean onlyStatic = object.isClass();
        if (!onlyStatic) {
            // no support for non-static members now
            return false;
        }
        Class<?> clazz = object.clazz;
        if (Modifier.isPublic(clazz.getModifiers())) {
            for (Class<?> t : clazz.getClasses()) {
                if (isStaticTypeOrInterface(t) && t.getSimpleName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isJNIName(String name) {
        return name.contains("__");
    }

    @CompilerDirectives.TruffleBoundary
    static boolean isApplicableByArity(JavaMethodDesc method, int nArgs) {
        if (method instanceof SingleMethodDesc) {
            return nArgs == ((SingleMethodDesc) method).getParameterCount() ||
                            ((SingleMethodDesc) method).isVarArgs() && nArgs >= ((SingleMethodDesc) method).getParameterCount() - 1;
        } else {
            SingleMethodDesc[] overloads = ((OverloadedMethodDesc) method).getOverloads();
            for (SingleMethodDesc overload : overloads) {
                if (isApplicableByArity(overload, nArgs)) {
                    return true;
                }
            }
            return false;
        }
    }

    @CompilerDirectives.TruffleBoundary
    static JavaMethodDesc findMethod(JavaObject object, String name) {
        if (TruffleOptions.AOT) {
            return null;
        }

        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        JavaMethodDesc foundMethod = classDesc.lookupMethod(name, object.isClass());
        if (foundMethod == null && isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, object.isClass());
        }
        return foundMethod;
    }

    @CompilerDirectives.TruffleBoundary
    static JavaFieldDesc findField(JavaObject receiver, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(receiver.clazz);
        final boolean onlyStatic = receiver.isClass();
        return classDesc.lookupField(name, onlyStatic);
    }

    @CompilerDirectives.TruffleBoundary
    static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function, Object languageContext) {
        assert JavaInterop.isJavaFunctionInterface(functionalType);
        Method functionalInterfaceMethod = JavaInterop.functionalInterfaceMethod(functionalType);
        final FunctionProxyHandler handler = new FunctionProxyHandler(function, functionalInterfaceMethod, languageContext);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
    }

    @CompilerDirectives.TruffleBoundary
    static TruffleObject asTruffleViaReflection(Object obj, Object languageContext) {
        if (obj instanceof TruffleFunction) {
            return ((TruffleFunction<?, ?>) obj).guestObject;
        } else if (Proxy.isProxyClass(obj.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (h instanceof FunctionProxyHandler) {
                return ((FunctionProxyHandler) h).functionObj;
            } else if (h instanceof ObjectProxyHandler) {
                return ((ObjectProxyHandler) h).obj;
            }
        }
        return new JavaObject(obj, obj.getClass(), languageContext);
    }

    static Object newProxyInstance(Class<?> clazz, TruffleObject obj, Object languageContext) throws IllegalArgumentException {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new ObjectProxyHandler(obj, languageContext, clazz));
    }

    static boolean isStaticTypeOrInterface(Class<?> t) {
        // anonymous classes are private, they should be eliminated elsewhere
        return Modifier.isPublic(t.getModifiers()) && (t.isInterface() || t.isEnum() || Modifier.isStatic(t.getModifiers()));
    }

    @CompilerDirectives.TruffleBoundary
    static String[] findUniquePublicMemberNames(Class<?> clazz, boolean onlyInstance, boolean includeInternal) throws SecurityException {
        JavaClassDesc classDesc = JavaClassDesc.forClass(clazz);
        Collection<String> names = new LinkedHashSet<>();
        names.addAll(classDesc.getFieldNames(!onlyInstance));
        names.addAll(classDesc.getMethodNames(!onlyInstance, includeInternal));
        if (!onlyInstance) {
            if (Modifier.isPublic(clazz.getModifiers())) {
                // no support for non-static member types now
                for (Class<?> t : clazz.getClasses()) {
                    if (!isStaticTypeOrInterface(t)) {
                        continue;
                    }
                    names.add(t.getSimpleName());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    public static Class<?> getMethodReturnType(Method method) {
        if (method == null || method.getReturnType() == void.class) {
            return Object.class;
        }
        return method.getReturnType();
    }

    public static Type getMethodGenericReturnType(Method method) {
        if (method == null || method.getReturnType() == void.class) {
            return Object.class;
        }
        return method.getGenericReturnType();
    }

    static String jniName(Method m) {
        StringBuilder sb = new StringBuilder();
        noUnderscore(sb, m.getName()).append("__");
        appendType(sb, m.getReturnType());
        Class<?>[] arr = m.getParameterTypes();
        for (int i = 0; i < arr.length; i++) {
            appendType(sb, arr[i]);
        }
        return sb.toString();
    }

    private static StringBuilder noUnderscore(StringBuilder sb, String name) {
        return sb.append(name.replace("_", "_1").replace('.', '_'));
    }

    private static void appendType(StringBuilder sb, Class<?> type) {
        if (type == Integer.TYPE) {
            sb.append('I');
            return;
        }
        if (type == Long.TYPE) {
            sb.append('J');
            return;
        }
        if (type == Double.TYPE) {
            sb.append('D');
            return;
        }
        if (type == Float.TYPE) {
            sb.append('F');
            return;
        }
        if (type == Byte.TYPE) {
            sb.append('B');
            return;
        }
        if (type == Boolean.TYPE) {
            sb.append('Z');
            return;
        }
        if (type == Short.TYPE) {
            sb.append('S');
            return;
        }
        if (type == Void.TYPE) {
            sb.append('V');
            return;
        }
        if (type == Character.TYPE) {
            sb.append('C');
            return;
        }
        if (type.isArray()) {
            sb.append("_3");
            appendType(sb, type.getComponentType());
            return;
        }
        noUnderscore(sb.append('L'), type.getName());
        sb.append("_2");
    }
}

class FunctionProxyNode extends HostEntryRootNode<TruffleObject> implements Supplier<String> {

    final Class<?> receiverClass;
    final Method method;
    @Child private TruffleExecuteNode executeNode;
    @CompilationFinal private Class<?> returnClass;
    @CompilationFinal private Type returnType;

    FunctionProxyNode(Class<?> receiverType, Method method) {
        this.receiverClass = receiverType;
        this.method = method;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<? extends TruffleObject> getReceiverType() {
        return (Class<? extends TruffleObject>) receiverClass;
    }

    @Override
    public final String get() {
        return "FunctionalInterfaceProxy<" + receiverClass + ", " + method + ">";
    }

    @Override
    protected Object executeImpl(Object languageContext, TruffleObject function, Object[] args, int offset) {
        if (executeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.returnClass = JavaInteropReflect.getMethodReturnType(method);
            this.returnType = JavaInteropReflect.getMethodGenericReturnType(method);
            this.executeNode = insert(new TruffleExecuteNode());
        }
        return executeNode.execute(languageContext, function, args[offset], returnClass, returnType);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(receiverClass);
        result = 31 * result + Objects.hashCode(method);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FunctionProxyNode)) {
            return false;
        }
        FunctionProxyNode other = (FunctionProxyNode) obj;
        return receiverClass == other.receiverClass && method.equals(other.method);
    }

    static CallTarget lookup(Object languageContext, Class<?> receiverClass, Method method) {
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        if (engine == null) {
            return createTarget(new FunctionProxyNode(receiverClass, method));
        }
        FunctionProxyNode node = new FunctionProxyNode(receiverClass, method);
        CallTarget target = engine.lookupJavaInteropCodeCache(languageContext, node, CallTarget.class);
        if (target == null) {
            target = engine.installJavaInteropCodeCache(languageContext, node, createTarget(node), CallTarget.class);
        }
        return target;
    }
}

final class FunctionProxyHandler implements InvocationHandler {
    final TruffleObject functionObj;
    final Object languageContext;
    private final Method functionMethod;
    private final CallTarget target;

    FunctionProxyHandler(TruffleObject obj, Method functionMethod, Object languageContext) {
        this.functionObj = obj;
        this.languageContext = languageContext;
        this.functionMethod = functionMethod;
        this.target = FunctionProxyNode.lookup(languageContext, obj.getClass(), functionMethod);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();
        if (method.equals(functionMethod)) {
            return target.call(languageContext, functionObj, spreadVarArgsArray(arguments));
        } else {
            return invokeDefault(proxy, method, arguments);
        }
    }

    private Object[] spreadVarArgsArray(Object[] arguments) {
        if (!functionMethod.isVarArgs()) {
            return arguments;
        }
        if (arguments.length == 1) {
            return (Object[]) arguments[0];
        } else {
            final int allButOne = arguments.length - 1;
            Object[] last = (Object[]) arguments[allButOne];
            Object[] merge = new Object[allButOne + last.length];
            System.arraycopy(arguments, 0, merge, 0, allButOne);
            System.arraycopy(last, 0, merge, allButOne, last.length);
            return merge;
        }
    }

    private static Object invokeDefault(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals":
                    return proxy == arguments[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
        // default method; requires Java 9 (JEP 274)
        Class<?> declaringClass = method.getDeclaringClass();
        assert declaringClass.isInterface() : declaringClass;
        MethodHandle mh;
        try {
            mh = MethodHandles.lookup().findSpecial(declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(method.getName(), e);
        }
        return mh.bindTo(proxy).invokeWithArguments(arguments);
    }
}

class ObjectProxyNode extends HostEntryRootNode<TruffleObject> implements Supplier<String> {

    final Class<?> receiverClass;
    final Class<?> interfaceType;

    @Child private ProxyInvokeNode proxyInvoke = ProxyInvokeNodeGen.create();
    @CompilationFinal private BiFunction<Object, Object[], Object[]> toGuests;

    ObjectProxyNode(Class<?> receiverType, Class<?> interfaceType) {
        this.receiverClass = receiverType;
        this.interfaceType = interfaceType;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<? extends TruffleObject> getReceiverType() {
        return (Class<? extends TruffleObject>) receiverClass;
    }

    @Override
    public final String get() {
        return "InterfaceProxy<" + receiverClass + ">";
    }

    @Override
    protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
        if (proxyInvoke == null || toGuests == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toGuests = createToGuestValuesNode();
            proxyInvoke = ProxyInvokeNodeGen.create();
        }
        Method method = (Method) args[offset];
        Object[] arguments = toGuests.apply(languageContext, (Object[]) args[offset + 1]);
        return proxyInvoke.execute(languageContext, receiver, method, arguments);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(receiverClass);
        result = 31 * result + Objects.hashCode(interfaceType);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ObjectProxyNode)) {
            return false;
        }
        ObjectProxyNode other = (ObjectProxyNode) obj;
        return receiverClass == other.receiverClass && interfaceType == other.interfaceType;
    }

    static CallTarget lookup(Object languageContext, Class<?> receiverClass, Class<?> interfaceClass) {
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        if (engine == null) {
            return createTarget(new ObjectProxyNode(receiverClass, interfaceClass));
        }
        ObjectProxyNode node = new ObjectProxyNode(receiverClass, interfaceClass);
        CallTarget target = engine.lookupJavaInteropCodeCache(languageContext, node, CallTarget.class);
        if (target == null) {
            target = engine.installJavaInteropCodeCache(languageContext, node, createTarget(node), CallTarget.class);
        }
        return target;
    }
}

@ImportStatic({Message.class, JavaInteropReflect.class})
abstract class ProxyInvokeNode extends Node {

    public abstract Object execute(Object languageContext, TruffleObject receiver, Method method, Object[] arguments);

    /*
     * The limit of the proxy node is unbounded. There are only so many methods a Java interface can
     * have. So we always want to specialize.
     */
    protected static final int LIMIT = Integer.MAX_VALUE;

    /*
     * It is supposed to be safe to compare method names with == only as they are always interned.
     */
    @Specialization(guards = {"cachedMethod.equals(method)"}, limit = "LIMIT")
    @SuppressWarnings("unused")
    protected Object doCachedMethod(Object languageContext, TruffleObject receiver, Method method, Object[] arguments,
                    @Cached("method") Method cachedMethod,
                    @Cached("method.getName()") String name,
                    @Cached("getMethodReturnType(method)") Class<?> returnClass,
                    @Cached("getMethodGenericReturnType(method)") Type returnType,
                    @Cached("createInvoke(0).createNode()") Node invokeNode,
                    @Cached("KEY_INFO.createNode()") Node keyInfoNode,
                    @Cached("READ.createNode()") Node readNode,
                    @Cached("IS_EXECUTABLE.createNode()") Node isExecutableNode,
                    @Cached("createExecute(0).createNode()") Node executeNode,
                    @Cached("createBinaryProfile()") ConditionProfile branchProfile,
                    @Cached("create()") ToJavaNode toJava,
                    @Cached("findMessage(method)") Message message,
                    @Cached("maybeCreateNode(message)") Node messageNode) {
        Object result;
        if (message == null) {
            result = invokeOrExecute(receiver, arguments, name, invokeNode, keyInfoNode, readNode, isExecutableNode, executeNode, branchProfile);
        } else {
            // legacy behavior for MethodMessage
            try {
                result = handleMessage(message, messageNode, receiver, name, arguments);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                // MethodMessage is not reachable normally so its not a problem to rethrow interop
                // exceptions
                // to make current unit tests happy.
                throw e.raise();
            }
        }
        return toJava.execute(result, returnClass, returnType, languageContext);
    }

    protected static Node maybeCreateNode(Message message) {
        return message == null ? null : message.createNode();
    }

    private static Object handleMessage(Message message, Node messageNode, TruffleObject obj, String name, Object[] args) throws InteropException {
        if (message == Message.WRITE) {
            ForeignAccess.sendWrite(messageNode, obj, name, args[0]);
            return null;
        }
        if (message == Message.HAS_SIZE || message == Message.IS_BOXED || message == Message.IS_EXECUTABLE || message == Message.IS_NULL || message == Message.GET_SIZE) {
            return ForeignAccess.send(messageNode, obj);
        }

        if (message == Message.KEY_INFO) {
            return ForeignAccess.sendKeyInfo(messageNode, obj, name);
        }

        if (message == Message.READ) {
            return ForeignAccess.sendRead(messageNode, obj, name);
        }

        if (message == Message.UNBOX) {
            return ForeignAccess.sendUnbox(messageNode, obj);
        }

        if (Message.createExecute(0).equals(message)) {
            return ForeignAccess.sendExecute(messageNode, obj, args);
        }

        if (Message.createInvoke(0).equals(message)) {
            return ForeignAccess.sendInvoke(messageNode, obj, name, args);
        }

        if (Message.createNew(0).equals(message)) {
            return ForeignAccess.sendNew(messageNode, obj, args);
        }

        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(message);
    }

    protected static Message findMessage(Method method) {
        CompilerAsserts.neverPartOfCompilation();
        MethodMessage mm = method.getAnnotation(MethodMessage.class);
        if (mm == null) {
            return null;
        }
        Message message = Message.valueOf(mm.message());
        if (message == Message.WRITE && method.getParameterTypes().length != 1) {
            throw new IllegalStateException("Method needs to have a single argument to handle WRITE message " + method);
        }
        return message;
    }

    @TruffleBoundary
    private static boolean guardReturnType(Method method, Type returnType) {
        return method.getGenericReturnType().equals(returnType);
    }

    private static Object invokeOrExecute(TruffleObject receiver, Object[] arguments, String name, Node invokeNode, Node keyInfoNode, Node readNode, Node isExecutableNode, Node executeNode,
                    ConditionProfile branchProfile) {
        try {
            int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, receiver, name);
            if (branchProfile.profile(KeyInfo.isInvocable(keyInfo))) {
                try {
                    return ForeignAccess.sendInvoke(invokeNode, receiver, name, arguments);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    // fallthrough to unsupported
                }
            } else if (KeyInfo.isReadable(keyInfo)) {
                Object readValue = ForeignAccess.sendRead(readNode, receiver, name);
                if (readValue instanceof TruffleObject) {
                    TruffleObject truffleReadValue = (TruffleObject) readValue;
                    if (ForeignAccess.sendIsExecutable(isExecutableNode, truffleReadValue)) {
                        return ForeignAccess.sendExecute(executeNode, truffleReadValue, arguments);
                    }
                }
                if (arguments.length == 0) {
                    return readValue;
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newUnsupportedOperationException("Member " + name + " not executable or readable.");
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newUnsupportedOperationException("Unknown member " + e.getUnknownIdentifier() + ".");
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newIllegalArgumentException("Illegal argument provided.");
        } catch (ArityException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newIllegalArgumentException("Illegal number of arguments.");
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newUnsupportedOperationException("Unsupported operation.");
        }
    }

}

final class ObjectProxyHandler implements InvocationHandler {

    final TruffleObject obj;
    final Object languageContext;
    final CallTarget invoke;

    ObjectProxyHandler(TruffleObject obj, Object languageContext, Class<?> interfaceClass) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.invoke = ObjectProxyNode.lookup(languageContext, obj.getClass(), interfaceClass);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();
        return invoke.call(languageContext, obj, method, arguments == null ? JavaInteropReflect.EMPTY : arguments);
    }

}
