/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class PerformanceWarningTest {

    @Test
    @SuppressWarnings("try")
    public void testVirtualCallWithoutBoundary() throws ExecutionException, TimeoutException {

        // TODO assert performance warnings properly.

        // test single include
        try (OverrideScope scope = OptionValue.override(TruffleCompilerOptions.TraceTrufflePerformanceWarnings, Boolean.TRUE)) {
            OptimizedCallTarget target = (OptimizedCallTarget) GraalTruffleRuntime.getRuntime().createCallTarget(new RootNode(TruffleLanguage.class, null, null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    Interface a = (Interface) frame.getArguments()[0];
                    a.doVirtual();
                    return 0;
                }
            });
            Interface object1 = new VirtualObject1();
            Interface object2 = new VirtualObject2();
            target.call(object1);
            target.call(object2);

            target.compile();
            GraalTruffleRuntime.getRuntime().waitForCompilation(target, 10000L);
        }
    }

    private interface Interface {

        void doVirtual();

    }

    private static class VirtualObject1 implements Interface {
        @Override
        public void doVirtual() {
        }
    }

    private static class VirtualObject2 implements Interface {
        @Override
        public void doVirtual() {
        }
    }

}
