/**
 * Copyright 2010 Lennart Koopmann <lennart@scopeport.org>
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

/**
 * MongoMapper.java: lennart | Apr 13, 2010 9:13:03 PM
 */

package graylog2.database;

import com.mongodb.Mongo;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.MongoException;

import graylog2.Log;

import java.util.Iterator;
import java.util.List;

import org.productivity.java.syslog4j.server.SyslogServerEventIF;

public class MongoMapper {
    // TODO: make configurable
    public static final int MAX_MESSAGE_SIZE = 500000000;
    public static final int STANDARD_PORT = 27017;

    private Mongo m = null;
    private DB db = null;
    
    private String username = null;
    private String password = null;
    private String hostname = null;
    private String database = null;
    private int    port = 27017;

    public MongoMapper(String username, String password, String hostname, String database, int port) {
        this.username = username;
        this.password = password;
        this.hostname = hostname;
        this.database = database;
        if (port == 0) {
            this.port = MongoMapper.STANDARD_PORT;
        } else {
            this.port = port;
        }
    }

    private void connect() throws Exception {
        try {
            this.m = new Mongo(this.hostname, this.port);
            this.db = m.getDB(this.database);

            // Try to authenticate.
            if(!db.authenticate(this.username, this.password.toCharArray())) {
                throw new Exception("Could not authenticate to database '" + this.database + "' with user '" + this.username + "'.");
            }
        } catch (MongoException.Network e) {
            throw new Exception("Could not connect to Mongo DB.");
        }
    }

    public void dropCollection(String databaseName) throws Exception {
        this.connect();
        m.dropDatabase(databaseName);
    }

    public DBCollection getMessagesColl() {
        DBCollection coll = null;

        // Create a capped collection if the collection does not yet exist.
        if(db.getCollectionNames().contains("messages")) {
            coll = db.getCollection("messages");
        } else {
            coll = db.createCollection("messages", BasicDBObjectBuilder.start().add("capped", true).add("size", MongoMapper.MAX_MESSAGE_SIZE).get());
        }

        coll.ensureIndex(new BasicDBObject("created_at", 1));
        coll.ensureIndex(new BasicDBObject("host", 1));
        coll.ensureIndex(new BasicDBObject("facility", 1));
        coll.ensureIndex(new BasicDBObject("level", 1));

        return coll;
    }

    public void insert(SyslogServerEventIF event) throws Exception {
        this.connect();

        DBCollection coll = this.getMessagesColl();

        BasicDBObject dbObj = new BasicDBObject();
        dbObj.put("message", event.getMessage());
        dbObj.put("host", event.getHost());
        dbObj.put("facility", event.getFacility());
        dbObj.put("level", event.getLevel());
        dbObj.put("created_at", (int) (System.currentTimeMillis()/1000));

        coll.insert(dbObj);
    }
    
    public void insertSystemStatisticValue(String key, long value) throws Exception {
        this.connect();
        
        DBCollection coll = null;

        // Create a capped collection if the collection does not yet exist.
        if(db.getCollectionNames().contains("systemstatistics")) {
            coll = db.getCollection("systemstatistics");
        } else {
            coll = db.createCollection("systemstatistics", BasicDBObjectBuilder.start().add("capped", true).add("size", 5242880).get());
        }
        
        BasicDBObject dbObj = new BasicDBObject();
        dbObj.put(key, value);

        coll.insert(dbObj);
    }

    public void distinctHosts() throws Exception {
        this.connect();

        // Fetch all hosts.
        DBCollection messages = this.getMessagesColl();
        List hosts = messages.distinct("host");

        DBCollection coll = null;

        // Create a capped collection if the collection does not yet exist.
        if(db.getCollectionNames().contains("hosts")) {
            coll = db.getCollection("hosts");
        } else {
            coll = db.createCollection("hosts", new BasicDBObject());
        }

        coll.ensureIndex(new BasicDBObject("name", 1));

        // Truncate host collection.
        coll.remove(new BasicDBObject());
        
        // Go trough every host and insert.
        for (Iterator<String> i = hosts.iterator(); i.hasNext( ); ) {
            try {
                String host = i.next();

                // Get message count of this host.
                BasicDBObject countQuery = new BasicDBObject();
                countQuery.put("host", host);
                long messageCount = messages.getCount(countQuery);

                // Build document.
                BasicDBObject doc = new BasicDBObject();
                doc.put("host", host);
                doc.put("message_count", messageCount);

                // Store document.
                coll.insert(doc);
            } catch (Exception e) {
                Log.crit("Could not insert distinct host: " + e.toString());
                continue;
            }
        }
    }

}
