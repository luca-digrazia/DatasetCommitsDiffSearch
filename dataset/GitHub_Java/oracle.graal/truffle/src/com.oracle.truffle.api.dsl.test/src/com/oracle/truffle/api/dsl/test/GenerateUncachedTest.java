/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.TestNonStaticSpecializationNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached1NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached2NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached3NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached4NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached5NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached6NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.Uncached7NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial1NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial2NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial3NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial4NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial5NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial6NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateUncachedTestFactory.UncachedTrivial7NodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class GenerateUncachedTest {

    @GenerateUncached
    abstract static class Uncached1Node extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "v == cachedV")
        static String s1(int v, @Cached("v") int cachedV) {
            return "s1";
        }

        @Specialization
        static String s2(double v) {
            return "s2";
        }

    }

    @Test
    public void testUncached1() {
        Uncached1Node node = Uncached1NodeGen.getUncached();
        assertEquals("s1", node.execute(42));
        assertEquals("s2", node.execute(42d));
        try {
            node.execute(42L);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @GenerateUncached
    abstract static class Uncached2Node extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "v == cachedV")
        static String s1(int v,
                        @Cached("v") int cachedV) {
            return "s1";
        }

        @Specialization(replaces = "s1")
        static String s2(int v) {
            return "s2";
        }
    }

    @Test
    public void testUncached2() {
        Uncached2Node node = Uncached2NodeGen.getUncached();
        assertEquals("s2", node.execute(42));
        assertEquals("s2", node.execute(43));
    }

    @GenerateUncached
    abstract static class Uncached3Node extends Node {

        static boolean guard;

        abstract Object execute(Object arg);

        @Specialization(guards = "guard")
        static String s1(int v) {
            return "s1";
        }

        @Specialization
        static String s2(int v) {
            return "s2";
        }
    }

    @Test
    public void testUncached3() {
        Uncached3Node node = Uncached3NodeGen.getUncached();
        assertEquals("s2", node.execute(42));
        Uncached3Node.guard = true;
        assertEquals("s1", node.execute(42));
    }

    @GenerateUncached
    abstract static class Uncached4Node extends Node {

        abstract Object execute(Object arg);

        @Specialization(rewriteOn = ArithmeticException.class)
        static String s1(int v) {
            if (v == 42) {
                throw new ArithmeticException();
            }
            return "s1";
        }

        @Specialization(replaces = "s1")
        static String s2(int v) {
            return "s2";
        }
    }

    @Test
    public void testUncached4() {
        Uncached4Node node = Uncached4NodeGen.getUncached();
        assertEquals("s2", node.execute(42));
    }

    @GenerateUncached
    abstract static class Uncached5Node extends Node {

        abstract Object execute(Object arg);

        static Assumption testAssumption = Truffle.getRuntime().createAssumption();

        @Specialization(assumptions = "testAssumption")
        static String s1(int v) {
            return "s1";
        }

        @Specialization
        static String s2(int v) {
            return "s2";
        }
    }

    @Test
    public void testUncached5() {
        Uncached5Node node = Uncached5NodeGen.getUncached();
        assertEquals("s1", node.execute(42));
        Uncached5Node.testAssumption.invalidate();
        assertEquals("s2", node.execute(42));
        Uncached5Node.testAssumption = null;
        assertEquals("s1", node.execute(42));
    }

    @TypeSystem
    static class Uncached6TypeSystem {

        @ImplicitCast
        public static long fromInt(int l) {
            return l;
        }

    }

    @TypeSystemReference(Uncached6TypeSystem.class)
    @GenerateUncached
    abstract static class Uncached6Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(long v) {
            return "s1";
        }
    }

    @Test
    public void testUncached6() {
        Uncached6Node node = Uncached6NodeGen.getUncached();
        assertEquals("s1", node.execute(42));
        assertEquals("s1", node.execute(42L));
        assertEquals("s1", node.execute(42L));
    }

    @GenerateUncached
    abstract static class Uncached7Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(int v) {
            return "s1";
        }

        @Specialization
        static String s2(double v) {
            return "s2";
        }

        @Fallback
        static String fallback(Object v) {
            return "fallback";
        }
    }

    @Test
    public void testUncached7() {
        Uncached7Node node = Uncached7NodeGen.getUncached();
        assertEquals("s1", node.execute(42));
        assertEquals("s2", node.execute(42d));
        assertEquals("fallback", node.execute(42f));
    }

    @GenerateUncached
    abstract static class UncachedTrivial1Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(int v, @Cached("v") int cachedV) {
            return "s1_" + cachedV;
        }
    }

    @Test
    public void testUncachedTrivial1() {
        UncachedTrivial1Node node = UncachedTrivial1NodeGen.getUncached();
        assertEquals("s1_42", node.execute(42));
        assertEquals("s1_43", node.execute(43));
    }

    @GenerateUncached
    abstract static class UncachedTrivial2Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v, @Cached("v.getClass()") Class<?> cachedV) {
            return "s1_" + cachedV.getSimpleName();
        }
    }

    @Test
    public void testUncachedTrivial2() {
        UncachedTrivial2Node node = UncachedTrivial2NodeGen.getUncached();
        assertEquals("s1_Integer", node.execute(42));
        assertEquals("s1_Double", node.execute(42d));
    }

    @GenerateUncached
    abstract static class UncachedTrivial3Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(int v, @Cached("v == 42") boolean cachedV) {
            return "s1_" + cachedV;
        }
    }

    @Test
    public void testUncachedTrivial3() {
        UncachedTrivial3Node node = UncachedTrivial3NodeGen.getUncached();
        assertEquals("s1_true", node.execute(42));
        assertEquals("s1_false", node.execute(43));
    }

    @GenerateUncached
    abstract static class UncachedTrivial4Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v, @Cached("v == null") boolean cachedV) {
            return "s1_" + cachedV;
        }
    }

    @Test
    public void testUncachedTrivial4() {
        UncachedTrivial4Node node = UncachedTrivial4NodeGen.getUncached();
        assertEquals("s1_false", node.execute(42));
        assertEquals("s1_true", node.execute(null));
    }

    @GenerateUncached
    abstract static class UncachedTrivial5Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v,
                        @Cached(value = "foo(v)", allowUncached = true) boolean cached) {
            return "s1_" + cached;
        }

        static boolean foo(Object o) {
            return o == Integer.valueOf(42);
        }

    }

    @Test
    public void testUncachedTrivial5() {
        UncachedTrivial5Node node = UncachedTrivial5NodeGen.getUncached();
        assertEquals("s1_true", node.execute(42));
        assertEquals("s1_false", node.execute(43));
    }

    @GenerateUncached
    abstract static class UncachedTrivial6Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v,
                        @Cached(value = "foo(v)", uncached = "foo(null)") boolean cached) {
            return "s1_" + cached;
        }

        static boolean foo(Object o) {
            return o == Integer.valueOf(42);
        }

    }

    @Test
    public void testUncachedTrivial6() {
        UncachedTrivial6Node node = UncachedTrivial6NodeGen.getUncached();
        assertEquals("s1_false", node.execute(42));
        assertEquals("s1_false", node.execute(43));
    }

    @GenerateUncached
    abstract static class UncachedTrivial7Node extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v,
                        @Cached(value = "v == null") boolean cached) {
            return "s1_" + cached;
        }

        static boolean foo(Object o) {
            return o == Integer.valueOf(42);
        }

    }

    @Test
    public void testUncachedTrivial7() {
        UncachedTrivial7Node node = UncachedTrivial7NodeGen.getUncached();
        assertEquals("s1_true", node.execute(null));
        assertEquals("s1_false", node.execute(43));
    }

    @GenerateUncached
    public abstract static class TestNonStaticSpecializationNode extends Node {

        public abstract Object execute(Object arg);

        @Specialization
        protected String s0(int arg) {
            return "s0";
        }

        @Override
        protected boolean isAdoptable() {
            return super.isAdoptable();
        }
    }

    @Test
    public void testNonStaticSpecialization() {
        TestNonStaticSpecializationNode node = TestNonStaticSpecializationNodeGen.getUncached();
        assertEquals("s0", node.execute(42));
        assertFalse(node.isAdoptable());
    }

    @GenerateUncached
    @ExpectError("Failed to generate code for @GenerateUncached: The node must not declare any instance variables. Found instance variable ErrorNode1.field. Remove instance variable to resolve this.")
    abstract static class ErrorNode1 extends Node {

        Object field;

        abstract Object execute(Object arg);

        @Specialization
        static int f0(int v) {
            return v;
        }

    }

    @GenerateUncached
    @ExpectError("Failed to generate code for @GenerateUncached: The node must not declare any instance variables. Found instance variable ErrorNode1.field. Remove instance variable to resolve this.")
    abstract static class ErrorNode1_Sub extends ErrorNode1 {

        @Specialization
        static double f1(double v) {
            return v;
        }

    }

    @GenerateUncached
    abstract static class ErrorNode2 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static int f0(int v,
                        @ExpectError("Failed to generate code for @GenerateUncached: The specialization uses @Cached without valid uncached expression. " +
                                        "Error parsing expression 'getUncached()': The method getUncached is undefined for the enclosing scope.. " +
                                        "To resolve this specify the uncached or allowUncached attribute in @Cached.") //
                        @Cached("nonTrivialCache(v)") int cachedV) {
            return v;
        }

        int nonTrivialCache(int v) {
            // we cannot know this is trivial
            return v;
        }

    }

    @GenerateUncached
    abstract static class ErrorNode3 extends Node {

        abstract Object execute(Object arg);

        @ExpectError("Failed to generate code for @GenerateUncached: The specialization must declare the modifier static. Add a static modifier to the method to resolve this.")
        @Specialization
        int f0(int v) {
            return v;
        }

    }

    @GenerateUncached
    abstract static class ErrorNode4 extends Node {

        abstract Object execute(Object arg);

        @ExpectError("Failed to generate code for @GenerateUncached: One of the guards bind non-static methods or fields . Add a static modifier to the bound guard method or field to resolve this.")
        @Specialization(guards = "g0(v)")
        static int f0(int v) {
            return v;
        }

        boolean g0(int v) {
            return v == 42;
        }

    }

    @ExpectError("Failed to generate code for @GenerateUncached: The node must not declare any instance variables. Found instance variable ErrorNode5.guard. Remove instance variable to resolve this.")
    @GenerateUncached
    abstract static class ErrorNode5 extends Node {

        abstract Object execute(Object arg);

        boolean guard;

        @ExpectError("Failed to generate code for @GenerateUncached: One of the guards bind non-static methods or fields . Add a static modifier to the bound guard method or field to resolve this.")
        @Specialization(guards = "guard")
        static int f0(int v) {
            return v;
        }

    }

    abstract static class BaseNode extends Node {

        abstract Object execute();

    }

    @ExpectError("Failed to generate code for @GenerateUncached: The node must not declare any @NodeChild annotations. Remove these annotations to resolve this.")
    @GenerateUncached
    @NodeChild(type = BaseNode.class)
    abstract static class ErrorNode6 extends Node {

        abstract Object execute(Object arg);

        abstract Object execute();

        @Specialization
        static int f0(int v) {
            return v;
        }

    }

    @GenerateUncached
    abstract static class ErrorNode7 extends Node {

        abstract Object execute(Object arg);

        @ExpectError("Failed to generate code for @GenerateUncached: The specialization rewrites on exceptions and there is no specialization that replaces it. Add a replaces=\"s1\" class to specialization below to resolve this problem.")
        @Specialization(rewriteOn = ArithmeticException.class)
        static String s1(int v) {
            return "s1";
        }

        @Specialization
        static String s2(int v) {
            return "s2";
        }
    }

    @GenerateUncached
    abstract static class ErrorNonTrivialNode1 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v,
                        @ExpectError("Failed to generate code for @GenerateUncached: The specialization uses @Cached without valid uncached expression. " +
                                        "Error parsing expression 'getUncached()': The method getUncached is undefined for the enclosing scope.. " +
                                        "To resolve this specify the uncached or allowUncached attribute in @Cached.")//
                        @Cached("foo(v)") Object cached) {
            return "s1";
        }

        static Object foo(Object o) {
            return o;
        }

    }

    @GenerateUncached
    abstract static class ErrorNonTrivialNode2 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        static String s1(Object v,
                        @ExpectError("The attributes 'allowUncached' and 'uncached' are mutually exclusive. Remove one of the attributes to resolve this.") //
                        @Cached(value = "foo(v)", allowUncached = true, uncached = "foo(v)") Object cached) {
            return "s1";
        }

        static Object foo(Object o) {
            return o;
        }

    }

}
