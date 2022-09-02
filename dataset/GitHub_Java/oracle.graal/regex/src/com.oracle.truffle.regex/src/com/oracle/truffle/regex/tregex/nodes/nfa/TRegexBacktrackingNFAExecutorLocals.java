/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nfa.PureNFATransition;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Contains the stack used by {@link TRegexBacktrackingNFAExecutorNode}. One stack frame represents
 * a snapshot of the backtracker's state. The backtracker state consists of:
 * <ul>
 * <li>the current index in the input string</li>
 * <li>the current NFA state</li>
 * <li>all current capture group boundaries</li>
 * <li>all current quantifier loop counters</li>
 * <li>all saved indices for zero-width checks in quantifiers</li>
 * </ul>
 * The backtracker state is written to the stack in the order given above, so one stack frame looks
 * like this:
 *
 * <pre>
 * sp    sp+1      sp+2           sp+2+ncg           sp+2+ncg+nq
 * |     |         |              |                  |
 * v     v         v              v                  v
 * --------------------------------------------------------------------------
 * |index|nfa_state|capture_groups|quantifiers_counts|zero_width_quantifiers|
 * --------------------------------------------------------------------------
 *
 * frame size: 2 + n_capture_groups*2 + n_quantifiers + n_zero_width_quantifiers
 * </pre>
 */
public final class TRegexBacktrackingNFAExecutorLocals extends TRegexExecutorLocals {

    private final int stackFrameSize;
    private final int nQuantifierCounts;
    private final int nZeroWidthQuantifiers;
    private final int[] zeroWidthTermEnclosedCGLow;
    private final int[] zeroWidthQuantifierCGOffsets;
    private final int stackBase;
    private final Stack stack;
    private int sp;
    private final int[] result;
    private final long[] transitionBitSet;
    private int lastResultSp = -1;
    private int lastInnerLiteralIndex;
    private int lastInitialStateIndex;

    public TRegexBacktrackingNFAExecutorLocals(Object input, int fromIndex, int index, int maxIndex, int nCaptureGroups, int nQuantifiers, int nZeroWidthQuantifiers, int[] zeroWidthTermEnclosedCGLow,
                    int[] zeroWidthQuantifierCGOffsets, int maxNTransitions) {
        this(input, fromIndex, index, maxIndex, nCaptureGroups, nQuantifiers, nZeroWidthQuantifiers, zeroWidthTermEnclosedCGLow,
                        zeroWidthQuantifierCGOffsets,
                        new Stack(new int[getStackFrameSize(nCaptureGroups, nQuantifiers, nZeroWidthQuantifiers, zeroWidthQuantifierCGOffsets) * 4]), 0,
                        BitSets.createBitSetArray(maxNTransitions));
        setIndex(fromIndex);
        clearCaptureGroups();
    }

    private TRegexBacktrackingNFAExecutorLocals(Object input, int fromIndex, int index, int maxIndex, int nCaptureGroups, int nQuantifiers, int nZeroWidthQuantifiers, int[] zeroWidthTermEnclosedCGLow,
                    int[] zeroWidthQuantifierCGOffsets, Stack stack, int stackBase,
                    long[] transitionBitSet) {
        super(input, fromIndex, maxIndex, index);
        this.stackFrameSize = getStackFrameSize(nCaptureGroups, nQuantifiers, nZeroWidthQuantifiers, zeroWidthQuantifierCGOffsets);
        this.nQuantifierCounts = nQuantifiers;
        this.nZeroWidthQuantifiers = nZeroWidthQuantifiers;
        this.zeroWidthTermEnclosedCGLow = zeroWidthTermEnclosedCGLow;
        this.zeroWidthQuantifierCGOffsets = zeroWidthQuantifierCGOffsets;
        this.stack = stack;
        this.stackBase = stackBase;
        this.sp = stackBase;
        this.result = new int[nCaptureGroups * 2];
        this.transitionBitSet = transitionBitSet;
    }

    private int[] stack() {
        return stack.stack;
    }

    private static int getStackFrameSize(int nCaptureGroups, int nQuantifiers, int nZeroWidthQuantifiers, int[] zeroWidthQuantifierCGOffsets) {
        return 2 + nCaptureGroups * 2 + nQuantifiers + nZeroWidthQuantifiers + zeroWidthQuantifierCGOffsets[zeroWidthQuantifierCGOffsets.length - 1];
    }

    public TRegexBacktrackingNFAExecutorLocals createSubNFALocals() {
        dupFrame();
        return newSubLocals();
    }

    public TRegexBacktrackingNFAExecutorLocals createSubNFALocals(PureNFATransition t) {
        dupFrame();
        t.getGroupBoundaries().applyExploded(stack(), sp + stackFrameSize + 2, getIndex());
        return newSubLocals();
    }

    private TRegexBacktrackingNFAExecutorLocals newSubLocals() {
        return new TRegexBacktrackingNFAExecutorLocals(getInput(), getFromIndex(), getIndex(), getMaxIndex(), result.length / 2, nQuantifierCounts, nZeroWidthQuantifiers, zeroWidthTermEnclosedCGLow,
                        zeroWidthQuantifierCGOffsets, stack, sp + stackFrameSize,
                        transitionBitSet);
    }

    private int offsetIP() {
        return sp + 1;
    }

    private int offsetCaptureGroups() {
        return sp + 2;
    }

    private int offsetQuantifierCounts() {
        return sp + 2 + result.length;
    }

    private int offsetZeroWidthQuantifierIndices() {
        return sp + 2 + result.length + nQuantifierCounts;
    }

    private int offsetZeroWidthQuantifierCG() {
        return sp + 2 + result.length + nQuantifierCounts + nZeroWidthQuantifiers;
    }

    private int offsetQuantifierCount(Quantifier q) {
        CompilerDirectives.isPartialEvaluationConstant(q.getIndex());
        return offsetQuantifierCounts() + q.getIndex();
    }

    private int offsetZeroWidthQuantifierIndex(Quantifier q) {
        CompilerDirectives.isPartialEvaluationConstant(q.getZeroWidthIndex());
        return offsetZeroWidthQuantifierIndices() + q.getZeroWidthIndex();
    }

    private int offsetZeroWidthQuantifierCG(Quantifier q) {
        CompilerDirectives.isPartialEvaluationConstant(q.getZeroWidthIndex());
        return offsetZeroWidthQuantifierCG() + zeroWidthQuantifierCGOffsets[q.getZeroWidthIndex()];
    }

    public void apply(PureNFATransition t, int index) {
        t.getGroupBoundaries().applyExploded(stack(), offsetCaptureGroups(), index);
    }

    public void resetToInitialState() {
        clearCaptureGroups();
        clearQuantifierCounts();
        // no need to reset zero-width quantifier indices, they will always be overwritten before
        // being checked
    }

    protected void clearCaptureGroups() {
        Arrays.fill(stack(), offsetCaptureGroups(), offsetCaptureGroups() + result.length, -1);
    }

    protected void clearQuantifierCounts() {
        Arrays.fill(stack(), offsetQuantifierCounts(), offsetQuantifierCounts() + nQuantifierCounts, 0);
    }

    public void push() {
        sp += stackFrameSize;
    }

    public void dupFrame() {
        dupFrame(1);
    }

    public void dupFrame(int n) {
        int minSize = sp + (stackFrameSize * (n + 1));
        if (stack().length < minSize) {
            int newLength = stack().length << 1;
            while (newLength < minSize) {
                newLength <<= 1;
            }
            stack.stack = Arrays.copyOf(stack(), newLength);
        }
        int targetFrame = sp;
        for (int i = 0; i < n; i++) {
            targetFrame += stackFrameSize;
            System.arraycopy(stack(), sp, stack(), targetFrame, stackFrameSize);
        }
    }

    public void pushResult(PureNFATransition t, int index) {
        t.getGroupBoundaries().applyExploded(result, 0, index);
        pushResult();
    }

    /**
     * Marks that a result was pushed at the current stack frame.
     */
    public void pushResult() {
        lastResultSp = sp;
    }

    /**
     * Copies the current capture group boundaries to the result array.
     */
    public void setResult() {
        System.arraycopy(stack(), offsetCaptureGroups(), result, 0, result.length);
    }

    public boolean canPopResult() {
        return lastResultSp == sp;
    }

    public int[] popResult() {
        return lastResultSp < 0 ? null : result;
    }

    public boolean canPop() {
        return sp > stackBase;
    }

    public int pop() {
        assert sp > stackBase;
        sp -= stackFrameSize;
        restoreIndex();
        return stack()[offsetIP()];
    }

    public void saveIndex(int index) {
        stack()[sp] = index;
    }

    public void restoreIndex() {
        setIndex(stack()[sp]);
    }

    public int setPc(int pc) {
        return stack()[offsetIP()] = pc;
    }

    public int getCaptureGroupBoundary(int boundary) {
        return stack()[offsetCaptureGroups() + boundary];
    }

    public void setCaptureGroupBoundary(int boundary, int index) {
        stack()[offsetCaptureGroups() + boundary] = index;
    }

    public int getCaptureGroupStart(int groupNumber) {
        return getCaptureGroupBoundary(groupNumber * 2);
    }

    public int getCaptureGroupEnd(int groupNumber) {
        return getCaptureGroupBoundary(groupNumber * 2 + 1);
    }

    public void overwriteCaptureGroups(int[] captureGroups) {
        assert captureGroups.length == result.length;
        System.arraycopy(captureGroups, 0, stack(), offsetCaptureGroups(), captureGroups.length);
    }

    public int getQuantifierCount(Quantifier q) {
        return stack()[offsetQuantifierCount(q)];
    }

    public void setQuantifierCount(Quantifier q, int count) {
        stack()[offsetQuantifierCount(q)] = count;
    }

    public void resetQuantifierCount(Quantifier q) {
        stack()[offsetQuantifierCount(q)] = 0;
    }

    public void incQuantifierCount(Quantifier q) {
        stack()[offsetQuantifierCount(q)]++;
    }

    public int getZeroWidthQuantifierGuardIndex(Quantifier q) {
        return stack()[offsetZeroWidthQuantifierIndex(q)];
    }

    public void setZeroWidthQuantifierGuardIndex(Quantifier q) {
        stack()[offsetZeroWidthQuantifierIndex(q)] = getIndex();
    }

    public boolean isResultUnmodifiedByZeroWidthQuantifier(Quantifier q) {
        int start = offsetCaptureGroups() + 2 * zeroWidthTermEnclosedCGLow[q.getZeroWidthIndex()];
        int length = zeroWidthQuantifierCGOffsets[q.getZeroWidthIndex() + 1] - zeroWidthQuantifierCGOffsets[q.getZeroWidthIndex()];
        for (int i = 0; i < length; i++) {
            if (stack()[offsetZeroWidthQuantifierCG(q) + i] != stack()[start + i]) {
                return false;
            }
        }
        return true;
    }

    public void setZeroWidthQuantifierResults(Quantifier q) {
        int start = offsetCaptureGroups() + 2 * zeroWidthTermEnclosedCGLow[q.getZeroWidthIndex()];
        int length = zeroWidthQuantifierCGOffsets[q.getZeroWidthIndex() + 1] - zeroWidthQuantifierCGOffsets[q.getZeroWidthIndex()];
        System.arraycopy(stack(), start, stack(), offsetZeroWidthQuantifierCG(q), length);
    }

    public long[] getTransitionBitSet() {
        return transitionBitSet;
    }

    public int getLastInnerLiteralIndex() {
        return lastInnerLiteralIndex;
    }

    public void setLastInnerLiteralIndex(int i) {
        this.lastInnerLiteralIndex = i;
    }

    public int getLastInitialStateIndex() {
        return lastInitialStateIndex;
    }

    public void setLastInitialStateIndex(int i) {
        this.lastInitialStateIndex = i;
    }

    @TruffleBoundary
    public void printStack(int curPc) {
        System.out.println("STACK SNAPSHOT");
        System.out.println("==============");
        for (int i = sp; i >= 0; i -= stackFrameSize) {
            int read = i;
            System.out.print(String.format("pc: %d, i: %d,\n  cg: [", (i == sp ? curPc : stack()[i + 1]), stack()[i]));
            read += 2;
            for (int j = read; j < read + result.length; j++) {
                System.out.print(String.format("%d, ", stack()[j]));
            }
            read += result.length;
            System.out.print(String.format("],\n  quant: ["));
            for (int j = read; j < read + nQuantifierCounts; j++) {
                System.out.print(String.format("%d, ", stack()[j]));
            }
            read += nQuantifierCounts;
            System.out.print(String.format("],\n  zwq-indices: ["));
            for (int j = read; j < read + nZeroWidthQuantifiers; j++) {
                System.out.print(String.format("%d, ", stack()[j]));
            }
            read += nZeroWidthQuantifiers;
            System.out.print(String.format("],\n  zwq-cg: {\n"));
            for (int zwq = 0; zwq < nZeroWidthQuantifiers; zwq++) {
                System.out.print(String.format("    %d: [", zwq));
                for (int j = read; j < read + result.length; j++) {
                    System.out.print(String.format("%d, ", stack()[j]));
                }
                read += result.length;
                System.out.print("],\n");
            }
            System.out.println("}\n");
        }
    }

    private static final class Stack {

        private int[] stack;

        Stack(int[] stack) {
            this.stack = stack;
        }
    }
}
