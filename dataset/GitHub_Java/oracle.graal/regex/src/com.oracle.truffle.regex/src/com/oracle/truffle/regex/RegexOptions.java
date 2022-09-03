/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;

import java.util.Arrays;

public final class RegexOptions {

    private static final int U180E_WHITESPACE = 1;
    private static final int REGRESSION_TEST_MODE = 1 << 1;

    public static final RegexOptions DEFAULT = new RegexOptions(0, null);

    private final int options;
    private final RegexFlavor flavor;

    private RegexOptions(int options, RegexFlavor flavor) {
        this.options = options;
        this.flavor = flavor;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @CompilerDirectives.TruffleBoundary
    public static RegexOptions parse(String optionsString) throws RegexSyntaxException {
        int options = 0;
        RegexFlavor flavor = null;
        for (String propValue : optionsString.split(",")) {
            if (propValue.isEmpty()) {
                continue;
            }
            int eqlPos = propValue.indexOf('=');
            if (eqlPos < 0) {
                throw optionsSyntaxError(optionsString, propValue + " is not in form 'key=value'");
            }
            String key = propValue.substring(0, eqlPos);
            String value = propValue.substring(eqlPos + 1);
            switch (key) {
                case "U180EWhitespace":
                    options = parseBooleanOption(optionsString, options, key, value, U180E_WHITESPACE);
                    break;
                case "RegressionTestMode":
                    options = parseBooleanOption(optionsString, options, key, value, REGRESSION_TEST_MODE);
                    break;
                case "Flavor":
                    flavor = parseFlavor(optionsString, value);
                    break;
                default:
                    throw optionsSyntaxError(optionsString, "unexpected option " + key);
            }
        }
        return new RegexOptions(options, flavor);
    }

    private static int parseBooleanOption(String optionsString, int options, String key, String value, int flag) throws RegexSyntaxException {
        if (value.equals("true")) {
            return options | flag;
        } else if (!value.equals("false")) {
            throw optionsSyntaxErrorUnexpectedValue(optionsString, key, value, "true", "false");
        }
        return options;
    }

    private static RegexFlavor parseFlavor(String optionsString, String value) throws RegexSyntaxException {
        switch (value) {
            case "PythonStr":
                return PythonFlavor.STR_INSTANCE;
            case "PythonBytes":
                return PythonFlavor.BYTES_INSTANCE;
            case "ECMAScript":
                return null;
            default:
                throw optionsSyntaxErrorUnexpectedValue(optionsString, "Flavor", value, "Python", "ECMAScript");
        }
    }

    private static RegexSyntaxException optionsSyntaxErrorUnexpectedValue(String optionsString, String key, String value, String... expectedValues) {
        return optionsSyntaxError(optionsString, String.format("unexpected value '%s' for option '%s', expected one of %s", value, key, Arrays.toString(expectedValues)));
    }

    private static RegexSyntaxException optionsSyntaxError(String optionsString, String msg) {
        return new RegexSyntaxException(String.format("Invalid options syntax in '%s': %s", optionsString, msg));
    }

    private boolean isBitSet(int bit) {
        return (options & bit) != 0;
    }

    public boolean isU180EWhitespace() {
        return isBitSet(U180E_WHITESPACE);
    }

    public boolean isRegressionTestMode() {
        return isBitSet(REGRESSION_TEST_MODE);
    }

    public RegexFlavor getFlavor() {
        return flavor;
    }

    @Override
    public int hashCode() {
        return options;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof RegexOptions && options == ((RegexOptions) obj).options;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isU180EWhitespace()) {
            sb.append("U180EWhitespace");
        }
        if (isRegressionTestMode()) {
            if (isU180EWhitespace()) {
                sb.append(",");
            }
            sb.append("RegressionTestMode");
        }
        return sb.toString();
    }

    public static final class Builder {

        private int options;
        private RegexFlavor flavor;

        private Builder() {
            this.options = 0;
            this.flavor = null;
        }

        public Builder u180eWhitespace(boolean enabled) {
            updateOption(enabled, U180E_WHITESPACE);
            return this;
        }

        public Builder regressionTestMode(boolean enabled) {
            updateOption(enabled, REGRESSION_TEST_MODE);
            return this;
        }

        public Builder flavor(@SuppressWarnings("hiding") RegexFlavor flavor) {
            this.flavor = flavor;
            return this;
        }

        public RegexOptions build() {
            return new RegexOptions(this.options, this.flavor);
        }

        private void updateOption(boolean enabled, int bitMask) {
            if (enabled) {
                this.options |= bitMask;
            } else {
                this.options &= ~bitMask;
            }
        }
    }
}
