/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.charset.ImmutableSortedListOfRanges;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;

import org.graalvm.collections.EconomicMap;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides an algorithm for converting a list of NFA transitions into a set of DFA
 * transitions.
 *
 * @param <TS> represents a set of NFA transitions.
 * @param <TB> represents a DFA transition fragment. This type is used for both intermediate and
 *            final results.
 *
 * @see TransitionSet
 * @see TransitionBuilder
 */
public abstract class StateTransitionCanonicalizer<TS extends TransitionSet, TB extends TransitionBuilder<TS>> {

    private final ArrayList<TB> disjointTransitions = new ArrayList<>();
    private final EconomicMap<TS, TB> mergeSameTargetsMap = EconomicMap.create();

    /**
     * Runs the NFA to DFA transition conversion algorithm. This algorithm has two phases:
     * <ol>
     * <li>Merge NFA transitions according to their expected character sets. The result of this
     * phase is a list of {@link TransitionBuilder}s whose {@link CharSet}s have no more
     * intersections.</li>
     * <li>Merge {@link TransitionBuilder}s generated by the first phase if their <em>target
     * state</em> is equal and
     * {@link #isSameTargetMergeAllowed(TransitionBuilder, TransitionBuilder)} returns {@code true}.
     * </li>
     * </ol>
     *
     * @param transitions The list of NFA transitions to merge. Initially, all
     *            {@link TransitionBuilder}s contained in this list should represent a single NFA
     *            transition.
     * @return a set of transition builders representing the DFA transitions generated from the
     *         given NFA transitions.
     */
    public TB[] run(List<TB> transitions, CompilationBuffer compilationBuffer) {
        calcDisjointTransitions(transitions, compilationBuffer);
        return mergeSameTargets(compilationBuffer);
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
     * {@link TransitionSet#addAll(TransitionSet)}.</li>
     * <li>Otherwise, a new transition containing <em>e.ts</em> and <em>r.ts</em> and the
     * intersection of <em>e.cs</em> and <em>r.cs</em> is added to the result list. This new
     * transition is created using
     * {@link TransitionBuilder#createMerged(TransitionBuilder, CharSet)}. The intersection of
     * <em>e.cs</em> and <em>r.cs</em> is removed from <em>r.cs</em>.</li>
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
     * property is crucial for generating priority-sensitive DFAs with
     * {@link com.oracle.truffle.regex.tregex.dfa.PrioritySensitiveNFATransitionSet}, since that
     * transition set implementation tracks priorities of transitions by <em>insertion order</em>!
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
    private void calcDisjointTransitions(List<TB> transitions, CompilationBuffer compilationBuffer) {
        for (TB e : transitions) {
            for (int i = 0; i < disjointTransitions.size(); i++) {
                TB r = disjointTransitions.get(i);
                ImmutableSortedListOfRanges.IntersectAndSubtractResult<CharSet> result = r.getMatcherBuilder().intersectAndSubtract(e.getMatcherBuilder(), compilationBuffer);
                CharSet rSubtractedMatcher = result.subtractedA;
                CharSet eSubtractedMatcher = result.subtractedB;
                CharSet intersection = result.intersection;
                if (intersection.matchesSomething()) {
                    if (rSubtractedMatcher.matchesNothing()) {
                        r.getTransitionSet().addAll(e.getTransitionSet());
                    } else {
                        r.setMatcherBuilder(rSubtractedMatcher);
                        disjointTransitions.add((TB) r.createMerged(e, intersection));
                    }
                    e.setMatcherBuilder(eSubtractedMatcher);
                    if (eSubtractedMatcher.matchesNothing()) {
                        break;
                    }
                }
            }
            if (e.getMatcherBuilder().matchesSomething()) {
                disjointTransitions.add(e);
            }
        }
    }

    /**
     * Merges transitions calculated by {@link #calcDisjointTransitions(List, CompilationBuffer)} if
     * their target state set is equal <strong>and</strong>
     * {@link #isSameTargetMergeAllowed(TransitionBuilder, TransitionBuilder)} returns {@code true}.
     * Equality of target state sets is checked by {@link TransitionSet#hashCode()} and
     * {@link TransitionSet#equals(Object)}.
     */
    @SuppressWarnings("unchecked")
    private TB[] mergeSameTargets(CompilationBuffer compilationBuffer) {
        int resultSize = 0;
        for (TB tb : disjointTransitions) {
            if (tb.getMatcherBuilder().matchesNothing()) {
                continue;
            }
            TB existingTransitions = mergeSameTargetsMap.get(tb.getTransitionSet());
            if (existingTransitions == null) {
                mergeSameTargetsMap.put(tb.getTransitionSet(), tb);
                resultSize++;
            } else {
                boolean merged = false;
                TB mergeCandidate = existingTransitions;
                do {
                    if (isSameTargetMergeAllowed(tb, mergeCandidate)) {
                        mergeCandidate.setMatcherBuilder(mergeCandidate.getMatcherBuilder().union(tb.getMatcherBuilder(), compilationBuffer));
                        merged = true;
                        break;
                    }
                    mergeCandidate = (TB) mergeCandidate.getNext();
                } while (mergeCandidate != null);
                if (!merged) {
                    tb.setNext(existingTransitions);
                    mergeSameTargetsMap.put(tb.getTransitionSet(), tb);
                    resultSize++;
                }
            }
        }
        TB[] resultArray = createResultArray(resultSize);
        int i = 0;
        for (TB list : mergeSameTargetsMap.getValues()) {
            TB tb = list;
            do {
                resultArray[i++] = tb;
                tb = (TB) tb.getNext();
            } while (tb != null);
        }
        disjointTransitions.clear();
        mergeSameTargetsMap.clear();
        return resultArray;
    }

    /**
     * Returns {@code true} if two DFA transitions leading to the same target state are allowed to
     * be merged into one.
     */
    protected abstract boolean isSameTargetMergeAllowed(TB a, TB b);

    /**
     * Returns an array suitable for holding the result of {@link #run(List, CompilationBuffer)}.
     */
    protected abstract TB[] createResultArray(int size);
}
