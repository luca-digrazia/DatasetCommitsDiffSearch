/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.codegen.processor.ast;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public class CodeTypeMirror implements TypeMirror {

    private final TypeKind kind;

    public CodeTypeMirror(TypeKind kind) {
        this.kind = kind;
    }

    @Override
    public TypeKind getKind() {
        return kind;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        throw new UnsupportedOperationException();
    }

    public static class ArrayCodeTypeMirror extends CodeTypeMirror implements ArrayType {

        private final TypeMirror component;

        public ArrayCodeTypeMirror(TypeMirror component) {
            super(TypeKind.ARRAY);
            this.component = component;
        }

        @Override
        public TypeMirror getComponentType() {
            return component;
        }

    }

    public static class DeclaredCodeTypeMirror extends CodeTypeMirror implements DeclaredType {

        private final CodeTypeElement clazz;

        public DeclaredCodeTypeMirror(CodeTypeElement clazz) {
            super(TypeKind.DECLARED);
            this.clazz = clazz;
        }

        @Override
        public Element asElement() {
            return clazz;
        }

        @Override
        public TypeMirror getEnclosingType() {
            return clazz.getEnclosingElement().asType();
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return clazz.getQualifiedName().toString();
        }

    }

}
