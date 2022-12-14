/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl.test;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.dsl.Layout;

import org.junit.Assert;

public class IdentifierTest {

    private static final String A_IDENTIFIER_VALUE = "a_identifier";
    private static final Integer B_IDENTIFIER_VALUE = 14;

    @Layout
    public interface IdentifierTestLayout {

        public static final String A_IDENTIFIER = A_IDENTIFIER_VALUE;
        public static final Integer B_IDENTIFIER = B_IDENTIFIER_VALUE;

        DynamicObject createIdentifierTest(int a, int b);

    }

    private static final IdentifierTestLayout LAYOUT = IdentifierTestLayoutImpl.INSTANCE;

    @Test
    public void testContainsKey() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertTrue(object.containsKey(A_IDENTIFIER_VALUE));
        Assert.assertTrue(object.containsKey(B_IDENTIFIER_VALUE));
    }

    @Test
    public void testHasProperty() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertTrue(object.getShape().hasProperty(A_IDENTIFIER_VALUE));
        Assert.assertTrue(object.getShape().hasProperty(B_IDENTIFIER_VALUE));
    }

    @Test
    public void testGet() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertEquals(1, object.get(A_IDENTIFIER_VALUE));
        Assert.assertEquals(2, object.get(B_IDENTIFIER_VALUE));
    }

    @Test
    public void testSet() {
        final DynamicObject object = LAYOUT.createIdentifierTest(1, 2);
        Assert.assertEquals(1, object.get(A_IDENTIFIER_VALUE));
        object.set(A_IDENTIFIER_VALUE, 11);
        Assert.assertEquals(11, object.get(A_IDENTIFIER_VALUE));
    }

}
