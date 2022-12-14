/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

/**
 * <p>
 * The service provider interface (SPI) for <em>instruments</em>: clients of Truffle instrumentation
 * that may observe and inject behavior into interpreters written using the Truffle framework. A
 * registered instrument may be created and disposed multiple times; only one instance is ever
 * active per {@linkplain com.oracle.truffle.api.vm.PolyglotEngine engine}.
 * </p>
 * <h4>Registration</h4>
 * <p>
 * Instrument implementation classes must use the {@link Registration} annotation to provide
 * required metadata and to enable automatic discovery of the implementation.
 * </p>
 * <h4>Instrument Creation</h4>
 * <ul>
 * <li>A newly created instrument is notified once via {@link #onCreate(Env)}.</li>
 * <li>The {@link Instrumenter} instance available in the provided {@linkplain Env environment}
 * allows the instrument to bind listeners and node factories to guest language code locations.</li>
 * </ul>
 * <h4>Instrument Disposal</h4>
 * <ul>
 * <li>An instrument that will no longer be used is notified once via {@link #onDispose(Env)}.</li>
 * <li>Any active bindings created by the disposed instrument will be disposed automatically by the
 * framework.</li>
 * <li>The {@link Instrumenter} instance available in the provided {@linkplain Env environment} may
 * not be used after disposal.</li>
 * </ul>
 * <h4>Example</h4>
 * <p>
 * <b> Example for a simple expression coverage instrument: </b>
 *
 * <pre>
 * &#064;Registration(name = Coverage.NAME, version = Coverage.VERSION, instrumentType = Coverage.TYPE)
 * public final class Coverage extends TruffleInstrument {
 * 
 *     public static final String NAME = &quot;sample-coverage&quot;;
 *     public static final String TYPE = &quot;coverage&quot;;
 *     public static final String VERSION = &quot;coverage&quot;;
 * 
 *     private final Set&lt;SourceSection&gt; coverage = new HashSet&lt;&gt;();
 * 
 *     &#064;Override
 *     protected void onCreate(Env env, Instrumenter instrumenter) {
 *         instrumenter.attachFactory(SourceSectionFilter.newBuilder() //
 *         .tagIs(&quot;EXPRESSION&quot;).build(), new EventNodeFactory() {
 *             public EventNode create(final EventContext context) {
 *                 return new EventNode() {
 *                     &#064;CompilationFinal private boolean visited;
 * 
 *                     &#064;Override
 *                     public void onReturnValue(VirtualFrame vFrame, Object result) {
 *                         if (!visited) {
 *                             CompilerDirectives.transferToInterpreterAndInvalidate();
 *                             visited = true;
 *                             coverage.add(context.getInstrumentedSourceSection());
 *                         }
 *                     }
 *                 };
 *             }
 *         });
 *     }
 * 
 *     &#064;Override
 *     protected void onDispose(Env env) {
 *         // print result
 *     }
 * 
 * }
 * </pre>
 *
 * @since 0.12
 */
public abstract class TruffleInstrument {
    /**
     * Constructor for subclasses.
     *
     * @since 0.12
     */
    protected TruffleInstrument() {
    }

    /**
     * Invoked once on each newly allocated {@link TruffleInstrument} instance.
     * <p>
     * The method may {@link Env#registerService(java.lang.Object) register} additional
     * <em>services</em> - e.g. objects to be exposed via
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup} query. For example to
     * expose a debugger one could define an abstract debugger controller:
     * </p>
     *
     * {@codesnippet DebuggerController}
     *
     * and then implement it, instantiate and @link Env#registerService(java.lang.Object) register}
     * in own's instrument
     * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) onCreate}
     * method:
     *
     * {@codesnippet DebuggerExample}
     *
     * @param env environment information for the instrument
     *
     * @see Env#getInstrumenter()
     * @since 0.12
     */
    protected abstract void onCreate(Env env);

    /**
     * Invoked once on an {@linkplain TruffleInstrument instance} when it is no longer needed,
     * possibly because the underlying {@linkplain com.oracle.truffle.api.vm.PolyglotEngine engine}
     * has been disposed. A disposed instance is no longer usable. If the instrument is re-enabled,
     * the engine will create a new instance.
     *
     * @param env environment information for the instrument
     * @since 0.12
     */
    protected void onDispose(Env env) {
        // default implementation does nothing
    }

    /**
     * Provides ways and means to access input, output and error streams. Also allows to parse
     * arbitrary code from other Truffle languages.
     *
     * @since 0.12
     */
    public static final class Env {

        private final Instrumenter instrumenter;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private List<Object> services;

        Env(Instrumenter instrumenter, OutputStream out, OutputStream err, InputStream in) {
            this.instrumenter = instrumenter;
            this.in = in;
            this.err = err;
            this.out = out;
        }

        /**
         * Returns the instrumenter which lets you instrument guest language ASTs.
         *
         * @see Instrumenter
         * @since 0.12
         */
        public Instrumenter getInstrumenter() {
            return instrumenter;
        }

        /**
         * Input associated with {@link com.oracle.truffle.api.vm.PolyglotEngine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return reader, never <code>null</code>
         * @since 0.12
         */
        public InputStream in() {
            return in;
        }

        /**
         * Standard output writer for {@link com.oracle.truffle.api.vm.PolyglotEngine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.12
         */
        public OutputStream out() {
            return out;
        }

        /**
         * Standard error writer for {@link com.oracle.truffle.api.vm.PolyglotEngine} this
         * {@link TruffleInstrument instrument} is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.12
         */
        public OutputStream err() {
            return err;
        }

        /**
         * Registers additional service. This method can be called multiple time, but only during
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
         * initialization of the instrument}. These services are made available to users via
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup} query method.
         *
         * This method can only be called from
         * {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)} method -
         * then the services are collected and cannot be changed anymore.
         *
         * @param service a service to be returned from associated
         *            {@link com.oracle.truffle.api.vm.PolyglotEngine.Instrument#lookup}
         * @throws IllegalStateException if the method is called later than from
         *             {@link #onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) }
         *             method
         * @since 0.12
         */
        public void registerService(Object service) {
            if (services == null) {
                throw new IllegalStateException();
            }
            services.add(service);
        }

        Object[] onCreate(TruffleInstrument instrument) {
            List<Object> arr = new ArrayList<>();
            services = arr;
            try {
                instrument.onCreate(this);
            } finally {
                services = null;
            }
            return arr.toArray();
        }

        /**
         * Evaluates source of (potentially different) language using the current context.The names
         * of arguments are parameters for the resulting {#link CallTarget} that allow the
         * <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @throws IOException if the parsing or evaluation fails for some reason
         * @since 0.12
         */
        @SuppressWarnings("static-method")
        public CallTarget parse(Source source, String... argumentNames) throws IOException {
            return InstrumentationHandler.ACCESSOR.parse(null, source, null, argumentNames);
        }

    }

    /**
     * Annotation to register an {@link TruffleInstrument instrument} implementations for automatic
     * discovery.
     *
     * @since 0.12
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {

        /**
         * A custom machine identifier for this instrument. If not defined then the fully qualified
         * class name is used.
         */
        String id() default "";

        /**
         * The name of the instrument in an arbitrary format for humans.
         */
        String name() default "";

        /**
         * The version for instrument in an arbitrary format.
         */
        String version() default "";

    }

    static {
        try {
            // Instrument is loaded by PolyglotEngine which should load InstrumentationHandler
            // this is important to load the accessors properly.
            Class.forName(InstrumentationHandler.class.getName(), true, InstrumentationHandler.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
