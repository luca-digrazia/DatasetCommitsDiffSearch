/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.util;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Immutable Bit Set implementation, with a lot of code shamelessly ripped from
 * {@link java.util.BitSet}.
 */
public class CompilationFinalBitSet implements Iterable<Integer> {

    private static final CompilationFinalBitSet[] STATIC_INSTANCES = new CompilationFinalBitSet[16];

    static {
        for (int i = 0; i < STATIC_INSTANCES.length; i++) {
            STATIC_INSTANCES[i] = new CompilationFinalBitSet(new long[]{i});
        }
    }

    private static int wordIndex(int i) {
        return i >> 6;
    }

    public static CompilationFinalBitSet valueOf(int... values) {
        assert values.length > 0;
        CompilationFinalBitSet bs = new CompilationFinalBitSet(values[values.length - 1]);
        for (int v : values) {
            bs.set(v);
        }
        return bs;
    }

    public static long[] createBitSetArray(int nbits) {
        return new long[wordIndex(nbits - 1) + 1];
    }

    @CompilationFinal(dimensions = 1) private long[] words;

    public CompilationFinalBitSet(int nbits) {
        this.words = createBitSetArray(nbits);
    }

    public CompilationFinalBitSet(long[] words) {
        this.words = words;
    }

    private CompilationFinalBitSet(CompilationFinalBitSet copy) {
        this.words = Arrays.copyOf(copy.words, copy.words.length);
    }

    public static CompilationFinalBitSet getEmptyInstance() {
        return STATIC_INSTANCES[0];
    }

    public static CompilationFinalBitSet getStaticInstance(int i) {
        return STATIC_INSTANCES[i];
    }

    public static int getNumberOfStaticInstances() {
        return STATIC_INSTANCES.length;
    }

    public int getStaticCacheKey() {
        for (int i = 1; i < words.length; i++) {
            if (words[i] != 0) {
                return -1;
            }
        }
        if (words.length == 0) {
            return 0;
        }
        return 0 <= words[0] && words[0] < STATIC_INSTANCES.length ? (int) words[0] : -1;
    }

    public CompilationFinalBitSet copy() {
        return new CompilationFinalBitSet(this);
    }

    public long[] toLongArray() {
        return Arrays.copyOf(words, words.length);
    }

    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    public int numberOfSetBits() {
        int ret = 0;
        for (long w : words) {
            ret += Long.bitCount(w);
        }
        return ret;
    }

    public boolean get(int b) {
        return wordIndex(b) < words.length && (words[wordIndex(b)] & (1L << b)) != 0;
    }

    private void ensureCapacity(int nWords) {
        if (words.length < nWords) {
            words = Arrays.copyOf(words, Math.max(2 * words.length, nWords));
        }
    }

    public void set(int b) {
        final int wordIndex = wordIndex(b);
        ensureCapacity(wordIndex + 1);
        words[wordIndex] |= 1L << b;
    }

    public void setRange(int lo, int hi) {
        int wordIndexLo = wordIndex(lo);
        int wordIndexHi = wordIndex(hi);
        ensureCapacity(wordIndexHi + 1);
        long rangeLo = (~0L) << lo;
        long rangeHi = (~0L) >>> (63 - (hi & 0x3f));
        if (wordIndexLo == wordIndexHi) {
            words[wordIndexLo] |= rangeLo & rangeHi;
            return;
        }
        words[wordIndexLo] |= rangeLo;
        for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
            words[i] = ~0L;
        }
        words[wordIndexHi] |= rangeHi;
    }

    public void clear() {
        Arrays.fill(words, 0L);
    }

    public void clear(int b) {
        final int wordIndex = wordIndex(b);
        ensureCapacity(wordIndex + 1);
        words[wordIndex] &= ~(1L << b);
    }

    public void invert() {
        for (int i = 0; i < words.length; i++) {
            words[i] = ~words[i];
        }
    }

    public void intersect(CompilationFinalBitSet other) {
        int i = 0;
        for (; i < Math.min(words.length, other.words.length); i++) {
            words[i] &= other.words[i];
        }
        for (; i < words.length; i++) {
            words[i] = 0;
        }
    }

    public void subtract(CompilationFinalBitSet other) {
        for (int i = 0; i < Math.min(words.length, other.words.length); i++) {
            words[i] &= ~other.words[i];
        }
    }

    public void union(CompilationFinalBitSet other) {
        ensureCapacity(other.words.length);
        for (int i = 0; i < Math.min(words.length, other.words.length); i++) {
            words[i] |= other.words[i];
        }
    }

    public boolean isDisjoint(CompilationFinalBitSet other) {
        for (int i = 0; i < Math.min(words.length, other.words.length); i++) {
            if ((words[i] & other.words[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(CompilationFinalBitSet other) {
        for (int i = 0; i < other.words.length; i++) {
            if (i >= words.length) {
                if (other.words[i] != 0) {
                    return false;
                }
            } else if ((words[i] & other.words[i]) != other.words[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof CompilationFinalBitSet)) {
            return false;
        }
        CompilationFinalBitSet o = (CompilationFinalBitSet) obj;
        if (words.length == o.words.length) {
            return Arrays.equals(words, o.words);
        }
        for (int i = 0; i < Math.min(words.length, o.words.length); i++) {
            if (words[i] != o.words[i]) {
                return false;
            }
        }
        for (int i = words.length; i < o.words.length; i++) {
            if (words[i] != 0) {
                return false;
            }
        }
        for (int i = o.words.length; i < words.length; i++) {
            if (o.words[i] != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        long h = 1234;
        for (int i = words.length; --i >= 0;) {
            h ^= words[i] * (i + 1);
        }
        return (int) ((h >> 32) ^ h);
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new CompilationFinalBitSetIterator();
    }

    private final class CompilationFinalBitSetIterator implements PrimitiveIterator.OfInt {

        private int wordIndex = 0;
        private byte bitIndex = 0;
        private long curWord;
        private int last;

        private CompilationFinalBitSetIterator() {
            if (hasNext()) {
                curWord = words[0];
            }
            findNext();
        }

        private void findNext() {
            while (curWord == 0) {
                wordIndex++;
                bitIndex = 0;
                if (hasNext()) {
                    curWord = words[wordIndex];
                } else {
                    return;
                }
            }
            assert hasNext();
            assert curWord != 0;
            int trailingZeros = Long.numberOfTrailingZeros(curWord);
            curWord >>>= trailingZeros;
            bitIndex += trailingZeros;
        }

        @Override
        public boolean hasNext() {
            return wordIndex < words.length;
        }

        @Override
        public int nextInt() {
            assert hasNext();
            last = (wordIndex * 64) + bitIndex;
            curWord >>>= 1;
            bitIndex++;
            findNext();
            return last;
        }

        @Override
        public void remove() {
            clear(last);
        }
    }

    @TruffleBoundary
    @Override
    public Spliterator.OfInt spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.SORTED | Spliterator.NONNULL);
    }

    @TruffleBoundary
    public IntStream stream() {
        return StreamSupport.intStream(spliterator(), false);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[ ");
        int last = -2;
        int rangeBegin = -2;
        for (int b : this) {
            if (b != last + 1) {
                appendRange(sb, rangeBegin, last);
                rangeBegin = b;
            }
            last = b;
        }
        appendRange(sb, rangeBegin, last);
        sb.append(']');
        return sb.toString();
    }

    @TruffleBoundary
    private static void appendRange(StringBuilder sb, int rangeBegin, int last) {
        if (rangeBegin >= 0 && rangeBegin < last) {
            sb.append(String.format("%02x", rangeBegin));
            if (rangeBegin + 1 < last) {
                sb.append("-");
            } else {
                sb.append(" ");
            }
        }
        if (last >= 0) {
            sb.append(String.format("%02x ", last));
        }
    }
}
