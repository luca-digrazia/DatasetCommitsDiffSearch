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

public abstract class NodeIterable<T extends Node> implements Iterable<T> {
    protected NodePredicate until = NodePredicate.IS_NULL;
    public NodeIterable<T> until(final T u) {
        until = until.or(NodePredicate.equals(u));
        return this;
    }
    public NodeIterable<T> until(final Class<? extends T> clazz) {
        until = until.or(new TypePredicate(clazz));
        return this;
    }
    public <F extends T> FilteredNodeIterable<F> filter(Class<F> clazz) {
        return new FilteredNodeIterable<T>(this).and(clazz);
    }
    public List<T> snapshot() {
        ArrayList<T> list = new ArrayList<T>();
        for (T n : this) {
            list.add(n);
        }
        return list;
    }
    public T first() {
        Iterator<T> iterator = iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
}
