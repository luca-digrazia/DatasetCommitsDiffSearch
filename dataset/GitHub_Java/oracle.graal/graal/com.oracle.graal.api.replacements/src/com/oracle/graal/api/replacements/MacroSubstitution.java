/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.replacements;

import java.lang.annotation.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

/**
 * Denotes a macro substitute method. This replaces a method invocation with an instance of the
 * specified node class.
 * 
 * A macro substitution can be combined with a normal substitution, so that the macro node can be
 * replaced with the actual substitution code during lowering.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MacroSubstitution {

    /**
     * Gets the name of the substituted method.
     * <p>
     * If the default value is specified for this element, then the name of the substituted method
     * is same as the substitute method.
     */
    String value() default "";

    /**
     * Determines if the substituted method is static.
     */
    boolean isStatic() default true;

    /**
     * Gets the {@linkplain MetaUtil#signatureToMethodDescriptor signature} of the substituted
     * method.
     * <p>
     * If the default value is specified for this element, then the signature of the substituted
     * method is the same as the substitute method.
     */
    String signature() default "";

    /**
     * The node class with which the method invocation should be replaced. It needs to be a subclass
     * of {@link FixedWithNextNode}, and it is expected to provide a public constructor that takes
     * an {@link InvokeNode} as a parameter.
     */
    Class<? extends FixedWithNextNode> macro();

    /**
     * Determines if this method should be substituted in all cases, even if inlining thinks it is
     * not important.
     */
    boolean isForcedInlining() default false;
}
