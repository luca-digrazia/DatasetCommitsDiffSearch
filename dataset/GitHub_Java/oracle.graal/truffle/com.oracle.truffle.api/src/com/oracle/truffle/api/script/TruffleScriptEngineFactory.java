/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.script;

import javax.script.*;

@Deprecated
/**
 * Tool access to the creation of Truffle execution engines.
 * @deprecated no longer needed and will be removed
 */
public abstract class TruffleScriptEngineFactory implements ScriptEngineFactory {

    // TODO (mlvdv) first step, based on a suggestion from NetBeans
    /**
     * To be called by each concrete factory just after each engine instance is created, presenting
     * an opportunity for an IDE to interrupt in a language-independent way.
     *
     * @param engine a just-created engine
     */
    protected final void engineCreated(ScriptEngine engine) {
    }

}
