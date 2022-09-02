/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(className = "java.nio.Bits")
final class Target_java_nio_Bits {

    /*
     * The original native method implementation calls back into the Java HotSpot VM, via the
     * function JVM_CopySwapMemory. So this substitution is necessary even when we use the JDK
     * native code. Our implementation is also OS and architecture independent - so we can have this
     * substitution without a @Platforms annotation.
     */
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Substitute
    private static void copySwapMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        MemoryUtil.copyConjointSwap(
                        Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
    }
}
