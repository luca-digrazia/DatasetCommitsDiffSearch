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
package org.graylog2.streams;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.graylog2.database.CollectionName;
import org.graylog2.plugin.streams.Output;
import org.mongojack.ObjectId;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.Map;

@AutoValue
@JsonAutoDetect
@CollectionName("outputs")
public abstract class OutputAVImpl implements Output {
    static final String FIELD_ID = "_id";
    static final String FIELD_TITLE = "title";
    static final String FIELD_TYPE = "type";
    static final String FIELD_CONFIGURATION = "configuration";
    static final String FIELD_CREATOR_USER_ID = "creator_user_id";
    static final String FIELD_CREATED_AT = "created_at";
    static final String FIELD_CONTENT_PACK = "content_pack";

    @Override
    @JsonProperty(FIELD_ID)
    @ObjectId
    public abstract String getId();

    @Override
    @JsonProperty(FIELD_TITLE)
    public abstract String getTitle();

    @Override
    @JsonProperty(FIELD_TYPE)
    public abstract String getType();

    @Override
    @JsonProperty(FIELD_CREATOR_USER_ID)
    public abstract String getCreatorUserId();

    @Override
    @JsonProperty(FIELD_CONFIGURATION)
    public abstract Map<String, Object> getConfiguration();

    @Override
    @JsonProperty(FIELD_CREATED_AT)
    public abstract Date getCreatedAt();

    @Override
    @JsonProperty(FIELD_CONTENT_PACK)
    @Nullable
    public abstract String getContentPack();

    @JsonCreator
    public static OutputAVImpl create(@JsonProperty(FIELD_ID) String _id,
                                    @JsonProperty(FIELD_TITLE) String title,
                                    @JsonProperty(FIELD_TYPE) String type,
                                    @JsonProperty(FIELD_CREATOR_USER_ID) String creator_user_id,
                                    @JsonProperty(FIELD_CONFIGURATION) Map<String, Object> configuration,
                                    @JsonProperty(FIELD_CREATED_AT) Date created_at,
                                    @JsonProperty(FIELD_CONTENT_PACK) @Nullable String content_pack) {
        return new AutoValue_OutputAVImpl(_id, title, type, creator_user_id, configuration, created_at, content_pack);

    }
}
