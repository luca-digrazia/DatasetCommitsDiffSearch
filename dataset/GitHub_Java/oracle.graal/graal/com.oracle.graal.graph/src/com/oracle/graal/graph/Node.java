/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import static com.oracle.graal.graph.Graph.*;

import java.lang.annotation.*;
import java.util.*;

import com.oracle.graal.graph.Graph.NodeChangedListener;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;

/**
 * This class is the base class for all nodes, it represent a node which can be inserted in a
 * {@link Graph}.
 * <p>
 * Once a node has been added to a graph, it has a graph-unique {@link #id()}. Edges in the
 * subclasses are represented with annotated fields. There are two kind of edges : {@link Input} and
 * {@link Successor}. If a field, of a type compatible with {@link Node}, annotated with either
 * {@link Input} and {@link Successor} is not null, then there is an edge from this node to the node
 * this field points to.
 * <p>
 * Nodes which are be value numberable should implement the {@link ValueNumberable} interface.
 * 
 * <h1>Assertions and Verification</h1>
 * 
 * The Node class supplies the {@link #assertTrue(boolean, String, Object...)} and
 * {@link #assertFalse(boolean, String, Object...)} methods, which will check the supplied boolean
 * and throw a VerificationError if it has the wrong value. Both methods will always either throw an
 * exception or return true. They can thus be used within an assert statement, so that the check is
 * only performed if assertions are enabled.
 */
public abstract class Node implements Cloneable, Formattable {

    static final int DELETED_ID_START = -1000000000;
    static final int INITIAL_ID = -1;
    static final int ALIVE_ID_START = 0;

    /**
     * Denotes a node input. This should be applied to exactly the fields of a node that are of type
     * {@link Node}. Nodes that update their inputs outside of their constructor should call
     * {@link Node#updateUsages(Node, Node)} just prior to doing the update of the input.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Input {

        boolean notDataflow() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Successor {
    }

    /**
     * Denotes that a parameter of an {@linkplain NodeIntrinsic intrinsic} method must be a compile
     * time constant at all call sites to the intrinsic method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public static @interface ConstantNodeParameter {
    }

    /**
     * Denotes an injected parameter in a {@linkplain NodeIntrinsic node intrinsic} constructor. If
     * the constructor is called as part of node intrinsification, the node intrinsifier will inject
     * an argument for the annotated parameter. Injected parameters must precede all non-injected
     * parameters in a constructor.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public static @interface InjectedNodeParameter {
    }

    /**
     * Annotates a method that can be replaced by a compiler intrinsic. A (resolved) call to the
     * annotated method can be replaced with an instance of the node class denoted by
     * {@link #value()}. For this reason, the signature of the annotated method must match the
     * signature (excluding a prefix of {@linkplain InjectedNodeParameter injected} parameters) of a
     * constructor in the node class.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface NodeIntrinsic {

        /**
         * Gets the {@link Node} subclass instantiated when intrinsifying a call to the annotated
         * method. If not specified, then the class in which the annotated method is declared is
         * used (and is assumed to be a {@link Node} subclass).
         */
        Class value() default NodeIntrinsic.class;

        /**
         * Determines if the stamp of the instantiated intrinsic node has its stamp set from the
         * return type of the annotated method.
         * <p>
         * When it is set to true, the stamp that is passed in to the constructor of ValueNode is
         * ignored and can therefore safely be {@code null}.
         */
        boolean setStampFromReturnType() default false;
    }

    public interface ValueNumberable {
    }

    private Graph graph;
    int id;

    // this next pointer is used in Graph to implement fast iteration over NodeClass types, it
    // therefore points to the next Node of the same type.
    Node typeCacheNext;

    private static final int INLINE_USAGE_COUNT = 2;
    private static final Node[] NO_NODES = {};

    /**
     * Head of usage list. The elements of the usage list in order are {@link #usage0},
     * {@link #usage1} and {@link #extraUsages}. The first null entry terminates the list.
     */
    private Node usage0;
    private Node usage1;
    private Node[] extraUsages = NO_NODES;

    private Node predecessor;

    public Node() {
        this.graph = null;
        this.id = INITIAL_ID;
    }

    int id() {
        return id;
    }

    /**
     * Gets the graph context of this node. This must not be called for {@linkplain #isExternal()
     * external} nodes.
     */
    public Graph graph() {
        assert !isExternal() : "external node has no graph: " + this;
        return graph;
    }

    /**
     * Returns an {@link NodeClassIterable iterable} which can be used to traverse all non-null
     * input edges of this node.
     * 
     * @return an {@link NodeClassIterable iterable} for all non-null input edges.
     */
    public NodeClassIterable inputs() {
        return getNodeClass().getInputIterable(this);
    }

    /**
     * Returns an {@link NodeClassIterable iterable} which can be used to traverse all non-null
     * successor edges of this node.
     * 
     * @return an {@link NodeClassIterable iterable} for all non-null successor edges.
     */
    public NodeClassIterable successors() {
        return getNodeClass().getSuccessorIterable(this);
    }

    class NodeUsageIterator implements Iterator<Node> {

        private final int expectedModCount = usageModCount();
        int index = -1;
        Node current;

        private void advance() {
            assert index == -1 || current != null;
            current = null;
            index++;
            if (index == 0) {
                current = usage0;
            } else if (index == 1) {
                current = usage1;
            } else {
                if (index - INLINE_USAGE_COUNT < extraUsages.length) {
                    current = extraUsages[index - INLINE_USAGE_COUNT];
                }
            }
        }

        public NodeUsageIterator() {
            advance();
        }

        public boolean hasNext() {
            assert expectedModCount == usageModCount();
            return current != null;
        }

        public Node next() {
            assert expectedModCount == usageModCount();
            Node result = current;
            if (result == null) {
                throw new NoSuchElementException();
            }
            advance();
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    class NodeUsageIterable extends AbstractNodeIterable<Node> {

        public NodeUsageIterator iterator() {
            return new NodeUsageIterator();
        }

        @Override
        public boolean isEmpty() {
            return usage0 == null;
        }

        @Override
        public boolean isNotEmpty() {
            return usage0 != null;
        }

        @Override
        public int count() {
            if (usage0 == null) {
                return 0;
            }
            if (usage1 == null) {
                return 1;
            }
            return 2 + indexOfLastNonNull(extraUsages) + 1;
        }
    }

    int getUsageCountUpperBound() {
        assert recordsUsages();
        if (usage0 == null) {
            return 0;
        }
        if (usage1 == null) {
            return 1;
        }
        return 2 + extraUsages.length;
    }

    /**
     * Gets the list of nodes that use this node (i.e., as an input).
     */
    public final NodeIterable<Node> usages() {
        assert recordsUsages() : this;
        return new NodeUsageIterable();
    }

    /**
     * Determines if this node records its usages (i.e. the nodes for which it is an input). All
     * methods in {@link Node} that pertain to querying or updating usage information must not be
     * called for a {@link Node} instance that returns false for this method.
     */
    public boolean recordsUsages() {
        return true;
    }

    /**
     * Determines if this node has a {@linkplain #graph() graph} context or is external to any
     * graph. The {@link #graph()} method must only be called on nodes for which this method returns
     * true.
     */
    public boolean isExternal() {
        return false;
    }

    /**
     * Finds the index of the last non-null entry in a node array. The search assumes that all
     * non-null entries precede the first null entry in the array.
     * 
     * @param nodes the array to search
     * @return the index of the last non-null entry in {@code nodes} if it exists, else -1
     */
    private static int indexOfLastNonNull(Node[] nodes) {
        if (nodes.length == 0 || nodes[0] == null) {
            return -1;
        }
        if (nodes[nodes.length - 1] != null) {
            return nodes.length - 1;
        }

        // binary search
        int low = 0;
        int high = nodes.length - 1;
        while (true) {
            int mid = (low + high) >>> 1;
            if (nodes[mid] == null) {
                if (nodes[mid - 1] != null) {
                    return mid - 1;
                }
                high = mid - 1;
            } else {
                if (mid == nodes.length - 1 || nodes[mid + 1] == null) {
                    return mid;
                }
                low = mid + 1;
            }
        }
    }

    /**
     * Adds a given node to this node's {@linkplain #usages() usages}.
     * 
     * @param node the node to add
     */
    private void addUsage(Node node) {
        assert recordsUsages();
        incUsageModCount();
        if (usage0 == null) {
            usage0 = node;
        } else if (usage1 == null) {
            usage1 = node;
        } else {
            int length = extraUsages.length;
            if (length == 0) {
                extraUsages = new Node[4];
                extraUsages[0] = node;
            } else {
                int lastNonNull = indexOfLastNonNull(extraUsages);
                if (lastNonNull == length - 1) {
                    extraUsages = Arrays.copyOf(extraUsages, length * 2 + 1);
                    extraUsages[length] = node;
                } else if (lastNonNull == -1) {
                    extraUsages[0] = node;
                } else {
                    extraUsages[lastNonNull + 1] = node;
                }
            }
        }
    }

    /**
     * Removes a given node from this node's {@linkplain #usages() usages}.
     * 
     * @param node the node to remove
     * @return whether or not {@code usage} was in the usage list
     */
    private boolean removeUsage(Node node) {
        assert recordsUsages();
        // It is critical that this method maintains the invariant that
        // the usage list has no null element preceding a non-null element
        incUsageModCount();
        if (usage0 == node) {
            if (usage1 != null) {
                int lastNonNull = indexOfLastNonNull(extraUsages);
                if (lastNonNull >= 0) {
                    usage0 = extraUsages[lastNonNull];
                    extraUsages[lastNonNull] = null;
                } else {
                    // usage1 is the last element
                    usage0 = usage1;
                    usage1 = null;
                }
            } else {
                // usage0 is the last element
                usage0 = null;
            }
            return true;
        }
        if (usage1 == node) {
            int lastNonNull = indexOfLastNonNull(extraUsages);
            if (lastNonNull >= 0) {
                usage1 = extraUsages[lastNonNull];
                extraUsages[lastNonNull] = null;
            } else {
                // usage1 is the last element
                usage1 = null;
            }
            return true;
        }
        int lastNonNull = indexOfLastNonNull(extraUsages);
        if (lastNonNull >= 0) {
            for (int i = 0; i <= lastNonNull; ++i) {
                Node n = extraUsages[i];
                if (n == node) {
                    if (i < lastNonNull) {
                        extraUsages[i] = extraUsages[lastNonNull];
                        extraUsages[lastNonNull] = null;
                    } else {
                        extraUsages[i] = null;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void clearUsages() {
        assert recordsUsages();
        incUsageModCount();
        usage0 = null;
        usage1 = null;
        extraUsages = NO_NODES;
    }

    public final Node predecessor() {
        return predecessor;
    }

    final int modCount() {
        if (MODIFICATION_COUNTS_ENABLED && graph != null) {
            return graph.modCount(this);
        }
        return 0;
    }

    final void incModCount() {
        if (MODIFICATION_COUNTS_ENABLED && graph != null) {
            graph.incModCount(this);
        }
    }

    final int usageModCount() {
        if (MODIFICATION_COUNTS_ENABLED && graph != null) {
            return graph.usageModCount(this);
        }
        return 0;
    }

    final void incUsageModCount() {
        if (MODIFICATION_COUNTS_ENABLED && graph != null) {
            graph.incUsageModCount(this);
        }
    }

    public boolean isDeleted() {
        return id <= DELETED_ID_START;
    }

    public boolean isAlive() {
        return id >= ALIVE_ID_START;
    }

    /**
     * Updates the usages sets of the given nodes after an input slot is changed from oldInput to
     * newInput: removes this node from oldInput's usages and adds this node to newInput's usages.
     */
    protected void updateUsages(Node oldInput, Node newInput) {
        if (oldInput != newInput) {
            if (oldInput != null) {
                if (oldInput.recordsUsages()) {
                    boolean result = removeThisFromUsages(oldInput);
                    assert assertTrue(result, "not found in usages, old input: %s", oldInput);
                }
            }
            if (newInput != null) {
                if (newInput.recordsUsages()) {
                    NodeChangedListener listener = graph.inputChangedListener;
                    if (listener != null) {
                        listener.nodeChanged(this);
                    }
                    newInput.addUsage(this);
                }
            } else if (oldInput != null && oldInput.recordsUsages() && oldInput.usages().isEmpty()) {
                NodeChangedListener listener = graph.usagesDroppedToZeroListener;
                if (listener != null) {
                    listener.nodeChanged(oldInput);
                }
            }
        }
    }

    /**
     * Updates the predecessor of the given nodes after a successor slot is changed from
     * oldSuccessor to newSuccessor: removes this node from oldSuccessor's predecessors and adds
     * this node to newSuccessor's predecessors.
     */
    protected void updatePredecessor(Node oldSuccessor, Node newSuccessor) {
        if (oldSuccessor != newSuccessor) {
            if (oldSuccessor != null) {
                assert assertTrue(oldSuccessor.predecessor == this, "wrong predecessor in old successor (%s): %s", oldSuccessor, oldSuccessor.predecessor);
                oldSuccessor.predecessor = null;
            }
            if (newSuccessor != null) {
                assert assertTrue(newSuccessor.predecessor == null, "unexpected non-null predecessor in new successor (%s): %s, this=%s", newSuccessor, newSuccessor.predecessor, this);
                newSuccessor.predecessor = this;
            }
        }
    }

    void initialize(Graph newGraph) {
        assert assertTrue(id == INITIAL_ID, "unexpected id: %d", id);
        this.graph = newGraph;
        newGraph.register(this);
        for (Node input : inputs()) {
            updateUsages(null, input);
        }
        for (Node successor : successors()) {
            updatePredecessor(null, successor);
        }
    }

    public final NodeClass getNodeClass() {
        return NodeClass.get(getClass());
    }

    private boolean checkReplaceWith(Node other) {
        assert assertFalse(other == this, "cannot replace a node with itself");
        assert assertFalse(isDeleted(), "cannot replace deleted node");
        assert assertTrue(other == null || !other.isDeleted(), "cannot replace with deleted node %s", other);
        assert assertTrue(other == null || other.isExternal() || other.graph() == graph, "cannot replace with node in different graph: %s", other == null || other.isExternal() ? null : other.graph());
        return true;
    }

    public void replaceAtUsages(Node other) {
        assert checkReplaceWith(other);
        for (Node usage : usages()) {
            boolean result = usage.getNodeClass().replaceFirstInput(usage, this, other);
            assert assertTrue(result, "not found in inputs, usage: %s", usage);
            if (other != null) {
                NodeChangedListener listener = graph.inputChangedListener;
                if (listener != null) {
                    listener.nodeChanged(usage);
                }
                if (other.recordsUsages()) {
                    other.addUsage(usage);
                }
            }
        }
        clearUsages();
    }

    public void replaceAtPredecessor(Node other) {
        assert checkReplaceWith(other);
        if (predecessor != null) {
            boolean result = predecessor.getNodeClass().replaceFirstSuccessor(predecessor, this, other);
            assert assertTrue(result, "not found in successors, predecessor: %s", predecessor);
            predecessor.updatePredecessor(this, other);
        }
    }

    public void replaceAndDelete(Node other) {
        assert checkReplaceWith(other);
        if (other != null) {
            clearSuccessors();
            replaceAtUsages(other);
            replaceAtPredecessor(other);
        }
        safeDelete();
    }

    public void replaceFirstSuccessor(Node oldSuccessor, Node newSuccessor) {
        if (getNodeClass().replaceFirstSuccessor(this, oldSuccessor, newSuccessor)) {
            updatePredecessor(oldSuccessor, newSuccessor);
        }
    }

    public void replaceFirstInput(Node oldInput, Node newInput) {
        if (getNodeClass().replaceFirstInput(this, oldInput, newInput)) {
            updateUsages(oldInput, newInput);
        }
    }

    public void clearInputs() {
        assert assertFalse(isDeleted(), "cannot clear inputs of deleted node");

        for (Node input : inputs()) {
            if (input.recordsUsages() && !input.isExternal()) {
                removeThisFromUsages(input);
                if (input.usages().isEmpty()) {
                    NodeChangedListener listener = graph.usagesDroppedToZeroListener;
                    if (listener != null) {
                        listener.nodeChanged(input);
                    }
                }
            }
        }
        getNodeClass().clearInputs(this);
    }

    private boolean removeThisFromUsages(Node n) {
        return n.removeUsage(this);
    }

    public void clearSuccessors() {
        assert assertFalse(isDeleted(), "cannot clear successors of deleted node");

        for (Node successor : successors()) {
            assert assertTrue(successor.predecessor == this, "wrong predecessor in old successor (%s): %s", successor, successor.predecessor);
            successor.predecessor = null;
        }
        getNodeClass().clearSuccessors(this);
    }

    private boolean checkDeletion() {
        if (recordsUsages()) {
            assertTrue(usages().isEmpty(), "cannot delete node %s because of usages: %s", this, usages());
        }
        assertTrue(predecessor == null, "cannot delete node %s because of predecessor: %s", this, predecessor);
        return true;
    }

    /**
     * Removes this node from its graph. This node must have no {@linkplain Node#usages() usages}
     * and no {@linkplain #predecessor() predecessor}.
     */
    public void safeDelete() {
        assert checkDeletion();
        clearInputs();
        clearSuccessors();
        graph.unregister(this);
        id = DELETED_ID_START - id;
        assert isDeleted();
    }

    public final Node copyWithInputs() {
        assert !isExternal();
        Node newNode = clone(graph);
        NodeClass clazz = getNodeClass();
        clazz.copyInputs(this, newNode);
        for (Node input : inputs()) {
            if (input.recordsUsages()) {
                input.addUsage(newNode);
            }
        }
        return newNode;
    }

    public final Node clone(Graph into) {
        return clone(into, true);
    }

    /**
     * Must be overridden by subclasses that implement {@link Canonicalizable}. The implementation
     * in {@link Node} exists to obviate the need to cast a node before invoking
     * {@link Canonicalizable#canonical(CanonicalizerTool)}.
     * 
     * @param tool
     */
    public Node canonical(CanonicalizerTool tool) {
        throw new UnsupportedOperationException();
    }

    /**
     * Must be overridden by subclasses that implement {@link Simplifiable}. The implementation in
     * {@link Node} exists to obviate the need to cast a node before invoking
     * {@link Simplifiable#simplify(SimplifierTool)}.
     * 
     * @param tool
     */
    public void simplify(SimplifierTool tool) {
        throw new UnsupportedOperationException();
    }

    final Node clone(Graph into, boolean clearInputsAndSuccessors) {
        assert !isExternal();
        NodeClass nodeClass = getNodeClass();
        if (nodeClass.valueNumberable() && nodeClass.isLeafNode()) {
            Node otherNode = into.findNodeInCache(this);
            if (otherNode != null) {
                return otherNode;
            }
        }

        Node newNode = null;
        try {
            newNode = (Node) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new GraalInternalError(e).addContext(this);
        }
        if (clearInputsAndSuccessors) {
            nodeClass.clearInputs(newNode);
            nodeClass.clearSuccessors(newNode);
        }
        newNode.graph = into;
        newNode.typeCacheNext = null;
        newNode.id = INITIAL_ID;
        into.register(newNode);
        newNode.usage0 = null;
        newNode.usage1 = null;
        newNode.extraUsages = NO_NODES;
        newNode.predecessor = null;

        if (nodeClass.valueNumberable() && nodeClass.isLeafNode()) {
            into.putNodeIntoCache(newNode);
        }
        newNode.afterClone(this);
        return newNode;
    }

    /**
     * Determines if a given node is {@linkplain Graph#uniqueWithoutAdd(Node) unique} within a given
     * graph if the node is non-null and {@linkplain #isExternal() external}.
     * 
     * @param node node to check
     * @param graph graph context to use
     * @return true if node is null, not external or unique within {@code graph} otherwise raises a
     *         {@link VerificationError}
     */
    public static boolean verifyUniqueIfExternal(Node node, Graph graph) {
        if (node != null && node.isExternal()) {
            Node cached = graph.findNodeInCache(node);
            node.assertTrue(cached == node, "external node does not match canonical node %s", cached);
        }
        return true;
    }

    protected void afterClone(@SuppressWarnings("unused") Node other) {
    }

    public boolean verify() {
        assertTrue(isAlive(), "cannot verify inactive nodes (id=%d)", id);
        assertTrue(graph() != null, "null graph");
        for (Node input : inputs()) {
            assertTrue(!input.recordsUsages() || input.usages().contains(this), "missing usage in input %s", input);
            assert verifyUniqueIfExternal(input, graph());
            assertTrue(input.isExternal() || input.graph() == graph(), "mismatching graph in input %s", input);
        }
        for (Node successor : successors()) {
            assertTrue(successor.predecessor() == this, "missing predecessor in %s (actual: %s)", successor, successor.predecessor());
            assertTrue(successor.graph() == graph(), "mismatching graph in successor %s", successor);
        }
        if (recordsUsages()) {
            for (Node usage : usages()) {
                assertFalse(usage.isDeleted(), "usage %s must never be deleted", usage);
                assertTrue(usage.inputs().contains(this), "missing input in usage %s", usage);
            }
        }
        if (predecessor != null) {
            assertFalse(predecessor.isDeleted(), "predecessor %s must never be deleted", predecessor);
            assertTrue(predecessor.successors().contains(this), "missing successor in predecessor %s", predecessor);
        }
        return true;
    }

    public boolean assertTrue(boolean condition, String message, Object... args) {
        if (condition) {
            return true;
        } else {
            throw new VerificationError(message, args).addContext(this);
        }
    }

    public boolean assertFalse(boolean condition, String message, Object... args) {
        if (condition) {
            throw new VerificationError(message, args).addContext(this);
        } else {
            return true;
        }
    }

    public Iterable<? extends Node> cfgPredecessors() {
        if (predecessor == null) {
            return Collections.emptySet();
        } else {
            return Collections.singleton(predecessor);
        }
    }

    /**
     * Returns an iterator that will provide all control-flow successors of this node. Normally this
     * will be the contents of all fields marked as NodeSuccessor, but some node classes (like
     * EndNode) may return different nodes. Note that the iterator may generate null values if the
     * fields contain them.
     */
    public Iterable<? extends Node> cfgSuccessors() {
        return successors();
    }

    /**
     * Nodes always use an {@linkplain System#identityHashCode(Object) identity} hash code. For this
     * reason, {@linkplain #isExternal() external} nodes should still be {@link Graph#unique unique}
     * within the context of a graph.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Equality tests must rely solely on identity. For this reason, {@linkplain #isExternal()
     * external} nodes should still be {@link Graph#unique unique} within the context of a graph.
     */
    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Provides a {@link Map} of properties of this node for use in debugging (e.g., to view in the
     * ideal graph visualizer).
     */
    public final Map<Object, Object> getDebugProperties() {
        return getDebugProperties(new HashMap<>());
    }

    /**
     * Fills a {@link Map} with properties of this node for use in debugging (e.g., to view in the
     * ideal graph visualizer). Subclasses overriding this method should also fill the map using
     * their superclass.
     * 
     * @param map
     */
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        getNodeClass().getDebugProperties(this, map);
        return map;
    }

    /**
     * This method is a shortcut for {@link #toString(Verbosity)} with {@link Verbosity#Short}.
     */
    @Override
    public final String toString() {
        return toString(Verbosity.Short);
    }

    public enum Verbosity {
        /**
         * Only the id of the node.
         */
        Id,
        /**
         * Only the name of the node, which may contain some more information for certain node types
         * (constants, ...).
         */
        Name,
        /**
         * {@link #Id} + {@link #Name}.
         */
        Short,
        /**
         * Defaults to {@link #Short} and may be enhanced by subclasses.
         */
        Long,
        /**
         * For use by a custom formatting facility in an IDE.
         */
        Debugger,
        /**
         * All the other information plus all debug properties of the node.
         */
        All
    }

    /**
     * Creates a String representation for this node with a given {@link Verbosity}.
     */
    public String toString(Verbosity verbosity) {
        switch (verbosity) {
            case Id:
                return Integer.toString(id);
            case Name:
                return getNodeClass().shortName();
            case Short:
                return toString(Verbosity.Id) + "|" + toString(Verbosity.Name);
            case Long:
                return toString(Verbosity.Short);
            case Debugger:
            case All: {
                StringBuilder str = new StringBuilder();
                str.append(toString(Verbosity.Short)).append(" { ");
                for (Map.Entry<Object, Object> entry : getDebugProperties().entrySet()) {
                    str.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
                }
                str.append(" }");
                return str.toString();
            }
            default:
                throw new RuntimeException("unknown verbosity: " + verbosity);
        }
    }

    @Deprecated
    public int getId() {
        return id;
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        if ((flags & FormattableFlags.ALTERNATE) == FormattableFlags.ALTERNATE) {
            formatter.format("%s", toString(Verbosity.Id));
        } else if ((flags & FormattableFlags.UPPERCASE) == FormattableFlags.UPPERCASE) {
            formatter.format("%s", toString(Verbosity.Long));
        } else {
            formatter.format("%s", toString(Verbosity.Short));
        }

        boolean neighborsAlternate = ((flags & FormattableFlags.LEFT_JUSTIFY) == FormattableFlags.LEFT_JUSTIFY);
        int neighborsFlags = (neighborsAlternate ? FormattableFlags.ALTERNATE | FormattableFlags.LEFT_JUSTIFY : 0);
        NodeClass nodeClass = getNodeClass();
        if (width > 0) {
            if (this.predecessor != null) {
                formatter.format(" pred={");
                this.predecessor.formatTo(formatter, neighborsFlags, width - 1, 0);
                formatter.format("}");
            }

            NodeClassIterator inputIter = inputs().iterator();
            while (inputIter.hasNext()) {
                Position position = inputIter.nextPosition();
                Node input = nodeClass.get(this, position);
                if (input != null) {
                    formatter.format(" ");
                    formatter.format(nodeClass.getName(position));
                    formatter.format("={");
                    input.formatTo(formatter, neighborsFlags, width - 1, 0);
                    formatter.format("}");
                }
            }
        }

        if (precision > 0) {
            if (!usages().isEmpty()) {
                formatter.format(" usages={");
                int z = 0;
                for (Node usage : usages()) {
                    if (z != 0) {
                        formatter.format(", ");
                    }
                    usage.formatTo(formatter, neighborsFlags, 0, precision - 1);
                    ++z;
                }
                formatter.format("}");
            }

            NodeClassIterator succIter = successors().iterator();
            while (succIter.hasNext()) {
                Position position = succIter.nextPosition();
                Node successor = nodeClass.get(this, position);
                if (successor != null) {
                    formatter.format(" ");
                    formatter.format(nodeClass.getName(position));
                    formatter.format("={");
                    successor.formatTo(formatter, neighborsFlags, 0, precision - 1);
                    formatter.format("}");
                }
            }
        }
    }
}
