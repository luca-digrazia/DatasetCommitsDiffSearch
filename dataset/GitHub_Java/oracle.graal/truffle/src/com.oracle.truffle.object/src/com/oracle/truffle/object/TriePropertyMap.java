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
package com.oracle.truffle.object;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.object.Property;

/**
 * Implementation of {@link PropertyMap} as a hash array mapped trie.
 *
 * Keeps insertion order through previous and next key links in the entries.
 */
final class TriePropertyMap extends PropertyMap implements LinkedImmutableMap<Object, Property> {

    private static final TrieNode<Object, Property, LinkedPropertyEntry> EMPTY_LINKED_PROPERTY_NODE = TrieNode.empty();
    private static final TriePropertyMap EMPTY = new TriePropertyMap(0, EMPTY_LINKED_PROPERTY_NODE, null, null);

    /* Enables stricter assertions for debugging. */
    private static final boolean VERIFY = false;

    @SuppressWarnings("hiding")
    static final class LinkedPropertyEntry implements LinkedEntry<Object, Property> {
        private final Property value;
        private final Object prevKey;
        private final Object nextKey;

        LinkedPropertyEntry(Property value, Object prevKey, Object nextKey) {
            this.value = Objects.requireNonNull(value);
            this.prevKey = prevKey;
            this.nextKey = nextKey;
        }

        @Override
        public Object getKey() {
            return value.getKey();
        }

        @Override
        public Property getValue() {
            return value;
        }

        @Override
        public Property setValue(Property value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LinkedPropertyEntry)) {
                return false;
            }
            LinkedPropertyEntry other = (LinkedPropertyEntry) obj;
            return this.value.equals(other.value) && Objects.equals(this.prevKey, other.prevKey) && Objects.equals(this.nextKey, other.nextKey);
        }

        @Override
        public Object getPrevKey() {
            return prevKey;
        }

        @Override
        public Object getNextKey() {
            return nextKey;
        }

        @Override
        public LinkedPropertyEntry withValue(Property value) {
            return new LinkedPropertyEntry(value, prevKey, nextKey);
        }

        @Override
        public LinkedPropertyEntry withPrevKey(Object prevKey) {
            return new LinkedPropertyEntry(value, prevKey, nextKey);
        }

        @Override
        public LinkedPropertyEntry withNextKey(Object nextKey) {
            return new LinkedPropertyEntry(value, prevKey, nextKey);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + "[" + value + "]";
        }
    }

    private final int size;
    private final TrieNode<Object, Property, LinkedPropertyEntry> root;
    private final LinkedPropertyEntry head;
    private final LinkedPropertyEntry tail;

    static int hash(Object key) {
        return key.hashCode();
    }

    static Object key(Property property) {
        return property.getKey();
    }

    private TriePropertyMap(int size, TrieNode<Object, Property, LinkedPropertyEntry> root, LinkedPropertyEntry head, LinkedPropertyEntry tail) {
        this.size = size;
        this.root = root;
        this.head = head;
        this.tail = tail;
        assert verify();
    }

    private boolean verify() {
        assert root.count() == size : root.count() + " != " + size;
        assert (size == 0 && head == null && tail == null) || (size != 0 && head != null && tail != null) : "size=" + size + ", head=" + head + ", tail=" + tail;
        assert head == null || head == getEntry(head.getKey());
        assert tail == null || tail == getEntry(tail.getKey());
        if (VERIFY) {
            assert root.verify(0);
            assert entrySet().stream().count() == size : "size=" + size + ", entries=" + entrySet();
            assert entrySet().stream().allMatch(new Predicate<Map.Entry<Object, Property>>() {
                public boolean test(Map.Entry<Object, Property> e) {
                    return e == getEntry(e.getKey());
                }
            });
        }
        return true;
    }

    public static TriePropertyMap empty() {
        return EMPTY;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Map.Entry<Object, Property> entry : reverseOrderEntrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Property get(Object key) {
        LinkedPropertyEntry entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public LinkedPropertyEntry getEntry(Object key) {
        LinkedPropertyEntry entry = root.find(key, hash(key));
        assert entry == null || entry.getKey().equals(key);
        return entry;
    }

    @Override
    public TriePropertyMap putCopy(Property value) {
        Object key = key(value);
        return copyAndPutImpl(key, value);
    }

    @Override
    public TriePropertyMap copyAndPut(Object key, Property value) {
        if (!value.getKey().equals(key)) {
            throw new IllegalArgumentException("Key must equal extracted key of property.");
        }

        return copyAndPutImpl(key, value);
    }

    private TriePropertyMap copyAndPutImpl(Object key, Property value) {
        int hash = hash(key);
        LinkedPropertyEntry existing = root.find(key, hash);
        TrieNode<Object, Property, LinkedPropertyEntry> newRoot = root;
        LinkedPropertyEntry newTail;
        LinkedPropertyEntry newHead;
        final int newSize;
        final LinkedPropertyEntry newEntry;
        if (existing == null) {
            newSize = size + 1;
            if (tail == null) {
                // inserting into empty map
                newEntry = new LinkedPropertyEntry(value, null, null);
                newHead = newTail = newEntry;
            } else {
                // inserting at the end
                assert tail != null && head != null;
                Object tailKey = tail.getKey();
                newEntry = new LinkedPropertyEntry(value, tailKey, null);
                // old tail needs to point to the new key
                LinkedPropertyEntry tailWithNext = tail.withNextKey(key);
                newRoot = newRoot.put(tailKey, hash(tailKey), tailWithNext);
                if (head == tail) {
                    newHead = tailWithNext;
                } else {
                    newHead = head;
                }
                newTail = newEntry;
            }
        } else if (value.equals(existing.value)) {
            return this;
        } else {
            // replace
            newSize = size;
            newHead = head;
            newTail = tail;

            newEntry = existing.withValue(value);
            assert !newEntry.equals(existing);
            if (existing.getPrevKey() != null) {
                assert getEntry(existing.getPrevKey()).getNextKey().equals(key);
            } else {
                assert existing == head;
                newHead = newEntry;
            }
            if (existing.getNextKey() != null) {
                assert getEntry(existing.getNextKey()).getPrevKey().equals(key);
            } else {
                assert existing == tail;
                newTail = newEntry;
            }
        }
        newRoot = newRoot.put(key, hash, newEntry);
        return new TriePropertyMap(newSize, newRoot, newHead, newTail);
    }

    @Override
    public TriePropertyMap removeCopy(Property value) {
        Object key = key(value);
        return copyAndRemove(key);
    }

    @Override
    public TriePropertyMap copyAndRemove(Object key) {
        int hash = hash(key);
        LinkedPropertyEntry existing = root.find(key, hash);
        if (existing == null) {
            return this;
        } else if (size == 1) {
            return empty();
        }
        TrieNode<Object, Property, LinkedPropertyEntry> newRoot = root;
        LinkedPropertyEntry newHead = head;
        LinkedPropertyEntry newTail = tail;
        if (existing.getPrevKey() != null) {
            Object prevKey = existing.getPrevKey();
            LinkedPropertyEntry existingPrev = getEntry(prevKey);
            LinkedPropertyEntry newPrev = existingPrev.withNextKey(existing.getNextKey());
            newRoot = newRoot.put(prevKey, hash(prevKey), newPrev);
            if (existing == tail) {
                newTail = newPrev;
            }
            if (existingPrev == head) {
                newHead = newPrev;
            }
        }
        if (existing.getNextKey() != null) {
            Object nextKey = existing.getNextKey();
            LinkedPropertyEntry existingNext = getEntry(nextKey);
            LinkedPropertyEntry newNext = existingNext.withPrevKey(existing.getPrevKey());
            newRoot = newRoot.put(nextKey, hash(nextKey), newNext);
            if (existing == head) {
                newHead = newNext;
            }
            if (existingNext == tail) {
                newTail = newNext;
            }
        }
        newRoot = newRoot.remove(key, hash);
        assert newRoot != null;
        return new TriePropertyMap(size - 1, newRoot, newHead, newTail);
    }

    @Override
    public TriePropertyMap replaceCopy(Property oldValue, Property newValue) {
        return putCopy(newValue);
    }

    Iterator<Map.Entry<Object, Property>> orderedEntryIterator() {
        return new LinkedEntryIterator<>(TriePropertyMap.this, head, true);
    }

    Iterator<Map.Entry<Object, Property>> reverseOrderedEntryIterator() {
        return new LinkedEntryIterator<>(TriePropertyMap.this, tail, false);
    }

    @Override
    public Iterator<Object> orderedKeyIterator() {
        return new LinkedKeyIterator<>(TriePropertyMap.this, head, true);
    }

    @Override
    public Iterator<Object> reverseOrderedKeyIterator() {
        return new LinkedKeyIterator<>(TriePropertyMap.this, tail, false);
    }

    @Override
    public Iterator<Property> orderedValueIterator() {
        return new LinkedValueIterator<>(TriePropertyMap.this, head, true);
    }

    @Override
    public Iterator<Property> reverseOrderedValueIterator() {
        return new LinkedValueIterator<>(TriePropertyMap.this, tail, false);
    }

    @Override
    public Set<Map.Entry<Object, Property>> entrySet() {
        return new AbstractSet<Map.Entry<Object, Property>>() {
            @Override
            public Iterator<Map.Entry<Object, Property>> iterator() {
                return orderedEntryIterator();
            }

            @Override
            public int size() {
                return TriePropertyMap.this.size();
            }
        };
    }

    @Override
    public Set<Object> keySet() {
        return new AbstractSet<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return TriePropertyMap.this.orderedKeyIterator();
            }

            @Override
            public int size() {
                return TriePropertyMap.this.size();
            }
        };
    }

    @Override
    public Collection<Property> values() {
        return new AbstractSet<Property>() {
            @Override
            public Iterator<Property> iterator() {
                return TriePropertyMap.this.orderedValueIterator();
            }

            @Override
            public int size() {
                return TriePropertyMap.this.size();
            }
        };
    }

    public Set<Map.Entry<Object, Property>> reverseOrderEntrySet() {
        return new AbstractSet<Map.Entry<Object, Property>>() {
            @Override
            public Iterator<Map.Entry<Object, Property>> iterator() {
                return reverseOrderedEntryIterator();
            }

            @Override
            public int size() {
                return TriePropertyMap.this.size();
            }
        };
    }

    public Set<Object> reverseOrderKeys() {
        return new AbstractSet<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return TriePropertyMap.this.reverseOrderedKeyIterator();
            }

            @Override
            public int size() {
                return TriePropertyMap.this.size();
            }
        };
    }

    public Set<Property> reverseOrderValues() {
        return new AbstractSet<Property>() {
            @Override
            public Iterator<Property> iterator() {
                return TriePropertyMap.this.reverseOrderedValueIterator();
            }

            @Override
            public int size() {
                return TriePropertyMap.this.size();
            }
        };
    }

    @Override
    public Property getLastProperty() {
        return tail == null ? null : tail.getValue();
    }

    @Override
    public String toString() {
        return values().toString();
    }
}
