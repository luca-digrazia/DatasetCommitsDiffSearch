/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import java.util.List;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Represents a guest stack trace element.
 *
 * @since 0.27
 */
public final class TruffleStackTraceElement {

    private final Node location;
    private final RootCallTarget target;
    private final Frame frame;

    TruffleStackTraceElement(Node location, RootCallTarget target, Frame frame) {
        this.location = location;
        this.target = target;
        this.frame = frame;
    }

    /**
     * Returns a node representing the callsite on the stack.
     * <p>
     * Returns <code>null</code> if no detailed callsite information is available. This is the case
     * when {@link CallTarget#call(Object...)} is used or for the top-of-the-stack element if
     * {@link TruffleException#getLocation()} returned <code>null</code> or the exception wasn't a
     * {@link TruffleException}.
     * <p>
     * See {@link FrameInstance#getCallNode()} for the relation between callsite and CallTarget.
     *
     * @since 0.27
     **/
    public Node getLocation() {
        return location;
    }

    /**
     * Returns the call target on the stack. Never returns <code>null</code>.
     * <p>
     * See {@link FrameInstance#getCallNode()} for the relation between callsite and CallTarget.
     *
     * @since 0.27
     **/
    public RootCallTarget getTarget() {
        return target;
    }

    /**
     * Returns the materialized frame. Returns <code>null</code> if the initial {@link RootNode}
     * that filled in the stack trace did not request frames to be captured by overriding
     * {@link RootNode#isCaptureFramesForTrace()}.
     *
     * @since 0.31
     */
    public Frame getFrame() {
        return frame;
    }

    /**
     * @deprecated Use {@link TruffleStackTrace#getStacktrace(Throwable)} instead.
     *
     * @since 0.27
     */
    @Deprecated
    public static List<TruffleStackTraceElement> getStackTrace(Throwable throwable) {
        return TruffleStackTrace.getStacktrace(throwable);
    }

    /**
     * @deprecated Use {@link TruffleStackTrace#fillIn(Throwable)} instead.
     *
     * @since 0.27
     */
    @Deprecated
    public static void fillIn(Throwable throwable) {
        TruffleStackTrace.fillIn(throwable);
    }

}
