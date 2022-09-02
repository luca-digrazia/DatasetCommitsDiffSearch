/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyCaseUnfoldingTrie.Unfolding;
import com.oracle.truffle.regex.util.TBitSet;

public class RubyCaseFolding {

    public static String caseFoldUnfoldString(int[] codepoints) {
        List<Integer> caseFolded = caseFold(codepoints);

        List<Unfolding> unfoldings = RubyCaseUnfoldingTrie.findUnfoldings(caseFolded);

        StringBuilder out = new StringBuilder();
        out.append("(?:");

        // We identify segments of the string which are independent, i.e. there is no unfolding
        // that crosses the boundary of a segment. We the unfold each segment separately, which
        // helps to avoid unnecessary combinatorial explosions.
        int start = 0;
        int end = 0;
        int unfoldingsStartIndex = 0;
        int unfoldingsEndIndex = 0;
        for (int i = 0; i < unfoldings.size(); i++) {
            Unfolding unfolding = unfoldings.get(i);
            if (unfolding.getStart() >= end) {
                unfoldSegment(out, caseFolded, unfoldings.subList(unfoldingsStartIndex, unfoldingsEndIndex), start, end, 0);
                if (unfolding.getStart() > end) {
                    emitString(out, caseFolded.subList(end, unfolding.getStart()));
                }
                start = unfolding.getStart();
                unfoldingsStartIndex = i;
            }
            end = Math.max(end, unfolding.getEnd());
            unfoldingsEndIndex = i + 1;
        }

        unfoldSegment(out, caseFolded, unfoldings.subList(unfoldingsStartIndex, unfoldingsEndIndex), start, end, 0);
        if (end < caseFolded.size()) {
            emitString(out, caseFolded.subList(end, caseFolded.size()));
        }

        out.append(')');
        return out.toString();
    }

    private static List<Integer> caseFold(int[] codepoints) {
        List<Integer> caseFolded = new ArrayList<>();
        for (int codepoint : codepoints) {
            if (RubyCaseFoldingData.CASE_FOLD.containsKey(codepoint)) {
                for (int caseFoldedCodepoint : RubyCaseFoldingData.CASE_FOLD.get(codepoint)) {
                    caseFolded.add(caseFoldedCodepoint);
                }
            } else {
                caseFolded.add(codepoint);
            }
        }
        return caseFolded;
    }

    private static void emitChar(StringBuilder out, int codepoint, boolean inCharClass) {
        TBitSet syntaxChars = inCharClass ? RubyFlavorProcessor.CHAR_CLASS_SYNTAX_CHARACTERS : RubyFlavorProcessor.SYNTAX_CHARACTERS;
        if (syntaxChars.get(codepoint)) {
            out.append('\\');
        }
        out.appendCodePoint(codepoint);
    }

    private static void emitString(StringBuilder out, List<Integer> codepoints) {
        for (int codepoint : codepoints) {
            if (RubyFlavorProcessor.SYNTAX_CHARACTERS.get(codepoint)) {
                out.append('\\');
            }
            out.appendCodePoint(codepoint);
        }
    }

    private static void unfoldSegment(StringBuilder out, List<Integer> caseFolded, List<Unfolding> unfoldings, int start, int end, int backtrackingDepth) {
        if (backtrackingDepth > 8) {
            throw new UnsupportedRegexException("case-unfolding of case-insensitive string is too complex");
        }
        // The terminating condition of this recursion. This is reached when we have generated
        // an alternative that covers the entire case-folded segment given by `start` and `end`.
        if (start == end) {
            return;
        }

        // This shouldn't happen in our current use case, but its included for completeness.
        if (unfoldings.isEmpty()) {
            emitString(out, caseFolded.subList(start, end));
            return;
        }

        Unfolding unfolding = unfoldings.get(0);

        // Fast-forward to the next possible unfolding.
        if (unfolding.getStart() > start) {
            emitString(out, caseFolded.subList(start, unfolding.getStart()));
            unfoldSegment(out, caseFolded, unfoldings, unfolding.getStart(), end, backtrackingDepth);
            return;
        }

        // The unfolding has length > 1. We will generate two alternatives, one with the sequence
        // unfolded and one with the sequence folded.
        if (unfolding.getLength() > 1) {
            // If we do the unfolding, we will advance the `start` position. We will therefore
            // also clean up the `unfoldings` list so that we drop unfoldings that will be ruled
            // out by the current unfolding.
            int unfoldingsNextIndex = 1;
            while (unfoldingsNextIndex < unfoldings.size() && unfoldings.get(unfoldingsNextIndex).getStart() < unfolding.getEnd()) {
                unfoldingsNextIndex++;
            }
            // We include parentheses so that we limit the scope of the alternation operator (|).
            out.append("(?:");
            emitChar(out, unfolding.getCodepoint(), false);
            unfoldSegment(out, caseFolded, unfoldings.subList(unfoldingsNextIndex, unfoldings.size()), unfolding.getEnd(), end, backtrackingDepth + 1);
            out.append('|');
            // In the second alternative, where we decide not to pursue the unfolding, we just drop
            // it from the `unfoldings` list.
            unfoldSegment(out, caseFolded, unfoldings.subList(1, unfoldings.size()), start, end, backtrackingDepth + 1);
            out.append(')');
            return;
        }

        // The only possible unfoldings at this position have length == 1. We can express all the
        // choices by using a character class.
        out.append("[");
        emitChar(out, caseFolded.get(start), true);
        int unfoldingsNextIndex = 0;
        while (unfoldingsNextIndex < unfoldings.size() && unfoldings.get(unfoldingsNextIndex).getStart() == start) {
            // The `unfoldings` are sorted by length in descending order and all unfoldings have
            // length > 0.
            assert unfoldings.get(unfoldingsNextIndex).getLength() == 1;
            emitChar(out, unfoldings.get(unfoldingsNextIndex).getCodepoint(), true);
            unfoldingsNextIndex++;
        }
        out.append("]");
        unfoldSegment(out, caseFolded, unfoldings.subList(unfoldingsNextIndex, unfoldings.size()), start + 1, end, backtrackingDepth);
    }
}
