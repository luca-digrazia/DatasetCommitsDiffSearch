/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;

/**
 * Store the compile-time information for a field in the Substrate VM, such as the field offset.
 */
public class HostedField implements ReadableJavaField, SharedField, Comparable<HostedField> {

    private final HostedUniverse universe;
    private final HostedMetaAccess metaAccess;
    public final AnalysisField wrapped;

    private final HostedType holder;
    private final HostedType type;

    protected int location;

    private final JavaTypeProfile typeProfile;

    private static final int LOC_CONSTANT_STATIC_FIELD = -10;

    public HostedField(HostedUniverse universe, HostedMetaAccess metaAccess, AnalysisField wrapped, HostedType holder, HostedType type, JavaTypeProfile typeProfile) {
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.wrapped = wrapped;
        this.holder = holder;
        this.type = type;
        this.typeProfile = typeProfile;
        this.location = LOC_UNINITIALIZED;
    }

    public JavaTypeProfile getFieldTypeProfile() {
        return typeProfile;
    }

    protected void setLocation(int location) {
        assert this.location == LOC_UNINITIALIZED;
        assert location >= 0;
        this.location = location;
    }

    protected void setConstantValue() {
        assert this.location == LOC_UNINITIALIZED;
        this.location = LOC_CONSTANT_STATIC_FIELD;
    }

    public JavaConstant getConstantValue() {
        if (location == LOC_CONSTANT_STATIC_FIELD) {
            return universe.getConstantReflectionProvider().readFieldValue(wrapped, null);
        } else {
            return null;
        }
    }

    public boolean hasLocation() {
        return location >= 0;
    }

    /**
     * The offset or index of the field. The value depends on the kind of field:
     * <ul>
     * <li>instance fields: the offset (in bytes) from the origin of the instance.
     * <li>static fields of primitive type: the offset (in bytes) into the static primitive data
     * area.
     * <li>static reference fields: the offset (in bytes) into the static object data area.
     * <li>static fields that are never written (including but not limited to static final fields):
     * unused, this method must not be called.
     * </ul>
     */
    @Override
    public int getLocation() {
        return location;
    }

    @Override
    public boolean isAccessed() {
        return wrapped.isAccessed();
    }

    @Override
    public boolean isWritten() {
        return wrapped.isWritten();
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public HostedType getType() {
        return type;
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public JavaConstant readValue(JavaConstant receiver) {
        JavaConstant result;
        if (location == LOC_CONSTANT_STATIC_FIELD) {
            return getConstantValue();
        } else {
            JavaConstant wrappedReceiver;
            if (receiver != null && SubstrateObjectConstant.asObject(receiver) instanceof Class) {
                /* Manual object replacement from java.lang.Class to DynamicHub. */
                wrappedReceiver = SubstrateObjectConstant.forObject(metaAccess.lookupJavaType((Class<?>) SubstrateObjectConstant.asObject(receiver)).getHub());
            } else {
                wrappedReceiver = receiver;
            }
            result = universe.lookup(universe.getConstantReflectionProvider().readFieldValue(wrapped, wrappedReceiver));
        }

        return result;
    }

    @Override
    public boolean allowConstantFolding() {
        if (location == LOC_CONSTANT_STATIC_FIELD) {
            return true;
        } else if (!wrapped.isWritten()) {
            assert !Modifier.isStatic(getModifiers()) : "should have constantValue in this case";
            return true;
        } else if (Modifier.isFinal(getModifiers()) && !Modifier.isStatic(getModifiers())) {
            /*
             * No check for value.isDefaultForKind() is needed here, since during native image
             * generation we are sure that we are not in the middle of a constructor where the final
             * field has not been written yet. Only for dynamic compilation at run time, the check
             * is necessary, but we use a different field implementation class for that.
             */
            return true;
        }

        return false;
    }

    @Override
    public boolean injectFinalForRuntimeCompilation() {
        return ReadableJavaField.injectFinalForRuntimeCompilation(wrapped);
    }

    public JavaConstant readStorageValue(JavaConstant receiver) {
        JavaConstant result = readValue(receiver);
        assert result.getJavaKind() == getType().getStorageKind() : this;
        return result;
    }

    @Override
    public HostedType getDeclaringClass() {
        return holder;
    }

    @Override
    public boolean isInternal() {
        return wrapped.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public Annotation[] getAnnotations() {
        return wrapped.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return wrapped.getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return wrapped.getAnnotation(annotationClass);
    }

    @Override
    public String toString() {
        return "HostedField<" + format("%h.%n") + " location: " + location + "   " + wrapped.toString() + ">";
    }

    @Override
    public JavaKind getStorageKind() {
        return getType().getStorageKind();
    }

    @Override
    public int compareTo(HostedField other) {
        if (this.equals(other)) {
            return 0;
        }
        /*
         * Order by JavaKind. This is required, since we want instance fields of the same size and
         * kind consecutive.
         */
        int result = other.getJavaKind().ordinal() - this.getJavaKind().ordinal();

        if (result == 0) {
            /*
             * Make the field order deterministic by sorting by name. This is arbitrary, we can come
             * up with any better ordering.
             */
            result = this.getDeclaringClass().getName().compareTo(other.getDeclaringClass().getName());
            if (result == 0) {
                result = this.getName().compareTo(other.getName());
            }
        }
        assert result != 0 : "Fields not distinguishable: " + this + ", " + other;
        return result;
    }
}
