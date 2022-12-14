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
package org.graylog2.rest.resources.system.indices.ranges;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.core.ResourceConfig;
import org.graylog2.Core;
import org.graylog2.indexer.ranges.RebuildIndexRangesJob;
import org.graylog2.rest.RestResource;
import org.graylog2.systemjobs.SystemJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
@Path("/system/indices/ranges")
public class IndexRangesResource extends RestResource {

    private static final Logger LOG = LoggerFactory.getLogger(IndexRangesResource.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Context
    ResourceConfig rc;

    @POST
    @Path("/rebuild")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rebuild() {
        Core core = (Core) rc.getProperty("core");

        SystemJob rebuildJob = new RebuildIndexRangesJob();
        rebuildJob.prepare(core);
        core.getSystemJobManager().submit(rebuildJob);

        return Response.status(Response.Status.ACCEPTED).build();
    }

}
