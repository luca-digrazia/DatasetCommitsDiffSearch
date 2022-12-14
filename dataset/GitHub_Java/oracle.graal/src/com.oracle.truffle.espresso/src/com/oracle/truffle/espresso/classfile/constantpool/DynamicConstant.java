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
package com.oracle.truffle.espresso.classfile.constantpool;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MHLinkToNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface DynamicConstant extends PoolConstant {

    static DynamicConstant create(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        return new Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    default Tag tag() {
        return Tag.DYNAMIC;
    }

    final class Indexes extends BootstrapMethodConstant.Indexes implements DynamicConstant, Resolvable {
        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            super(bootstrapMethodAttrIndex, nameAndTypeIndex);
        }

        private static Resolved makeResolved(Klass type, StaticObject result) {
            switch (type.getJavaKind()) {
                case Boolean:
                case Byte:
                case Short:
                case Char: {
                    int value = (int) MHLinkToNode.rebasic(type.getMeta().unboxGuest(result), type.getJavaKind());
                    return new ResolvedInt(value);
                }
                case Int: {
                    int value = type.getMeta().unboxInteger(result);
                    return new ResolvedInt(value);
                }
                case Float: {
                    float value = type.getMeta().unboxFloat(result);
                    return new ResolvedFloat(value);
                }
                case Long: {
                    long value = type.getMeta().unboxLong(result);
                    return new ResolvedLong(value);
                }
                case Double: {
                    double value = type.getMeta().unboxDouble(result);
                    return new ResolvedDouble(value);
                }
                case Object:
                    return new ResolvedObject(result);
            }
            throw EspressoError.shouldNotReachHere();
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.nameAndTypeAt(nameAndTypeIndex).validateField(pool);
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            Meta meta = accessingKlass.getMeta();

            // Indy constant resolving.
            BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) ((ObjectKlass) accessingKlass).getAttribute(BootstrapMethodsAttribute.NAME);
            NameAndTypeConstant specifier = pool.nameAndTypeAt(nameAndTypeIndex);

            assert (bms != null);
            // TODO(garcia) cache bootstrap method resolution
            // Bootstrap method resolution
            BootstrapMethodsAttribute.Entry bsEntry = bms.at(getBootstrapMethodAttrIndex());

            StaticObject bootstrapmethodMethodHandle = bsEntry.getMethodHandle(accessingKlass, pool);
            StaticObject[] args = bsEntry.getStaticArguments(accessingKlass, pool);

            StaticObject fieldName = meta.toGuestString(specifier.getName(pool));
            Klass fieldType = meta.resolveSymbolOrFail(Types.fromDescriptor(specifier.getDescriptor(pool)), accessingKlass.getDefiningClassLoader());

            Object result = meta.java_lang_invoke_MethodHandleNatives_linkDynamicConstant.invokeDirect(
                            null,
                            accessingKlass.mirror(),
                            thisIndex,
                            bootstrapmethodMethodHandle,
                            fieldName, fieldType.mirror(),
                            args);
            try {
                return makeResolved(fieldType, (StaticObject) result);
            } catch (ClassCastException | NullPointerException e) {
                throw Meta.throwException(meta.java_lang_BootstrapMethodError);
            } catch (EspressoException e) {
                if (meta.java_lang_NullPointerException.isAssignableFrom(e.getExceptionObject().getKlass()) ||
                                meta.java_lang_ClassCastException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                    throw Meta.throwExceptionWithCause(meta.java_lang_BootstrapMethodError, e.getExceptionObject());
                }
                throw e;
            }
        }
    }

    interface Resolved extends DynamicConstant, Resolvable.ResolvedConstant {
        void putResolved(VirtualFrame frame, int top, BytecodeNode node);
    }

    final class ResolvedObject implements Resolved {
        final StaticObject resolved;

        public ResolvedObject(StaticObject resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            node.putObject(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedInt implements Resolved {
        final int resolved;

        public ResolvedInt(int resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            node.putInt(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedLong implements Resolved {
        final long resolved;

        public ResolvedLong(long resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            node.putLong(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedDouble implements Resolved {
        final double resolved;

        public ResolvedDouble(double resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            node.putDouble(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }

    final class ResolvedFloat implements Resolved {
        final float resolved;

        public ResolvedFloat(float resolved) {
            this.resolved = resolved;
        }

        @Override
        public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
            node.putFloat(frame, top, resolved);
        }

        @Override
        public Object value() {
            return resolved;
        }

        @Override
        public String toString(ConstantPool pool) {
            return "ResolvedDynamicConstant(" + resolved + ")";
        }
    }
}
