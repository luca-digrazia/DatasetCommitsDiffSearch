package com.oracle.truffle.espresso.jni;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Manages a collection of weak references associated to handles.
 */
public class Handles<T> {

    private final static int DEFAULT_INITIAL_CAPACITY = 32;
    private final WeakHashMap<T, Integer> map;
    private final LinkedList<Integer> freeList = new LinkedList<>();

    // Non-empty.
    private WeakReference<T>[] handles;

    /**
     * Creates a handle collection pre-allocated capacity.
     *
     * @param initialCapacity must be > 0
     */
    public Handles(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be > 0");
        }
        map = new WeakHashMap<>(initialCapacity);
        handles = new WeakReference[initialCapacity];
    }

    public Handles() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Creates a new handle for the given object or returns an existing handle if the object is
     * already in the collection.
     * 
     * @return new or existing handle, provided handles are guanteed to be > 0
     */
    public synchronized int handlify(T object) {
        Objects.requireNonNull(object);
        Integer handle = map.get(object);
        return handle != null
                        ? handle
                        : addHandle(object);
    }

    /**
     * Returns the object associated with a handle. This operation is performance-critical,
     * shouldn't block.
     *
     * @param index handle, must be > 0 and fit in an integer
     * @return the object associated with the handle or null if was collected
     */
    public T getObject(long index) {
        if (index <= 0) {
            throw new IllegalArgumentException("index");
        }
        WeakReference<T> weakRef = handles[Math.toIntExact(index)];
        return weakRef != null
                        ? weakRef.get()
                        : null;
    }

    /**
     * Returns the handle associated with a given object.
     * 
     * @return The handle associated with the given object or -1 if the object doesn't have a handle
     *         or the object was collected. A valid handle is guaranteed to be != 0.
     */
    public synchronized long getIndex(T object) {
        Integer index = map.get(Objects.requireNonNull(object));
        return index != null
                        ? index
                        : -1;
    }

    private int getFreeSlot() {
        if (!freeList.isEmpty()) {
            return freeList.removeFirst();
        }
        // 0 is a dummy entry, start at 1 to avoid NULL handles.
        for (int i = 1; i < handles.length; ++i) {
            if (handles[i] == null || handles[i].get() == null) {
                freeList.addLast(i);
            }
        }
        return freeList.isEmpty()
                        ? -1
                        : freeList.removeFirst();
    }

    private synchronized int addHandle(T object) {
        Objects.requireNonNull(object);
        int index = getFreeSlot();
        if (index < 0) { // no slot available
            WeakReference<T>[] newHandles = Arrays.copyOf(handles, 2 * handles.length);
            for (int i = handles.length; i < newHandles.length; ++i) {
                freeList.addLast(i);
            }
            handles = newHandles;
            index = freeList.removeFirst();
        }
        assert index >= 0;
        handles[index] = new WeakReference<>(object);
        map.put(object, index);
        return index;
    }
}
