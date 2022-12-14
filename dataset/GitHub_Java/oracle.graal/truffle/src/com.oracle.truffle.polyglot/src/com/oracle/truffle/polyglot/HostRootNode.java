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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.PolyglotLanguage.ContextProfile;

abstract class HostRootNode<T> extends RootNode {

    protected static final int OFFSET = 2;

    @CompilationFinal private boolean seenEnter;
    @CompilationFinal private boolean seenNonEnter;

    @CompilationFinal private volatile ContextProfile profile;

    HostRootNode() {
        super(null);
    }

    protected abstract Class<? extends T> getReceiverType();

    @Override
    public final Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        PolyglotLanguageContext languageContext = profileContext(args[0]);
        assert languageContext != null;
        PolyglotContextImpl context = languageContext.context;
        boolean needsEnter = languageContext != null && context.needsEnter();
        Object prev;
        if (needsEnter) {
            if (!seenEnter) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenEnter = true;
            }
            prev = context.enter();
        } else {
            if (!seenNonEnter) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNonEnter = true;
            }
            prev = null;
        }
        try {
            Object[] arguments = frame.getArguments();
            T receiver = getReceiverType().cast(arguments[1]);
            Object result;
            result = executeImpl(languageContext, receiver, arguments);
            assert !(result instanceof TruffleObject);
            return result;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw PolyglotImpl.wrapGuestException((languageContext), e);
        } finally {
            if (needsEnter) {
                context.leave(prev);
            }
        }
    }

    private PolyglotLanguageContext profileContext(Object languageContext) {
        ContextProfile localProfile = this.profile;
        if (localProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profile = localProfile = ((PolyglotLanguageContext) languageContext).language.profile;
        }
        return profile.profile(languageContext);
    }

    protected abstract Object executeImpl(PolyglotLanguageContext languageContext, T receiver, Object[] args);

    protected static CallTarget createTarget(HostRootNode<?> node) {
        return Truffle.getRuntime().createCallTarget(node);
    }

    static <T> T installHostCodeCache(PolyglotLanguageContext languageContext, Object key, T value, Class<T> expectedType) {
        T result = expectedType.cast(languageContext.getLanguageInstance().hostInteropCodeCache.putIfAbsent(key, value));
        if (result != null) {
            return result;
        } else {
            return value;
        }
    }

    static <T> T lookupHostCodeCache(PolyglotLanguageContext languageContext, Object key, Class<T> expectedType) {
        return expectedType.cast(languageContext.getLanguageInstance().hostInteropCodeCache.get(key));
    }

}
