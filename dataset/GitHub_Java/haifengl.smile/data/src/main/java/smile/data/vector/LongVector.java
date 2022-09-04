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

import java.util.stream.Collectors;
import java.util.stream.LongStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;

/**
 * An immutable long vector.
 *
 * @author Haifeng Li
 */
public interface LongVector extends BaseVector<Long, Long, LongStream> {
    @Override
    default DataType type() {
        return DataTypes.LongType;
    }

    /**
     * Returns the value at position i.
     */
    long getLong(int i);

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %d more]", size() - n);
        return stream().limit(n).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named long vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static LongVector of(String name, long[] vector) {
        return new LongVectorImpl(name, vector);
    }
}