package org.graylog.plugins.enterprise.search.searchtypes.pivot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import org.graylog.plugins.enterprise.search.SearchType;
import org.graylog2.plugin.indexer.searches.timeranges.AbsoluteRange;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

@AutoValue
public abstract class PivotResult implements SearchType.Result {
    private static final String FIELD_EFFECTIVE_TIMERANGE = "effective_timerange";

    @Override
    @JsonProperty
    public abstract String id();

    @Override
    @JsonProperty
    public String type() {
        return Pivot.NAME;
    }

    @JsonProperty
    public abstract ImmutableList<Row> rows();

    @JsonProperty
    public abstract long total();

    @JsonProperty(FIELD_EFFECTIVE_TIMERANGE)
    public abstract AbsoluteRange effectiveTimerange();

    public static Builder builder() {
        return new AutoValue_PivotResult.Builder();
    }

    public static PivotResult empty(String id) {
        return builder().id(id).build();
    }

    @AutoValue.Builder
    public static abstract class Builder {

        public abstract Builder id(String id);

        abstract ImmutableList.Builder<Row> rowsBuilder();
        public Builder addRow(Row row) {
            rowsBuilder().add(row);
            return this;
        }
        public Builder addAllRows(List<Row> rows) {
            rowsBuilder().addAll(rows);
            return this;
        }

        public abstract Builder total(long total);

        public abstract Builder effectiveTimerange(AbsoluteRange effectiveTimerange);

        public abstract PivotResult build();
    }

    @AutoValue
    public static abstract class Row {

        @JsonProperty
        public abstract ImmutableList<String> key();

        @JsonProperty
        public abstract ImmutableList<Value> values();

        @JsonProperty
        public abstract String source();

        public static Builder builder() {
            return new AutoValue_PivotResult_Row.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder key(ImmutableList<String> key);

            abstract ImmutableList.Builder<Value> valuesBuilder();
            //public abstract Builder values(ImmutableList<Value> values);
            public Builder addValue(Value value) {
                valuesBuilder().add(value);
                return this;
            }
            public Builder addAllValues(List<Value> values) {
                valuesBuilder().addAll(values);
                return this;
            }

            public abstract Builder source(String source);

            public abstract Row build();
        }
    }

    @AutoValue
    public static abstract class Value {

        @JsonProperty
        public abstract ImmutableList<String> key();

        @JsonProperty
        @Nullable
        public abstract Object value();

        @JsonProperty
        public abstract boolean rollup();

        @JsonProperty
        public abstract String source();

        public static Value create(Collection<String> key, @Nullable Object value, boolean rollup, String source) {
            return new AutoValue_PivotResult_Value(ImmutableList.copyOf(key), value, rollup, source);
        }
    }
}
