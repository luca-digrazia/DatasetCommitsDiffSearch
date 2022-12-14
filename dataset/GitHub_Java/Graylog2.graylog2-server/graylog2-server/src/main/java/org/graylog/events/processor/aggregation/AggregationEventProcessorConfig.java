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
package org.graylog.events.processor.aggregation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.graylog.events.contentpack.entities.AggregationEventProcessorConfigEntity;
import org.graylog.events.contentpack.entities.EventProcessorConfigEntity;
import org.graylog.events.processor.EventDefinition;
import org.graylog.events.processor.EventProcessorConfig;
import org.graylog.events.processor.EventProcessorSchedulerConfig;
import org.graylog.scheduler.clock.JobSchedulerClock;
import org.graylog.events.processor.EventProcessorExecutionJob;
import org.graylog.scheduler.schedule.IntervalJobSchedule;
import org.graylog2.contentpacks.model.entities.references.ValueReference;
import org.graylog2.plugin.indexer.searches.timeranges.AbsoluteRange;
import org.graylog2.plugin.rest.ValidationResult;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@AutoValue
@JsonTypeName(AggregationEventProcessorConfig.TYPE_NAME)
@JsonDeserialize(builder = AggregationEventProcessorConfig.Builder.class)
public abstract class AggregationEventProcessorConfig implements EventProcessorConfig {
    public static final String TYPE_NAME = "aggregation-v1";

    private static final String FIELD_QUERY = "query";
    private static final String FIELD_STREAMS = "streams";
    private static final String FIELD_GROUP_BY = "group_by";
    private static final String FIELD_SERIES = "series";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final String FIELD_SEARCH_WITHIN_MS = "search_within_ms";
    private static final String FIELD_EXECUTE_EVERY_MS = "execute_every_ms";

    @JsonProperty(FIELD_QUERY)
    public abstract String query();

    @JsonProperty(FIELD_STREAMS)
    public abstract ImmutableSet<String> streams();

    @JsonProperty(FIELD_GROUP_BY)
    public abstract List<String> groupBy();

    @JsonProperty(FIELD_SERIES)
    public abstract List<AggregationSeries> series();

    @JsonProperty(FIELD_CONDITIONS)
    public abstract Optional<AggregationConditions> conditions();

    @JsonProperty(FIELD_SEARCH_WITHIN_MS)
    public abstract long searchWithinMs();

    @JsonProperty(FIELD_EXECUTE_EVERY_MS)
    public abstract long executeEveryMs();

    public static Builder builder() {
        return Builder.create();
    }

    public abstract Builder toBuilder();

    @Override
    public Optional<EventProcessorSchedulerConfig> toJobSchedulerConfig(EventDefinition eventDefinition, JobSchedulerClock clock) {
        final DateTime now = clock.nowUTC();

        // We need an initial timerange for the first execution of the event processor
        final AbsoluteRange timerange = AbsoluteRange.create(now.minus(searchWithinMs()), now);

        final EventProcessorExecutionJob.Config jobDefinitionConfig = EventProcessorExecutionJob.Config.builder()
                .eventDefinitionId(eventDefinition.id())
                .processingWindowSize(searchWithinMs())
                .processingHopSize(executeEveryMs())
                .parameters(AggregationEventProcessorParameters.builder()
                        .timerange(timerange)
                        .build())
                .build();
        final IntervalJobSchedule schedule = IntervalJobSchedule.builder()
                .interval(executeEveryMs())
                .unit(TimeUnit.MILLISECONDS)
                .build();

        return Optional.of(EventProcessorSchedulerConfig.create(jobDefinitionConfig, schedule));
    }

    @AutoValue.Builder
    public static abstract class Builder implements EventProcessorConfig.Builder<Builder> {
        @JsonCreator
        public static Builder create() {
            return new AutoValue_AggregationEventProcessorConfig.Builder()
                    .type(TYPE_NAME);
        }

        @JsonProperty(FIELD_QUERY)
        public abstract Builder query(String query);

        @JsonProperty(FIELD_STREAMS)
        public abstract Builder streams(Set<String> streams);

        @JsonProperty(FIELD_GROUP_BY)
        public abstract Builder groupBy(List<String> groupBy);

        @JsonProperty(FIELD_SERIES)
        public abstract Builder series(List<AggregationSeries> series);

        @JsonProperty(FIELD_CONDITIONS)
        public abstract Builder conditions(@Nullable AggregationConditions conditions);

        @JsonProperty(FIELD_SEARCH_WITHIN_MS)
        public abstract Builder searchWithinMs(long searchWithinMs);

        @JsonProperty(FIELD_EXECUTE_EVERY_MS)
        public abstract Builder executeEveryMs(long executeEveryMs);

        public abstract AggregationEventProcessorConfig build();
    }

    private boolean isConditionsEmpty() {
        return !conditions().isPresent() || !conditions().get().expression().isPresent();
    }

    @Override
    public ValidationResult validate() {
        final ValidationResult validationResult = new ValidationResult();

        if (searchWithinMs() <= 0) {
            validationResult.addError(FIELD_SEARCH_WITHIN_MS,
                "Filter & Aggregation search_within_ms must be greater than 0.");
        }
        if (executeEveryMs() <= 0) {
            validationResult.addError(FIELD_EXECUTE_EVERY_MS,
                "Filter & Aggregation execute_every_ms must be greater than 0.");
        }
        if (!groupBy().isEmpty() && (series().isEmpty() || isConditionsEmpty())) {
            validationResult.addError(FIELD_SERIES, "Aggregation with group_by must also contain series");
            validationResult.addError(FIELD_CONDITIONS, "Aggregation with group_by must also contain conditions");
        }
        if (series().isEmpty() && !isConditionsEmpty()) {
            validationResult.addError(FIELD_SERIES, "Aggregation with conditions must also contain series");
        }
        if (!series().isEmpty() && isConditionsEmpty()) {
            validationResult.addError(FIELD_CONDITIONS, "Aggregation with series must also contain conditions");
        }

        return validationResult;
    }

    @Override
    public EventProcessorConfigEntity toContentPackEntity() {
        return AggregationEventProcessorConfigEntity.builder()
            .type(type())
            .query(ValueReference.of(query()))
            .streams(streams())
            .groupBy(groupBy())
            .series(series())
            .conditions(conditions().orElse(null))
            .executeEveryMs(ValueReference.of(executeEveryMs()))
            .searchWithinMs(ValueReference.of(searchWithinMs()))
            .build();
    }
}
