/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.management;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExecutionListenerImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.MonitoringAccess;

/**
 * Execution listeners allow to instrument the execution of guest languages. For example, it is
 * possible to attach an execution listeners that is invoked for every statement of the guest
 * language program, similar to how a debugger would single-step through the program.
 * <p>
 * The following example prints the characters of every executed statement of simple JavaScript
 * loop.
 *
 * <code>
 * <pre>
 * Context context = Context.create("js");
 * ExecutionListeners listener = ExecutionListeners.newBuilder()
 *          .onEnter((e) -> System.out.println(
 *                  e.getLocation().getCharacters()))
 *          .statements(true)
 *          .attach(context.getEngine());
 * context.eval("js", "for (var i = 0; i < 2; i++);");
 * listener.close();
 * </pre>
 * </code>
 *
 * Prints the following result:
 *
 * <pre>
 * i = 0
 * i < 2
 * i++
 * i < 2
 * i++
 * i < 2
 * </pre>
 *
 * <h3>Creation and Closing</h3>
 *
 * An execution listener builder can be created by first invoking {@link #newBuilder()}. At least
 * one event consumer and one filtered source element needs to be enabled. To complete the listener
 * attachment {@link Builder#attach(Engine)} needs to be invoked. {@link Builder#attach(Engine)
 * Attach} may be invoked multiple times for one builder.
 * <p>
 * All execution listeners are automatically closed when the engine {@link Engine#close() closed}.
 * To close a listener earlier {@link #close()} may be invoked. Execution listeners are
 * {@link AutoCloseable} and can therefore be used in try-with-resource blocks.
 *
 * <h3>Event Consumers</h3>
 *
 * The following event consumers can be set for an execution listener:
 * <ul>
 * <li>{@link Builder#onEnter(Consumer) OnEnter}: An event that is notified when an execution of an
 * element is entered. This event is consumed before any input values are executed.
 * <li>{@link Builder#onReturn(Consumer) OnReturn}: An event that is notified when an execution of
 * an element was entered and completed.
 * </ul>
 * At least one event consumer needs to be set otherwise an {@link IllegalArgumentException} will be
 * thrown by the builder when it is {@link Builder#attach(Engine) attached}.
 * <p>
 * Event consumers may throw any Java host exception. Such exceptions will be reported to the
 * context as {@link PolyglotException} instances. The thrown exception may be accessed using
 * {@link PolyglotException#asHostException()}.
 *
 * <h3>Event Data</h3>
 *
 * For every event that is consumed the {@link ExecutionEvent#getLocation() source location} and
 * {@link ExecutionEvent#getRootName() root name} data is available. Other event data will return
 * <code>null</code> by default.
 * <p>
 * The collection of the following event data may be enabled:
 * <ul>
 * <li>{@link Builder#collectReturnValue(boolean) Return values}: Enables access to
 * {@link ExecutionEvent#getReturnValue() return values} in {@link Builder#onReturn(Consumer)
 * OnReturn} events.
 * <li>{@link Builder#collectInputValues(boolean) Input values}: Enables access to
 * {@link ExecutionEvent#getInputValues() input values} in {@link Builder#onReturn(Consumer)
 * OnReturn} events.
 * <li>{@link Builder#collectErrors(boolean) Errors}: Enables access to
 * {@link ExecutionEvent#getError() errors} in {@link Builder#onReturn(Consumer) OnReturn} events.
 * </ul>
 * If additional event data is collected then the peak performance overhead of execution listeners
 * is significant. It is not recommended to collect additional event data when running production
 * workloads.
 * <p>
 * Provided event instances may escape the event consumer and remain usable until the engine is
 * closed.
 *
 * <h3>Event Filters</h3>
 *
 * Execution listeners can be applied to the following source elements:
 * <ul>
 * <li>{@link Builder#roots(boolean) Roots}: Filter for marked program locations that represent a
 * root of a function, method or closure.
 * <li>{@link Builder#statements(boolean) Statements}: Filter for marked program locations that
 * represent a statement.
 * <li>{@link Builder#expressions(boolean) Expressions}: Filter for marked program locations that
 * represent an expression.
 * </ul>
 * At least one source element needs to be enabled otherwise an {@link IllegalArgumentException}
 * will be thrown by the builder when it is {@link Builder#attach(Engine) attached}. Not all source
 * elements may be supported by a language. If the language does not support listening to a source
 * element then no events will be triggered.
 * <p>
 * If multiple source elements are enabled, multiple or one event may be reported per source
 * location. If this behavior is not desirable than multiple execution listeners for each source
 * element can be created and attached.
 * <p>
 * By default the execution listener is applied to all {@link Source sources} that were loaded. A
 * {@link Builder#sourceFilter(Predicate) source filter} may be attached to limit the number of
 * sources that will trigger events.
 *
 * <h3>Performance</h3>
 *
 * The peak performance overhead of execution listeners depend on the granularity of the filter.
 * Roots can be collected more efficiently than statement or expression events due to their
 * frequency. If additional event data is collected then the peak performance overhead of execution
 * listeners is significant. It is not recommended to collect additional event data when running
 * production workloads.
 * <p>
 * {@link Builder#attach(Engine) Attaching} and {@link #close() closing} execution listeners are
 * expensive operations and typically require to traverse through all loaded code. Code that was
 * previously optimized will be deoptimized in the process. It is most efficient to attach an
 * execution listener before any code is executed and let execution listeners automatically close
 * with the engine.
 *
 * <h3>Compatibility</h3>
 *
 * Event execution order and granularity of events are language specific and may change without
 * notice. There are no compatibility guarantees for that provided by the polyglot SDK. Certain
 * language implementations may do so. Please see the language implementation documentation for
 * further details.
 *
 * @since 1.0
 */
public final class ExecutionListener implements AutoCloseable {

    private static final ExecutionListener EMPTY = new ExecutionListener(null);

    private final Object impl;

    ExecutionListener(Object impl) {
        this.impl = impl;
    }

    /**
     * Closes and detaches this execution listener from the engine. After an execution listener was
     * closed no further events will be reported.
     * <p>
     * {@link Builder#attach(Engine) Attaching} and {@link #close() closing} execution listeners are
     * expensive operations and typically require to traverse through all loaded code. Code that was
     * previously optimized will be deoptimized in the process. It is most efficient to attach an
     * execution listener before any code is executed and let execution listeners automatically
     * close with the engine.
     *
     * @see Builder#attach(Engine)
     * @since 1.0
     */
    public void close() {
        IMPL.closeExecutionListener(impl);
    }

    /**
     * Creates a builder that can be used to attach execution listeners. The returned Builder
     * instance is not thread-safe.
     * <p>
     * A minimal example on how to build and attach a listener:
     *
     * <code>
     * <pre>
     * ExecutionListeners listener = ExecutionListeners.newBuilder()
     *          .onEnter((e) -> ...)
     *          .statements(true)
     *          .attach(context.getEngine());
     * </pre>
     * </code>
     *
     * @see ExecutionListener
     * @since 1.0
     */
    public static Builder newBuilder() {
        return EMPTY.new Builder();
    }

    /**
     * A builder used to construct execution events. Builder instances are not thread-safe and may
     * not be used from multiple threads at the same time.
     *
     * @see ExecutionEvent For further details.
     * @since 1.0
     */
    public final class Builder {

        private Consumer<ExecutionEvent> onReturn;
        private Consumer<ExecutionEvent> onEnter;

        private boolean expressions;
        private boolean statements;
        private boolean roots;
        private Predicate<Source> sourceFilter;
        private Predicate<String> rootNameFilter;
        private boolean collectInputValues;
        private boolean collectReturnValues;
        private boolean collectErrors;

        Builder() {
        }

        /**
         * Set a listener that is notified when an execution of an element is entered.
         *
         * @param listener
         * @since 1.0
         */
        public Builder onEnter(Consumer<ExecutionEvent> listener) {
            this.onEnter = listener;
            return this;
        }

        /**
         * Set a listener that is notified when an execution of an element was entered and
         * completed.
         *
         * @param listener
         * @return
         */
        public Builder onReturn(Consumer<ExecutionEvent> listener) {
            this.onReturn = listener;
            return this;
        }

        /**
         * Set an addition filter execution events by source. By default all sources are include.
         * Source predicates must be stable, i.e. always return the same result for a source. The
         * filter predicate may be invoked on multiple threads at the same time.
         *
         * @param predicate the source predicate that returns <code>true</code> for a source to be
         *            included and <code>false</code> otherwise.
         * @since 1.0
         */
        public Builder sourceFilter(Predicate<Source> predicate) {
            this.sourceFilter = predicate;
            return this;
        }

        /**
         * Set an addition filter execution events by root name. By default all root names are
         * included. Root name predicates must be stable and always return the same result for
         * source. The filter predicate may be invoked on multiple threads at the same time.
         *
         * @param predicate
         * @return
         */
        public Builder rootNameFilter(Predicate<String> predicate) {
            this.rootNameFilter = predicate;
            return this;
        }

        /**
         * Include program locations that are marked as root of a function, method or closure. By
         * default no source elements are included.
         *
         * @param enabled <code>true</code> if enabled, else <code>false</code>
         * @see #expressions(boolean)
         * @see #statements(boolean)
         * @since 1.0
         */
        public Builder roots(boolean enabled) {
            this.roots = enabled;
            return this;
        }

        /**
         * Include program locations that are marked as statements. By default no source elements
         * are included.
         *
         * @param enabled <code>true</code> if enabled, else <code>false</code>
         * @see #expressions(boolean)
         * @see #roots(boolean)
         * @since 1.0
         */
        public Builder statements(boolean enabled) {
            this.statements = enabled;
            return this;
        }

        /**
         * Include program locations that are marked as expressions. By default no source elements
         * are included.
         *
         * @param enabled <code>true</code> if enabled, else <code>false</code>
         * @see #statements(boolean)
         * @see #roots(boolean)
         * @since 1.0
         */
        public Builder expressions(boolean enabled) {
            this.expressions = enabled;
            return this;
        }

        /**
         * Collect additional execution event data for input values. The error may be accessed in
         * {@link #onReturn(Consumer) OnReturn} events with {@link ExecutionEvent#getInputValues()}.
         * <p>
         * If additional event data is collected then the peak performance overhead of execution
         * listeners is significant. It is not recommended to collect additional event data when
         * running production workloads.
         *
         * @param enabled <code>true</code> if enabled, else <code>false</code>
         * @since 1.0
         */
        public Builder collectInputValues(boolean value) {
            this.collectInputValues = value;
            return this;
        }

        /**
         * Collect additional execution event data about return values. The error may be accessed in
         * {@link #onReturn(Consumer) OnReturn} events with {@link ExecutionEvent#getReturnValue()}.
         * <p>
         * If additional event data is collected then the peak performance overhead of execution
         * listeners is significant. It is not recommended to collect additional event data when
         * running production workloads.
         *
         * @param enabled <code>true</code> if enabled, else <code>false</code>
         * @since 1.0
         */
        public Builder collectReturnValue(boolean enabled) {
            this.collectReturnValues = enabled;
            return this;
        }

        /**
         * Collect additional execution event data about errors. The error may be accessed in
         * {@link #onReturn(Consumer) OnReturn} events with {@link ExecutionEvent#getError()}.
         * <p>
         * If additional event data is collected then the peak performance overhead of execution
         * listeners is significant. It is not recommended to collect additional event data when
         * running production workloads.
         *
         * @param enabled <code>true</code> if enabled, else <code>false</code>
         * @since 1.0
         */
        public Builder collectErrors(boolean enabled) {
            this.collectErrors = enabled;
            return this;
        }

        /**
         * Creates a new execution listener using the current builder configuration and attaches it
         * to an engine. The same builder configuration may be used to attach multiple listeners.
         * <p>
         * Execution listeners cannot be attached to engines that were statically looked up using
         * <code>
         * Context.{@link Context#getCurrent() getCurrent()}.{@link Context#getEngine() getEngine()}
         * </code>. For security reasons only the original {@link Context.Builder#build() creator}
         * of the context or engine is allowed to do perform this action.
         * <p>
         * {@link Builder#attach(Engine) Attaching} and {@link #close() closing} execution listeners
         * are expensive operations and typically require to traverse through all loaded code. Code
         * that was previously optimized will be deoptimized in the process. It is most efficient to
         * attach an execution listener before any code is executed and let execution listeners
         * automatically close with the engine.
         *
         * @throws PolyglotException if one of the provided filter predicate fails.
         * @param engine the engine to attach to
         * @returns the attached closable execution listener.
         * @since 1.0
         */
        public ExecutionListener attach(Engine engine) {
            return new ExecutionListener(
                            IMPL.attachExecutionListener(engine, onEnter, onReturn, expressions, statements, roots,
                                            sourceFilter, rootNameFilter, collectInputValues, collectReturnValues, collectErrors));
        }
    }

    static final AbstractExecutionListenerImpl IMPL = initImpl();

    private static AbstractExecutionListenerImpl initImpl() {
        try {
            Method method = Engine.class.getDeclaredMethod("getImpl");
            method.setAccessible(true);
            AbstractPolyglotImpl impl = (AbstractPolyglotImpl) method.invoke(null);
            impl.setMonitoring(new MonitoringAccessImpl());
            return impl.getExecutionListenerImpl();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initilize execution listener class.", e);
        }
    }

    private static class MonitoringAccessImpl extends MonitoringAccess {
        @Override
        public ExecutionEvent newExecutionEvent(Object event) {
            return new ExecutionEvent(event);
        }
    }

}
