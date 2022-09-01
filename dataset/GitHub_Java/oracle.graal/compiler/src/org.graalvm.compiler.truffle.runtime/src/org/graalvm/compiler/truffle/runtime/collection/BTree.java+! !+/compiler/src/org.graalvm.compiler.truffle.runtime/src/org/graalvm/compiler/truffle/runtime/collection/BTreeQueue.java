/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.collection;

import java.util.ArrayList;
import java.util.Arrays;

public class BTree<E extends Comparable<E>> implements Pool<E> {
    private static final Object FAILURE_DUPLICATE = new Object();
    private static final Object SUCCESS = new Object();
    private static final Object MAX_ELEMENT = new Object() {
        @Override
        public String toString() {
            return "<max-value>";
        }
    };
    private static final int BRANCHING_FACTOR = 16;

    private static abstract class Node<E extends Comparable<E>> {
        Object pivot;
        int count;
        final Object[] children;

        public Node(Object pivot) {
            this.pivot = pivot;
            this.count = 0;
            this.children = new Object[BRANCHING_FACTOR];
        }

        public boolean isLeaf() {
            return this instanceof Leaf<?>;
        }
    }

    private static final class Leaf<E extends Comparable<E>> extends Node<E> {
        public Leaf(Object pivot) {
            super(pivot);
        }

        @Override
        public String toString() {
            return String.format("Leaf(pivot = %s, count = %d; %s)", pivot, count, Arrays.asList(children));
        }
    }

    private static final class Inner<E extends Comparable<E>> extends Node<E> {
        int childCount;

        public Inner(Object pivot) {
            super(pivot);
            this.childCount = 0;
        }

        @SuppressWarnings("unchecked")
        @Override
        public String toString() {
            ArrayList<Object> childPivots = new ArrayList<>();
            for (Object c : children) {
                if (c != null) {
                    childPivots.add(((Node<E>) c).pivot);
                }
            }
            return String.format("Inner(pivot = %s, count = %d; %s)", pivot, count, childPivots);
        }
    }

    private class Compress {
        final E removedValue;
        Node<E> target;

        public Compress(E removedValue, Leaf<E> target) {
            this.removedValue = removedValue;
            this.target = target;
        }
    }

    private Node<E> root;

    public BTree() {
        this.root = new Leaf<>(MAX_ELEMENT);
    }

    @SuppressWarnings("unchecked")
    private Object insert(Node<E> node, E x) {
        if (node.isLeaf()) {
            if (node.count < BRANCHING_FACTOR) {
                // We are inserting into the leaf, there is space left.
                //
                //   [...]
                //    | \
                // [yz.] [...]
                //  ^
                //  x
                int pos = 0;
                E cur = null;
                while ((cur = (E) node.children[pos]) != null) {
                    if (cur == x) {
                        // The item is already in the queue.
                        return FAILURE_DUPLICATE;
                    }
                    if (compareUnique(cur, x) >= 0) {
                        break;
                    }
                    pos++;
                }
                E prev = x;
                while (prev != null) {
                    node.children[pos] = prev;
                    prev = cur;
                    pos++;
                    cur = pos < node.children.length ? (E) node.children[pos] : null;
                }
                node.count++;
                if (node.pivot != MAX_ELEMENT && node.pivot != node.children[node.count - 1]) {
                    node.pivot = (E) node.children[node.count - 1];
                }
                return SUCCESS;
            } else {
                // No space left, split the leaf.
                //
                //   [...]
                //    | \
                // [yzw] [...]
                //  ^
                //  x
                //
                // Create one new node to hold half of the elements:
                //
                //       [...]
                //      /     \
                // [xy.][zw.]  [...]
                int siblingStart = BRANCHING_FACTOR / 2;
                E midElement = (E) node.children[siblingStart - 1];
                Leaf<E> sibling = new Leaf<E>(node.pivot);
                node.pivot = midElement;
                for (int npos = siblingStart, spos = 0; npos < BRANCHING_FACTOR; npos++, spos++) {
                    sibling.children[spos] = node.children[npos];
                    sibling.count++;
                    node.children[npos] = null;
                    node.count--;
                }
                if (compareUnique(x, midElement) < 0) {
                    insert(node, x);
                } else {
                    insert(sibling, x);
                }
                return sibling;
            }
        } else {
            Inner<E> inner = (Inner<E>) node;
            int pos = 0;
            Node<E> child = null;
            while (pos < BRANCHING_FACTOR) {
                final Node<E> candidate = (Node<E>) inner.children[pos];
                if (candidate == null) {
                    throw new IllegalStateException("Child not found for: " + x);
                }
                if (compareUnique(candidate.pivot, x) > 0) {
                    child = candidate;
                    break;
                }
                pos++;
            }
            Object result = insert(child, x);
            if (result == SUCCESS) {
                inner.count++;
                return result;
            } else if (result == FAILURE_DUPLICATE) {
                return result;
            } else {
                final Node<E> nchild = (Node<E>) result;
                inner.count++;
                inner.count -= nchild.count;
                if (inner.childCount < BRANCHING_FACTOR) {
                    // There is space left, shift some of the inner nodes.
                    pos++;
                    insertChildAt(inner, pos, nchild);
                    return SUCCESS;
                } else {
                    // There is no space left, split this inner node.
                    int siblingStart = BRANCHING_FACTOR / 2;
                    E midElement = (E) ((Node<E>) inner.children[siblingStart - 1]).pivot;
                    Inner<E> sibling = new Inner<E>(inner.pivot);
                    if (compare(sibling.pivot, nchild.pivot) < 0) {
                        sibling.pivot = nchild.pivot;
                    }
                    inner.pivot = midElement;
                    for (int npos = siblingStart, spos = 0; npos < BRANCHING_FACTOR; npos++, spos++) {
                        final Node<E> c = (Node<E>) inner.children[npos];
                        sibling.children[spos] = c;
                        sibling.childCount++;
                        sibling.count += c.count;
                        inner.children[npos] = null;
                        inner.childCount--;
                        inner.count -= c.count;
                    }
                    // Insert the new child node into the appropriate node.
                    final Inner<E> target = compareUnique(nchild.pivot, inner.pivot) <= 0 ? inner : sibling;
                    int tpos = 0;
                    while (tpos < BRANCHING_FACTOR) {
                        Node<E> c = (Node<E>) target.children[tpos];
                        if (c == null) {
                            // New child is the last child.
                            target.children[tpos] = nchild;
                            target.childCount++;
                            target.count += nchild.count;
                            return sibling;
                        } else if (compare(nchild.pivot, c.pivot) <= 0) {
                            insertChildAt(target, tpos, nchild);
                            return sibling;
                        }
                        tpos++;
                    }
                    throw new IllegalStateException("Could not find the insertion point after split: " + target + ", new child: " + nchild.pivot);
                }
            }
        }
    }

    private void insertChildAt(Inner<E> inner, int from, Node<E> nchild) {
        int pos = from;
        Node<?> cur = (Node<?>) inner.children[pos];
        Node<?> prev = nchild;
        while (prev != null) {
            inner.children[pos] = prev;
            prev = cur;
            pos++;
            cur = pos < inner.children.length ? (Node<?>) inner.children[pos] : null;
        }
        inner.childCount++;
        inner.count += nchild.count;
    }

    @SuppressWarnings("unchecked")
    private boolean insertRoot(E x) {
        final Object result = insert(root, x);
        if (result == SUCCESS) {
            return true;
        } else if (result instanceof Node<?>) {
            // Need to add one more level of the tree.
            final Node<E> sibling = (Node<E>) result;
            final Inner<E> nroot = new Inner<E>(null);
            if (compareUnique(root.pivot, sibling.pivot) < 0) {
                nroot.children[0] = root;
                nroot.children[1] = sibling;
                nroot.pivot = sibling.pivot;
            } else {
                nroot.children[0] = sibling;
                nroot.children[1] = root;
                nroot.pivot = root.pivot;
            }
            nroot.count = root.count + sibling.count;
            nroot.childCount = 2;
            root = nroot;
            return true;
        } else if (result == FAILURE_DUPLICATE) {
            return false;
        } else {
            throw new IllegalStateException("Unexpected result: " + result);
        }
    }

    @SuppressWarnings("unchecked")
    private int compareUnique(Object a, Object b) {
        if (a == MAX_ELEMENT) {
            if (b == MAX_ELEMENT) {
                return 0;
            }
            return 1;
        }
        if (b == MAX_ELEMENT) {
            return -1;
        }
        int result = ((Comparable<E>) a).compareTo((E) b);
        if (result != 0) {
            return result;
        } else {
            throw new UnsupportedOperationException("Two different objects must not be equal in comparison.");
        }
    }

    @SuppressWarnings("unchecked")
    private int compare(Object a, Object b) {
        if (a == MAX_ELEMENT) {
            if (b == MAX_ELEMENT) {
                return 0;
            }
            return 1;
        }
        if (b == MAX_ELEMENT) {
            return -1;
        }
        return ((Comparable<E>) a).compareTo((E) b);
    }

    @Override
    public void add(E x) {
        insertRoot(x);
    }

    @Override
    public int addIndexOf(E x) {
        add(x);
        return indexBefore(x);
    }

    @SuppressWarnings("unchecked")
    public int indexBefore(E x) {
        int count = 0;
        Node<E> node = root;
        while (!node.isLeaf()) {
            int pos = 0;
            while (pos < BRANCHING_FACTOR) {
                final Node<E> child = (Node<E>) node.children[pos];
                if (compare(x, child.pivot) <= 0) {
                    node = child;
                    break;
                }
                count += child.count;
                pos++;
            }
            if (pos == BRANCHING_FACTOR) {
                throw new IllegalStateException("Key " + x + " cannot be found in node: " + node);
            }
        }
        int pos = 0;
        while (pos < BRANCHING_FACTOR) {
            final E cur = (E) node.children[pos];
            if (cur == null) {
                return count;
            }
            final int comparison = compare(x, cur);
            if (comparison <= 0) {
                return count;
            }
            count++;
            pos++;
        }
        return count;
    }

    @Override
    public E poll() {
        return removeFirstRoot();
    }

    @SuppressWarnings("unchecked")
    private E removeFirstRoot() {
        final Object result = removeFirst(root);
        if (result instanceof BTree<?>.Compress) {
            Compress compress = (Compress) result;
            if (compress.target == root) {
                root = new Leaf<E>(MAX_ELEMENT);
            }
            insertAll(compress.target);
            return compress.removedValue;
        } else {
            return (E) result;
        }
    }

    @SuppressWarnings("unchecked")
    private void insertAll(Node<E> node) {
        if (node.isLeaf()) {
            for (int pos = 0; pos < BRANCHING_FACTOR; pos++) {
                E element = (E) node.children[pos];
                if (element == null) {
                    break;
                }
                insertRoot(element);
            }
        } else {
            for (int pos = 0; pos < BRANCHING_FACTOR; pos++) {
                Node<E> child = (Node<E>) node.children[pos];
                if (child == null) {
                    break;
                }
                insertAll(child);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object removeFirst(Node<E> node) {
        if (node instanceof Leaf<?>) {
            final E result = (E) node.children[0];
            int pos = 0;
            while (pos < BRANCHING_FACTOR) {
                final Object next = pos + 1 < node.children.length ? node.children[pos + 1] : null;
                node.children[pos] = next;
                if (next == null) {
                    break;
                }
                pos++;
            }
            node.count--;
            if (node.count == 1 && node != root) {
                return new Compress(result, (Leaf<E>) node);
            } else {
                return result;
            }
        } else {
            final Node<E> child = (Node<E>) node.children[0];
            final Object result = removeFirst(child);
            node.count--;
            if (result instanceof BTree<?>.Compress) {
                final Inner<E> inner = (Inner<E>) node;
                final Compress compress = (Compress) result;
                if (compress.target == child) {
                    if (inner.childCount == 2) {
                        // If the child is supposed to be compressed and the current child count is 2,
                        // then we must also recycle this node.
                        compress.target = inner;
                    } else {
                        // We need to remove this child (because its contents will be reinserted).
                        int pos = 0;
                        while (pos < BRANCHING_FACTOR) {
                            final Node<E> next = pos + 1 < node.children.length ? ((Node<E>) node.children[pos + 1]) : null;
                            node.children[pos] = next;
                            if (next == null) {
                                break;
                            }
                            pos++;
                        }
                        inner.childCount--;
                        inner.count -= child.count;
                    }
                } else {
                    // If the compression target is not the immediate child,
                    // we must still decrease the count.
                    inner.count -= compress.target.count;
                }
            }
            return result;
        }
    }

    @Override
    public E peek() {
        return lookupFirst(root);
    }

    @SuppressWarnings("unchecked")
    private E lookupFirst(Node<E> node) {
        if (node instanceof Leaf<?>) {
            return (E) node.children[0];
        } else {
            return lookupFirst((Node<E>) node.children[0]);
        }
    }

    @Override
    public void clear() {
        root = new Leaf<>(MAX_ELEMENT);
    }

    @Override
    public int size() {
        return root.count;
    }

    @Override
    public Object[] toArray() {
        final Object[] result = new Object[root.count];
        toArray(result, 0, root);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void toArray(Object[] result, int from, Node<E> node) {
        if (node.isLeaf()) {
            for (int i = from, pos = 0; pos < BRANCHING_FACTOR; i++, pos++) {
                E element = (E) node.children[pos];
                if (element == null) {
                    break;
                }
                result[i] = element;
            }
        } else {
            for (int updatedFrom = from, pos = 0; pos < BRANCHING_FACTOR; pos++) {
                Node<E> child = (Node<E>) node.children[pos];
                if (child == null) {
                    break;
                }
                toArray(result, updatedFrom, child);
                updatedFrom += child.count;
            }
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        // TODO
        return null;
    }

    @Override
    public int internalCapacity() {
        return -1;
    }

    public void checkInvariants() {
        check(root, null);
    }

    @SuppressWarnings("unchecked")
    private int check(Node<E> node, Object max) {
        if (node instanceof Leaf<?>) {
            boolean nullSeen = false;
            int count = 0;
            for (int i = 0; i < node.children.length; i++) {
                E x = (E) node.children[i];
                if (nullSeen) {
                    ensure(x == null, "After first null, all children must be null: %s", node);
                } else {
                    if (x == null) {
                        nullSeen = true;
                    } else {
                        count++;
                        if (max != null) {
                            ensure(compareUnique(max, x) < 0, "Elements must be ordered: %s", node);
                        }
                        max = x;
                    }
                }
            }
            ensure(max == node.pivot || node.pivot == MAX_ELEMENT, "Pivot must be the largest element: %s", node);
            ensure(count == node.count, "Count must correspond to the number of non-null slots: %s", node);
        } else {
            final Inner<E> inner = (Inner<E>) node;
            boolean nullSeen = false;
            int count = 0;
            int childCount = 0;
            for (int i = 0; i < inner.children.length; i++) {
                Node<E> child = (Node<E>) inner.children[i];
                if (nullSeen) {
                    ensure(child == null, "After first null, all children must be null: %s", inner);
                } else {
                    if (child == null) {
                        nullSeen = true;
                    } else {
                        check(child, max);
                        childCount++;
                        count += child.count;
                        if (max != null) {
                            ensure(compareUnique(max, child.pivot) < 0, "Elements must be ordered: %s", inner);
                        }
                        max = child.pivot;
                    }
                }
            }
            ensure(max == inner.pivot || inner.pivot == MAX_ELEMENT, "Pivot must be the largest child key: %s", inner);
            ensure(count == inner.count, "Count must correspond to the sum of child counts: %s", inner);
            ensure(childCount == inner.childCount, "Child count must correspond to the number children: %s", inner);
        }
        return node.count;
    }

    private void ensure(boolean condition, String title, Object value) {
        if (!condition) {
            throw new IllegalStateException(String.format(title, value));
        }
    }

    public String prettyString() {
        StringBuilder result = new StringBuilder(128);
        prettyString(root, 0, result);
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private void prettyString(Node<E> node, int indentation, StringBuilder result) {
        for (int i = 0; i < indentation; i++) {
            result.append(" ");
        }
        if (node instanceof Inner<?>) {
            final Inner<E> inner = (Inner<E>) node;
            result.append("<# = " + inner.count + ", max = " + inner.pivot + ", #child = " + inner.childCount + ">");
            result.append(System.lineSeparator());
            for (int i = 0; i < inner.childCount; i++) {
                prettyString((Node<E>) inner.children[i], indentation + 2, result);
            }
        } else {
            result.append("<# = " + node.count + ", max = " + node.pivot + "; ");
            for (int i = 0; i < node.count; i++) {
                result.append(node.children[i]).append(", ");
            }
            for (int i = node.count; i < node.children.length; i++) {
                result.append(node.children[i] != null ? "!! " + node.children[i] + " !!" : ".").append(", ");
            }
            result.append(">");
            result.append(System.lineSeparator());
        }
    }
}
