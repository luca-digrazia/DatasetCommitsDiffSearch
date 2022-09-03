/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.util.impl;

import java.util.Iterator;
import java.util.function.BiFunction;

import org.graalvm.util.CompareStrategy;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.EconomicSet;
import org.graalvm.util.ImmutableEconomicMap;
import org.graalvm.util.ImmutableEconomicSet;
import org.graalvm.util.ImmutableMapCursor;
import org.graalvm.util.MapCursor;

public final class EconomicMapImpl<K, V> implements EconomicMap<K, V>, EconomicSet<K> {

    /**
     * Initial number of key/value pair entries that is allocated in the first entries array.
     */
    private static final int INITIAL_CAPACITY = 4;

    /**
     * Maximum number of entries that are moved linearly forward if a key is removed.
     */
    private static final int COMPRESS_IMMEDIATE_CAPACITY = 8;

    /**
     * Minimum number of key/value pair entries added when the entries array is increased in size.
     */
    private static final int MIN_CAPACITY_INCREASE = 8;

    /**
     * Number of entries above which a hash table is created.
     */
    private static final int HASH_THRESHOLD = 4;

    /**
     * Number of entries above which a hash table is created when equality can be checked with
     * object identity.
     */
    private static final int HASH_THRESHOLD_IDENTITY_COMPARE = 8;

    /**
     * Maximum number of entries allowed in the map.
     */
    private static final int MAX_ELEMENT_COUNT = Integer.MAX_VALUE >> 1;

    /**
     * Number of entries above which more than 1 byte is necessary for the hash index.
     */
    private static final int LARGE_HASH_THRESHOLD = ((1 << Byte.SIZE) << 1);

    /**
     * Number of entries above which more than 2 bytes are are necessary for the hash index.
     */
    private static final int VERY_LARGE_HASH_THRESHOLD = (LARGE_HASH_THRESHOLD << Byte.SIZE);

    /**
     * Total number of entries (actual entries plus deleted entries).
     */
    private int totalEntries;

    /**
     * Number of deleted entries.
     */
    private int deletedEntries;

    /**
     * Entries array with even indices storing keys and odd indices storing values.
     */
    private Object[] entries;

    /**
     * Hash array that is interpreted either as byte or short or int array depending on number of
     * map entries.
     */
    private byte[] hashArray;

    /**
     * The strategy used for comparing keys or {@code null} for denoting special strategy
     * {@link CompareStrategy#IDENTITY}.
     */
    private final CompareStrategy strategy;

    /**
     * Intercept method for debugging purposes.
     */
    private static <K, V> EconomicMapImpl<K, V> intercept(EconomicMapImpl<K, V> map) {
        return map;
    }

    public static <K, V> EconomicMapImpl<K, V> create(CompareStrategy strategy) {
        return intercept(new EconomicMapImpl<>(strategy));
    }

    public static <K, V> EconomicMapImpl<K, V> create(CompareStrategy strategy, int initialCapacity) {
        return intercept(new EconomicMapImpl<>(strategy, initialCapacity));
    }

    public static <K, V> EconomicMapImpl<K, V> create(CompareStrategy strategy, ImmutableEconomicMap<K, V> other) {
        return intercept(new EconomicMapImpl<>(strategy, other));
    }

    public static <K, V> EconomicMapImpl<K, V> create(CompareStrategy strategy, ImmutableEconomicSet<K> other) {
        return intercept(new EconomicMapImpl<>(strategy, other));
    }

    private EconomicMapImpl(CompareStrategy strategy) {
        if (strategy == CompareStrategy.IDENTITY) {
            this.strategy = null;
        } else {
            this.strategy = strategy;
        }
    }

    private EconomicMapImpl(CompareStrategy strategy, int initialCapacity) {
        this(strategy);
        init(initialCapacity);
    }

    private EconomicMapImpl(CompareStrategy strategy, ImmutableEconomicMap<K, V> other) {
        this(strategy);
        if (other instanceof EconomicMapImpl) {
            initFrom((EconomicMapImpl<K, V>) other);
        } else {
            init(other.size());
            addAll(other);
        }
    }

    @SuppressWarnings("unchecked")
    private EconomicMapImpl(CompareStrategy strategy, ImmutableEconomicSet<K> other) {
        this(strategy);
        if (other instanceof EconomicMapImpl) {
            initFrom((EconomicMapImpl<K, V>) other);
        } else {
            init(other.size());
            addAll(other);
        }
    }

    private void addAll(ImmutableEconomicMap<K, V> other) {
        ImmutableMapCursor<K, V> entry = other.getEntries();
        while (entry.advance()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    private void initFrom(EconomicMapImpl<K, V> otherMap) {
        totalEntries = otherMap.totalEntries;
        deletedEntries = otherMap.deletedEntries;
        if (otherMap.entries != null) {
            entries = otherMap.entries.clone();
        }
        if (otherMap.hashArray != null) {
            hashArray = otherMap.hashArray.clone();
        }
    }

    private void init(int size) {
        if (size > INITIAL_CAPACITY) {
            entries = new Object[size << 1];
        }
    }

    /**
     * Links the collisions. Needs to be immutable class for allowing efficient shallow copy from
     * other map on construction.
     */
    private static final class CollisionLink {

        CollisionLink(Object value, int next) {
            this.value = value;
            this.next = next;
        }

        final Object value;

        /**
         * Index plus one of the next entry in the collision link chain.
         */
        final int next;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(K key) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index = find(key);
        if (index != -1) {
            return (V) getValue(index);
        }
        return null;
    }

    private int find(K key) {
        if (hasHashArray()) {
            return findHash(key);
        } else {
            return findLinear(key);
        }
    }

    private int findLinear(K key) {
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = entries[i << 1];
            if (entryKey != null && compareKeys(key, entryKey)) {
                return i;
            }
        }
        return -1;
    }

    private boolean compareKeys(Object key, Object entryKey) {
        if (key == entryKey) {
            return true;
        }
        if (strategy != null && strategy != CompareStrategy.IDENTITY_WITH_SYSTEM_HASHCODE) {
            if (strategy == CompareStrategy.EQUALS) {
                return key.equals(entryKey);
            } else {
                return strategy.equals(key, entryKey);
            }
        }
        return false;
    }

    private int findHash(K key) {
        int index = getHashArray(getHashIndex(key)) - 1;
        if (index != -1) {
            Object entryKey = getKey(index);
            if (compareKeys(key, entryKey)) {
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findWithCollision(key, (CollisionLink) entryValue);
                }
            }
        }

        return -1;
    }

    private int findWithCollision(K key, CollisionLink initialEntryValue) {
        int index;
        Object entryKey;
        CollisionLink entryValue = initialEntryValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(key, entryKey)) {
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashArray(int index) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return (hashArray[index] & 0xFF);
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8);
        } else {
            int adjustedIndex = index << 2;
            return (hashArray[adjustedIndex] & 0xFF) | ((hashArray[adjustedIndex + 1] & 0xFF) << 8) | ((hashArray[adjustedIndex + 2] & 0xFF) << 16) | ((hashArray[adjustedIndex + 3] & 0xFF) << 24);
        }
    }

    private void setHashArray(int index, int value) {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            hashArray[index] = (byte) value;
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            int adjustedIndex = index << 1;
            hashArray[adjustedIndex] = (byte) value;
            hashArray[adjustedIndex + 1] = (byte) (value >> 8);
        } else {
            int adjustedIndex = index << 2;
            hashArray[adjustedIndex] = (byte) value;
            hashArray[adjustedIndex + 1] = (byte) (value >> 8);
            hashArray[adjustedIndex + 2] = (byte) (value >> 16);
            hashArray[adjustedIndex + 3] = (byte) (value >> 24);
        }
    }

    private int findAndRemoveHash(Object key) {
        int hashIndex = getHashIndex(key);
        int index = getHashArray(hashIndex) - 1;
        if (index != -1) {
            Object entryKey = getKey(index);
            if (compareKeys(key, entryKey)) {
                Object value = getRawValue(index);
                int nextIndex = -1;
                if (value instanceof CollisionLink) {
                    CollisionLink collisionLink = (CollisionLink) value;
                    nextIndex = collisionLink.next;
                }
                setHashArray(hashIndex, nextIndex + 1);
                return index;
            } else {
                Object entryValue = getRawValue(index);
                if (entryValue instanceof CollisionLink) {
                    return findAndRemoveWithCollision(key, (CollisionLink) entryValue, index);
                }
            }
        }

        return -1;
    }

    private int findAndRemoveWithCollision(Object key, CollisionLink initialEntryValue, int initialIndexValue) {
        int index;
        Object entryKey;
        CollisionLink entryValue = initialEntryValue;
        int lastIndex = initialIndexValue;
        while (true) {
            CollisionLink collisionLink = entryValue;
            index = collisionLink.next;
            entryKey = getKey(index);
            if (compareKeys(key, entryKey)) {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    CollisionLink thisCollisionLink = (CollisionLink) value;
                    setRawValue(lastIndex, new CollisionLink(collisionLink.value, thisCollisionLink.next));
                } else {
                    setRawValue(lastIndex, collisionLink.value);
                }
                return index;
            } else {
                Object value = getRawValue(index);
                if (value instanceof CollisionLink) {
                    entryValue = (CollisionLink) getRawValue(index);
                    lastIndex = index;
                } else {
                    return -1;
                }
            }
        }
    }

    private int getHashIndex(Object key) {
        int hash;
        if (strategy != null && strategy != CompareStrategy.EQUALS) {
            if (strategy == CompareStrategy.IDENTITY_WITH_SYSTEM_HASHCODE) {
                hash = System.identityHashCode(key);
            } else {
                hash = strategy.hashCode(key);
            }
        } else {
            hash = key.hashCode();
        }
        hash = hash ^ (hash >>> 16);
        return hash & (getHashTableSize() - 1);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V put(K key, V value) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index = find(key);
        if (index != -1) {
            Object oldValue = getValue(index);
            setValue(index, value);
            return (V) oldValue;
        }

        int nextEntryIndex = totalEntries;
        if (entries == null) {
            entries = new Object[INITIAL_CAPACITY << 1];
        } else if (entries.length == nextEntryIndex << 1) {
            grow();

            assert entries.length > totalEntries << 1;
            // Can change if grow is actually compressing.
            nextEntryIndex = totalEntries;
        }

        setKey(nextEntryIndex, key);
        setValue(nextEntryIndex, value);
        totalEntries++;

        if (hasHashArray()) {
            // Rehash on collision if hash table is more than three quarters full.
            boolean rehashOnCollision = (getHashTableSize() < (size() + (size() >> 1)));
            putHashEntry(key, nextEntryIndex, rehashOnCollision);
        } else if (totalEntries > getHashThreshold()) {
            createHash();
        }

        return null;
    }

    /**
     * Number of entries above which a hash table should be constructed.
     */
    private int getHashThreshold() {
        if (strategy == null || strategy == CompareStrategy.IDENTITY_WITH_SYSTEM_HASHCODE) {
            return HASH_THRESHOLD_IDENTITY_COMPARE;
        } else {
            return HASH_THRESHOLD;
        }
    }

    private void grow() {
        if (maybeCompress()) {
            return;
        }

        int entriesLength = entries.length;
        int newSize = (entriesLength >> 1) + Math.max(MIN_CAPACITY_INCREASE, entriesLength >> 2);
        if (newSize > MAX_ELEMENT_COUNT) {
            throw new UnsupportedOperationException("map grown too large!");
        }
        Object[] newEntries = new Object[newSize << 1];
        System.arraycopy(entries, 0, newEntries, 0, entriesLength);
        entries = newEntries;
        if ((entriesLength < LARGE_HASH_THRESHOLD && newEntries.length >= LARGE_HASH_THRESHOLD) ||
                        (entriesLength < VERY_LARGE_HASH_THRESHOLD && newEntries.length > VERY_LARGE_HASH_THRESHOLD)) {
            // Rehash in order to change number of bits reserved for hash indices.
            createHash();
        }
    }

    private boolean maybeCompress() {
        if (entries.length != INITIAL_CAPACITY << 1 && deletedEntries >= (totalEntries >> 1) + (totalEntries >> 2)) {
            compressLarge();
            return true;
        }
        return false;
    }

    private void compressLarge() {
        int size = INITIAL_CAPACITY;
        int remaining = totalEntries - deletedEntries;

        while (size <= remaining) {
            size += Math.max(MIN_CAPACITY_INCREASE, size >> 1);
        }

        Object[] newEntries = new Object[size << 1];
        int z = 0;
        for (int i = 0; i < totalEntries; ++i) {
            Object key = getKey(i);
            if (key != null) {
                newEntries[z << 1] = key;
                newEntries[(z << 1) + 1] = getValue(i);
                z++;
            }
        }

        this.entries = newEntries;
        totalEntries = z;
        deletedEntries = 0;
        if (z <= getHashThreshold()) {
            this.hashArray = null;
        } else {
            createHash();
        }
    }

    private int getHashTableSize() {
        if (entries.length < LARGE_HASH_THRESHOLD) {
            return hashArray.length;
        } else if (entries.length < VERY_LARGE_HASH_THRESHOLD) {
            return hashArray.length >> 1;
        } else {
            return hashArray.length >> 2;
        }
    }

    private void createHash() {
        int entryCount = size();

        // Calculate smallest 2^n that is greater number of entries.
        int size = getHashThreshold();
        while (size <= entryCount) {
            size <<= 1;
        }

        // Give extra size to avoid collisions.
        size <<= 1;

        if (this.entries.length >= VERY_LARGE_HASH_THRESHOLD) {
            // Every entry has 4 bytes.
            size <<= 2;
        } else if (this.entries.length >= LARGE_HASH_THRESHOLD) {
            // Every entry has 2 bytes.
            size <<= 1;
        } else {
            // Entries are very small => give extra size to further reduce collisions.
            size <<= 1;
        }

        hashArray = new byte[size];
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = getKey(i);
            if (entryKey != null) {
                putHashEntry(entryKey, i, false);
            }
        }
    }

    private void putHashEntry(Object key, int entryIndex, boolean rehashOnCollision) {
        int hashIndex = getHashIndex(key);
        int oldIndex = getHashArray(hashIndex) - 1;
        if (oldIndex != -1 && rehashOnCollision) {
            this.createHash();
            return;
        }
        setHashArray(hashIndex, entryIndex + 1);
        Object value = getRawValue(entryIndex);
        if (oldIndex != -1) {
            assert entryIndex != oldIndex : "this cannot happend and would create an endless collision link cycle";
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, new CollisionLink(collisionLink.value, oldIndex));
            } else {
                setRawValue(entryIndex, new CollisionLink(getRawValue(entryIndex), oldIndex));
            }
        } else {
            if (value instanceof CollisionLink) {
                CollisionLink collisionLink = (CollisionLink) value;
                setRawValue(entryIndex, collisionLink.value);
            }
        }
    }

    @Override
    public int size() {
        return totalEntries - deletedEntries;
    }

    @Override
    public boolean containsKey(K key) {
        return find(key) != -1;
    }

    @Override
    public void clear() {
        entries = null;
        hashArray = null;
        totalEntries = deletedEntries = 0;
    }

    private boolean hasHashArray() {
        return hashArray != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V removeKey(K key) {
        if (key == null) {
            throw new UnsupportedOperationException("null not supported as key!");
        }
        int index;
        if (hasHashArray()) {
            index = this.findAndRemoveHash(key);
        } else {
            index = this.findLinear(key);
        }

        if (index != -1) {
            Object value = getValue(index);
            remove(index);
            maybeCompress();
            return (V) value;
        }
        return null;
    }

    private void remove(int indexToRemove) {
        int index = indexToRemove;
        int entriesAfterIndex = totalEntries - index - 1;

        // Without hash array, compress immediately.
        if (entriesAfterIndex <= COMPRESS_IMMEDIATE_CAPACITY && !hasHashArray()) {
            while (index < totalEntries - 1) {
                setKey(index, getKey(index + 1));
                setRawValue(index, getRawValue(index + 1));
                index++;
            }
        }

        setKey(index, null);
        setRawValue(index, null);
        if (index == totalEntries - 1) {
            // Make sure last element is always non-null.
            totalEntries--;
            while (index > 0 && getKey(index - 1) == null) {
                totalEntries--;
                deletedEntries--;
                index--;
            }
        } else {
            deletedEntries++;
        }
    }

    private abstract class SparseMapIterator<E> implements Iterator<E> {

        protected int current;

        @Override
        public boolean hasNext() {
            return current < totalEntries;
        }

        @Override
        public void remove() {
            if (hasHashArray()) {
                EconomicMapImpl.this.findAndRemoveHash(getKey(current - 1));
            }
            int oldTotal = totalEntries;
            EconomicMapImpl.this.remove(current - 1);
            if (oldTotal != totalEntries) {
                // Compression happened.
                current--;
            }
        }
    }

    @Override
    public Iterable<V> getValues() {
        return new Iterable<V>() {
            @Override
            public Iterator<V> iterator() {
                return new SparseMapIterator<V>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public V next() {
                        Object result;
                        while (true) {
                            result = getValue(current);
                            if (result == null && getKey(current) == null) {
                                // values can be null, double-check if key is also null
                                current++;
                            } else {
                                current++;
                                break;
                            }
                        }
                        return (V) result;
                    }
                };
            }
        };
    }

    @Override
    public Iterable<K> getKeys() {
        return this;
    }

    @Override
    public boolean isEmpty() {
        return this.size() == 0;
    }

    @Override
    public MapCursor<K, V> getEntries() {
        return new MapCursor<K, V>() {
            int current = -1;

            @Override
            public boolean advance() {
                current++;
                if (current >= totalEntries) {
                    return false;
                } else {
                    while (EconomicMapImpl.this.getKey(current) == null) {
                        // Skip over null entries
                        current++;
                    }
                    return true;
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public K getKey() {
                return (K) EconomicMapImpl.this.getKey(current);
            }

            @SuppressWarnings("unchecked")
            @Override
            public V getValue() {
                return (V) EconomicMapImpl.this.getValue(current);
            }

            @Override
            public void remove() {
                if (hasHashArray()) {
                    EconomicMapImpl.this.findAndRemoveHash(EconomicMapImpl.this.getKey(current));
                }
                int oldTotal = totalEntries;
                EconomicMapImpl.this.remove(current);
                if (oldTotal != totalEntries) {
                    // Compression happened.
                    current--;
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        for (int i = 0; i < totalEntries; i++) {
            Object entryKey = getKey(i);
            if (entryKey != null) {
                Object newValue = function.apply((K) entryKey, (V) getValue(i));
                setValue(i, newValue);
            }
        }
    }

    private Object getKey(int index) {
        return entries[index << 1];
    }

    private void setKey(int index, Object newValue) {
        entries[index << 1] = newValue;
    }

    private void setValue(int index, Object newValue) {
        Object oldValue = getRawValue(index);
        if (oldValue instanceof CollisionLink) {
            CollisionLink collisionLink = (CollisionLink) oldValue;
            setRawValue(index, new CollisionLink(newValue, collisionLink.next));
        } else {
            setRawValue(index, newValue);
        }
    }

    private void setRawValue(int index, Object newValue) {
        entries[(index << 1) + 1] = newValue;
    }

    private Object getRawValue(int index) {
        return entries[(index << 1) + 1];
    }

    private Object getValue(int index) {
        Object object = getRawValue(index);
        if (object instanceof CollisionLink) {
            return ((CollisionLink) object).value;
        }
        return object;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("map(size=").append(size()).append(", {");
        MapCursor<K, V> cursor = getEntries();
        while (cursor.advance()) {
            builder.append("(").append(cursor.getKey()).append(",").append(cursor.getValue()).append("),");
        }
        builder.append("}");
        return builder.toString();
    }

    @Override
    public Iterator<K> iterator() {
        return new SparseMapIterator<K>() {
            @SuppressWarnings("unchecked")
            @Override
            public K next() {
                Object result;
                while ((result = getKey(current++)) == null) {
                    // skip null entries
                }
                return (K) result;
            }
        };
    }

    @Override
    public boolean contains(K element) {
        return containsKey(element);
    }

    @Override
    public void addAll(Iterable<K> values) {
        for (K k : values) {
            add(k);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean add(K element) {
        return put(element, (V) element) == null;
    }

    @Override
    public void remove(K element) {
        removeKey(element);
    }
}
