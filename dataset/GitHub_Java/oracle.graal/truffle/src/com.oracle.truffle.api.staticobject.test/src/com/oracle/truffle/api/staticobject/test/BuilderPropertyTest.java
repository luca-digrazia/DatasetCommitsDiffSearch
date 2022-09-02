/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject.test;

import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticPropertyKind;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

@RunWith(Theories.class)
public class BuilderPropertyTest extends StaticObjectModelTest {
    @DataPoints //
    public static TestEnvironment[] environments;

    @BeforeClass
    public static void setup() {
        environments = getTestEnvironments();
    }

    @AfterClass
    public static void teardown() {
        for (TestEnvironment env : environments) {
            env.close();
        }
    }

    @Theory
    public void sameBuilderSameProperty(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        builder.property(property);
        try {
            // You cannot add the same property twice
            builder.property(property);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // You cannot add the same property twice
            Assert.assertEquals("This builder already contains a property with id 'property'", e.getMessage());
        }
    }

    @Theory
    public void sameBuilderSameName(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty p1 = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        StaticProperty p2 = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        builder.property(p1);
        try {
            // You cannot add two properties with the same name
            builder.property(p2);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("This builder already contains a property with id 'property'", e.getMessage());
        }
    }

    @Theory
    public void differentBuildersSameProperty(TestEnvironment te) {
        StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
        StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        b1.property(property);
        b2.property(property);
        b1.build();
        try {
            // You cannot build shapes that share properties
            b2.build();
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertEquals("Attempt to reinitialize the offset of static property 'property' of kind 'Int'.\nWas it added to more than one builder or multiple times to the same builder?",
                            e.getMessage());
        }
    }

    @Theory
    public void propertyName(TestEnvironment te) throws NoSuchFieldException {
        if (!te.arrayBased) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
            builder.property(property);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();
            object.getClass().getField(guessGeneratedFieldName(property));
        }
    }

    @Theory
    public void propertyNameWithForbiddenChars(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty p1 = new DefaultStaticProperty("forbidden.char", StaticPropertyKind.Int, false);
        StaticProperty p2 = new DefaultStaticProperty("forbidden;char", StaticPropertyKind.Int, false);
        StaticProperty p3 = new DefaultStaticProperty("forbidden[char", StaticPropertyKind.Int, false);
        StaticProperty p4 = new DefaultStaticProperty("forbidden/char", StaticPropertyKind.Int, false);
        builder.property(p1).property(p2).property(p3).property(p4).build();
    }

    @Theory
    public void propertyNameTooLong(TestEnvironment te) {
        char[] longId = new char[65529];
        char[] tooLongId = new char[65530];
        Arrays.fill(longId, 'x');
        Arrays.fill(tooLongId, 'x');

        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        builder.property(new DefaultStaticProperty(new String(longId), StaticPropertyKind.Int, false));
        try {
            builder.property(new DefaultStaticProperty(new String(tooLongId), StaticPropertyKind.Int, false));
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("The property id cannot be longer than 65529 characters", e.getMessage());
        }
        builder.build();
    }

    @Theory
    public void propertyFinal(TestEnvironment te) throws NoSuchFieldException {
        if (!te.arrayBased) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("p1", StaticPropertyKind.Int, true);
            StaticProperty p2 = new DefaultStaticProperty("p2", StaticPropertyKind.Int, false);
            builder.property(p1);
            builder.property(p2);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();
            Field f1 = object.getClass().getField(guessGeneratedFieldName(p1));
            Field f2 = object.getClass().getField(guessGeneratedFieldName(p2));
            Assert.assertTrue(Modifier.isFinal(f1.getModifiers()));
            Assert.assertFalse(Modifier.isFinal(f2.getModifiers()));
        }
    }

    @Theory
    public void propertyKind(TestEnvironment te) throws NoSuchFieldException {
        if (!te.arrayBased) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticPropertyKind[] kinds = StaticPropertyKind.values();
            StaticProperty[] properties = new StaticProperty[kinds.length];
            for (int i = 0; i < properties.length; i++) {
                properties[i] = new DefaultStaticProperty(kinds[i].name(), kinds[i], false);
                builder.property(properties[i]);
            }
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object object = shape.getFactory().create();
            for (int i = 0; i < properties.length; i++) {
                Class<?> expectedType;
                switch (kinds[i]) {
                    case Boolean:
                        expectedType = boolean.class;
                        break;
                    case Byte:
                        expectedType = byte.class;
                        break;
                    case Char:
                        expectedType = char.class;
                        break;
                    case Double:
                        expectedType = double.class;
                        break;
                    case Float:
                        expectedType = float.class;
                        break;
                    case Int:
                        expectedType = int.class;
                        break;
                    case Long:
                        expectedType = long.class;
                        break;
                    case Object:
                        expectedType = Object.class;
                        break;
                    case Short:
                        expectedType = short.class;
                        break;
                    default:
                        expectedType = null;
                        Assert.fail("Unexpected type: " + kinds[i]);
                }
                Assert.assertEquals(expectedType, object.getClass().getField(guessGeneratedFieldName(properties[i])).getType());
            }
        }
    }

    @Theory
    public void maxProperties(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        for (int i = 0; i <= 65535; i++) {
            try {
                builder.property(new DefaultStaticProperty("property" + i, StaticPropertyKind.Int, false));
            } catch (IllegalArgumentException e) {
                Assert.assertEquals("This builder already contains the maximum number of properties: 65535", e.getMessage());
                Assert.assertEquals(65535, i);
                return;
            }
        }
        Assert.fail();
    }

    @Test
    public void dummy() {
        // to make sure this file is recognized as a test
    }
}
