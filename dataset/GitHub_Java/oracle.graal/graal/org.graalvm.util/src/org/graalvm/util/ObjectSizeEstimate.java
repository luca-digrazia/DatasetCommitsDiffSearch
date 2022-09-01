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
package org.graalvm.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class ObjectSizeEstimate {

    private static final int UNCOMPRESSED_POINTER_SIZE = 8;
    private static final int UNCOMPRESSED_HEADER_SIZE = 16;
    private static final int COMPRESSED_POINTER_SIZE = 4;
    private static final int COMPRESSED_HEADER_SIZE = 12;

    public static ObjectSizeEstimate forObject(Object object) {
        return forObject(object, Integer.MAX_VALUE);
    }

    private int headerCount;
    private int pointerCount;
    private int primitiveByteSize;

    private void recordHeader() {
        headerCount++;
    }

    private void recordPointer() {
        pointerCount++;
    }

    private void recordPrimitiveBytes(int size) {
        primitiveByteSize += size;
    }

    public ObjectSizeEstimate add(ObjectSizeEstimate other) {
        ObjectSizeEstimate result = new ObjectSizeEstimate();
        result.headerCount = headerCount + other.headerCount;
        result.primitiveByteSize = primitiveByteSize + other.primitiveByteSize;
        result.pointerCount = pointerCount + other.pointerCount;
        return result;
    }

    public ObjectSizeEstimate subtract(ObjectSizeEstimate other) {
        ObjectSizeEstimate result = new ObjectSizeEstimate();
        result.headerCount = headerCount - other.headerCount;
        result.primitiveByteSize = primitiveByteSize - other.primitiveByteSize;
        result.pointerCount = pointerCount - other.pointerCount;
        return result;
    }

    @Override
    public String toString() {
        return String.format("(#headers=%s, #pointers=%s, #primitiveBytes=%s, totalCompressed=%s, totalNonCompressed=%s)", headerCount, pointerCount, primitiveByteSize,
                        getCompressedTotalBytes(), getTotalBytes());
    }

    public int getCompressedTotalBytes() {
        return headerCount * COMPRESSED_HEADER_SIZE + pointerCount * COMPRESSED_POINTER_SIZE + primitiveByteSize;
    }

    public int getTotalBytes() {
        return headerCount * UNCOMPRESSED_HEADER_SIZE + pointerCount * UNCOMPRESSED_POINTER_SIZE + primitiveByteSize;
    }

    public static ObjectSizeEstimate forObject(Object object, int maxDepth) {
        EconomicMap<Object, Object> identityHashMap = CollectionFactory.newMap(CompareStrategy.IDENTITY_WITH_SYSTEM_HASHCODE);
        ObjectSizeEstimate size = new ObjectSizeEstimate();

        ArrayList<Object> stack = new ArrayList<>();
        ArrayList<Integer> depthStack = new ArrayList<>();
        stack.add(object);
        depthStack.add(0);
        identityHashMap.put(object, object);

        while (!stack.isEmpty()) {
            Object o = stack.remove(stack.size() - 1);
            int depth = depthStack.remove(depthStack.size() - 1);
            size.recordHeader();
            Class<?> c = o.getClass();
            if (c.isArray()) {
                size.recordPrimitiveBytes(Integer.BYTES);
                if (o instanceof byte[]) {
                    size.recordPrimitiveBytes(Byte.BYTES * ((byte[]) o).length);
                } else if (o instanceof boolean[]) {
                    size.recordPrimitiveBytes(Byte.BYTES * ((boolean[]) o).length);
                } else if (o instanceof char[]) {
                    size.recordPrimitiveBytes(Character.BYTES * ((char[]) o).length);
                } else if (o instanceof short[]) {
                    size.recordPrimitiveBytes(Short.BYTES * ((short[]) o).length);
                } else if (o instanceof int[]) {
                    size.recordPrimitiveBytes(Integer.BYTES * ((int[]) o).length);
                } else if (o instanceof long[]) {
                    size.recordPrimitiveBytes(Long.BYTES * ((long[]) o).length);
                } else if (o instanceof float[]) {
                    size.recordPrimitiveBytes(Float.BYTES * ((float[]) o).length);
                } else if (o instanceof double[]) {
                    size.recordPrimitiveBytes(Byte.BYTES * ((double[]) o).length);
                } else {
                    for (Object element : (Object[]) o) {
                        size.recordPointer();
                        if (element != null) {
                            if (depth < maxDepth && !identityHashMap.containsKey(element)) {
                                identityHashMap.put(element, null);
                                stack.add(element);
                                depthStack.add(depth + 1);
                            }
                        }
                    }
                }
            } else {
                while (c != null) {
                    Field[] fields = c.getDeclaredFields();
                    for (Field f : fields) {
                        if (!Modifier.isStatic(f.getModifiers())) {
                            Class<?> type = f.getType();
                            if (type == Byte.TYPE) {
                                size.recordPrimitiveBytes(Byte.BYTES);
                            } else if (type == Boolean.TYPE) {
                                size.recordPrimitiveBytes(Byte.BYTES);
                            } else if (type == Character.TYPE) {
                                size.recordPrimitiveBytes(Character.BYTES);
                            } else if (type == Short.TYPE) {
                                size.recordPrimitiveBytes(Short.BYTES);
                            } else if (type == Integer.TYPE) {
                                size.recordPrimitiveBytes(Integer.BYTES);
                            } else if (type == Long.TYPE) {
                                size.recordPrimitiveBytes(Long.BYTES);
                            } else if (type == Float.TYPE) {
                                size.recordPrimitiveBytes(Float.BYTES);
                            } else if (type == Double.TYPE) {
                                size.recordPrimitiveBytes(Double.BYTES);
                            } else {
                                size.recordPointer();
                                if (maxDepth > 1) {
                                    f.setAccessible(true);
                                    try {
                                        Object inner = f.get(o);
                                        if (inner != null) {
                                            if (depth < maxDepth && !identityHashMap.containsKey(inner)) {
                                                identityHashMap.put(inner, null);
                                                stack.add(inner);
                                                depthStack.add(depth + 1);
                                            }
                                        }
                                    } catch (IllegalArgumentException | IllegalAccessException e) {
                                        throw new UnsupportedOperationException("Must have access privileges to traverse object graph");
                                    }
                                }
                            }
                        }
                    }
                    c = c.getSuperclass();
                }
            }
        }
        return size;
    }
}
