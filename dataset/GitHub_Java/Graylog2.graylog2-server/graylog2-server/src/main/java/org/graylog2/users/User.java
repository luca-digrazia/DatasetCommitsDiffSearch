/**
 * Copyright 2012 Lennart Koopmann <lennart@socketfeed.com>
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
package org.graylog2.users;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.graylog2.Core;
import org.graylog2.database.Persisted;
import org.graylog2.database.ValidationException;
import org.graylog2.database.validators.FilledStringValidator;
import org.graylog2.database.validators.ListValidator;
import org.graylog2.database.validators.Validator;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class User extends Persisted {

    private static final Logger LOG = LoggerFactory.getLogger(User.class);

    private static final String COLLECTION = "users";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String EMAIL = "email";
    public static final String FULL_NAME = "full_name";
    public static final String PERMISSIONS = "permissions";
    public static final String TIMEZONE = "timezone";
    public static final String EXTERNAL_USER = "external_user";

    public User(Map<String, Object> fields, Core core) {
        super(core, fields);
    }

    protected User(ObjectId id, Map<String, Object> fields, Core core) {
        super(core, id, fields);
    }

    public static User load(String username, Core core) {
        // special case for the locally defined user, we don't store that in MongoDB.
        if (core.getConfiguration().getRootUsername().equals(username)) {
            return new LocalAdminUser(core);
        }

        DBObject query = new BasicDBObject();
        query.put(USERNAME, username);

        List<DBObject> result = query(query, core, COLLECTION);

        if (result == null)     { return null; }
        if (result.size() == 0) { return null; }

        if (result.size() > 1) {
            throw new RuntimeException("There was more than one matching user. This should never happen.");
        }
        final DBObject userObject = result.get(0);

        final Object userId = userObject.get("_id");
        return new User((ObjectId) userId, userObject.toMap(), core);
    }

    public static List<User> loadAll(Core core) {
        List<User> users = Lists.newArrayList();

        DBObject query = new BasicDBObject();
        List<DBObject> result = query(query, core, COLLECTION);

        for (DBObject dbObject : result) {
            users.add(new User((ObjectId) dbObject.get("_id"), dbObject.toMap(), core));
        }
        return users;
    }

    // TODO remove this and use a proper salted digest, this is not secure at all
    @Deprecated
    public static String saltPass(String password, String salt) {
        if (password == null || password.isEmpty()) {
            throw new RuntimeException("No password given.");
        }

        if (salt == null || salt.isEmpty()) {
            throw new RuntimeException("No salt given.");
        }

        return password + salt;
    }

    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    @Override
    public ObjectId getId() {
        return this.id;
    }

    protected Map<String, Validator> getValidations() {
        return new HashMap<String, Validator>() {{
            put(USERNAME, new FilledStringValidator());
            put(PASSWORD, new FilledStringValidator());
            put(EMAIL, new FilledStringValidator());
            put(FULL_NAME, new FilledStringValidator());
            put(PERMISSIONS, new ListValidator());
        }};
    }

    @Override
    protected Map<String, Validator> getEmbeddedValidations(String key) {
        return Maps.newHashMap();
    }

    public String getFullName() {
        return fields.get(FULL_NAME).toString();
    }

    public String getName() {
        return fields.get(USERNAME).toString();
    }

    public void setName(String username) {
        fields.put(USERNAME, username);
    }

    public String getEmail() {
        final Object email = fields.get(EMAIL);
        return email == null ? "" : email.toString();
    }

    public List<String> getPermissions() {
        final Object o = fields.get(PERMISSIONS);
        return (List<String>) o;
    }

    public void setPermissions(List<String> permissions) {
        fields.put(PERMISSIONS, permissions);
    }

    public void setEmail(String email) {
        fields.put(EMAIL, email);
    }

    public void setFullName(String fullname) {
        fields.put(FULL_NAME, fullname);
    }

    public String getHashedPassword() {
        return fields.get(PASSWORD).toString();
    }

    public void setHashedPassword(String hashedPassword) {
        fields.put(PASSWORD, hashedPassword);
    }

    public DateTimeZone getTimeZone() {
        final Object o = fields.get(TIMEZONE);
        try {
            if (o != null) {
                return DateTimeZone.forID(o.toString());
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid timezone {} saved for user {}", o.toString(), getName());
        }
        return null;
    }

    public void setTimeZone(DateTimeZone timeZone) {
        fields.put(TIMEZONE, timeZone.getID());
    }

    public boolean isExternalUser() {
        return Boolean.valueOf(String.valueOf(fields.get(EXTERNAL_USER)));
    }

    public void setExternal(boolean external) {
        fields.put(EXTERNAL_USER, external);
    }

    public static class LocalAdminUser extends User {
        public LocalAdminUser(Core core) {
            super(null, Maps.<String, Object>newHashMap(), core);
        }

        @Override
        public String getFullName() {
            return "Administrator";
        }

        public String getEmail() {
            return "none";
        }

        @Override
        public String getName() {
            return core.getConfiguration().getRootUsername();
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public boolean isExternalUser() {
            return false;
        }

        @Override
        public List<String> getPermissions() {
            return Lists.newArrayList("*");
        }

        @Override
        public DateTimeZone getTimeZone() {
            return null;
        }

        @Override
        public ObjectId save() throws ValidationException {
            throw new IllegalStateException("Cannot modify local root user, this is a bug.");
        }
    }
}
