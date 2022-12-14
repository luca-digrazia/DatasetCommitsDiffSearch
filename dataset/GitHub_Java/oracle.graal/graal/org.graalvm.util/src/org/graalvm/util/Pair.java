/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.util;

import java.util.Objects;

/**
 * Utility class repres4enting a pair of values.
 */
public class Pair<L, R> {
    private L left;
    private R right;

    public Pair() {
    }

    public Pair(L left) {
        this.left = left;
    }

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    public void setLeft(L value) {
        this.left = value;
    }

    public void setRight(R value) {
        this.right = value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(left) + 31 * Objects.hashCode(right);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof Pair) {
            Pair<L, R> pair = (Pair<L, R>) obj;
            return Objects.equals(left, pair.left) && Objects.equals(right, pair.right);
        }

        return false;
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", left, right);
    }
}
