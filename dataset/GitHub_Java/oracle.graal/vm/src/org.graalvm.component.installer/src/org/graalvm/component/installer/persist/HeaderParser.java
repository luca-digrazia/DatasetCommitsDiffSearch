/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.persist;

import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.DependencyException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.Feedback;

/**
 * Parses OSGI-like metadata in JAR component bundles.
 */
public class HeaderParser {
    private static final String DIRECTIVE_FILTER = "filter"; // NOI18N

    private final String headerName;
    private final Map<String, String> parameters = new HashMap<>();
    private final Map<String, String> directives = new HashMap<>();
    private final Map<String, String> filterValue = new HashMap<>();
    private final Feedback feedback;

    private String header;
    private int pos;
    private String directiveOrParameterName;
    private int contentStart;
    private String versionFilter;

    // static final ResourceBundle BUNDLE =
    // ResourceBundle.getBundle("org.graalvm.component.installer.persist.Bundle");

    public HeaderParser(String headerName, String header, Feedback feedback) {
        this.headerName = headerName;
        this.feedback = feedback;

        if (header != null) {
            // trim whitespaces;
            this.header = header.trim();
        } else {
            this.header = "";
        }
    }

    private MetadataException metaEx(String key, Object... args) {
        return new MetadataException(headerName, feedback.l10n(key, args));
    }

    public HeaderParser mustExist() throws MetadataException {
        if (header == null || header.isEmpty()) {
            throw metaEx("ERROR_HeaderMissing", headerName);
        }
        return this;
    }

    private static boolean isAlphaNum(char c) {
        return (c >= '0' && c <= '9') ||    // NOI18N
                        (c >= 'A' && c <= 'Z') ||    // NOI18N
                        (c >= 'a' && c <= 'z');      // NOI18N
    }

    private static boolean isToken(char c) {
        return isAlphaNum(c) || c == '_' || c == '-'; // NOI18N
    }

    private static boolean isExtended(char c) {
        return isToken(c) || c == '.';
    }

    public boolean getBoolean(Boolean defValue) {
        if (pos >= header.length()) {
            if (defValue == null) {
                throw metaEx("ERROR_HeaderMissing", headerName);
            }
            return defValue;
        } else {
            String s = header.substring(pos).trim().toLowerCase();
            switch (s) {
                case "true":
                    return true;
                case "false":
                    return false;
            }
            throw metaEx("ERROR_HeaderInvalid", headerName, s);
        }
    }

    public String getContents(String defValue) {
        if (pos >= header.length()) {
            return defValue;
        } else {
            return header.substring(pos).trim();
        }
    }

    private void addFilterAttribute(String attrName, String value) {
        if (filterValue.put(attrName, value) != null) {
            throw metaErr("ERROR_DuplicateFilterAttribute");
        }
    }

    private boolean isEmpty() {
        return pos >= header.length();
    }

    public String parseSymbolicName() throws MetadataException {
        return parseNameOrNamespace(HeaderParser::isToken, "ERROR_MissingSymbolicName", "ERROR_InvalidSymbolicName", '.');
    }

    private char next() {
        return pos < header.length() ? header.charAt(pos++) : 0;
    }

    private void advance() {
        pos++;
    }

    private char ch() {
        return isEmpty() ? 0 : header.charAt(pos);
    }

    private String returnCut() {
        String s = cut();
        skipWhitespaces();
        return s;
    }

    private void skipWhitespaces() {
        while (!isEmpty()) {
            if (!Character.isWhitespace(ch())) {
                contentStart = pos;
                return;
            }
            advance();
        }
        contentStart = -1;
    }

    private void skipWithSemicolon() {
        skipWhitespaces();
        if (ch() == ';') {
            advance();
        }
        contentStart = -1;
    }

    private String cut() {
        return cut(0);
    }

    private String cut(int delim) {
        int e = pos - delim;
        return contentStart == -1 || contentStart >= e ? "" : header.substring(contentStart, e); // NOI18N
    }

    private void markContent() {
        contentStart = pos;
    }

    private String readExtendedParameter() throws MetadataException {
        skipWhitespaces();
        while (!isEmpty()) {
            char c = next();
            if (Character.isWhitespace(c)) {
                break;
            }
            if (!isExtended(c)) {
                throw metaEx("ERROR_InvalidParameterSyntax", directiveOrParameterName);
            }
        }
        String s = cut();
        skipWithSemicolon();
        return s;
    }

    private String readQuotedParameter() throws MetadataException {
        markContent();
        while (!isEmpty()) {
            char c = next();
            switch (c) {
                case '"':
                    return cut(1);
                case '\n':
                case '\r':
                case 0:
                    throw metaEx("ERROR_InvalidQuotedString");
                case '\\':
                    c = next();
                    break;
            }
        }
        throw metaEx("ERROR_InvalidQuotedString");
    }

    private String parseArgument() throws MetadataException {
        skipWhitespaces();
        char c = ch();
        if (c == ';') {
            throw metaEx("ERROR_MissingArgument", directiveOrParameterName);
        }
        if (c == '"') { // NOI18N
            advance();
            return readQuotedParameter();
        } else {
            return readExtendedParameter();
        }
    }

    private String parseNameOrNamespace(Predicate<Character> charAcceptor,
                    String missingKeyName, String invalidKeyName, char compDelimiter) throws MetadataException {
        if (header == null || isEmpty()) {
            throw metaEx(missingKeyName);
        }
        skipWhitespaces();
        boolean componentEmpty = true;
        while (!isEmpty()) {
            char c = ch();
            if (c == ';') {
                String s = cut();
                return s;
            }
            advance();
            if (c == compDelimiter) {
                if (componentEmpty) {
                    throw metaEx(invalidKeyName);
                }
                componentEmpty = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                break;
            }
            if (!charAcceptor.test(c)) {
                throw metaEx(invalidKeyName);
            }
            componentEmpty = false;
        }
        return returnCut();
    }

    private String parseNamespace() throws MetadataException {
        return parseNameOrNamespace(HeaderParser::isExtended, "ERROR_MissingCapabilityName", "ERROR_InvalidCapabilityName", (char) 0);
    }

    /**
     * Parses version at the current position.
     */
    public String version() throws MetadataException {
        int versionStart = -1;
        int partCount = 0;
        boolean partContents = false;
        if (isEmpty()) {
            throw metaErr("ERROR_InvalidVersion");
        }
        boolean dash = false;
        while (!isEmpty()) {
            char c = ch();

            if (Character.isWhitespace(c)) {
                if (versionStart != -1) {
                    break;
                }
                advance();
                continue;
            }

            if (c == ';') {
                break;
            }
            advance();
            if (c == '.') {
                if (++partCount > 3 || !partContents) {
                    throw metaErr("ERROR_InvalidVersion");
                }
                partContents = false;
                dash = false;
                continue;
            }
            if (partCount > 0 && partContents && c == '-') {
                dash = true;
                continue;
            }
            if (c >= '0' && c <= '9') {
                if (versionStart == -1) {
                    versionStart = pos - 1;
                }
            } else {
                if (partCount < 1) {
                    throw metaErr("ERROR_InvalidVersion");
                }
                boolean err = false;
                if (partCount >= 3 || dash) {
                    err = !isToken(c);
                } else {
                    err = true;
                }
                if (err) {
                    throw metaErr("ERROR_InvalidVersion");
                }
            }
            partContents = true;
        }
        String v = cut();
        skipWhitespaces();
        if (!isEmpty() || !partContents) {
            throw metaErr("ERROR_InvalidVersion");
        }

        return v;
    }

    private String readExtendedName() {
        skipWhitespaces();
        while (!isEmpty()) {
            char c = ch();
            if (isExtended(c)) {
                advance();
            } else if (Character.isWhitespace(c) || c == ':' || c == '=') {
                break;
            } else {
                throw metaEx("ERROR_InvalidParameterName");
            }
        }
        return returnCut();
    }

    private void parseParameters() {
        while (!isEmpty()) {
            String paramOrDirectiveName = readExtendedName();
            if (paramOrDirectiveName.isEmpty()) {
                throw metaEx("ERROR_InvalidParameterName");
            }

            char c = ch();
            boolean dcolon = c == ':'; // NOI18N
            if (dcolon) {
                advance();
            }
            c = next();
            if (c != '=') { // NOI18N
                throw metaEx("ERROR_InvalidParameterSyntax", paramOrDirectiveName);
            }
            (dcolon ? directives : parameters).put(paramOrDirectiveName, parseArgument());
        }
    }

    private void replaceInputText(String text) {
        this.header = text;
        this.pos = 0;
    }

    private MetadataException metaErr(String key, Object... args) throws MetadataException {
        throw metaEx(key, args);
    }

    private MetadataException filterError() throws MetadataException {
        throw metaErr("ERROR_InvalidFilterSpecification");
    }

    private void parseFilterConjunction() {
        skipWhitespaces();
        char c = next();
        while (c == '(') {
            parseFilterContent();
            c = next();
        }
        if (c != ')') {
            throw filterError();
        }
    }

    private void parseFilterClause() {
        skipWhitespaces();
        int lastPos = -1;
        W: while (!isEmpty()) {
            char c = ch();
            if (Character.isWhitespace(c)) {
                if (lastPos == -1) {
                    lastPos = pos;
                }
                continue;
            }
            switch (c) {
                case '=':
                case '<':
                case '>':
                case '~':
                case '(':
                case ')':
                    break W;
            }
            lastPos = -1;
            advance();
        }

        String attributeName = returnCut();
        char c = next();
        if (c != '=') {
            throw metaErr("ERROR_UnsupportedFilterOperation");
        }
        c = ch();
        if (c == '*') {
            throw metaErr("ERROR_UnsupportedFilterOperation");
        }
        markContent();
        while (!isEmpty()) {
            c = next();
            if (c == ')') {
                addFilterAttribute(attributeName, cut(1));
                skipWhitespaces();
                return;
            }

            switch (c) {
                case '\\':
                    c = next();
                    if (c == 0) {
                        throw filterError();
                    }
                    break;
                case '*':
                    throw metaErr("ERROR_UnsupportedFilterOperation");
                case '(':
                case '<':
                case '>':
                case '~':
                case '=':
                    throw filterError();
            }
        }
        throw filterError();
    }

    private void parseFilterContent() {
        skipWhitespaces();
        char o = ch();
        if (o == '&') {
            advance();
            parseFilterConjunction();
        } else if (isExtended(o)) {
            parseFilterClause();
        } else {
            throw metaErr("ERROR_InvalidFilterSpecification");
        }
    }

    private void parseFilterSpecification() {
        skipWhitespaces();
        if (isEmpty()) {
            throw filterError();
        }
        char c = next();
        if (c == '(') {
            parseFilterContent();
            skipWhitespaces();
            if (!isEmpty()) {
                throw metaErr("ERROR_InvalidFilterSpecification");
            }
        } else {
            throw filterError();
        }
    }

    /**
     * Parses required capabilities string.
     *
     * org.graalvm; filter:="(&(graalvm_version=0.32)(os_name=linux)(os_arch=amd64))"
     * 
     * @return graal capabilities
     * @throws MetadataException
     */
    public Map<String, String> parseRequiredCapabilities() {
        String namespace = parseNamespace();

        char c = next();
        if (c != ';' && c != 0) {
            throw metaErr("ERROR_InvalidFilterSpecification");
        }

        if (!BundleConstants.GRAALVM_CAPABILITY.equals(namespace)) {
            // unsupported capability
            throw new DependencyException(namespace, null, null, feedback.l10n("ERROR_UnknownCapability"));
        }
        parseParameters();

        if (!parameters.isEmpty()) {
            throw metaErr("ERROR_UnsupportedParameters");
        }
        versionFilter = directives.remove(DIRECTIVE_FILTER);
        if (!directives.isEmpty()) {
            throw metaErr("ERROR_UnsupportedDirectives");
        }
        if (versionFilter == null) {
            throw metaErr("ERROR_MissingVersionFilter");
        }

        // replace the input text, the rest of header will be ignored
        replaceInputText(versionFilter);
        parseFilterSpecification();

        return filterValue;
    }
}
