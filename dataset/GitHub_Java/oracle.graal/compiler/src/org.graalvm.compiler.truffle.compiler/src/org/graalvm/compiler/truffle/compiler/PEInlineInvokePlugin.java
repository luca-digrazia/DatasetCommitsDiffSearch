/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

public class PEInlineInvokePlugin implements InlineInvokePlugin {

    private TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();

    protected InlineInfo asInlineInfo(ResolvedJavaMethod method) {
        final TruffleCompilerRuntime.InlineKind inlineKind = rt.getInlineKind(method, true);
        switch (inlineKind) {
            case DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION:
                return InlineInfo.DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION;
            case DO_NOT_INLINE_NO_EXCEPTION:
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            case DO_NOT_INLINE_WITH_EXCEPTION:
            case DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION:
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            case INLINE:
                return InlineInfo.createStandardInlineInfo(method);
            default:
                throw new IllegalArgumentException(String.valueOf(inlineKind));
        }
    }
}
