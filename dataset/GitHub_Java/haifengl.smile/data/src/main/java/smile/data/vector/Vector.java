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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import smile.data.measure.NominalScale;
import smile.data.measure.OrdinalScale;
import smile.data.type.DataType;

/**
 * An immutable generic vector.
 *
 * @author Haifeng Li
 */
public interface Vector<T> extends BaseVector<T, T, Stream<T>> {

    /**
     * Returns a vector of LocalDate. If the vector is of strings, it uses the default
     * ISO date formatter that parses a date without an offset, such as '2011-12-03'.
     * If the vector is of other time related objects such as Instant, java.util.Date,
     * java.sql.Timestamp, etc., do a proper conversion.
     */
    Vector<LocalDate> toDate();

    /**
     * Returns a vector of LocalDate. This method assumes that this is a string vector and
     * uses the given date format pattern to parse strings.
     */
    default Vector<LocalDate> toDate(String pattern) {
        return toDate(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Returns a vector of LocalDate. This method assumes that this is a string vector and
     * uses the given date format pattern to parse strings.
     */
    Vector<LocalDate> toDate(DateTimeFormatter format);

    /**
     * Returns a vector of LocalTime. If the vector is of strings, it uses the default
     * ISO time formatter that parses a time without an offset, such as '10:15' or '10:15:30'.
     * If the vector is of other time related objects such as Instant, java.util.Date,
     * java.sql.Timestamp, etc., do a proper conversion.
     */
    Vector<LocalTime> toTime();

    /**
     * Returns a vector of LocalTime. This method assumes that this is a string vector and
     * uses the given time format pattern to parse strings.
     */
    default Vector<LocalTime> toTime(String pattern) {
        return toTime(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Returns a vector of LocalDate. This method assumes that this is a string vector and
     * uses the given time format pattern to parse strings.
     */
    Vector<LocalTime> toTime(DateTimeFormatter format);

    /**
     * Returns a vector of LocalDateTime. If the vector is of strings, it uses the default
     * ISO date time formatter that parses a date without an offset, such as '2011-12-03T10:15:30'.
     * If the vector is of other time related objects such as Instant, java.util.Date,
     * java.sql.Timestamp, etc., do a proper conversion.
     */
    Vector<LocalDateTime> toDateTime();

    /**
     * Returns a vector of LocalDateTime. This method assumes that this is a string vector and
     * uses the given date time format pattern to parse strings.
     */
    default Vector<LocalDateTime> toDateTime(String pattern) {
        return toDateTime(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Returns a vector of LocalDateTime. This method assumes that this is a string vector and
     * uses the given date time format pattern to parse strings.
     */
    Vector<LocalDateTime> toDateTime(DateTimeFormatter format);

    /**
     * Converts strings to nominal values. Depending on how many levels
     * in the nominal scale, the type of returned vector may be byte, short
     * or integer. The missing values/nulls will be converted to -1.
     */
    BaseVector toNominal(NominalScale scale);

    /**
     * Converts strings to ordinal values. Depending on how many levels
     * in the nominal scale, the type of returned vector may be byte, short
     * or integer. The missing values/nulls will be converted to -1.
     */
    BaseVector toOrdinal(OrdinalScale scale);

    /** Returns the distinct values. */
    default List<T> distinct() {
        return stream().distinct().collect(Collectors.toList());
    }

    @Override
    default byte getByte(int i) {
        return ((Number) get(i)).byteValue();
    }

    @Override
    default short getShort(int i) {
        return ((Number) get(i)).shortValue();
    }

    @Override
    default int getInt(int i) {
        return ((Number) get(i)).intValue();
    }

    @Override
    default long getLong(int i) {
        return ((Number) get(i)).longValue();
    }

    @Override
    default float getFloat(int i) {
        Number x = (Number) get(i);
        return x == null ? Float.NaN : x.floatValue();
    }

    @Override
    default double getDouble(int i) {
        Number x = (Number) get(i);
        return x == null ? Double.NaN : x.doubleValue();
    }

    /** Checks whether the value at position i is null. */
    default boolean isNullAt(int i) {
        return get(i) == null;
    }

    /** Returns true if there are any NULL values in this row. */
    default boolean anyNull() {
        return stream().filter(Objects::isNull).findAny().isPresent();
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).map(Object::toString).collect(Collectors.joining(", ", "[", suffix));
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param clazz the class of data type.
     * @param vector the data of vector.
     */
    static <T> Vector<T> of(String name, Class clazz, T[] vector) {
        return new VectorImpl<>(name, clazz, vector);
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param type the data type of vector.
     * @param vector the data of vector.
     */
    static <T> Vector<T> of(String name, DataType type, T[] vector) {
        return new VectorImpl<>(name, type, vector);
    }
}