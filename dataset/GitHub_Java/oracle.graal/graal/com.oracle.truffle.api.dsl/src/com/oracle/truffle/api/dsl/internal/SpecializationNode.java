/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.internal;

import java.lang.reflect.*;
import java.util.concurrent.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.RewriteEvent.RewriteEvent0;
import com.oracle.truffle.api.dsl.internal.RewriteEvent.RewriteEvent1;
import com.oracle.truffle.api.dsl.internal.RewriteEvent.RewriteEvent2;
import com.oracle.truffle.api.dsl.internal.RewriteEvent.RewriteEvent3;
import com.oracle.truffle.api.dsl.internal.RewriteEvent.RewriteEvent4;
import com.oracle.truffle.api.dsl.internal.RewriteEvent.RewriteEventN;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;

/**
 * Internal implementation dependent base class for generated specialized nodes.
 */
@SuppressWarnings("unused")
@NodeInfo(cost = NodeCost.NONE)
public abstract class SpecializationNode extends Node {

    @Child protected SpecializationNode next;

    private final int index;

    public SpecializationNode() {
        this(-1);
    }

    public SpecializationNode(int index) {
        this.index = index;
    }

    @Override
    public final NodeCost getCost() {
        return NodeCost.NONE;
    }

    public static Node updateRoot(Node node) {
        updateRootImpl(((SpecializedNode) node).getSpecializationNode(), node);
        return node;
    }

    private static void updateRootImpl(SpecializationNode start, Node node) {
        NodeField[] fields = NodeClass.get(start.getClass()).getFields();
        for (int i = fields.length - 1; i >= 0; i--) {
            NodeField f = fields[i];
            if (f.getName().equals("root")) {
                f.putObject(start, node);
                break;
            }
        }
        if (start.next != null) {
            updateRootImpl(start.next, node);
        }
    }

    protected final SpecializationNode polymorphicMerge(SpecializationNode newNode) {
        SpecializationNode merged = next.merge(newNode);
        if (merged == newNode && !isSame(newNode) && count() <= 2) {
            return removeSame(new RewriteEvent0(findParentNode(), "merged polymorphic to monomorphic"));
        }
        return merged;
    }

    public final NodeCost getNodeCost() {
        switch (count()) {
            case 0:
            case 1:
                return NodeCost.UNINITIALIZED;
            case 2:
                return NodeCost.MONOMORPHIC;
            default:
                return NodeCost.POLYMORPHIC;
        }
    }

    protected abstract Node[] getSuppliedChildren();

    protected SpecializationNode merge(SpecializationNode newNode) {
        if (this.isSame(newNode)) {
            return this;
        }
        return next != null ? next.merge(newNode) : newNode;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof SpecializationNode) {
            return ((SpecializationNode) obj).isSame(this);
        }
        return super.equals(obj);
    }

    @Override
    public final int hashCode() {
        return index;
    }

    protected boolean isSame(SpecializationNode other) {
        return getClass() == other.getClass();
    }

    private final int count() {
        return next != null ? next.count() + 1 : 1;
    }

    protected final SpecializationNode removeSame(final CharSequence reason) {
        return atomic(new Callable<SpecializationNode>() {
            public SpecializationNode call() throws Exception {
                return removeImpl(SpecializationNode.this, reason);
            }
        });
    }

    /** Find the topmost of the specialization chain. */
    private final SpecializationNode findStart() {
        SpecializationNode node = this;
        Node parent = this.getParent();
        while (parent instanceof SpecializationNode) {
            SpecializationNode parentCast = ((SpecializationNode) parent);
            if (parentCast.next != node) {
                break;
            }
            node = parentCast;
            parent = node.getParent();
        }
        return node;
    }

    private final Node findParentNode() {
        return findStart().getParent();
    }

    private SpecializationNode removeImpl(SpecializationNode toRemove, CharSequence reason) {
        SpecializationNode start = findStart();
        SpecializationNode current = start;
        while (current != null) {
            if (current.isSame(toRemove)) {
                current.replace(current.next, reason);
                if (current == start) {
                    start = start.next;
                }
            }
            current = current.next;
        }
        return start;
    }

    public Object acceptAndExecute(VirtualFrame frame) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(VirtualFrame frame, Object o1) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(VirtualFrame frame, Object o1, Object o2) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(VirtualFrame frame, Object o1, Object o2, Object o3) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(VirtualFrame frame, Object o1, Object o2, Object o3, Object o4) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(VirtualFrame frame, Object... args) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createFallback() {
        return null;
    }

    protected SpecializationNode createPolymorphic() {
        return null;
    }

    protected SpecializationNode createNext(VirtualFrame frame) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(VirtualFrame frame, Object o1) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(VirtualFrame frame, Object o1, Object o2) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(VirtualFrame frame, Object o1, Object o2, Object o3) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(VirtualFrame frame, Object o1, Object o2, Object o3, Object o4) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(VirtualFrame frame, Object... args) {
        throw new UnsupportedOperationException();
    }

    protected final Object uninitialized(VirtualFrame frame) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame);
        }
        return insertSpecialization(nextSpecialization, new RewriteEvent0(findParentNode(), "inserted new specialization")).acceptAndExecute(frame);
    }

    protected final Object uninitialized(VirtualFrame frame, Object o1) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1);
        }
        return insertSpecialization(nextSpecialization, new RewriteEvent1(findParentNode(), "inserted new specialization", o1)).acceptAndExecute(frame, o1);
    }

    protected final Object uninitialized(VirtualFrame frame, Object o1, Object o2) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1, o2);
        }
        return insertSpecialization(nextSpecialization, new RewriteEvent2(findParentNode(), "inserted new specialization", o1, o2)).acceptAndExecute(frame, o1, o2);
    }

    protected final Object uninitialized(VirtualFrame frame, Object o1, Object o2, Object o3) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2, o3);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1, o2, o3);
        }
        return insertSpecialization(nextSpecialization, new RewriteEvent3(findParentNode(), "inserted new specialization", o1, o2, o3)).acceptAndExecute(frame, o1, o2, o3);
    }

    protected final Object uninitialized(VirtualFrame frame, Object o1, Object o2, Object o3, Object o4) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2, o3, o4);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1, o2, o3, o4);
        }
        return insertSpecialization(nextSpecialization, new RewriteEvent4(findParentNode(), "inserts new specialization", o1, o2, o3, o4)).acceptAndExecute(frame, o1, o2, o3, o4);
    }

    protected final Object uninitialized(VirtualFrame frame, Object... args) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, args);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            unsupported(frame, args);
        }
        return insertSpecialization(nextSpecialization, new RewriteEventN(findParentNode(), "inserts new specialization", args)).acceptAndExecute(frame, args);
    }

    private boolean needsPolymorphic() {
        return findStart().count() == 2;
    }

    protected final Object remove(String reason, VirtualFrame frame) {
        return removeSame(new RewriteEvent0(findParentNode(), reason)).acceptAndExecute(frame);
    }

    protected final Object remove(String reason, VirtualFrame frame, Object o1) {
        return removeSame(new RewriteEvent1(findParentNode(), reason, o1)).acceptAndExecute(frame, o1);
    }

    protected final Object remove(String reason, VirtualFrame frame, Object o1, Object o2) {
        return removeSame(new RewriteEvent2(findParentNode(), reason, o1, o2)).acceptAndExecute(frame, o1, o2);
    }

    protected final Object remove(String reason, VirtualFrame frame, Object o1, Object o2, Object o3) {
        return removeSame(new RewriteEvent3(findParentNode(), reason, o1, o2, o3)).acceptAndExecute(frame, o1, o2, o3);
    }

    protected final Object remove(String reason, VirtualFrame frame, Object o1, Object o2, Object o3, Object o4) {
        return removeSame(new RewriteEvent4(findParentNode(), reason, o1, o2, o3, o4)).acceptAndExecute(frame, o1, o2, o3, o4);
    }

    protected final Object remove(String reason, VirtualFrame frame, Object... args) {
        return removeSame(new RewriteEventN(findParentNode(), reason, args)).acceptAndExecute(frame, args);
    }

    protected Object unsupported(VirtualFrame frame) {
        throw new UnsupportedSpecializationException(findParentNode(), getSuppliedChildren());
    }

    protected Object unsupported(VirtualFrame frame, Object o1) {
        throw new UnsupportedSpecializationException(findParentNode(), getSuppliedChildren(), o1);
    }

    protected Object unsupported(VirtualFrame frame, Object o1, Object o2) {
        throw new UnsupportedSpecializationException(findParentNode(), getSuppliedChildren(), o1, o2);
    }

    protected Object unsupported(VirtualFrame frame, Object o1, Object o2, Object o3) {
        throw new UnsupportedSpecializationException(findParentNode(), getSuppliedChildren(), o1, o2, o3);
    }

    protected Object unsupported(VirtualFrame frame, Object o1, Object o2, Object o3, Object o4) {
        throw new UnsupportedSpecializationException(findParentNode(), getSuppliedChildren(), o1, o2, o3, o4);
    }

    protected Object unsupported(VirtualFrame frame, Object... args) {
        throw new UnsupportedSpecializationException(findParentNode(), getSuppliedChildren(), args);
    }

    private SpecializationNode insertSpecialization(final SpecializationNode generated, final CharSequence message) {
        return atomic(new Callable<SpecializationNode>() {
            public SpecializationNode call() {
                return insert(generated, message);
            }
        });
    }

    private final SpecializationNode insert(final SpecializationNode generated, CharSequence message) {
        SpecializationNode start = findStart();
        if (start == this) {
            // fast path for first insert
            return insertBefore(this, generated, message);
        } else {
            return slowSortedInsert(start, generated, message);
        }
    }

    private static <T> SpecializationNode slowSortedInsert(SpecializationNode start, final SpecializationNode generated, final CharSequence message) {
        final SpecializationNode merged = start.merge(generated);
        if (merged == generated) {
            // new node
            if (start.count() == 2) {
                insertBefore(start, start.createPolymorphic(), "insert polymorphic");
            }
            SpecializationNode insertBefore = findInsertBeforeNode(generated.index, start);
            return insertBefore(insertBefore, generated, message);
        } else {
            // existing node
            merged.replace(merged, new RewriteEvent0(merged.findParentNode(), "merged specialization"));
            return merged;
        }
    }

    private static SpecializationNode findInsertBeforeNode(int generatedIndex, SpecializationNode start) {
        SpecializationNode current = start;
        while (current != null && current.index < generatedIndex) {
            current = current.next;
        }
        return current;
    }

    private static <T> SpecializationNode insertBefore(SpecializationNode node, SpecializationNode insertBefore, CharSequence message) {
        insertBefore.next = node;
        return node.replace(insertBefore, message);
    }

    @Override
    public final String toString() {
        Class<?> clazz = getClass();
        StringBuilder b = new StringBuilder();
        b.append(clazz.getSimpleName());

        appendFields(b, clazz);
        if (next != null) {
            b.append(" -> ").append(next.toString());
        }
        return b.toString();
    }

    private void appendFields(StringBuilder b, Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        if (fields.length == 0) {
            return;
        }
        b.append("(");
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            if (name.equals("root")) {
                continue;
            }
            b.append(field.getName());
            try {
                field.setAccessible(true);
                b.append(field.get(this));
            } catch (IllegalArgumentException e) {
                b.append(e.toString());
            } catch (IllegalAccessException e) {
                b.append(e.toString());
            }
        }
        b.append(")");
    }

}
