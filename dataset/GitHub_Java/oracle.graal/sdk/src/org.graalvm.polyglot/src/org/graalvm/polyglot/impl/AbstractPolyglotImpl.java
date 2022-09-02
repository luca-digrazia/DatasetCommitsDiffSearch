/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.AccessibleObject;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Handler;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;

@SuppressWarnings("unused")
public abstract class AbstractPolyglotImpl {

    protected AbstractPolyglotImpl() {
        if (!getClass().getName().equals("com.oracle.truffle.polyglot.PolyglotImpl") && !getClass().getName().equals("org.graalvm.polyglot.Engine$PolyglotInvalid")) {
            throw new AssertionError("Only one implementation Engine.Impl allowed.");
        }
    }

    public abstract static class MonitoringAccess {
        protected MonitoringAccess() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.management.ExecutionListener.MonitoringAccessImpl")) {
                throw new AssertionError("Only one implementation of MonitoringAccessImpl allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract ExecutionEvent newExecutionEvent(Object event);
    }

    public abstract static class APIAccess {

        protected APIAccess() {
            if (!getClass().getCanonicalName().equals("org.graalvm.polyglot.Engine.APIAccessImpl")) {
                throw new AssertionError("Only one implementation of APIAccess allowed. " + getClass().getCanonicalName());
            }
        }

        public abstract boolean useContextClassLoader();

        public abstract Engine newEngine(AbstractEngineImpl impl);

        public abstract Context newContext(AbstractContextImpl impl);

        public abstract PolyglotException newLanguageException(String message, AbstractExceptionImpl impl);

        public abstract Language newLanguage(AbstractLanguageImpl impl);

        public abstract Instrument newInstrument(AbstractInstrumentImpl impl);

        public abstract Value newValue(Object value, AbstractValueImpl impl);

        public abstract Source newSource(String language, Object impl);

        public abstract SourceSection newSourceSection(Source source, Object impl);

        public abstract Object getReceiver(Value value);

        public abstract AbstractValueImpl getImpl(Value engine);

        public abstract AbstractEngineImpl getImpl(Engine engine);

        public abstract AbstractExceptionImpl getImpl(PolyglotException value);

        public abstract AbstractStackFrameImpl getImpl(StackFrame value);

        public abstract AbstractLanguageImpl getImpl(Language value);

        public abstract AbstractInstrumentImpl getImpl(Instrument value);

        public abstract StackFrame newPolyglotStackTraceElement(PolyglotException e, AbstractStackFrameImpl impl);

        public abstract boolean allowAccess(HostAccess conf, AccessibleObject member);
    }

    // shared SPI

    APIAccess api;
    MonitoringAccess monitoring;

    public final void setMonitoring(MonitoringAccess monitoring) {
        this.monitoring = monitoring;
    }

    public final void setConstructors(APIAccess constructors) {
        this.api = constructors;
        initialize();
    }

    public APIAccess getAPIAccess() {
        return api;
    }

    public MonitoringAccess getMonitoring() {
        return monitoring;
    }

    protected void initialize() {
    }

    public abstract Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> arguments, long timeout, TimeUnit timeoutUnit, boolean sandbox,
                    long maximumAllowedAllocationBytes, boolean useSystemProperties, boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, Object logHandlerOrStream,
                    HostAccess conf);

    public abstract void preInitializeEngine();

    public abstract void resetPreInitializedEngine();

    public abstract AbstractSourceImpl getSourceImpl();

    public abstract AbstractSourceSectionImpl getSourceSectionImpl();

    public abstract AbstractExecutionListenerImpl getExecutionListenerImpl();

    public abstract Path findHome();

    public abstract static class AbstractExecutionListenerImpl {

        protected AbstractExecutionListenerImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract List<Value> getInputValues(Object impl);

        public abstract SourceSection getLocation(Object impl);

        public abstract String getRootName(Object impl);

        public abstract Value getReturnValue(Object impl);

        public abstract boolean isExpression(Object impl);

        public abstract boolean isStatement(Object impl);

        public abstract boolean isRoot(Object impl);

        public abstract void closeExecutionListener(Object impl);

        public abstract Object attachExecutionListener(Engine engine, Consumer<ExecutionEvent> onEnter,
                        Consumer<ExecutionEvent> onReturn,
                        boolean expressions,
                        boolean statements,
                        boolean roots,
                        Predicate<Source> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions);

        public abstract PolyglotException getException(Object impl);

    }

    public abstract static class AbstractSourceImpl {

        protected final AbstractPolyglotImpl engineImpl;

        protected AbstractSourceImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
            this.engineImpl = engineImpl;
        }

        public abstract Source build(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached) throws IOException;

        public abstract String getName(Object impl);

        public abstract String getPath(Object impl);

        public abstract boolean isInteractive(Object impl);

        public abstract URL getURL(Object impl);

        public abstract URI getURI(Object impl);

        public abstract Reader getReader(Object impl);

        public abstract InputStream getInputStream(Object impl);

        public abstract int getLength(Object impl);

        public abstract CharSequence getCode(Object impl);

        public abstract CharSequence getCode(Object impl, int lineNumber);

        public abstract int getLineCount(Object impl);

        public abstract int getLineNumber(Object impl, int offset);

        public abstract int getColumnNumber(Object impl, int offset);

        public abstract int getLineStartOffset(Object impl, int lineNumber);

        public abstract int getLineLength(Object impl, int lineNumber);

        public abstract String toString(Object impl);

        public abstract int hashCode(Object impl);

        public abstract boolean equals(Object impl, Object otherImpl);

        public abstract boolean isInternal(Object impl);

        public abstract String findLanguage(File file) throws IOException;

        public abstract String findLanguage(URL url) throws IOException;

        public abstract String findLanguage(String mimeType);

        public abstract String findMimeType(File file) throws IOException;

        public abstract String findMimeType(URL url) throws IOException;

        public abstract ByteSequence getBytes(Object impl);

        public abstract boolean hasCharacters(Object impl);

        public abstract boolean hasBytes(Object impl);

        public abstract String getMimeType(Object impl);

    }

    public abstract static class AbstractSourceSectionImpl {

        protected AbstractSourceSectionImpl(AbstractPolyglotImpl polyglotImpl) {
            Objects.requireNonNull(polyglotImpl);
        }

        public abstract boolean isAvailable(Object impl);

        public abstract boolean hasLines(Object impl);

        public abstract boolean hasColumns(Object impl);

        public abstract boolean hasCharIndex(Object impl);

        public abstract int getStartLine(Object impl);

        public abstract int getStartColumn(Object impl);

        public abstract int getEndLine(Object impl);

        public abstract int getEndColumn(Object impl);

        public abstract int getCharIndex(Object impl);

        public abstract int getCharLength(Object impl);

        public abstract int getCharEndIndex(Object impl);

        public abstract CharSequence getCode(Object impl);

        public abstract String toString(Object impl);

        public abstract int hashCode(Object impl);

        public abstract boolean equals(Object impl, Object obj);

    }

    public abstract static class AbstractContextImpl {

        protected AbstractContextImpl(AbstractPolyglotImpl impl) {
            if (!getClass().getName().equals("com.oracle.truffle.polyglot.PolyglotContextImpl")) {
                throw new AssertionError("Only one implementation of AbstractContextImpl allowed.");
            }
        }

        public abstract boolean initializeLanguage(String languageId);

        public abstract Value eval(String language, Object sourceImpl);

        public abstract Engine getEngineImpl(Context sourceContext);

        public abstract void close(Context sourceContext, boolean interuptExecution);

        public abstract Value asValue(Object hostValue);

        public abstract void explicitEnter(Context sourceContext);

        public abstract void explicitLeave(Context sourceContext);

        public abstract Value getBindings(String language);

        public abstract Value getPolyglotBindings();
    }

    public abstract static class AbstractEngineImpl {

        protected AbstractEngineImpl(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public abstract Language requirePublicLanguage(String id);

        public abstract Instrument requirePublicInstrument(String id);

        // Runtime

        public abstract void close(Engine sourceEngine, boolean cancelIfExecuting);

        public abstract Map<String, Instrument> getInstruments();

        public abstract Map<String, Language> getLanguages();

        public abstract String getVersion();

        public abstract OptionDescriptors getOptions();

        public abstract Context createContext(OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, boolean allowNativeAccess,
                        boolean allowCreateThread, boolean allowHostIO, boolean allowHostClassLoading, boolean allowExperimentalOptions,
                        Predicate<String> classFilter, Map<String, String> options, Map<String, String[]> arguments,
                        String[] onlyLanguages, FileSystem fileSystem, Object logHandlerOrStream);

        public abstract String getImplementationName();

    }

    public abstract static class AbstractExceptionImpl {

        protected AbstractExceptionImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract boolean isInternalError();

        public abstract boolean isCancelled();

        public abstract boolean isExit();

        public abstract int getExitStatus();

        public abstract Iterable<StackFrame> getPolyglotStackTrace();

        public abstract boolean isSyntaxError();

        public abstract Value getGuestObject();

        public abstract boolean isIncompleteSource();

        public abstract void onCreate(PolyglotException api);

        public abstract void printStackTrace(PrintStream s);

        public abstract void printStackTrace(PrintWriter s);

        public abstract StackTraceElement[] getStackTrace();

        public abstract String getMessage();

        public abstract boolean isHostException();

        public abstract Throwable asHostException();

        public abstract SourceSection getSourceLocation();

    }

    public abstract static class AbstractStackFrameImpl {

        protected AbstractStackFrameImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract StackTraceElement toHostFrame();

        public abstract SourceSection getSourceLocation();

        public abstract String getRootName();

        public abstract Language getLanguage();

        public abstract boolean isHostFrame();

        public abstract String toStringImpl(int languageColumn);

    }

    public abstract static class AbstractInstrumentImpl {

        protected AbstractInstrumentImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getId();

        public abstract String getName();

        public abstract OptionDescriptors getOptions();

        public abstract String getVersion();

        public abstract <T> T lookup(Class<T> type);

    }

    public abstract static class AbstractLanguageImpl {

        protected AbstractLanguageImpl(AbstractPolyglotImpl engineImpl) {
            Objects.requireNonNull(engineImpl);
        }

        public abstract String getName();

        public abstract String getImplementationName();

        public abstract boolean isInteractive();

        public abstract String getVersion();

        public abstract String getId();

        public abstract OptionDescriptors getOptions();

        public abstract Set<String> getMimeTypes();

        public abstract String getDefaultMimeType();
    }

    public abstract static class AbstractValueImpl {

        protected AbstractValueImpl(AbstractPolyglotImpl impl) {
            Objects.requireNonNull(impl);
        }

        public boolean hasArrayElements(Object receiver) {
            return false;
        }

        public abstract Value getArrayElement(Object receiver, long index);

        public abstract void setArrayElement(Object receiver, long index, Object value);

        public abstract boolean removeArrayElement(Object receiver, long index);

        public abstract long getArraySize(Object receiver);

        public boolean hasMembers(Object receiver) {
            return false;
        }

        public abstract Value getMember(Object receiver, String key);

        public boolean hasMember(Object receiver, String key) {
            return false;
        }

        public Set<String> getMemberKeys(Object receiver) {
            return Collections.emptySet();
        }

        public abstract void putMember(Object receiver, String key, Object member);

        public abstract boolean removeMember(Object receiver, String key);

        public boolean canExecute(Object receiver) {
            return false;
        }

        public abstract Value execute(Object receiver, Object[] arguments);

        public abstract Value execute(Object receiver);

        public boolean canInstantiate(Object receiver) {
            return false;
        }

        public abstract Value newInstance(Object receiver, Object[] arguments);

        public abstract void executeVoid(Object receiver, Object[] arguments);

        public abstract void executeVoid(Object receiver);

        public boolean canInvoke(String identifier, Object receiver) {
            return false;
        }

        public abstract Value invoke(Object receiver, String identifier, Object[] arguments);

        public abstract Value invoke(Object receiver, String identifier);

        public boolean isString(Object receiver) {
            return false;
        }

        public abstract String asString(Object receiver);

        public boolean isBoolean(Object receiver) {
            return false;
        }

        public abstract boolean asBoolean(Object receiver);

        public boolean fitsInInt(Object receiver) {
            return false;
        }

        public abstract int asInt(Object receiver);

        public boolean fitsInLong(Object receiver) {
            return false;
        }

        public abstract long asLong(Object receiver);

        public boolean fitsInDouble(Object receiver) {
            return false;
        }

        public abstract double asDouble(Object receiver);

        public boolean fitsInFloat(Object receiver) {
            return false;
        }

        public abstract float asFloat(Object receiver);

        public boolean isNull(Object receiver) {
            return false;
        }

        public boolean isNativePointer(Object receiver) {
            return false;
        }

        public boolean fitsInByte(Object receiver) {
            return false;
        }

        public abstract byte asByte(Object receiver);

        public boolean fitsInShort(Object receiver) {
            return false;
        }

        public abstract short asShort(Object receiver);

        public abstract long asNativePointer(Object receiver);

        public boolean isHostObject(Object receiver) {
            return false;
        }

        public boolean isProxyObject(Object receiver) {
            return false;
        }

        public abstract Object asHostObject(Object receiver);

        public abstract Object asProxyObject(Object receiver);

        public abstract String toString(Object receiver);

        public abstract Value getMetaObject(Object receiver);

        public boolean isNumber(Object receiver) {
            return false;
        }

        public abstract <T> T as(Object receiver, Class<T> targetType);

        public abstract <T> T as(Object receiver, TypeLiteral<T> targetType);

        public abstract SourceSection getSourceLocation(Object receiver);
    }

    public abstract Class<?> loadLanguageClass(String className);

    public Context getCurrentContext() {
        throw new IllegalStateException("No current context is available. Make sure the Java method is invoked by a Graal guest language or a context is entered using Context.enter().");
    }

    public abstract Collection<Engine> findActiveEngines();

    public abstract Value asValue(Object o);

}
