/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.instrument;

import java.io.IOException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.AdvancedInstrumentResultListener;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRootFactory;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.InstrumentationTestRootNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestAdditionNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestLanguageNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestLanguageWrapperNode;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestValueNode;

@TruffleLanguage.Registration(name = "instrumentationTestLanguage", version = "0", mimeType = "text/x-instTest")
public final class InstrumentationTestingLanguage extends TruffleLanguage<Object> {

    public static final InstrumentationTestingLanguage INSTANCE = new InstrumentationTestingLanguage();

    static enum InstrumentTestTag implements SyntaxTag {

        ADD_TAG("addition", "test language addition node"),

        VALUE_TAG("value", "test language value node");

        private final String name;
        private final String description;

        private InstrumentTestTag(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    private InstrumentationTestingLanguage() {
    }

    @Override
    protected CallTarget parse(Source code, Node context, String... argumentNames) throws IOException {
        final TestValueNode leftValueNode = new TestValueNode(6);
        final TestValueNode rightValueNode = new TestValueNode(7);
        final TestAdditionNode addNode = new TestAdditionNode(leftValueNode, rightValueNode);
        final InstrumentationTestRootNode rootNode = new InstrumentationTestRootNode(addNode);
        final TruffleRuntime runtime = Truffle.getRuntime();
        final CallTarget callTarget = runtime.createCallTarget(rootNode);
        return callTarget;
    }

    @Override
    protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
        return null;
    }

    @Override
    protected Object getLanguageGlobal(Object context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    @Override
    protected Visualizer getVisualizer() {
        return null;
    }

    @Override
    protected boolean isInstrumentable(Node node) {
        return node instanceof TestAdditionNode || node instanceof TestValueNode;
    }

    @Override
    protected WrapperNode createWrapperNode(Node node) {
        if (isInstrumentable(node)) {
            return new TestLanguageWrapperNode((TestLanguageNode) node);
        }
        return null;
    }

    @Override
    protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
        return null;
    }

    @Override
    protected AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws IOException {
        return null;
    }

    @Override
    protected Object createContext(Env env) {
        return null;
    }

    static final class TestASTProber implements ASTProber {

        public void probeAST(final Instrumenter instrumenter, RootNode startNode) {
            startNode.accept(new NodeVisitor() {

                @Override
                public boolean visit(Node node) {
                    if (node instanceof TestLanguageNode) {

                        final TestLanguageNode testNode = (TestLanguageNode) node;

                        if (node instanceof TestValueNode) {
                            instrumenter.probe(testNode).tagAs(InstrumentTestTag.VALUE_TAG, null);

                        } else if (node instanceof TestAdditionNode) {
                            instrumenter.probe(testNode).tagAs(InstrumentTestTag.ADD_TAG, null);

                        }
                    }
                    return true;
                }
            });
        }
    }

}
