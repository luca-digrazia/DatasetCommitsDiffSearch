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
/*
 * Copyright (c) 2007 Sun Microsystems, Inc. All rights reserved. Use is subject to license terms.
 */
package test.com.sun.max.asm;

import junit.framework.*;

@org.junit.runner.RunWith(org.junit.runners.AllTests.class)
public final class AutoTest {

    private AutoTest() {
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(AutoTest.suite());
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(AllTests.class.getPackage().getName());
        suite.addTestSuite(test.com.sun.max.asm.sparc.InternalTest.class);
        suite.addTestSuite(test.com.sun.max.asm.ia32.InternalTest.class);
        suite.addTestSuite(test.com.sun.max.asm.amd64.InternalTest.class);
        suite.addTestSuite(test.com.sun.max.asm.ppc.InternalTest.class);

        suite.addTestSuite(test.com.sun.max.asm.sparc.InliningAndAlignmentTest.class);
        suite.addTestSuite(test.com.sun.max.asm.ia32.InliningAndAlignmentTest.class);
        suite.addTestSuite(test.com.sun.max.asm.amd64.InliningAndAlignmentTest.class);
        suite.addTestSuite(test.com.sun.max.asm.ppc.InliningAndAlignmentTest.class);
        return suite;
    }
}
