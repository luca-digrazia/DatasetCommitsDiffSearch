/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PRIVATE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PROTECTED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PUBLIC;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public final class ArrayKlass extends Klass {

    private final Klass componentType;
    private final Klass elementalType;
    private final int dimension;

    ArrayKlass(Klass componentType) {
        super(componentType.getContext(),
                        null, // TODO(peterssen): Internal, , or / name?
                        componentType.getTypes().arrayOf(componentType.getType()),
                        componentType.getMeta().java_lang_Object,
                        componentType.getMeta().ARRAY_SUPERINTERFACES);
        this.componentType = componentType;
        this.elementalType = componentType.getElementalType();
        this.dimension = Types.getArrayDimensions(getType());
    }

    @Override
    public StaticObject getStatics() {
        throw EspressoError.shouldNotReachHere("Arrays do not have static fields");
    }

    @Override
    public int getModifiers() {
        // Arrays (of static inner class) may have protected access.
        return (getElementalType().getModifiers() & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED)) | ACC_FINAL | ACC_ABSTRACT;
    }

    @Override
    public int getClassModifiers() {
        // Arrays (of static inner class) may have protected access.
        return (getElementalType().getClassModifiers() & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED)) | ACC_FINAL | ACC_ABSTRACT;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        // Always initialized, independent of the elemental type initialization state.
        return true;
    }

    @Override
    public void initialize() {
        // Array class initialization does not trigger elemental type initialization.
    }

    @Override
    public Klass getHostClass() {
        return null;
    }

    @Override
    public Klass getElementalType() {
        return elementalType;
    }

    @Override
    public Klass getComponentType() {
        return componentType;
    }

    @Override
    public Method vtableLookup(int vtableIndex) {
        return getSuperKlass().vtableLookup(vtableIndex);
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public Method[] getDeclaredConstructors() {
        return Method.EMPTY_ARRAY;
    }

    @Override
    public Method[] getDeclaredMethods() {
        return Method.EMPTY_ARRAY;
    }

    @Override
    public Field[] getDeclaredFields() {
        return Field.EMPTY_ARRAY;
    }

    @Override
    public Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass) {
        methodLookupCount.inc();
        return getSuperKlass().lookupMethod(methodName, signature, accessingKlass);
    }

    @Override
    public Field lookupFieldTable(int slot) {
        return getSuperKlass().lookupFieldTable(slot);
    }

    @Override
    public Field lookupStaticFieldTable(int slot) {
        return getSuperKlass().lookupStaticFieldTable(slot);
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return elementalType.getDefiningClassLoader();
    }

    @Override
    public ConstantPool getConstantPool() {
        return getElementalType().getConstantPool();
    }

    public int getDimension() {
        return dimension;
    }

    boolean arrayTypeChecks(ArrayKlass other) {
        assert isArray();
        int thisDim = getDimension();
        int otherDim = other.getDimension();
        if (otherDim > thisDim) {
            Klass thisElemental = this.getElementalType();
            return thisElemental == getMeta().java_lang_Object || thisElemental == getMeta().java_io_Serializable || thisElemental == getMeta().java_lang_Cloneable;
        } else if (thisDim == otherDim) {
            Klass klass = getElementalType();
            Klass other1 = other.getElementalType();
            if (klass == other1) {
                return true;
            }
            if (klass.isPrimitive() || other1.isPrimitive()) {
                // Reference equality is enough within the same context.
                assert klass.getContext() == other1.getContext();
                return klass == other1;
            }
            if (klass.isInterface()) {
                return klass.checkInterfaceSubclassing(other1);
            }
            int depth = klass.getHierarchyDepth();
            return other1.getHierarchyDepth() >= depth && other1.getSuperTypes()[depth] == klass;
        } else {
            assert thisDim > otherDim;
            return false;
        }
    }

    @Override
    public String getNameAsString() {
        return "[" + componentType.getNameAsString();
    }
}
