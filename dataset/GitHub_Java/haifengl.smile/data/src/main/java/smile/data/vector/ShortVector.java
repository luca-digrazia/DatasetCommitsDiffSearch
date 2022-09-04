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

import smile.data.type.DataType;
import smile.data.type.DataTypes;

import java.util.stream.IntStream;

/**
 * An immutable short vector.
 *
 * @author Haifeng Li
 */
public interface ShortVector extends BaseVector<Short, Integer, IntStream> {
    @Override
    default DataType type() {
        return DataTypes.ShortType;
    }

    /**
     * Returns the value at position i.
     */
    short getShort(int i);

    /** Creates a named short vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static ShortVector of(String name, short[] vector) {
        return new ShortVectorImpl(name, vector);
    }
}