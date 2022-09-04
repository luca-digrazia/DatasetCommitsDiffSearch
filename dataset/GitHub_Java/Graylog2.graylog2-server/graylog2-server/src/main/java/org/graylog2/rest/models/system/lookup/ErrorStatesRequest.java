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
package org.graylog2.rest.models.system.lookup;

import com.google.auto.value.AutoValue;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.graylog.autovalue.WithBeanGetter;

import java.util.Set;

import javax.annotation.Nullable;

@AutoValue
@WithBeanGetter
@JsonAutoDetect
@JsonDeserialize(builder = AutoValue_ErrorStatesRequest.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ErrorStatesRequest {

    @Nullable
    @JsonProperty("tables")
    public abstract Set<String > tables();

    @Nullable
    @JsonProperty("data_adapters")
    public abstract Set<String > dataAdapters();

    @Nullable
    @JsonProperty("caches")
    public abstract Set<String > caches();

    public static Builder builder() {
        return new AutoValue_ErrorStatesRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("tables")
        public abstract Builder tables(@Nullable Set<String> tables);

        @JsonProperty("data_adapters")
        public abstract Builder dataAdapters(@Nullable Set<String> dataAdapters);

        @JsonProperty("caches")
        public abstract Builder caches(@Nullable Set<String> caches);

        public abstract ErrorStatesRequest build();
    }
}
