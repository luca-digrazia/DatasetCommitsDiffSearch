/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyTestLanguage.LanguageContext;

@TruffleLanguage.Registration(id = ProxyTestLanguage.ID, name = ProxyTestLanguage.ID, version = "1.0", mimeType = ProxyTestLanguage.MIME)
public class ProxyTestLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "ProxyTestLanguage";
    static final String MIME = "ProxyTestLanguage";
    static Function<Env, Object> runinside;

    static class LanguageContext {

        Env env;

    }

    public static LanguageContext getContext() {
        return getCurrentContext(ProxyTestLanguage.class);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Object result = "null result";
        if (runinside != null) {
            try {
                result = runinside.apply(getContext().env);
            } finally {
                runinside = null;
            }
        }
        if (result == null) {
            result = "null result";
        }
        final Object finalResult = result;
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                return finalResult;
            }
        });
    }

    @Override
    protected LanguageContext createContext(Env env) {
        LanguageContext ctx = new ProxyTestLanguage.LanguageContext();
        ctx.env = env;
        return ctx;
    }

    @Override
    protected Object lookupSymbol(LanguageContext context, String symbolName) {
        return super.lookupSymbol(context, symbolName);
    }

    @Override
    protected Object getLanguageGlobal(LanguageContext context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}
