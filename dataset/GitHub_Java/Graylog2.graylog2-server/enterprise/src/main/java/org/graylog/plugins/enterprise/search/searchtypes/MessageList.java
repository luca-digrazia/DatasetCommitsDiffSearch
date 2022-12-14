package org.graylog.plugins.enterprise.search.searchtypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import org.graylog.plugins.enterprise.search.SearchType;

import javax.annotation.Nullable;
import java.util.List;

@AutoValue
@JsonTypeName(MessageList.NAME)
@JsonDeserialize(builder = AutoValue_MessageList.Builder.class)
public abstract class MessageList implements SearchType {
    public static final String NAME = "messages";

    @Override
    @JsonProperty
    public abstract String type();

    @Nullable
    @JsonProperty
    public abstract String id();

    @JsonProperty
    public abstract int limit();

    @JsonProperty
    public abstract int offset();

    @Nullable
    public abstract List<Sort> sort();

    public static Builder builder() {
        return new AutoValue_MessageList.Builder()
                .type(NAME)
                .limit(150)
                .offset(0);
    }

    public abstract Builder toBuilder();

    @Override
    public SearchType withId(String id) {
        return toBuilder().id(id).build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty
        public abstract Builder type(String type);

        @JsonProperty
        public abstract Builder id(@Nullable String id);

        @JsonProperty
        public abstract Builder limit(int limit);

        @JsonProperty
        public abstract Builder offset(int offset);

        @JsonProperty
        public abstract Builder sort(@Nullable List<Sort> sort);

        public abstract MessageList build();
    }

    @AutoValue
    public abstract static class Result implements SearchType.Result {

        @Override
        public abstract String id();

    }
}
