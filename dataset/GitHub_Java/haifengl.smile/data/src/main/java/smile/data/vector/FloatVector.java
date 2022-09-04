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

import smile.data.type.ContinuousMeasure;
import smile.data.type.DataType;
import smile.data.type.DataTypes;

import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * An immutable float vector.
 *
 * @author Haifeng Li
 */
public interface FloatVector extends BaseVector<Float, Double, DoubleStream> {
    @Override
    default DataType type() {
        return DataTypes.FloatType;
    }

    /** Returns the scale of measure. Returns null if unknown. */
    ContinuousMeasure getScale();

    /** Sets the (optional) scale of measure. */
    void setScale(ContinuousMeasure scale);

    @Override
    default byte getByte(int i) {
        throw new UnsupportedOperationException("cast float to byte");
    }

    @Override
    default short getShort(int i) {
        throw new UnsupportedOperationException("cast float to short");
    }

    @Override
    default int getInt(int i) {
        throw new UnsupportedOperationException("cast float to int");
    }

    @Override
    default long getLong(int i) {
        throw new UnsupportedOperationException("cast float to long");
    }

    @Override
    default double getDouble(int i) {
        return getFloat(i);
    }


    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named float vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static FloatVector of(String name, float[] vector) {
        return new FloatVectorImpl(name, vector);
    }
}