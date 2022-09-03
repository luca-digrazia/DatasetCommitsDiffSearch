/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

public class OverloadedTest extends ProxyLanguageEnvTest {
    public static final class Data {
        public int x;

        public void x(int value) {
            this.x = value * 2;
        }

        public double x() {
            return this.x * 2;
        }
    }

    public static final class Num {
        public Object x;
        public String parameter;

        public void x(int value) {
            this.x = value;
            this.parameter = "int";
        }

        public void x(Number value) {
            this.x = value;
            this.parameter = "Number";
        }

        public void x(BigInteger value) {
            this.x = value;
            this.parameter = "BigInteger";
        }

        public void d(int value) {
            this.x = value;
            this.parameter = "int";
        }

        public void d(double value) {
            this.x = value;
            this.parameter = "double";
        }

        public void f(int value) {
            this.x = value;
            this.parameter = "int";
        }

        public void f(float value) {
            this.x = value;
            this.parameter = "float";
        }
    }

    private TruffleObject obj;
    private Data data;

    @Before
    public void initObjects() {
        data = new Data();
        obj = asTruffleObject(data);
    }

    @Test
    public void threeProperties() {
        TruffleObject ret = HostInteropTest.sendKeys(obj);
        List<?> list = context.asValue(ret).as(List.class);
        assertEquals("Just one (overloaded) property: " + list, 1, list.size());
        assertEquals("x", list.get(0));
    }

    @Test
    public void readAndWriteField() {
        data.x = 11;
        assertEquals(11, HostInteropTest.message(Message.READ, obj, "x"));

        HostInteropTest.message(Message.WRITE, obj, "x", 12);
        assertEquals(12, data.x);

        HostInteropTest.message(Message.WRITE, obj, "x", new UnboxableToInt(13));
        assertEquals(13, data.x);
    }

    @Test
    public void callGetterAndSetter() {
        data.x = 11;
        assertEquals(22.0, HostInteropTest.message(Message.INVOKE, obj, "x"));

        HostInteropTest.message(Message.INVOKE, obj, "x", 10);
        assertEquals(20, data.x);

        HostInteropTest.message(Message.INVOKE, obj, "x", new UnboxableToInt(21));
        assertEquals(42, data.x);
    }

    @Test
    public void testOverloadingTruffleObjectArg() throws InteropException {
        Node n = Message.INVOKE.createNode();
        ForeignAccess.sendInvoke(n, obj, "x", new UnboxableToInt(21));
        assertEquals(42, data.x);
        ForeignAccess.sendInvoke(n, obj, "x", env.asBoxedGuestValue(10));
        assertEquals(20, data.x);
        ForeignAccess.sendInvoke(n, obj, "x", 10);
        assertEquals(20, data.x);
    }

    @Test
    public void testOverloadingNumber() throws InteropException {
        Node n = Message.INVOKE.createNode();
        Num num = new Num();
        TruffleObject numobj = asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "x", new UnboxableToInt(21));
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "x", asTruffleObject(new AtomicInteger(22)));
        assertEquals("Number", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "x", asTruffleObject(BigInteger.TEN));
        assertEquals("BigInteger", num.parameter);
    }

    @Test
    public void testVarArgs() throws InteropException {
        TruffleObject stringClass = asTruffleHostSymbol(String.class);
        assertEquals("bla", ForeignAccess.sendInvoke(Message.INVOKE.createNode(), stringClass, "format", "bla"));
        assertEquals("42", ForeignAccess.sendInvoke(Message.INVOKE.createNode(), stringClass, "format", "%d", 42));
        assertEquals("1337", ForeignAccess.sendInvoke(Message.INVOKE.createNode(), stringClass, "format", "%d%d", 13, 37));
    }

    public interface Identity<T> {
        T getId();
    }

    public interface SomeThingWithIdentity extends Identity<Integer> {
        @Override
        Integer getId();
    }

    public static class ActualRealThingWithIdentity implements SomeThingWithIdentity {
        Integer id = 42;

        @Override
        public Integer getId() {
            return id;
        }
    }

    @Test
    public void testGenericReturnTypeBridgeMethod() throws InteropException {
        TruffleObject thing = asTruffleObject(new ActualRealThingWithIdentity());
        assertEquals(42, ForeignAccess.sendInvoke(Message.INVOKE.createNode(), thing, "getId"));
    }

    @Test
    public void testWidening() throws InteropException {
        Node n = Message.INVOKE.createNode();
        Num num = new Num();
        TruffleObject numobj = asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "d", (byte) 42);
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", (short) 42);
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", 42);
        assertEquals("int", num.parameter);

        ForeignAccess.sendInvoke(n, numobj, "d", 42.1f);
        assertEquals("double", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", 42.1d);
        assertEquals("double", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", 0x8000_0000L);
        assertEquals("double", num.parameter);

        ForeignAccess.sendInvoke(n, numobj, "d", 42L);
        assertEquals("int", num.parameter);

        ForeignAccess.sendInvoke(n, numobj, "f", 42L);
        assertEquals("int", num.parameter);
    }

    @Test
    public void testNarrowing() throws InteropException {
        Node n = Message.INVOKE.createNode();
        Num num = new Num();
        TruffleObject numobj = asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "f", 42.5f);
        assertEquals("float", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "f", 42.5d);
        assertEquals("float", num.parameter);
    }

    @Test
    public void testPrimitive() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject sample = asTruffleObject(new Sample());
        for (int i = 0; i < 2; i++) {
            assertEquals("int,boolean", ForeignAccess.sendInvoke(n, sample, "m1", 42, true));
            assertEquals("double,String", ForeignAccess.sendInvoke(n, sample, "m1", 42, "asdf"));
        }
        for (int i = 0; i < 2; i++) {
            assertEquals("int,boolean", ForeignAccess.sendInvoke(n, sample, "m1", 42, true));
            assertEquals("double,Object", ForeignAccess.sendInvoke(n, sample, "m1", 4.2, true));
        }
    }

    @SuppressWarnings("unused")
    public static class Sample {
        public Object m1(int a0, boolean a1) {
            return "int,boolean";
        }

        public Object m1(int a0, Boolean a1) {
            return "int,Boolean";
        }

        public Object m1(double a0, Object a1) {
            return "double,Object";
        }

        public Object m1(double a0, String a1) {
            return "double,String";
        }
    }

    @Test
    public void testClassVsInterface() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject pool = asTruffleObject(new Pool());
        TruffleObject concrete = asTruffleObject(new Concrete());
        TruffleObject handler = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        assertEquals(Concrete.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare1", "select", concrete, handler));
        assertEquals(Concrete.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare2", "select", handler, concrete));
    }

    @Test
    public void testClassVsInterface2() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject pool = asTruffleObject(new Pool());
        TruffleObject thandler = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        TruffleObject chandler = asTruffleObject(new CHander());
        assertEquals(CHander.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare3", "select", chandler, thandler));
        TruffleObject proxied = new AsCollectionsTest.MapBasedTO(Collections.singletonMap("handle", new FunctionalInterfaceTest.TestExecutable()));
        assertEquals(IHandler.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare3", "select", proxied, thandler));
    }

    @Test
    public void testClassVsInterface3() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject pool = asTruffleObject(new Pool());
        TruffleObject thandler = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        TruffleObject chandler = asTruffleObject(new CHander());
        TruffleObject concrete = asTruffleObject(new Concrete());
        assertEquals(IHandler.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare4", "select", chandler, 42));
        assertEquals(IHandler.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare4", "select", thandler, 42));
        assertEquals(Concrete.class.getName(), ForeignAccess.sendInvoke(n, pool, "prepare4", "select", concrete, 42));
    }

    @FunctionalInterface
    public interface IHandler<T> {
        void handle(T value);
    }

    public static class Concrete {
    }

    public static class CHander implements IHandler<String> {
        public void handle(String value) {
        }
    }

    public static class Pool {
        @SuppressWarnings("unused")
        public String prepare1(String query, Concrete concrete, IHandler<String> handler) {
            return Concrete.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare1(String query, Iterable<?> iterable, IHandler<String> handler) {
            return Iterable.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare2(String query, IHandler<String> handler, Concrete concrete) {
            return Concrete.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare2(String query, IHandler<String> handler, Iterable<?> iterable) {
            return Iterable.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare3(String query, CHander arg1, IHandler<String> arg2) {
            return CHander.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare3(String query, IHandler<String> arg1, IHandler<String> arg2) {
            return IHandler.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare4(String query, CHander arg1, String arg2) {
            return CHander.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare4(String query, IHandler<String> arg1, int arg2) {
            return IHandler.class.getName();
        }

        @SuppressWarnings("unused")
        public String prepare4(String query, Concrete arg1, String arg2) {
            return Concrete.class.getName();
        }
    }

    @Test
    public void testFunctionalVsNonFunctionalInterface() throws InteropException {
        Node n = Message.INVOKE.createNode();
        TruffleObject receiver = asTruffleObject(new PreferSAM());
        TruffleObject executable = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        assertEquals("SAM", ForeignAccess.sendInvoke(n, receiver, "overloaded1", executable));
        assertEquals("SAM", ForeignAccess.sendInvoke(n, receiver, "overloaded2", executable));

        // ambiguous (we do not take the object's members into consideration)
        TruffleObject keysObject = asTruffleObject(new HostInteropTest.HasKeysObject(true));
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendInvoke(n, receiver, "overloaded1", keysObject), UnsupportedTypeException.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendInvoke(n, receiver, "overloaded2", keysObject), UnsupportedTypeException.class);
    }

    public interface TwoMethods {
        String one();

        String two();
    }

    @SuppressWarnings("unused")
    public static class PreferSAM {
        public String overloaded1(Supplier<String> one) {
            assertEquals(FunctionalInterfaceTest.EXPECTED_RESULT, one.get());
            return "SAM";
        }

        public String overloaded1(TwoMethods two) {
            return TwoMethods.class.getName();
        }

        public String overloaded2(FunctionalInterfaceTest.LegacyFunctionalInterface<String> one) {
            assertEquals(FunctionalInterfaceTest.EXPECTED_RESULT, one.get());
            return "SAM";
        }

        public String overloaded2(TwoMethods two) {
            return TwoMethods.class.getName();
        }
    }
}
