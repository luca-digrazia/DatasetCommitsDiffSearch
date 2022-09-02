/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

/**
 * The client capabilities of a [DeclarationRequest](#DeclarationRequest).
 *
 * Since 3.14.0
 */
public class DeclarationClientCapabilities extends JSONBase {

    DeclarationClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether declaration supports dynamic registration. If this is set to `true` the client
     * supports the new `DeclarationRegistrationOptions` return value for the corresponding server
     * capability as well.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public DeclarationClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * The client supports additional metadata in the form of declaration links.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getLinkSupport() {
        return jsonData.has("linkSupport") ? jsonData.getBoolean("linkSupport") : null;
    }

    public DeclarationClientCapabilities setLinkSupport(Boolean linkSupport) {
        jsonData.putOpt("linkSupport", linkSupport);
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
        DeclarationClientCapabilities other = (DeclarationClientCapabilities) obj;
        if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
            return false;
        }
        if (!Objects.equals(this.getLinkSupport(), other.getLinkSupport())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getDynamicRegistration() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getLinkSupport() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getLinkSupport());
        }
        return hash;
    }

    public static DeclarationClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new DeclarationClientCapabilities(json);
    }
}
