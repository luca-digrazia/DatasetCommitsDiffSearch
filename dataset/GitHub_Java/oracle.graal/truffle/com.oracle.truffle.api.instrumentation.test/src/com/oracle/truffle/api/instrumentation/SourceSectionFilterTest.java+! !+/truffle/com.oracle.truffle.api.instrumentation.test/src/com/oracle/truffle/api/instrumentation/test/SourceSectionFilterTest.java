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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("deprecation")
public class SourceSectionFilterTest {

    private static final int LINE_LENGTH = 6;

    private static final Set<Class<?>> ALL_TAGS = new HashSet<>(Arrays.asList(InstrumentationTestLanguage.TAGS));

    private static boolean isInstrumentedNode(SourceSectionFilter filter, Node instrumentedNode) {
        try {
            Method m = filter.getClass().getDeclaredMethod("isInstrumentedNode", Set.class, Node.class, SourceSection.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, ALL_TAGS, instrumentedNode, instrumentedNode != null ? instrumentedNode.getSourceSection() : null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isInstrumented(SourceSectionFilter filter, Node rootNode, Node node) {
        return isInstrumentedRoot(filter, rootNode) && isInstrumentedNode(filter, node);
    }

    private static boolean isInstrumentedRoot(SourceSectionFilter filter, Node root) {
        try {
            Method m = filter.getClass().getDeclaredMethod("isInstrumentedRoot", Set.class, SourceSection.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, ALL_TAGS, root != null ? root.getSourceSection() : null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Node createNode(final SourceSection section, final Class<?>... tags) {
        return new Node() {

            @Override
            public SourceSection getSourceSection() {
                return section;
            }

            @Override
            protected boolean isTaggedWith(Class<?> tag) {
                for (int i = 0; i < tags.length; i++) {
                    if (tags[i] == tag) {
                        return true;
                    }
                }
                return super.isTaggedWith(tag);
            }

        };
    }

    @Test
    public void testEmpty() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Node unavailable = createNode(SourceSection.createUnavailable(null, null));
        SourceSectionFilter filter = SourceSectionFilter.ANY;
        Node sampleSection = createNode(sampleSource.createSection(2));

        Assert.assertTrue(isInstrumentedNode(filter, source()));
        Assert.assertTrue(isInstrumentedNode(filter, unavailable));
        Assert.assertTrue(isInstrumentedNode(filter, sampleSection));

        Assert.assertTrue(isInstrumentedRoot(filter, null));
        Assert.assertTrue(isInstrumentedRoot(filter, unavailable));
        Assert.assertTrue(isInstrumentedRoot(filter, sampleSection));

        Assert.assertTrue(isInstrumented(filter, root, source()));
        Assert.assertTrue(isInstrumented(filter, root, unavailable));
        Assert.assertTrue(isInstrumented(filter, root, sampleSection));

        Class<?> prevTag = null;
        for (Class<?> tag : InstrumentationTestLanguage.TAGS) {
            Assert.assertTrue(isInstrumented(filter, root, source(tag)));
            Assert.assertTrue(isInstrumented(filter, root, unavailable));
            Assert.assertTrue(isInstrumented(filter, root, createNode(sampleSource.createSection(0, 4), tag)));
            if (prevTag != null) {
                Assert.assertTrue(isInstrumented(filter, root, source(tag)));
                Assert.assertTrue(isInstrumented(filter, root, unavailable));
                Assert.assertTrue(isInstrumented(filter, root, createNode(sampleSource.createSection(0, 4), tag, prevTag)));
            }
            prevTag = tag;
        }
        Assert.assertNotNull(filter.toString());

    }

    @Test
    public void testLineIn() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 1).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 2).build(),
                        root, createNode(sampleSource.createSection(2, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 1).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 1, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH + 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 1).build(),
                        root, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIs(1).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineIn(IndexRange.byLength(1, 1), IndexRange.between(2, 3), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineIn(IndexRange.byLength(1, 1), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().lineIn(2, 2).build().toString());
    }

    @Test
    public void testLineStartIn() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 15))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 15))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 15))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 15))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(6, 15))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineStartsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(12, 6))));
    }

    @Test
    public void testLineEndsIn() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 6))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 6))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 6))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(0, 6))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(6, 6))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineEndsIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(12, 6))));
    }

    @Test
    public void testLineNotIn() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 2)).build(),
                        root, createNode(sampleSource.createSection(2, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(6, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 1, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(3 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH + 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(2, 2)).build(),
                        root, createNode(sampleSource.createSection(1 * LINE_LENGTH - 2, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(0, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1), IndexRange.between(2, 3), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().lineNotIn(IndexRange.byLength(1, 1), IndexRange.byLength(3, 1)).build(),
                        root, createNode(sampleSource.createSection(LINE_LENGTH, LINE_LENGTH), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().lineIn(2, 2).build().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLineInFail1() {
        SourceSectionFilter.newBuilder().lineIn(0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLineInFail2() {
        SourceSectionFilter.newBuilder().lineIn(3, -1);
    }

    @Test
    public void testMimeType() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs().build(),
                                        null, createNode(sampleSource.withMimeType("mime3").createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1").build(),
                                        null, createNode(sampleSource.withMimeType("mime1").createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1").build(),
                                        null, createNode(sampleSource.withMimeType("mime2").createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build(),
                                        null, createNode(sampleSource.withMimeType("mime2").createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build(),
                                        null, createNode(sampleSource.withMimeType("mime3").createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build(),
                                        null, createNode(sampleSource.withMimeType(null).createSection(0, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build().toString());
    }

    @Test
    public void testIndexIn() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(0, 0).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(0, 1).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(0, 4), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(4, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(4, 6), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(10, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(9, 1), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(sampleSource.createSection(9, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                        root, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 5), IndexRange.between(5, 6)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexIn(IndexRange.between(0, 5), IndexRange.between(11, 12)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().indexIn(5, 5).build().toString());

    }

    @Test
    public void testIndexNotIn() {
        Source sampleSource = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root = createNode(sampleSource.createSection(0, 23));
        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(0, 0)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(0, 1)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(0, 4), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(0, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(4, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(4, 6), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(10, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(9, 1), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(sampleSource.createSection(9, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.byLength(5, 5)).build(),
                        root, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(0, 5)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(0, 5), IndexRange.between(5, 6)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().indexNotIn(IndexRange.between(0, 5), IndexRange.between(11, 12)).build(),
                        root, createNode(sampleSource.createSection(5, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().indexIn(5, 5).build().toString());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexInFail1() {
        SourceSectionFilter.newBuilder().indexIn(-1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexInFail2() {
        SourceSectionFilter.newBuilder().indexIn(3, -1);
    }

    @Test
    public void testSourceIn() {
        Source sampleSource1 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Source sampleSource2 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Source sampleSource3 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root1 = createNode(sampleSource1.createSection(0, 23));
        Node root2 = createNode(sampleSource2.createSection(0, 23));
        Node root3 = createNode(sampleSource3.createSection(0, 23));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        root1, createNode(sampleSource1.createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        null, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        root2, createNode(sampleSource2.createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        root2, createNode(sampleSource2.createSection(0, 5), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        root1, createNode(sampleSource1.createSection(0, 5), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        root3, createNode(sampleSource3.createSection(0, 5), tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().toString());
    }

    @Test
    public void testSourceSectionEquals() {
        Source sampleSource1 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Source sampleSource2 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();
        Node root1 = createNode(sampleSource1.createSection(0, 23));
        Node root2 = createNode(sampleSource2.createSection(0, 23));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                                        root1, createNode(sampleSource1.createSection(1, 6), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                                        root2, createNode(sampleSource2.createSection(1, 6), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 7)).build(),
                                        root1, createNode(sampleSource1.createSection(1, 6), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6)).build(),
                                        root1, createNode(sampleSource1.createSection(1, 6), tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        root1, createNode(sampleSource1.createSection(2, 7), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        root1, createNode(sampleSource1.createSection(2, 8), tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        null, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(2, 6), sampleSource1.createSection(2, 7)).build(),
                                        root1, null));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(1, 6)).build().toString());
    }

    @Test
    public void testRootSourceSectionEquals() {
        Source sampleSource1 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build();

        Assert.assertTrue(isInstrumentedNode(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(6, 4))));
        Assert.assertTrue(isInstrumentedRoot(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 6))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 6)), createNode(sampleSource1.createSection(6, 4))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(2, 6)), createNode(sampleSource1.createSection(1, 4))));

        Assert.assertFalse(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 7)), createNode(sampleSource1.createSection(1, 4))));

        Assert.assertTrue(isInstrumented(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build(),
                        createNode(sampleSource1.createSection(1, 6)), createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().rootSourceSectionEquals(sampleSource1.createSection(1, 6)).build().toString());
    }

    private static Node source(Class<?>... tags) {
        return createNode(Source.newBuilder("foo").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build().createSection(0, 3), tags);
    }

    @Test
    public void testTagsIn() {
        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs().build(), null, source()));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs().build(), null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.ROOT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source()));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testTagsNotIn() {
        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot().build(),
                                        null, source()));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot().build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.ROOT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source()));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build(),
                                        null, source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testComplexFilter() {
        Source sampleSource1 = Source.newBuilder("line1\nline2\nline3\nline4").name("unknown").mimeType("mime2").build();
        Node root = createNode(sampleSource1.createSection(0, 23));

        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.DEFINE).//
                        tagIsNot(InstrumentationTestLanguage.DEFINE, InstrumentationTestLanguage.ROOT).//
                        indexIn(0, 3).//
                        sourceIs(sampleSource1).sourceSectionEquals(sampleSource1.createSection(0, 5)).//
                        lineIn(1, 1).lineIs(1).mimeTypeIs("mime1", "mime2").build();

        Assert.assertFalse(isInstrumented(filter, root, source()));
        Assert.assertTrue(isInstrumentedRoot(filter, null));
        Assert.assertFalse(isInstrumentedNode(filter, source()));

        Assert.assertFalse(isInstrumented(filter, root, createNode(SourceSection.createUnavailable(null, null))));
        Assert.assertFalse(isInstrumentedRoot(filter, createNode(SourceSection.createUnavailable(null, null))));
        Assert.assertFalse(isInstrumentedNode(filter, createNode(SourceSection.createUnavailable(null, null))));

        Assert.assertTrue(isInstrumented(filter, root, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.EXPRESSION))));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(0, 5))));
        Assert.assertTrue(isInstrumentedNode(filter, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.EXPRESSION))));

        Assert.assertFalse(isInstrumented(filter, root, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.STATEMENT))));
        Assert.assertTrue(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(0, 5))));
        Assert.assertFalse(isInstrumentedRoot(filter, createNode(sampleSource1.createSection(10, 5))));
        Assert.assertFalse(isInstrumentedNode(filter, createNode(sampleSource1.createSection(0, 5), tags(InstrumentationTestLanguage.STATEMENT))));

        Assert.assertNotNull(filter.toString());
    }

    private static Class<?>[] tags(Class<?>... tags) {
        return tags;
    }

}
