/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic.test;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Layout.ImplicitCast;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;

@RunWith(Parameterized.class)
public class ImplicitCastTest {
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();

    static final Layout longLayout = Layout.newLayout().addAllowedImplicitCast(ImplicitCast.IntToLong).build();
    static final Layout doubleLayout = Layout.newLayout().addAllowedImplicitCast(ImplicitCast.IntToDouble).build();

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                        {longLayout, 1, 1L << 42, long.class},
                        {doubleLayout, 1, 3.14, double.class}
        });
    }

    final Layout layout;
    final int intVal;
    final Object otherVal;
    final Class<?> otherPrimClass;

    public ImplicitCastTest(Layout layout, int intVal, Object otherVal, Class<?> otherPrimClass) {
        this.layout = layout;
        this.intVal = intVal;
        this.otherVal = otherVal;
        this.otherPrimClass = otherPrimClass;
    }

    @SuppressWarnings("deprecation")
    private static Class<?> getLocationType(Location location) {
        return ((com.oracle.truffle.api.object.TypedLocation) location).getType();
    }

    @Test
    public void testIntOther() {
        Shape rootShape = layout.createShape(new ObjectType());
        DynamicObject object = rootShape.newInstance();
        LIBRARY.put(object, "a", intVal);
        Location location1 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(int.class, getLocationType(location1));

        LIBRARY.put(object, "a", otherVal);
        Location location2 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(otherPrimClass, getLocationType(location2));
        Assert.assertEquals(otherVal.getClass(), LIBRARY.getOrDefault(object, "a", null).getClass());
        DOTestAsserts.assertSameLocation(location1, location2);
    }

    @Test
    public void testOtherInt() {
        Shape rootShape = layout.createShape(new ObjectType());
        DynamicObject object = rootShape.newInstance();
        LIBRARY.put(object, "a", otherVal);
        Location location1 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(otherPrimClass, getLocationType(location1));

        LIBRARY.put(object, "a", intVal);
        Location location2 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(otherPrimClass, getLocationType(location2));
        Assert.assertEquals(otherVal.getClass(), LIBRARY.getOrDefault(object, "a", null).getClass());
        DOTestAsserts.assertSameLocation(location1, location2);
    }

    @Test
    public void testIntOtherDoesNotGoBack() {
        Shape rootShape = layout.createShape(new ObjectType());
        DynamicObject object = rootShape.newInstance();
        LIBRARY.put(object, "a", intVal);
        Location location1 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(int.class, getLocationType(location1));

        LIBRARY.put(object, "a", otherVal);
        Location location2 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(otherPrimClass, getLocationType(location2));
        Assert.assertEquals(otherVal.getClass(), LIBRARY.getOrDefault(object, "a", null).getClass());
        DOTestAsserts.assertSameLocation(location1, location2);

        LIBRARY.put(object, "a", intVal);
        Location location3 = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(otherPrimClass, getLocationType(location3));
        Assert.assertEquals(otherVal.getClass(), LIBRARY.getOrDefault(object, "a", null).getClass());
        DOTestAsserts.assertSameLocation(location2, location3);
    }

    @Test
    public void testIntObject() {
        Shape rootShape = layout.createShape(new ObjectType());
        DynamicObject object = rootShape.newInstance();
        LIBRARY.put(object, "a", intVal);
        LIBRARY.put(object, "a", "");
        Location location = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(Object.class, getLocationType(location));
        Assert.assertEquals(String.class, LIBRARY.getOrDefault(object, "a", null).getClass());
    }

    @Test
    public void testIntOtherObject() {
        Shape rootShape = layout.createShape(new ObjectType());
        DynamicObject object = rootShape.newInstance();
        LIBRARY.put(object, "a", intVal);
        LIBRARY.put(object, "a", otherVal);
        LIBRARY.put(object, "a", "");
        Location location = object.getShape().getProperty("a").getLocation();
        Assert.assertEquals(Object.class, getLocationType(location));
        Assert.assertEquals(String.class, LIBRARY.getOrDefault(object, "a", null).getClass());
    }

    @Test
    public void testLocationDecoratorEquals() {
        Layout defaultLayout = Layout.newLayout().build();
        Shape defaultRootShape = defaultLayout.createShape(new ObjectType());
        Shape implicitCastRootShape = layout.createShape(new ObjectType());

        DynamicObject object1 = implicitCastRootShape.newInstance();
        LIBRARY.put(object1, "a", otherVal);
        Location location1 = object1.getShape().getProperty("a").getLocation();

        // Location of "a" should not change if an Integer is set
        LIBRARY.putIfPresent(object1, "a", intVal);
        Assert.assertEquals(location1, object1.getShape().getProperty("a").getLocation());

        DynamicObject object2 = defaultRootShape.newInstance();
        LIBRARY.put(object2, "a", otherVal);
        Location location2 = object2.getShape().getProperty("a").getLocation();

        // This test relies on the assumption that both locations are of the same class
        Assert.assertEquals(location1.getClass(), location2.getClass());
        Assert.assertNotEquals(location1, location2);
    }
}
