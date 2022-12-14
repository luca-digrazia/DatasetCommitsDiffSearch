/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class NodeFactoryMissingChildTest {

    static abstract class TestBase extends Node {
    }

    static abstract class TestChild extends Node {
        public abstract Object execute();
    }

    /*
     * Tests that we can generate a node factory if no children are specified but with one execute
     * evaluated parameter.
     */
    @GenerateNodeFactory
    static abstract class TestA extends TestBase {

        public abstract Object execute(Object v);

        @Specialization
        protected Object test(Object value) {
            return null;
        }
    }

    @GenerateNodeFactory
    @NodeChild(value = "value", type = TestChild.class)
    static abstract class TestB extends TestBase {
        public abstract Object execute();

        @Specialization
        protected Object test(Object value) {
            return null;
        }
    }

}
