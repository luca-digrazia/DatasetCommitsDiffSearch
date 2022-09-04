/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.data.vector;

import java.util.Arrays;
import java.util.stream.LongStream;

/**
 * An immutable long vector.
 *
 * @author Haifeng Li
 */
class LongVectorImpl implements LongVector {
    /** The name of vector. */
    private String name;
    /** The vector data. */
    private long[] vector;

    /** Constructor. */
    public LongVectorImpl(String name, long[] vector) {
        this.name = name;
        this.vector = vector;
    }

    @Override
    public long[] array() {
        return vector;
    }

    @Override
    public double[] toDoubleArray() {
        double[] a = new double[vector.length];
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public long getLong(int i) {
        return vector[i];
    }

    @Override
    public Long get(int i) {
        return vector[i];
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public LongStream stream() {
        return Arrays.stream(vector);
    }

    @Override
    public String toString() {
        return toString(10);
    }
}