/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.UnsignedWord;

public final class Isolates {
    private Isolates() {
    }

    /**
     * An exception thrown in the context of managing isolates.
     *
     * @since 1.0
     */
    public static class IsolateException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public IsolateException(String message) {
            super(message);
        }
    }

    public static class CreateIsolateParameters {
        private UnsignedWord reservedSpaceSize;

        /**
         * Sets the size in bytes for the reserved virtual address space of the new isolate.
         *
         * @since 1.0
         */
        public void setReservedSpaceSize(UnsignedWord reservedSpaceSize) {
            this.reservedSpaceSize = reservedSpaceSize;
        }

        /** @see #setReservedSpaceSize(UnsignedWord) */
        public UnsignedWord getReservedSpaceSize() {
            return reservedSpaceSize;
        }
    }

    /**
     * Create a new isolate, considering the passed parameters (which may be {@code null}). On
     * success, the current thread is attached to the created isolate.
     *
     * @param parameters Optional parameters for the creation of the isolate.
     * @return A pointer to the newly created isolate.
     * @throws IsolateException on error.
     *
     * @since 1.0
     */
    public static Isolate createIsolate(CreateIsolateParameters parameters) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).createIsolate(parameters);
    }

    /**
     * Attaches the current thread to the passed isolate. If the thread has already been attached,
     * the call provides the thread's existing isolate thread structure.
     *
     * @param isolate The isolate to which to attach the current thread.
     * @return A pointer to the structure representing the newly attached isolate thread.
     * @throws IsolateException on error.
     *
     * @since 1.0
     */
    public static IsolateThread attachCurrentThread(Isolate isolate) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).attachCurrentThread(isolate);
    }

    /**
     * Given an isolate to which the current thread is attached, returns the address of the thread's
     * associated isolate thread structure. If the current thread is not attached to the passed
     * isolate, returns {@code null}.
     *
     * @param isolate The isolate for which to retrieve the current thread's corresponding structure
     * @return A pointer to the current thread's structure in the specified isolate or {@code null}
     *         if the thread is not attached to that isolate.
     * @throws IsolateException on error.
     *
     * @since 1.0
     */
    public static IsolateThread getCurrentThread(Isolate isolate) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).getCurrentThread(isolate);
    }

    /**
     * Given an isolate thread structure for the current thread, determines to which isolate it
     * belongs and returns the address of the isolate structure. May return {@code null} if the
     * specified isolate thread structure is no longer valid.
     *
     * @param thread The isolate thread for which to retrieve the isolate.
     * @return A pointer to the isolate, or {@code null}.
     * @throws IsolateException on error.
     *
     * @since 1.0
     */
    public static Isolate getCurrentIsolate(IsolateThread thread) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).getCurrentIsolate(thread);
    }

    /**
     * Detaches the passed isolate thread from its isolate and discards any state or context that is
     * associated with it. At the time of the call, no code may still be executing in the isolate
     * thread's context.
     *
     * @param thread The isolate thread to detach from its isolate.
     * @throws IsolateException on error.
     *
     * @since 1.0
     */
    public static void detachThread(IsolateThread thread) throws IsolateException {
        ImageSingletons.lookup(IsolateSupport.class).detachThread(thread);
    }

    /**
     * Tears down the passed isolate, waiting for any attached threads to detach from it, then
     * discards the isolate's objects, threads, and any other state or context that is associated
     * with it.
     *
     * @param isolate The isolate to tear down.
     * @throws IsolateException on error.
     *
     * @since 1.0
     */
    public static void tearDownIsolate(Isolate isolate) throws IsolateException {
        ImageSingletons.lookup(IsolateSupport.class).tearDownIsolate(isolate);
    }
}
