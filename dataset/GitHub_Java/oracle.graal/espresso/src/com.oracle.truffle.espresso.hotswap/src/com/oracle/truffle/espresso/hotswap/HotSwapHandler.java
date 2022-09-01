/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.hotswap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HotSwapHandler {

    private static HotSwapHandler theHandler;

    private final Set<HotSwapPlugin> plugins = Collections.synchronizedSet(new HashSet<>());
    private final Map<Class<?>, Set<HotSwapAction>> hotSwapActions = new HashMap<>();
    private final List<HotSwapAction> postHotSwapActions = Collections.synchronizedList(new ArrayList<>());
    private final Map<Class<?>, Boolean> staticInitializerHotSwap = new HashMap<>();
    private final Map<Class<?>, List<HotSwapAction>> staticReInitCallBacks = new HashMap<>();
    private final ServiceWatcher serviceWatcher = new ServiceWatcher();

    private HotSwapHandler() {
    }

    static HotSwapHandler create() {
        theHandler = new HotSwapHandler();
        // register handler to Espresso if present
        return registerHandler(theHandler) ? theHandler : null;
    }

    // substituted by Espresso
    private static boolean registerHandler(@SuppressWarnings("unused") Object handler) {
        return false;
    }

    void addPlugin(HotSwapPlugin plugin) {
        plugins.add(plugin);
    }

    public void registerHotSwapAction(Class<?> klass, HotSwapAction action) {
        hotSwapActions.putIfAbsent(klass, new HashSet<>());
        hotSwapActions.get(klass).add(action);
    }

    public void registerPostHotSwapAction(HotSwapAction action) {
        postHotSwapActions.add(action);
    }

    public void registerStaticClassInitHotSwap(Class<?> klass, boolean onChange, HotSwapAction callback) {
        if (!staticInitializerHotSwap.containsKey(klass)) {
            staticInitializerHotSwap.put(klass, onChange);
        } else if (!onChange) {
            staticInitializerHotSwap.put(klass, false);
        }
        if (callback != null) {
            List<HotSwapAction> reInitCallbacks = staticReInitCallBacks.get(klass);
            if (reInitCallbacks == null) {
                reInitCallbacks = new ArrayList<>(1);
                staticReInitCallBacks.put(klass, reInitCallbacks);
            }
            reInitCallbacks.add(callback);
        }
    }

    public void registerMetaInfServicesListener(Class<?> service, ClassLoader loader, HotSwapAction callback) {
        serviceWatcher.addServiceWatcher(service, loader, callback);
    }

    @SuppressWarnings("unused")
    public void postHotSwap(Class<?>[] changedClasses) {
        // fire all registered HotSwap actions
        for (Class<?> klass : changedClasses) {
            Set<HotSwapAction> actions = hotSwapActions.getOrDefault(klass, Collections.emptySet());
            actions.forEach(HotSwapAction::fire);
        }
        // fire a generic HotSwap plugin listener
        for (HotSwapPlugin plugin : plugins) {
            plugin.postHotSwap(changedClasses);
        }
        // fire all registered post HotSwap actions
        postHotSwapActions.forEach(HotSwapAction::fire);
    }

    @SuppressWarnings("unused")
    public boolean rerunClassInit(Class<?> klass, boolean changed) {
        if (staticInitializerHotSwap.containsKey(klass)) {
            boolean onlyOnChange = staticInitializerHotSwap.get(klass);
            boolean rerun = !onlyOnChange || changed;
            if (rerun) {
                staticReInitCallBacks.getOrDefault(klass, Collections.emptyList()).forEach(HotSwapAction::fire);
            }
            return rerun;
        } else {
            // check class hierarchy
            for (Map.Entry<Class<?>, Boolean> entry : staticInitializerHotSwap.entrySet()) {
                Class<?> key = entry.getKey();
                if (key.isAssignableFrom(klass)) {
                    boolean rerun = !entry.getValue() || changed;
                    if (rerun) {
                        staticReInitCallBacks.getOrDefault(key, Collections.emptyList()).forEach(HotSwapAction::fire);
                    }
                    return rerun;
                }
            }
        }
        return false;
    }
}
