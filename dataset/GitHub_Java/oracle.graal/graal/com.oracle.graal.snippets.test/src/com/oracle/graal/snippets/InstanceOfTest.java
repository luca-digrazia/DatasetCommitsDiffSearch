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
package com.oracle.graal.snippets;

import java.util.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

/**
 * Tests the implementation of instanceof, allowing profiling information to
 * be manually specified.
 */
public class InstanceOfTest extends TypeCheckTest {

    @Override
    protected void editPhasePlan(ResolvedJavaMethod method, StructuredGraph graph, PhasePlan phasePlan) {
        phasePlan.disablePhase(InliningPhase.class);
    }

    @Override
    protected void replaceProfile(StructuredGraph graph, JavaTypeProfile profile) {
        InstanceOfNode ion = graph.getNodes().filter(InstanceOfNode.class).first();
        if (ion != null) {
            InstanceOfNode ionNew = graph.add(new InstanceOfNode(ion.targetClassInstruction(), ion.targetClass(), ion.object(), profile));
            graph.replaceFloating(ion, ionNew);
        }
    }

    @Test
    public void test1() {
        test("isString",    profile(),                        "object");
        test("isString",    profile(String.class),            "object");

        test("isString",    profile(),                        Object.class);
        test("isString",    profile(String.class),            Object.class);
    }

    @Test
    public void test2() {
        test("isStringInt",    profile(),                        "object");
        test("isStringInt",    profile(String.class),            "object");

        test("isStringInt",    profile(),                        Object.class);
        test("isStringInt",    profile(String.class),            Object.class);
    }

    @Test
    public void test2_1() {
        test("isStringIntComplex",    profile(),                        "object");
        test("isStringIntComplex",    profile(String.class),            "object");

        test("isStringIntComplex",    profile(),                        Object.class);
        test("isStringIntComplex",    profile(String.class),            Object.class);
    }

    @Test
    public void test3() {
        Throwable throwable = new Exception();
        test("isThrowable",    profile(),                             throwable);
        test("isThrowable",    profile(Throwable.class),              throwable);
        test("isThrowable",    profile(Exception.class, Error.class), throwable);

        test("isThrowable",    profile(),                             Object.class);
        test("isThrowable",    profile(Throwable.class),              Object.class);
        test("isThrowable",    profile(Exception.class, Error.class), Object.class);
    }

    @Test
    public void test3_1() {
        onlyFirstIsException(new Exception(), new Error());
        test("onlyFirstIsException",    profile(),                             new Exception(), new Error());
        test("onlyFirstIsException",    profile(),                             new Error(), new Exception());
        test("onlyFirstIsException",    profile(),                             new Exception(), new Exception());
        test("onlyFirstIsException",    profile(),                             new Error(), new Error());
    }

    @Test
    public void test4() {
        Throwable throwable = new Exception();
        test("isThrowableInt",    profile(),                             throwable);
        test("isThrowableInt",    profile(Throwable.class),              throwable);
        test("isThrowableInt",    profile(Exception.class, Error.class), throwable);

        test("isThrowableInt",    profile(),                             Object.class);
        test("isThrowableInt",    profile(Throwable.class),              Object.class);
        test("isThrowableInt",    profile(Exception.class, Error.class), Object.class);
    }

    @Test
    public void test5() {
        Map map = new HashMap<>();
        test("isMap",    profile(),                             map);
        test("isMap",    profile(HashMap.class),                map);
        test("isMap",    profile(TreeMap.class, HashMap.class), map);

        test("isMap",    profile(),                             Object.class);
        test("isMap",    profile(HashMap.class),                Object.class);
        test("isMap",    profile(TreeMap.class, HashMap.class), Object.class);
    }

    @Test
    public void test6() {
        Map map = new HashMap<>();
        test("isMapInt",    profile(),                             map);
        test("isMapInt",    profile(HashMap.class),                map);
        test("isMapInt",    profile(TreeMap.class, HashMap.class), map);

        test("isMapInt",    profile(),                             Object.class);
        test("isMapInt",    profile(HashMap.class),                Object.class);
        test("isMapInt",    profile(TreeMap.class, HashMap.class), Object.class);
    }

    public static boolean isString(Object o) {
        return o instanceof String;
    }

    public static int isStringInt(Object o) {
        if (o instanceof String) {
            return id(0);
        }
        return id(0);
    }

    public static int isStringIntComplex(Object o) {
        if (o instanceof String || o instanceof Integer) {
            return id(o instanceof String ? 1 : 0);
        }
        return id(0);
    }

    public static int id(int value) {
        return value;
    }

    public static boolean isThrowable(Object o) {
        return ((Throwable) o) instanceof Exception;
    }

    public static int onlyFirstIsException(Throwable t1, Throwable t2) {
        if (t1 instanceof Exception ^ t2 instanceof Exception) {
            return t1 instanceof Exception ? 1 : -1;
        }
        return -1;
    }


    public static int isThrowableInt(Object o) {
        if (o instanceof Throwable) {
            return 1;
        }
        if (o instanceof Throwable) {
            return 2;
        }
        return 0;
    }

    public static boolean isMap(Object o) {
        return o instanceof Map;
    }

    public static int isMapInt(Object o) {
        if (o instanceof Map) {
            return 1;
        }
        return 0;
    }
}
