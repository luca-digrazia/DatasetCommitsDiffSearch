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
package org.graylog.plugins.views.search.export;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.elasticsearch.search.sort.SortOrder;
import org.graylog.plugins.views.search.elasticsearch.ElasticsearchQueryString;
import org.graylog.plugins.views.search.searchtypes.Sort;
import org.graylog2.decorators.Decorator;
import org.graylog2.plugin.indexer.searches.timeranges.AbsoluteRange;
import org.graylog2.plugin.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.graylog.plugins.views.search.export.LinkedHashSetUtil.linkedHashSetOf;

@AutoValue
public abstract class ExportMessagesCommand {
    public static final ElasticsearchQueryString DEFAULT_QUERY = ElasticsearchQueryString.empty();
    public static final Set<String> DEFAULT_STREAMS = ImmutableSet.of();
    public static final LinkedHashSet<String> DEFAULT_FIELDS = linkedHashSetOf("timestamp", "source", "message");
    public static final LinkedHashSet<Sort> DEFAULT_SORT = linkedHashSetOf(Sort.create("timestamp", SortOrder.DESC));
    public static final int DEFAULT_CHUNK_SIZE = 1000;

    public static AbsoluteRange defaultTimeRange() {
        try {
            RelativeRange lastFiveMinutes = RelativeRange.create(300);
            return AbsoluteRange.create(lastFiveMinutes.getFrom(), lastFiveMinutes.getTo());
        } catch (InvalidRangeParametersException e) {
            throw new RuntimeException("Error creating default time range", e);
        }
    }

    public abstract AbsoluteRange timeRange();

    public abstract ElasticsearchQueryString queryString();

    public abstract Set<String> streams();

    public abstract LinkedHashSet<String> fieldsInOrder();

    public abstract LinkedHashSet<Sort> sort();

    public abstract List<Decorator> decorators();

    public abstract int chunkSize();

    public abstract OptionalInt limit();

    public static ExportMessagesCommand withDefaults() {
        return builder().build();
    }

    public static ExportMessagesCommand.Builder builder() {
        return ExportMessagesCommand.Builder.create();
    }

    public abstract ExportMessagesCommand.Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder timeRange(AbsoluteRange timeRange);

        public abstract Builder streams(Set<String> streams);

        public abstract Builder queryString(ElasticsearchQueryString queryString);

        public abstract Builder fieldsInOrder(LinkedHashSet<String> fieldsInOrder);

        public Builder fieldsInOrder(String... fieldsInOrder) {
            return fieldsInOrder(linkedHashSetOf(fieldsInOrder));
        }

        public abstract Builder sort(LinkedHashSet<Sort> sort);

        public abstract Builder decorators(List<Decorator> decorators);

        public abstract Builder chunkSize(int chunkSize);

        public abstract Builder limit(Integer limit);

        abstract ExportMessagesCommand autoBuild();

        public ExportMessagesCommand build() {
            return autoBuild();
        }

        public static Builder create() {
            return new AutoValue_ExportMessagesCommand.Builder()
                    .timeRange(defaultTimeRange())
                    .streams(DEFAULT_STREAMS)
                    .queryString(DEFAULT_QUERY)
                    .fieldsInOrder(DEFAULT_FIELDS)
                    .sort(DEFAULT_SORT)
                    .decorators(Collections.emptyList())
                    .chunkSize(DEFAULT_CHUNK_SIZE);
        }
    }
}
