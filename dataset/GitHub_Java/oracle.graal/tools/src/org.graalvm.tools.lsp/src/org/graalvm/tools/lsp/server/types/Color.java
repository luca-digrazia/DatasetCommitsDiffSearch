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
 * Represents a color in RGBA space.
 */
public class Color {

    final JSONObject jsonData;

    Color(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The red component of this color in the range [0-1].
     */
    public int getRed() {
        return jsonData.getInt("red");
    }

    /**
     * The green component of this color in the range [0-1].
     */
    public int getGreen() {
        return jsonData.getInt("green");
    }

    /**
     * The blue component of this color in the range [0-1].
     */
    public int getBlue() {
        return jsonData.getInt("blue");
    }

    /**
     * The alpha component of this color in the range [0-1].
     */
    public int getAlpha() {
        return jsonData.getInt("alpha");
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
        Color other = (Color) obj;
        if (this.getRed() != other.getRed()) {
            return false;
        }
        if (this.getGreen() != other.getGreen()) {
            return false;
        }
        if (this.getBlue() != other.getBlue()) {
            return false;
        }
        if (this.getAlpha() != other.getAlpha()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 53 * hash + Integer.hashCode(this.getRed());
        hash = 53 * hash + Integer.hashCode(this.getGreen());
        hash = 53 * hash + Integer.hashCode(this.getBlue());
        hash = 53 * hash + Integer.hashCode(this.getAlpha());
        return hash;
    }

    /**
     * Creates a new Color literal.
     */
    public static Color create(int red, int green, int blue, int alpha) {
        final JSONObject json = new JSONObject();
        json.put("red", red);
        json.put("green", green);
        json.put("blue", blue);
        json.put("alpha", alpha);
        return new Color(json);
    }
}
