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
package org.graylog2.shared.system.stats;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.graylog2.shared.system.stats.os.OsStats;
import org.graylog2.shared.system.stats.process.ProcessStats;

@JsonAutoDetect
@AutoValue
public abstract class SystemStats {
    @JsonProperty
    public abstract OsStats osStats();

    @JsonProperty
    public abstract ProcessStats processStats();

    public static SystemStats create(OsStats osStats, ProcessStats processStats) {
        return new AutoValue_SystemStats(osStats, processStats);
    }
}
