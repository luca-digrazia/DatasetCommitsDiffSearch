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
package com.oracle.truffle.api;

import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.graalvm.polyglot.Context;

/**
 * Support for logging in Truffle languages and instruments.
 * <p>
 * The logger's {@link Level} configuration is done using the
 * {@link org.graalvm.polyglot.Context.Builder#options(java.util.Map) Context's options}. The level
 * option key has the following format: {@code log.languageId.className.level} or
 * {@code log.instrumentId.className.level}. The value is either the name of pre-defined
 * {@link Level} constant or a numeric {@link Level} value. If not explicitly set in
 * {@link org.graalvm.polyglot.Context.Builder#options(java.util.Map) Context's options} the level
 * is inherited from the parent logger.
 * <p>
 * The {@link TruffleLogger} supports {@link LogRecord#getParameters() message parameters} of
 * primitive types and strings. The object parameters are converted into string value before they
 * are passed to the {@link Handler}.
 * <p>
 * The {@link TruffleLogger} instances are safe to be used on compiled code paths as well as from
 * multiple-threads.
 *
 * @since 1.0
 */
public final class TruffleLogger {

    private static final String ROOT_NAME = "";
    private static final int MAX_CLEANED_REFS = 100;
    private static final int OFF_VALUE = Level.OFF.intValue();
    private static final int DEFAULT_VALUE = Level.INFO.intValue();
    private static final ReferenceQueue<TruffleLogger> loggersRefQueue = new ReferenceQueue<>();
    private static final Object childrenLock = new Object();

    private final String name;
    private final Supplier<? extends Handler> handlerProvider;
    @CompilerDirectives.CompilationFinal private volatile int levelNum;
    @CompilerDirectives.CompilationFinal private volatile Assumption levelNumStable;
    private volatile Level levelObj;
    private volatile TruffleLogger parent;
    private Collection<ChildLoggerRef> children;

    private TruffleLogger(final String loggerName, final Supplier<? extends Handler> handlerProvider) {
        this.name = loggerName;
        this.handlerProvider = handlerProvider;
        this.levelNum = DEFAULT_VALUE;
        this.levelNumStable = Truffle.getRuntime().createAssumption("Log Level Value stable for: " + loggerName);
    }

    private TruffleLogger() {
        this(ROOT_NAME, new PolyglotLogHandlerProvider());
    }

    /**
     * Find or create a root logger for a given language or instrument. If the root logger for given
     * language or instrument already exists it's returned, otherwise a new root logger is created.
     *
     * @param id the unique id of language or instrument
     * @return a {@link Logger}
     * @throws NullPointerException if {@code id} is null
     * @since 1.0
     */
    public static TruffleLogger getLogger(final String id) {
        Objects.requireNonNull(id, "LanguageId must be non null.");
        return LoggerCache.getInstance().getOrCreateLogger(id);
    }

    /**
     * Find or create a logger for a given language or instrument class. If a logger for the class
     * already exists it's returned, otherwise a new logger is created.
     *
     * @param id the unique id of language or instrument
     * @param forClass the {@link Class} to create a logger for
     * @return a {@link Logger}
     * @throws NullPointerException if {@code id} or {@code forClass} is null
     * @since 1.0
     */
    public static TruffleLogger getLogger(final String id, final Class<?> forClass) {
        Objects.requireNonNull(forClass, "Class must be non null.");
        return getLogger(id, forClass.getName());
    }

    /**
     * Find or create a logger for a given language or instrument. If a logger with given name
     * already exists it's returned, otherwise a new logger is created.
     *
     * @param id the unique id of language or instrument
     * @param loggerName the the name of a {@link Logger}, if a {@code loggerName} is null or empty
     *            a root logger for language or instrument is returned
     * @return a {@link Logger}
     * @throws NullPointerException if {@code id} is null
     * @since 1.0
     */
    public static TruffleLogger getLogger(final String id, final String loggerName) {
        Objects.requireNonNull(id, "LanguageId must be non null.");
        final String globalLoggerId = loggerName == null || loggerName.isEmpty() ? id : id + '.' + loggerName;
        return LoggerCache.getInstance().getOrCreateLogger(globalLoggerId);
    }

    /**
     * Logs a message with {@link Level#CONFIG config level}.
     * <p>
     * If the logger is enabled for the {@link Level#CONFIG config level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void config(final String message) {
        log(Level.CONFIG, message);
    }

    /**
     * Logs a message with {@link Level#CONFIG config level}. The message is constructed only when
     * the logger is enabled for the {@link Level#CONFIG config level}.
     * <p>
     * If the logger is enabled for the {@link Level#CONFIG config level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void config(final Supplier<String> messageSupplier) {
        log(Level.CONFIG, messageSupplier);
    }

    /**
     * Logs entry into method.
     * <p>
     * This method can be used to log entry into a method. A {@link LogRecord} with message "ENTRY"
     * and the given {@code sourceMethod} and {@code sourceClass} is logged with {@link Level#FINER
     * finer level}.
     *
     * @param sourceClass the entered class
     * @param sourceMethod the entered method
     * @since 1.0
     */
    public void entering(final String sourceClass, final String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY");
    }

    /**
     * Logs entry into method with single parameter.
     * <p>
     * This method can be used to log entry into a method. A {@link LogRecord} with message "ENTRY",
     * the given {@code sourceMethod} and {@code sourceClass} and given parameter is logged with
     * {@link Level#FINER finer level}.
     *
     * @param sourceClass the entered class
     * @param sourceMethod the entered method
     * @param parameter the method parameter
     * @since 1.0
     */
    public void entering(final String sourceClass, final String sourceMethod, final Object parameter) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY {0}", parameter);
    }

    /**
     * Logs entry into method with multiple parameters.
     * <p>
     * This method can be used to log entry into a method. A {@link LogRecord} with message "ENTRY",
     * the given {@code sourceMethod} and {@code sourceClass} and given parameters is logged with
     * {@link Level#FINER finer level}.
     *
     * @param sourceClass the entered class
     * @param sourceMethod the entered method
     * @param parameters the method parameters
     * @since 1.0
     */
    public void entering(final String sourceClass, final String sourceMethod, final Object[] parameters) {
        String msg = "ENTRY";
        if (parameters == null) {
            logp(Level.FINER, sourceClass, sourceMethod, msg);
            return;
        }
        if (!isLoggable(Level.FINER)) {
            return;
        }
        for (int i = 0; i < parameters.length; i++) {
            msg = msg + " {" + i + "}";
        }
        logp(Level.FINER, sourceClass, sourceMethod, msg, parameters);
    }

    /**
     * Logs a return from method.
     * <p>
     * This method can be used to log return from a method. A {@link LogRecord} with message
     * "RETURN" and the given {@code sourceMethod} and {@code sourceClass} is logged with
     * {@link Level#FINER finer level}.
     *
     * @param sourceClass the exiting class
     * @param sourceMethod the exiting method
     * @since 1.0
     */
    public void exiting(final String sourceClass, final String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN");
    }

    /**
     * Logs a return from method with result.
     * <p>
     * This method can be used to log return from a method. A {@link LogRecord} with message
     * "RETURN", the given {@code sourceMethod} and {@code sourceClass} and method result is logged
     * with {@link Level#FINER finer level}.
     *
     * @param sourceClass the exiting class
     * @param sourceMethod the exiting method
     * @param result the return value
     * @since 1.0
     */
    public void exiting(final String sourceClass, final String sourceMethod, final Object result) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN {0}", result);
    }

    /**
     * Logs a message with {@link Level#FINE fine level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINE fine level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void fine(final String message) {
        log(Level.FINE, message);
    }

    /**
     * Logs a message with {@link Level#FINE fine level}. The message is constructed only when the
     * logger is enabled for the {@link Level#FINE fine level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINE fine level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void fine(final Supplier<String> messageSupplier) {
        log(Level.FINE, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#FINER finer level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINER finer level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void finer(final String message) {
        log(Level.FINER, message);
    }

    /**
     * Logs a message with {@link Level#FINER finer level}. The message is constructed only when the
     * logger is enabled for the {@link Level#FINER finer level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINER finer level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void finer(final Supplier<String> messageSupplier) {
        log(Level.FINER, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#FINEST finest level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINEST finest level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void finest(final String message) {
        log(Level.FINEST, message);
    }

    /**
     * Logs a message with {@link Level#FINEST finest level}. The message is constructed only when
     * the logger is enabled for the {@link Level#FINEST finest level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINEST finest level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void finest(final Supplier<String> messageSupplier) {
        log(Level.FINEST, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#INFO info level}.
     * <p>
     * If the logger is enabled for the {@link Level#INFO info level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void info(final String message) {
        log(Level.INFO, message);
    }

    /**
     * Logs a message with {@link Level#INFO info level}. The message is constructed only when the
     * logger is enabled for the {@link Level#INFO info level}.
     * <p>
     * If the logger is enabled for the {@link Level#INFO info level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void info(final Supplier<String> messageSupplier) {
        log(Level.INFO, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#SEVERE severe level}.
     * <p>
     * If the logger is enabled for the {@link Level#SEVERE severe level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void severe(final String message) {
        log(Level.SEVERE, message);
    }

    /**
     * Logs a message with {@link Level#SEVERE severe level}. The message is constructed only when
     * the logger is enabled for the {@link Level#SEVERE severe level}.
     * <p>
     * If the logger is enabled for the {@link Level#SEVERE severe level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void severe(final Supplier<String> messageSupplier) {
        log(Level.SEVERE, messageSupplier);
    }

    /**
     * Logs throwing an exception.
     * <p>
     * This method can be used to log exception thrown from a method. A {@link LogRecord} with
     * message "THROW",the given {@code sourceMethod} and {@code sourceClass} and {@code thrown} is
     * logged with {@link Level#FINER finer level}.
     *
     * @param sourceClass the class throwing an exception
     * @param sourceMethod the method throwing an exception
     * @param thrown the thrown exception
     * @since 1.0
     */
    public <T extends Throwable> T throwing(final String sourceClass, final String sourceMethod, final T thrown) {
        logp(Level.FINER, sourceClass, sourceMethod, "THROW", thrown);
        return thrown;
    }

    /**
     * Logs a message with {@link Level#WARNING warning level}.
     * <p>
     * If the logger is enabled for the {@link Level#WARNING warning level} the message is sent to
     * the {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 1.0
     */
    public void warning(final String message) {
        log(Level.WARNING, message);
    }

    /**
     * Logs a message with {@link Level#WARNING warning level}. The message is constructed only when
     * the logger is enabled for the {@link Level#WARNING warning level}.
     * <p>
     * If the logger is enabled for the {@link Level#WARNING warning level} the message is sent to
     * the {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void warning(final Supplier<String> messageSupplier) {
        log(Level.WARNING, messageSupplier);
    }

    /**
     * Logs a message.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @since 1.0
     */
    public void log(final Level level, final String message) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, (Object) null);
    }

    /**
     * Logs a message. The message is constructed only when the logger is enabled for the given
     * {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void log(final Level level, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, null, null, (Object) null);
    }

    /**
     * Logs a message with single parameter.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @param parameter the log message parameter
     * @since 1.0
     */
    public void log(final Level level, final String message, final Object parameter) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, parameter);
    }

    /**
     * Logs a message with multiple parameters.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @param parameters the log message parameters
     * @since 1.0
     */
    public void log(final Level level, final String message, final Object[] parameters) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, parameters);
    }

    /**
     * Logs a message with an exception.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @param thrown the exception to log
     * @since 1.0
     */
    public void log(final Level level, final String message, final Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, thrown);
    }

    /**
     * Logs a message with an exception. The message is constructed only when the logger is enabled
     * for the given {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param thrown the exception to log
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void log(final Level level, final Throwable thrown, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, null, null, thrown);
    }

    /**
     * Logs a message, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @since 1.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, (Object) null);
    }

    /**
     * Logs a message, specifying source class and source method. The message is constructed only
     * when the logger is enabled for the given {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, sourceClass, sourceMethod, (Object) null);
    }

    /**
     * Logs a message with single parameter, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @param parameter the log message parameter
     * @since 1.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message, final Object parameter) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, parameter);
    }

    /**
     * Log a message with multiple parameters, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @param parameters the log message parameters
     * @since 1.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message, Object[] parameters) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, parameters);
    }

    /**
     * Logs a message with an exception, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @param thrown the exception to log
     * @since 1.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message, final Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, thrown);
    }

    /**
     * Logs a message with an exception, specifying source class and source method. The message is
     * constructed only when the logger is enabled for the given {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param thrown the exception to log
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 1.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Throwable thrown, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, sourceClass, sourceMethod, thrown);
    }

    /**
     * Returns the name of the logger.
     *
     * @return the logger name
     * @since 1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the parent {@link TruffleLogger}.
     *
     * @return the parent {@link TruffleLogger} or null when the {@link TruffleLogger} has no
     *         parent.
     * @since 1.0
     */
    public TruffleLogger getParent() {
        return parent;
    }

    /**
     * Checks if a message of the given level would be logged by this logger.
     *
     * @param level the required logging level
     * @return true if message is loggable by this logger
     * @since 1.0
     */
    public boolean isLoggable(final Level level) {
        int value = getLevelNum();
        if (level.intValue() < value || value == OFF_VALUE) {
            return false;
        }
        final Object currentContext = TruffleLanguage.AccessAPI.engineAccess().getCurrentOuterContext();
        if (currentContext == null) {
            return false;
        }
        return isLoggableSlowPath(currentContext, level);
    }

    @CompilerDirectives.TruffleBoundary
    private boolean isLoggableSlowPath(final Object context, final Level level) {
        return LoggerCache.getInstance().isLoggable(getName(), context, level);
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final String message,
                    final String className,
                    final String methodName,
                    final Object param) {
        doLog(level, message, className, methodName, new Object[]{param});
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final String message,
                    final String className,
                    final String methodName,
                    final Object[] params) {
        final LogRecord logRecord = TruffleLanguage.AccessAPI.engineAccess().createLogRecord(
                        level,
                        getName(),
                        message,
                        className,
                        methodName,
                        params,
                        null);
        callHandlers(logRecord);
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final String message,
                    final String className,
                    final String methodName,
                    final Throwable thrown) {
        final LogRecord logRecord = TruffleLanguage.AccessAPI.engineAccess().createLogRecord(
                        level,
                        getName(),
                        message,
                        className,
                        methodName,
                        null,
                        thrown);
        callHandlers(logRecord);
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final Supplier<String> messageSupplier,
                    final String className,
                    final String methodName,
                    final Object param) {
        doLog(level, messageSupplier.get(), className, methodName, new Object[]{param});
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final Supplier<String> messageSupplier,
                    final String className,
                    final String methodName,
                    final Throwable thrown) {
        doLog(level, messageSupplier.get(), className, methodName, thrown);
    }

    private void callHandlers(final LogRecord record) {
        CompilerAsserts.neverPartOfCompilation("Log handler should never be called from compiled code.");
        for (TruffleLogger current = this; current != null; current = current.getParent()) {
            if (current.handlerProvider != null) {
                current.handlerProvider.get().publish(record);
            }
        }
    }

    private void removeChild(final ChildLoggerRef child) {
        synchronized (childrenLock) {
            if (children != null) {
                for (Iterator<ChildLoggerRef> it = children.iterator(); it.hasNext();) {
                    if (it.next() == child) {
                        it.remove();
                        return;
                    }
                }
            }
        }
    }

    private void updateLevelNum() {
        int value;
        if (levelObj != null) {
            value = levelObj.intValue();
            if (parent != null) {
                value = Math.min(value, parent.getLevelNum());
            }
        } else if (parent != null) {
            value = parent.getLevelNum();
        } else {
            value = DEFAULT_VALUE;
        }
        setLevelNum(value);
        if (children != null) {
            for (ChildLoggerRef ref : children) {
                final TruffleLogger logger = ref.get();
                if (logger != null) {
                    logger.updateLevelNum();
                }
            }
        }
    }

    private int getLevelNum() {
        if (!levelNumStable.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return levelNum;
    }

    private boolean setLevelNum(final int value) {
        if (this.levelNum != value) {
            this.levelNum = value;
            final Assumption currentAssumtion = levelNumStable;
            levelNumStable = Truffle.getRuntime().createAssumption("Log Level Value stable for: " + getName());
            currentAssumtion.invalidate();
            return true;
        }
        return false;
    }

    private void setLevel(final Level level) {
        synchronized (childrenLock) {
            this.levelObj = level;
            updateLevelNum();
        }
    }

    private void setParent(final TruffleLogger newParent) {
        Objects.requireNonNull(newParent, "Parent must be non null.");
        synchronized (childrenLock) {
            ChildLoggerRef found = null;
            if (parent != null) {
                for (Iterator<ChildLoggerRef> it = parent.children.iterator(); it.hasNext();) {
                    final ChildLoggerRef childRef = it.next();
                    final TruffleLogger childLogger = childRef.get();
                    if (childLogger == this) {
                        found = childRef;
                        it.remove();
                        break;
                    }
                }
            }
            this.parent = newParent;
            if (found == null) {
                found = new ChildLoggerRef(this);
            }
            found.setParent(parent);
            if (parent.children == null) {
                parent.children = new ArrayList<>(2);
            }
            parent.children.add(found);
            updateLevelNum();
        }
    }

    private static void cleanupFreedReferences() {
        for (int i = 0; i < MAX_CLEANED_REFS; i++) {
            final AbstractLoggerRef ref = (AbstractLoggerRef) loggersRefQueue.poll();
            if (ref == null) {
                break;
            }
            ref.close();
        }
    }

    private abstract static class AbstractLoggerRef extends WeakReference<TruffleLogger> implements Closeable {
        private final AtomicBoolean closed;

        AbstractLoggerRef(final TruffleLogger logger) {
            super(logger, loggersRefQueue);
            this.closed = new AtomicBoolean();
        }

        @Override
        public abstract void close();

        boolean shouldClose() {
            return !closed.getAndSet(true);
        }
    }

    private static final class ChildLoggerRef extends AbstractLoggerRef {

        private volatile Reference<TruffleLogger> parent;

        ChildLoggerRef(final TruffleLogger logger) {
            super(logger);
        }

        void setParent(TruffleLogger parent) {
            this.parent = new WeakReference<>(parent);
        }

        @Override
        public void close() {
            if (shouldClose()) {
                final Reference<TruffleLogger> p = parent;
                if (p != null) {
                    TruffleLogger parentLogger = p.get();
                    if (parentLogger != null) {
                        parentLogger.removeChild(this);
                    }
                    parent = null;
                }
            }
        }
    }

    static final class LoggerCache {
        private static final LoggerCache INSTANCE = new LoggerCache();
        private final TruffleLogger polyglotRootLogger;
        private final Map<String, NamedLoggerRef> loggers;
        private final LoggerNode root;
        private final Map<Object, Map<String, Level>> levelsByContext;
        private Map<String, Level> effectiveLevels;

        private LoggerCache() {
            this.polyglotRootLogger = new TruffleLogger();
            this.loggers = new HashMap<>();
            this.loggers.put(ROOT_NAME, new NamedLoggerRef(this.polyglotRootLogger, ROOT_NAME));
            this.root = new LoggerNode(null, new NamedLoggerRef(this.polyglotRootLogger, ROOT_NAME));
            this.levelsByContext = new WeakHashMap<>();
            this.effectiveLevels = Collections.emptyMap();
        }

        void addLogLevelsForContext(final Object context, final Map<String, Level> addedLevels) {
            synchronized (this) {
                levelsByContext.put(context, addedLevels);
                final Collection<String> removedLevels = new HashSet<>();
                final Collection<String> changedLevels = new HashSet<>();
                effectiveLevels = computeEffectiveLevels(
                                effectiveLevels,
                                Collections.emptySet(),
                                addedLevels,
                                levelsByContext,
                                removedLevels,
                                changedLevels);
                reconfigure(removedLevels, changedLevels);
            }
        }

        synchronized void removeLogLevelsForContext(final Object context) {
            final Map<String, Level> levels = levelsByContext.remove(context);
            final Collection<String> removedLevels = new HashSet<>();
            final Collection<String> changedLevels = new HashSet<>();
            effectiveLevels = computeEffectiveLevels(
                            effectiveLevels,
                            levels.keySet(),
                            Collections.emptyMap(),
                            levelsByContext,
                            removedLevels,
                            changedLevels);
            reconfigure(removedLevels, changedLevels);
        }

        synchronized boolean isLoggable(final String loggerName, final Object currentContext, final Level level) {
            final Map<String, Level> current = levelsByContext.get(currentContext);
            if (current == null) {
                final int currentLevel = DEFAULT_VALUE;
                return level.intValue() >= currentLevel && currentLevel != OFF_VALUE;
            }
            if (levelsByContext.size() == 1) {
                return true;
            }
            final int currentLevel = Math.min(computeLevel(loggerName, current), DEFAULT_VALUE);
            return level.intValue() >= currentLevel && currentLevel != OFF_VALUE;
        }

        private static int computeLevel(String loggeName, final Map<String, Level> levels) {
            for (String currentName = loggeName; currentName != null;) {
                final Level l = levels.get(currentName);
                if (l != null) {
                    return l.intValue();
                }
                if (currentName.isEmpty()) {
                    currentName = null;
                } else {
                    final int index = currentName.lastIndexOf('.');
                    currentName = index == -1 ? "" : currentName.substring(0, index);
                }
            }
            return DEFAULT_VALUE;
        }

        TruffleLogger getOrCreateLogger(final String loggerName) {
            TruffleLogger found = getLogger(loggerName);
            if (found == null) {
                for (final TruffleLogger logger = new TruffleLogger(loggerName, null); found == null;) {
                    if (addLogger(logger)) {
                        found = logger;
                        break;
                    }
                    found = getLogger(loggerName);
                }
            }
            return found;
        }

        private synchronized TruffleLogger getLogger(final String loggerName) {
            TruffleLogger res = null;
            final NamedLoggerRef ref = loggers.get(loggerName);
            if (ref != null) {
                res = ref.get();
                if (res == null) {
                    ref.close();
                }
            }
            return res;
        }

        private boolean addLogger(final TruffleLogger logger) {
            final String loggerName = logger.getName();
            if (loggerName == null) {
                throw new NullPointerException("Logger must have non null name.");
            }
            synchronized (this) {
                cleanupFreedReferences();
                NamedLoggerRef ref = loggers.get(loggerName);
                if (ref != null) {
                    final TruffleLogger loggerInstance = ref.get();
                    if (loggerInstance != null) {
                        return false;
                    } else {
                        ref.close();
                    }
                }
                ref = new NamedLoggerRef(logger, loggerName);
                loggers.put(loggerName, ref);
                setLoggerLevel(logger, loggerName);
                createParents(loggerName);
                final LoggerNode node = findLoggerNode(loggerName);
                node.setLoggerRef(ref);
                final TruffleLogger parentLogger = node.findParentLogger();
                if (parentLogger != null) {
                    logger.setParent(parentLogger);
                }
                node.updateChildParents();
                ref.setNode(node);
                return true;
            }
        }

        private Level getEffectiveLevel(final String loggerName) {
            return effectiveLevels.get(loggerName);
        }

        private void reconfigure(final Collection<? extends String> removedLoggers, final Collection<? extends String> changedLoogers) {
            for (String loggerName : removedLoggers) {
                final TruffleLogger logger = getLogger(loggerName);
                if (logger != null) {
                    logger.setLevel(null);
                }
            }
            for (String loggerName : changedLoogers) {
                final TruffleLogger logger = getLogger(loggerName);
                if (logger != null) {
                    setLoggerLevel(logger, loggerName);
                    createParents(loggerName);
                } else {
                    getOrCreateLogger(loggerName);
                }
            }
        }

        private void setLoggerLevel(final TruffleLogger logger, final String loggerName) {
            final Level l = getEffectiveLevel(loggerName);
            if (l != null) {
                logger.setLevel(l);
            }
        }

        private void createParents(final String loggerName) {
            int index = -1;
            for (int start = 1;; start = index + 1) {
                index = loggerName.indexOf('.', start);
                if (index < 0) {
                    break;
                }
                final String parentName = loggerName.substring(0, index);
                if (getEffectiveLevel(parentName) != null) {
                    getOrCreateLogger(parentName);
                }
            }
        }

        private LoggerNode findLoggerNode(final String loggerName) {
            LoggerNode node = root;
            String currentName = loggerName;
            while (!currentName.isEmpty()) {
                int index = currentName.indexOf('.');
                String currentNameCompoment;
                if (index > 0) {
                    currentNameCompoment = currentName.substring(0, index);
                    currentName = currentName.substring(index + 1);
                } else {
                    currentNameCompoment = currentName;
                    currentName = "";
                }
                if (node.children == null) {
                    node.children = new HashMap<>();
                }
                LoggerNode child = node.children.get(currentNameCompoment);
                if (child == null) {
                    child = new LoggerNode(node, null);
                    node.children.put(currentNameCompoment, child);
                }
                node = child;
            }
            return node;
        }

        static LoggerCache getInstance() {
            return INSTANCE;
        }

        private static Map<String, Level> computeEffectiveLevels(
                        final Map<String, Level> currentEffectiveLevels,
                        final Set<String> removed,
                        final Map<String, Level> added,
                        final Map<Object, Map<String, Level>> levelsByContext,
                        final Collection<? super String> removedLevels,
                        final Collection<? super String> changedLevels) {
            final Map<String, Level> newEffectiveLevels = new HashMap<>(currentEffectiveLevels);
            for (String loggerName : removed) {
                final Level level = findMinLevel(loggerName, levelsByContext);
                if (level == null) {
                    newEffectiveLevels.remove(loggerName);
                    removedLevels.add(loggerName);
                } else {
                    final Level currentLevel = newEffectiveLevels.get(loggerName);
                    if (min(level, currentLevel) != currentLevel) {
                        newEffectiveLevels.put(loggerName, level);
                        changedLevels.add(loggerName);
                    }
                }
            }
            for (Map.Entry<String, Level> addedLevel : added.entrySet()) {
                final String loggerName = addedLevel.getKey();
                final Level loggerLevel = addedLevel.getValue();
                final Level currentLevel = newEffectiveLevels.get(loggerName);
                if (currentLevel == null || min(loggerLevel, currentLevel) != currentLevel) {
                    newEffectiveLevels.put(loggerName, loggerLevel);
                    changedLevels.add(loggerName);
                }
            }
            return newEffectiveLevels;
        }

        private static Level findMinLevel(final String loggerName, final Map<Object, Map<String, Level>> levelsByContext) {
            Level min = null;
            for (Map<String, Level> levels : levelsByContext.values()) {
                Level level = levels.get(loggerName);
                if (level == null) {
                    continue;
                }
                if (min == null) {
                    min = level;
                } else {
                    min = min(min, level);
                }
            }
            return min;
        }

        private static Level min(final Level l1, final Level l2) {
            return l1.intValue() < l2.intValue() ? l1 : l2;
        }

        private final class NamedLoggerRef extends AbstractLoggerRef {
            private final String loggerName;
            private LoggerNode node;

            NamedLoggerRef(final TruffleLogger logger, final String loggerName) {
                super(logger);
                this.loggerName = loggerName;
            }

            void setNode(final LoggerNode node) {
                assert Thread.holdsLock(LoggerCache.this);
                this.node = node;
            }

            @Override
            public void close() {
                assert Thread.holdsLock(LoggerCache.this);
                if (shouldClose()) {
                    if (node != null) {
                        if (node.loggerRef == this) {
                            LoggerCache.this.loggers.remove(loggerName);
                            node.loggerRef = null;
                        }
                        node = null;
                    }
                }
            }
        }

        private final class LoggerNode {
            final LoggerNode parent;
            Map<String, LoggerNode> children;
            private NamedLoggerRef loggerRef;

            LoggerNode(final LoggerNode parent, final NamedLoggerRef loggerRef) {
                this.parent = parent;
                this.loggerRef = loggerRef;
            }

            void setLoggerRef(final NamedLoggerRef loggerRef) {
                this.loggerRef = loggerRef;
            }

            void updateChildParents() {
                final TruffleLogger logger = loggerRef.get();
                updateChildParentsImpl(logger);
            }

            TruffleLogger findParentLogger() {
                if (parent == null) {
                    return null;
                }
                TruffleLogger logger;
                if (parent.loggerRef != null && (logger = parent.loggerRef.get()) != null) {
                    return logger;
                }
                return parent.findParentLogger();
            }

            private void updateChildParentsImpl(final TruffleLogger parentLogger) {
                if (children == null || children.isEmpty()) {
                    return;
                }
                for (LoggerNode child : children.values()) {
                    TruffleLogger childLogger = child.loggerRef != null ? child.loggerRef.get() : null;
                    if (childLogger != null) {
                        childLogger.setParent(parentLogger);
                    } else {
                        child.updateChildParentsImpl(parentLogger);
                    }
                }
            }
        }
    }

    private static final class PolyglotLogHandlerProvider implements Supplier<Handler> {
        @Override
        public Handler get() {
            return TruffleLanguage.AccessAPI.engineAccess().getLogHandler();
        }
    }
}
