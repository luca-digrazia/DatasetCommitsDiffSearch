/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.ref.*;
import java.net.*;
import java.util.*;

import com.oracle.truffle.api.instrument.*;

/**
 * Representation of a guest language source code unit and its contents. Sources originate in
 * several ways:
 * <ul>
 * <li><strong>Literal:</strong> An anonymous text string: not named and not indexed. These should
 * be considered value objects; equality is defined based on contents.<br>
 * See {@link Source#fromText(CharSequence, String)}</li>
 * <p>
 * <li><strong>Named Literal:</strong> A text string that can be retrieved by name as if it were a
 * file, but without any assumption that the name is related to a file path. Creating a new literal
 * with an already existing name will replace its predecessor in the index.<br>
 * See {@link Source#fromNamedText(CharSequence, String)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>File:</strong> Each file is represented as a canonical object, indexed by the
 * absolute, canonical path name of the file. File contents are <em>read lazily</em> and contents
 * optionally <em>cached</em>. <br>
 * See {@link Source#fromFileName(String)}<br>
 * See {@link Source#fromFileName(String, boolean)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>URL:</strong> Each URL source is represented as a canonical object, indexed by the
 * URL. Contents are <em>read eagerly</em> and <em>cached</em>. <br>
 * See {@link Source#fromURL(URL, String)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>Reader:</strong> Contents are <em>read eagerly</em> and treated as an anonymous
 * (non-indexed) <em>Literal</em> . <br>
 * See {@link Source#fromReader(Reader, String)}</li>
 * <p>
 * <li><strong>Sub-Source:</strong> A representation of the contents of a sub-range of another
 * {@link Source}.<br>
 * See @link {@link Source#subSource(Source, int, int)}<br>
 * See @link {@link Source#subSource(Source, int)}</li>
 * <p>
 * <li><strong>AppendableSource:</strong> Literal contents are provided by the client,
 * incrementally, after the instance is created.<br>
 * See {@link Source#fromAppendableText(String)}<br>
 * See {@link Source#fromNamedAppendableText(String)}</li>
 * </ul>
 * <p>
 * <strong>File cache:</strong>
 * <ol>
 * <li>File content caching is optional, <em>on</em> by default.</li>
 * <li>The first access to source file contents will result in the contents being read, and (if
 * enabled) cached.</li>
 * <li>If file contents have been cached, access to contents via {@link Source#getInputStream()} or
 * {@link Source#getReader()} will be provided from the cache.</li>
 * <li>Any access to file contents via the cache will result in a timestamp check and possible cache
 * reload.</li>
 * </ol>
 * <p>
 *
 * @see SourceTag
 * @see SourceListener
 */
public abstract class Source {

    // TODO (mlvdv) consider canonicalizing and reusing SourceSection instances
    // TOOD (mlvdv) connect SourceSections into a spatial tree for fast geometric lookup

    public enum Tags implements SourceTag {

        /**
         * From bytes.
         */
        FROM_BYTES("bytes", "read from bytes"),

        /**
         * Read from a file.
         */
        FROM_FILE("file", "read from a file"),

        /**
         * From literal text.
         */
        FROM_LITERAL("literal", "from literal text"),

        /**
         * From a {@linkplain java.io.Reader Reader}.
         */
        FROM_READER("reader", "read from a Java Reader"),

        /**
         * Read from a URL.
         */
        FROM_URL("URL", "read from a URL");

        private final String name;
        private final String description;

        private Tags(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

    }

    /**
     * All Sources that have been created.
     */
    private static final List<WeakReference<Source>> allSources = Collections.synchronizedList(new ArrayList<WeakReference<Source>>());

    /**
     * Index of all named sources.
     */
    private static final Map<String, WeakReference<Source>> nameToSource = new HashMap<>();

    private static boolean fileCacheEnabled = true;

    private static final List<SourceListener> sourceListeners = new ArrayList<>();

    /**
     * Locates an existing instance by the name under which it was indexed.
     */
    public static Source find(String name) {
        final WeakReference<Source> nameRef = nameToSource.get(name);
        return nameRef == null ? null : nameRef.get();
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     *
     * @param fileName name
     * @param reset forces any existing {@link Source} cache to be cleared, forcing a re-read
     * @return canonical representation of the file's contents.
     * @throws IOException if the file can not be read
     */
    public static Source fromFileName(String fileName, boolean reset) throws IOException {

        final WeakReference<Source> nameRef = nameToSource.get(fileName);
        Source source = nameRef == null ? null : nameRef.get();
        if (source == null) {
            final File file = new File(fileName);
            if (!file.canRead()) {
                throw new IOException("Can't read file " + fileName);
            }
            final String path = file.getCanonicalPath();
            final WeakReference<Source> pathRef = nameToSource.get(path);
            source = pathRef == null ? null : pathRef.get();
            if (source == null) {
                source = new FileSource(file, fileName, path);
                nameToSource.put(path, new WeakReference<>(source));
            }
        }
        if (reset) {
            source.reset();
        }
        notifyNewSource(source).tagAs(Tags.FROM_FILE);
        return source;
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     *
     * @param fileName name
     * @return canonical representation of the file's contents.
     * @throws IOException if the file can not be read
     */
    public static Source fromFileName(String fileName) throws IOException {
        return fromFileName(fileName, false);
    }

    /**
     * Gets the canonical representation of a source file, whose contents have already been read and
     * need not be read again. It is confirmed that the file resolves to a file name, so it can be
     * indexed by canonical path. It is not confirmed that the text supplied agrees with the file's
     * contents or even whether the file is readable.
     *
     * @param chars textual source code already read from the file
     * @param fileName
     * @return canonical representation of the file's contents.
     * @throws IOException if the file cannot be found
     */
    public static Source fromFileName(CharSequence chars, String fileName) throws IOException {

        final WeakReference<Source> nameRef = nameToSource.get(fileName);
        Source source = nameRef == null ? null : nameRef.get();
        if (source == null) {
            final File file = new File(fileName);
            // We are going to trust that the fileName is readable.
            final String path = file.getCanonicalPath();
            final WeakReference<Source> pathRef = nameToSource.get(path);
            source = pathRef == null ? null : pathRef.get();
            if (source == null) {
                source = new FileSource(file, fileName, path, chars);
                nameToSource.put(path, new WeakReference<>(source));
            }
        }
        notifyNewSource(source).tagAs(Tags.FROM_FILE);
        return source;
    }

    /**
     * Creates an anonymous source from literal text: not named and not indexed.
     *
     * @param chars textual source code
     * @param description a note about the origin, for error messages and debugging
     * @return a newly created, non-indexed source representation
     */
    public static Source fromText(CharSequence chars, String description) {
        assert chars != null;
        final LiteralSource source = new LiteralSource(description, chars.toString());
        notifyNewSource(source).tagAs(Tags.FROM_LITERAL);
        return source;
    }

    /**
     * Creates an anonymous source from literal text that is provided incrementally after creation:
     * not named and not indexed.
     *
     * @param description a note about the origin, for error messages and debugging
     * @return a newly created, non-indexed, initially empty, appendable source representation
     */
    public static Source fromAppendableText(String description) {
        final Source source = new AppendableLiteralSource(description);
        notifyNewSource(source).tagAs(Tags.FROM_LITERAL);
        return source;
    }

    /**
     * Creates a source from literal text that can be retrieved by name, with no assumptions about
     * the structure or meaning of the name. If the name is already in the index, the new instance
     * will replace the previously existing instance in the index.
     *
     * @param chars textual source code
     * @param name string to use for indexing/lookup
     * @return a newly created, source representation
     */
    public static Source fromNamedText(CharSequence chars, String name) {
        final Source source = new LiteralSource(name, chars.toString());
        nameToSource.put(name, new WeakReference<>(source));
        notifyNewSource(source).tagAs(Tags.FROM_LITERAL);
        return source;
    }

    /**
     * Creates a source from literal text that is provided incrementally after creation and which
     * can be retrieved by name, with no assumptions about the structure or meaning of the name. If
     * the name is already in the index, the new instance will replace the previously existing
     * instance in the index.
     *
     * @param name string to use for indexing/lookup
     * @return a newly created, indexed, initially empty, appendable source representation
     */
    public static Source fromNamedAppendableText(String name) {
        final Source source = new AppendableLiteralSource(name);
        nameToSource.put(name, new WeakReference<>(source));
        notifyNewSource(source).tagAs(Tags.FROM_LITERAL);
        return source;
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range of
     * an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @param length the number of characters in the sub-range
     * @return a new instance representing a sub-range of another Source
     * @throws IllegalArgumentException if the specified sub-range is not contained in the base
     */
    public static Source subSource(Source base, int baseCharIndex, int length) {
        final SubSource subSource = SubSource.create(base, baseCharIndex, length);
        return subSource;
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range at
     * the end of an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @return a new instance representing a sub-range at the end of another Source
     * @throws IllegalArgumentException if the index is out of range
     */
    public static Source subSource(Source base, int baseCharIndex) {
        return subSource(base, baseCharIndex, base.getLength() - baseCharIndex);
    }

    /**
     * Creates a source whose contents will be read immediately from a URL and cached.
     *
     * @param url
     * @param description identifies the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     */
    public static Source fromURL(URL url, String description) throws IOException {
        final URLSource source = URLSource.get(url, description);
        notifyNewSource(source).tagAs(Tags.FROM_URL);
        return source;
    }

    /**
     * Creates a source whose contents will be read immediately and cached.
     *
     * @param reader
     * @param description a note about the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     */
    public static Source fromReader(Reader reader, String description) throws IOException {
        final LiteralSource source = new LiteralSource(description, read(reader));
        notifyNewSource(source).tagAs(Tags.FROM_READER);
        return source;
    }

    /**
     * Creates a source from raw bytes. This can be used if the encoding of strings in your language
     * is not compatible with Java strings, or if your parser returns byte indices instead of
     * character indices. The returned source is then indexed by byte, not by character.
     *
     * @param bytes the raw bytes of the source
     * @param description a note about the origin, possibly useful for debugging
     * @param decoder how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     */
    public static Source fromBytes(byte[] bytes, String description, BytesDecoder decoder) {
        return fromBytes(bytes, 0, bytes.length, description, decoder);
    }

    /**
     * Creates a source from raw bytes. This can be used if the encoding of strings in your language
     * is not compatible with Java strings, or if your parser returns byte indices instead of
     * character indices. The returned source is then indexed by byte, not by character. Offsets are
     * relative to byteIndex.
     *
     * @param bytes the raw bytes of the source
     * @param byteIndex where the string starts in the byte array
     * @param length the length of the string in the byte array
     * @param description a note about the origin, possibly useful for debugging
     * @param decoder how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     */
    public static Source fromBytes(byte[] bytes, int byteIndex, int length, String description, BytesDecoder decoder) {
        final BytesSource source = new BytesSource(description, bytes, byteIndex, length, decoder);
        notifyNewSource(source).tagAs(Tags.FROM_BYTES);
        return source;
    }

    // TODO (mlvdv) enable per-file choice whether to cache?
    /**
     * Enables/disables caching of file contents, <em>disabled</em> by default. Caching of sources
     * created from literal text or readers is always enabled.
     */
    public static void setFileCaching(boolean enabled) {
        fileCacheEnabled = enabled;
    }

    /**
     * Returns all {@link Source}s holding a particular {@link SyntaxTag}, or the whole collection
     * of Sources if the specified tag is {@code null}.
     *
     * @return A collection of Sources containing the given tag.
     */
    public static Collection<Source> findSourcesTaggedAs(SourceTag tag) {
        final List<Source> taggedSources = new ArrayList<>();
        synchronized (allSources) {
            for (WeakReference<Source> ref : allSources) {
                Source source = ref.get();
                if (source != null) {
                    if (tag == null || source.isTaggedAs(tag)) {
                        taggedSources.add(ref.get());
                    }
                }
            }
        }
        return taggedSources;
    }

    /**
     * Adds a {@link SourceListener} to receive events.
     */
    public static void addSourceListener(SourceListener listener) {
        assert listener != null;
        sourceListeners.add(listener);
    }

    /**
     * Removes a {@link SourceListener}. Ignored if listener not found.
     */
    public static void removeSourceListener(SourceListener listener) {
        sourceListeners.remove(listener);
    }

    private static Source notifyNewSource(Source source) {
        allSources.add(new WeakReference<>(source));
        for (SourceListener listener : sourceListeners) {
            listener.sourceCreated(source);
        }
        return source;
    }

    private static String read(Reader reader) throws IOException {
        final BufferedReader bufferedReader = new BufferedReader(reader);
        final StringBuilder builder = new StringBuilder();
        final char[] buffer = new char[1024];

        while (true) {
            final int n = bufferedReader.read(buffer);
            if (n == -1) {
                break;
            }
            builder.append(buffer, 0, n);
        }

        return builder.toString();
    }

    private final ArrayList<SourceTag> tags = new ArrayList<>();

    private Source() {
    }

    private TextMap textMap = null;

    abstract void reset();

    public final boolean isTaggedAs(SourceTag tag) {
        assert tag != null;
        return tags.contains(tag);
    }

    public final Collection<SourceTag> getSourceTags() {
        return Collections.unmodifiableCollection(tags);
    }

    /**
     * Adds a {@linkplain SourceTag tag} to the set of tags associated with this {@link Source};
     * {@code no-op} if already in the set.
     *
     * @return this
     */
    public final Source tagAs(SourceTag tag) {
        assert tag != null;
        if (!tags.contains(tag)) {
            tags.add(tag);
            for (SourceListener listener : sourceListeners) {
                listener.sourceTaggedAs(this, tag);
            }
        }
        return this;
    }

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file.
     *
     * @return the name of the guest language program
     */
    public abstract String getName();

    /**
     * Returns a short version of the name of the resource holding a guest language program (as
     * described in @getName). For example, this could be just the name of the file, rather than a
     * full path.
     *
     * @return the short name of the guest language program
     */
    public abstract String getShortName();

    /**
     * The normalized, canonical name if the source is a file.
     */
    public abstract String getPath();

    /**
     * The URL if the source is retrieved via URL.
     */
    public abstract URL getURL();

    /**
     * Access to the source contents.
     */
    public abstract Reader getReader();

    /**
     * Access to the source contents.
     */
    public final InputStream getInputStream() {
        return new ByteArrayInputStream(getCode().getBytes());
    }

    /**
     * Gets the number of characters in the source.
     */
    public final int getLength() {
        return getTextMap().length();
    }

    /**
     * Returns the complete text of the code.
     */
    public abstract String getCode();

    /**
     * Returns a subsection of the code test.
     */
    public String getCode(int charIndex, int charLength) {
        return getCode().substring(charIndex, charIndex + charLength);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     */
    public final String getCode(int lineNumber) {
        final int offset = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return getCode().substring(offset, offset + length);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line.
     */
    public final int getLineCount() {
        return getTextMap().lineCount();
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     */
    public final int getLineNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToLine(offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     */
    public final int getColumnNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToCol(offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     */
    public final int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineStartOffset(lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     */
    public final int getLineLength(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineLength(lineNumber);
    }

    /**
     * Append text to a Source explicitly created as <em>Appendable</em>.
     *
     * @param chars the text to append
     * @throws UnsupportedOperationException by concrete subclasses that do not support appending
     */
    public void appendCode(CharSequence chars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a representation of a contiguous region of text in the source.
     * <p>
     * This method performs no checks on the validity of the arguments.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param charIndex the 0-based index of the first character of the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length) {
        return new DefaultSourceSection(this, identifier, startLine, startColumn, charIndex, length);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code charIndex} value by building a {@linkplain TextMap map} of lines in the source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if arguments are outside the text of the source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int length) {
        final int lineStartOffset = getTextMap().lineStartOffset(startLine);
        if (startColumn > getTextMap().lineLength(startLine)) {
            throw new IllegalArgumentException("column out of range");
        }
        final int startOffset = lineStartOffset + startColumn - 1;
        return new DefaultSourceSection(this, identifier, startLine, startColumn, startOffset, length);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code (startLine, startColumn)} values by building a {@linkplain TextMap map} of lines in
     * the source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     *
     * @param identifier terse description of the region
     * @param charIndex 0-based position of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if either of the arguments are outside the text of the
     *             source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int charIndex, int length) throws IllegalArgumentException {
        checkRange(charIndex, length);
        final int startLine = getLineNumber(charIndex);
        final int startColumn = charIndex - getLineStartOffset(startLine) + 1;
        return new DefaultSourceSection(this, identifier, startLine, startColumn, charIndex, length);
    }

    void checkRange(int charIndex, int length) {
        if (!(charIndex >= 0 && length >= 0 && charIndex + length <= getCode().length())) {
            throw new IllegalArgumentException("text positions out of range");
        }
    }

    /**
     * Creates a representation of a line of text in the source identified only by line number, from
     * which the character information will be computed.
     *
     * @param identifier terse description of the line
     * @param lineNumber 1-based line number of the first character in the section
     * @return newly created object representing the specified line
     * @throws IllegalArgumentException if the line does not exist the source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int lineNumber) {
        final int charIndex = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return createSection(identifier, charIndex, length);
    }

    /**
     * Creates a representation of a line number in this source, suitable for use as a hash table
     * key with equality defined to mean equivalent location.
     *
     * @param lineNumber a 1-based line number in this source
     * @return a representation of a line in this source
     */
    public final LineLocation createLineLocation(int lineNumber) {
        return new LineLocationImpl(this, lineNumber);
    }

    /**
     * An object suitable for using as a key into a hashtable that defines equivalence between
     * different source types.
     */
    Object getHashKey() {
        return getName();
    }

    final TextMap getTextMap() {
        if (textMap == null) {
            textMap = createTextMap();
        }
        return textMap;
    }

    final void clearTextMap() {
        textMap = null;
    }

    TextMap createTextMap() {
        final String code = getCode();
        if (code == null) {
            throw new RuntimeException("can't read file " + getName());
        }
        return TextMap.fromString(code);
    }

    private static final class LiteralSource extends Source {

        private final String description;
        private final String code;

        public LiteralSource(String description, String code) {
            this.description = description;
            this.code = code;
        }

        @Override
        public String getName() {
            return description;
        }

        @Override
        public String getShortName() {
            return description;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getPath() {
            return description;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return new StringReader(code);
        }

        @Override
        void reset() {
        }

        @Override
        public int hashCode() {
            return description.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof LiteralSource) {
                LiteralSource other = (LiteralSource) obj;
                return description.equals(other.description);
            }
            return false;
        }
    }

    private static final class AppendableLiteralSource extends Source {
        private String description;
        final List<CharSequence> codeList = new ArrayList<>();

        public AppendableLiteralSource(String description) {
            this.description = description;
        }

        @Override
        public String getName() {
            return description;
        }

        @Override
        public String getShortName() {
            return description;
        }

        @Override
        public String getCode() {
            return getCodeFromIndex(0);
        }

        @Override
        public String getPath() {
            return description;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return new StringReader(getCode());
        }

        @Override
        void reset() {
        }

        private String getCodeFromIndex(int index) {
            StringBuilder sb = new StringBuilder();
            for (int i = index; i < codeList.size(); i++) {
                CharSequence s = codeList.get(i);
                sb.append(s);
            }
            return sb.toString();
        }

        @Override
        public void appendCode(CharSequence chars) {
            codeList.add(chars);
            clearTextMap();
        }

    }

    private static final class FileSource extends Source {

        private final File file;
        private final String name; // Name used originally to describe the source
        private final String path;  // Normalized path description of an actual file

        private String code = null;  // A cache of the file's contents
        private long timeStamp;      // timestamp of the cache in the file system

        public FileSource(File file, String name, String path) {
            this(file, name, path, null);
        }

        public FileSource(File file, String name, String path, CharSequence chars) {
            this.file = file.getAbsoluteFile();
            this.name = name;
            this.path = path;
            if (chars != null) {
                this.code = chars.toString();
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getShortName() {
            return file.getName();
        }

        @Override
        Object getHashKey() {
            return path;
        }

        @Override
        public String getCode() {
            if (fileCacheEnabled) {
                if (code == null || timeStamp != file.lastModified()) {
                    try {
                        code = read(getReader());
                        timeStamp = file.lastModified();
                    } catch (IOException e) {
                    }
                }
                return code;
            }
            try {
                return read(new FileReader(file));
            } catch (IOException e) {
            }
            return null;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            if (code != null && timeStamp == file.lastModified()) {
                return new StringReader(code);
            }
            try {
                return new FileReader(file);
            } catch (FileNotFoundException e) {

                throw new RuntimeException("Can't find file " + path, e);
            }
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof FileSource) {
                FileSource other = (FileSource) obj;
                return path.equals(other.path);
            }
            return false;
        }

        @Override
        void reset() {
            this.code = null;
        }
    }

    private static final class URLSource extends Source {

        private static final Map<URL, WeakReference<URLSource>> urlToSource = new HashMap<>();

        public static URLSource get(URL url, String name) throws IOException {
            WeakReference<URLSource> sourceRef = urlToSource.get(url);
            URLSource source = sourceRef == null ? null : sourceRef.get();
            if (source == null) {
                source = new URLSource(url, name);
                urlToSource.put(url, new WeakReference<>(source));
            }
            return source;
        }

        private final URL url;
        private final String name;
        private String code = null;  // A cache of the source contents

        public URLSource(URL url, String name) throws IOException {
            this.url = url;
            this.name = name;
            code = read(new InputStreamReader(url.openStream()));
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public String getPath() {
            return url.getPath();
        }

        @Override
        public URL getURL() {
            return url;
        }

        @Override
        public Reader getReader() {
            return new StringReader(code);
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        void reset() {
        }
    }

    private static final class SubSource extends Source {
        private final Source base;
        private final int baseIndex;
        private final int subLength;

        private static SubSource create(Source base, int baseIndex, int length) {
            if (baseIndex < 0 || length < 0 || baseIndex + length > base.getLength()) {
                throw new IllegalArgumentException("text positions out of range");
            }
            return new SubSource(base, baseIndex, length);
        }

        private SubSource(Source base, int baseIndex, int length) {
            this.base = base;
            this.baseIndex = baseIndex;
            this.subLength = length;
        }

        @Override
        void reset() {
            assert false;
        }

        @Override
        public String getName() {
            return base.getName();
        }

        @Override
        public String getShortName() {
            return base.getShortName();
        }

        @Override
        public String getPath() {
            return base.getPath();
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            assert false;
            return null;
        }

        @Override
        public String getCode() {
            return base.getCode(baseIndex, subLength);
        }
    }

    private static final class BytesSource extends Source {

        private final String name;
        private final byte[] bytes;
        private final int byteIndex;
        private final int length;
        private final BytesDecoder decoder;

        public BytesSource(String name, byte[] bytes, int byteIndex, int length, BytesDecoder decoder) {
            this.name = name;
            this.bytes = bytes;
            this.byteIndex = byteIndex;
            this.length = length;
            this.decoder = decoder;
        }

        @Override
        void reset() {
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public String getPath() {
            return name;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return null;
        }

        @Override
        public String getCode() {
            return decoder.decode(bytes, byteIndex, length);
        }

        @Override
        public String getCode(int byteOffset, int codeLength) {
            return decoder.decode(bytes, byteIndex + byteOffset, codeLength);
        }

        @Override
        void checkRange(int charIndex, int rangeLength) {
            if (!(charIndex >= 0 && rangeLength >= 0 && charIndex + rangeLength <= length)) {
                throw new IllegalArgumentException("text positions out of range");
            }
        }

        @Override
        TextMap createTextMap() {
            return TextMap.fromBytes(bytes, byteIndex, length, decoder);
        }
    }

    private static final class DefaultSourceSection implements SourceSection {

        private final Source source;
        private final String identifier;
        private final int startLine;
        private final int startColumn;
        private final int charIndex;
        private final int charLength;

        /**
         * Creates a new object representing a contiguous text section within the source code of a
         * guest language program's text.
         * <p>
         * The starting location of the section is specified using two different coordinate:
         * <ul>
         * <li><b>(row, column)</b>: rows and columns are 1-based, so the first character in a
         * source file is at position {@code (1,1)}.</li>
         * <li><b>character index</b>: 0-based offset of the character from the beginning of the
         * source, so the first character in a file is at index {@code 0}.</li>
         * </ul>
         * The <b>newline</b> that terminates each line counts as a single character for the purpose
         * of a character index. The (row,column) coordinates of a newline character should never
         * appear in a text section.
         * <p>
         *
         * @param source object representing the complete source program that contains this section
         * @param identifier an identifier used when printing the section
         * @param startLine the 1-based number of the start line of the section
         * @param startColumn the 1-based number of the start column of the section
         * @param charIndex the 0-based index of the first character of the section
         * @param charLength the length of the section in number of characters
         */
        public DefaultSourceSection(Source source, String identifier, int startLine, int startColumn, int charIndex, int charLength) {
            this.source = source;
            this.identifier = identifier;
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.charIndex = charIndex;
            this.charLength = charLength;
        }

        @Override
        public Source getSource() {
            return source;
        }

        @Override
        public int getStartLine() {
            return startLine;
        }

        @Override
        public LineLocation getLineLocation() {
            return source.createLineLocation(startLine);
        }

        @Override
        public int getStartColumn() {
            return startColumn;
        }

        public int getEndLine() {
            return source.getLineNumber(charIndex + charLength - 1);
        }

        public int getEndColumn() {
            return source.getColumnNumber(charIndex + charLength - 1);
        }

        @Override
        public int getCharIndex() {
            return charIndex;
        }

        @Override
        public int getCharLength() {
            return charLength;
        }

        @Override
        public int getCharEndIndex() {
            return charIndex + charLength;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String getCode() {
            return getSource().getCode(charIndex, charLength);
        }

        @Override
        public String getShortDescription() {
            return String.format("%s:%d", source.getShortName(), startLine);
        }

        @Override
        public String toString() {
            return getCode();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + charIndex;
            result = prime * result + charLength;
            result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
            result = prime * result + ((source == null) ? 0 : source.hashCode());
            result = prime * result + startColumn;
            result = prime * result + startLine;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof DefaultSourceSection)) {
                return false;
            }
            DefaultSourceSection other = (DefaultSourceSection) obj;
            if (charIndex != other.charIndex) {
                return false;
            }
            if (charLength != other.charLength) {
                return false;
            }
            if (identifier == null) {
                if (other.identifier != null) {
                    return false;
                }
            } else if (!identifier.equals(other.identifier)) {
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
    }

    private static final class LineLocationImpl implements LineLocation {
        private final Source source;
        private final int line;

        public LineLocationImpl(Source source, int line) {
            assert source != null;
            this.source = source;
            this.line = line;
        }

        @Override
        public Source getSource() {
            return source;
        }

        @Override
        public int getLineNumber() {
            return line;
        }

        @Override
        public String getShortDescription() {
            return source.getShortName() + ":" + line;
        }

        @Override
        public String toString() {
            return "Line[" + getShortDescription() + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + line;
            result = prime * result + source.getHashKey().hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof LineLocationImpl)) {
                return false;
            }
            LineLocationImpl other = (LineLocationImpl) obj;
            if (line != other.line) {
                return false;
            }
            return source.getHashKey().equals(other.source.getHashKey());
        }

    }

    /**
     * A utility for converting between coordinate systems in a string of text interspersed with
     * newline characters. The coordinate systems are:
     * <ul>
     * <li>0-based character offset from the beginning of the text, where newline characters count
     * as a single character and the first character in the text occupies position 0.</li>
     * <li>1-based position in the 2D space of lines and columns, in which the first position in the
     * text is at (1,1).</li>
     * </ul>
     * <p>
     * This utility is based on positions occupied by characters, not text stream positions as in a
     * text editor. The distinction shows up in editors where you can put the cursor just past the
     * last character in a buffer; this is necessary, among other reasons, so that you can put the
     * edit cursor in a new (empty) buffer. For the purposes of this utility, however, there are no
     * character positions in an empty text string and there are no lines in an empty text string.
     * <p>
     * A newline character designates the end of a line and occupies a column position.
     * <p>
     * If the text ends with a character other than a newline, then the characters following the
     * final newline character count as a line, even though not newline-terminated.
     * <p>
     * <strong>Limitations:</strong>
     * <ul>
     * <li>Does not handle multiple character encodings correctly.</li>
     * <li>Treats tabs as occupying 1 column.</li>
     * <li>Does not handle multiple-character line termination sequences correctly.</li>
     * </ul>
     */
    private static final class TextMap {

        // 0-based offsets of newline characters in the text, with sentinel
        private final int[] nlOffsets;

        // The number of characters in the text, including newlines (which count as 1).
        private final int textLength;

        // Is the final text character a newline?
        final boolean finalNL;

        public TextMap(int[] nlOffsets, int textLength, boolean finalNL) {
            this.nlOffsets = nlOffsets;
            this.textLength = textLength;
            this.finalNL = finalNL;
        }

        /**
         * Constructs map permitting translation between 0-based character offsets and 1-based
         * lines/columns.
         */
        public static TextMap fromString(String text) {
            final int textLength = text.length();
            final ArrayList<Integer> lines = new ArrayList<>();
            lines.add(0);
            int offset = 0;

            while (offset < text.length()) {
                final int nlIndex = text.indexOf('\n', offset);
                if (nlIndex >= 0) {
                    offset = nlIndex + 1;
                    lines.add(offset);
                } else {
                    break;
                }
            }
            lines.add(Integer.MAX_VALUE);

            final int[] nlOffsets = new int[lines.size()];
            for (int line = 0; line < lines.size(); line++) {
                nlOffsets[line] = lines.get(line);
            }

            final boolean finalNL = textLength > 0 && (textLength == nlOffsets[nlOffsets.length - 2]);

            return new TextMap(nlOffsets, textLength, finalNL);
        }

        public static TextMap fromBytes(byte[] bytes, int byteIndex, int length, BytesDecoder bytesDecoder) {
            final ArrayList<Integer> lines = new ArrayList<>();
            lines.add(0);

            bytesDecoder.decodeLines(bytes, byteIndex, length, new BytesDecoder.LineMarker() {

                public void markLine(int index) {
                    lines.add(index);
                }
            });

            lines.add(Integer.MAX_VALUE);

            final int[] nlOffsets = new int[lines.size()];
            for (int line = 0; line < lines.size(); line++) {
                nlOffsets[line] = lines.get(line);
            }

            final boolean finalNL = length > 0 && (length == nlOffsets[nlOffsets.length - 2]);

            return new TextMap(nlOffsets, length, finalNL);
        }

        /**
         * Converts 0-based character offset to 1-based number of the line containing the character.
         *
         * @throws IllegalArgumentException if the offset is outside the string.
         */
        public int offsetToLine(int offset) throws IllegalArgumentException {
            if (offset < 0 || offset >= textLength) {
                throw new IllegalArgumentException("offset out of bounds");
            }
            int line = 1;
            while (offset >= nlOffsets[line]) {
                line++;
            }
            return line;
        }

        /**
         * Converts 0-based character offset to 1-based number of the column occupied by the
         * character.
         * <p>
         * Tabs are not expanded; they occupy 1 column.
         *
         * @throws IllegalArgumentException if the offset is outside the string.
         */
        public int offsetToCol(int offset) throws IllegalArgumentException {
            return 1 + offset - nlOffsets[offsetToLine(offset) - 1];
        }

        /**
         * The number of characters in the mapped text.
         */
        public int length() {
            return textLength;
        }

        /**
         * The number of lines in the text; if characters appear after the final newline, then they
         * also count as a line, even though not newline-terminated.
         */
        public int lineCount() {
            if (textLength == 0) {
                return 0;
            }
            return finalNL ? nlOffsets.length - 2 : nlOffsets.length - 1;
        }

        /**
         * Converts 1-based line number to the 0-based offset of the line's first character; this
         * would be the offset of a newline if the line is empty.
         *
         * @throws IllegalArgumentException if there is no such line in the text.
         */
        public int lineStartOffset(int line) throws IllegalArgumentException {
            if (textLength == 0 || lineOutOfRange(line)) {
                throw new IllegalArgumentException("line out of bounds");
            }
            return nlOffsets[line - 1];
        }

        /**
         * Gets the number of characters in a line, identified by 1-based line number;
         * <em>does not</em> include the final newline, if any.
         *
         * @throws IllegalArgumentException if there is no such line in the text.
         */
        public int lineLength(int line) throws IllegalArgumentException {
            if (textLength == 0 || lineOutOfRange(line)) {
                throw new IllegalArgumentException("line out of bounds");
            }
            if (line == nlOffsets.length - 1 && !finalNL) {
                return textLength - nlOffsets[line - 1];
            }
            return (nlOffsets[line] - nlOffsets[line - 1]) - 1;

        }

        /**
         * Is the line number out of range.
         */
        private boolean lineOutOfRange(int line) {
            return line <= 0 || line >= nlOffsets.length || (line == nlOffsets.length - 1 && finalNL);
        }

    }

}
