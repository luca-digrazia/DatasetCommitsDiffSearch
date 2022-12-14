/**
 * The MIT License
 * Copyright (c) 2012 Graylog, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.plugin.indexer.searches.timeranges;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import org.graylog2.plugin.Tools;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Map;

@AutoValue
@JsonTypeName(value = AbsoluteRange.ABSOLUTE)
public abstract class AbsoluteRange extends TimeRange {

    public static final String ABSOLUTE = "absolute";

    @JsonProperty
    public abstract String type();

    @JsonProperty
    public abstract DateTime from();

    @JsonProperty
    public abstract DateTime to();

    public static Builder builder() {
        return new AutoValue_AbsoluteRange.Builder();
    }

    @JsonCreator
    public static AbsoluteRange create(@JsonProperty("type") String type,
                                       @JsonProperty("from") DateTime from,
                                       @JsonProperty("to") DateTime to) {
        return builder().type(type).from(from).to(to).build();
    }

    public static AbsoluteRange create(DateTime from, DateTime to) {
        return builder().type(ABSOLUTE).from(from).to(to).build();
    }

    public static AbsoluteRange create(String from, String to) throws InvalidRangeParametersException {
        return builder().type(ABSOLUTE).from(from).to(to).build();
    }

    @Override
    public DateTime getFrom() {
        return from();
    }

    @Override
    public DateTime getTo() {
        return to();
    }

    @Override
    public Map<String, Object> getPersistedConfig() {
        return ImmutableMap.<String, Object>of(
                "type", ABSOLUTE,
                "from", getFrom(),
                "to", getTo());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract AbsoluteRange build();

        public abstract Builder type(String type);

        public abstract Builder to(DateTime to);

        public abstract Builder from(DateTime to);

        // TODO replace with custom build()
        public Builder to(String to) throws InvalidRangeParametersException {
            try {
                return to(parseDateTime(to));
            } catch (IllegalArgumentException e) {
                throw new InvalidRangeParametersException();
            }
        }

        // TODO replace with custom build()
        public Builder from(String from) throws InvalidRangeParametersException {
            try {
                return from(parseDateTime(from));
            } catch (IllegalArgumentException e) {
                throw new InvalidRangeParametersException();
            }
        }

        private DateTime parseDateTime(String to) {
            DateTime ts;
            if (to.contains("T")) {
                ts = DateTime.parse(to, ISODateTimeFormat.dateTime());
            } else {
                ts = DateTime.parse(to, Tools.timeFormatterWithOptionalMilliseconds());
            }
            return ts;
        }
    }
}
