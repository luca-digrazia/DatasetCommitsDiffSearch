/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ResourceBundle;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;


final class PolyglotLoggers {

    private PolyglotLoggers(){
    }

    static Set<String> getInternalIds() {
        return Collections.singleton(PolyglotEngineImpl.OPTION_GROUP_ENGINE);
    }

    static LoggerCache defaultSPI() {
        return LoggerCacheImpl.DEFAULT;
    }

    static LoggerCache createEngineSPI(PolyglotEngineImpl engine) {
        return new LoggerCacheImpl(new PolyglotLogHandler(engine), engine, true);
    }

    static LogRecord createLogRecord(final Level level, String loggerName, final String message, final String className, final String methodName, final Object[] parameters, final Throwable thrown) {
        return new ImmutableLogRecord(level, loggerName, message, className, methodName, parameters, thrown);
    }

    static PolyglotContextImpl getCurrentOuterContext() {
        PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
        if (currentContext != null) {
            while (currentContext.parent != null) {
                currentContext = currentContext.parent;
            }
        }
        return currentContext;
    }

    static boolean isSameLogSink(Handler h1, Handler h2) {
        if (h1 == h2) {
            return true;
        }
        if (h1 instanceof PolyglotStreamHandler && h2 instanceof PolyglotStreamHandler) {
            return ((PolyglotStreamHandler) h1).sink == ((PolyglotStreamHandler) h2).sink;
        }
        return false;
    }

    static Supplier<TruffleLogger> createCompilerLoggerProvider(PolyglotEngineImpl engine) {
        return new CompilerLoggerProvider(engine);
    }

    /**
     * Returns a {@link Handler} for given {@link Handler} or {@link OutputStream}. If the
     * {@code logHandlerOrStream} is instance of {@link Handler} the {@code logHandlerOrStream} is
     * returned. If the {@code logHandlerOrStream} is instance of {@link OutputStream} a new
     * {@link StreamHandler} is created for given stream. If the {@code logHandlerOrStream} is
     * {@code null} the {@code null} is returned. Otherwise a {@link IllegalArgumentException} is
     * thrown.
     *
     * @param logHandlerOrStream the {@link Handler} or {@link OutputStream}
     * @return {@link Handler} or {@code null}
     * @throws IllegalArgumentException if {@code logHandlerOrStream} is not {@code null} nor
     *             {@link Handler} nor {@link OutputStream}
     */
    static Handler asHandler(Object logHandlerOrStream) {
        if (logHandlerOrStream == null) {
            return null;
        }
        if (logHandlerOrStream instanceof Handler) {
            return (Handler) logHandlerOrStream;
        }
        if (logHandlerOrStream instanceof OutputStream) {
            return createStreamHandler((OutputStream) logHandlerOrStream, true, true);
        }
        throw new IllegalArgumentException("Unexpected logHandlerOrStream parameter: " + logHandlerOrStream);
    }

    /**
     * Creates a {@link Handler} printing log messages into given {@link OutputStream}.
     *
     * @param out the {@link OutputStream} to print log messages into
     * @param closeStream if true the {@link Handler#close() handler's close} method closes given
     *            stream
     * @param flushOnPublish if true the {@link Handler#flush() flush} method is called after
     *            {@link Handler#publish(java.util.logging.LogRecord) publish}
     * @return the {@link Handler}
     */
    static Handler createStreamHandler(final OutputStream out, final boolean closeStream, final boolean flushOnPublish) {
        return new PolyglotStreamHandler(out, closeStream, flushOnPublish);
    }


    interface LoggerCache {
        Handler getLogHandler();
        Map<String,Level> getLogLevels();
    }

    private static final class LoggerCacheImpl implements LoggerCache {

        static final LoggerCache DEFAULT = new LoggerCacheImpl(PolyglotLogHandler.INSTANCE, true, null);
        static final LoggerCache DISABLED;
        static {
            Handler handler = new PolyglotStreamHandler(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            }, false, false);
            DISABLED = new LoggerCacheImpl(handler, false, Collections.emptyMap());
        }

        private final Handler handler;
        private final boolean useCurrentContext;
        private final PolyglotEngineImpl engine;
        private final Map<String,Level> defaultValue;

        LoggerCacheImpl(Handler handler, PolyglotEngineImpl engine, boolean useCurrentContext) {
            Objects.requireNonNull(handler, "Handler must be non null.");
            Objects.requireNonNull(engine, "Engine must be non null.");
            this.handler = handler;
            this.useCurrentContext = useCurrentContext;
            this.engine = engine;
            this.defaultValue = null;
        }

        private LoggerCacheImpl(Handler handler, boolean useCurrentContext, Map<String,Level> defaultValue) {
            Objects.requireNonNull(handler, "Handler must be non null.");
            this.handler = handler;
            this.useCurrentContext = useCurrentContext;
            this.engine = null;
            this.defaultValue = defaultValue;
        }

        @Override
        public Handler getLogHandler() {
            return handler;
        }

        @Override
        public Map<String, Level> getLogLevels() {
            if (useCurrentContext) {
                PolyglotContextImpl context = getCurrentOuterContext();
                if (context != null) {
                    return context.config.logLevels;
                }
            }
            if (engine != null) {
                return engine.logLevels;
            }
            return defaultValue;
        }
    }

    private static final class PolyglotLogHandler extends Handler {

        private static final Handler INSTANCE = new PolyglotLogHandler();

        private final Handler fallBackHandler;

        PolyglotLogHandler() {
            this.fallBackHandler = null;
        }

        PolyglotLogHandler(PolyglotEngineImpl engine) {
            fallBackHandler = engine.logHandler;
        }

        @Override
        public void publish(final LogRecord record) {
            Handler handler = findDelegate();
            if (handler == null) {
                handler = fallBackHandler;
            }
            if (handler != null) {
                handler.publish(record);
            }
        }

        @Override
        public void flush() {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.flush();
            }
        }

        @Override
        public void close() throws SecurityException {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.close();
            }
        }

        private static Handler findDelegate() {
            final PolyglotContextImpl currentContext = getCurrentOuterContext();
            return currentContext != null ? currentContext.config.logHandler : null;
        }
    }

    private static final class ImmutableLogRecord extends LogRecord {

        private static final long serialVersionUID = 1L;

        ImmutableLogRecord(final Level level, final String loggerName, final String message, final String className, final String methodName, final Object[] parameters,
                        final Throwable thrown) {
            super(level, message);
            super.setLoggerName(loggerName);
            if (className != null) {
                super.setSourceClassName(className);
            }
            if (methodName != null) {
                super.setSourceMethodName(methodName);
            }
            Object[] copy = parameters;
            if (parameters != null && parameters.length > 0) {
                copy = new Object[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    copy[i] = safeValue(parameters[i]);
                }
            }
            super.setParameters(copy);
            super.setThrown(thrown);
        }

        @Override
        public void setLevel(Level level) {
            throw new UnsupportedOperationException("Setting Level is not supported.");
        }

        @Override
        public void setLoggerName(String name) {
            throw new UnsupportedOperationException("Setting Logger Name is not supported.");
        }

        @Override
        public void setMessage(String message) {
            throw new UnsupportedOperationException("Setting Messag is not supported.");
        }

        @SuppressWarnings("deprecation")
        @Override
        public void setMillis(long millis) {
            throw new UnsupportedOperationException("Setting Millis is not supported.");
        }

        @Override
        public void setParameters(Object[] parameters) {
            throw new UnsupportedOperationException("Setting Parameters is not supported.");
        }

        @Override
        public void setResourceBundle(ResourceBundle bundle) {
            throw new UnsupportedOperationException("Setting Resource Bundle is not supported.");
        }

        @Override
        public void setResourceBundleName(String name) {
            throw new UnsupportedOperationException("Setting Resource Bundle Name is not supported.");
        }

        @Override
        public void setSequenceNumber(long seq) {
            throw new UnsupportedOperationException("Setting Sequence Number is not supported.");
        }

        @Override
        public void setSourceClassName(String sourceClassName) {
            throw new UnsupportedOperationException("Setting Parameters is not supported.");
        }

        @Override
        public void setSourceMethodName(String sourceMethodName) {
            throw new UnsupportedOperationException("Setting Source Method Name is not supported.");
        }

        @Override
        public void setThreadID(int threadID) {
            throw new UnsupportedOperationException("Setting Thread ID is not supported.");
        }

        @Override
        public void setThrown(Throwable thrown) {
            throw new UnsupportedOperationException("Setting Throwable is not supported.");
        }

        private static Object safeValue(final Object param) {
            if (param == null || EngineAccessor.EngineImpl.isPrimitive(param)) {
                return param;
            }
            try {
                return InteropLibrary.getFactory().getUncached().asString(InteropLibrary.getFactory().getUncached().toDisplayString(param));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    private static final class PolyglotStreamHandler extends StreamHandler {

        private final OutputStream sink;
        private final boolean closeStream;
        private final boolean flushOnPublish;

        PolyglotStreamHandler(final OutputStream out, final boolean closeStream, final boolean flushOnPublish) {
            super(out, FormatterImpl.INSTANCE);
            setLevel(Level.ALL);
            this.sink = out;
            this.closeStream = closeStream;
            this.flushOnPublish = flushOnPublish;
        }

        @Override
        public synchronized void publish(LogRecord record) {
            super.publish(record);
            if (flushOnPublish) {
                flush();
            }
        }

        @SuppressWarnings("sync-override")
        @Override
        public void close() {
            if (closeStream) {
                super.close();
            } else {
                flush();
            }
        }

        private static final class FormatterImpl extends Formatter {
            private static final String FORMAT = "[%1$s] %2$s: %3$s%4$s%n";
            static final Formatter INSTANCE = new FormatterImpl();

            private FormatterImpl() {
            }

            @Override
            public String format(LogRecord record) {
                String loggerName = formatLoggerName(record.getLoggerName());
                final String message = formatMessage(record);
                String stackTrace = "";
                final Throwable exception = record.getThrown();
                if (exception != null) {
                    final StringWriter str = new StringWriter();
                    try (PrintWriter out = new PrintWriter(str)) {
                        out.println();
                        exception.printStackTrace(out);
                    }
                    stackTrace = str.toString();
                }
                return String.format(
                                FORMAT,
                                loggerName,
                                record.getLevel().getName(),
                                message,
                                stackTrace);
            }

            private static String formatLoggerName(final String loggerName) {
                final String id;
                String name;
                int index = loggerName.indexOf('.');
                if (index < 0) {
                    id = loggerName;
                    name = "";
                } else {
                    id = loggerName.substring(0, index);
                    name = loggerName.substring(index + 1);
                }
                if (name.isEmpty()) {
                    return id;
                }
                final StringBuilder sb = new StringBuilder(id);
                sb.append("::");
                sb.append(possibleSimpleName(name));
                return sb.toString();
            }

            private static String possibleSimpleName(final String loggerName) {
                int index = -1;
                for (int i = 0; i >= 0; i = loggerName.indexOf('.', i + 1)) {
                    if (i + 1 < loggerName.length() && Character.isUpperCase(loggerName.charAt(i + 1))) {
                        index = i + 1;
                        break;
                    }
                }
                return index < 0 ? loggerName : loggerName.substring(index);
            }
        }
    }

    private static final class CompilerLoggerProvider implements Supplier<TruffleLogger> {

        private final PolyglotEngineImpl engine;
        private volatile Object loggers;

        CompilerLoggerProvider(PolyglotEngineImpl engine) {
            this.engine = engine;
        }

        @Override
        public TruffleLogger get() {
            Object loggersCache = loggers;
            if (loggersCache == null) {
                synchronized (this) {
                    loggersCache = loggers;
                    if (loggersCache == null) {
                        LoggerCache spi;
                        Map<String,Level> levels;
                        if (engine != null) {
                            spi = new LoggerCacheImpl(engine.logHandler, engine, false);
                            levels = engine.logLevels;
                        } else {
                            spi = LoggerCacheImpl.DISABLED;
                            levels = Collections.emptyMap();
                        }
                        loggersCache = EngineAccessor.LANGUAGE.createEngineLoggers(spi, levels);
                        loggers = loggersCache;
                    }
                }
            }
            return EngineAccessor.LANGUAGE.getLogger(PolyglotEngineImpl.OPTION_GROUP_ENGINE, null, loggersCache);
        }
    }
}
