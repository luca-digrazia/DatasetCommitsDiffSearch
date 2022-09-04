package org.graylog.plugins.enterprise.search.searchtypes.pivot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import org.graylog.plugins.enterprise.search.Filter;
import org.graylog.plugins.enterprise.search.SearchType;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.collect.ImmutableList.of;

@AutoValue
@JsonTypeName(Pivot.NAME)
@JsonDeserialize(builder = Pivot.Builder.class)
public abstract class Pivot implements SearchType {
    public static final String NAME = "pivot";

    @Override
    public abstract String type();

    @Override
    @Nullable
    @JsonProperty
    public abstract String id();

    @JsonProperty("row_groups")
    public abstract List<BucketSpec> rowGroups();

    @JsonProperty("column_groups")
    public abstract List<BucketSpec> columnGroups();

    @JsonProperty
    public abstract List<SeriesSpec> series();

    @JsonProperty
    public abstract boolean rollup();

    @Nullable
    @Override
    public abstract Filter filter();

    @Override
    public SearchType withId(String id) {
        return builder().id(id).build();
    }

    @Override
    public SearchType applyExecutionContext(ObjectMapper objectMapper, JsonNode state) {
        return this;
    }

    public static Builder builder() {
        return new AutoValue_Pivot.Builder()
                .rowGroups(of())
                .columnGroups(of());
    }

    @AutoValue.Builder
    public static abstract class Builder {

        @JsonCreator
        public static Builder createDefault() {
            return builder();
        }

        @JsonProperty
        public abstract Builder type(String type);

        @JsonProperty
        public abstract Builder id(@Nullable String id);

        @JsonProperty("row_groups")
        public abstract Builder rowGroups(@Nullable List<BucketSpec> rowGroups);

        @JsonProperty("column_groups")
        public abstract Builder columnGroups(@Nullable List<BucketSpec> columnGroups);

        @JsonProperty
        public abstract Builder series(List<SeriesSpec> series);

        @JsonProperty
        public abstract Builder rollup(boolean rollup);

        @JsonProperty
        public abstract Builder filter(@Nullable Filter filter);

        public abstract Pivot build();
    }

}
