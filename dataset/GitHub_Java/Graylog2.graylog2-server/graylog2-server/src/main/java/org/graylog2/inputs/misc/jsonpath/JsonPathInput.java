/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
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
package org.graylog2.inputs.misc.jsonpath;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jayway.jsonpath.JsonPath;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.MisfireException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class JsonPathInput extends MessageInput {

    private static final Logger LOG = LoggerFactory.getLogger(JsonPathInput.class);

    public static final String NAME = "JSON path from HTTP API";

    private Configuration config;
    private GraylogServer graylogServer;

    private static final String CK_URL = "target_url";
    private static final String CK_PATH = "path";
    private static final String CK_SOURCE = "source";
    private static final String CK_HEADERS = "headers";

    private JsonPath jsonPath;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
            .setNameFormat("input-" + inputId + "-jsonpath-%d").build());

    private ScheduledFuture<?> scheduledFuture;

    /*
     * TODO:
     *
     *   - make interval configurable
     *   - make it work with *all* releases from asset list
     *
     */

    @Override
    public void configure(Configuration config, GraylogServer graylogServer) throws ConfigurationException {
        this.config = config;
        this.graylogServer = graylogServer;

        if (!checkConfig(config)) {
            throw new ConfigurationException(config.getSource().toString());
        }

        this.jsonPath = JsonPath.compile(config.getString(CK_PATH));
    }

    @Override
    public void launch() throws MisfireException {
        final MessageInput parentInput = this;

        Runnable task = new Runnable() {
            @Override
            public void run() {
                // Fetch JSON.
                String json;
                try {
                    Collector collector = new Collector(
                            config.getString(CK_URL),
                            parseHeaders(config.getString(CK_HEADERS)),
                            inputId
                    );

                    json = collector.getJson();
                } catch(Exception e) {
                    LOG.debug("Could not fetch JSON for JsonPathInput <{}>.", inputId, e);
                    return;
                }

                // Extract desired data from it.
                Selector selector = new Selector(jsonPath);
                Map<String, Object> fields = selector.read(json);

                Message m = new Message(selector.buildShortMessage(fields), config.getString(CK_SOURCE), Tools.getUTCTimestampWithMilliseconds());
                m.addFields(fields);

                // Add to buffer.
                graylogServer.getProcessBuffer().insertCached(m, parentInput);
            }
        };

        scheduledFuture = scheduler.scheduleAtFixedRate(task, 0, 30, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        ConfigurationRequest r = new ConfigurationRequest();

        r.addField(new TextField(
                CK_URL,
                "URI of JSON resource",
                "http://example.org/api",
                "HTTP resource returning JSON on GET",
                ConfigurationField.Optional.NOT_OPTIONAL
        ));

        r.addField(new TextField(
                CK_PATH,
                "JSON path of data to extract",
                "$.store.book[1].number_of_orders",
                "Path to the value you want to extract from the JSON response. Take a look at the documentation for a more detailled explanation.",
                ConfigurationField.Optional.NOT_OPTIONAL
        ));


        r.addField(new TextField(
                CK_SOURCE,
                "Message source",
                "yourapi",
                "What to use as source field of the resulting message.",
                ConfigurationField.Optional.NOT_OPTIONAL
        ));

        r.addField(new TextField(
                CK_HEADERS,
                "Additional HTTP headers",
                "",
                "Add a comma separated list of additional HTTP headers. For example: Accept: application/json, X-Requester: Graylog2",
                ConfigurationField.Optional.OPTIONAL
        ));

        return r;
    }

    @Override
    public boolean isExclusive() {
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String linkToDocs() {
        return "http://support.torch.sh/help/kb/graylog2-server/the-json-path-from-http-api-input";
    }

    @Override
    public Map<String, Object> getAttributes() {
        return config.getSource();
    }

    protected boolean checkConfig(Configuration config) {
        return config.stringIsSet(CK_URL) && config.stringIsSet(CK_PATH) && config.stringIsSet(CK_SOURCE);
    }

    public static Map<String, String> parseHeaders(String headerString) {
        Map<String, String> headers = Maps.newHashMap();

        if (headerString == null || headerString.isEmpty()) {
            return headers;
        }

        headerString = headerString.trim();

        for (String headerPart : headerString.split(",")) {
            headerPart = headerPart.trim();

            String[] parts = headerPart.split(":");
            if (parts == null || parts.length != 2) {
                continue;
            }

            headers.put(parts[0].trim(), parts[1].trim());
        }

        return headers;
    }

}
