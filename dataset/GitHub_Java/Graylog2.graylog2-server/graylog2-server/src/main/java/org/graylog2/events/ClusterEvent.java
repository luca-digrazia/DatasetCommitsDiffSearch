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
package org.graylog2.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.graylog2.plugin.system.NodeId;
import org.hibernate.validator.constraints.NotEmpty;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mongojack.Id;
import org.mongojack.ObjectId;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@JsonAutoDetect
@AutoValue
public abstract class ClusterEvent {
    @Id
    @ObjectId
    @Nullable
    public abstract String id();

    @JsonProperty
    @Nullable
    public abstract DateTime date();

    @JsonProperty
    @Nullable
    public abstract String producer();

    @JsonProperty
    @Nullable
    public abstract Set<String> consumers();

    @JsonProperty
    @Nullable
    public abstract String eventClass();

    @JsonProperty
    @Nullable
    public abstract Map<String, Object> payload();


    @JsonCreator
    public static ClusterEvent create(@Id @ObjectId @JsonProperty("_id") @Nullable String id,
                                      @JsonProperty("date") @Nullable DateTime date,
                                      @JsonProperty("producer") @Nullable String producer,
                                      @JsonProperty("consumers") @Nullable Set<String> consumers,
                                      @JsonProperty("event_class") @Nullable String eventClass,
                                      @JsonProperty("payload") @Nullable Map<String, Object> payload) {
        return new AutoValue_ClusterEvent(id, date, producer, consumers, eventClass, payload);
    }

    public static ClusterEvent create(@NotEmpty String producer,
                                      @NotEmpty String eventClass,
                                      @NotEmpty Map<String, Object> payload) {
        return new AutoValue_ClusterEvent(null,
                DateTime.now(DateTimeZone.UTC),
                producer,
                Collections.<String>emptySet(),
                eventClass,
                payload);
    }
}
