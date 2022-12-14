/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.instrument;

import static com.oracle.truffle.api.instrument.StandardSyntaxTag.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.local.*;

/**
 * A visitor which traverses a completely parsed Simple AST (presumed not yet executed) and enables
 * instrumentation at a few standard kinds of nodes.
 */
public class SLStandardASTProber implements ASTProber {

    public void probeAST(final Instrumenter instrumenter, Node startNode) {
        startNode.accept(new NodeVisitor() {

            /**
             * Instruments and tags all relevant {@link SLStatementNode}s and
             * {@link SLExpressionNode}s. Currently, only SLStatementNodes that are not
             * SLExpressionNodes are tagged as statements.
             */
            public boolean visit(Node node) {

                if (!(node instanceof InstrumentationNode) && node instanceof SLStatementNode && node.getParent() != null && node.getSourceSection() != null) {
                    // All SL nodes are instrumentable, but treat expressions specially

                    if (node instanceof SLExpressionNode) {
                        SLExpressionNode expressionNode = (SLExpressionNode) node;
                        final Probe probe = instrumenter.probe(expressionNode);
                        if (node instanceof SLWriteLocalVariableNode) {
                            probe.tagAs(STATEMENT, null);
                            probe.tagAs(ASSIGNMENT, null);
                        }
                    } else {
                        SLStatementNode statementNode = (SLStatementNode) node;
                        final Probe probe = instrumenter.probe(statementNode);
                        probe.tagAs(STATEMENT, null);
                        if (node instanceof SLWhileNode) {
                            probe.tagAs(START_LOOP, null);
                        }
                    }
                }
                return true;
            }
        });
    }
}
