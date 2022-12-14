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
package com.oracle.max.graal.graph.iterators;

import java.util.*;

import com.oracle.max.graal.graph.*;

public class FilteredNodeIterable<T extends Node> extends NodeIterable<T> {
    private final NodeIterable<T> nodeIterable;
    private NodePredicate predicate = NodePredicates.alwaysTrue();
    private NodePredicate until = NodePredicates.isNull();
    public FilteredNodeIterable(NodeIterable<T> nodeIterable) {
        this.nodeIterable = nodeIterable;
    }
    public FilteredNodeIterable<T> and(NodePredicate nodePredicate) {
        this.predicate = this.predicate.and(nodePredicate);
        return this;
    }
    public FilteredNodeIterable<T> or(NodePredicate nodePredicate) {
        this.predicate = this.predicate.or(nodePredicate);
        return this;
    }
    @Override
    public NodeIterable<T> until(final T u) {
        until = until.or(NodePredicates.equals(u));
        return this;
    }
    @Override
    public NodeIterable<T> until(final Class<? extends T> clazz) {
        until = until.or(NodePredicates.isA(clazz));
        return this;
    }
    @Override
    public Iterator<T> iterator() {
        final Iterator<T> iterator = nodeIterable.iterator();
        return new PredicatedProxyNodeIterator<>(until, iterator, predicate);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <F extends T> FilteredNodeIterable<F> filter(Class<F> clazz) {
        return (FilteredNodeIterable<F>) this.and(NodePredicates.isA(clazz));
    }

    @Override
    public FilteredNodeIterable<T> filter(NodePredicate p) {
        return this.and(p);
    }

    @Override
    public FilteredNodeIterable<T> filterInterface(Class< ? > iface) {
        return this.and(NodePredicates.isAInterface(iface));
    }
}
