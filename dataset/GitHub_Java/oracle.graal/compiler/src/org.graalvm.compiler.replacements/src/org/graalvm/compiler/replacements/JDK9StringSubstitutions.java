/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;

// JaCoCo Exclude

/**
 * Substitutions for {@link String} methods.
 */
@ClassSubstitution(String.class)
public class JDK9StringSubstitutions {

    @MethodSubstitution(isStatic = false)
    @SuppressFBWarnings(value = "ES_COMPARING_PARAMETER_STRING_WITH_EQ", justification = "reference equality on the receiver is what we want")
    public static boolean equals(final String thisString, Object obj) {
        if (thisString == obj) {
            return true;
        }
        if (!(obj instanceof String)) {
            return false;
        }
        String thatString = (String) obj;
        if (thisString.length() != thatString.length()) {
            return false;
        }
        if (thisString.length() == 0) {
            return true;
        }

        final byte[] array1 = getValue(thisString);
        final byte[] array2 = getValue(thatString);

        return ArrayEqualsNode.equals(array1, array2, array1.length);
    }

    /**
     * Will be intrinsified with an {@link InvocationPlugin} to a {@link LoadFieldNode}.
     */
    public static native byte[] getValue(String s);
}
