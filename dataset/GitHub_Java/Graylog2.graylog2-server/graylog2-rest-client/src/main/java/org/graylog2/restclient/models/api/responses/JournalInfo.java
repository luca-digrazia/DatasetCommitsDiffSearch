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
package org.graylog2.restclient.models.api.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

import javax.annotation.Nullable;

public class JournalInfo {
    @JsonProperty(required = true)
    public boolean enabled;

    public long appendEventsPerSecond;

    public long readEventsPerSecond;

    public long uncommittedJournalEntries;

    public long journalSize;

    public long journalSizeLimit;

    public int numberOfSegments;

    @Nullable
    public DateTime oldestSegment;

    @Nullable
    public KafkaJournalConfiguration journalConfig;


}
