/**
 * Copyright 2010 Lennart Koopmann <lennart@socketfeed.com>
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

import com.mongodb.Mongo;
import com.mongodb.DB;
import com.mongodb.MongoException;

/**
 * MongoConnection.java: Jun 6, 2010 1:36:19 PM
 *
 * MongoDB connection singleton
 *
 * @author: Lennart Koopmann <lennart@socketfeed.com>
 */
public class MongoConnection {
    private static MongoConnection INSTANCE;

    private static Mongo m = null;
    private static DB db = null;

    private MongoConnection() {}

    /**
     * Get the connection instance
     * @return MongoConnection instance
     */
    public synchronized static MongoConnection getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MongoConnection();
        }
        return INSTANCE;
    }

    /**
     * Connect the instance.
     *
     * @param username MongoDB user
     * @param password MongoDB password
     * @param hostname MongoDB host
     * @param database MongoDB database
     * @param port MongoDB port
     * @param useAuth Use authentication?
     * @throws Exception
     */
    public void connect(String username, String password, String hostname, String database, int port, String useAuth) throws Exception {
        try {
            MongoConnection.m = new Mongo(hostname, port);
            MongoConnection.db = m.getDB(database);

            // Try to authenticate if configured.
            if (useAuth.equals("true")) {
                if(!db.authenticate(username, password.toCharArray())) {
                    throw new Exception("Could not authenticate to database '" + database + "' with user '" + username + "'.");
                }
            }
        } catch (MongoException.Network e) {
            throw new Exception("Could not connect to Mongo DB. (" + e.toString() + ")");
        }
    }

    /**
     * Returns the raw connection.
     * @return connection
     */
    public Mongo getConnection() {
        return MongoConnection.m;
    }

    /**
     * Returns the raw database object.
     * @return database
     */
    public DB getDatabase() {
        return MongoConnection.db;
    }
}
