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
package com.oracle.truffle.regex.tregex.automaton;

import java.util.Arrays;
import java.util.Iterator;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.ImmutableSortedListOfRanges;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * This class provides an algorithm for converting a list of NFA transitions into a set of DFA
 * transitions.
 *
 * @param <TB> represents a DFA transition fragment. This type is used for both intermediate and
 *            final results.
 *
 * @see TransitionBuilder
 * @see TransitionSet
 */
public abstract class StateTransitionCanonicalizer<S extends AbstractState<S, T>, T extends AbstractTransition<S, T>, TB extends TransitionBuilder<S, T>> {

    private final ObjectArrayBuffer<T> argTransitions = new ObjectArrayBuffer<>();
    private final ObjectArrayBuffer<CodePointSet> argCharSets = new ObjectArrayBuffer<>();

    private static final int INITIAL_CAPACITY = 8;
    @SuppressWarnings("unchecked") private ObjectArrayBuffer<T>[] transitionLists = new ObjectArrayBuffer[INITIAL_CAPACITY];
    @SuppressWarnings("unchecked") private StateSet<S>[] targetStateSets = new StateSet[INITIAL_CAPACITY];
    private CodePointSet[] matcherBuilders = new CodePointSet[INITIAL_CAPACITY];
    private CompilationFinalBitSet leadsToFinalState = new CompilationFinalBitSet(INITIAL_CAPACITY);
    private int resultLength = 0;

    private final StateIndex<? super S> stateIndex;
    private final boolean forward;
    private final boolean prioritySensitive;

    public StateTransitionCanonicalizer(StateIndex<? super S> stateIndex, boolean forward, boolean prioritySensitive) {
        this.stateIndex = stateIndex;
        this.forward = forward;
        this.prioritySensitive = prioritySensitive;
    }

    /**
     * If priority-sensitive mode, transition sets are pruned after transitions to final states.
     * Also, target state sets are considered equal iff their order is equal as well.
     */
    protected boolean isPrioritySensitive() {
        return prioritySensitive;
    }

    /**
     * Submits an argument to be processed by {@link #run(CompilationBuffer)}.
     */
    public void addArgument(T transition, CodePointSet charSet) {
        argTransitions.add(transition);
        argCharSets.add(charSet);
    }

    /**
     * Runs the NFA to DFA transition conversion algorithm on the NFA transitions given by previous
     * calls to {@link #addArgument(AbstractTransition, CodePointSet)}. This algorithm has two
     * phases:
     * <ol>
     * <li>Merge NFA transitions according to their expected character sets. The result of this
     * phase is a list of {@link TransitionBuilder}s whose {@link CodePointSet}s have no more
     * intersections.</li>
     * <li>Merge {@link TransitionBuilder}s generated by the first phase if their <em>target
     * state</em> is equal and {@link #canMerge(TransitionBuilder, TransitionBuilder)} returns
     * {@code true}.</li>
     * </ol>
     *
     * @return a set of transition builders representing the DFA transitions generated from the
     *         given NFA transitions.
     */
    public TB[] run(CompilationBuffer compilationBuffer) {
        calcDisjointTransitions(compilationBuffer);
        TB[] result = mergeSameTargets(compilationBuffer);
        resultLength = 0;
        leadsToFinalState.clear();
        argTransitions.clear();
        argCharSets.clear();
        return result;
    }

    /**
     * Merges NFA transitions according to their expected character sets as returned
     * {@link TransitionBuilder#getMatcherBuilder()}, in the following way: <br>
     * <ul>
     * <li>The result of the algorithm is a list of transitions where no two elements have an
     * intersection in their respective set of expected characters. We initially define the result
     * as an empty list.</li>
     * <li>For every element <em>e</em> of the input list, we compare the expected character sets of
     * <em>e</em> and every element in the result list. If an intersection with an element
     * <em>r</em> of the result list is found, we distinguish the following two cases, where the
     * character set of an element <em>x</em> is denoted as <em>x.cs</em> and the transition set of
     * an element <em>x</em> is denoted as <em>x.ts</em>:
     * <ul>
     * <li>If <em>e.cs</em> contains <em>r.cs</em>, <em>e.ts</em> is merged into <em>r.ts</em> using
     * TransitionSet#addAll(TransitionSet).</li>
     * <li>Otherwise, a new transition containing <em>e.ts</em> and <em>r.ts</em> and the
     * intersection of <em>e.cs</em> and <em>r.cs</em> is added to the result list. This new
     * transition is created using TransitionBuilder#createMerged(TransitionBuilder, CodePointSet).
     * The intersection of <em>e.cs</em> and <em>r.cs</em> is removed from <em>r.cs</em>.</li>
     * </ul>
     * </li>
     * <li>Every time an intersection is found, that intersection is removed from <em>e.cs</em>. If
     * <em>e.cs</em> is not empty after <em>e</em> has been compared to every element of the result
     * list, <em>e</em> is added to the result list.</li>
     * <li>The result list at all times fulfills the property that no two elements in the list
     * intersect.</li>
     * </ul>
     * This algorithm has an important property: every entry in the input list will be merged with
     * the elements it intersects with <em>in the order of the input list</em>. This means that
     * given an input list {@code [1, 2, 3]} where all elements intersect with each other, element
     * {@code 2} will be merged with {@code 1} before {@code 3} is merged with {@code 1}. This
     * property is crucial for generating priority-sensitive DFAs, since we track priorities of NFA
     * transitions by their order!
     *
     * Example: <br>
     *
     * <pre>
     * input: [
     *   {transitionSet {1}, matcherBuilder [ab]},
     *   {transitionSet {2}, matcherBuilder [bc]}
     * ]
     * output: [
     *   {transitionSet {1},    matcherBuilder [a]},
     *   {transitionSet {1, 2}, matcherBuilder [b]},
     *   {transitionSet {2},    matcherBuilder [c]}
     * ]
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private void calcDisjointTransitions(CompilationBuffer compilationBuffer) {
        for (int i = 0; i < argTransitions.length(); i++) {
            T argTransition = argTransitions.get(i);
            CodePointSet argCharSet = argCharSets.get(i);
            int currentResultLength = resultLength;
            for (int j = 0; j < currentResultLength; j++) {
                ImmutableSortedListOfRanges.IntersectAndSubtractResult<CodePointSet> result = matcherBuilders[j].intersectAndSubtract(argCharSet, compilationBuffer);
                CodePointSet rSubtractedMatcher = result.subtractedA;
                CodePointSet eSubtractedMatcher = result.subtractedB;
                CodePointSet intersection = result.intersection;
                if (intersection.matchesSomething()) {
                    if (rSubtractedMatcher.matchesNothing()) {
                        addTransitionTo(j, argTransition);
                    } else {
                        createSlot();
                        matcherBuilders[j] = rSubtractedMatcher;
                        matcherBuilders[resultLength] = intersection;
                        targetStateSets[resultLength] = targetStateSets[j].copy();
                        transitionLists[resultLength].addAll(transitionLists[j]);
                        if (isPrioritySensitive() && leadsToFinalState.get(j)) {
                            leadsToFinalState.set(resultLength);
                        }
                        addTransitionTo(resultLength, argTransition);
                        resultLength++;
                    }
                    argCharSet = eSubtractedMatcher;
                    if (eSubtractedMatcher.matchesNothing()) {
                        break;
                    }
                }
            }
            if (argCharSet.matchesSomething()) {
                createSlot();
                targetStateSets[resultLength] = StateSet.create(stateIndex);
                matcherBuilders[resultLength] = argCharSet;
                addTransitionTo(resultLength, argTransition);
                resultLength++;
            }
        }
    }

    private void createSlot() {
        if (transitionLists.length <= resultLength) {
            transitionLists = Arrays.copyOf(transitionLists, resultLength * 2);
            targetStateSets = Arrays.copyOf(targetStateSets, resultLength * 2);
            matcherBuilders = Arrays.copyOf(matcherBuilders, resultLength * 2);
        }
        if (transitionLists[resultLength] == null) {
            transitionLists[resultLength] = new ObjectArrayBuffer<>();
        }
        transitionLists[resultLength].clear();
    }

    private void addTransitionTo(int i, T transition) {
        if (isPrioritySensitive() && leadsToFinalState.get(i)) {
            return;
        }
        if (targetStateSets[i].add(transition.getTarget(forward))) {
            transitionLists[i].add(transition);
            if (isPrioritySensitive() && ((BasicState<?, ?>) transition.getTarget(forward)).hasTransitionToUnAnchoredFinalState(forward)) {
                leadsToFinalState.set(i);
            }
        }
    }

    /**
     * Merges transitions calculated by {@link #calcDisjointTransitions(CompilationBuffer)} if their
     * target state set is equal <strong>and</strong>
     * {@link #canMerge(TransitionBuilder, TransitionBuilder)} returns {@code true}.
     */
    @SuppressWarnings("unchecked")
    private TB[] mergeSameTargets(CompilationBuffer compilationBuffer) {
        ObjectArrayBuffer<TB> resultBuffer1 = compilationBuffer.getObjectBuffer1();
        resultBuffer1.ensureCapacity(resultLength);
        for (int i = 0; i < resultLength; i++) {
            assert matcherBuilders[i].matchesSomething();
            resultBuffer1.add(createTransitionBuilder(transitionLists[i].toArray(createTransitionArray(transitionLists[i].length())), targetStateSets[i], matcherBuilders[i]));
        }
        if (isPrioritySensitive() && leadsToFinalState.isEmpty()) {
            // no transitions were pruned, so no equal transition sets are possible
            return resultBuffer1.toArray(createResultArray(resultBuffer1.length()));
        }
        resultBuffer1.sort((TB o1, TB o2) -> {
            TransitionSet<S, T> t1 = o1.getTransitionSet();
            TransitionSet<S, T> t2 = o2.getTransitionSet();
            int cmp = t1.size() - t2.size();
            if (cmp != 0) {
                return cmp;
            }
            if (isPrioritySensitive()) {
                // Transition sets are equal iff they lead to the same target states in the same
                // order.
                for (int i = 0; i < t1.size(); i++) {
                    cmp = t1.getTransition(i).getTarget(forward).getId() - t2.getTransition(i).getTarget(forward).getId();
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return cmp;
            } else {
                // Transition sets are equal iff they lead to the same set of target states.
                // Here, we abuse the fact that our state set iterators yield states ordered by id.
                Iterator<S> i1 = t1.getTargetStateSet().iterator();
                Iterator<S> i2 = t2.getTargetStateSet().iterator();
                while (i1.hasNext()) {
                    assert i2.hasNext();
                    cmp = i1.next().getId() - i2.next().getId();
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return cmp;
            }
        });
        ObjectArrayBuffer<TB> resultBuffer2 = compilationBuffer.getObjectBuffer2();
        TB last = null;
        for (TB tb : resultBuffer1) {
            if (last != null && canMerge(last, tb)) {
                last.setMatcherBuilder(last.getMatcherBuilder().union(tb.getMatcherBuilder(), compilationBuffer));
            } else {
                resultBuffer2.add(tb);
                last = tb;
            }
        }
        return resultBuffer2.toArray(createResultArray(resultBuffer2.length()));
    }

    protected abstract TB createTransitionBuilder(T[] transitions, StateSet<S> targetStateSet, CodePointSet matcherBuilder);

    /**
     * Returns {@code true} if two DFA transitions are allowed to be merged into one.
     */
    protected abstract boolean canMerge(TB a, TB b);

    protected abstract T[] createTransitionArray(int size);

    /**
     * Returns an array suitable for holding the result of {@link #run(CompilationBuffer)}.
     */
    protected abstract TB[] createResultArray(int size);

}
