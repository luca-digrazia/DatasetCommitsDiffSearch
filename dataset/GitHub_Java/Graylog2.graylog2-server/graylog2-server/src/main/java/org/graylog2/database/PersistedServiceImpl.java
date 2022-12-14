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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.graylog2.plugin.database.EmbeddedPersistable;
import org.graylog2.plugin.database.Persisted;
import org.graylog2.plugin.database.validators.Validator;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class PersistedServiceImpl implements PersistedService {
    private static final Logger LOG = LoggerFactory.getLogger(PersistedServiceImpl.class);
    public final MongoConnection mongoConnection;

    protected PersistedServiceImpl(MongoConnection mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    protected DBObject get(ObjectId id, String collectionName) {
        return collection(collectionName).findOne(new BasicDBObject("_id", id));
    }

    protected <T extends Persisted> DBObject get(Class<T> modelClass, ObjectId id) {
        return collection(modelClass).findOne(new BasicDBObject("_id", id));
    }

    protected <T extends Persisted> DBObject get(Class<T> modelClass, String id) {
        return get(modelClass, new ObjectId(id));
    }

    protected List<DBObject> query(DBObject query, String collectionName) {
        return query(query, collection(collectionName));
    }

    protected List<DBObject> query(DBObject query, DBCollection collection) {
        return cursorToList(collection.find(query));
    }

    protected <T extends Persisted> List<DBObject> query(Class<T> modelClass, DBObject query) {
        return query(query, collection(modelClass));
    }

    protected <T extends Persisted> List<DBObject> query(Class<T> modelClass, DBObject query, DBObject sort) {
        return cursorToList(collection(modelClass).find(query).sort(sort));
    }

    protected <T extends Persisted> List<DBObject> query(Class<T> modelClass, DBObject query, DBObject sort, int limit, int offset) {
        return cursorToList(
                collection(modelClass)
                        .find(query)
                        .sort(sort)
                        .limit(limit)
                        .skip(offset)
        );
    }

    protected long count(DBObject query, String collectionName) {
        return collection(collectionName).count(query);
    }

    protected <T extends Persisted> long count(Class<T> modelClass, DBObject query) {
        return collection(modelClass).count(query);
    }

    private DBCollection collection(String collectionName) {
        return mongoConnection.getDatabase().getCollection(collectionName);
    }

    protected <T extends Persisted> DBCollection collection(Class<T> modelClass) {
        CollectionName collectionNameAnnotation = modelClass.getAnnotation(CollectionName.class);
        final String collectionName = (collectionNameAnnotation == null ? null : collectionNameAnnotation.value());

        if (collectionName == null)
            return null;
        else
            return collection(collectionName);
    }

    protected <T extends Persisted> DBCollection collection(T model) {
        return collection(model.getClass());
    }

    protected List<DBObject> cursorToList(DBCursor cursor) {
        List<DBObject> results = Lists.newArrayList();

        if (cursor == null) {
            return results;
        }

        try {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        } finally {
            cursor.close();
        }

        return results;
    }

    protected <T extends Persisted> DBObject findOne(Class<T> model, DBObject query) {
        return collection(model).findOne(query);
    }

    protected <T extends Persisted> DBObject findOne(Class<T> model, DBObject query, DBObject sort) {
        return collection(model).findOne(query, new BasicDBObject(), sort);
    }

    protected DBObject findOne(DBObject query, String collectionName) {
        return collection(collectionName).findOne(query);
    }

    protected DBObject findOne(DBObject query, DBObject sort, String collectioName) {
        return collection(collectioName).findOne(query, new BasicDBObject(), sort);
    }

    protected long totalCount(String collectionName) {
        return collection(collectionName).count();
    }

    protected <T extends Persisted> long totalCount(Class<T> modelClass) {
        return collection(modelClass).count();
    }

    @Override
    public <T extends Persisted> int destroy(T model) {
        return collection(model).remove(new BasicDBObject("_id", new ObjectId(model.getId()))).getN();
    }

    @Override
    public <T extends Persisted> int destroyAll(Class<T> modelClass) {
        return collection(modelClass).remove(new BasicDBObject()).getN();
    }

    protected int destroyAll(String collectionName) {
        return collection(collectionName).remove(new BasicDBObject()).getN();
    }

    protected int destroy(DBObject query, String collectionName) {
        return collection(collectionName).remove(query).getN();
    }

    protected <T extends Persisted> int destroyAll(Class<T> modelClass, DBObject query) {
        return collection(modelClass).remove(query).getN();
    }

    @Override
    public <T extends Persisted> String save(T model) throws ValidationException {
        if (!validate(model, model.getFields())) {
            throw new ValidationException();
        }

        BasicDBObject doc = new BasicDBObject(model.getFields());
        doc.put("_id", new ObjectId(model.getId())); // ID was created in constructor or taken from original doc already.

        // Do field transformations
        fieldTransformations(doc);

		/*
         * We are running an upsert. This means that the existing
		 * document will be updated if the ID already exists and
		 * a new document will be created if it doesn't.
		 */
        BasicDBObject q = new BasicDBObject("_id", new ObjectId(model.getId()));
        collection(model).update(q, doc, true, false);

        return model.getId();
    }

    @Override
    public <T extends Persisted> String saveWithoutValidation(T model) {
        try {
            return save(model);
        } catch (ValidationException e) { /* */ }

        return null;
    }

    @Override
    public <T extends Persisted> boolean validate(T model, Map<String, Object> fields) {
        return validate(model.getValidations(), fields);
    }

    @Override
    public boolean validate(Map<String, Validator> validators, Map<String, Object> fields) {
        if (validators == null || validators.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Validator> validation : validators.entrySet()) {
            Validator v = validation.getValue();
            String field = validation.getKey();

            try {
                if (!v.validate(fields.get(field))) {
                    LOG.info("Validation failure: [{}] on field [{}]", v.getClass().getCanonicalName(), field);
                    return false;
                }
            } catch (Exception e) {
                LOG.error("Error while trying to validate <{}>. Marking as invalid.", field, e);
                return false;
            }
        }

        return true;
    }

    @Override
    public <T extends Persisted> boolean validate(T model) {
        return validate(model, model.getFields());
    }

    protected <T extends Persisted> void embed(T model, String key, EmbeddedPersistable o) throws ValidationException {
        if (!validate(model.getEmbeddedValidations(key), o.getPersistedFields())) {
            throw new ValidationException();
        }

        Map<String, Object> fields = Maps.newHashMap(o.getPersistedFields());
        fieldTransformations(fields);

        BasicDBObject dbo = new BasicDBObject(fields);
        collection(model).update(new BasicDBObject("_id", model.getId()), new BasicDBObject("$push", new BasicDBObject(key, dbo)));
    }

    protected <T extends Persisted> void removeEmbedded(T model, String key, String searchId) {
        BasicDBObject aryQry = new BasicDBObject("id", searchId);
        BasicDBObject qry = new BasicDBObject("_id", new ObjectId(model.getId()));
        BasicDBObject update = new BasicDBObject("$pull", new BasicDBObject(key, aryQry));

        // http://docs.mongodb.org/manual/reference/operator/pull/

        collection(model).update(qry, update);
    }

    protected <T extends Persisted> void removeEmbedded(T model, String arrayKey, String key, String searchId) {
        BasicDBObject aryQry = new BasicDBObject(arrayKey, searchId);
        BasicDBObject qry = new BasicDBObject("_id", model.getId());
        BasicDBObject update = new BasicDBObject("$pull", new BasicDBObject(key, aryQry));

        // http://docs.mongodb.org/manual/reference/operator/pull/

        collection(model).update(qry, update);
    }

    private void fieldTransformations(Map<String, Object> doc) {
        for (Map.Entry<String, Object> x : doc.entrySet()) {

            // Work on embedded Maps, too.
            if (x.getValue() instanceof Map) {
                fieldTransformations((Map<String, Object>) x.getValue());
                continue;
            }

            // JodaTime DateTime is not accepted by MongoDB. Convert to java.util.Date...
            if (x.getValue() instanceof DateTime) {
                doc.put(x.getKey(), ((DateTime) x.getValue()).toDate());
            }

        }
    }

}