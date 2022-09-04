/*
 * Copyright 2012-2014 TORCH GmbH
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

package org.graylog2.alerts;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import org.bson.types.ObjectId;
import org.graylog2.alerts.types.FieldValueAlertCondition;
import org.graylog2.alerts.types.MessageCountAlertCondition;
import org.graylog2.database.MongoConnection;
import org.graylog2.database.PersistedServiceImpl;
import org.graylog2.indexer.Indexer;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.rest.resources.streams.alerts.requests.CreateConditionRequest;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertServiceImpl extends PersistedServiceImpl implements AlertService {
    private static final Logger LOG = LoggerFactory.getLogger(AlertServiceImpl.class);

    @Inject
    public AlertServiceImpl(MongoConnection mongoConnection) {
        super(mongoConnection);
    }

    @Override
    public Alert factory(AlertCondition.CheckResult checkResult) {
        Map<String, Object> fields = Maps.newHashMap();

        if (!checkResult.isTriggered()) {
            throw new RuntimeException("Tried to create alert from not triggered alert condition result.");
        }

        fields.put("triggered_at", checkResult.getTriggeredAt());
        fields.put("condition_id", checkResult.getTriggeredCondition().getId());
        fields.put("stream_id", checkResult.getTriggeredCondition().getStream().getId());
        fields.put("description", checkResult.getResultDescription());
        fields.put("condition_parameters", checkResult.getTriggeredCondition().getParameters());

        return new AlertImpl(fields);
    }

    @Override
    public List<Alert> loadRecentOfStream(String streamId, DateTime since) {
        QueryBuilder qb = QueryBuilder.start("stream_id").is(streamId);

        if (since != null) {
            qb.and("triggered_at").greaterThanEquals(since.toDate());
        }

        BasicDBObject sort = new BasicDBObject("triggered_at", -1);

        final List<DBObject> alertObjects = query(AlertImpl.class,
                qb.get(),
                sort,
                AlertImpl.MAX_LIST_COUNT,
                0
        );

        List<Alert> alerts = Lists.newArrayList();

        for (DBObject alertObj : alertObjects) {
            alerts.add(new AlertImpl(new ObjectId(alertObj.get("_id").toString()), alertObj.toMap()));
        }

        return alerts;
    }

    @Override
    public int triggeredSecondsAgo(String streamId, String conditionId) {
        DBObject query = QueryBuilder.start("stream_id").is(streamId)
                .and("condition_id").is(conditionId).get();
        BasicDBObject sort = new BasicDBObject("triggered_at", -1);

        DBObject alert = findOne(AlertImpl.class, query, sort);

        if (alert == null) {
            return -1;
        }

        DateTime triggeredAt = new DateTime(alert.get("triggered_at"));

        return Seconds.secondsBetween(triggeredAt, DateTime.now()).getSeconds();
    }

    @Override
    public long totalCount() {
        return collection(AlertImpl.class).count();
    }

    public AlertCondition fromPersisted(Map<String, Object> fields, Stream stream) throws AlertCondition.NoSuchAlertConditionTypeException {
        AlertCondition.Type type;
        try {
            type = AlertCondition.Type.valueOf(((String) fields.get("type")).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AlertCondition.NoSuchAlertConditionTypeException("No such alert condition type: [" + fields.get("type") + "]");
        }

        switch(type) {
            case MESSAGE_COUNT:
                return new MessageCountAlertCondition(
                        stream,
                        (String) fields.get("id"),
                        DateTime.parse((String) fields.get("created_at")),
                        (String) fields.get("creator_user_id"),
                        (Map<String, Object>) fields.get("parameters")
                );
            case FIELD_VALUE:
                return new FieldValueAlertCondition(
                        stream,
                        (String) fields.get("id"),
                        DateTime.parse((String) fields.get("created_at")),
                        (String) fields.get("creator_user_id"),
                        (Map<String, Object>) fields.get("parameters")
                );
        }

        throw new AlertCondition.NoSuchAlertConditionTypeException("Unhandled alert condition type: " + type);
    }

    public AlertCondition fromRequest(CreateConditionRequest ccr, Stream stream) throws AlertCondition.NoSuchAlertConditionTypeException {
        AlertCondition.Type type;
        try {
            Integer Type;
            type = AlertCondition.Type.valueOf(ccr.type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AlertCondition.NoSuchAlertConditionTypeException("No such alert condition type: [" + ccr.type + "]");
        }

        Map<String, Object> parameters = ccr.parameters;

        switch(type) {
            case MESSAGE_COUNT:
                return new MessageCountAlertCondition(
                        stream,
                        null,
                        Tools.iso8601(),
                        ccr.creatorUserId,
                        parameters
                );
            case FIELD_VALUE:
                return new FieldValueAlertCondition(
                        stream,
                        null,
                        Tools.iso8601(),
                        ccr.creatorUserId,
                        parameters
                );
        }

        throw new AlertCondition.NoSuchAlertConditionTypeException("Unhandled alert condition type: " + type);
    }

    public boolean inGracePeriod(AlertCondition alertCondition) {
        int lastAlertSecondsAgo = triggeredSecondsAgo(alertCondition.getStream().getId(), alertCondition.getId());

        if (lastAlertSecondsAgo == -1 || alertCondition.getGrace() == 0) {
            return false;
        }

        return lastAlertSecondsAgo < alertCondition.getGrace()*60;
    }

    public AlertCondition.CheckResult triggeredNoGrace(AlertCondition alertCondition, Indexer indexer) {
        LOG.debug("Checking alert condition [{}] and not accounting grace time.", this);
        return alertCondition.runCheck(indexer);
    }

    public AlertCondition.CheckResult triggered(AlertCondition alertCondition, Indexer indexer) {
        LOG.debug("Checking alert condition [{}]", this);

        if(inGracePeriod(alertCondition)) {
            LOG.debug("Alert condition [{}] is in grace period. Not triggered.", this);
            return new AlertCondition.CheckResult(false);
        }

        return alertCondition.runCheck(indexer);
    }

    public Map<String, Object> asMap(final AlertCondition alertCondition) {
        return new HashMap<String, Object>() {{
            put("id", alertCondition.getId());
            put("type", alertCondition.getType().toString().toLowerCase());
            put("creator_user_id", alertCondition.creatorUserId);
            put("created_at", Tools.getISO8601String(alertCondition.createdAt));
            put("parameters", alertCondition.getParameters());
            put("in_grace", inGracePeriod(alertCondition));
        }};
    }
}