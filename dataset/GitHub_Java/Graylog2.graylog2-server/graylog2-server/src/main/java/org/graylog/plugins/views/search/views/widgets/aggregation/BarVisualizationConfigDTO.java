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
package org.graylog.plugins.views.search.views.widgets.aggregation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

@AutoValue
@JsonTypeName(BarVisualizationConfigDTO.NAME)
@JsonDeserialize(builder = BarVisualizationConfigDTO.Builder.class)
public abstract class BarVisualizationConfigDTO implements VisualizationConfigDTO {
    public static final String NAME = "bar";
    private static final String FIELD_BAR_MODE = "barmode";

    public enum BarMode {
        stack,
        overlay,
        group,
        relative
    };

    @JsonProperty
    public abstract BarMode barmode();

    @AutoValue.Builder
    public abstract static class Builder {

        @JsonProperty(FIELD_BAR_MODE)
        public abstract Builder barmode(BarMode barMode);

        public abstract BarVisualizationConfigDTO build();

        @JsonCreator
        public static Builder builder() {
            return new AutoValue_BarVisualizationConfigDTO.Builder();
        }
    }
}
