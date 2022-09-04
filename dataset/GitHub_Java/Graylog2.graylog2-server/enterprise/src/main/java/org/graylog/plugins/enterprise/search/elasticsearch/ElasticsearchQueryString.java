package org.graylog.plugins.enterprise.search.elasticsearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import org.graylog.plugins.enterprise.search.engine.BackendQuery;

@AutoValue
@JsonTypeName(ElasticsearchQueryString.NAME)
@JsonDeserialize(builder = AutoValue_ElasticsearchQueryString.Builder.class)
public abstract class ElasticsearchQueryString implements BackendQuery {

    public static final String NAME = "elasticsearch";

    @Override
    @JsonProperty
    public abstract String type();

    @JsonProperty
    public abstract String queryString();

    public static Builder builder() {
        return new AutoValue_ElasticsearchQueryString.Builder().type(NAME);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty
        public abstract Builder type(String type);

        @JsonProperty
        public abstract Builder queryString(String queryString);

        public abstract ElasticsearchQueryString build();
    }
}
