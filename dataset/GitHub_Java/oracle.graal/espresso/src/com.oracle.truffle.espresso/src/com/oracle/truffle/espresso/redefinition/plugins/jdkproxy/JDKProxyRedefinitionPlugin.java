/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.jdkproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.MethodVariable;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.api.MethodLocator;
import com.oracle.truffle.espresso.redefinition.plugins.api.TriggerClass;

public final class JDKProxyRedefinitionPlugin extends InternalRedefinitionPlugin {
    private static final Symbol<Symbol.Type> PROXY_GENERATOR_CLASS = Symbol.Type.sun_misc_ProxyGenerator;
    private static final Symbol<Symbol.Name> GENERATOR_METHOD = Symbol.Name.generateProxyClass;

    private static final Symbol<Symbol.Type> PROXY_SUPER_CLASS = Symbol.Type.java_lang_reflect_Proxy;

    private final Map<KlassRef, List<ProxyCache>> cache = Collections.synchronizedMap(new HashMap<>());

    private MethodRef proxyGeneratorMethod;
    private KlassRef proxySuperKlass;

    private ThreadLocal<Boolean> generationInProgress = ThreadLocal.withInitial(() -> false);

    @Override
    public String getName() {
        return "JDK Dynamic Proxy Reloading Plugin";
    }

    @Override
    public TriggerClass[] getTriggerClasses() {
        TriggerClass[] triggerClasses = new TriggerClass[2];
        // trigger on proxy generator class and add generator method hooks
        triggerClasses[0] = new TriggerClass(PROXY_GENERATOR_CLASS, this, klass -> {
            // hook into the proxy generator method to obtain proxy generation arguments
            hookMethodEntry(klass, new MethodLocator(GENERATOR_METHOD, Symbol.Signature._byte_array_String_Class_array_int), MethodHook.Kind.INDEFINITE, (method, variables) -> {
                if (generationInProgress.get()) {
                    // don't hook when we're re-generating proxy bytes
                    return;
                }
                if (proxyGeneratorMethod == null) {
                    proxyGeneratorMethod = method;
                }
                collectProxyArguments(variables);
            });
        });
        // trigger on Proxy super class to obtain the type
        triggerClasses[1] = new TriggerClass(PROXY_SUPER_CLASS, this, klass -> proxySuperKlass = klass);
        return triggerClasses;
    }

    private synchronized void collectProxyArguments(MethodVariable[] variables) {
        Object[] proxyArgs = new Object[3];
        // proxy name
        proxyArgs[0] = variables[0].getValue();
        // proxy interfaces
        proxyArgs[1] = variables[1].getValue();
        // proxy access modifiers
        proxyArgs[2] = variables[2].getValue();

        try {
            // fetch klass instances for the declared proxy interfaces
            Object interfaces = proxyArgs[1];
            long arraySize = INTEROP.getArraySize(interfaces);
            KlassRef[] proxyInterfaces = new KlassRef[(int) arraySize];
            for (int i = 0; i < arraySize; i++) {
                // get the klass type of the interface
                proxyInterfaces[i] = getreflectedKlassType(INTEROP.readArrayElement(interfaces, i));
            }

            // register onLoad action that will give us
            // the klass object for the generated proxy
            String proxyName = proxyArgs[0].toString();
            registerClassLoadAction(proxyName, klass -> {
                ProxyCache proxyCache = new ProxyCache(klass, proxyArgs);

                // cache proxy arguments under each interface, so that
                // when they change we can re-generate the proxy bytes
                for (KlassRef proxyInterface : proxyInterfaces) {
                    addCacheEntry(proxyCache, proxyInterface);
                }
            });
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            // TODO - log here. Should we have a dedicated HotSwap logger that logs to file?
        }
    }

    private void addCacheEntry(ProxyCache proxyCache, KlassRef proxyInterface) {
        List<ProxyCache> list = cache.get(proxyInterface);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
            cache.put(proxyInterface, list);
        }
        list.add(proxyCache);
    }

    @Override
    @TruffleBoundary
    public synchronized void fillExtraReloadClasses(List<RedefineInfo> redefineInfos, List<RedefineInfo> additional) {
        for (RedefineInfo redefineInfo : redefineInfos) {
            KlassRef klass = redefineInfo.getKlass();
            if (klass != null) {
                List<ProxyCache> list = cache.getOrDefault(klass, Collections.emptyList());
                for (ProxyCache proxyCache : list) {
                    generationInProgress.set(true);
                    byte[] proxyBytes = (byte[]) proxyGeneratorMethod.invokeMethod(null, proxyCache.proxyArgs);
                    generationInProgress.set(false);
                    additional.add(new RedefineInfo(proxyCache.klass, proxyBytes));
                }
            }
        }
    }

    @Override
    public boolean reRunClinit(KlassRef klass, boolean changed) {
        // changed Dynamic Proxy classes have cached Method references
        // in static fields, so always re-run the static initializer
        return proxySuperKlass.isAssignable(klass);
    }

    private final class ProxyCache {
        private final KlassRef klass;
        private final Object[] proxyArgs;

        ProxyCache(KlassRef klass, Object[] proxyArgs) {
            assert proxyArgs.length == 3;
            this.klass = klass;
            this.proxyArgs = proxyArgs;
        }
    }
}
