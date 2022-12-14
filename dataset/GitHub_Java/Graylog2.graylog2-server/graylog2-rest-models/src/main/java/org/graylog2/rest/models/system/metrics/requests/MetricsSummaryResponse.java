/**
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.rest.models.system.metrics.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.Collection;

@AutoValue
public abstract class MetricsSummaryResponse {
    @JsonProperty
    public abstract int total();
    @JsonProperty
    public abstract Collection metrics();

    @JsonCreator
    public static MetricsSummaryResponse create(@JsonProperty("total") int total, @JsonProperty("metrics") Collection metrics) {
        return new AutoValue_MetricsSummaryResponse(total, metrics);
    }

    public static MetricsSummaryResponse create(Collection metrics) {
        return new AutoValue_MetricsSummaryResponse(metrics.size(), metrics);
    }
}
