/*
 * Copyright 2013-2014 TORCH GmbH
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graylog2.database;

import com.google.common.base.Objects;
import org.bson.types.ObjectId;
import org.graylog2.plugin.database.Persisted;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public abstract class PersistedImpl implements Persisted {
    private static final Logger LOG = LoggerFactory.getLogger(PersistedImpl.class);

    protected final Map<String, Object> fields;
    protected final ObjectId id;

    protected PersistedImpl(Map<String, Object> fields) {
        this(new ObjectId(), fields);
    }

    protected PersistedImpl(ObjectId id, Map<String, Object> fields) {
        this.id = id;
        this.fields = fields;

        // Transform all java.util.Date's to JodaTime because MongoDB gives back java.util.Date's. #lol
        for(Map.Entry<String, Object> field : fields.entrySet()) {
            if (field.getValue() instanceof Date) {
                fields.put(field.getKey(), new DateTime((Date) field.getValue(), DateTimeZone.UTC));
            }
        }
    }

    protected ObjectId getObjectId() {
        return this.id;
    }

    @Override
    public String getId() {
        return getObjectId().toStringMongod();
    }

    @Override
    public Map<String, Object> getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PersistedImpl))
            return false;

        PersistedImpl other = (PersistedImpl) o;
        return Objects.equal(fields, other.fields) && Objects.equal(getObjectId(), other.getObjectId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getObjectId(), fields);
    }
}
