/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolved non-primitive, non-array types in Espresso.
 */
public final class ObjectKlass extends Klass {

    public static final ObjectKlass[] EMPTY_ARRAY = new ObjectKlass[0];

    private final EnclosingMethodAttribute enclosingMethod;

    private final RuntimeConstantPool pool;

    private final LinkedKlass linkedKlass;

    @CompilationFinal //
    private StaticObject statics;

    @CompilationFinal(dimensions = 1) //
    private Field[] declaredFields;

    @CompilationFinal(dimensions = 1) private final Field[] fieldTable;

    private final int wordFields;
    private final int staticWordFields;
    private final int objectFields;
    private final int staticObjectFields;

    @CompilationFinal(dimensions = 1) private final Field[] staticFieldTable;

    @CompilationFinal(dimensions = 1) //
    private Method[] declaredMethods;

    @CompilationFinal(dimensions = 1) private Method[] mirandaMethods;

    private final InnerClassesAttribute innerClasses;

    private final Attribute runtimeVisibleAnnotations;

    private final Klass hostKlass;

    @CompilationFinal(dimensions = 1) private final Method[] vtable;
    @CompilationFinal(dimensions = 2) private final Method[][] itable;
    @CompilationFinal(dimensions = 1) private final Klass[] iKlassTable;

    private int initState = LINKED;

    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;

    public final Attribute getAttribute(Symbol<Name> name) {
        return linkedKlass.getAttribute(name);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader) {
        this(context, linkedKlass, superKlass, superInterfaces, classLoader, null);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader, Klass hostKlass) {
        super(context, linkedKlass.getName(), linkedKlass.getType(), superKlass, superInterfaces);

        this.linkedKlass = linkedKlass;
        this.hostKlass = hostKlass;

        this.enclosingMethod = (EnclosingMethodAttribute) getAttribute(EnclosingMethodAttribute.NAME);
        this.innerClasses = (InnerClassesAttribute) getAttribute(InnerClassesAttribute.NAME);

        // Move attribute name to better location.
        this.runtimeVisibleAnnotations = getAttribute(Name.RuntimeVisibleAnnotations);

        // TODO(peterssen): Make writable copy.
        this.pool = new RuntimeConstantPool(getContext(), linkedKlass.getConstantPool(), classLoader);

        FieldTable.CreationResult fieldCR = FieldTable.create(superKlass, this, linkedKlass);

        this.fieldTable = fieldCR.fieldTable;
        this.staticFieldTable = fieldCR.staticFieldTable;
        this.declaredFields = fieldCR.declaredFields;

        this.wordFields = fieldCR.wordFields;
        this.staticWordFields = fieldCR.staticWordFields;
        this.objectFields = fieldCR.objectFields;
        this.staticObjectFields = fieldCR.staticObjectFields;

        LinkedMethod[] linkedMethods = linkedKlass.getLinkedMethods();
        Method[] methods = new Method[linkedMethods.length];
        for (int i = 0; i < methods.length; ++i) {
            methods[i] = new Method(this, linkedMethods[i]);
        }

        this.declaredMethods = methods;
        if (this.isInterface()) {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(this, superInterfaces, declaredMethods);
            this.itable = methodCR.getItable();
            this.iKlassTable = methodCR.getiKlass();
            this.vtable = null;
        } else {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(superKlass, superInterfaces, this);
            this.itable = methodCR.getItable();
            this.iKlassTable = methodCR.getiKlass();
            this.vtable = VirtualTable.create(superKlass, declaredMethods, this);
        }
    }

    @Override
    public StaticObject getStatics() {
        if (statics == null) {
            obtainStatics();
        }
        return statics;
    }

    private synchronized void obtainStatics() {
        if (statics == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            statics = new StaticObject(this, true);
        }
    }

    @Override
    public boolean isInstanceClass() {
        throw EspressoError.unimplemented();
    }

    @Override
    public int getFlags() {
        return linkedKlass.getFlags();
    }

    @Override
    public boolean isInitialized() {
        return initState == INITIALIZED;
    }

    private boolean isPrepared() {
        return initState == PREPARED;
    }

    private boolean isInitializedOrPrepared() {
        return isPrepared() || isInitialized();
    }

    private synchronized void actualInit() {
        if (!(isInitializedOrPrepared())) { // Check under lock
            if (getSuperKlass() != null) {
                getSuperKlass().initialize();
            }

            /**
             * Spec fragment: Then, initialize each final static field of C with the constant value
             * in its ConstantValue attribute (§4.7.2), in the order the fields appear in the
             * ClassFile structure.
             *
             * ...
             *
             * Next, execute the class or interface initialization method of C.
             */
            for (Field f : declaredFields) {
                if (f.isStatic()) {
                    ConstantValueAttribute a = (ConstantValueAttribute) f.getAttribute(Name.ConstantValue);
                    if (a == null) {
                        break;
                    }
                    switch (f.getKind()) {
                        case Boolean: {
                            boolean c = getConstantPool().intAt(a.getConstantvalueIndex()) != 0;
                            f.set(getStatics(), c);
                            break;
                        }
                        case Byte: {
                            byte c = (byte) getConstantPool().intAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Short: {
                            short c = (short) getConstantPool().intAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Char: {
                            char c = (char) getConstantPool().intAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Int: {
                            int c = getConstantPool().intAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Float: {
                            float c = getConstantPool().floatAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Long: {
                            long c = getConstantPool().longAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Double: {
                            double c = getConstantPool().doubleAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        case Object: {
                            StaticObject c = getConstantPool().resolvedStringAt(a.getConstantvalueIndex());
                            f.set(getStatics(), c);
                            break;
                        }
                        default:
                            EspressoError.shouldNotReachHere("invalid constant field kind");
                    }
                }
            }
            initState = PREPARED;
            Method clinit = getClassInitializer();
            if (clinit != null) {
                clinit.getCallTarget().call();
            }
            initState = INITIALIZED;
            assert isInitialized();
        }
    }

    @Override
    public void initialize() {
        if (!isInitialized()) { // Skip synchronization and locks if already init.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            actualInit();
        }
    }

    @Override
    public Klass getElementalType() {
        return this;
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return pool.getClassLoader();
    }

    @Override
    public RuntimeConstantPool getConstantPool() {
        return pool;
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
        List<Method> constructors = new ArrayList<>();
        for (Method m : getDeclaredMethods()) {
            if (Name.INIT.equals(m.getName())) {
                constructors.add(m);
            }
        }
        return constructors.toArray(Method.EMPTY_ARRAY);
    }

    Method[] getMirandaMethods() {
        return mirandaMethods;
    }

    @Override
    public Method[] getDeclaredMethods() {
        return declaredMethods;
    }

    @Override
    public Field[] getDeclaredFields() {
        return declaredFields;
    }

    @Override
    public Klass getComponentType() {
        return null;
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return innerClasses;
    }

    public final LinkedKlass getLinkedKlass() {
        return linkedKlass;
    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }

    public int getStaticFieldSlots() {
        return linkedKlass.staticFieldCount;
    }

    public int getInstanceFieldSlots() {
        return linkedKlass.instanceFieldCount;
    }

    public int getObjectFieldsCount() {
        return objectFields;
    }

    public int getWordFieldsCount() {
        return wordFields;
    }

    public int getStaticObjectFieldsCount() {
        return staticObjectFields;
    }

    public int getStaticWordFieldsCount() {
        return staticWordFields;
    }

    @Override
    public Klass getHostClass() {
        return hostKlass;
    }

    @Override
    public final Field lookupFieldTable(int slot) {
        assert (slot >= 0 && slot < getInstanceFieldSlots());
        return fieldTable[slot];
    }

    @Override
    public final Field lookupStaticFieldTable(int slot) {
        assert (slot >= 0 && slot < getStaticFieldSlots());
        return staticFieldTable[slot];
    }

    public final Field lookupHiddenField(Symbol<Name> name) {
        // Hidden fields are located at the end of the field table.
        for (int i = fieldTable.length - 1; i > 0; i--) {
            Field f = fieldTable[i];
            if (f.getName() == name && f.isHidden()) {
                return f;
            }
        }
        throw EspressoError.shouldNotReachHere();
    }

    Method[] getVTable() {
        return vtable;
    }

    @Override
    public final Method vtableLookup(int index) {
        assert (index >= 0) : "Undeclared virtual method";
        return vtable[index];
    }

    @Override
    @ExplodeLoop
    public final Method itableLookup(Klass interfKlass, int index) {
        assert (index >= 0) : "Undeclared interface method";
        int i = 0;
        for (Klass k : iKlassTable) {
            if (k == interfKlass) {
                return itable[i][index];
            }
            i++;
        }
        return null;
    }

    final Method[][] getItable() {
        return itable;
    }

    final Klass[] getiKlassTable() {
        return iKlassTable;
    }

    final Method lookupVirtualMethod(Symbol<Name> name, Symbol<Signature> signature) {
        for (Method m : vtable) {
            if (m.getName() == name && m.getRawSignature() == signature) {
                return m;
            }
        }
        return null;
    }

    public final Method lookupInterfaceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        for (Method[] table : itable) {
            for (Method m : table) {
                if (!m.isStatic() && !m.isPrivate() && name == m.getName() && signature == m.getRawSignature()) {
                    return m;
                }
            }
        }
        assert getSuperKlass().getType() == Type.Object;
        Method m = getSuperKlass().lookupDeclaredMethod(name, signature);
        if (m != null && m.isPublic() && !m.isStatic()) {
            return m;
        }
        return null;
    }

    @Override
    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        methodLookupCount.inc();
        Method method = lookupDeclaredMethod(methodName, signature);
        if (method == null) {
            method = lookupMirandas(methodName, signature);
        }
        if (method == null && getType() == Type.MethodHandle) {
            method = lookupPolysigMethod(methodName, signature);
        }
        if (method == null && getSuperKlass() != null) {
            method = getSuperKlass().lookupMethod(methodName, signature);
        }
        return method;
    }

    public final Field[] getFieldTable() {
        return fieldTable;
    }

    public final Field[] getStaticFieldTable() {
        return staticFieldTable;
    }

    final void setMirandas(ArrayList<InterfaceTables.Miranda> mirandas) {
        Method[] declaredAndMirandaMethods = new Method[mirandas.size()];
        int pos = 0;
        for (InterfaceTables.Miranda miranda : mirandas) {
            miranda.setDeclaredMethodPos(pos);
            declaredAndMirandaMethods[pos++] = new Method(miranda.method);
        }
        this.mirandaMethods = declaredAndMirandaMethods;
    }

    private Method lookupMirandas(Symbol<Name> methodName, Symbol<Signature> signature) {
        if (mirandaMethods == null) {
            return null;
        }
        for (Method miranda : mirandaMethods) {
            if (miranda.getName() == methodName && miranda.getRawSignature() == signature) {
                return miranda;
            }
        }
        return null;
    }
}
