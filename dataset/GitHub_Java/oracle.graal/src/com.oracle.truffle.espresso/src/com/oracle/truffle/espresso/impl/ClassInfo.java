/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.ArrayList;
import java.util.regex.Matcher;

public abstract class ClassInfo {

    public static ImmutableClassInfo create(Klass klass, EspressoContext context) {
        StringBuilder hierarchy = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        StringBuilder enclosing = new StringBuilder();
        String name = klass.getNameAsString();

        Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(name);
        if (matcher.matches()) {
            // fingerprints are only relevant for inner classes
            hierarchy.append(klass.getSuperClass().getTypeAsString()).append(";");
            for (ObjectKlass itf : klass.getInterfaces()) {
                hierarchy.append(itf.getTypeAsString()).append(";");
            }

            for (Method method : klass.getDeclaredMethods()) {
                methods.append(method.getNameAsString()).append(";");
                methods.append(method.getSignatureAsString()).append(";");
            }

            for (Field field : klass.getDeclaredFields()) {
                fields.append(field.getTypeAsString()).append(";");
                fields.append(field.getNameAsString()).append(";");
            }

            ObjectKlass objectKlass = (ObjectKlass) klass;
            ConstantPool pool = klass.getConstantPool();
            NameAndTypeConstant nmt = pool.nameAndTypeAt(objectKlass.getEnclosingMethod().getMethodIndex());
            enclosing.append(nmt.getName(pool)).append(";").append(nmt.getDescriptor(pool));
        }
        // find all currently loaded direct inner classes and create class infos
        ArrayList<ImmutableClassInfo> inners = new ArrayList<>(1);

        Klass[] loadedInnerClasses = InnerClassRedefiner.findLoadedInnerClasses(klass, context);
        for (Klass inner : loadedInnerClasses) {
            matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(inner.getNameAsString());
            // only add anonymous inner classes
            if (matcher.matches()) {
                inners.add(InnerClassRedefiner.getGlobalClassInfo(inner, context));
            }
        }
        return new ImmutableClassInfo((ObjectKlass) klass, name, klass.getDefiningClassLoader(), hierarchy.toString(), methods.toString(), fields.toString(), enclosing.toString(),
                        inners.toArray(new ImmutableClassInfo[0]), null);
    }

    public static HotSwapClassInfo create(RedefineInfo redefineInfo, EspressoContext context) {
        ObjectKlass klass = (ObjectKlass) redefineInfo.getKlass();
        return create(klass, klass.getNameAsString(), redefineInfo.getClassBytes(), klass.getDefiningClassLoader(), context);
    }

    public static HotSwapClassInfo create(String name, byte[] bytes, StaticObject definingLoader, EspressoContext context) {
        return create(null, name, bytes, definingLoader, context);
    }

    public static HotSwapClassInfo create(ObjectKlass klass, String name, byte[] bytes, StaticObject definingLoader, EspressoContext context) {
        ParserKlass parserKlass = ClassfileParser.parse(new ClassfileStream(bytes, null), "L" + name + ";", null, context);

        StringBuilder hierarchy = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        StringBuilder enclosing = new StringBuilder();

        Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(name);
        if (matcher.matches()) {
            // fingerprints are only relevant for inner classes
            hierarchy.append(parserKlass.getSuperKlass().toString()).append(";");
            for (Symbol<Symbol.Type> itf : parserKlass.getSuperInterfaces()) {
                hierarchy.append(itf.toString()).append(";");
            }

            for (ParserMethod method : parserKlass.getMethods()) {
                methods.append(method.getName().toString()).append(";");
                methods.append(method.getSignature().toString()).append(";");
            }

            for (ParserField field : parserKlass.getFields()) {
                fields.append(field.getType().toString()).append(";");
                fields.append(field.getName().toString()).append(";");
            }

            ConstantPool pool = parserKlass.getConstantPool();
            EnclosingMethodAttribute attr = (EnclosingMethodAttribute) parserKlass.getAttribute(EnclosingMethodAttribute.NAME);
            NameAndTypeConstant nmt = pool.nameAndTypeAt(attr.getMethodIndex());
            enclosing.append(nmt.getName(pool)).append(";").append(nmt.getDescriptor(pool));
        }

        return new HotSwapClassInfo(klass, name, definingLoader, hierarchy.toString(), methods.toString(), fields.toString(), enclosing.toString(), new ArrayList<>(1), bytes);
    }

    public static ImmutableClassInfo copyFrom(HotSwapClassInfo info) {
        ArrayList<ImmutableClassInfo> inners = new ArrayList<>(info.getInnerClasses().length);
        for (HotSwapClassInfo innerClass : info.getInnerClasses()) {
            inners.add(copyFrom(innerClass));
        }
        return new ImmutableClassInfo(info.getKlass(), info.getName(), info.getClassLoader(), info.getClassFingerprint(), info.getMethodFingerprint(), info.getFieldFingerprint(),
                        info.getEnclosingMethodFingerprint(), inners.toArray(new ImmutableClassInfo[0]), info.getBytes());
    }

    public abstract String getClassFingerprint();

    public abstract String getMethodFingerprint();

    public abstract String getFieldFingerprint();

    public abstract String getEnclosingMethodFingerprint();

    public abstract ClassInfo[] getInnerClasses();

    public abstract String getName();

    public abstract StaticObject getClassLoader();

    public abstract ObjectKlass getKlass();

    public abstract byte[] getBytes();

    public int match(ClassInfo other) {
        if (!getClassFingerprint().equals(other.getClassFingerprint())) {
            // always mark super hierachy changes as incompatible
            return 0;
        }
        if (!getFieldFingerprint().equals(other.getFieldFingerprint())) {
            // field changed not supported yet
            // Remove this restriction when supported
            return 0;
        }
        int score = 0;
        score += getMethodFingerprint().equals(other.getMethodFingerprint()) ? InnerClassRedefiner.METHOD_FINGERPRINT_EQUALS : 0;
        score += getEnclosingMethodFingerprint().equals(other.getEnclosingMethodFingerprint()) ? InnerClassRedefiner.ENCLOSING_METHOD_FINGERPRINT_EQUALS : 0;
        score += getFieldFingerprint().equals(other.getFieldFingerprint()) ? InnerClassRedefiner.FIELD_FINGERPRINT_EQUALS : 0;
        score += getInnerClasses().length == other.getInnerClasses().length ? InnerClassRedefiner.NUMBER_INNER_CLASSES : 0;
        return score;
    }
}
