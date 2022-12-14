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
package com.oracle.graal.hotspot;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.snippets.*;

/**
 * Filters certain intrinsifications based on whether there is underlying hardware support for them.
 */
public class HotSpotSnippetInstaller extends SnippetInstaller {

    private final HotSpotVMConfig config;

    public HotSpotSnippetInstaller(HotSpotRuntime runtime, Assumptions assumptions, TargetDescription target) {
        super(runtime, assumptions, target);
        this.config = runtime.config;
    }

    @Override
    protected void installSubstitution(Method originalMethod, Method substituteMethod) {
        if (substituteMethod.getDeclaringClass() == IntegerSubstitutions.class) {
            if (substituteMethod.getName().equals("bitCount")) {
                if (!config.usePopCountInstruction) {
                    return;
                }
            }
        } else if (substituteMethod.getDeclaringClass() == AESCryptSubstitutions.class) {
            if (!config.useAESIntrinsics) {
                return;
            }
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
        }
        super.installSubstitution(originalMethod, substituteMethod);
    }
}
