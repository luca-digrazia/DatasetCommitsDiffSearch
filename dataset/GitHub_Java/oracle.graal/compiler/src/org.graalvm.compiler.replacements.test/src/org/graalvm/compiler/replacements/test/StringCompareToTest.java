/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests compareTo method intrinsic.
 */
public class StringCompareToTest extends MethodSubstitutionTest {

    private final ResolvedJavaMethod realMethod;
    private final ResolvedJavaMethod testMethod;
    private final InstalledCode testCode;

    private final String[] testData = new String[]{
                    "A", "\uFF21", "AB", "A", "a", "Ab", "AA", "\uFF21",
                    "A\uFF21", "ABC", "AB", "ABcD", "ABCD\uFF21\uFF21", "ABCD\uFF21", "ABCDEFG\uFF21", "ABCD",
                    "ABCDEFGH\uFF21\uFF21", "\uFF22", "\uFF21\uFF22", "\uFF21A",
                    "\uFF21\uFF21",
                    "\u043c\u0430\u043c\u0430\u0020\u043c\u044b\u043b\u0430\u0020\u0440\u0430\u043c\u0443\u002c\u0020\u0440\u0430\u043c\u0430\u0020\u0441\u044a\u0435\u043b\u0430\u0020\u043c\u0430\u043c\u0443",
                    "crazy dog jumps over laszy fox"
    };

    /**
     * Initialize variables
     */
    public StringCompareToTest() {
        realMethod = getResolvedJavaMethod(String.class, "compareTo", String.class);
        testMethod = getResolvedJavaMethod("stringCompareTo");
        StructuredGraph graph = testGraph("stringCompareTo");

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getSubstitution(realMethod, -1, false);
        if (replacement == null) {
            assertInGraph(graph, ArrayCompareToNode.class);
        }

        // Force compilation
        testCode = getCode(testMethod);
        Assert.assertNotNull(testCode);
    }

    private void executeStringCompareTo(String s0, String s1) {
        Object expected = invokeSafe(realMethod, s0, s1);
        // Verify that the original method and the substitution produce the same value
        assertDeepEquals(expected, invokeSafe(testMethod, null, s0, s1));
        // Verify that the generated code and the original produce the same value
        assertDeepEquals(expected, executeVarargsSafe(testCode, s0, s1));
    }

    public static int stringCompareTo(String a, String b) {
        return a.compareTo(b);
    }

    @Test
    public void testEqualString() {
        String s = "equal-string";
        executeStringCompareTo(s, new String(s.toCharArray()));
    }

    @Test
    public void testDifferentString() {
        executeStringCompareTo("some-string", "different-string");
    }

    @Test
    public void testAllStrings() {
        for (String s0 : testData) {
            for (String s1 : testData) {
                executeStringCompareTo(s0, s1);
            }
        }
    }
}
