/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.nodes.nfa;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.charset.CP16BitMatchers;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;

public class TRegexLiteralLookAroundExecutorNode extends TRegexExecutorNode {

    private final boolean forward;
    private final boolean negated;
    @Children private CharMatcher[] matchers;

    public TRegexLiteralLookAroundExecutorNode(LookAroundAssertion lookAround, CompilationBuffer compilationBuffer) {
        assert lookAround.isLiteral();
        forward = lookAround.isLookAheadAssertion();
        negated = lookAround.isNegated();
        matchers = new CharMatcher[lookAround.getLiteralLength()];
        for (int i = 0; i < matchers.length; i++) {
            CharMatcher matcher = CP16BitMatchers.createMatcher(lookAround.getGroup().getFirstAlternative().get(i).asCharacterClass().getCharSet(), compilationBuffer);
            matchers[forward ? i : matchers.length - (i + 1)] = insert(matcher);
        }
    }

    @Override
    public boolean writesCaptureGroups() {
        return false;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException();
    }

    @ExplodeLoop
    @Override
    public Object execute(TRegexExecutorLocals abstractLocals, boolean compactString) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;
        int index = locals.getIndex();
        for (int i = 0; i < matchers.length; i++) {
            int iChar = forward ? index + i : index - i;
            if (!inputBoundsCheck(iChar, 0, locals.getMaxIndex()) || !matchers[i].execute(inputGetChar(locals, iChar), compactString)) {
                return negated;
            }
        }
        return !negated;
    }

    private char inputGetChar(TRegexBacktrackingNFAExecutorLocals locals, int index) {
        return forward ? getCharAt(locals, index) : getCharAt(locals, index - 1);
    }

    private boolean inputBoundsCheck(int i, int min, int max) {
        return forward ? i < max : i > min;
    }
}
