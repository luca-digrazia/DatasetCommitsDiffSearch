package org.graylog.plugins.pipelineprocessor.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;
import org.mongojack.Id;
import org.mongojack.ObjectId;

import javax.annotation.Nullable;

@AutoValue
public abstract class PipelineDao {
    @JsonProperty("id")
    @Nullable
    @Id
    @ObjectId
    public abstract String id();

    @JsonProperty
    public abstract String title();

    @JsonProperty
    @Nullable
    public abstract String description();

    @JsonProperty
    public abstract String source();

    @JsonProperty
    @Nullable
    public abstract DateTime createdAt();

    @JsonProperty
    @Nullable
    public abstract DateTime modifiedAt();

    public static Builder builder() {
        return new AutoValue_PipelineDao.Builder();
    }

    public abstract Builder toBuilder();

    @JsonCreator
    public static PipelineDao create(@Id @ObjectId @JsonProperty("_id") @Nullable String id,
                                        @JsonProperty("title")  String title,
                                        @JsonProperty("description") @Nullable String description,
                                        @JsonProperty("source") String source,
                                        @Nullable @JsonProperty("created_at") DateTime createdAt,
                                        @Nullable @JsonProperty("modified_at") DateTime modifiedAt) {
        return builder()
                .id(id)
                .title(title)
                .description(description)
                .source(source)
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract PipelineDao build();

        public abstract Builder id(String id);

        public abstract Builder title(String title);

        public abstract Builder description(String description);

        public abstract Builder source(String source);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder modifiedAt(DateTime modifiedAt);
    }
}
