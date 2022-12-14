/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * A node that is repeatedly invoked as part of a Truffle loop control structure. Repeating nodes
 * must extend {@link Node} or a subclass of {@link Node}.
 *
 * Repeating nodes are intended to be implemented by guest language implementations. For a full
 * usage example please see {@link LoopNode}.
 *
 * @see LoopNode
 * @see TruffleRuntime#createLoopNode(RepeatingNode)
 * @since 0.8 or earlier
 */
public interface RepeatingNode extends NodeInterface {
    int CONTINUE_LOOP_STATUS = 0;
    int BREAK_LOOP_STATUS = -1;

    /**
     * Repeatedly invoked by a {@link LoopNode loop node} implementation until the method returns
     * <code>false</code> or throws an exception.
     *
     * @param frame the current execution frame passed through the interpreter
     * @return <code>true</code> if the method should be executed again to complete the loop and
     *         <code>false</code> if it must not.
     * @since 0.8 or earlier
     */
    boolean executeRepeating(VirtualFrame frame);

    /**
     * Repeatedly invoked by a {@link LoopNode loop node} implementation,
     * but allows returning a language-specific loop exit status.
     *
     * @param frame
     * @param frame the current execution frame passed through the interpreter
     * @return <code>CONTINUE_LOOP_STATUS</code> if the method should be executed again to
     *         complete the loop and any other (language-specific) value if it must not.
     */
    default int executeRepeatingWithStatus(VirtualFrame frame) {
        if (executeRepeating(frame)) {
            return CONTINUE_LOOP_STATUS;
        } else {
            return BREAK_LOOP_STATUS;
        }
    }
}
