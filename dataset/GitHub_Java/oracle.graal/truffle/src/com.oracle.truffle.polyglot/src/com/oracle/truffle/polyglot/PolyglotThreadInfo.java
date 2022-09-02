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
package com.oracle.truffle.polyglot;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.LinkedList;

final class PolyglotThreadInfo {

    static final PolyglotThreadInfo NULL = new PolyglotThreadInfo(null);

    private final Reference<Thread> thread;

    private int enteredCount;
    final LinkedList<Object> explicitContextStack = new LinkedList<>();
    volatile boolean cancelled;

    PolyglotThreadInfo(Thread thread) {
        this.thread = new WeakReference<>(thread);
    }

    Thread getThread() {
        return thread.get();
    }

    boolean isCurrent() {
        return getThread() == Thread.currentThread();
    }

    void enter() {
        assert Thread.currentThread() == getThread();
        enteredCount++;
    }

    boolean isPolyglotThread(PolyglotContextImpl c) {
        if (getThread() instanceof PolyglotThread) {
            return ((PolyglotThread) getThread()).isOwner(c);
        }
        return false;
    }

    void leave() {
        assert Thread.currentThread() == getThread();
        --enteredCount;
    }

    boolean isLastActive() {
        assert Thread.currentThread() == getThread();
        return getThread() != null && enteredCount == 1 && !cancelled;
    }

    boolean isActive() {
        return getThread() != null && enteredCount > 0 && !cancelled;
    }

    @Override
    public String toString() {
        return super.toString() + "[thread=" + getThread() + ", enteredCount=" + enteredCount + ", cancelled=" + cancelled + "]";
    }
}
