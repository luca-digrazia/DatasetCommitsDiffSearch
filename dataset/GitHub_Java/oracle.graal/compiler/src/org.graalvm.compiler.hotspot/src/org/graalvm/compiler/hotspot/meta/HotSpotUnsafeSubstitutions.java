/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.doingUnsafeAccessOffset;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

@ClassSubstitution(className = {"jdk.internal.misc.Unsafe", "sun.misc.Unsafe"})
public class HotSpotUnsafeSubstitutions {

    public static final String copyMemoryName = JavaVersionUtil.JAVA_SPEC <= 8 ? "copyMemory" : "copyMemory0";

    @SuppressWarnings("unused")
    @MethodSubstitution(isStatic = false)
    static void copyMemory(Object receiver, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        Word srcAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(srcBase, srcOffset));
        Word dstAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(destBase, destOffset));
        Word size = WordFactory.signed(bytes);

        HotSpotBackend.unsafeArraycopy(srcAddr, dstAddr, size);
    }

    @SuppressWarnings("unused")
    @MethodSubstitution(value = "copyMemory", isStatic = false)
    static void copyMemoryGuarded(Object receiver, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        Word srcAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(srcBase, srcOffset));
        Word dstAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(destBase, destOffset));
        Word size = WordFactory.signed(bytes);
        Word javaThread = CurrentJavaThreadNode.get();
        int offset = doingUnsafeAccessOffset(INJECTED_VMCONFIG);
        LocationIdentity any = LocationIdentity.any();

        /* Set doingUnsafeAccess to guard and handle unsafe memory access failures */
        javaThread.writeByte(offset, (byte) 1, any);
        HotSpotBackend.unsafeArraycopy(srcAddr, dstAddr, size);
        /* Reset doingUnsafeAccess */
        javaThread.writeByte(offset, (byte) 0, any);
    }
}
