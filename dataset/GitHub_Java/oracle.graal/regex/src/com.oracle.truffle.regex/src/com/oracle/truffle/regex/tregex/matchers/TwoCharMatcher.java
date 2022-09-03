/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

/**
 * Matcher that matches two characters. Used for things like dot (.) or ignore-case.
 */
public abstract class TwoCharMatcher extends InvertibleCharMatcher {

    private final char c1;
    private final char c2;

    /**
     * Constructs a new {@link TwoCharMatcher}.
     * 
     * @param invert see {@link InvertibleCharMatcher}.
     * @param c1 first character to match.
     * @param c2 second character to match.
     */
    TwoCharMatcher(boolean invert, char c1, char c2) {
        super(invert);
        this.c1 = c1;
        this.c2 = c2;
    }

    public static TwoCharMatcher create(boolean invert, char c1, char c2) {
        return TwoCharMatcherNodeGen.create(invert, c1, c2);
    }

    @Specialization
    public boolean match(char m, boolean compactString) {
        return result((!compactString || c1 < 256) && m == c1 || (!compactString || c2 < 256) && m == c2);
    }

    @Override
    public int estimatedCost() {
        return 2;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public String toString() {
        return modifiersToString() + DebugUtil.charToString(c1) + "||" + DebugUtil.charToString(c2);
    }
}
