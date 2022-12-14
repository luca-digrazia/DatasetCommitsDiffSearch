/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LoggingTest {

    private Map<Handler, Level> rootHandlerLevels;

    @Before
    public void setUp() {
        rootHandlerLevels = new IdentityHashMap<>();
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            rootHandlerLevels.put(handler, handler.getLevel());
            handler.setLevel(Level.OFF);
        }
    }

    @After
    public void tearDown() {
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            final Level level = rootHandlerLevels.get(handler);
            handler.setLevel(level);
        }
    }

    @Test
    public void testDefaultLogging() {
        final TestHandler handler = new TestHandler();
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        try (Context ctx = Context.newBuilder().logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            List<Map.Entry<Level, String>> expected = createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap());
            Assert.assertEquals(expected, handler.getLog());
            handler.clear();
            ctx.eval(LoggingLanguageSecond.ID, "");
            expected = createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap());
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testSingleLanguageAllLogging() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            // All levels from log1 language and logs >= defaultLevel from log2 language
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            // All levels from log2 language and logs >= defaultLevel from log1 language
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testAllLanguagesAllLogging() {
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(null, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testBothLanguagesAllLogging() {
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(
                        createLoggingOptions(LoggingLanguageFirst.ID, null, Level.FINEST.toString(), LoggingLanguageSecond.ID, null, Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, Level.FINEST, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, Level.FINEST, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testFinestOnListLogger() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.b", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.b", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testFinestOnIntermediateLogger() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testFinestOnIntermediateNonExistentLogger() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "b.a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("b.a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testDifferentLogLevelOnChildAndParent() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(
                        LoggingLanguageFirst.ID, "a", Level.FINE.toString(),
                        LoggingLanguageFirst.ID, "a.a", Level.FINER.toString(),
                        LoggingLanguageFirst.ID, "a.a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            final List<Map.Entry<Level, String>> expected = new ArrayList<>();
            final Map<String, Level> levels = new HashMap<>();
            levels.put("a", Level.FINE);
            levels.put("a.a", Level.FINER);
            levels.put("a.a.a", Level.FINEST);
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, levels));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testMultipleContextsExclusive() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        TestHandler handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = Context.newBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, "a.a", Level.FINEST.toString())).logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
            Assert.assertEquals(expected, handler.getLog());
        }
        handler = new TestHandler();
        try (Context ctx = Context.newBuilder().logHandler(handler).build()) {
            ctx.eval(LoggingLanguageFirst.ID, "");
            ctx.eval(LoggingLanguageSecond.ID, "");
            List<Map.Entry<Level, String>> expected = new ArrayList<>();
            expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
            expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
            Assert.assertEquals(expected, handler.getLog());
        }
    }

    @Test
    public void testMultipleContextsNested() {
        final Level defaultLevel = min(Logger.getLogger("").getLevel(), Level.INFO);
        final TestHandler handler1 = new TestHandler();
        final TestHandler handler2 = new TestHandler();
        final TestHandler handler3 = new TestHandler();
        try (Context ctx1 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageFirst.ID, "a.a", Level.FINEST.toString())).logHandler(handler1).build()) {
            try (Context ctx2 = Context.newBuilder().options(createLoggingOptions(LoggingLanguageSecond.ID, "a.a", Level.FINEST.toString())).logHandler(handler2).build()) {
                try (Context ctx3 = Context.newBuilder().logHandler(handler3).build()) {
                    ctx1.eval(LoggingLanguageFirst.ID, "");
                    ctx1.eval(LoggingLanguageSecond.ID, "");
                    ctx2.eval(LoggingLanguageFirst.ID, "");
                    ctx2.eval(LoggingLanguageSecond.ID, "");
                    ctx3.eval(LoggingLanguageFirst.ID, "");
                    ctx3.eval(LoggingLanguageSecond.ID, "");
                    List<Map.Entry<Level, String>> expected = new ArrayList<>();
                    expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
                    expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
                    Assert.assertEquals(expected, handler1.getLog());
                    expected = new ArrayList<>();
                    expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                    expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.singletonMap("a.a", Level.FINEST)));
                    Assert.assertEquals(expected, handler2.getLog());
                    expected = new ArrayList<>();
                    expected.addAll(createExpectedLog(LoggingLanguageFirst.ID, defaultLevel, Collections.emptyMap()));
                    expected.addAll(createExpectedLog(LoggingLanguageSecond.ID, defaultLevel, Collections.emptyMap()));
                    Assert.assertEquals(expected, handler3.getLog());
                }
            }
        }
    }

    private static Level min(Level a, Level b) {
        return a.intValue() < b.intValue() ? a : b;
    }

    private static Map<String, String> createLoggingOptions(final String... kvs) {
        if ((kvs.length % 3) != 0) {
            throw new IllegalArgumentException("Lang, Key, Val length has to be divisible by 3.");
        }
        final Map<String, String> options = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 3) {
            final String key;
            if (kvs[i] == null) {
                assert kvs[i + 1] == null;
                key = "log.level";
            } else if (kvs[i + 1] == null) {
                key = String.format("log.%s.level", kvs[i]);
            } else {
                key = String.format("log.%s.%s.level", kvs[i], kvs[i + 1]);
            }
            options.put(key, kvs[i + 2]);
        }
        return options;
    }

    private static List<Map.Entry<Level, String>> createExpectedLog(final String languageId, final Level defaultLevel, final Map<String, Level> levels) {
        final LoggerNode root = levels.isEmpty() ? null : createLevelsTree(levels);
        final List<Map.Entry<Level, String>> res = new ArrayList<>();
        for (Level level : AbstractLoggingLanguage.LOGGER_LEVELS) {
            for (String loggerName : AbstractLoggingLanguage.LOGGER_NAMES) {
                final Level loggerLevel = root == null ? defaultLevel : root.computeLevel(loggerName, defaultLevel);
                if (loggerLevel.intValue() <= level.intValue()) {
                    res.add(new AbstractMap.SimpleImmutableEntry<>(
                                    level,
                                    String.format("%s.%s", languageId, loggerName)));
                }
            }
        }
        return res;
    }

    private static LoggerNode createLevelsTree(Map<String, Level> levels) {
        final LoggerNode root = new LoggerNode();
        for (Map.Entry<String, Level> level : levels.entrySet()) {
            final String loggerName = level.getKey();
            final Level loggerLevel = level.getValue();
            final LoggerNode node = root.findChild(loggerName);
            node.level = loggerLevel;
        }
        return root;
    }

    public static final class LoggingContext {
        private final TruffleLanguage.Env env;

        LoggingContext(final TruffleLanguage.Env env) {
            this.env = env;
        }

        TruffleLanguage.Env getEnv() {
            return env;
        }
    }

    private static final class LoggerNode {
        private final Map<String, LoggerNode> children;
        private Level level;

        LoggerNode() {
            this.children = new HashMap<>();
        }

        LoggerNode findChild(String loggerName) {
            if (loggerName.isEmpty()) {
                return this;
            }
            int index = loggerName.indexOf('.');
            String currentNameCompoment;
            String nameRest;
            if (index > 0) {
                currentNameCompoment = loggerName.substring(0, index);
                nameRest = loggerName.substring(index + 1);
            } else {
                currentNameCompoment = loggerName;
                nameRest = "";
            }
            LoggerNode child = children.get(currentNameCompoment);
            if (child == null) {
                child = new LoggerNode();
                children.put(currentNameCompoment, child);
            }
            return child.findChild(nameRest);
        }

        Level computeLevel(final String loggerName, final Level bestSoFar) {
            Level res = bestSoFar;
            if (this.level != null) {
                res = level;
            }
            if (loggerName.isEmpty()) {
                return res;
            }
            int index = loggerName.indexOf('.');
            String currentNameCompoment;
            String nameRest;
            if (index > 0) {
                currentNameCompoment = loggerName.substring(0, index);
                nameRest = loggerName.substring(index + 1);
            } else {
                currentNameCompoment = loggerName;
                nameRest = "";
            }
            LoggerNode child = children.get(currentNameCompoment);
            if (child == null) {
                return res;
            }
            return child.computeLevel(nameRest, res);
        }
    }

    public abstract static class AbstractLoggingLanguage extends TruffleLanguage<LoggingContext> {
        static final String[] LOGGER_NAMES = {"a", "a.a", "a.b", "a.a.a", "b", "b.a", "b.a.a.a"};
        static final Level[] LOGGER_LEVELS = {Level.FINEST, Level.FINER, Level.FINE, Level.INFO, Level.SEVERE, Level.WARNING};
        private final Collection<Logger> allLoggers;

        AbstractLoggingLanguage(final String id) {
            final Collection<Logger> loggers = new ArrayList<>(LOGGER_NAMES.length);
            for (String loggerName : LOGGER_NAMES) {
                loggers.add(Truffle.getLogger(id, loggerName, null));
            }
            allLoggers = loggers;
        }

        @Override
        protected LoggingContext createContext(Env env) {
            return new LoggingContext(env);
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final RootNode root = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    doLog();
                    return getContextReference().get().getEnv().asGuestValue(null);
                }
            };
            return Truffle.getRuntime().createCallTarget(root);
        }

        private void doLog() {
            for (Level level : LOGGER_LEVELS) {
                for (Logger logger : allLoggers) {
                    logger.log(level, logger.getName());
                }
            }
        }
    }

    @TruffleLanguage.Registration(id = LoggingLanguageFirst.ID, name = LoggingLanguageFirst.ID, version = "1.0", mimeType = LoggingLanguageFirst.ID)
    public static final class LoggingLanguageFirst extends AbstractLoggingLanguage {
        static final String ID = "log1";

        public LoggingLanguageFirst() {
            super(ID);
        }
    }

    @TruffleLanguage.Registration(id = LoggingLanguageSecond.ID, name = LoggingLanguageSecond.ID, version = "1.0", mimeType = LoggingLanguageSecond.ID)
    public static final class LoggingLanguageSecond extends AbstractLoggingLanguage {
        static final String ID = "log2";

        public LoggingLanguageSecond() {
            super(ID);
        }
    }

    private static final class TestHandler extends Handler {
        private final List<Map.Entry<Level, String>> logRecords;

        TestHandler() {
            this.logRecords = new ArrayList<>();
        }

        @Override
        public void publish(final LogRecord record) {
            final String message = record.getMessage();
            Assert.assertNotNull(message);
            final Level level = record.getLevel();
            Assert.assertNotNull(level);
            logRecords.add(new AbstractMap.SimpleImmutableEntry<>(level, message));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
            clear();
        }

        List<Map.Entry<Level, String>> getLog() {
            return logRecords;
        }

        void clear() {
            logRecords.clear();
        }
    }
}
