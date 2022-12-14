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
package org.graylog.events.processor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.graylog.events.contentpack.entities.EventProcessorConfigEntity;
import org.graylog.scheduler.JobDefinitionConfig;
import org.graylog.scheduler.clock.JobSchedulerClock;
import org.graylog2.contentpacks.ContentPackable;
import org.graylog2.plugin.rest.ValidationResult;

import java.util.Optional;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = EventProcessorConfig.TYPE_FIELD,
        visible = true,
        defaultImpl = EventProcessorConfig.FallbackConfig.class)
public interface EventProcessorConfig extends ContentPackable<EventProcessorConfigEntity> {
    String TYPE_FIELD = "type";

    @JsonProperty(TYPE_FIELD)
    String type();

    /**
     * Returns a {@link JobDefinitionConfig} for this event processor configuration. If the event processor shouldn't
     * be scheduled, this method returns an empty {@link Optional}.
     *
     * @param eventDefinition the event definition
     * @param clock           the clock that can be used to get the current time
     * @return the job definition config or an empty optional if the processor shouldn't be scheduled
     */
    @JsonIgnore
    default Optional<EventProcessorSchedulerConfig> toJobSchedulerConfig(EventDefinition eventDefinition, JobSchedulerClock clock) {
        return Optional.empty();
    }

    @JsonIgnore
    ValidationResult validate();

    interface Builder<SELF> {
        @JsonProperty(TYPE_FIELD)
        SELF type(String type);
    }

    class FallbackConfig implements EventProcessorConfig {
        @Override
        public String type() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValidationResult validate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EventProcessorConfigEntity toContentPackEntity() {
            throw new UnsupportedOperationException();
        }
    }
}
