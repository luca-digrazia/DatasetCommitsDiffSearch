/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * Code Lens options.
 */
public class CodeLensOptions {

    final JSONObject jsonData;

    CodeLensOptions(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * Code lens has a resolve provider as well.
     */
    public Boolean getResolveProvider() {
        return jsonData.optBoolean("resolveProvider");
    }

    public CodeLensOptions setResolveProvider(Boolean resolveProvider) {
        jsonData.putOpt("resolveProvider", resolveProvider);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        CodeLensOptions other = (CodeLensOptions) obj;
        if (this.getResolveProvider() != other.getResolveProvider()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getResolveProvider() != null) {
            hash = 43 * hash + Boolean.hashCode(this.getResolveProvider());
        }
        return hash;
    }

    public static CodeLensOptions create() {
        final JSONObject json = new JSONObject();
        return new CodeLensOptions(json);
    }
}
