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
package org.graylog2.rest.resources.system;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.jersey.api.core.ResourceConfig;
import org.graylog2.Core;
import org.graylog2.system.activities.SystemMessage;
import org.graylog2.plugin.Tools;
import org.graylog2.rest.resources.RestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
@Path("/system/messages")
public class MessagesResource extends RestResource {

    private static final Logger LOG = LoggerFactory.getLogger(MessagesResource.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Context
    ResourceConfig rc;

    @GET @Timed
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public String all(@QueryParam("page") int page, @QueryParam("pretty") boolean prettyPrint) {
        Core core = (Core) rc.getProperty("core");

        List<Map<String, Object>> messages = Lists.newArrayList();

        for (SystemMessage sm : SystemMessage.all(core, page)) {
            Map<String, Object> message = Maps.newHashMap();
            message.put("caller", sm.getCaller());
            message.put("content", sm.getContent());
            message.put("timestamp", Tools.getISO8601String(sm.getTimestamp()));
            message.put("node_id", sm.getNodeId());

            messages.add(message);
        }

        Map<String, Object> result = Maps.newHashMap();
        result.put("messages", messages);

        return json(result, prettyPrint);
    }

}
