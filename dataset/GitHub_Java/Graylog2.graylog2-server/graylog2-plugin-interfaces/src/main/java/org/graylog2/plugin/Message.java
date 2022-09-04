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

package org.graylog2.plugin;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.streams.Stream;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.joda.time.DateTimeZone.UTC;


/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class Message {

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    private Map<String, Object> fields = Maps.newHashMap();
    private List<Stream> streams = Lists.newArrayList();

    private MessageInput sourceInput;

    private static final Pattern VALID_KEY_CHARS = Pattern.compile("^[\\w\\.\\-]*$");

    // Used for drools to filter out messages.
    private boolean filterOut = false;

    public static final ImmutableSet<String> RESERVED_FIELDS = ImmutableSet.of(
        // ElasticSearch fields.
        "_id",
        "_ttl",
        "_source",
        "_all",
        "_index",
        "_type",
        "_score",
        
        // Our reserved fields.
        "message",
        "source",
        "timestamp",
        "gl2_source_node",
        "gl2_source_input",
        "gl2_source_radio",
        "gl2_source_radio_input"
    );

    public static final ImmutableSet<String> RESERVED_SETTABLE_FIELDS = ImmutableSet.of(
            "message",
            "source",
            "timestamp",
            "gl2_source_node",
            "gl2_source_input",
            "gl2_source_radio",
            "gl2_source_radio_input"
    );

    private static final ImmutableSet<String> REQUIRED_FIELDS = ImmutableSet.of(
            "message", "source", "_id"
    );

    public static final Function<Message, String> ID_FUNCTION = new MessageIdFunction();

    private InetAddress inetAddress;

    public Message(String message, String source, DateTime timestamp) {
        // Adding the fields directly because they would not be accepted as a reserved fields.
        fields.put("_id", new com.eaio.uuid.UUID().toString());
        fields.put("message", message);
        fields.put("source", source);
        fields.put("timestamp", timestamp);

        streams = Lists.newArrayList();
    }

    public Message(Map<String, Object> fields) {
        this.fields.putAll(fields);
    }

    public boolean isComplete() {
        for (String key : REQUIRED_FIELDS) {
            if (getField(key) == null || ((String) getField(key)).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public String getValidationErrors() {
        StringBuilder sb = new StringBuilder();

        for (String key : REQUIRED_FIELDS) {
            if (getField(key) == null) {
                sb.append(key).append(" is missing, ");
            } else if (((String)getField(key)).isEmpty()) {
                sb.append(key).append(" is empty, ");
            }
        }
        return sb.toString();
    }

    public String getId() {
        return (String) getField("_id");
    }

    public Map<String, Object> toElasticSearchObject() {
        Map<String, Object> obj = Maps.newHashMap();

        // Standard fields.
        obj.put("message", getMessage());
        obj.put("source", this.getSource());

        // Add fields.
        obj.putAll(getFields());

        if (getField("timestamp") instanceof DateTime) {
            // Timestamp
            obj.put("timestamp", Tools.buildElasticSearchTimeFormat(
                    ((DateTime) getField("timestamp")).withZone(UTC)));
        }

        // Manually converting stream ID to string - caused strange problems without it.
        if (getStreams().size() > 0) {
            List<String> streamIds = Lists.newArrayList();
            for (Stream stream : this.getStreams()) {
                streamIds.add(stream.getId());
            }
            obj.put("streams", streamIds);
        } else {
            obj.put("streams", Collections.EMPTY_LIST);
        }

        return obj;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("source: ").append(getField("source")).append(" | ");

        String message = getField("message").toString().replaceAll("\\n", "").replaceAll("\\t", "");
        sb.append("message: ");

        if (message.length() > 225) {
            message = message.substring(0, 225);
            sb.append(message).append(" (...)");
        } else {
            sb.append(message);
        }

        Map<String, Object> filteredFields = Maps.newHashMap(fields);
        filteredFields.remove("source");
        filteredFields.remove("message");

        for (Map.Entry<String, Object> entry : filteredFields.entrySet())
            sb.append(" | ").append(entry.getKey()).append(": ").append(entry.getValue());

        return sb.toString();
    }

    public String getMessage() {
        return (String) getField("message");
    }

    public String getSource() {
        return (String) getField("source");
    }

    public void addField(String key, Object value) {
        if (value == null) {
            return;
        }

        if (String.class.equals(value.getClass())) {
            value = ((String) value).trim();

            if (((String) value).isEmpty()) {
                return;
            }
        }

        // Don't accept protected keys. (some are allowed though lol)
        if (RESERVED_FIELDS.contains(key) && !RESERVED_SETTABLE_FIELDS.contains(key)) {
            return;
        }

        if(!validKey(key)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignoring invalid key {} for message {}", key, getId());
            }
            return;
        }

        this.fields.put(key.trim(), value);
    }

    public static boolean validKey(String key) {
        return VALID_KEY_CHARS.matcher(key).matches();
    }

    public void addFields(Map<String, Object> fields) {
        if(fields == null) {
            return;
        }

        for (Map.Entry<String, Object> field : fields.entrySet()) {
            addField(field.getKey(), field.getValue());
        }
    }

    public void addStringFields(Map<String, String> fields) {
        if(fields == null) {
            return;
        }

        for (Map.Entry<String, String> field : fields.entrySet()) {
            addField(field.getKey(), field.getValue());
        }
    }

    public void addLongFields(Map<String, Long> fields) {
        if(fields == null) {
            return;
        }

        for (Map.Entry<String, Long> field : fields.entrySet()) {
            addField(field.getKey(), field.getValue());
        }
    }

    public void addDoubleFields(Map<String, Double> fields) {
        if(fields == null) {
            return;
        }

        for (Map.Entry<String, Double> field : fields.entrySet()) {
            addField(field.getKey(), field.getValue());
        }
    }

    public void removeField(String key) {
        if (!RESERVED_FIELDS.contains(key)) {
            this.fields.remove(key);
        }
    }

    public <T> T getFieldAs(Class<T> T, String key) throws ClassCastException{
        Object rawField = getField(key);

        return T.cast(rawField);
    }

    public Object getField(String key) {
        return fields.get(key);
    }

    public Map<String, Object> getFields() {
        return this.fields;
    }

    public void setStreams(List<Stream> streams) {
        this.streams = streams;
    }

    public List<Stream> getStreams() {
        return this.streams;
    }

    public List<String> getStreamIds() {
        List<String> result = new ArrayList<String>();
        try {
            result.addAll(getFieldAs(result.getClass(), "streams"));
        } catch (ClassCastException e) {
        }

        return result;
    }

    public void setFilterOut(boolean set) {
        this.filterOut = set;
    }

    public boolean getFilterOut() {
        return this.filterOut;
    }

    public MessageInput getSourceInput() {
        return sourceInput;
    }

    public void setSourceInput(MessageInput input) {
        this.sourceInput = input;
    }

    // drools seems to need the "get" prefix
    public boolean getIsSourceInetAddress() {
        return inetAddress != null;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public static class MessageIdFunction implements Function<Message, String> {
        @Override
        public String apply(Message input) {
            return input.getId();
        }
    }
}
