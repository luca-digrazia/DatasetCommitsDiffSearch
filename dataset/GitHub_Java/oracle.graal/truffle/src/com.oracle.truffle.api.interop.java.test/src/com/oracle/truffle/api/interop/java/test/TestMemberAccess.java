/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

// Checkstyle: stop line length check
public class TestMemberAccess {

    private final String TEST_CLASS = TestClass.class.getName();

    private final Node CREATE_NEW_NODE = Message.createNew(0).createNode();
    private final Node EXEC_NODE = Message.createExecute(0).createNode();
    private final Node UNBOX_NODE = Message.UNBOX.createNode();
    private final Node IS_BOXED_NODE = Message.IS_BOXED.createNode();
    private final Node IS_NULL_NODE = Message.IS_NULL.createNode();
    private final Node IS_EXEC_NODE = Message.IS_EXECUTABLE.createNode();
    private final Node READ_NODE = Message.READ.createNode();

    @Test
    public void testFields() throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, UnsupportedTypeException, ArityException, UnsupportedMessageException, InteropException {
        TestClass t = new TestClass();
        Field[] fields = t.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            String name = f.getName();
            if (name.startsWith("field")) {
                boolean isPublic = (f.getModifiers() & Modifier.PUBLIC) != 0;
                boolean wasUIE = false;
                try {
                    testForValue(name, f.get(t));
                } catch (UnknownIdentifierException e) {
                    if (isPublic) {
                        throw e;
                    }
                    wasUIE = true;
                }
                if (!isPublic && !wasUIE) {
                    fail("expected UnknownIdentifierException when accessing field: " + name);
                }
            }
        }
    }

    @Test
    public void testMethods()
                    throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, UnsupportedTypeException, ArityException, UnsupportedMessageException, InteropException {
        TestClass t = new TestClass();
        Method[] methods = t.getClass().getDeclaredMethods();
        for (Method m : methods) {
            if (m.getParameterCount() == 0) {
                m.setAccessible(true);
                String name = m.getName();
                if (name.startsWith("method")) {
                    boolean isPublic = (m.getModifiers() & Modifier.PUBLIC) != 0;
                    boolean wasUIE = false;
                    try {
                        testForValue(name, m.invoke(t));
                    } catch (UnknownIdentifierException e) {
                        if (isPublic) {
                            throw e;
                        }
                        wasUIE = true;
                    }
                    if (!isPublic && !wasUIE) {
                        fail("expected UnknownIdentifierException when accessing method: " + name);
                    }
                }
            }
        }
    }

    @Test
    public void testAllTypes() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        getValueForAllTypesMethod("allTypesMethod");
        getValueForAllTypesMethod("allTypesStaticMethod");
    }

    @Test
    public void testNullParameter() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        Object o = getValueFromMember("isNull", new Object[]{JavaInterop.asTruffleObject(null)});
        assertEquals(Long.class.getName(), o);
        fail("there are two isNull methods - isNull(String) and isNull(Long), but how to differ between calling isNull((String) null) or isNull((Long)null)");
    }

    @Test
    public void testOverloaded() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("boolean", getValueFromMember("isOverloaded", true));
        assertEquals(Boolean.class.getName(), getValueFromMember("isOverloaded", Boolean.TRUE));
        assertEquals("byte", getValueFromMember("isOverloaded", Byte.MAX_VALUE));
        assertEquals(Byte.class.getName(), getValueFromMember("isOverloaded", new Byte(Byte.MAX_VALUE)));
        assertEquals("char", getValueFromMember("isOverloaded", 'a'));
        assertEquals(Character.class.getName(), getValueFromMember("isOverloaded", new Character('a')));
        assertEquals("float", getValueFromMember("isOverloaded", Float.MAX_VALUE));
        assertEquals(Float.class.getName(), getValueFromMember("isOverloaded", new Float(Float.MAX_VALUE)));
        assertEquals("double", getValueFromMember("isOverloaded", Double.MAX_VALUE));
        assertEquals(Double.class.getName(), getValueFromMember("isOverloaded", new Double(Double.MAX_VALUE)));
        assertEquals("int", getValueFromMember("isOverloaded", Integer.MAX_VALUE));
        assertEquals(Integer.class.getName(), getValueFromMember("isOverloaded", new Integer(Integer.MAX_VALUE)));
        assertEquals("long", getValueFromMember("isOverloaded", Long.MAX_VALUE));
        assertEquals(Long.class.getName(), getValueFromMember("isOverloaded", new Long(Long.MAX_VALUE)));
        assertEquals("short", getValueFromMember("isOverloaded", Short.MAX_VALUE));
        assertEquals(Short.class.getName(), getValueFromMember("isOverloaded", new Short(Short.MAX_VALUE)));
        assertEquals(String.class.getName(), getValueFromMember("isOverloaded", "testString"));
    }

    @Test
    public void testClassAsArg() throws ClassNotFoundException, InteropException {
        TruffleObject clazz = JavaInterop.asTruffleObject(String.class);
        assertEquals(String.class.getName(), getValueFromMember("classAsArg", clazz));
    }

    @Test
    public void testNewArray() throws ClassNotFoundException, InteropException {
        TruffleObject arrayClass = JavaInterop.asTruffleObject(Array.class);
        TruffleObject newInstanceMethod = (TruffleObject) ForeignAccess.send(READ_NODE, arrayClass, "newInstance");
        TruffleObject stringArray = (TruffleObject) ForeignAccess.sendExecute(EXEC_NODE, newInstanceMethod, JavaInterop.asTruffleObject(String.class), 2);
        assertTrue(JavaInterop.isArray(stringArray));
    }

    private void testForValue(String name, Object value) throws ClassNotFoundException, UnsupportedTypeException, ArityException, UnsupportedMessageException, InteropException {
        Object o = getValueFromMember(name);
        if (value == null) {
            if (o == null || (o instanceof TruffleObject && ForeignAccess.sendIsNull(IS_NULL_NODE, (TruffleObject) o))) {
                return;
            }
        }
        assertEquals(value, o);
    }

    private Object getValueFromMember(String name, Object... parameters) throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        TruffleObject clazz = JavaInterop.asTruffleObject(Class.forName(TEST_CLASS));
        Object o = ForeignAccess.sendNew(CREATE_NEW_NODE, clazz);
        try {
            o = ForeignAccess.sendRead(READ_NODE, (TruffleObject) o, name);
        } catch (UnknownIdentifierException e) {
            o = ForeignAccess.sendRead(READ_NODE, clazz, name);
        }
        if (ForeignAccess.sendIsExecutable(IS_EXEC_NODE, (TruffleObject) o)) {
            o = ForeignAccess.sendExecute(EXEC_NODE, (TruffleObject) o, parameters);
        }
        if (o instanceof TruffleObject && ForeignAccess.sendIsBoxed(IS_BOXED_NODE, (TruffleObject) o)) {
            o = ForeignAccess.sendUnbox(UNBOX_NODE, (TruffleObject) o);
        }
        return o;
    }

    private void getValueForAllTypesMethod(String method) throws ClassNotFoundException, InteropException {
        boolean bo = true;
        byte bt = 127;
        char c = 'a';
        short sh = 1234;
        int i = Integer.MAX_VALUE;
        long l = Long.MAX_VALUE;
        double d = Double.MAX_VALUE;
        float f = Float.MAX_VALUE;
        String s = "testString";
        Object o = getValueFromMember(method, new Object[]{bo, bt, c, sh, i, l, d, f, s});
        assertEquals("" + bo + bt + c + sh + i + l + d + f + s, o);
    }

    public static class TestClass {

        public static boolean fieldStaticBoolean = true;
        public static byte fieldStaticByte = Byte.MAX_VALUE;
        public static char fieldStaticChar = 'a';
        public static short fieldStaticShort = Short.MAX_VALUE;
        public static int fieldStaticInteger = Integer.MAX_VALUE;
        public static long fieldStaticLong = Long.MAX_VALUE;
        public static double fieldStaticDouble = 1.1;
        public static float fieldStaticFloat = 1.1f;
        public static String fieldStaticString = "a static string";

        public boolean fieldBoolean = true;
        public byte fieldByte = Byte.MAX_VALUE;
        public char fieldChar = 'a';
        public short fieldShort = Short.MAX_VALUE;
        public int fieldInteger = Integer.MAX_VALUE;
        public long fieldLong = Long.MAX_VALUE;
        public double fieldDouble = 1.1;
        public float fieldFloat = 1.1f;
        public String fieldString = "a string";

        public static Boolean fieldStaticBooleanObject = true;
        public static Byte fieldStaticByteObject = Byte.MAX_VALUE;
        public static Character fieldStaticCharObject = 'a';
        public static Short fieldStaticShortObject = Short.MAX_VALUE;
        public static Integer fieldStaticIntegerObject = Integer.MAX_VALUE;
        public static Long fieldStaticLongObject = Long.MAX_VALUE;
        public static Double fieldStaticDoubleObject = Double.MAX_VALUE;
        public static Float fieldStaticFloatObject = Float.MAX_VALUE;

        public Boolean fieldBooleanObject = true;
        public Byte fieldByteObject = Byte.MAX_VALUE;
        public Character fieldCharObject = 'a';
        public Short fieldShortObject = Short.MAX_VALUE;
        public Integer fieldIntegerObject = Integer.MAX_VALUE;
        public Long fieldLongObject = Long.MAX_VALUE;
        public Double fieldDoubleObject = Double.MAX_VALUE;
        public Float fieldFloatObject = Float.MAX_VALUE;

        public static Double fieldStaticNaNObject = Double.NaN;
        public static double fieldStaticNaN = Double.NaN;

        private static String fieldPrivateStaticString = "private static string";
        protected static String fieldProtectedStaticString = "protected static string";
        static String fieldPackageStaticString = "package static string";
        private String fieldPrivateString = "private string";
        protected String fieldProtectedString = "protected string";
        String fieldPackageString = "package string";

        private static int fieldPrivateStaticInt = Integer.MAX_VALUE;
        protected static int fieldProtectedStaticInt = Integer.MAX_VALUE;
        static int fieldPackageStaticInt = Integer.MAX_VALUE;
        private int fieldPrivateInt = Integer.MAX_VALUE;
        protected int fieldProtectedInt = Integer.MAX_VALUE;
        int fieldPackageInt = Integer.MAX_VALUE;

        public static boolean methodStaticBoolean() {
            return true;
        }

        public static byte methodStaticByte() {
            return Byte.MAX_VALUE;
        }

        public static char methodStaticChar() {
            return 'a';
        }

        public static short methodStaticShort() {
            return Short.MAX_VALUE;
        }

        public static int methodStaticInteger() {
            return Integer.MAX_VALUE;
        }

        public static long methodStaticLong() {
            return Long.MAX_VALUE;
        }

        public static double methodStaticDouble() {
            return Double.MAX_VALUE;
        }

        public static float methodStaticFloat() {
            return Float.MAX_VALUE;
        }

        public static String methodStaticString() {
            return "a static string";
        }

        public boolean methodBoolean() {
            return true;
        }

        public byte methodByte() {
            return Byte.MAX_VALUE;
        }

        public char methodChar() {
            return 'a';
        }

        public short methodShort() {
            return Short.MAX_VALUE;
        }

        public int methodInteger() {
            return Integer.MAX_VALUE;
        }

        public long methodLong() {
            return Long.MAX_VALUE;
        }

        public double methodDouble() {
            return Double.MAX_VALUE;
        }

        public float methodFloat() {
            return Float.MAX_VALUE;
        }

        public String methodString() {
            return "string";
        }

        public static Boolean methodStaticBooleanObject() {
            return true;
        }

        public static Byte methodStaticByteObject() {
            return Byte.MAX_VALUE;
        }

        public static Character methodStaticCharObject() {
            return 'a';
        }

        public static Short methodStaticShortObject() {
            return Short.MAX_VALUE;
        }

        public static Integer methodStaticIntegerObject() {
            return Integer.MAX_VALUE;
        }

        public static Long methodStaticLongObject() {
            return Long.MAX_VALUE;
        }

        public static Double methodStaticDoubleObject() {
            return Double.MAX_VALUE;
        }

        public static Float methodStaticFloatObject() {
            return Float.MAX_VALUE;
        }

        public Boolean methodBooleanObject() {
            return true;
        }

        public Byte methodByteObject() {
            return Byte.MAX_VALUE;
        }

        public Character methodCharObject() {
            return 'a';
        }

        public Short methodShortObject() {
            return Short.MAX_VALUE;
        }

        public Integer methodIntegerObject() {
            return Integer.MAX_VALUE;
        }

        public Long methodLongObject() {
            return Long.MAX_VALUE;
        }

        public Double methodDoubleObject() {
            return Double.MAX_VALUE;
        }

        public Float methodFloatObject() {
            return Float.MAX_VALUE;
        }

        public static Object methodStaticReturnsNull() {
            return null;
        }

        public Object methodReturnsNull() {
            return null;
        }

        private static String methodPrivateStaticString() {
            return fieldPrivateStaticString;
        }

        protected static String methodProtectedStaticString() {
            return fieldProtectedStaticString;
        }

        static String methodPackageStaticString() {
            return fieldPackageStaticString;
        }

        private String methodPrivateString() {
            return fieldPrivateString;
        }

        protected String methodProtectedString() {
            return fieldProtectedString;
        }

        String methodPackageString() {
            return fieldPackageString;
        }

        private static int methodPrivateStaticInt() {
            return fieldPrivateStaticInt;
        }

        protected static int methodProtectedStaticInt() {
            return fieldProtectedStaticInt;
        }

        static int methodPackageStaticInt() {
            return fieldPackageStaticInt;
        }

        private int methodPrivateInt() {
            return fieldPrivateInt;
        }

        protected int methodProtectedInt() {
            return fieldProtectedInt;
        }

        int methodPackageInt() {
            return fieldPackageInt;
        }

        public static Object fieldStaticNullObject = null;
        public Object fieldNullObject = null;

        public String allTypesMethod(boolean bo, byte bt, char ch, short sh, int in, long lo, double db, float fl, String st) {
            return "" + bo + bt + ch + sh + in + lo + db + fl + st;
        }

        public static Object allTypesStaticMethod(boolean bo, byte bt, char ch, short sh, int in, long lo, double db, float fl, String st) {
            return "" + bo + bt + ch + sh + in + lo + db + fl + st;
        }

        public String isNull(String s) {
            return s == null ? String.class.getName() : null;
        }

        public String isNull(Long l) {
            return l == null ? Long.class.getName() : null;
        }

        public String isOverloaded(boolean b) {
            return "boolean";
        }

        public String isOverloaded(Boolean b) {
            return Boolean.class.getName();
        }

        public String isOverloaded(byte b) {
            return "byte";
        }

        public String isOverloaded(Byte b) {
            return Byte.class.getName();
        }

        public String isOverloaded(char c) {
            return "char";
        }

        public String isOverloaded(Character c) {
            return Character.class.getName();
        }

        public String isOverloaded(double l) {
            return "double";
        }

        public String isOverloaded(Double l) {
            return Double.class.getName();
        }

        public String isOverloaded(Float f) {
            return Float.class.getName();
        }

        public String isOverloaded(float f) {
            return "float";
        }

        public String isOverloaded(int c) {
            return "int";
        }

        public String isOverloaded(Integer c) {
            return Integer.class.getName();
        }

        public String isOverloaded(long l) {
            return "long";
        }

        public String isOverloaded(Long l) {
            return Long.class.getName();
        }

        public String isOverloaded(short c) {
            return "short";
        }

        public String isOverloaded(Short c) {
            return Short.class.getName();
        }

        public String isOverloaded(String s) {
            return String.class.getName();
        }

        public String classAsArg(Class c) {
            return c.getName();
        }

    }
}
