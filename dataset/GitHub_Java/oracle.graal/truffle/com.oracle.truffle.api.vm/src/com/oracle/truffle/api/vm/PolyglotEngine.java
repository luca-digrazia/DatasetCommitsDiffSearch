/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.FindContextNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;

/**
 * A multi-language execution environment for Truffle-implemented {@linkplain Language languages}
 * that supports <em>interoperability</em> among the Truffle languages and with Java, for example
 * cross-language calls, foreign object exchange, and shared <em>global symbols</em>.
 *
 * <h4>Creation</h4>
 *
 * <em>Engine</em> instances are created using a {@linkplain #newBuilder() builder} that allows
 * application- and language-specific configuration.
 *
 * <h4>Global Symbols</h4>
 *
 * Communication among languages within an engine is supported by shared <em>global symbols</em>.
 * Each active language provides a namespace of global values that have usually (but not always)
 * been exported explicitly. The engine provides its own namespace of values configured when the
 * engine is {@linkplain Builder#globalSymbol(String, Object) built}.
 * <p>
 * The engine {@linkplain #findGlobalSymbol(String) retrieves} global symbols by name, searching
 * every namespace uniformly. Name collisions across namespaces are possible and can only be
 * discovered by explicitly {@linkplain #findGlobalSymbols(String) retrieving} all values exported
 * with a particular name.
 *
 * <h4>Isolation</h4>
 *
 * An engine runs as an isolated <a href="https://en.wikipedia.org/wiki/Multitenancy">tenant</a> on
 * a Java Virtual Machine. No aspects of program execution, language environments, or global symbols
 * are shared with other engine instances.
 *
 * <h4>Threads</h4>
 *
 * Guest language code execution is single-threaded, performed on a thread determined by the
 * engine's configuration.
 * <p>
 * <ul>
 * <li>Execution is by default synchronous (performed on the calling thread) and only permitted by
 * the thread that created the engine.</li>
 * <li>An engine can be {@linkplain Builder#executor(Executor) configured} with a custom
 * {@link Executor} that performs all executions on a different thread. In this case the engine
 * requires only that all executions are performed on the same thread.</li>
 * </ul>
 *
 * <h4>Languages</h4>
 * <p>
 * An engine supports every Truffle language available on the JVM class path.
 * <p>
 * Languages are initialized on demand, the first time an engine evaluates code of a matching
 * {@linkplain Source#getMimeType() MIME type}. The engine throws an {@link IllegalStateException}
 * if no matching language is available. Languages remains initialized for the lifetime of the
 * engine.
 * <p>
 * Specific language environments can be configured, for example in response to command line
 * options, by building the engine with combinations of language-specific
 * {@linkplain Builder#config(String, String, Object) MIME-key-value settings} and pre-registered
 * {@linkplain PolyglotEngine.Builder#globalSymbol global symbols}.
 *
 * <h4>Use case: run guest language code</h4>
 *
 * An engine {@linkplain #eval(Source) evaluates} {@link Source} objects, which may wrap references
 * to guest language code (e.g. a filename or URL) or may represent code literally as in the example
 * below. The engine uses the language matching the code's MIME type and returns the result wrapped
 * in a language-agnostic {@link Value}.
 *
 * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#evalCode}
 *
 * <h4>Use case: Java interoperation with guest language code</h4>
 *
 * There are many ways in which Java and guest language code can interoperate. For example:
 * <ul>
 * <li>The documentation for {@link PolyglotEngine#eval(Source)} includes three examples that show
 * how Java code can directly access JavaScript functions, objects, and classes respectively.</li>
 *
 * <li>The documentation for {@link Value#as(Class)} includes two examples that show how Java code
 * can directly access JavaScript data structures: lists and JSON data respectively.</li>
 *
 * <li>The documentation for {@link Value#execute(Object...)} includes examples showing how guest
 * language code can access Java objects, classes, and constructors.</li>
 *
 * </ul>
 *
 * @since 0.9
 */
@SuppressWarnings({"rawtypes", "deprecation"})
public class PolyglotEngine {
    static final Logger LOG = Logger.getLogger(PolyglotEngine.class.getName());
    private static final SPIAccessor SPI = new SPIAccessor();
    private final Thread initThread;
    private final ComputeInExecutor.Info executor;
    private final Map<String, Language> langs;
    private final InputStream in;
    private final OutputStream err;
    private final OutputStream out;
    private final EventConsumer<?>[] handlers;
    private final Map<String, Object> globals;
    private final Object instrumentationHandler;
    private final Map<String, Instrument> instruments;
    private final List<Object[]> config;
    private final Object[] debugger = {null};
    private final ContextStore context;
    private volatile boolean disposed;

    static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    static {
        try {
            // We need to ensure that the Instrumentation class is loaded so accessors are created
            // properly.
            Class.forName(TruffleInstrument.class.getName(), true, TruffleInstrument.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Private & temporary only constructor.
     */
    PolyglotEngine() {
        assertNoTruffle();
        this.initThread = null;
        this.in = null;
        this.err = null;
        this.out = null;
        this.langs = null;
        this.handlers = null;
        this.globals = null;
        this.executor = null;
        this.instrumentationHandler = null;
        this.instruments = null;
        this.config = null;
        this.context = null;
    }

    /**
     * Real constructor used from the builder.
     */
    PolyglotEngine(Executor executor, Map<String, Object> globals, OutputStream out, OutputStream err, InputStream in, EventConsumer<?>[] handlers, List<Object[]> config) {
        assertNoTruffle();
        this.executor = ComputeInExecutor.wrap(executor);
        this.out = out;
        this.err = err;
        this.in = in;
        this.handlers = handlers;
        this.initThread = Thread.currentThread();
        this.globals = new HashMap<>(globals);
        this.config = config;
        // this.debugger = SPI.createDebugger(this, this.instrumenter);
        // new instrumentation
        this.instrumentationHandler = Access.INSTRUMENT.createInstrumentationHandler(this, out, err, in);
        Map<String, Language> map = new HashMap<>();
        /* We want to create a language instance but per LanguageCache and not per mime type. */
        Set<LanguageCache> uniqueCaches = new HashSet<>(LanguageCache.languages().values());
        for (LanguageCache languageCache : uniqueCaches) {
            Language newLanguage = new Language(languageCache);
            for (String mimeType : newLanguage.getMimeTypes()) {
                map.put(mimeType, newLanguage);
            }
        }
        this.langs = map;
        this.instruments = createAndAutostartDescriptors(InstrumentCache.load());
        this.context = ExecutionImpl.createStore(this);
    }

    private Map<String, Instrument> createAndAutostartDescriptors(List<InstrumentCache> instrumentCaches) {
        Map<String, Instrument> instr = new LinkedHashMap<>();
        for (InstrumentCache cache : instrumentCaches) {
            Instrument instrument = new Instrument(cache);
            instr.put(cache.getId(), instrument);
        }
        return Collections.unmodifiableMap(instr);
    }

    /**
     * Returns a builder for creating an engine instance. After any configuration methods have been
     * called, the final build step creates the engine and installs all available languages. For
     * example:
     *
     * <pre>
     * {@link PolyglotEngine} engine = {@link PolyglotEngine}.{@link PolyglotEngine#newBuilder() newBuilder()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * @return a builder to create a new engine with all available languages installed
     * @since 0.10
     */
    public static PolyglotEngine.Builder newBuilder() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to PolyglotEngine class
        PolyglotEngine engine = new PolyglotEngine();
        return engine.new Builder();
    }

    /**
     * @return new builder
     * @deprecated use {@link #newBuilder()}
     * @since 0.9
     */
    @Deprecated
    public static PolyglotEngine.Builder buildNew() {
        return newBuilder();
    }

    /**
     * A builder for creating an engine instance. After any configuration methods have been called,
     * the final build step creates the engine and installs all available languages. For example:
     *
     * <pre>
     * {@link PolyglotEngine} engine = {@link PolyglotEngine}.{@link PolyglotEngine#newBuilder() newBuilder()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * @since 0.9
     */
    public class Builder {
        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private final List<EventConsumer<?>> handlers = new ArrayList<>();
        private final Map<String, Object> globals = new HashMap<>();
        private Executor executor;
        private List<Object[]> arguments;

        Builder() {
        }

        /**
         * Configures default output for languages running in the {@link PolyglotEngine engine}
         * being built, defaults to {@link System#out}.
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.9
         */
        public Builder setOut(OutputStream os) {
            out = os;
            return this;
        }

        /**
         * Configures error output for languages running in the {@link PolyglotEngine engine} being
         * built, defaults to {@link System#err}.
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.9
         */
        public Builder setErr(OutputStream os) {
            err = os;
            return this;
        }

        /**
         * Configures default input for languages running in the {@link PolyglotEngine engine} being
         * built, defaults to {@link System#in}.
         *
         * @param is the stream to use as input
         * @return this builder
         * @since 0.9
         */
        public Builder setIn(InputStream is) {
            in = is;
            return this;
        }

        /**
         * Registers another instance of {@link EventConsumer} into the to be created
         * {@link PolyglotEngine}.
         *
         * @param handler the handler to register
         * @return this builder
         * @since 0.9
         * @deprecated all event types that use this API have been deprecated.
         */
        @Deprecated
        public Builder onEvent(EventConsumer<?> handler) {
            Objects.requireNonNull(handler);
            handlers.add(handler);
            return this;
        }

        /**
         * Adds language-specific initialization data to the {@link PolyglotEngine engine} being
         * built. For example:
         *
         * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters}
         *
         * If the same key is specified multiple times for the same language, only the last one
         * specified applies.
         *
         * @param mimeType identification of the language to which the configuration data is
         *            provided; any of the language's declared MIME types may be used
         *
         * @param key to identify a language-specific configuration element
         * @param value to parameterize initial state of a language
         * @return this builder
         * @since 0.11
         */
        public Builder config(String mimeType, String key, Object value) {
            if (this.arguments == null) {
                this.arguments = new ArrayList<>();
            }
            this.arguments.add(new Object[]{mimeType, key, value});
            return this;
        }

        /**
         * Adds a global symbol to the {@link PolyglotEngine engine} being built. This symbol will
         * be accessible to all languages take will take precedence over symbols exported by the
         * languages. Any number of symbols may be added; later definition of the same name
         * overrides the previous one.
         * <p>
         * In case one wants to interoperate with Java, one can export any Java classes or objects
         * when creating the {@link PolyglotEngine} as shown in the following snippet:
         *
         * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop}
         *
         * The <b>mul</b> and <b>compose</b> objects are then available to any language.
         *
         * @param name name of the symbol to register
         * @param obj value of the object - expected to be primitive wrapper, {@link String} or
         *            <code>TruffleObject</code> for mutual interoperability. If the object isn't of
         *            the previous types, the system tries to wrap it using
         *            {@link JavaInterop#asTruffleObject(Object)}, if available
         * @return this builder
         * @see PolyglotEngine#findGlobalSymbol(String)
         * @throws IllegalArgumentException if the object isn't of primitive type and cannot be
         *             converted to {@link TruffleObject}
         * @since 0.9
         */
        public Builder globalSymbol(String name, Object obj) {
            final Object truffleReady = JavaInterop.asTruffleValue(obj);
            globals.put(name, truffleReady);
            return this;
        }

        /**
         * Provides an {@link Executor} for running guest language code asynchronously, on a thread
         * other than the calling thread.
         * <p>
         * By default engines execute both {@link PolyglotEngine#eval(Source)} and
         * {@link Value#invoke(Object, Object[])} synchronously in the calling thread.
         * <p>
         * A custom {@link Executor} is expected to perform every execution it is given (via
         * {@link Executor#execute(Runnable)}) in order of arrival. An arbitrary thread may be used,
         * but the engine requires that there be only one.
         *
         * @param executor the executor of code to be used by {@link PolyglotEngine engine} being
         *            built
         * @return this builder
         * @since 0.9
         */
        @SuppressWarnings("hiding")
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Creates an {@link PolyglotEngine engine} configured by builder methods.
         *
         * @return a new engine with all available languages installed
         * @since 0.9
         */
        public PolyglotEngine build() {
            assertNoTruffle();
            if (out == null) {
                out = System.out;
            }
            if (err == null) {
                err = System.err;
            }
            if (in == null) {
                in = System.in;
            }
            return new PolyglotEngine(executor, globals, out, err, in, handlers.toArray(new EventConsumer[0]), arguments);
        }
    }

    /**
     * Gets the map: MIME type --> {@linkplain Language metadata} for the matching language
     * installed in this engine, whether or not the language has been initialized.
     *
     *
     * @return an immutable map: MIME type --> metadata for the language that supports the source
     *         type
     * @since 0.9
     */
    public Map<String, ? extends Language> getLanguages() {
        return Collections.unmodifiableMap(langs);
    }

    /**
     * Gets the map: {@linkplain Instrument#getId() Instrument ID} --> {@link Instrument} loaded in
     * this {@linkplain PolyglotEngine engine}, whether the instrument is
     * {@linkplain Instrument#isEnabled() enabled} or not. Instruments may be enabled automatically
     * at startup.
     *
     * @return map of currently loaded instruments
     * @since 0.9
     */
    public Map<String, Instrument> getInstruments() {
        return instruments;
    }

    /**
     * Evaluates guest language source code, using the installed {@link Language} that matches the
     * code's {@link Source#getMimeType() MIME type}. Throws an {@link IllegalStateException} if no
     * matching language is installed. The engine wraps the result in an instance of {@link Value}.
     * The method {@link Value#as(Class)} creates Java-typed views (i.e. objects) for access to the
     * result.
     * <p>
     * After evaluating any code marked as {@link Source#isInteractive() interactive}, the engine
     * checks the result for {@link TruffleLanguage#isVisible(Object, Object) visibility}. The
     * engine prints to its own {@link PolyglotEngine.Builder#setOut standard output} any resulting
     * {@link Value} marked as <em>visible</em>, using a string
     * {@link TruffleLanguage#toString(Object, Object) provided} by the language implementation.
     *
     * <h5>Java interoperation examples</h5>
     * <p>
     * {@link #eval(Source)} is also useful for Java applications that <em>interoperate</em> with
     * guest languages. The following examples show how Java can access a guest language function,
     * object, and class respectively. The general strategy is to {@linkplain #eval(Source)
     * evaluate} guest language code that produces the desired language element and then
     * {@linkplain Value#as(Class) create} a Java object of the appropriate type for Java access to
     * the result.
     * <p>
     * The examples use JavaScript as the guest language, assuming that a Truffle implementation of
     * JavaScript is on the JVM class path. In each example a {@link Source#newBuilder(String)
     * Source} builder creates a literal fragment of JavaScript code.
     *
     * <h6>Java interop example: Java access to a JavaScript function</h6>
     *
     * The simplest kind of interoperation is calling a guest language (foreign) function from Java.
     * <p>
     * In this example a fragment of JavaScript code named {@code "mul.js"} defines an anonymous
     * function of two arguments. Evaluation of that code returns the JavaScript function wrapped in
     * a {@link Value}. The method {@link Value#as(Class)} creates a Java object (an instance of
     * functional interface {@code Multiplier}) that supports calls to the JavaScript function. The
     * JavaScript value returned by the function is made available as a Java {@code int}.
     * <p>
     * Parentheses around the function definition keep it out of JavaScript's global scope, so the
     * Java object holds the only reference to it.
     *
     * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionFromJava}
     *
     * <h6>Java interop example: Java access to a JavaScript object</h6>
     *
     * A slightly more complex example requires access to two guest language functions that share
     * state, which is implemented in the simplest case as a guest language object accessible from
     * Java.
     * <p>
     * In this example a fragment of JavaScript code named {@code "CountSeconds.js"} defines an
     * anonymous function of no arguments. That function creates a variable {@code "seconds"} plus
     * two functions and returns them as a dynamic object. Evaluation of {@code "CountSections.js"}
     * produces a JavaScript function wrapped as a {@link Value} that can be called directly using
     * the method {@link Value#execute(Object...)}. Executing the JavaScript function produces a
     * JavaScript object wrapped as a {@link Value}. The method {@link Value#as(Class)} creates a
     * Java object that allows access to the JavaScript object as an instance of the Java interface
     * {@code Counter}.
     * <p>
     * Parentheses around the function definition keep it out of JavaScript's global scope, so the
     * Java object holds the only reference to it.
     *
     * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionsWithSharedStateFromJava}
     *
     * <h6>Java interop example: Java access to a JavaScript class</h6>
     *
     * The ECMAScript 6 specification adds the concept of typeless classes to JavaScript.
     * Interoperability allows Java to access fields and functions of a JavaScript class. A
     * JavaScript function that creates new instances can be called directly from Java, playing the
     * role of a <em>factory</em> for the JavaScript class.
     * <p>
     * In this example a fragment of JavaScript code named {@code "Incrementor.js"} defines an
     * anonymous function of no arguments. Evaluation of that code returns the JavaScript function
     * wrapped in a {@link Value}, which can be called directly from Java using the method
     * {@link Value#execute(Object...)}. That JavaScript function defines the JavaScript class
     * {@code JSIncrementor} and returns another JavaScript function (wrapped in a {@link Value})
     * that acts as a factory. Each call to the factory produces an instance of
     * {@code JSIncrementor} wrapped as a {@link Value}. The method {@link Value#as(Class)} creates
     * a Java object for each of those instances that allows access via the Java type
     * {@code Incrementor}.
     *
     * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptClassFactoryFromJava}
     *
     * <h6>More use cases</h6>
     *
     * More examples can be found in description of {@link Value#execute(Object...)} and
     * {@link Value#as(Class)} methods.
     *
     * @param source code snippet to execute
     * @return a non-null {@link Value} that holds the result
     * @throws Exception thrown to signal errors while processing the code
     * @since 0.9
     */
    public Value eval(Source source) {
        assert checkThread();
        assertNoTruffle();
        String mimeType = source.getMimeType();
        Language l = langs.get(mimeType);
        if (l == null) {
            throw new IllegalStateException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return evalImpl(langs.get(source.getMimeType()), source);
    }

    /**
     * Disposes this engine instance and releases {@link TruffleLanguage#disposeContext(Object) all
     * resources} allocated by languages active in this engine.
     * <p>
     * Calling any other method on this instance after disposal throws an
     * {@link IllegalStateException}.
     *
     * @since 0.9
     */
    public void dispose() {
        assert checkThread();
        assertNoTruffle();
        disposed = true;
        ComputeInExecutor<Void> compute = new ComputeInExecutor<Void>(executor) {
            @Override
            protected Void compute() {
                for (Language language : getLanguages().values()) {
                    TruffleLanguage<?> impl = language.getImpl(false);
                    if (impl != null) {
                        final Env env = language.getEnv(false, true);
                        if (env != null) {
                            try {
                                Access.LANGS.dispose(impl, env);
                            } catch (Exception | Error ex) {
                                LOG.log(Level.SEVERE, "Error disposing " + impl, ex);
                            }
                        }
                    }
                }

                for (Instrument instrument : instruments.values()) {
                    try {
                        /*
                         * TODO (chumer): ideally no cleanup is required for disposing
                         * PolyglotEngine if no ASTs are shared between instances. the anything
                         * might be shared assumption invalidates this optimization we should have a
                         * way to find out if a CallTarget/RootNode is shared across PolyglotEngine
                         * instances.
                         */
                        instrument.setEnabledImpl(false, false);
                    } catch (Exception | Error ex) {
                        LOG.log(Level.SEVERE, "Error disposing " + instrument, ex);
                    }
                }

                return null;
            }
        };
        compute.perform();
    }

    private Value evalImpl(final Language l, final Source source) {
        final TruffleLanguage[] lang = {null};
        if (executor == null) {
            Object value = evalImpl(lang, l, source);
            return new DirectValue(lang, value);
        }
        assert checkThread();
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
            @Override
            protected Object compute() {
                return evalImpl(lang, l, source);
            }
        };
        compute.perform();
        return new ExecutorValue(lang, compute);
    }

    private Object evalImpl(TruffleLanguage[] langTarget, Language l, Source source) {
        CallTarget target = l.cache.get(source);
        if (target == null) {
            target = Truffle.getRuntime().createCallTarget(new PolyglotEvalRootNode(this, l, source));
            l.cache.put(source, target);
        }
        Object value = target.call((Object) langTarget);
        if (source.isInteractive()) {
            String stringResult = Access.LANGS.toStringIfVisible(langTarget[0], findEnv(langTarget[0].getClass()), value, source);
            if (stringResult != null) {
                try {
                    PolyglotEngine.this.out.write(stringResult.getBytes(StandardCharsets.UTF_8));
                    PolyglotEngine.this.out.write(System.getProperty("line.separator").getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioex) {
                    // out stream has problems.
                    throw new IllegalStateException(ioex);
                }
            }
        }
        return value;
    }

    ContextStore context() {
        return context;
    }

    Object[] debugger() {
        return debugger;
    }

    @SuppressWarnings({"try"})
    final Object invokeForeign(final Node foreignNode, VirtualFrame frame, final TruffleObject receiver) {
        assertNoTruffle();
        Object res;
        CompilerAsserts.neverPartOfCompilation();
        if (executor == null) {
            ContextStore prev = ExecutionImpl.executionStarted(context);
            try {
                Access.DEBUG.executionStarted(PolyglotEngine.this);
                final Object[] args = ForeignAccess.getArguments(frame).toArray();
                SymbolInvokerImpl.unwrapArgs(this, args);
                res = ForeignAccess.execute(foreignNode, frame, receiver, args);
            } finally {
                ExecutionImpl.executionEnded(prev);
            }
        } else {
            res = invokeForeignOnExecutor(foreignNode, frame, receiver);
        }
        return EngineTruffleObject.wrap(this, res);
    }

    static void assertNoTruffle() {
        CompilerAsserts.neverPartOfCompilation("Methods of PolyglotEngine must not be compiled by Truffle. Use Truffle interoperability or a @TruffleBoundary instead.");
    }

    @TruffleBoundary
    private Object invokeForeignOnExecutor(final Node foreignNode, VirtualFrame frame, final TruffleObject receiver) {
        final MaterializedFrame materialized = frame.materialize();
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
            @SuppressWarnings("try")
            @Override
            protected Object compute() {
                ContextStore prev = ExecutionImpl.executionStarted(context);
                try {
                    Access.DEBUG.executionStarted(PolyglotEngine.this);
                    final Object[] args = ForeignAccess.getArguments(materialized).toArray();
                    RootNode node = SymbolInvokerImpl.createTemporaryRoot(TruffleLanguage.class, foreignNode, receiver);
                    final CallTarget target = Truffle.getRuntime().createCallTarget(node);
                    return target.call(args);
                } finally {
                    ExecutionImpl.executionEnded(prev);
                }
            }
        };
        return compute.get();
    }

    /**
     * Finds a <em>global symbol</em> by name by searching every language's namespace of exported
     * symbols plus the the engine's namespace of {@linkplain Builder#globalSymbol(String, Object)
     * preconfigured} symbols.
     * <p>
     * Symbol names are language dependent. Name collisions across namespaces are possible, in which
     * case this method only returns one of them (use {@link #findGlobalSymbols(String)} to return
     * all of them).
     *
     * @param globalName a global symbol name
     * @return the value of a global symbol with the specified name, <code>null</code> if none
     * @since 0.9
     */
    public Value findGlobalSymbol(final String globalName) {
        assert checkThread();
        assertNoTruffle();
        final TruffleLanguage<?>[] lang = {null};
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
            @Override
            protected Object compute() {
                Iterator<?> it = importSymbol(lang, globalName).iterator();
                return it.hasNext() ? it.next() : null;
            }
        };
        compute.perform();
        if (compute.get() == null) {
            return null;
        }
        return new ExecutorValue(lang, compute);
    }

    /**
     * Finds all <em>global symbols</em> with a specified name by searching every language's
     * namespace of exported symbols plus the the engine's namespace of
     * {@linkplain Builder#globalSymbol(String, Object) preconfigured} symbols.
     * <p>
     * The following example shows how this method can be used to retrieve a single global symbol,
     * while treating name collisions as an error.
     *
     * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#findAndReportMultipleExportedSymbols}
     *
     * @param globalName a global symbol name
     * @return iterable access to the values of global symbols with the specified name
     * @since 0.22
     */
    public Iterable<Value> findGlobalSymbols(String globalName) {
        assert checkThread();
        assertNoTruffle();
        final TruffleLanguage<?>[] lang = {null};
        Iterable<? extends Object> it = importSymbol(lang, globalName);
        class ValueIterator implements Iterator<Value> {
            private final Iterator<? extends Object> delegate;

            ValueIterator(Iterator<? extends Object> delegate) {
                this.delegate = delegate;
            }

            @Override
            public boolean hasNext() {
                ComputeInExecutor<Boolean> compute = new ComputeInExecutor<Boolean>(executor) {
                    @Override
                    protected Boolean compute() {
                        return delegate.hasNext();
                    }
                };
                compute.perform();
                return compute.get();
            }

            @Override
            public Value next() {
                ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
                    @Override
                    protected Object compute() {
                        return delegate.next();
                    }
                };
                compute.perform();
                return new ExecutorValue(lang, compute);
            }
        }
        return new Iterable<Value>() {
            @Override
            public ValueIterator iterator() {
                return new ValueIterator(it.iterator());
            }
        };
    }

    final Iterable<? extends Object> importSymbol(TruffleLanguage<?>[] arr, String globalName) {
        class SymbolIterator implements Iterator<Object> {
            private final Collection<? extends Language> uniqueLang;
            private Object next;
            private Iterator<? extends Language> explicit;
            private Iterator<? extends Language> implicit;

            SymbolIterator(Collection<? extends Language> uniqueLang, Object first) {
                this.uniqueLang = uniqueLang;
                this.next = first;
            }

            @Override
            public boolean hasNext() {
                return findNext() != this;
            }

            @Override
            public Object next() {
                Object res = findNext();
                if (res == this) {
                    throw new NoSuchElementException();
                }
                next = null;
                return res;
            }

            private Object findNext() {
                if (next != null) {
                    return next;
                }

                if (explicit == null) {
                    explicit = uniqueLang.iterator();
                }

                while (explicit.hasNext()) {
                    Language dl = explicit.next();
                    TruffleLanguage<?> l = dl.getImpl(false);
                    TruffleLanguage.Env env = dl.getEnv(false);
                    if (l != arr[0] && l != null && env != null) {
                        Object obj = Access.LANGS.findExportedSymbol(env, globalName, true);
                        if (obj != null) {
                            next = obj;
                            explicit.remove();
                            arr[0] = l;
                            return next;
                        }
                    }
                }

                if (implicit == null) {
                    implicit = uniqueLang.iterator();
                }

                while (implicit.hasNext()) {
                    Language dl = implicit.next();
                    TruffleLanguage<?> l = dl.getImpl(false);
                    TruffleLanguage.Env env = dl.getEnv(false);
                    if (l != arr[0] && l != null && env != null) {
                        Object obj = Access.LANGS.findExportedSymbol(env, globalName, false);
                        if (obj != null) {
                            next = obj;
                            arr[0] = l;
                            return next;
                        }
                    }
                }
                return next = this;
            }
        }
        Object g = globals.get(globalName);
        final Collection<? extends Language> uniqueLang = getLanguages().values();
        return new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new SymbolIterator(new LinkedHashSet<>(uniqueLang), g);
            }
        };
    }

    boolean checkThread() {
        if (initThread != Thread.currentThread()) {
            throw new IllegalStateException("PolyglotEngine created on " + initThread.getName() + " but used on " + Thread.currentThread().getName());
        }
        if (disposed) {
            throw new IllegalStateException("Engine has already been disposed");
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    void dispatch(Object ev, int type) {
        if (type == Accessor.EngineSupport.EXECUTION_EVENT) {
            dispatchExecutionEvent(ev);
        }
        if (type == Accessor.EngineSupport.SUSPENDED_EVENT) {
            dispatchSuspendedEvent(ev);
        }
        Class clazz = ev.getClass();
        dispatch(clazz, ev);
    }

    /**
     * just to make javac happy.
     *
     * @param event
     */
    void dispatchSuspendedEvent(Object event) {
    }

    /**
     * just to make javac happy.
     *
     * @param event
     */
    void dispatchExecutionEvent(Object event) {
    }

    @SuppressWarnings("unchecked")
    <Event> void dispatch(Class<Event> type, Event event) {
        for (EventConsumer handler : handlers) {
            if (handler.type == type) {
                handler.on(event);
            }
        }
    }

    abstract static class PolyglotRootNode extends RootNode {

        protected final PolyglotEngine engine;

        PolyglotRootNode(Class<? extends TruffleLanguage> language, PolyglotEngine engine) {
            super(language, null, null);
            this.engine = engine;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            ContextStore prev = ExecutionImpl.executionStarted(engine.context());
            Access.DEBUG.executionStarted(engine);
            try {
                return executeImpl(frame);
            } finally {
                ExecutionImpl.executionEnded(prev);
            }
        }

        protected abstract Object executeImpl(VirtualFrame frame);
    }

    private static class PolyglotEvalRootNode extends PolyglotRootNode {

        private static final Object[] EMPTY_ARRAY = new Object[0];

        @Child private DirectCallNode call;
        private TruffleLanguage<?> fillLanguage;
        private final Language language;
        private final Source source;
        private final PolyglotEngine engine;

        PolyglotEvalRootNode(PolyglotEngine engine, Language language, Source source) {
            super(TruffleLanguage.class, engine);
            this.engine = engine;
            this.source = source;
            this.language = language;
        }

        public PolyglotEngine getEngine() {
            return engine;
        }

        @Override
        protected Object executeImpl(VirtualFrame frame) {
            TruffleLanguage[] fillLang = (TruffleLanguage[]) frame.getArguments()[0];
            if (call == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initialize();
            }
            fillLang[0] = fillLanguage;
            return call.call(EMPTY_ARRAY);
        }

        private void initialize() {
            TruffleLanguage<?> languageImpl = language.getImpl(true);
            CallTarget target = Access.LANGS.parse(languageImpl, source, null);
            if (target == null) {
                throw new NullPointerException("Parsing has not produced a CallTarget for " + source);
            }
            fillLanguage = languageImpl;
            call = insert(DirectCallNode.create(target));
        }
    }

    /**
     * A future value wrapper. A user level wrapper around values returned by evaluation of various
     * {@link PolyglotEngine} functions like {@link PolyglotEngine#findGlobalSymbol(String)} and
     * {@link PolyglotEngine#eval(Source)} or a value returned by {@link #invoke(Object, Object...)
     * a subsequent execution}. In case the {@link PolyglotEngine} has been initialized for
     * {@link Builder#executor(java.util.concurrent.Executor) asynchronous execution}, the
     * {@link Value} represents a future - i.e., it is returned immediately, leaving the execution
     * running on behind.
     *
     * @since 0.9
     */
    public abstract class Value {
        private final TruffleLanguage<?>[] language;
        private CallTarget target;

        Value(TruffleLanguage<?>[] language) {
            this.language = language;
        }

        abstract boolean isDirect();

        abstract Object value();

        /**
         * Returns the object represented by this value, possibly null. The <em>raw</em> object can
         * either be a wrapped primitive type (e.g. {@link Number}, {@link String},
         * {@link Character}, {@link Boolean}) or a {@link TruffleObject} representing more complex
         * object created by a language.
         *
         * @return the object, possibly <code>null</code>
         * @throws Exception in case it is not possible to obtain the value of the object
         * @since 0.9
         */
        public Object get() {
            return get(true, true);
        }

        private Object get(boolean unwrapJava, boolean wrapEngine) {
            assertNoTruffle();
            Object result = waitForSymbol();
            if (result instanceof TruffleObject) {
                if (unwrapJava) {
                    result = JavaInterop.asJavaObject(Object.class, (TruffleObject) result);
                }
                if (wrapEngine && result instanceof TruffleObject) {
                    return EngineTruffleObject.wrap(PolyglotEngine.this, result);
                }
            }
            return result;
        }

        /**
         * Creates Java-typed access to the object wrapped by this {@link Value}. Results depend on
         * the requested type:
         * <ul>
         * <li>For primitive types such as {@link Number}, the value is simply cast and returned.
         * </li>
         * <li>A {@link String} is produced by the language that returned the value.</li>
         * <li>A {@link FunctionalInterface} instance is returned if the value
         * {@link Message#IS_EXECUTABLE can be executed}.</li>
         * <li>Aggregate types such as {@link List} and {@link Map} are supported, including when
         * used in combination with nested generics.</li>
         * </ul>
         *
         * <h5>Java interoperation examples</h5>
         * <p>
         * The method {@link PolyglotEngine.Value#as(Class)} plays an essential role supporting
         * interoperation between Java and guest languages. Examples of basic Java interoperation
         * (access to JavaScript functions, simple objects, and classes) appear in the method
         * documentation for {@link PolyglotEngine#eval(Source)}. The examples here demonstrate Java
         * access to more complex data structures.
         * <p>
         * The examples use JavaScript as the guest language, assuming that a Truffle implementation
         * of JavaScript is on the JVM class path. In each example a
         * {@link Source#newBuilder(String) Source} builder creates a literal fragment of JavaScript
         * code.
         *
         * <h6>Java interop example: Java access to a JavaScript array with typed elements</h6>
         * <p>
         * This example shows how Java can be given type-safe access to members of a JavaScript
         * array with members of a known type.
         * <p>
         * In this example a fragment of JavaScript code named {@code "ArrayOfPoints.js"} defines an
         * anonymous function of no arguments. Evaluation of that code returns the JavaScript
         * function wrapped in a {@link Value}. The method {@link Value#as(Class)} creates a Java
         * object (an instance of functional interface {@code PointProvider}) that supports calls to
         * the JavaScript function. The JavaScript list returned by the function is made available
         * as Java type {@code List<Point>}.
         *
         *
         * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptArrayWithTypedElementsFromJava}
         *
         * <h6>Java interop example: Java access to JavaScript JSON data</h6>
         *
         * Imagine a need to access a JavaScript JSON-like structure from Java with type safety.
         * This example is based on JSON data returned by a GitHub API.
         * <p>
         * The GitHub response contains a list of repository objects. Each repository has an id,
         * name, list of URLs, and a nested structure describing its owner. Interfaces
         * {@code Repository} and {@code Owner} define the structure as Java types.
         * <p>
         * In the example a fragment of Javascript code named {@code "github-api-value.js"} defines
         * an anonymous function of no arguments. Evaluation of that code returns the JavaScript
         * function, wrapped in a {@link Value} that can be executed directly. Execution of that
         * function returns a JavaScript mock JSON parser function, also wrapped in a {@link Value}.
         * The method {@link Value#as(Class)} creates a Java object (an instance of functional
         * interface {@code ParseJSON}) that supports Java calls to the mock parser, producing
         * results that can be inspected in a type-safe way.
         *
         * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptJSONObjectFromJava}
         *
         * <p>
         * Other examples of Java to dynamic language interop can be found in documentation of
         * {@link PolyglotEngine#eval(Source)} and {@link #execute(Object...)} methods.
         *
         * @param <T> the type of the requested view
         * @param representation an interface describing the the requested view (must be an
         *            interface)
         * @return instance of the view wrapping the object of this value
         * @throws Exception in case it is not possible to obtain the value of the object
         * @throws ClassCastException if the value cannot be converted to desired view
         * @since 0.9
         */
        public <T> T as(final Class<T> representation) {
            assertNoTruffle();
            final Object obj = get(true, false);
            if (obj instanceof EngineTruffleObject) {
                EngineTruffleObject eto = (EngineTruffleObject) obj;
                if (representation.isInstance(eto.getDelegate())) {
                    return representation.cast(eto.getDelegate());
                }
            }
            if (representation == String.class) {
                final Class<? extends TruffleLanguage> clazz = language[0].getClass();
                Object unwrapped = obj;
                while (unwrapped instanceof EngineTruffleObject) {
                    unwrapped = ((EngineTruffleObject) obj).getDelegate();
                }
                return representation.cast(Access.LANGS.toStringIfVisible(language[0], findEnv(clazz), unwrapped, null));
            }
            if (representation.isInstance(obj)) {
                return representation.cast(obj);
            }
            return JavaInterop.asJavaObject(representation, (TruffleObject) get(false, true));
        }

        /**
         * Invokes the symbol. If the symbol represents a function, then it should be invoked with
         * provided arguments. If the symbol represents a field, then first argument (if provided)
         * should set the value to the field; the return value should be the actual value of the
         * field when the <code>invoke</code> method returns.
         *
         * @param thiz this/self in language that support such concept; use <code>null</code> to let
         *            the language use default this/self or ignore the value
         * @param args arguments to pass when invoking the symbol
         * @return symbol wrapper around the value returned by invoking the symbol, never
         *         <code>null</code>
         * @throws Exception signals problem during execution
         * @since 0.9
         */
        @Deprecated
        public Value invoke(final Object thiz, final Object... args) {
            return execute(args);
        }

        /**
         * Executes this value. If the value represents a function, then it should be invoked with
         * provided arguments. If the value represents a field, then first argument (if provided)
         * should set the value to the field; the return value should be the actual value of the
         * field when this method returns.
         * <p>
         * All {@linkplain Language languages} accept wrappers of Java primitive types (e.g.
         * {@link java.lang.Byte}, {@link java.lang.Short}, {@link java.lang.Integer},
         * {@link java.lang.Long}, {@link java.lang.Float}, {@link java.lang.Double},
         * {@link java.lang.Character}, {@link java.lang.Boolean}, and {@link java.lang.String}) or
         * generic {@link TruffleObject objects created} by one of the languages as parameters of
         * their functions (or other objects that can be executed). In addition to that the
         * <code>execute</code> method converts plain Java objects into appropriate wrappers to make
         * them easily accessible from dynamic languages.
         *
         * <h5>Java interoperation examples</h5>
         * <p>
         * The method {@link PolyglotEngine.Value#execute(Object...)} plays an essential role
         * supporting interoperation between Java and guest languages. The examples here demonstrate
         * JavaScript access to Java elements. Access in the other direction, Java access to
         * JavaScript elements appear in method documentation for
         * {@link PolyglotEngine#eval(Source)} and {@link Value#as(Class)}.
         * <p>
         * The examples use JavaScript as the guest language, assuming that a Truffle implementation
         * of JavaScript is on the JVM class path. In each example a
         * {@link Source#newBuilder(String) Source} builder creates a literal fragment of JavaScript
         * code.
         *
         * <h6>Java interop example: JavaScript access to Java object fields and methods</h6>
         *
         * This example shows how to expose <b>public</b> members of Java objects to scripts written
         * in dynamic languages. In the example a JavaScript function is able to access fields in
         * Java objects of type {@code Moment}.
         * <p>
         * Evaluating the JavaScript code fragment named {@code "MomentToSeconds.js"} produces a
         * JavaScript function wrapped in a {@link Value}. This can be executed directly, as shown,
         * which returns a JavaScript number that is also wrapped in a {@link Value}. Converting the
         * result to Java requires using the method {@link Value#as(Class)}.
         *
         * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObject}
         *
         * The explicit conversion of the result in the above example can be avoided by explicitly
         * converting the type of the <em>function</em> instead. In the following variation, the
         * JavaScript function is given the Java functional type {@code MomentConvertor}, which
         * returns a Java {@code int}.
         *
         * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConverter}
         *
         * <h6>Java interop example: JavaScript access to Java static methods and constructors</h6>
         *
         * Dynamic languages can also access <b>public static methods</b> and <b>public
         * constructors</b> of Java classes, if they can get reference to them. In this example a
         * JavaScript function (created by evaluating the JavaScript code fragment named
         * {@code "ConstructMoment.js"}) creates a JavaScript factory for a Java class passed to it
         * as an argument. The factory invokes the class constructor and returns the new Java object
         * instance wrapped in a {@link Value}.
         *
         * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createJavaScriptFactoryForJavaClass}
         *
         * <p>
         * Additional examples of Java/dynamic language interop can be found in description of
         * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)
         * PolyglotEngine.eval(Source)} and {@link #as(Class) Value.as(Class)} methods.
         *
         * @param args arguments to pass when invoking the value
         * @return a non-null {@link Value} that holds the result
         * @throws Exception signals problem during execution
         * @since 0.9
         */
        public Value execute(final Object... args) {
            if (isDirect()) {
                Object ret = executeDirect(args);
                return new DirectValue(language, ret);
            }
            assertNoTruffle();

            get();
            ComputeInExecutor<Object> invokeCompute = new ComputeInExecutor<Object>(executor) {
                @SuppressWarnings("try")
                @Override
                protected Object compute() {
                    return executeDirect(args);
                }
            };
            invokeCompute.perform();
            return new ExecutorValue(language, invokeCompute);
        }

        @SuppressWarnings("try")
        private Object executeDirect(Object[] args) {
            if (target == null) {
                target = SymbolInvokerImpl.createExecuteSymbol(language[0], PolyglotEngine.this, value());
            }
            return target.call(args);
        }

        private Object waitForSymbol() {
            assertNoTruffle();
            assert checkThread();
            return value();
        }
    }

    private class DirectValue extends Value {
        private final Object value;

        DirectValue(TruffleLanguage<?>[] language, Object value) {
            super(language);
            this.value = value;
        }

        @Override
        boolean isDirect() {
            return true;
        }

        @Override
        Object value() {
            return value;
        }

        @Override
        public String toString() {
            return "PolyglotEngine.Value[value=" + value + ",computed=true,exception=null]";
        }
    }

    private class ExecutorValue extends Value {
        private final ComputeInExecutor<Object> compute;

        ExecutorValue(TruffleLanguage<?>[] language, ComputeInExecutor<Object> compute) {
            super(language);
            this.compute = compute;
        }

        @Override
        boolean isDirect() {
            return false;
        }

        @Override
        Object value() {
            return compute.get();
        }

        @Override
        public String toString() {
            return "PolyglotEngine.Value[" + compute + "]";
        }
    }

    /**
     * A handle for an <em>instrument</em> installed in an engine that can observe and inject
     * behavior into language execution. The handle provides access to the instrument's metadata and
     * allows the instrument to be dynamically {@linkplain Instrument#setEnabled(boolean)
     * enabled/disabled} in the engine.
     *
     * @see PolyglotEngine#getInstruments()
     * @since 0.9
     */
    public final class Instrument {

        private final InstrumentCache info;
        private final Object instrumentLock = new Object();
        private volatile boolean enabled;

        Instrument(InstrumentCache cache) {
            this.info = cache;
        }

        /**
         * Gets the id clients can use to acquire this instrument.
         *
         * @return this instrument's unique id
         * @since 0.9
         */
        public String getId() {
            return info.getId();
        }

        /**
         * Gets a human readable name of this instrument.
         *
         * @return this instrument's user-friendly name
         * @since 0.9
         */
        public String getName() {
            return info.getName();
        }

        /**
         * Gets the version of this instrument.
         *
         * @return this instrument's version
         * @since 0.9
         */
        public String getVersion() {
            return info.getVersion();
        }

        InstrumentCache getCache() {
            return info;
        }

        /**
         * Returns whether this instrument is currently enabled in the engine.
         *
         * @return this instrument's status in the engine
         * @since 0.9
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Returns an additional service provided by this instrument, specified by type.
         * <p>
         * Here is an example for locating a hypothetical <code>DebuggerController</code>:
         *
         * {@codesnippet DebuggerExampleTest}
         *
         * @param <T> the type of the service
         * @param type class of the service that is being requested
         * @return instance of requested type, <code>null</code> if no such service is available
         * @since 0.9
         */
        public <T> T lookup(Class<T> type) {
            return Access.INSTRUMENT.getInstrumentationHandlerService(instrumentationHandler, this, type);
        }

        /**
         * Enables/disables this instrument in the engine.
         *
         * @param enabled <code>true</code> to enable <code>false</code> to disable
         * @since 0.9
         */
        public void setEnabled(final boolean enabled) {
            if (disposed) {
                throw new IllegalStateException("Engine has already been disposed");
            }
            if (executor == null) {
                setEnabledImpl(enabled, true);
            } else {
                ComputeInExecutor<Void> compute = new ComputeInExecutor<Void>(executor) {
                    @Override
                    protected Void compute() {
                        setEnabledImpl(enabled, true);
                        return null;
                    }
                };
                compute.perform();
            }
        }

        void setEnabledImpl(final boolean enabled, boolean cleanup) {
            synchronized (instrumentLock) {
                if (this.enabled != enabled) {
                    if (enabled) {
                        Access.INSTRUMENT.addInstrument(instrumentationHandler, this, getCache().getInstrumentationClass());
                    } else {
                        Access.INSTRUMENT.disposeInstrument(instrumentationHandler, this, cleanup);
                    }
                    this.enabled = enabled;
                }
            }
        }

        /** @since 0.9 */
        @Override
        public String toString() {
            return "Instrument [id=" + getId() + ", name=" + getName() + ", version=" + getVersion() + ", enabled=" + enabled + "]";
        }
    }

    /**
     * A handle for a Truffle language installed in a {@link PolyglotEngine}. The handle provides
     * access to the language's metadata, including the language's {@linkplain #getName() name},
     * {@linkplain #getVersion() version}, and supported {@linkplain #getMimeTypes() MIME types}.
     * <p>
     * Documentation about how Truffle languages are implemented begins with the abstract class
     * {@link TruffleLanguage}.
     *
     * @see PolyglotEngine#getLanguages()
     * @since 0.9
     */
    public class Language {
        private final Map<Source, CallTarget> cache;
        private final LanguageCache info;
        private volatile TruffleLanguage.Env env;
        private volatile TruffleLanguage<?> language;

        Language(LanguageCache info) {
            this.cache = new WeakHashMap<>();
            this.info = info;
        }

        /**
         * Gets the MIME types supported by this language.
         *
         * @return an immutable set of supported MIME types
         * @since 0.9
         */
        public Set<String> getMimeTypes() {
            return info.getMimeTypes();
        }

        /**
         * Gets the human-readable name of this language.
         *
         * @return a human-friendly name
         * @since 0.9
         */
        public String getName() {
            return info.getName();
        }

        /**
         * Gets the version of this language.
         *
         * @return a version string
         * @since 0.9
         */
        public String getVersion() {
            return info.getVersion();
        }

        /**
         * Returns whether this language supports interactive evaluation of {@link Source sources}.
         * Such languages should be displayed in interactive environments and presented to the user.
         *
         * @return <code>true</code> if and only if this language implements an interactive response
         *         to evaluation of interactive sources.
         * @since 0.22
         */
        public boolean isInteractive() {
            return info.isInteractive();
        }

        /**
         * Evaluates code using this language, ignoring the code's {@link Source#getMimeType() MIME
         * type}.
         * <p>
         * When evaluating an {@link Source#isInteractive() interactive source} the result of the
         * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval evaluation} is
         * {@link TruffleLanguage#isVisible(Object, Object) tested to be visible} and if the value
         * is visible, it gets {@link TruffleLanguage#toString(Object, Object) converted to string}
         * and printed to {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut standard
         * output}.
         *
         * @param source code to execute
         * @return a non-null {@link Value} that holds the result
         * @throws Exception thrown to signal errors while processing the code
         * @since 0.9
         */
        public Value eval(Source source) {
            assertNoTruffle();
            return PolyglotEngine.this.evalImpl(this, source);
        }

        /**
         * Returns this language's <em>global object</em>, {@code null} if not supported.
         * <p>
         * The result is expected to be a {@link TruffleObject} (e.g. a native object from the other
         * language) but technically it can also be one of Java's primitive wrappers (
         * {@link Integer} , {@link Double}, {@link Short}, etc.).
         *
         * @return this language's global object, <code>null</code> if the language has none
         * @since 0.9
         */
        @SuppressWarnings("try")
        public Value getGlobalObject() {
            assert checkThread();
            ContextStore prev = ExecutionImpl.executionStarted(context);
            try {
                Object res = Access.LANGS.languageGlobal(getEnv(true));
                if (res == null) {
                    return null;
                }
                return new DirectValue(new TruffleLanguage[]{getImpl(true)}, res);
            } finally {
                ExecutionImpl.executionEnded(prev);
            }
        }

        TruffleLanguage<?> getImpl(boolean create) {
            getEnv(create);
            return language;
        }

        private Map<String, Object> getArgumentsForLanguage() {
            if (config == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> forLanguage = new HashMap<>();
            for (Object[] mimeKeyValue : config) {
                if (getMimeTypes().contains(mimeKeyValue[0])) {
                    forLanguage.put((String) mimeKeyValue[1], mimeKeyValue[2]);
                }
            }
            return Collections.unmodifiableMap(forLanguage);
        }

        TruffleLanguage.Env getEnv(boolean create) {
            return getEnv(create, false);
        }

        TruffleLanguage.Env getEnv(boolean create, boolean clear) {
            TruffleLanguage.Env tmp = env;
            if ((tmp == null && create) || clear) {
                // getEnv is accessed from the instrumentation code so it needs to be thread-safe.
                synchronized (this) {
                    tmp = env;
                    if (tmp == null && create) {
                        language = info.loadLanguage();
                        env = tmp = Access.LANGS.attachEnv(PolyglotEngine.this, language, out, err, in, getArgumentsForLanguage());
                        Access.LANGS.postInitEnv(env);
                    }
                    if (clear) {
                        language = null;
                        env = null;
                    }
                }
            }
            return tmp;
        }

        /** @since 0.9 */
        @Override
        public String toString() {
            return "[" + getName() + "@ " + getVersion() + " for " + getMimeTypes() + "]";
        }

    } // end of Language

    //
    // Accessor helper methods
    //

    TruffleLanguage<?> findLanguage(Class<? extends TruffleLanguage> languageClazz) {
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            final TruffleLanguage<?> impl = languageDescription.getImpl(false);
            if (languageClazz.isInstance(impl)) {
                return impl;
            }
        }
        return null;
    }

    TruffleLanguage<?> findLanguage(String mimeType) {
        Language languageDescription = this.langs.get(mimeType);
        if (languageDescription != null) {
            return languageDescription.getImpl(true);
        }
        return null;
    }

    Env findEnv(Class<? extends TruffleLanguage> languageClazz) {
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            Env env = languageDescription.getEnv(false);
            if (env != null && languageClazz.isInstance(languageDescription.getImpl(false))) {
                return env;
            }
        }
        throw new IllegalStateException("Cannot find language " + languageClazz + " among " + langs);
    }

    static class Access {
        static final Accessor.LanguageSupport LANGS = SPIAccessor.langs();
        static final Accessor.InstrumentSupport INSTRUMENT = SPIAccessor.instrumentAccess();
        static final Accessor.DebugSupport DEBUG = SPIAccessor.debugAccess();

        static Collection<ClassLoader> loaders() {
            return SPI.allLoaders();
        }
    }

    private static class SPIAccessor extends Accessor {
        static LanguageSupport langs() {
            return SPI.languageSupport();
        }

        static InstrumentSupport instrumentAccess() {
            return SPI.instrumentSupport();
        }

        static DebugSupport debugAccess() {
            return SPI.debugSupport();
        }

        Collection<ClassLoader> allLoaders() {
            return loaders();
        }

        @Override
        protected EngineSupport engineSupport() {
            return new EngineImpl();
        }

        static final class EngineImpl extends EngineSupport {

            @Override
            public boolean isEvalRoot(RootNode target) {
                if (target instanceof PolyglotEvalRootNode) {
                    if (((PolyglotEvalRootNode) target).getEngine() == ExecutionImpl.findVM()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Object findLanguage(Class<? extends TruffleLanguage> language) {
                if (language == TruffleLanguage.class) {
                    return null;
                }
                return ((PolyglotEngine) ExecutionImpl.findVM()).findLanguage(language);
            }

            @Override
            public boolean isMimeTypeSupported(Object obj, String mimeType) {
                final PolyglotEngine vm = (PolyglotEngine) obj;
                return vm.findLanguage(mimeType) != null;
            }

            @Override
            public Env findEnv(Object obj, Class<? extends TruffleLanguage> languageClass) {
                PolyglotEngine vm = (PolyglotEngine) obj;
                return vm.findEnv(languageClass);
            }

            @Override
            public void dispatchEvent(Object obj, Object event, int type) {
                PolyglotEngine vm = (PolyglotEngine) obj;
                vm.dispatch(event, type);
            }

            @Override
            public TruffleLanguage<?> findLanguageImpl(Object obj, Class<? extends TruffleLanguage> languageClazz, String mimeType) {
                final PolyglotEngine vm = (PolyglotEngine) (obj == null ? ExecutionImpl.findVM() : obj);
                if (vm == null) {
                    throw new IllegalStateException("Accessor.findLanguageImpl access to vm");
                }
                TruffleLanguage<?> language = null;
                if (languageClazz != null) {
                    language = vm.findLanguage(languageClazz);
                }
                if (language == null && mimeType != null) {
                    language = vm.findLanguage(mimeType);
                }
                if (language == null) {
                    throw new IllegalStateException("Cannot find language " + languageClazz + " with mimeType " + mimeType + " among " + vm.langs);
                }
                return language;
            }

            @Override
            public Object getInstrumentationHandler(Object obj) {
                final PolyglotEngine vm = (PolyglotEngine) (obj == null ? ExecutionImpl.findVM() : obj);
                return vm == null ? null : vm.instrumentationHandler;
            }

            @Override
            public Iterable<? extends Object> importSymbols(Object vmObj, TruffleLanguage<?> ownLang, String globalName) {
                PolyglotEngine vm = (PolyglotEngine) vmObj;
                return vm.importSymbol(new TruffleLanguage<?>[]{ownLang}, globalName);
            }

            @Override
            public <C> FindContextNode<C> createFindContextNode(TruffleLanguage<C> lang) {
                return new FindContextNodeImpl<>(lang);
            }

            @Override
            public void registerDebugger(Object vm, Object debugger) {
                PolyglotEngine engine = (PolyglotEngine) vm;
                assert engine.debugger()[0] == null || engine.debugger()[0] == debugger;
                engine.debugger()[0] = debugger;
            }

            @Override
            public Object findOriginalObject(Object truffleObject) {
                if (truffleObject instanceof EngineTruffleObject) {
                    return ((EngineTruffleObject) truffleObject).getDelegate();
                }
                return truffleObject;
            }
        }

    } // end of SPIAccessor

}

class PolyglotEngineSnippets {
    abstract class YourLang extends TruffleLanguage<Object> {
        public static final String MIME_TYPE = "application/my-test-lang";
    }

    public static int evalCode() {
        // @formatter:off
        // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#evalCode
        Source src = Source.newBuilder("3 + 39").
                        mimeType("application/my-test-lang").
                        name("example.test-lang").
                        build();
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        Value result = engine.eval(src);
        int answer = result.as(Integer.class);
        // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#evalCode
        // @formatter:on
        return answer;
    }

    public static PolyglotEngine initializeWithParameters() {
        // @formatter:off
        // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters
        String[] args = {"--kernel", "Kernel.som", "--instrument", "dyn-metrics"};
        PolyglotEngine.Builder builder = PolyglotEngine.newBuilder();
        builder.config(YourLang.MIME_TYPE, "CMD_ARGS", args);
        PolyglotEngine engine = builder.build();
        // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters
        // @formatter:on
        return engine;
    }

    // @formatter:off
    // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop
    public static final class Multiplier {
        public static int mul(int x, int y) {
            return x * y;
        }
    }

    public interface Multiply {
        int mul(int x, int y);
    }

    public static PolyglotEngine configureJavaInterop(Multiply multiply) {
        TruffleObject staticAccess = JavaInterop.asTruffleObject(Multiplier.class);
        TruffleObject instanceAccess = JavaInterop.asTruffleObject(multiply);

        PolyglotEngine engine = PolyglotEngine.newBuilder().
            globalSymbol("mul", staticAccess).
            globalSymbol("compose", instanceAccess).
            build();

        return engine;
    }
    // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop
    // @formatter:on

    static PolyglotEngine configureJavaInteropWithMul() {
        PolyglotEngineSnippets.Multiply multi = new PolyglotEngineSnippets.Multiply() {
            @Override
            public int mul(int x, int y) {
                return x * y;
            }
        };
        return configureJavaInterop(multi);
    }

    // @formatter:off
    // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#findAndReportMultipleExportedSymbols
    static Value findAndReportMultipleExportedSymbols(
                      PolyglotEngine engine, String name) {
        Value found = null;
        for (Value value : engine.findGlobalSymbols(name)) {
            if (found != null) {
                throw new IllegalStateException(
                    "Multiple global symbols exported with " + name + " name"
                );
            }
            found = value;
        }
        return found;
    }
    // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#findAndReportMultipleExportedSymbols
    // @formatter:on
}
