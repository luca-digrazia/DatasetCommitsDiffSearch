/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import com.oracle.truffle.api.nodes.Node;

/**
 * Base class for all Truffle library specifications.
 *
 * Libraries.
 *
 * <h3>Specifying library messages</h3>
 * <h3>Exporting library messages</h3>
 * <h3>Dynamic Dispatch</h3>
 * <h3>Reflection</h3>
 *
 * @see GenerateLibrary further details on how to use.
 * @since 1.0
 */
public abstract class Library extends Node {

    protected Library() {
    }

    /**
     * Returns <code>true</code> if this library instance supports sending messages with the given
     * receiver. If accepts returns <code>false</code> and a library message is sent anyway then an
     * {@link AssertionError} is thrown if assertions are enabled (-ea). Otherwise a
     * {@link NullPointerException} or {@link ClassCastException} may be thrown by the method.
     * <p>
     * A library that was created using a receiver value i.e. a
     * {@link LibraryFactory#createCached(Object)} only guarantees to accept the value it was
     * constructed with. For other values, the method may return <code>false</code>. Such libraries
     * need to check for acceptance before calling a library method with a receiver. If receiver
     * values are not accepted then a new library library needs to be created or fetched. Dispatched
     * versions of libraries always return <code>true</code> for any value as they take care of
     * dispatching to any receiver type.
     * <p>
     * It is not necessary to call accepts manually for most use cases. Instead the
     * {@link CachedLibrary} should be used instead. For slow-paths the
     * {@link LibraryFactory#getUncached() internally dispatched} versions of the uncached library
     * should be used.
     * <p>
     * The accepts message may be exported by receiver types. When exported it can only be further
     * restricted in addition to the default accepts implementation.
     *
     * @see LibraryFactory for ways how to dispatch libraries.
     * @since 1.0
     */
    public abstract boolean accepts(Object receiver);

}
