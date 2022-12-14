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
package org.graylog.plugins.views.migrations.V20191125144500_MigrateDashboardsToViewsSupport.viewwidgets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.Optional;

@AutoValue
public abstract class AutoInterval implements Interval {
    public static final String type = "auto";
    private static final String FIELD_SCALING = "scaling";

    @JsonProperty
    public abstract String type();

    @JsonProperty(FIELD_SCALING)
    public abstract Optional<Double> scaling();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder type(String type);
        public abstract Builder scaling(@Nullable Double scaling);

        public abstract AutoInterval build();

        static Builder builder() { return new AutoValue_AutoInterval.Builder().type(type); };
    }
}

