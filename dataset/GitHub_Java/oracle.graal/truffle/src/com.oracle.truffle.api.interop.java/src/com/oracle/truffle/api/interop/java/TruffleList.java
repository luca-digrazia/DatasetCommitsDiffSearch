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

import static com.oracle.truffle.api.interop.ForeignAccess.sendExecute;
import static com.oracle.truffle.api.interop.ForeignAccess.sendGetSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendHasSize;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsExecutable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsInstantiable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendKeyInfo;
import static com.oracle.truffle.api.interop.ForeignAccess.sendNew;
import static com.oracle.truffle.api.interop.ForeignAccess.sendRead;
import static com.oracle.truffle.api.interop.ForeignAccess.sendWrite;

import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

class TruffleList<T> extends AbstractList<T> {

    final TruffleObject array;
    final Object languageContext;
    final TruffleListCache cache;

    TruffleList(Class<T> elementClass, Type elementType, TruffleObject array, Object languageContext) {
        this.array = array;
        this.languageContext = languageContext;
        this.cache = TruffleListCache.lookup(languageContext, array.getClass(), elementClass, elementType);
    }

    @TruffleBoundary
    public static <T> List<T> create(Object languageContext, TruffleObject array, boolean implementFunction, Class<T> elementClass, Type elementType) {
        if (implementFunction) {
            return new FunctionTruffleList<>(elementClass, elementType, array, languageContext);
        } else {
            return new TruffleList<>(elementClass, elementType, array, languageContext);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(int index) {
        return (T) cache.get.call(languageContext, array, index);
    }

    @Override
    public T set(int index, T element) {
        T prev = get(index);
        cache.set.call(languageContext, array, index, element);
        return prev;
    }

    @Override
    public int size() {
        return (Integer) cache.size.call(languageContext, array);
    }

    @SuppressWarnings("unused")
    private static class FunctionTruffleList<T> extends TruffleList<T> implements Function<Object, Object> {

        FunctionTruffleList(Class<T> elementClass, Type elementType, TruffleObject array, Object languageContext) {
            super(elementClass, elementType, array, languageContext);
        }

        public Object apply(Object t) {
            return cache.apply.call(languageContext, array, t);
        }

    }

    private static final class TruffleListCache {

        final Class<?> receiverClass;
        final Class<?> valueClass;
        final Type valueType;

        final CallTarget get;
        final CallTarget set;
        final CallTarget size;
        final CallTarget apply;

        TruffleListCache(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            this.receiverClass = receiverClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.get = initializeCall(new Get(this));
            this.size = initializeCall(new Size(this));
            this.set = initializeCall(new Set(this));
            this.apply = initializeCall(new Apply(this));
        }

        private static CallTarget initializeCall(TruffleListNode node) {
            return Truffle.getRuntime().createCallTarget(JavaInterop.ACCESSOR.engine().wrapHostBoundary(node, node));
        }

        static TruffleListCache lookup(Object languageContext, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            EngineSupport engine = JavaInterop.ACCESSOR.engine();
            if (engine == null) {
                return new TruffleListCache(receiverClass, valueClass, valueType);
            }
            Key cacheKey = new Key(receiverClass, valueClass, valueType);
            TruffleListCache cache = engine.lookupJavaInteropCodeCache(languageContext, cacheKey, TruffleListCache.class);
            if (cache == null) {
                cache = engine.installJavaInteropCodeCache(languageContext, cacheKey, new TruffleListCache(receiverClass, valueClass, valueType), TruffleListCache.class);
            }
            assert cache.receiverClass == receiverClass;
            assert cache.valueClass == valueClass;
            assert cache.valueType == valueType;
            return cache;
        }

        private static final class Key {

            final Class<?> receiverClass;
            final Class<?> valueClass;
            final Type valueType;

            Key(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
                assert receiverClass != null;
                this.receiverClass = receiverClass;
                this.valueClass = valueClass;
                this.valueType = valueType;
            }

            @Override
            public int hashCode() {
                return 31 * (31 * (valueType == null ? 0 : valueType.hashCode()) + receiverClass.hashCode()) + valueClass.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return valueType == other.valueType && valueClass == other.valueClass && receiverClass == other.receiverClass;
            }
        }

        private static abstract class TruffleListNode extends HostEntryRootNode<TruffleObject> implements Supplier<String> {

            final TruffleListCache cache;

            @Child protected Node hasSize = Message.HAS_SIZE.createNode();

            TruffleListNode(TruffleListCache cache) {
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String get() {
                return "TruffleList<" + cache.receiverClass + ", " + cache.valueType + ">." + getOperationName();
            }

            protected abstract String getOperationName();

        }

        private static class Size extends TruffleListNode {

            @Child private Node getSize = Message.GET_SIZE.createNode();

            Size(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                int size = 0;
                if (sendHasSize(hasSize, receiver)) {
                    try {
                        size = ((Number) sendGetSize(getSize, receiver)).intValue();
                    } catch (UnsupportedMessageException e) {
                        size = 0;
                    }
                }
                return size;
            }

            @Override
            protected String getOperationName() {
                return "size";
            }

        }

        private static class Get extends TruffleListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node read = Message.READ.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();

            Get(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "get";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                if (sendHasSize(hasSize, receiver) && KeyInfo.isReadable(sendKeyInfo(keyInfo, receiver, key))) {
                    try {
                        result = toHost.execute(sendRead(read, receiver, key), cache.valueClass, cache.valueType, languageContext);
                    } catch (UnknownIdentifierException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw newArrayIndexOutOfBounds(key.toString());
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw newUnsupportedOperationException("Operation is not supported.");
                    }
                }
                return result;
            }

        }

        private static class Set extends TruffleListNode {

            @Child private Node keyInfo = Message.KEY_INFO.createNode();
            @Child private Node write = Message.WRITE.createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();
            private final BiFunction<Object, Object, Object> toGuest = JavaInterop.ACCESSOR.engine().createToGuestValueNode();

            Set(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "set";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject receiver, Object[] args, int offset) {
                Object key = args[offset];
                Object result = null;
                assert key instanceof Integer;
                Object value = args[offset + 1];
                if (sendHasSize(hasSize, receiver) && KeyInfo.isWritable(sendKeyInfo(keyInfo, receiver, key))) {
                    try {
                        sendWrite(write, receiver, key, toGuest.apply(languageContext, value));
                    } catch (UnknownIdentifierException e) {
                        throw newArrayIndexOutOfBounds("Out of bounds");
                    } catch (UnsupportedMessageException e) {
                        throw newUnsupportedOperationException("Unsupported operation");
                    } catch (UnsupportedTypeException e) {
                        throw newIllegalArgumentException("Unsupported type");
                    }
                    return cache.valueClass.cast(result);
                }
                throw newUnsupportedOperationException("Unsupported operation");
            }
        }

        private static class Apply extends TruffleListNode {

            @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
            @Child private Node isInstantiable = Message.IS_INSTANTIABLE.createNode();
            @Child private Node execute = Message.createExecute(0).createNode();
            @Child private Node instantiate = Message.createNew(0).createNode();
            @Child private ToJavaNode toHost = ToJavaNode.create();
            private final BiFunction<Object, Object[], Object[]> toGuests = JavaInterop.ACCESSOR.engine().createToGuestValuesNode();
            private final ConditionProfile condition = ConditionProfile.createBinaryProfile();

            Apply(TruffleListCache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(Object languageContext, TruffleObject function, Object[] args, int offset) {
                Object[] functionArgs = (Object[]) args[offset];
                functionArgs = toGuests.apply(languageContext, functionArgs);

                Object result;
                try {
                    if (condition.profile(sendIsExecutable(isExecutable, function))) {
                        result = sendExecute(execute, function, functionArgs);
                    } else if (sendIsInstantiable(isInstantiable, function)) {
                        result = sendNew(instantiate, function, functionArgs);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw newUnsupportedOperationException("Unsupported operation.");
                    }
                } catch (UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw newIllegalArgumentException("Illegal argument provided.");
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw newIllegalArgumentException("Illegal number of arguments.");
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw newUnsupportedOperationException("Unsupported operation.");
                }
                return toHost.execute(result, Object.class, Object.class, languageContext);
            }
        }
    }

}
