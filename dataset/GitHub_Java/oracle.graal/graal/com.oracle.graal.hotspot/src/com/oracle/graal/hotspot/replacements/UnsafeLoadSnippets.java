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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

public class UnsafeLoadSnippets implements Snippets {

    @Snippet
    public static Object lowerUnsafeLoad(Object object, long offset, int disp) {
        long displacement = disp + offset;
        if (object instanceof java.lang.ref.Reference && referentOffset() == displacement) {
            return Word.fromObject(object).readObject((int) displacement, 1, true);
        } else {
            return Word.fromObject(object).readObject((int) displacement, 0, true);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo unsafeLoad = snippet(UnsafeLoadSnippets.class, "lowerUnsafeLoad");

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target);
        }

        public void lower(UnsafeLoadNode load, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(unsafeLoad);
            args.add("object", load.object());
            args.add("offset", load.offset());
            args.add("disp", load.displacement());
            template(args).instantiate(runtime, load, DEFAULT_REPLACER, args);
        }
    }
}
