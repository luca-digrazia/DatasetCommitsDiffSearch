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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A factory class that generates host adapter classes.
 */
final class HostAdapterFactory {

    @TruffleBoundary
    static Class<?> getAdapterClassFor(HostClassCache hostClassCache, Class<?>[] types, Value classOverrides) {
        return getAdapterClassForCommon(hostClassCache, types, classOverrides, null);
    }

    @TruffleBoundary
    static Class<?> getAdapterClassFor(HostClassCache hostClassCache, Class<?> type) {
        return getAdapterClassForCommon(hostClassCache, new Class<?>[]{type}, null, null);
    }

    static Class<?> getAdapterClassForCommon(HostClassCache hostClassCache, Class<?>[] types, Value classOverrides, ClassLoader classLoader) {
        assert types.length > 0;
        CompilerAsserts.neverPartOfCompilation();

        Class<?> superClass = null;
        final List<Class<?>> interfaces = new ArrayList<>();
        for (final Class<?> t : types) {
            if (!t.isInterface()) {
                if (superClass != null) {
                    throw PolyglotEngineException.illegalArgument(
                                    String.format("Can not extend multiple classes %s and %s. At most one of the specified types can be a class, the rest must all be interfaces.",
                                                    t.getCanonicalName(), superClass.getCanonicalName()));
                } else if (Modifier.isFinal(t.getModifiers())) {
                    throw PolyglotEngineException.illegalArgument(String.format("Can not extend final class %s.", t.getCanonicalName()));
                } else {
                    superClass = t;
                }
            } else {
                if (interfaces.size() >= 65535) {
                    throw PolyglotEngineException.illegalArgument("interface limit exceeded");
                }

                interfaces.add(t);
            }
            if (!Modifier.isPublic(t.getModifiers())) {
                throw PolyglotEngineException.illegalArgument(String.format("Class not public: %s.", t.getCanonicalName()));
            }
            if (!HostInteropReflect.isExtensibleType(t) || !hostClassCache.allowsImplementation(t)) {
                throw PolyglotEngineException.illegalArgument("Implementation not allowed for " + t);
            }
        }
        superClass = superClass != null ? superClass : Object.class;

        ClassLoader commonLoader = classLoader != null ? classLoader : getCommonClassLoader(types);
        return generateAdapterClassFor(superClass, interfaces, commonLoader, hostClassCache, classOverrides);
    }

    private static Class<?> generateAdapterClassFor(Class<?> superClass, List<Class<?>> interfaces, ClassLoader commonLoader, HostClassCache hostClassCache, Value classOverrides) {
        boolean classOverride = classOverrides != null && classOverrides.hasMembers();
        HostAdapterBytecodeGenerator bytecodeGenerator = new HostAdapterBytecodeGenerator(superClass, interfaces, commonLoader, hostClassCache, classOverride);
        HostAdapterClassLoader generatedClassLoader = bytecodeGenerator.createAdapterClassLoader();

        Value classOverridesValue = classOverride ? classOverrides : null;
        return generatedClassLoader.generateClass(commonLoader, classOverridesValue);
    }

    @TruffleBoundary
    static boolean isAdapterInstance(Object adapter) {
        return adapter.getClass().getClassLoader() instanceof HostAdapterClassLoader.CLImpl;
    }

    private static boolean classLoaderCanSee(ClassLoader loader, Class<?> clazz) {
        if (clazz.getClassLoader() == loader) {
            return true;
        }
        try {
            return Class.forName(clazz.getName(), false, loader) == clazz;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean classLoaderCanSee(ClassLoader loader, Class<?>[] classes) {
        for (Class<?> c : classes) {
            if (!classLoaderCanSee(loader, c)) {
                return false;
            }
        }
        return true;
    }

    private static ClassLoader getCommonClassLoader(Class<?>[] types) {
        if (types.length == 1) {
            return types[0].getClassLoader();
        }
        Map<ClassLoader, Boolean> distinctLoaders = new HashMap<>();
        for (Class<?> type : types) {
            ClassLoader loader = type.getClassLoader();
            if (distinctLoaders.computeIfAbsent(loader, new Function<ClassLoader, Boolean>() {
                public Boolean apply(ClassLoader cl) {
                    return classLoaderCanSee(cl, types);
                }
            })) {
                return loader;
            }
        }
        throw PolyglotEngineException.illegalArgument("Could not determine a class loader that can see all types: " + Arrays.toString(types));
    }
}
