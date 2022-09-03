/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLFunction;
import java.util.List;

public class SLJavaInteropTest {

    private PolyglotEngine engine;
    private ByteArrayOutputStream os;

    @Before
    public void create() {
        os = new ByteArrayOutputStream();
        engine = PolyglotEngine.newBuilder().setOut(os).build();
    }

    @After
    public void dispose() {
        engine.dispose();
    }

    @Test
    public void asFunction() throws Exception {
        String scriptText = "function test() {\n" + "    println(\"Called!\");\n" + "}\n";
        Source script = Source.newBuilder(scriptText).name("Test").mimeType(SLLanguage.MIME_TYPE).build();
        engine.eval(script);
        PolyglotEngine.Value main = engine.findGlobalSymbol("test");
        final Object value = main.get();
        assertTrue("It's truffle object", value instanceof TruffleObject);
        SLFunction rawFunction = main.as(SLFunction.class);
        assertNotNull("One can get the type of the inner Truffle Object", rawFunction);
        Runnable runnable = JavaInterop.asJavaFunction(Runnable.class, (TruffleObject) value);
        runnable.run();

        assertEquals("Called!\n", os.toString("UTF-8"));
    }

    @Test
    public void asFunctionWithArg() throws Exception {
        String scriptText = "function values(a, b) {\n" + //
                        "  println(\"Called with \" + a + \" and \" + b);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");
        final Object value = fn.get();
        assertTrue("It's truffle object", value instanceof TruffleObject);
        PassInValues valuesIn = JavaInterop.asJavaFunction(PassInValues.class, (TruffleObject) value);
        valuesIn.call("OK", "Fine");

        assertEquals("Called with OK and Fine\n", os.toString("UTF-8"));
    }

    interface PassInValues {
        void call(Object a, Object b);
    }

    @Test
    public void asFunctionWithArr() throws Exception {
        String scriptText = "function values(a, b) {\n" + //
                        "  println(\"Called with \" + a[0] + a[1] + \" and \" + b);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");
        final Object value = fn.get();
        assertTrue("It's truffle object", value instanceof TruffleObject);
        PassInArray valuesIn = JavaInterop.asJavaFunction(PassInArray.class, (TruffleObject) value);

        valuesIn.call(new Object[]{"OK", "Fine"});
        assertEquals("Called with OKFine and null\n", os.toString("UTF-8"));
    }

    @Test
    public void asFunctionWithVarArgs() throws Exception {
        String scriptText = "function values(a, b) {\n" + //
                        "  println(\"Called with \" + a + \" and \" + b);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");
        final Object value = fn.get();
        assertTrue("It's truffle object", value instanceof TruffleObject);
        PassInVarArg valuesIn = JavaInterop.asJavaFunction(PassInVarArg.class, (TruffleObject) value);

        valuesIn.call("OK", "Fine");
        assertEquals("Called with OK and Fine\n", os.toString("UTF-8"));
    }

    @Test
    public void asFunctionWithArgVarArgs() throws Exception {
        String scriptText = "function values(a, b, c) {\n" + //
                        "  println(\"Called with \" + a + \" and \" + b + c);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");
        final Object value = fn.get();
        assertTrue("It's truffle object", value instanceof TruffleObject);
        PassInArgAndVarArg valuesIn = JavaInterop.asJavaFunction(PassInArgAndVarArg.class, (TruffleObject) value);

        valuesIn.call("OK", "Fine", "Well");
        assertEquals("Called with OK and FineWell\n", os.toString("UTF-8"));
    }

    @Test
    public void sumPairs() {
        String scriptText = "function values(sum, k, v) {\n" + //
                        "  obj = new();\n" + //
                        "  obj.key = k;\n" + //
                        "  obj.value = v;\n" + //
                        "  sum.sum(obj);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");

        Sum javaSum = new Sum();
        Object sum = javaSum;
        fn.execute(sum, "one", 1);
        fn.execute(sum, "two", 2);
        fn.execute(sum, "three", 3);

        assertEquals(6, javaSum.sum);
    }

    @Test
    public void sumPairsInArray() {
        String scriptText = "function values(sum, arr) {\n" + //
                        "  sum.sumArray(arr);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");

        Sum javaSum = new Sum();

        PairImpl[] arr = {
                        new PairImpl("one", 1),
                        new PairImpl("two", 2),
                        new PairImpl("three", 3),
        };
        TruffleObject truffleArr = JavaInterop.asTruffleObject(arr);
        fn.execute(javaSum, truffleArr);
        assertEquals(6, javaSum.sum);
    }

    @Test
    public void sumPairsInArrayOfArray() {
        String scriptText = "function values(sum, arr) {\n" + //
                        "  sum.sumArrayArray(arr);\n" + //
                        "}\n"; //
        Source script = Source.newBuilder(scriptText).name("Test").mimeType("application/x-sl").build();
        engine.eval(script);
        PolyglotEngine.Value fn = engine.findGlobalSymbol("values");

        Sum javaSum = new Sum();

        PairImpl[][] arr = {
                        new PairImpl[]{
                                        new PairImpl("one", 1),
                        },
                        new PairImpl[]{
                                        new PairImpl("two", 2),
                                        new PairImpl("three", 3),
                        }
        };
        TruffleObject truffleArr = JavaInterop.asTruffleObject(arr);
        fn.execute(javaSum, truffleArr);
        assertEquals(6, javaSum.sum);
    }

    interface PassInArray {
        void call(Object[] arr);
    }

    interface PassInVarArg {
        void call(Object... arr);
    }

    interface PassInArgAndVarArg {
        void call(Object first, Object... arr);
    }

    public interface Pair {
        String key();

        int value();
    }

    public static final class PairImpl {
        public final String key;
        public final int value;

        PairImpl(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class Sum {
        int sum;

        public void sum(Pair p) {
            sum += p.value();
        }

        public void sumArray(List<Pair> pairs) {
            Object[] arr = pairs.toArray();
            assertNotNull("Array created", arr);
            for (Pair p : pairs) {
                sum(p);
            }
        }

        public void sumArrayArray(List<List<Pair>> pairs) {
            Object[] arr = pairs.toArray();
            assertNotNull("Array created", arr);
            assertEquals("Two lists", 2, arr.length);
            for (List<Pair> list : pairs) {
                sumArray(list);
            }
        }
    }
}
