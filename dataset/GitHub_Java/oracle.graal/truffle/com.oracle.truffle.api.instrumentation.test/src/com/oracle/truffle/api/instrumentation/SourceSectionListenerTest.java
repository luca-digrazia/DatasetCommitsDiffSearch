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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;

public class SourceSectionListenerTest extends AbstractInstrumentationTest {

    @Test
    public void testLoadSourceSection1() throws IOException {
        testLoadSourceSectionImpl(1);
    }

    @Test
    public void testLoadSourceSection2() throws IOException {
        testLoadSourceSectionImpl(2);
    }

    @Test
    public void testLoadSourceSection3() throws IOException {
        testLoadSourceSectionImpl(5);
    }

    private void testLoadSourceSectionImpl(int runTimes) throws IOException {
        Instrument instrument = engine.getInstruments().get("testLoadSourceSection1");
        SourceSection[] sourceSections1 = sections("STATEMENT(EXPRESSION, EXPRESSION)", "STATEMENT(EXPRESSION, EXPRESSION)", "EXPRESSION");
        Source source1 = sourceSections1[0].getSource();
        for (int i = 0; i < runTimes; i++) {
            run(source1);
        }

        instrument.setEnabled(true);
        TestLoadSourceSection1 impl = instrument.lookup(TestLoadSourceSection1.class);

        SourceSection[] sourceSections2 = sections("STATEMENT(EXPRESSION)", "STATEMENT(EXPRESSION)", "EXPRESSION");
        Source source2 = sourceSections2[0].getSource();
        for (int i = 0; i < runTimes; i++) {
            run(source2);
        }

        assertEvents(impl.allEvents, merge(sourceSections1, sourceSections2));
        assertEvents(impl.onlyNewEvents, sourceSections2);
        assertEvents(impl.onlyStatements, sourceSections1[0], sourceSections2[0]);
        assertEvents(impl.onlyExpressions, sourceSections1[1], sourceSections1[2], sourceSections2[1]);

        instrument.setEnabled(false);

        SourceSection[] sourceSections3 = sections("STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)", "STATEMENT(EXPRESSION, EXPRESSION, EXPRESSION)", "EXPRESSION");
        Source source3 = sourceSections3[0].getSource();
        for (int i = 0; i < runTimes; i++) {
            run(source3);
        }

        assertEvents(impl.allEvents, merge(sourceSections1, sourceSections2));
        assertEvents(impl.onlyNewEvents, sourceSections2);
        assertEvents(impl.onlyStatements, sourceSections1[0], sourceSections2[0]);
        assertEvents(impl.onlyExpressions, sourceSections1[1], sourceSections1[2], sourceSections2[1]);

        instrument.setEnabled(true);
        // new instrument needs update
        impl = instrument.lookup(TestLoadSourceSection1.class);

        assertEvents(impl.onlyNewEvents);
        assertEvents(impl.allEvents, merge(sourceSections1, sourceSections2, sourceSections3));
        assertEvents(impl.onlyStatements, sourceSections1[0], sourceSections2[0], sourceSections3[0]);
        assertEvents(impl.onlyExpressions, sourceSections1[1], sourceSections1[2], sourceSections2[1], sourceSections3[1], sourceSections3[2], sourceSections3[3]);
    }

    private static SourceSection[] sections(String code, String... match) {
        Source source = Source.fromText(code, null).withMimeType(InstrumentationTestLanguage.MIME_TYPE);

        List<SourceSection> sections = new ArrayList<>();
        for (String matchExpression : match) {
            int index = -1;
            while ((index = code.indexOf(matchExpression, index + 1)) != -1) {
                sections.add(source.createSection(null, index, matchExpression.length()));
            }
        }
        return sections.toArray(new SourceSection[0]);
    }

    private static SourceSection[] merge(SourceSection[]... arrays) {
        int totalLength = 0;
        for (SourceSection[] array : arrays) {
            totalLength += array.length;
        }
        SourceSection[] newArray = new SourceSection[totalLength];

        int index = 0;
        for (SourceSection[] array : arrays) {
            System.arraycopy(array, 0, newArray, index, array.length);
            index += array.length;
        }
        return newArray;
    }

    private static void assertEvents(List<SourceSectionEvent> actualEvents, SourceSection... expectedSourceSections) {
        Assert.assertEquals(expectedSourceSections.length, actualEvents.size());
        for (int i = 0; i < expectedSourceSections.length; i++) {
            SourceSectionEvent actualEvent = actualEvents.get(i);
            SourceSection expectedSourceSection = expectedSourceSections[i];
            Assert.assertEquals("index " + i, expectedSourceSection, actualEvent.sourceSection);
            Assert.assertSame("index " + i, actualEvent.node.getSourceSection(), actualEvent.sourceSection);
        }
    }

    private static class SourceSectionEvent {

        final SourceSection sourceSection;
        final Node node;

        SourceSectionEvent(SourceSection sourceSection, Node node) {
            this.sourceSection = sourceSection;
            this.node = node;
        }

    }

    @Registration(id = "testLoadSourceSection1")
    public static class TestLoadSourceSection1 extends TruffleInstrument {
        List<SourceSectionEvent> onlyNewEvents = new ArrayList<>();
        List<SourceSectionEvent> allEvents = new ArrayList<>();
        List<SourceSectionEvent> onlyStatements = new ArrayList<>();
        List<SourceSectionEvent> onlyExpressions = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().build(), new SourceSectionListener(allEvents), true);
            env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().build(),
                            new SourceSectionListener(onlyNewEvents), false);
            env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                            new SourceSectionListener(onlyStatements), true);
            env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(),
                            new SourceSectionListener(onlyExpressions), true);

            env.registerService(this);
        }

        private class SourceSectionListener implements LoadSourceSectionEventListener {

            private final List<SourceSectionEvent> events;

            SourceSectionListener(List<SourceSectionEvent> events) {
                this.events = events;
            }

            public void onLoad(SourceSection sourceSection, Node node) {
                events.add(new SourceSectionEvent(sourceSection, node));
            }
        }

    }

    @Test
    public void testLoadSourceSectionException() throws IOException {
        engine.getInstruments().get("testLoadSourceSectionException").setEnabled(true);
        run("STATEMENT");
        Assert.assertTrue(getErr().contains("TestLoadSourceSectionExceptionClass"));
    }

    private static class TestLoadSourceSectionExceptionClass extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

    @Registration(id = "testLoadSourceSectionException")
    public static class TestLoadSourceSectionException extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().build(), new LoadSourceSectionEventListener() {
                public void onLoad(SourceSection sourceSection, Node node) {
                    throw new TestLoadSourceSectionExceptionClass();
                }
            }, true);
        }

    }

}
