/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyFindStartRootNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

public final class LazyCaptureGroupsResult extends LazyResult implements JsonConvertible {

    private int[] result = null;
    private final CallTarget findStartCallTarget;
    private final CallTarget captureGroupCallTarget;

    public LazyCaptureGroupsResult(Object input,
                    int fromIndex,
                    int end,
                    CallTarget findStartCallTarget,
                    CallTarget captureGroupCallTarget) {
        super(input, fromIndex, end);
        this.findStartCallTarget = findStartCallTarget;
        this.captureGroupCallTarget = captureGroupCallTarget;
    }

    public LazyCaptureGroupsResult(Object input, int[] result) {
        this(input, -1, -1, null, null);
        this.result = result;
    }

    @Override
    public int getStart(int groupNumber) {
        return result[groupNumber * 2] - 1;
    }

    @Override
    public int getEnd(int groupNumber) {
        return result[groupNumber * 2 + 1] - 1;
    }

    public void setResult(int[] result) {
        this.result = result;
    }

    public int[] getResult() {
        return result;
    }

    public CallTarget getFindStartCallTarget() {
        return findStartCallTarget;
    }

    public CallTarget getCaptureGroupCallTarget() {
        return captureGroupCallTarget;
    }

    /**
     * Creates an arguments array suitable for the lazy calculation of this result's starting index.
     *
     * @return an arguments array suitable for calling the {@link TRegexLazyFindStartRootNode}
     *         contained in {@link #getFindStartCallTarget()}.
     */
    public Object[] createArgsFindStart() {
        return new Object[]{getInput(), getEnd() - 1, getFromIndex()};
    }

    /**
     * Creates an arguments array suitable for the lazy calculation of this result's capture group
     * boundaries.
     *
     * @param start The value returned by the call to the {@link TRegexLazyFindStartRootNode}
     *            contained in {@link #getFindStartCallTarget()}.
     * @return an arguments array suitable for calling the {@link TRegexLazyCaptureGroupsRootNode}
     *         contained in {@link #getCaptureGroupCallTarget()}.
     */
    public Object[] createArgsCG(int start) {
        return new Object[]{this, start + 1, getEnd()};
    }

    /**
     * Creates an arguments array suitable for the lazy calculation of this result's capture group
     * boundaries if there is no find-start call target (this is the case when the expression is
     * sticky or starts with "^").
     *
     * @return an arguments array suitable for calling the {@link TRegexLazyCaptureGroupsRootNode}
     *         contained in {@link #getCaptureGroupCallTarget()}.
     */
    public Object[] createArgsCGNoFindStart() {
        assert findStartCallTarget == null;
        return new Object[]{this, getFromIndex(), getEnd()};
    }

    /**
     * Forces evaluation of this lazy regex result. Do not use this method on any fast paths, use
     * {@link com.oracle.truffle.regex.runtime.nodes.LazyCaptureGroupGetResultNode} instead!
     */
    @TruffleBoundary
    @Override
    public void debugForceEvaluation() {
        if (result == null) {
            if (getFindStartCallTarget() == null) {
                getCaptureGroupCallTarget().call(createArgsCGNoFindStart());
            } else {
                getCaptureGroupCallTarget().call(createArgsCG((int) getFindStartCallTarget().call(createArgsFindStart())));
            }
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (result == null) {
            debugForceEvaluation();
        }
        StringBuilder sb = new StringBuilder("[").append(result[0] - 1);
        for (int i = 1; i < result.length; i++) {
            sb.append(", ").append(result[i] - 1);
        }
        return sb.append("]").toString();
    }

    @TruffleBoundary
    @Override
    public JsonObject toJson() {
        return super.toJson().append(Json.prop("result", Json.array(result)));
    }
}
