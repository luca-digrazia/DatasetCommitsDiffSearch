/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodeinfo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_UNSET;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_UNSET;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NodeInfo {

    String shortName() default "";

    /**
     * The template used to build the {@link Verbosity#Name} version. Variable part are specified
     * using &#123;i#inputName&#125; or &#123;p#propertyName&#125;.
     */
    String nameTemplate() default "";

    InputType[] allowedUsageTypes() default {};

    /**
     * An estimation of the number of CPU cycles needed to execute this node. It should serve as an
     * estimation for the execution time of a high level node. The real execution time needed for a
     * certain high-level node depends on the target architecture and the low-level representation
     * of the node. However, this enum gives a rough estimation that can be used in order to compare
     * nodes based on their execution costs.
     * <p>
     * The default value of the annotation is {@link NodeCycles#CYCLES_UNSET}. It is not required
     * for a node to specify a custom {@link NodeInfo#cycles()} value. However, if a node does not
     * specify a custom value {@code != CYCLES_UNSET}, the value should never be used to argue about
     * the node. Implementations of the cost logic might throw an exception if a node's
     * {@link NodeCycles} value is used although it is {@link NodeCycles#CYCLES_UNSET}.
     */
    NodeCycles cycles() default CYCLES_UNSET;

    /**
     * A rationale for the chosen {@link NodeInfo#cycles()} value.
     */
    String cyclesRationale() default "";

    /**
     * An estimation of the size needed to represent this node in machine code. It should work as an
     * estimation for the number of machine words needed to represent this high-level node in
     * machine code. The real size needed by the generated code for the given high-level node
     * depends the target architecture and the low-level representation of the node. However, this
     * enum gives a rough estimation that can be used in order to compare nodes based on their
     * sizes.
     * <p>
     * The default value of the annotation is {@link NodeSize#SIZE_UNSET}. It is not required for a
     * node to specify a custom {@link NodeInfo#size()} value. However, if a node does not specify a
     * custom value {@code != SIZE_UNSET}, the value should never be used to argue about the node.
     * Implementations of the cost logic might throw an exception if a node's {@link NodeSize} value
     * is used although it is {@link NodeSize#SIZE_UNSET}.
     */
    NodeSize size() default SIZE_UNSET;

    /**
     * A rationale for the chosen {@link NodeInfo#size()} value.
     */
    String sizeRationale() default "";
}
