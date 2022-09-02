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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import java.util.Arrays;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ShortArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.QuantifierGuard;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Special AST visitor that will find all immediate successors of a given Term when the AST is seen
 * as a NFA, in priority order. A successor can either be a {@link CharacterClass} or a
 * {@link MatchFound} node.
 *
 * <pre>
 * {@code
 * Examples:
 *
 * Successors of "b" in (a|b)c:
 * 1.: "c"
 *
 * Successors of "b" in (a|b)*c:
 * 1.: "a"
 * 2.: "b"
 * 3.: "c"
 * }
 * </pre>
 *
 * For every successor, the visitor will find the full path of AST nodes that have been traversed
 * from the initial node to the successor node, where {@link Group} nodes are treated specially: The
 * path will contain separate entries for <em>entering</em> and <em>leaving</em> a {@link Group},
 * and a special <em>pass-through</em> node for empty sequences of {@link Group}s marked with
 * {@link Group#isExpandedQuantifier()}. Furthermore, the visitor will not descend into lookaround
 * assertions, it will jump over them and just add their corresponding {@link LookAheadAssertion} or
 * {@link LookBehindAssertion} node to the path.
 *
 * <pre>
 * {@code
 * Examples with full path information:
 *
 * Successors of "b" in (a|b)c:
 * 1.: [leave group 1], [CharClass c]
 *
 * Successors of "b" in (a|b)*c:
 * 1.: [leave group 1], [enter group 1], [CharClass a]
 * 2.: [leave group 1], [enter group 1], [CharClass b]
 * 3.: [leave group 1], [CharClass c]
 * }
 * </pre>
 */
public abstract class NFATraversalRegexASTVisitor {

    /**
     * Bailout threshold for the number of successors eliminated by de-duplication so far. This is
     * necessary for expressions with an exponential number of possible paths, like
     * {@code /(a?|b?|c?|d?|e?|f?|g?)(a?|b?|c?|d?|e?|f?|g?)(a?|b?|c?|d?|e?|f?|g?)/}.
     */
    private static final int SUCCESSOR_DEDUPLICATION_BAILOUT_THRESHOLD = 100_000;
    protected final RegexAST ast;
    /**
     * This buffer of long values represents the path of {@link RegexASTNode}s traversed so far.
     * Every path element consists of an ast node id, a "group-action" flag indicating whether we
     * enter, exit, or pass through a group, and a group alternation index, indicating the next
     * alternation we should visit when back-tracking to find the next successor.
     */
    private final LongArrayBuffer curPath = new LongArrayBuffer(8);
    private final StateSet<Group> insideEmptyGuardGroup;
    /**
     * insideLoops is the set of looping groups that we are currently inside of. We need to maintain
     * this in order to detect infinite loops in the NFA traversal. If we enter a looping group,
     * traverse it without encountering a CharacterClass node or a MatchFound node and arrive back
     * at the same group, then we are bound to loop like this forever. Using insideLoops, we can
     * detect this situation and proceed with the search using another alternative. For example, in
     * the RegexAST {@code ((|[a])*|)*}, which corresponds to the regex {@code /(a*?)* /}, we can
     * traverse the inner loop, {@code (|[a])*}, without hitting any CharacterClass node by choosing
     * the first alternative and we will then arrive back at the outer loop. There, we detect an
     * infinite loop, which causes us to backtrack and choose the second alternative in the inner
     * loop, leading us to the CharacterClass node [a].
     */
    private final StateSet<Group> insideLoops;
    private RegexASTNode root;
    private RegexASTNode cur;
    private Set<LookBehindAssertion> traversableLookBehindAssertions;
    private boolean canTraverseCaret = false;
    private boolean forward = true;
    private boolean done = false;

    /**
     * The exhaustive path traversal may result in some duplicate successors, e.g. on a regex like
     * {@code /(a?|b?)(a?|b?)/}. We consider two successors as identical if they go through the same
     * {@link PositionAssertion dollar-assertions} and {@link LookAroundAssertion}s, and their final
     * {@link CharacterClass} / {@link MatchFound} node is the same.
     */
    private final EconomicMap<StateSet<RegexASTNode>, StateSet<RegexASTNode>> targetDeduplicationMap = EconomicMap.create();
    private final StateSet<RegexASTNode> lookAroundsOnPath;
    private final ShortArrayBuffer lookAroundsOnPathOrdered = new ShortArrayBuffer(8);
    private final StateSet<RegexASTNode> dollarsOnPath;
    private final StateSet<RegexASTNode> targetsVisited;
    private final int[] nodeVisitCount;
    private int deduplicatedTargets = 0;

    private final CompilationFinalBitSet captureGroupUpdates;
    private final CompilationFinalBitSet captureGroupClears;

    private final ObjectArrayBuffer<QuantifierGuard> quantifierGuards = new ObjectArrayBuffer<>();

    protected NFATraversalRegexASTVisitor(RegexAST ast) {
        this.ast = ast;
        this.insideEmptyGuardGroup = StateSet.create(ast);
        this.insideLoops = StateSet.create(ast);
        this.targetsVisited = StateSet.create(ast);
        this.lookAroundsOnPath = StateSet.create(ast);
        this.dollarsOnPath = StateSet.create(ast);
        this.nodeVisitCount = new int[ast.getNumberOfStates()];
        this.captureGroupUpdates = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.captureGroupClears = new CompilationFinalBitSet(ast.getNumberOfCaptureGroups() * 2);
    }

    public Set<LookBehindAssertion> getTraversableLookBehindAssertions() {
        return traversableLookBehindAssertions;
    }

    public void setTraversableLookBehindAssertions(Set<LookBehindAssertion> traversableLookBehindAssertions) {
        this.traversableLookBehindAssertions = traversableLookBehindAssertions;
    }

    public boolean canTraverseCaret() {
        return canTraverseCaret;
    }

    public void setCanTraverseCaret(boolean canTraverseCaret) {
        this.canTraverseCaret = canTraverseCaret;
    }

    public void setReverse() {
        this.forward = false;
    }

    protected abstract boolean canTraverseLookArounds();

    protected void run(Term runRoot) {
        assert insideEmptyGuardGroup.isEmpty();
        assert insideLoops.isEmpty();
        assert curPath.isEmpty();
        assert dollarsOnPath.isEmpty();
        assert lookAroundsOnPath.isEmpty();
        assert lookAroundsOnPathOrdered.isEmpty();
        assert nodeVisitsEmpty() : Arrays.toString(nodeVisitCount);
        targetsVisited.clear();
        targetDeduplicationMap.clear();
        deduplicatedTargets = 0;
        root = runRoot;
        if (runRoot instanceof Group) {
            cur = runRoot;
        } else {
            advanceTerm(runRoot);
        }
        while (!done) {
            while (doAdvance()) {
                // advance until we reach the next node to visit
            }
            if (done) {
                break;
            }
            RegexASTNode target = pathGetNode(curPath.peek());
            // TODO: if target instance LookAroundAssertion, advance further until a non-empty-match
            // target is found, and only then visit the target
            visit(target);
            if (target instanceof MatchFound && !dollarsOnPath() && lookAroundsOnPath.isEmpty() && !hasQuantifierGuards()) {
                /*
                 * Transitions after an unconditional final state transition will never be taken, so
                 * it is safe to prune them.
                 */
                insideEmptyGuardGroup.clear();
                insideLoops.clear();
                curPath.clear();
                /*
                 * no need to clear nodeVisitedCount here, because !dollarsOnPath() &&
                 * lookAroundsOnPath.isEmpty() implies nodeVisitsEmpty()
                 */
                break;
            }
            retreat();
        }
        done = false;
    }

    /**
     * Visit the next successor found.
     */
    protected abstract void visit(RegexASTNode target);

    protected abstract void enterLookAhead(LookAheadAssertion assertion);

    protected abstract void leaveLookAhead(LookAheadAssertion assertion);

    protected boolean caretsOnPath() {
        for (int i = 0; i < curPath.length(); i++) {
            RegexASTNode node = pathGetNode(curPath.get(i));
            if (node instanceof PositionAssertion && ((PositionAssertion) node).type == PositionAssertion.Type.CARET) {
                return true;
            }
        }
        return false;
    }

    protected boolean dollarsOnPath() {
        return !dollarsOnPath.isEmpty();
    }

    protected QuantifierGuard[] getQuantifierGuardsOnPath() {
        quantifierGuards.clear();
        if (root instanceof QuantifiableTerm && !(root instanceof Group) && ((QuantifiableTerm) root).hasQuantifier()) {
            addQuantifierGuard(((QuantifiableTerm) root).getQuantifier(), false);
        }
        for (int i = 0; i < curPath.length(); i++) {
            long element = curPath.get(i);
            if (pathIsGroupPassThrough(element)) {
                continue;
            }
            if (pathIsGroup(element)) {
                Group group = (Group) pathGetNode(element);
                if (group.hasQuantifier()) {
                    addQuantifierGuard(group.getQuantifier(), pathIsGroupEnter(element));
                }
            }
        }
        RegexASTNode target = pathGetNode(curPath.peek());
        if (target instanceof QuantifiableTerm && ((QuantifiableTerm) target).hasQuantifier()) {
            addQuantifierGuard(((QuantifiableTerm) target).getQuantifier(), true);
        }
        return quantifierGuards.toArray(QuantifierGuard.NO_GUARDS);
    }

    private void addQuantifierGuard(Token.Quantifier quantifier, boolean enter) {
        if (quantifier.hasIndex()) {
            quantifierGuards.add(QuantifierGuard.create(quantifier, enter));
        }
        if (quantifier.hasZeroWidthIndex()) {
            quantifierGuards.add(QuantifierGuard.createZeroWidth(quantifier, enter));
        }
    }

    protected Iterable<Integer> getLookAroundsOnPath() {
        return lookAroundsOnPathOrdered;
    }

    protected boolean hasQuantifierGuards() {
        return false;
    }

    protected PositionAssertion getLastDollarOnPath() {
        assert dollarsOnPath();
        for (int i = curPath.length() - 1; i >= 0; i--) {
            long element = curPath.get(i);
            if (pathGetNode(element) instanceof PositionAssertion && ((PositionAssertion) pathGetNode(element)).type == PositionAssertion.Type.DOLLAR) {
                return (PositionAssertion) pathGetNode(element);
            }
        }
        throw new IllegalStateException();
    }

    protected GroupBoundaries getGroupBoundaries() {
        captureGroupUpdates.clear();
        captureGroupClears.clear();
        for (int i = 0; i < curPath.length(); i++) {
            long element = curPath.get(i);
            if (pathIsGroupPassThrough(element)) {
                continue;
            }
            if (pathIsGroup(element)) {
                Group group = (Group) pathGetNode(element);
                if (group.isCapturing()) {
                    captureGroupUpdates.set(pathIsGroupEnter(element) ? group.getBoundaryIndexStart() : group.getBoundaryIndexEnd());
                }
                assert !group.isLoop() || group.isExpandedQuantifier();
                if (group.isExpandedQuantifier() && group.hasEnclosedCaptureGroups() && pathIsGroupEnter(element)) {
                    captureGroupClears.setRange(Group.groupNumberToBoundaryIndexStart(group.getEnclosedCaptureGroupsLow()),
                                    Group.groupNumberToBoundaryIndexEnd(group.getEnclosedCaptureGroupsHigh() - 1));
                }
            }
        }
        captureGroupClears.subtract(captureGroupUpdates);
        return ast.createGroupBoundaries(captureGroupUpdates, captureGroupClears);
    }

    private boolean doAdvance() {
        if (cur.isDead() || insideLoops.contains(cur)) {
            return retreat();
        }
        if (cur instanceof Sequence) {
            final Sequence sequence = (Sequence) cur;
            if (sequence.isEmpty()) {
                Group parent = sequence.getParent();
                if (parent.isLoop()) {
                    insideLoops.remove(parent);
                }
                if (sequence.isExpandedQuantifier()) {
                    long lastElement = curPath.pop();
                    assert pathGetNode(lastElement) == parent && pathIsGroupEnter(lastElement);
                    curPath.add(pathSwitchEnterAndPassThrough(lastElement));
                } else {
                    pushGroupExit(parent);
                }
                return advanceTerm(parent);
            } else {
                cur = forward ? sequence.getFirstTerm() : sequence.getLastTerm();
                return true;
            }
        } else if (cur instanceof Group) {
            final Group group = (Group) cur;
            curPath.add(createGroupEnterPathElement(group));
            if (group.hasEmptyGuard()) {
                insideEmptyGuardGroup.add(group);
            }
            if (group.isLoop()) {
                insideLoops.add(group);
            }
            // This path will only be hit when visiting a group for the first time. All groups
            // must have at least one child sequence, so no check is needed here.
            // createGroupEnterPathElement initializes the group alternation index with 1, so we
            // don't have to increment it here, either.
            cur = group.getFirstAlternative();
            return true;
        } else {
            curPath.add(createPathElement(cur));
            if (cur instanceof PositionAssertion) {
                final PositionAssertion assertion = (PositionAssertion) cur;
                switch (assertion.type) {
                    case CARET:
                        if (canTraverseCaret) {
                            return advanceTerm(assertion);
                        } else {
                            return retreat();
                        }
                    case DOLLAR:
                        addToVisitedSet(dollarsOnPath);
                        return advanceTerm((Term) cur);
                    default:
                        throw new IllegalStateException();
                }
            } else if (canTraverseLookArounds() && cur instanceof LookAheadAssertion) {
                enterLookAhead((LookAheadAssertion) cur);
                putLookAroundOnPath();
                return advanceTerm((Term) cur);
            } else if (canTraverseLookArounds() && cur instanceof LookBehindAssertion) {
                putLookAroundOnPath();
                if (traversableLookBehindAssertions == null || traversableLookBehindAssertions.contains(cur)) {
                    return advanceTerm((LookBehindAssertion) cur);
                } else {
                    return retreat();
                }
            } else {
                assert cur instanceof CharacterClass || cur instanceof BackReference || cur instanceof MatchFound || (!canTraverseLookArounds() && cur instanceof LookAroundAssertion);
                if (forward && dollarsOnPath() && cur instanceof CharacterClass) {
                    // don't visit CharacterClass nodes if we traversed dollar - PositionAssertions
                    // already
                    return retreat();
                }
                return deduplicateTarget();
            }
        }
    }

    public void putLookAroundOnPath() {
        addToVisitedSet(lookAroundsOnPath);
        lookAroundsOnPathOrdered.add(((LookAroundAssertion) cur).getSubTreeId());
    }

    private void addToVisitedSet(StateSet<RegexASTNode> visitedSet) {
        nodeVisitCount[cur.getId()]++;
        visitedSet.add(cur);
    }

    private boolean advanceTerm(Term term) {
        if (ast.isNFAInitialState(term) || (term.getParent() instanceof RegexASTSubtreeRootNode && (term instanceof PositionAssertion || term instanceof MatchFound))) {
            assert term instanceof PositionAssertion || term instanceof MatchFound;
            if (term instanceof PositionAssertion) {
                cur = ((PositionAssertion) term).getNext();
            } else {
                cur = ((MatchFound) term).getNext();
            }
            return true;
        }
        Term curTerm = term;
        while (!(curTerm.getParent() instanceof RegexASTSubtreeRootNode)) {
            // We are leaving curTerm. If curTerm has an empty guard and we have already entered
            // curTerm during this step, then we stop and retreat. Otherwise, we would end up
            // letting curTerm match the empty string, which is what the empty guard is meant to
            // forbid.
            if (curTerm.hasEmptyGuard() && insideEmptyGuardGroup.contains(curTerm)) {
                return retreat();
            }
            Sequence parentSeq = (Sequence) curTerm.getParent();
            if (curTerm == (forward ? parentSeq.getLastTerm() : parentSeq.getFirstTerm())) {
                final Group parentGroup = parentSeq.getParent();
                pushGroupExit(parentGroup);
                if (parentGroup.isLoop()) {
                    cur = parentGroup;
                    return true;
                }
                curTerm = parentGroup;
            } else {
                cur = parentSeq.getTerms().get(curTerm.getSeqIndex() + (forward ? 1 : -1));
                return true;
            }
        }
        assert curTerm instanceof Group;
        assert curTerm.getParent() instanceof RegexASTSubtreeRootNode;
        if (curTerm.hasEmptyGuard() && insideEmptyGuardGroup.contains(curTerm)) {
            return retreat();
        }
        cur = curTerm.getSubTreeParent().getMatchFound();
        return true;
    }

    private void pushGroupExit(Group group) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_EXIT);
    }

    private boolean retreat() {
        while (!curPath.isEmpty()) {
            long lastVisited = curPath.pop();
            RegexASTNode node = pathGetNode(lastVisited);
            if (pathIsGroup(lastVisited)) {
                Group group = (Group) node;
                if (!pathIsGroupExit(lastVisited)) {
                    if (pathGroupHasNext(lastVisited)) {
                        cur = pathGroupGetNext(lastVisited);
                        curPath.add(pathToGroupEnter(pathIncGroupAltIndex(lastVisited)));
                        return true;
                    } else {
                        assert noEmptyGuardEnterOnPath(group);
                        assert !group.hasEmptyGuard() || insideEmptyGuardGroup.contains(group);
                        insideEmptyGuardGroup.remove(group);
                        if (group.isLoop()) {
                            insideLoops.remove(group);
                        }
                    }
                }
            } else {
                if (canTraverseLookArounds() && node instanceof LookAroundAssertion) {
                    if (node instanceof LookAheadAssertion) {
                        leaveLookAhead((LookAheadAssertion) node);
                    }
                    removeFromVisitedSet(lastVisited, lookAroundsOnPath);
                    lookAroundsOnPathOrdered.pop();
                } else if (node instanceof PositionAssertion && ((PositionAssertion) node).type == PositionAssertion.Type.DOLLAR) {
                    removeFromVisitedSet(lastVisited, dollarsOnPath);
                }
            }
        }
        done = true;
        return false;
    }

    private void removeFromVisitedSet(long pathElement, StateSet<RegexASTNode> visitedSet) {
        if (--nodeVisitCount[pathGetNodeId(pathElement)] == 0) {
            visitedSet.remove(pathGetNode(pathElement));
        }
    }

    private boolean deduplicateTarget() {
        boolean isDuplicate = false;
        if (dollarsOnPath.isEmpty() && lookAroundsOnPath.isEmpty()) {
            isDuplicate = !targetsVisited.add(cur);
        } else if (canTraverseLookArounds()) {
            StateSet<RegexASTNode> key = lookAroundsOnPath.copy();
            key.addAll(dollarsOnPath);
            key.add(cur);
            isDuplicate = targetDeduplicationMap.put(key, key) != null;
        } else {
            StateSet<RegexASTNode> key = dollarsOnPath.copy();
            key.add(cur);
            isDuplicate = targetDeduplicationMap.put(key, key) != null;
        }
        if (isDuplicate) {
            if (++deduplicatedTargets > SUCCESSOR_DEDUPLICATION_BAILOUT_THRESHOLD) {
                throw new UnsupportedRegexException("NFATraversal explosion");
            }
            return retreat();
        }
        return false;
    }

    /**
     * First field: group alternation index. This value is used to iterate the alternations of
     * groups referenced in a group-enter path element. <br>
     * Since the same group can appear multiple times on the path, we cannot reuse {@link Group}'s
     * implementation of {@link RegexASTVisitorIterable}. Therefore, every occurrence of a group on
     * the path has its own index for iterating and back-tracking over its alternatives. <br>
     * This field's offset <em>must</em> be zero for {@link #pathIncGroupAltIndex(long)} to work!
     */
    private static final int PATH_GROUP_ALT_INDEX_OFFSET = 0;
    /**
     * Second field: id of the path element's {@link RegexASTNode}.
     */
    private static final int PATH_NODE_OFFSET = Short.SIZE;
    /**
     * Third field: group action. Every path element referencing a group must have one of three
     * possible group actions:
     * <ul>
     * <li>group enter</li>
     * <li>group exit</li>
     * <li>group pass through</li>
     * </ul>
     */
    private static final int PATH_GROUP_ACTION_OFFSET = Short.SIZE * 2;
    private static final long PATH_GROUP_ACTION_ENTER = 1L << PATH_GROUP_ACTION_OFFSET;
    private static final long PATH_GROUP_ACTION_EXIT = 1L << PATH_GROUP_ACTION_OFFSET + 1;
    private static final long PATH_GROUP_ACTION_PASS_THROUGH = 1L << PATH_GROUP_ACTION_OFFSET + 2;
    private static final long PATH_GROUP_ACTION_ENTER_OR_PASS_THROUGH = PATH_GROUP_ACTION_ENTER | PATH_GROUP_ACTION_PASS_THROUGH;
    private static final long PATH_GROUP_ACTION_TO_ENTER_MASK = 0xffff0000ffffffffL | PATH_GROUP_ACTION_ENTER;
    private static final long PATH_GROUP_ACTION_ANY = PATH_GROUP_ACTION_ENTER | PATH_GROUP_ACTION_EXIT | PATH_GROUP_ACTION_PASS_THROUGH;

    /**
     * Create a new path element containing the given node.
     */
    private static long createPathElement(RegexASTNode node) {
        return (long) node.getId() << PATH_NODE_OFFSET;
    }

    /**
     * Create a group-enter path element for the given Group. The group alternation index is
     * initialized with 1!
     */
    private static long createGroupEnterPathElement(Group node) {
        return ((long) node.getId() << PATH_NODE_OFFSET) | (1 << PATH_GROUP_ALT_INDEX_OFFSET) | PATH_GROUP_ACTION_ENTER;
    }

    private static int pathGetNodeId(long pathElement) {
        return (short) (pathElement >>> PATH_NODE_OFFSET);
    }

    /**
     * Get the {@link RegexASTNode} contained in the given path element.
     */
    private RegexASTNode pathGetNode(long pathElement) {
        return ast.getState(pathGetNodeId(pathElement));
    }

    /**
     * Get the group alternation index of the given path element.
     */
    private static int pathGetGroupAltIndex(long pathElement) {
        return (short) (pathElement >>> PATH_GROUP_ALT_INDEX_OFFSET);
    }

    /**
     * Convert the given path element to a group-enter.
     */
    private static long pathToGroupEnter(long pathElement) {
        return pathElement & PATH_GROUP_ACTION_TO_ENTER_MASK;
    }

    /**
     * Returns {@code true} if the given path element has any group action set. Every path element
     * containing a group must have one group action.
     */
    private static boolean pathIsGroup(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_ANY) != 0;
    }

    private static boolean pathIsGroupEnter(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_ENTER) != 0;
    }

    private static boolean pathIsGroupExit(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_EXIT) != 0;
    }

    private static boolean pathIsGroupPassThrough(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_PASS_THROUGH) != 0;
    }

    /**
     * Convert a group enter path element to a group pass-through, and vice versa.
     */
    private static long pathSwitchEnterAndPassThrough(long pathElement) {
        assert (pathIsGroupEnter(pathElement) != pathIsGroupPassThrough(pathElement));
        return pathElement ^ PATH_GROUP_ACTION_ENTER_OR_PASS_THROUGH;
    }

    /**
     * Returns {@code true} if the path element's group alternation index is still in bounds.
     */
    private boolean pathGroupHasNext(long pathElement) {
        return pathGetGroupAltIndex(pathElement) < ((Group) pathGetNode(pathElement)).size();
    }

    /**
     * Returns the next alternative of the group contained in this path element. Does not increment
     * the group alternation index!
     */
    private Sequence pathGroupGetNext(long pathElement) {
        return ((Group) pathGetNode(pathElement)).getAlternatives().get(pathGetGroupAltIndex(pathElement));
    }

    /**
     * Increment the group alternation index. This requires {@link #PATH_GROUP_ALT_INDEX_OFFSET} to
     * be 0!
     */
    private static long pathIncGroupAltIndex(long pathElement) {
        return pathElement + 1;
    }

    private boolean noEmptyGuardEnterOnPath(Group group) {
        if (!group.hasEmptyGuard()) {
            return true;
        }
        for (int i = 0; i < curPath.length(); i++) {
            if (pathGetNode(curPath.get(i)) == group && pathIsGroupEnter(curPath.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean nodeVisitsEmpty() {
        for (int i : nodeVisitCount) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unused")
    private void dumpPath() {
        for (int i = 0; i < curPath.length(); i++) {
            long element = curPath.get(i);
            if (pathIsGroup(element)) {
                Group group = (Group) pathGetNode(element);
                if (pathIsGroupEnter(element)) {
                    System.out.println(String.format("ENTER (%d)   %s", pathGetGroupAltIndex(element), group));
                } else if (pathIsGroupExit(element)) {
                    System.out.println(String.format("EXIT        %s", group));
                } else {
                    System.out.println(String.format("PASSTHROUGH %s", group));
                }
            } else {
                System.out.println(String.format("NODE        %s", pathGetNode(element)));
            }
        }
    }
}
