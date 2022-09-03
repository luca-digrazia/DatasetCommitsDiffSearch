/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.referentOffset;
import static com.oracle.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import com.oracle.graal.api.replacements.Snippet;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.extended.FixedValueAnchorNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.Snippets;
import com.oracle.graal.word.Word;

import jdk.vm.ci.code.TargetDescription;

public class UnsafeLoadSnippets implements Snippets {

    @Snippet
    public static Object lowerUnsafeLoad(Object object, long offset) {
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        if (object instanceof java.lang.ref.Reference && referentOffset() == offset) {
            return Word.objectToTrackedPointer(fixedObject).readObject((int) offset, BarrierType.PRECISE);
        } else {
            return Word.objectToTrackedPointer(fixedObject).readObject((int) offset, BarrierType.NONE);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo unsafeLoad = snippet(UnsafeLoadSnippets.class, "lowerUnsafeLoad");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(UnsafeLoadNode load, LoweringTool tool) {
            Arguments args = new Arguments(unsafeLoad, load.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", load.object());
            args.add("offset", load.offset());
            template(args).instantiate(providers.getMetaAccess(), load, DEFAULT_REPLACER, args);
        }
    }
}
