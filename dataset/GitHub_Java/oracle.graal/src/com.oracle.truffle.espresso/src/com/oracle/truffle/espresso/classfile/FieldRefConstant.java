/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.runtime.FieldInfoView;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public interface FieldRefConstant extends MemberRefConstant {

    @Override
    default Tag tag() {
        return Tag.FIELD_REF;
    }

    TypeDescriptor getType(ConstantPool pool, int thisIndex);

    FieldInfoView resolve(ConstantPool pool, int thisIndex);

    @Override
    default String toString(ConstantPool pool, int thisIndex) {
        return getDeclaringClass(pool, thisIndex) + "." + getName(pool, thisIndex) + getType(pool, thisIndex);
    }

    public static final class Resolved implements FieldRefConstant {

        private final FieldInfoView field;

        public FieldInfoView field() {
            return field;
        }

        public Resolved(FieldInfoView field) {
            this.field = field;
        }

        public FieldInfoView resolve(ConstantPool pool, int index) {
            return field;
        }

        public TypeDescriptor getDeclaringClass(ConstantPool pool, int thisIndex) {
            return field.getDeclaringClass().getName();
        }

        public Utf8Constant getName(ConstantPool pool, int thisIndex) {
            return field.getName();
        }

        public TypeDescriptor getType(ConstantPool pool, int thisIndex) {
            return field.getType();
        }
    }

    static final class Unresolved extends MemberRefConstant.Unresolved implements FieldRefConstant {

        private final TypeDescriptor type;

        public Unresolved(TypeDescriptor declaringClass, Utf8Constant name, TypeDescriptor type) {
            super(declaringClass, name);
            this.type = type;
        }

        public TypeDescriptor getType(ConstantPool pool, int thisIndex) {
            return type;
        }

        public FieldInfoView resolve(ConstantPool pool, int thisIndex) {
            throw EspressoLanguage.unimplemented();
        }
    }

    static final class Indexes extends MemberRefConstant.Indexes implements FieldRefConstant {

        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        @Override
        protected MemberRefConstant createUnresolved(ConstantPool pool, TypeDescriptor declaringClass, Utf8Constant name, Utf8Constant type) {
            return new FieldRefConstant.Unresolved(declaringClass, name, pool.getContext().getLanguage().getTypeDescriptors().make(type.toString()));
        }

        @Override
        protected FieldRefConstant replace(ConstantPool pool, int thisIndex) {
            return (FieldRefConstant) super.replace(pool, thisIndex);
        }

        public FieldInfoView resolve(ConstantPool pool, int thisIndex) {
            return replace(pool, thisIndex).resolve(pool, thisIndex);
        }

        public TypeDescriptor getType(ConstantPool pool, int thisIndex) {
            return replace(pool, thisIndex).getType(pool, thisIndex);
        }
    }
}
