/**
 * Copyright 2013 Lennart Koopmann <lennart@socketfeed.com>
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
 *
 */

package org.graylog2.database;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.graylog2.Core;

import com.beust.jcommander.internal.Lists;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.graylog2.database.validators.Validator;
import org.graylog2.plugin.database.EmbeddedPersistable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public abstract class Persisted {

    private static final Logger LOG = LoggerFactory.getLogger(Persisted.class);

    protected final Map<String, Object> fields;
    protected final ObjectId id;
    
    protected final Core core;

    protected Persisted(Core core, Map<String, Object> fields) {
        this(core, new ObjectId(), fields);
    }

    protected Persisted(Core core, ObjectId id, Map<String, Object> fields) {
        this.id = id;
        this.fields = fields;

        this.core = core;

        // Transform all java.util.Date's to JodaTime because MongoDB gives back java.util.Date's. #lol
        for(Map.Entry<String, Object> field : fields.entrySet()) {
            if (field.getValue() instanceof Date) {
                fields.put(field.getKey(), new DateTime((Date) field.getValue(), DateTimeZone.UTC));
            }
        }
    }

    protected static DBObject get(ObjectId id, Core core, String collectionName) {
		return collection(core, collectionName).findOne(new BasicDBObject("_id", id));
	}

    protected List<DBObject> query(DBObject query, String collectionName) {
        return cursorToList(collection(core, collectionName).find(query));
    }

    protected static List<DBObject> query(DBObject query, Core core, String collectionName) {
        return cursorToList(collection(core, collectionName).find(query));
    }

    protected static List<DBObject> query(DBObject query, DBObject sort, Core core, String collectionName) {
        return cursorToList(collection(core, collectionName).find(query).sort(sort));
    }

    protected static List<DBObject> query(DBObject query, DBObject sort, int limit, int offset, Core core, String collectionName) {
        return cursorToList(
                collection(core, collectionName)
                        .find(query)
                        .sort(sort)
                        .limit(limit)
                        .skip(offset));
    }

    private static List<DBObject> cursorToList(DBCursor cursor) {
        List<DBObject> results = Lists.newArrayList();

        if (cursor == null) {
            return results;
        }

        try {
            while(cursor.hasNext()) {
                results.add(cursor.next());
            }
        } finally {
            cursor.close();
        }

        return results;
    }

    protected static DBObject findOne(DBObject query, Core core, String collectionName) {
        return collection(core, collectionName).findOne(query);
    }

    public static long totalCount(Core core, String collectionName) {
        return collection(core, collectionName).count();
    }

	public void destroy() {
		collection().remove(new BasicDBObject("_id", id));
	}

    public static void destroyAll(Core core, String collectionName) {
        collection(core, collectionName).remove(new BasicDBObject());
    }

    public static void destroy(DBObject query, Core core, String collectionName) {
        collection(core, collectionName).remove(query);
    }

	public ObjectId save() throws ValidationException {
        if(!validate(fields)) {
            throw new ValidationException();
        }

		BasicDBObject doc = new BasicDBObject(fields);
		doc.put("_id", id); // ID was created in constructor or taken from original doc already.

        // Do field transformations
        for (Map.Entry<String, Object> x : doc.entrySet()) {

            // JodaTime DateTime is not accepted by MongoDB. Convert to java.util.Date...
            if (x.getValue() instanceof org.joda.time.DateTime) {
                doc.put(x.getKey(), ((DateTime) x.getValue()).toDate());
            }
        }

		/*
		 * We are running an upsert. This means that the existing
		 * document will be updated if the ID already exists and
		 * a new document will be created if it doesn't.
		 */
		BasicDBObject q = new BasicDBObject("_id", id);
		collection().update(q, doc, true, false);
		
		return id;
	}

    public ObjectId saveWithoutValidation() {
        try {
            return save();
        } catch (ValidationException e) { /* */ }

        return null;
    }
	
	protected DBCollection collection() {
		return collection(this.core, getCollectionName());
	}

	protected static DBCollection collection(Core core, String collectionName) {
		return core.getMongoConnection().getDatabase().getCollection(collectionName);
	}

    public boolean validate(Map<String, Object> fields) {
        return validate(getValidations(), fields);
    }

    public boolean validate(Map<String, Validator> validators, Map<String, Object> fields) {
        if (validators == null || validators.isEmpty()) {
            return true;
        }

        for(Map.Entry<String, Validator> validation : validators.entrySet()) {
            Validator v = validation.getValue();
            String field = validation.getKey();

            try {
                if (!v.validate(fields.get(field))) {
                    LOG.info("Validation failure: [{}] on field [{}]", v.getClass().getCanonicalName(), field);
                    return false;
                }
            } catch(Exception e) {
                LOG.error("Error while trying to validate <{}>. Marking as invalid.", field, e);
                return false;
            }
        }

        return true;
    }

    public void embed(String key, EmbeddedPersistable o) throws ValidationException {
        if (!validate(getEmbeddedValidations(key), o.getPersistedFields())) {
            throw new ValidationException();
        }

        BasicDBObject dbo = new BasicDBObject(o.getPersistedFields());
        collection().update(new BasicDBObject("_id", id), new BasicDBObject("$push", new BasicDBObject(key, dbo)));
    }

    public void removeEmbedded(String key, String searchId) {
        BasicDBObject aryQry = new BasicDBObject("id", searchId);
        BasicDBObject qry = new BasicDBObject("_id", id);
        BasicDBObject update = new BasicDBObject("$pull", new BasicDBObject(key, aryQry));

        // http://docs.mongodb.org/manual/reference/operator/pull/

        collection().update(qry, update);
    }

    public void removeEmbedded(String arrayKey, String key, String searchId) {
        BasicDBObject aryQry = new BasicDBObject(arrayKey, searchId);
        BasicDBObject qry = new BasicDBObject("_id", id);
        BasicDBObject update = new BasicDBObject("$pull", new BasicDBObject(key, aryQry));

        // http://docs.mongodb.org/manual/reference/operator/pull/

        collection().update(qry, update);
    }

    public ObjectId getId() {
        return this.id;
    }

    public abstract String getCollectionName();
    protected abstract Map<String, Validator> getValidations();
    protected abstract Map<String, Validator> getEmbeddedValidations(String key);
}
