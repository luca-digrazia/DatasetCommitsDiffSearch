/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.LazyClassLoadingTestFactory.TestNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.*;

public class LazyClassLoadingTest {
    @Test
    public void test() {
        String factoryName = TestNodeFactory.class.getName();
        String nodeName = factoryName + "$" + TestNode.class.getSimpleName() + "Gen";
        Assert.assertTrue(isLoaded(factoryName));
        Assert.assertFalse(isLoaded(nodeName));

        NodeFactory<TestNode> factory = TestNodeFactory.getInstance();
        Assert.assertTrue(isLoaded(factoryName));
        Assert.assertTrue(isLoaded(nodeName));

        Assert.assertFalse(isLoaded(nodeName + "$UninitializedNode"));
        Assert.assertFalse(isLoaded(nodeName + "$BaseNode"));
        Assert.assertFalse(isLoaded(nodeName + "$IntNode"));
        Assert.assertFalse(isLoaded(nodeName + "$BooleanNode"));
        Assert.assertFalse(isLoaded(nodeName + "$PolymorphicNode"));

        TestRootNode<TestNode> root = TestHelper.createRoot(factory);

        Assert.assertTrue(isLoaded(nodeName + "$BaseNode"));
        Assert.assertTrue(isLoaded(nodeName + "$UninitializedNode"));
        Assert.assertFalse(isLoaded(nodeName + "$IntNode"));
        Assert.assertFalse(isLoaded(nodeName + "$BooleanNode"));
        Assert.assertFalse(isLoaded(nodeName + "$PolymorphicNode"));

        Assert.assertEquals(42, TestHelper.executeWith(root, 42));

        Assert.assertTrue(isLoaded(nodeName + "$IntNode"));
        Assert.assertFalse(isLoaded(nodeName + "$BooleanNode"));
        Assert.assertFalse(isLoaded(nodeName + "$PolymorphicNode"));

        Assert.assertEquals(true, TestHelper.executeWith(root, true));

        Assert.assertTrue(isLoaded(nodeName + "$IntNode"));
        Assert.assertTrue(isLoaded(nodeName + "$BooleanNode"));
        Assert.assertTrue(isLoaded(nodeName + "$PolymorphicNode"));
    }

    private boolean isLoaded(String className) {
        ClassLoader classLoader = getClass().getClassLoader();
        Method m;
        try {
            m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            return m.invoke(classLoader, className) != null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @NodeChild("a")
    abstract static class TestNode extends ValueNode {

        @Specialization
        int s(int a) {
            return a;
        }

        @Specialization
        boolean s(boolean a) {
            return a;
        }

    }
}
