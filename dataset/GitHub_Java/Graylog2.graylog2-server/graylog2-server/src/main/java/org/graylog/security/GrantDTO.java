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
package org.graylog.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import org.graylog.autovalue.WithBeanGetter;
import org.graylog2.utilities.GRN;
import org.mongojack.Id;
import org.mongojack.ObjectId;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@AutoValue
@WithBeanGetter
@JsonAutoDetect
@JsonDeserialize(builder = GrantDTO.Builder.class)
public abstract class GrantDTO {
    private static final String FIELD_ID = "id";
    static final String FIELD_GRANTEE = "grantee";
    private static final String FIELD_ROLE = "role";
    public static final String FIELD_TARGET = "target";
    private static final String FIELD_CREATED_BY = "created_by";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_UPDATED_BY = "updated_by";
    private static final String FIELD_UPDATED_AT = "updated_at";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    @Id
    @ObjectId
    @Nullable
    @JsonProperty(FIELD_ID)
    public abstract String id();

    @JsonProperty(FIELD_GRANTEE)
    public abstract GRN grantee();

    @NotBlank
    @JsonProperty(FIELD_ROLE)
    public abstract String role();

    @NotBlank
    @JsonProperty(FIELD_TARGET)
    public abstract GRN target();

    @JsonProperty(FIELD_CREATED_BY)
    public abstract String createdBy();

    @JsonProperty(FIELD_CREATED_AT)
    public abstract ZonedDateTime createdAt();

    @JsonProperty(FIELD_UPDATED_BY)
    public abstract String updatedBy();

    @JsonProperty(FIELD_UPDATED_AT)
    public abstract ZonedDateTime updatedAt();

    @JsonProperty(FIELD_EXPIRES_AT)
    public abstract Optional<ZonedDateTime> expiresAt();

    public static Builder builder() {
        return Builder.create();
    }

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonCreator
        public static Builder create() {
            return new AutoValue_GrantDTO.Builder()
                    .createdBy("")
                    .createdAt(ZonedDateTime.now(ZoneOffset.UTC))
                    .updatedBy("")
                    .updatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        }

        @Id
        @ObjectId
        @JsonProperty(FIELD_ID)
        public abstract Builder id(@Nullable String id);

        @JsonProperty(FIELD_GRANTEE)
        public abstract Builder grantee(GRN grantee);

        @JsonProperty(FIELD_ROLE)
        public abstract Builder role(String role);

        @JsonProperty(FIELD_TARGET)
        public abstract Builder target(GRN target);

        @JsonProperty(FIELD_CREATED_BY)
        public abstract Builder createdBy(String createdBy);

        @JsonProperty(FIELD_CREATED_AT)
        public abstract Builder createdAt(ZonedDateTime createdAt);

        @JsonProperty(FIELD_UPDATED_BY)
        public abstract Builder updatedBy(String updatedBy);

        @JsonProperty(FIELD_UPDATED_AT)
        public abstract Builder updatedAt(ZonedDateTime updatedAt);

        @JsonProperty(FIELD_EXPIRES_AT)
        public abstract Builder expiresAt(@Nullable ZonedDateTime expiresAt);

        public abstract GrantDTO build();
    }
}
