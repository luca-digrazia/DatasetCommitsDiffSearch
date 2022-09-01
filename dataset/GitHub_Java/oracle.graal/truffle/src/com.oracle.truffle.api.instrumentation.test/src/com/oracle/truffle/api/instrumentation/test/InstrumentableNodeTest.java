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
package com.oracle.truffle.api.instrumentation.test;

import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.ENTER;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.INPUT_VALUE;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.RETURN_VALUE;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ExpressionNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.MaterializeChildExpressionNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.MaterializedChildExpressionNode;

public class InstrumentableNodeTest extends InstrumentationEventTest {

    /*
     * Directly instrument and materialize all nodes.
     */
    @Test
    public void testSimpleMaterializeSyntax() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
    }

    /*
     * Directly instrument and materialize all nodes with input values.
     */
    @Test
    public void testSimpleMaterializeSyntaxWithInput() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, filter, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
    }

    /*
     * First instrument statements and then instrument expressions to test materialization at
     * locations where there is already a wrapper.
     */
    @Test
    public void testLateMaterializeSyntax() {
        Source source = createSource("MATERIALIZE_CHILD_EXPRESSION");
        SourceSectionFilter filter;

        filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute(source);
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializeChildExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializeChildExpressionNode);
        });
        assertAllEventsConsumed();
        filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute(source);
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });

    }

    /*
     * First instrument statements and then instrument expressions to test materialization at
     * locations where there is already a wrapper.
     */
    @Test
    public void testTagIsNot() {
        SourceSectionFilter filter;
        filter = SourceSectionFilter.newBuilder().tagIsNot(StandardTags.RootTag.class).build();
        instrumenter.attachExecutionEventFactory(filter, null, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");

        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof MaterializedChildExpressionNode);
        });

    }

    /*
     * Test materialize if the parent node is not instrumented. We need to call materializeSyntax
     * for all visited instrumentable nodes. Not just for instrumented ones.
     */
    @Test
    public void testMaterializeSyntaxNotInstrumented() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(expressionFilter, null, factory);
        execute("MATERIALIZE_CHILD_EXPRESSION");
        assertOn(ENTER, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertTrue(e.context.getInstrumentedNode() instanceof ExpressionNode);
        });
    }

}
