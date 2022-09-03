/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

/**
 * Description of contiguous section of text within a {@link Source} of program code.; supports
 * multiple modes of access to the text and its location.
 * <p>
 * Two source sections are considered equal if their sources, start and length are equal.
 * {@link #isAvailable() Unavailable} source sections are never equal with available ones. Source
 * sections can be used as keys in hash maps. Unavailable source sections are equal if their sources
 * are equal.
 *
 * @see Source#createSection(String, int, int, int)
 * @see Source#createSection(String, int, int)
 * @see Source#createSection(String, int)
 * @see Source#createUnavailableSection()
 * @since 0.8 or earlier
 */
public final class SourceSection {

    private static final String UNKNOWN = "<unknown>"; // deprecated
    private final Source source;
    private final int charIndex;
    private final int charLength; // -1 indicates unavailable

    private final String identifier; // deprecated
    private final int startLine; // deprecated
    private final int startColumn; // deprecated
    private final String kind; // deprecated

    SourceSection(Source source, String identifier, int startLine, int startColumn, int charIndex, int charLength) {
        this.kind = null;
        this.source = source;
        this.identifier = identifier;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.charIndex = charIndex;
        this.charLength = charLength;
    }

    SourceSection(Source source, int charIndex, int charLength) {
        this.source = source;
        this.charIndex = charIndex;
        this.charLength = charLength;
        // legacy support
        this.kind = null;
        this.identifier = null;
        this.startLine = -1;
        this.startColumn = -1;
    }

    SourceSection(Source source) {
        this.source = source;
        this.charIndex = 0;
        this.charLength = -1;
        // legacy support
        this.kind = null;
        this.identifier = null;
        this.startLine = -1;
        this.startColumn = -1;
    }

    /** Special representation for unknown source. */
    private SourceSection(String kind, String identifier) {
        this.source = null;
        this.kind = kind;
        this.identifier = identifier;
        this.startLine = -1;
        this.startColumn = -1;
        this.charIndex = -1;
        this.charLength = -1;
    }

    /**
     * Returns whether this is a special instance that signifies that source information is
     * available. Unavailable source sections can be created using
     * {@link Source#createUnavailableSection()}. Available source sections are never equal to
     * unavailable source sections. Unavailable source sections return the same indices and lengths
     * as empty source sections starting at character index <code>0</code>.
     *
     * @see Source#createUnavailableSection()
     * @since 0.18
     */
    public boolean isAvailable() {
        // TODO this check can be simplified when the deprecated method #createUnavailable was
        // removed. then source cannot become null anymore
        return charLength != -1 && source != null;
    }

    /**
     * Returns whether the source section is in bounds of the {@link #getSource() source}
     * {@link Source#getCode() code}. Please note that calling this method causes the
     * {@link Source#getCode() code} of the {@link #getSource() source} to be loaded if it was not
     * yet loaded.
     *
     * @since 0.18
     */
    public boolean isValid() {
        return isAvailable() ? (charIndex + charLength <= getSource().getCode().length()) : false;
    }

    /**
     * Representation of the source program that contains this section.
     *
     * @return the source object
     * @since 0.8 or earlier
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns 1-based line number of the first character in this section (inclusive). Returns
     * <code>1</code> for {@link #isValid() invalid} or {@link #isAvailable() unavailable} source
     * sections. Please note that calling this method causes the {@link Source#getCode() code} of
     * the {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting line number
     * @since 0.8 or earlier
     */
    public int getStartLine() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        if (startLine == -1) {
            return source.getLineNumber(getCharIndex());
        }
        return startLine;
    }

    /**
     * Gets a representation of the {@link #getStartLine() first line} of the section, suitable for
     * a hash key. Please note that calling this method causes the {@link Source#getCode() code} of
     * the {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return first line of the section
     * @since 0.8 or earlier
     * @deprecated without replacement
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public LineLocation getLineLocation() {
        if (!isValid()) {
            return null;
        }
        return source.createLineLocation(getStartLine());
    }

    /**
     * Returns the 1-based column number of the first character in this section (inclusive). Returns
     * <code>1</code> for {@link #isValid() invalid} or {@link #isAvailable() unavailable} source
     * sections. Please note that calling this method causes the {@link Source#getCode() code} of
     * the {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting column number
     * @since 0.8 or earlier
     */
    public int getStartColumn() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        if (startColumn == -1) {
            return source.getColumnNumber(getCharIndex());
        }
        return startColumn;
    }

    /**
     * Returns 1-based line number of the last character in this section (inclusive). Returns
     * <code>1</code> for {@link #isValid() invalid} or {@link #isAvailable() unavailable} source
     * sections. Please note that calling this method causes the {@link Source#getCode() code} of
     * the {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting line number
     * @since 0.8 or earlier
     */
    public int getEndLine() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        return source.getLineNumber(getCharIndex() + Math.max(0, getCharLength() - 1));
    }

    /**
     * Returns the 1-based column number of the last character in this section (inclusive). Returns
     * <code>1</code> for {@link #isValid() invalid} or {@link #isAvailable() unavailable} source
     * sections. Please note that calling this method causes the {@link Source#getCode() code} of
     * the {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting column number
     * @since 0.8 or earlier
     */
    public int getEndColumn() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        return source.getColumnNumber(getCharIndex() + Math.max(0, getCharLength() - 1));
    }

    /**
     * Returns the 0-based index of the first character in this section. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections. Please note that calling this method does
     * not cause the {@link Source#getCode() code} of the {@link #getSource() source} to be loaded.
     * The returned index might be out of bounds of the source code. Use {@link #isValid()} to find
     * out if a source section is within its bounds.
     *
     * @return the starting character index
     * @since 0.8 or earlier
     */
    public int getCharIndex() {
        return charIndex;
    }

    /**
     * Returns the length of this section in characters. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections. Please note that calling this method does
     * not cause the {@link Source#getCode() code} of the {@link #getSource() source} to be loaded.
     * The returned length might be out of bounds of the source code. Use {@link #isValid()} to find
     * out if a source section is within its bounds.
     *
     * @return the number of characters in the section
     * @since 0.8 or earlier
     */
    public int getCharLength() {
        if (source == null) {
            return -1;
        }
        return charLength == -1 ? 0 : charLength;
    }

    /**
     * Returns the index of the text position immediately following the last character in the
     * section. Returns <code>0</code> for {@link #isAvailable() unavailable} source sections.
     * Please note that calling this method does not cause the {@link Source#getCode() code} of the
     * {@link #getSource() source} to be loaded. The returned index might be out of bounds of the
     * source code. Use {@link #isValid()} to find out if a source section is within its bounds.
     *
     * @return the end position of the section
     * @since 0.8 or earlier
     */
    public int getCharEndIndex() {
        if (source == null) {
            return -1;
        }
        return getCharIndex() + getCharLength();
    }

    /**
     * Returns terse text describing this source section, typically used for printing the section.
     *
     * @return the identifier of the section
     * @since 0.8 or earlier
     * @deprecated without replacement
     */
    @Deprecated
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Returns the source code fragment described by this section. Returns an empty string for
     * {@link #isValid() invalid} or {@link #isAvailable() unavailable} source sections. Please note
     * that calling this method causes the {@link Source#getCode() code} of the {@link #getSource()
     * source} to be loaded if it was not yet loaded.
     *
     * @return the code as a string
     * @since 0.8 or earlier
     * @see Source#getCode(int, int)
     */
    public String getCode() {
        // TODO remove check for source == null if #createUnavailableSourceSection is removed.
        if (source == null) {
            return "<unavailable>";
        }
        if (!isValid()) {
            return "";
        }
        return source.getCode(getCharIndex(), getCharLength());
    }

    /**
     * Returns a short description of the source section, using just the file name, rather than its
     * full path.
     *
     * @return a short description of the source section formatted as {@code <filename>:<line>}.
     * @since 0.8 or earlier
     * @deprecated replace with <code>String.format("%s:%d", sourceSection.getSource().getName(),
     *             sourceSection.getStartLine())</code>
     */
    @Deprecated
    public String getShortDescription() {
        if (source == null) {
            return kind == null ? UNKNOWN : kind;
        }
        return String.format("%s:%d", source.getName(), getStartLine());
    }

    /**
     * Returns an implementation-defined string representation of this source section to be used for
     * debugging purposes only.
     *
     * @see #getCode()
     * @see #getShortDescription()
     * @since 0.8 or earlier
     */
    @Override
    public String toString() {
        if (source == null) {
            return kind == null ? UNKNOWN : kind;
        } else {
            StringBuilder b = new StringBuilder();
            b.append("SourceSection(source=").append(getSource().getName());
            if (isAvailable()) {
                b.append(", index=").append(getCharIndex());
                b.append(", length=").append(getCharLength());
                if (isValid()) {
                    b.append(", code=").append(getCode().replaceAll("\\n", "\\\\n"));
                } else {
                    b.append(", valid=false");
                }
            } else {
                b.append(" available=false");
            }
            b.append(")");
            return b.toString();
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + charIndex;
        result = prime * result + charLength;
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + startColumn;
        result = prime * result + startLine;
        return result;
    }

    /** @since 0.8 or earlier */
    @Override
    @SuppressFBWarnings("ES_COMPARING_STRINGS_WITH_EQ")
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SourceSection)) {
            return false;
        }
        SourceSection other = (SourceSection) obj;
        if (charIndex != other.charIndex) {
            return false;
        }
        if (charLength != other.charLength) {
            return false;
        }
        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
            return false;
        }
        if (startColumn != other.startColumn) {
            return false;
        }
        if (startLine != other.startLine) {
            return false;
        }
        return true;
    }

    /**
     * Placeholder for source that is unavailable, e.g. for language <em>builtins</em>. The
     * <code>SourceSection</code> created by this method returns <code>null</code> when queried for
     * a {@link #getSource()} - regular source sections created via one of
     * {@link Source#createSection(java.lang.String, int) Source.createSection} methods have a non-
     * <code>null</code> source.
     *
     * @param kind the general category, e.g. "JS builtin"
     * @param name specific name for this section
     * @return source section which is mostly <em>empty</em>
     * @since 0.8 or earlier
     * @deprecated use a dedicated named source for unavailable sources and call
     *             {@link Source#createUnavailableSection()} instead.
     */
    @Deprecated
    public static SourceSection createUnavailable(String kind, String name) {
        return new SourceSection(kind, name == null ? UNKNOWN : name);
    }

}
