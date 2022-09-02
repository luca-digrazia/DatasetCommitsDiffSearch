/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.struct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

/**
 * Denotes a method as a field access of a {@link CStruct C struct}.
 * <p>
 * If the method has a non-void return type, it is a get-method of the field. Calls of the method
 * are replaced with a memory read. The possible signatures are
 * {@code FieldType getFieldName([IntType index], [LocationIdentity locationIdentity]);}
 * <p>
 * If the method has the return type void, it is a set-method of the field. Calls of the method are
 * replaced with a memory write. The possible signatures are
 * {@code void setFieldName([IntType index], FieldType value, [LocationIdentity locationIdentity]);}
 * <p>
 * The receiver is the pointer to the struct that is accessed, i.e., the base address of the memory
 * access.
 * <p>
 * The {@code FieldType} must be the Java-equivalent of the C type used in the C struct. The
 * additional annotations {@link AllowNarrowingCast} or {@link AllowWideningCast} can be used to
 * relax the strict type requirements.
 * <p>
 * The optional parameter {@code index} (always the first parameter when it is present) denotes an
 * index, i.e., the receiver is treated as an array of the struct. The type must be a primitive
 * integer type or a {@link WordBase word type}. Address arithmetic is used to scale the index with
 * the size of the struct.
 * <p>
 * The optional parameter {@code locationIdentity} specifies the {@link LocationIdentity} to be used
 * for the memory access. Two memory accesses with two different location identities are guaranteed
 * to not alias. The parameter cannot be used together with the {@link UniqueLocationIdentity}
 * annotation, which is another way of providing a location identity for the memory access.
 * <p>
 * Multiple accessor methods, with different signatures according to the rules of allowed
 * signatures, are allowed for a single field.
 *
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CField {

    /**
     * Specifies the field name inside the {@link CStruct C struct}. If no name is provided, the
     * method name is used as the field name. A possible "get" or "set" prefix of the method name is
     * removed.
     *
     * @since 19.0
     */
    String value() default "";
}
