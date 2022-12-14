/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Reporter of guest language value allocations. Language implementation ought to use this class to
 * report all allocations and re-allocations of guest language values. An instance of this class can
 * be obtained from {@link Env#lookup(java.lang.Class) Env.lookup(AllocationReporter.class)}.
 * <p>
 * Usage example: {@link AllocationReporterSnippets#example}
 *
 * @since 0.27
 */
public final class AllocationReporter {

    /**
     * Constant specifying an unknown size. Use it when it's not possible to estimate size of the
     * memory being allocated.
     *
     * @since 0.27
     */
    public static final long SIZE_UNKNOWN = Long.MIN_VALUE;

    /**
     * Name of a property that is fired when an {@link #isActive() active} property of this reporter
     * changes.
     *
     * @since 0.27
     * @see #isActive()
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     */
    public static final String PROP_ACTIVE = "active";

    final LanguageInfo language;
    private final PropertyChangeSupport propSupport = new PropertyChangeSupport(this);
    private ThreadLocal<Reference<Object>> valueCheck;

    @CompilationFinal private volatile Assumption listenersNotChangedAssumption = Truffle.getRuntime().createAssumption();
    @CompilationFinal private volatile AllocationListener[] listeners = null;

    AllocationReporter(LanguageInfo language) {
        this.language = language;
        assert (valueCheck = new ThreadLocal<>()) != null;
    }

    /**
     * Add a property change listener that is notified when a property of this reporter changes. Use
     * it to get notified when {@link #isActive()} changes.
     *
     * @since 0.27
     * @see #PROP_ACTIVE
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove a property change listener that is notified when state of this reporter changes.
     *
     * @since 0.27
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(listener);
    }

    /**
     * Test if the reporter instance is actually doing some reporting when notify methods are
     * called. Methods {@link #onEnter(java.lang.Object, long)} and
     * {@link #onReturnValue(java.lang.Object, long)} have no effect when this method returns false.
     * A {@link PropertyChangeListener} can be
     * {@link #addPropertyChangeListener(java.beans.PropertyChangeListener) added} to listen on
     * changes of this property.
     *
     * @return <code>true</code> when there are some {@link AllocationListener}s attached,
     *         <code>false</code> otherwise.
     * @since 0.27
     */
    public boolean isActive() {
        if (!listenersNotChangedAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return listeners != null;
    }

    void addListener(AllocationListener l) {
        CompilerAsserts.neverPartOfCompilation();
        boolean hadListeners;
        synchronized (this) {
            if (listeners == null) {
                listeners = new AllocationListener[]{l};
                hadListeners = false;
            } else {
                int index = listeners.length;
                AllocationListener[] newListeners = Arrays.copyOf(listeners, index + 1);
                newListeners[index] = l;
                listeners = newListeners;
                hadListeners = true;
            }
            Assumption assumption = listenersNotChangedAssumption;
            listenersNotChangedAssumption = Truffle.getRuntime().createAssumption();
            assumption.invalidate();
        }
        if (!hadListeners) {
            propSupport.firePropertyChange(PROP_ACTIVE, false, true);
        }
    }

    void removeListener(AllocationListener l) {
        CompilerAsserts.neverPartOfCompilation();
        boolean hasListeners = true;
        synchronized (this) {
            final int len = listeners.length;
            if (len == 1) {
                if (listeners[0] == l) {
                    listeners = null;
                    hasListeners = false;
                }
            } else {
                for (int i = 0; i < len; i++) {
                    if (listeners[i] == l) {
                        if (i == (len - 1)) {
                            listeners = Arrays.copyOf(listeners, i);
                        } else if (i == 0) {
                            listeners = Arrays.copyOfRange(listeners, 1, len);
                        } else {
                            AllocationListener[] newListeners = new AllocationListener[len - 1];
                            System.arraycopy(listeners, 0, newListeners, 0, i);
                            System.arraycopy(listeners, i + 1, newListeners, i, len - i - 1);
                            listeners = newListeners;
                        }
                        break;
                    }
                }
            }
            Assumption assumption = listenersNotChangedAssumption;
            listenersNotChangedAssumption = Truffle.getRuntime().createAssumption();
            assumption.invalidate();
        }
        if (!hasListeners) {
            propSupport.firePropertyChange(PROP_ACTIVE, true, false);
        }
    }

    /**
     * Report an intent to allocate a new guest language value, or re-allocate an existing one. This
     * method delegates to all registered listeners
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)}.
     * Only primitive types, String and {@link com.oracle.truffle.api.interop.TruffleObject} are
     * accepted value types.
     * <p>
     * A call to this method needs to be followed by a call to
     * {@link #onReturnValue(java.lang.Object, long)} with the actual allocated value, or with the
     * same (re-allocated) value.
     *
     * @param valueToReallocate <code>null</code> in case of a new allocation, or the value that is
     *            to be re-allocated.
     * @param sizeChangeEstimate an estimate of the allocation size of the value which is to be
     *            created, in bytes. In case of a new allocation it must be positive, or
     *            {@link #SIZE_UNKNOWN} when the allocation size is not known.
     * @since 0.27
     */
    public void onEnter(Object valueToReallocate, long sizeChangeEstimate) {
        assert valueToReallocate != null || newSizeCheck(sizeChangeEstimate);
        assert valueToReallocate == null || allocateValueCheck(valueToReallocate);
        notifyAllocateOrReallocate(valueToReallocate, sizeChangeEstimate);
    }

    @ExplodeLoop
    private void notifyAllocateOrReallocate(Object value, long sizeEstimate) {
        assert setValueCheck(value);
        if (!listenersNotChangedAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        AllocationListener[] ls = this.listeners;
        if (ls != null) {
            AllocationEvent event = new AllocationEvent(language, value, sizeEstimate);
            for (AllocationListener l : ls) {
                l.onEnter(event);
            }
        }
    }

    /**
     * Report an allocation of a new one or re-allocation of an existing guest language value. This
     * method notifies all registered listeners
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent)}
     * . Only primitive types, String and {@link com.oracle.truffle.api.interop.TruffleObject} are
     * accepted value types.
     * <p>
     * A call to {@link #onEnter(java.lang.Object, long)} must precede this call. In case of
     * re-allocation, the value object passed to {@link #onEnter(java.lang.Object, long)} must be
     * the same instance as the value passed to this method.
     *
     * @param value the value that was newly allocated, or the re-allocated value. Must not be
     *            <code>null</code>.
     * @param sizeChange the size of the allocated value in bytes, or the change in size caused by
     *            re-allocation. The re-allocation size change can also be negative.
     * @since 0.27
     */
    public void onReturnValue(Object value, long sizeChange) {
        assert allocateValueCheck(value);
        assert allocatedCheck(value, sizeChange);
        notifyAllocated(value, sizeChange);
    }

    @ExplodeLoop
    private void notifyAllocated(Object value, long sizeChange) {
        if (!listenersNotChangedAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        AllocationListener[] ls = this.listeners;
        if (ls != null) {
            AllocationEvent event = new AllocationEvent(language, value, sizeChange);
            for (AllocationListener l : ls) {
                l.onReturnValue(event);
            }
        }
    }

    @TruffleBoundary
    private static boolean newSizeCheck(long sizeEstimate) {
        assert (sizeEstimate == SIZE_UNKNOWN || sizeEstimate > 0) : "Wrong size estimate = " + sizeEstimate;
        return true;
    }

    @TruffleBoundary
    private boolean setValueCheck(Object value) {
        valueCheck.set(new WeakReference<>(value));
        return true;
    }

    @TruffleBoundary
    private static boolean allocateValueCheck(Object value) {
        if (value == null) {
            throw new NullPointerException("No allocated value.");
        }
        // Strings are O.K.
        if (value instanceof String) {
            return true;
        }
        // Primitive types are O.K.
        if (value instanceof Boolean || value instanceof Byte || value instanceof Character ||
                        value instanceof Short || value instanceof Integer || value instanceof Long ||
                        value instanceof Float || value instanceof Double) {
            return true;
        }
        // TruffleObject is O.K.
        boolean isTO = InstrumentationHandler.ACCESSOR.isTruffleObject(value);
        assert isTO : "Wrong value class, TruffleObject is required. Was: " + value.getClass().getName();
        return isTO;
    }

    @TruffleBoundary
    private boolean allocatedCheck(Object value, long sizeChange) {
        assert valueCheck.get() != null : "notifyWillAllocate/Reallocate was not called";
        Object orig = valueCheck.get().get();
        assert orig == null || orig == value : "A different reallocated value. Was: " + orig + " now is: " + value;
        assert orig == null && (sizeChange == SIZE_UNKNOWN || sizeChange > 0) ||
                        orig != null : "Size change of a newly allocated value must be positive or unknown. Was: " + sizeChange;
        valueCheck.remove();
        return true;
    }
}

class AllocationReporterSnippets extends TruffleLanguage<ContextObject> {

    void example() {
    }

    // @formatter:off
    // BEGIN: AllocationReporterSnippets#example
    @Override
    protected ContextObject createContext(Env env) {
        AllocationReporter reporter = env.lookup(AllocationReporter.class);
        return new ContextObject(reporter);
    }

    Object allocateNew() {
        AllocationReporter reporter = getContextReference().get().getReporter();
        // Test if the reporter is active, we should compute the size estimate
        if (reporter.isActive()) {
            long size = findSizeEstimate();
            reporter.onEnter(null, size);
        }
        // Do the allocation itself
        Object newObject = new MyTruffleObject();
        // Test if the reporter is active,
        // we should compute the allocated object size
        if (reporter.isActive()) {
            long size = findSize(newObject);
            reporter.onReturnValue(newObject, size);
        }
        return newObject;
    }

    Object allocateComplex() {
        AllocationReporter reporter = getContextReference().get().getReporter();
        // If the allocated size is a constant, onEnter() and onReturnValue()
        // can be called without a fast-path performance penalty when not active
        reporter.onEnter(null, 16);
        // Do the allocation itself
        Object newObject = createComplexObject();
        // Report the allocation
        reporter.onReturnValue(newObject, 16);
        return newObject;
    }
    // END: AllocationReporterSnippets#example
    // @formatter:on

    @Override
    protected Object findExportedSymbol(ContextObject context, String globalName, boolean onlyExplicit) {
        return null;
    }

    @Override
    protected Object getLanguageGlobal(ContextObject context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    private static long findSizeEstimate() {
        return 0L;
    }

    private static long findSize(Object newObject) {
        assert newObject != null;
        return 0L;
    }

    private static Object createComplexObject() {
        return null;
    }

    private static class MyTruffleObject {
    }

}

class ContextObject {

    private final AllocationReporter reporter;

    ContextObject(AllocationReporter reporter) {
        this.reporter = reporter;
    }

    public AllocationReporter getReporter() {
        return reporter;
    }

}
