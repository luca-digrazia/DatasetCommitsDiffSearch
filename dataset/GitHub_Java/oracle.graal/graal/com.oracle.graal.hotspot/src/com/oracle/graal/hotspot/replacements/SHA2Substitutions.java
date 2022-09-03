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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.compiler.common.util.Util.Java8OrEarlier;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;

import com.oracle.graal.api.replacements.ClassSubstitution;
import com.oracle.graal.api.replacements.MethodSubstitution;
import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.nodes.ComputeObjectAddressNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.word.Word;

import jdk.vm.ci.meta.JavaKind;

@ClassSubstitution(className = "sun.security.provider.SHA2", optional = true)
public class SHA2Substitutions {

    static final long stateOffset;

    static final Class<?> shaClass;

    public static final String implCompressName = Java8OrEarlier ? "implCompress" : "implCompress0";

    static {
        try {
            // Need to use the system class loader as com.sun.crypto.provider.AESCrypt
            // is normally loaded by the extension class loader which is not delegated
            // to by the JVMCI class loader.
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            shaClass = Class.forName("sun.security.provider.SHA2", true, cl);
            stateOffset = UnsafeAccess.UNSAFE.objectFieldOffset(shaClass.getDeclaredField("state"));
        } catch (Exception ex) {
            throw new GraalError(ex);
        }
    }

    @MethodSubstitution(isStatic = false)
    static void implCompress0(Object receiver, byte[] buf, int ofs) {
        Object realReceiver = PiNode.piCastNonNull(receiver, shaClass);
        Object state = UnsafeLoadNode.load(realReceiver, stateOffset, JavaKind.Object, LocationIdentity.any());
        Word bufAddr = Word.unsigned(ComputeObjectAddressNode.get(buf, getArrayBaseOffset(JavaKind.Byte) + ofs));
        Word stateAddr = Word.unsigned(ComputeObjectAddressNode.get(state, getArrayBaseOffset(JavaKind.Int)));
        HotSpotBackend.sha2ImplCompressStub(bufAddr, stateAddr);
    }
}
