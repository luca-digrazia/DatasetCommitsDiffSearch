/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.api.vm.TruffleVM.Language;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * An entry point for everyone who wants to implement a Truffle based language. By providing
 * implementation of this type and registering it using {@link Registration} annotation, your
 * language becomes accessible to users of the {@link TruffleVM Truffle virtual machine} - all they
 * will need to do is to include your JAR into their application and all the Truffle goodies (multi
 * language support, multitenant hosting, debugging, etc.) will be made available to them.
 */
public abstract class TruffleLanguage {
    private final Env env;

    /**
     * Constructor to be called by subclasses.
     *
     * @param env language environment that will be available via {@link #env()} method to
     *            subclasses.
     */
    protected TruffleLanguage(Env env) {
        this.env = env;
    }

    /**
     * The annotation to use to register your language to the {@link TruffleVM Truffle} system. By
     * annotating your implementation of {@link TruffleLanguage} by this annotation you are just a
     * <em>one JAR drop to the class path</em> away from your users. Once they include your JAR in
     * their application, your language will be available to the {@link TruffleVM Truffle virtual
     * machine}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {
        /**
         * Unique name of your language. This name will be exposed to users via the
         * {@link Language#getName()} getter.
         *
         * @return identifier of your language
         */
        String name();

        /**
         * Unique string identifying the language version. This name will be exposed to users via
         * the {@link Language#getVersion()} getter.
         *
         * @return version of your language
         */
        String version();

        /**
         * List of MIME types associated with your language. Users will use them (directly or
         * indirectly) when {@link TruffleVM#eval(java.lang.String, java.lang.String) executing}
         * their code snippets or their {@link TruffleVM#eval(java.net.URI) files}.
         *
         * @return array of MIME types assigned to your language files
         */
        String[] mimeType();
    }

    protected final Env env() {
        if (this.env == null) {
            throw new NullPointerException("Accessing env before initialization is finished");
        }
        return this.env;
    }

    /**
     * Parses the provided source and generates appropriate AST. The parsing should execute no user
     * code, it should only create the {@link Node} tree to represent the source. The parsing may be
     * performed in a context (specified as another {@link Node}) or without context. The
     * {@code argumentNames} may contain symbolic names for actual parameters of the call to the
     * returned value. The result should be a call target with method
     * {@link CallTarget#call(java.lang.Object...)} that accepts as many arguments as were provided
     * via the {@code argumentNames} array.
     *
     * @param code source code to parse
     * @param context a {@link Node} defining context for the parsing
     * @param argumentNames symbolic names for parameters of
     *            {@link CallTarget#call(java.lang.Object...)}
     * @return a call target to invoke which also keeps in memory the {@link Node} tree representing
     *         just parsed <code>code</code>
     * @throws IOException thrown when I/O or parsing goes wrong
     */
    protected abstract CallTarget parse(Source code, Node context, String... argumentNames) throws IOException;

    /**
     * Called when some other language is seeking for a global symbol. This method is supposed to do
     * lazy binding, e.g. there is no need to export symbols in advance, it is fine to wait until
     * somebody asks for it (by calling this method).
     * <p>
     * The exported object can either be <code>TruffleObject</code> (e.g. a native object from the
     * other language) to support interoperability between languages, {@link String} or one of Java
     * primitive wrappers ( {@link Integer}, {@link Double}, {@link Short}, {@link Boolean}, etc.).
     * <p>
     * The way a symbol becomes <em>exported</em> is language dependent. In general it is preferred
     * to make the export explicit - e.g. call some function or method to register an object under
     * specific name. Some languages may however decide to support implicit export of symbols (for
     * example from global scope, if they have one). However explicit exports should always be
     * preferred. Implicitly exported object of some name should only be used when there is no
     * explicit export under such <code>globalName</code>. To ensure so the infrastructure first
     * asks all known languages for <code>onlyExplicit</code> symbols and only when none is found,
     * it does one more round with <code>onlyExplicit</code> set to <code>false</code>.
     *
     * @param globalName the name of the global symbol to find
     * @param onlyExplicit should the language seek for implicitly exported object or only consider
     *            the explicitly exported ones?
     * @return an exported object or <code>null</code>, if the symbol does not represent anything
     *         meaningful in this language
     */
    protected abstract Object findExportedSymbol(String globalName, boolean onlyExplicit);

    /**
     * Returns global object for the language.
     * <p>
     * The object is expected to be <code>TruffleObject</code> (e.g. a native object from the other
     * language) but technically it can be one of Java primitive wrappers ({@link Integer},
     * {@link Double}, {@link Short}, etc.).
     *
     * @return the global object or <code>null</code> if the language does not support such concept
     */
    protected abstract Object getLanguageGlobal();

    /**
     * Checks whether the object is provided by this language.
     *
     * @param object the object to check
     * @return <code>true</code> if this language can deal with such object in native way
     */
    protected abstract boolean isObjectOfLanguage(Object object);

    protected abstract ToolSupportProvider getToolSupport();

    protected abstract DebugSupportProvider getDebugSupport();

    /**
     * Finds the currently executing context for current thread.
     *
     * @param <Language> type of language making the query
     * @param language the language class
     * @return the context associated with current execution
     * @throws IllegalStateException if no context is associated with the execution
     */
    protected static <Language extends TruffleLanguage> Language findContext(Class<Language> language) {
        return language.cast(API.findLanguage(null, language));
    }

    /**
     * Represents execution environment of the {@link TruffleLanguage}. Each active
     * {@link TruffleLanguage} receives instance of the environment before any code is executed upon
     * it. The environment has knowledge of all active languages and can exchange symbols between
     * them.
     */
    public static final class Env {
        private final TruffleVM vm;
        private final TruffleLanguage lang;
        private final Reader in;
        private final Writer err;
        private final Writer out;

        Env(TruffleVM vm, Constructor<?> langConstructor, Writer out, Writer err, Reader in) {
            this.vm = vm;
            this.in = in;
            this.err = err;
            this.out = out;
            try {
                this.lang = (TruffleLanguage) langConstructor.newInstance(this);
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot construct language " + langConstructor.getDeclaringClass().getName(), ex);
            }
        }

        /**
         * Asks the environment to go through other registered languages and find whether they
         * export global symbol of specified name. The expected return type is either
         * <code>TruffleObject</code>, or one of wrappers of Java primitive types ({@link Integer},
         * {@link Double}).
         *
         * @param globalName the name of the symbol to search for
         * @return object representing the symbol or <code>null</code>
         */
        public Object importSymbol(String globalName) {
            return API.importSymbol(vm, lang, globalName);
        }

        /**
         * Input associated with this {@link TruffleVM}.
         *
         * @return reader, never <code>null</code>
         */
        public Reader stdIn() {
            return in;
        }

        /**
         * Standard output writer for this {@link TruffleVM}.
         *
         * @return writer, never <code>null</code>
         */
        public Writer stdOut() {
            return out;
        }

        /**
         * Standard error writer for this {@link TruffleVM}.
         *
         * @return writer, never <code>null</code>
         */
        public Writer stdErr() {
            return err;
        }
    }

    private static final AccessAPI API = new AccessAPI();

    private static final class AccessAPI extends Accessor {
        @Override
        protected TruffleLanguage attachEnv(TruffleVM vm, Constructor<?> langClazz, Writer stdOut, Writer stdErr, Reader stdIn) {
            Env env = new Env(vm, langClazz, stdOut, stdErr, stdIn);
            return env.lang;
        }

        @Override
        public Object importSymbol(TruffleVM vm, TruffleLanguage queryingLang, String globalName) {
            return super.importSymbol(vm, queryingLang, globalName);
        }

        private static final Map<Source, CallTarget> COMPILED = Collections.synchronizedMap(new WeakHashMap<Source, CallTarget>());

        @Override
        protected Object eval(TruffleLanguage language, Source source) throws IOException {
            CallTarget target = COMPILED.get(source);
            if (target == null) {
                target = language.parse(source, null);
                if (target == null) {
                    throw new IOException("Parsing has not produced a CallTarget for " + source);
                }
                COMPILED.put(source, target);
            }
            try {
                return target.call();
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        @Override
        protected Object findExportedSymbol(TruffleLanguage l, String globalName, boolean onlyExplicit) {
            return l.findExportedSymbol(globalName, onlyExplicit);
        }

        @Override
        protected TruffleLanguage findLanguage(TruffleVM vm, Class<? extends TruffleLanguage> languageClass) {
            return super.findLanguage(vm, languageClass);
        }

        @Override
        protected Object languageGlobal(TruffleLanguage l) {
            return l.getLanguageGlobal();
        }

        @Override
        protected ToolSupportProvider getToolSupport(TruffleLanguage l) {
            return l.getToolSupport();
        }

        @Override
        protected DebugSupportProvider getDebugSupport(TruffleLanguage l) {
            return l.getDebugSupport();
        }
    }

}
