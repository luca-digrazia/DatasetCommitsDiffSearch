/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;

public class CGTrackingDFAStateNode extends DFAStateNode {

    private final DFACaptureGroupPartialTransition anchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransition unAnchoredFinalStateTransition;

    @CompilationFinal(dimensions = 1) private final short[] captureGroupTransitions;

    @CompilationFinal(dimensions = 1) private final short[] precedingCaptureGroupTransitions;

    @Child private DFACaptureGroupPartialTransitionDispatchNode transitionDispatchNode;

    public CGTrackingDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher, short[] captureGroupTransitions,
                    short[] precedingCaptureGroupTransitions,
                    DFACaptureGroupPartialTransition anchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition unAnchoredFinalStateTransition) {
        super(id, flags, loopOptimizationNode, successors, matchers, null, allTransitionsInOneTreeMatcher);
        this.captureGroupTransitions = captureGroupTransitions;
        this.precedingCaptureGroupTransitions = precedingCaptureGroupTransitions;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
        this.anchoredFinalStateTransition = anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = unAnchoredFinalStateTransition;
    }

    private CGTrackingDFAStateNode(CGTrackingDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.captureGroupTransitions = copy.captureGroupTransitions;
        this.precedingCaptureGroupTransitions = copy.precedingCaptureGroupTransitions;
        this.anchoredFinalStateTransition = copy.anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = copy.unAnchoredFinalStateTransition;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
    }

    private DFACaptureGroupLazyTransition getCGTransitionToSelf(TRegexDFAExecutorNode executor) {
        return executor.getCGTransitions()[captureGroupTransitions[getLoopToSelf()]];
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new CGTrackingDFAStateNode(this, copyID);
    }

    @Override
    public void executeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(compactString);
        beforeFindSuccessor(locals, executor);
        if (!executor.hasNext(locals)) {
            locals.setSuccessorIndex(atEnd1(locals, executor));
            return;
        }
        if (treeTransitionMatching()) {
            doTreeMatch(locals, executor, compactString);
            return;
        }
        if (checkMatch1(locals, executor, compactString)) {
            if (executor.hasNext(locals)) {
                if (!checkMatch2(locals, executor, compactString)) {
                    return;
                }
            } else {
                locals.setSuccessorIndex(atEnd2(locals, executor));
                return;
            }
            final int preLoopIndex = locals.getIndex();
            if (executor.isForward() && hasLoopToSelf() && loopOptimizationNode.getIndexOfChars() != null) {
                int indexOfResult = loopOptimizationNode.getIndexOfNode().execute(locals.getInput(),
                                preLoopIndex,
                                locals.getCurMaxIndex(),
                                loopOptimizationNode.getIndexOfChars());
                if (indexOfResult < 0) {
                    locals.setIndex(locals.getCurMaxIndex());
                    locals.setSuccessorIndex(atEnd3(locals, executor, preLoopIndex));
                    return;
                } else {
                    if (successors.length == 2) {
                        int successor = (getLoopToSelf() + 1) % 2;
                        CompilerAsserts.partialEvaluationConstant(successor);
                        locals.setIndex(indexOfResult + 1);
                        locals.setSuccessorIndex(successor);
                        successorFound3(locals, executor, successor, preLoopIndex);
                        return;
                    } else {
                        locals.setIndex(indexOfResult);
                    }
                }
            }
            while (executor.hasNext(locals)) {
                if (!checkMatch3(locals, executor, compactString, preLoopIndex)) {
                    return;
                }
            }
            locals.setSuccessorIndex(atEnd3(locals, executor, preLoopIndex));
        }
    }

    private void doTreeMatch(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        int successor = getTreeMatcher().checkMatchTree(locals, executor, this, c);
        assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
        locals.setSuccessorIndex(successor);
    }

    /**
     * Finds the first matching transition. If a transition matches,
     * {@link #successorFound(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int)} is called. The
     * index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexExecutorNode#getChar(TRegexExecutorLocals)}) or {@link #FS_RESULT_NO_SUCCESSOR}
     * is stored via {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch1(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        for (int i = 0; i < matchers.length; i++) {
            if (matchers[i].execute(c, compactString)) {
                CompilerAsserts.partialEvaluationConstant(i);
                successorFound(locals, executor, i);
                locals.setSuccessorIndex(i);
                return isLoopToSelf(i);
            }
        }
        locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
        return false;
    }

    /**
     * Finds the first matching transition. This method is called only if the transition found by
     * {@link #checkMatch1(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, boolean)} was a loop back
     * to this state (indicated by {@link #isLoopToSelf(int)}). If a transition <i>other than</i>
     * the looping transition matches,
     * {@link #successorFound2(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int)} is called. The
     * index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexExecutorNode#getChar(TRegexExecutorLocals)}) or {@link #FS_RESULT_NO_SUCCESSOR}
     * is stored via {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}. If no transition
     * matches, {@link #noSuccessor2(TRegexDFAExecutorLocals, TRegexDFAExecutorNode)} is called.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        for (int i = 0; i < matchers.length; i++) {
            if (matchers[i].execute(c, compactString)) {
                locals.setSuccessorIndex(i);
                if (!isLoopToSelf(i)) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    successorFound2(locals, executor, i);
                    return false;
                }
                return true;
            }
        }
        locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
        noSuccessor2(locals, executor);
        return false;
    }

    /**
     * Finds the first matching transition. This method is called only if the transitions found by
     * {@link #checkMatch1(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, boolean)} AND
     * {@link #checkMatch2(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, boolean)} both were a
     * loop back to this state (indicated by {@link #isLoopToSelf(int)}), and will be called in a
     * loop until a transition other than the loop back transition matches. If a transition <i>other
     * than</i> the looping transition matches,
     * {@link #successorFound3(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int, int)} is called.
     * The index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexExecutorNode#getChar(TRegexExecutorLocals)}) or {@link #FS_RESULT_NO_SUCCESSOR}
     * is stored via {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}. If no transition
     * matches, {@link #noSuccessor3(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int)} is
     * called.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @param preLoopIndex the index pointed to by {@link TRegexDFAExecutorLocals#getIndex()}
     *            <i>before</i> this method is called for the first time.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch3(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString, int preLoopIndex) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        for (int i = 0; i < matchers.length; i++) {
            if (matchers[i].execute(c, compactString)) {
                locals.setSuccessorIndex(i);
                if (!isLoopToSelf(i)) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    successorFound3(locals, executor, i, preLoopIndex);
                    return false;
                }
                return true;
            }
        }
        locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
        noSuccessor3(locals, executor, preLoopIndex);
        return false;
    }

    private void beforeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalState(locals, executor);
        }
    }

    @Override
    void successorFound(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getPartialTransitions()[i].apply(executor, locals.getCGData(), prevIndex(locals));
        } else {
            transitionDispatchNode.applyPartialTransition(locals, executor, locals.getLastTransition(), i, prevIndex(locals));
        }
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    private int atEnd1(TRegexDFAExecutorLocals frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            applyAnchoredFinalStateTransition(frame, executor);
        } else {
            checkFinalState(frame, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    void successorFound2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        assert locals.getLastTransition() == captureGroupTransitions[getLoopToSelf()];
        if (executor.isSearching()) {
            checkFinalStateLoop(locals, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, locals.getCGData(), prevIndex(locals));
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    void noSuccessor2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        checkFinalStateLoop(locals, executor);
    }

    private int atEnd2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return atEndLoop(locals, executor);
    }

    void successorFound3(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        applyLoopTransitions(locals, executor, preLoopIndex, prevIndex(locals) - 1);
        if (executor.isSearching()) {
            checkFinalStateLoop(locals, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, locals.getCGData(), prevIndex(locals));
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    void noSuccessor3(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        applyLoopTransitions(locals, executor, preLoopIndex, prevIndex(locals) - 1);
        checkFinalStateLoop(locals, executor);
    }

    private int atEnd3(TRegexDFAExecutorLocals frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame));
        return atEndLoop(frame, executor);
    }

    private void applyLoopTransitions(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int preLoopIndex, int postLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        DFACaptureGroupPartialTransition transition = getCGTransitionToSelf(executor).getPartialTransitions()[getLoopToSelf()];
        if (transition.doesReorderResults()) {
            for (int i = preLoopIndex - 1; i <= postLoopIndex; i++) {
                transition.apply(executor, locals.getCGData(), i);
            }
        } else {
            transition.apply(executor, locals.getCGData(), postLoopIndex);
        }
    }

    private int atEndLoop(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert locals.getLastTransition() == captureGroupTransitions[getLoopToSelf()];
        if (isAnchoredFinalState() && executor.atEnd(locals)) {
            getCGTransitionToSelf(executor).getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            anchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
            storeResult(locals, executor);
        } else if (isFinalState()) {
            getCGTransitionToSelf(executor).getTransitionToFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
            storeResult(locals, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    private void checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            if (precedingCaptureGroupTransitions.length == 1) {
                executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToFinalState().applyPreFinalStateTransition(
                                executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            } else {
                transitionDispatchNode.applyPreFinalTransition(locals, executor, locals.getLastTransition(), curIndex(locals));
            }
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
            storeResult(locals, executor);
        }
    }

    private void applyAnchoredFinalStateTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(
                            executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
        } else {
            transitionDispatchNode.applyPreAnchoredFinalTransition(locals, executor, locals.getLastTransition(), curIndex(locals));
        }
        anchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
        storeResult(locals, executor);
    }

    private void checkFinalStateLoop(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert locals.getLastTransition() == captureGroupTransitions[getLoopToSelf()];
        if (isFinalState()) {
            getCGTransitionToSelf(executor).getTransitionToFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), prevIndex(locals));
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            storeResult(locals, executor);
        }
    }

    private void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!executor.isSearching()) {
            locals.getCGData().exportResult((byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        }
        locals.setResultInt(0);
    }
}
