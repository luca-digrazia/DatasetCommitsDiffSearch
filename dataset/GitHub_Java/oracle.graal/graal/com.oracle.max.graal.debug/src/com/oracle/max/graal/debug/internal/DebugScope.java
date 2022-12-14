/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.debug.internal;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.graal.debug.*;


public final class DebugScope {

    private static ThreadLocal<DebugScope> instance = new ThreadLocal<>();
    private static ThreadLocal<DebugConfig> config = new ThreadLocal<>();

    private final String name;
    private final DebugScope parent;

    private Object[] context;

    private List<DebugScope> children;
    private DebugValueMap valueMap;
    private String qualifiedName;

    public static final DebugScope DEFAULT_CONTEXT = new DebugScope("DEFAULT", "DEFAULT", null);
    private static final char SCOPE_SEP = '.';

    private boolean logEnabled;
    private boolean meterEnabled;
    private boolean timerEnabled;
    private boolean dumpEnabled;

    public static DebugScope getInstance() {
        DebugScope result = instance.get();
        if (result == null) {
            instance.set(new DebugScope("DEFAULT", "DEFAULT", null));
            return instance.get();
        } else {
            return result;
        }
    }

    public static DebugConfig getConfig() {
        return config.get();
    }

    private DebugScope(String name, String qualifiedName, DebugScope parent, Object... context) {
        this.name = name;
        this.parent = parent;
        this.context = context;
        this.qualifiedName = qualifiedName;
    }

    public boolean isDumpEnabled() {
        return dumpEnabled;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public boolean isMeterEnabled() {
        return meterEnabled;
    }

    public boolean isTimerEnabled() {
        return timerEnabled;
    }

    public void log(String msg, Object... args) {
        if (isLogEnabled()) {
            cachedOut.println(String.format(msg, args));
        }
    }

    public void dump(Object object, String msg, Object[] args) {
    }

    public <T> T scope(String newName, Runnable runnable, Callable<T> callable, boolean sandbox, Object[] newContext) {
        DebugScope oldContext = getInstance();
        DebugConfig oldConfig = getConfig();
        DebugScope newChild = null;
        if (sandbox) {
            newChild = new DebugScope(newName, newName, null, newContext);
            setConfig(null);
        } else {
            newChild = oldContext.createChild(newName, newContext);
        }
        instance.set(newChild);
        T result = null;
        updateFlags();
        log("Starting scope %s", newChild.getQualifiedName());
        try {
            if (runnable != null) {
                runnable.run();
            }
            if (callable != null) {
                result = call(callable);
            }
        } catch (RuntimeException e) {
            throw interceptException(e);
        } finally {
            newChild.deactivate();
            instance.set(oldContext);
            setConfig(oldConfig);
        }
        return result;
    }

    private void updateFlags() {
        DebugConfig config = getConfig();
        if (config == null) {
            logEnabled = false;
            meterEnabled = false;
            timerEnabled = false;
            dumpEnabled = false;
        } else {
            logEnabled = config.isLogEnabled();
            meterEnabled = config.isMeterEnabled();
            timerEnabled = config.isTimerEnabled();
            dumpEnabled = config.isDumpEnabled();
        }
    }

    private void deactivate() {
        context = null;
    }

    private RuntimeException interceptException(RuntimeException e) {
        return e;
    }

    private DebugValueMap getValueMap() {
        if (valueMap == null) {
            valueMap = new DebugValueMap();
        }
        return valueMap;
    }

    long getCurrentValue(int index) {
        return getValueMap().getCurrentValue(index);
    }

    void setCurrentValue(int index, long l) {
        getValueMap().setCurrentValue(index, l);
    }

    private DebugScope createChild(String newName, Object[] newContext) {
        String newQualifiedName = this.qualifiedName + SCOPE_SEP + newName;
        DebugScope result = new DebugScope(newName, newQualifiedName, this, newContext);
        if (children == null) {
            children = new ArrayList<>(4);
        }
        children.add(result);
        return result;
    }

    public Iterable<Object> getCurrentContext() {
        return new Iterable<Object>() {

            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {

                    DebugScope currentScope = DebugScope.this;
                    int objectIndex;

                    @Override
                    public boolean hasNext() {
                        selectScope();
                        return currentScope != null;
                    }

                    private void selectScope() {
                        while (currentScope != null && currentScope.context.length <= objectIndex) {
                            currentScope = currentScope.parent;
                            objectIndex = 0;
                        }
                    }

                    @Override
                    public Object next() {
                        selectScope();
                        if (currentScope != null) {
                            return currentScope.context[objectIndex++];
                        }
                        throw new IllegalStateException("May only be called if there is a next element.");
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException("This iterator is read only.");
                    }
                };
            }
        };
    }

    public static <T> T call(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public void setConfig(DebugConfig newConfig) {
        config.set(newConfig);
        updateFlags();
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public static PrintStream cachedOut;

    public static void initialize() {
        cachedOut = System.out;
    }
}

