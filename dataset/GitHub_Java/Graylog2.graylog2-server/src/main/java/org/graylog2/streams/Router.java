/**
 * Copyright 2011 Lennart Koopmann <lennart@socketfeed.com>
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

package org.graylog2.streams;

import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.graylog2.Log;
import org.graylog2.messagehandlers.gelf.GELFMessage;
import org.graylog2.streams.matchers.StreamRuleMatcherIF;

/**
 * Router.java: Mar 16, 2011 9:40:24 PM
 *
 * Routes a GELF Message to it's streams.
 *
 * @author: Lennart Koopmann <lennart@socketfeed.com>
 */
public class Router {

    // Hidden.
    private Router() { }

    public static List<ObjectId> route(GELFMessage msg) {
        ArrayList<ObjectId> matches = new ArrayList<ObjectId>();
        ArrayList<Stream> streams = null;
        try {
            streams = Stream.fetchAll();
        } catch (Exception e) {
            Log.emerg("Could not fetch streams: " + e.toString());
        }

        for (Stream stream : streams) {
            boolean missed = false;

            for (StreamRule rule : stream.getStreamRules()) {
                try {
                    StreamRuleMatcherIF matcher = StreamRuleMatcherFactory.build(rule.getRuleType());
                    if (!msg.matchStreamRule(matcher, rule)) {
                        missed = true;
                        break;
                    }
                } catch (InvalidStreamRuleTypeException e) {
                    Log.warn("Invalid stream rule type. Skipping matching for this rule. " + e.toString());
                }
            }

            // All rules were matched.
            if (!missed) {
                matches.add(stream.getId());
            }
        }

        return matches;
    }

}