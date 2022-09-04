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
package org.graylog2.rest.resources.system;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.graylog2.indexer.Deflector;
import org.graylog2.indexer.ranges.IndexRange;
import org.graylog2.indexer.ranges.IndexRangeService;
import org.graylog2.indexer.ranges.RebuildIndexRangesJob;
import org.graylog2.plugin.Tools;
import org.graylog2.rest.documentation.annotations.Api;
import org.graylog2.rest.documentation.annotations.ApiOperation;
import org.graylog2.rest.documentation.annotations.ApiResponse;
import org.graylog2.rest.documentation.annotations.ApiResponses;
import org.graylog2.rest.resources.RestResource;
import org.graylog2.security.RestPermissions;
import org.graylog2.system.jobs.SystemJob;
import org.graylog2.system.jobs.SystemJobConcurrencyException;
import org.graylog2.system.jobs.SystemJobManager;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
@RequiresAuthentication
@Api(value = "System/IndexRanges", description = "Index timeranges")
@Path("/system/indices/ranges")
public class IndexRangesResource extends RestResource {

    private static final Logger LOG = LoggerFactory.getLogger(IndexRangesResource.class);

    private final IndexRangeService indexRangeService;
    private final RebuildIndexRangesJob.Factory rebuildIndexRangesJobFactory;
    private final Deflector deflector;
    private final SystemJobManager systemJobManager;

    @Inject
    public IndexRangesResource(IndexRangeService indexRangeService,
                               RebuildIndexRangesJob.Factory rebuildIndexRangesJobFactory,
                               Deflector deflector,
                               SystemJobManager systemJobManager) {
        this.indexRangeService = indexRangeService;
        this.rebuildIndexRangesJobFactory = rebuildIndexRangesJobFactory;
        this.deflector = deflector;
        this.systemJobManager = systemJobManager;
    }

    @GET @Timed
    @ApiOperation(value = "Get a list of all index ranges")
    @Produces(MediaType.APPLICATION_JSON)
    public String list() {
        Map<String, Object> result = Maps.newHashMap();
        List<Map<String, Object>> ranges = Lists.newArrayList();

        for (IndexRange range : indexRangeService.getFrom(0)) {
            if (!isPermitted(RestPermissions.INDEXRANGES_READ, range.getIndexName())) {
                continue;
            }
            Map<String, Object> rangeInfo = Maps.newHashMap();

            // Calculated at and the calculation time in ms are not always set, depending on how/why the entry was created.
            DateTime calculatedAt = range.getCalculatedAt();
            if (calculatedAt != null) {
                rangeInfo.put("calculated_at", Tools.getISO8601String(calculatedAt));
            }

            int calculationTookMs = range.getCalculationTookMs();
            if (calculationTookMs >= 0) {
                rangeInfo.put("calculation_took_ms", calculationTookMs);
            }

            rangeInfo.put("starts", Tools.getISO8601String(range.getStart()));
            rangeInfo.put("index", range.getIndexName());

            ranges.add(rangeInfo);
        }

        result.put("ranges", ranges);
        result.put("total", ranges.size());

        return json(result);
    }

    @POST @Timed
    @Path("/rebuild")
    @RequiresPermissions(RestPermissions.INDEXRANGES_REBUILD)
    @ApiOperation(value = "Rebuild/sync index range information.",
                  notes = "This triggers a systemjob that scans every index and stores meta information " +
                          "about what indices contain messages in what timeranges. It atomically overwrites " +
                          "already existing meta information.")
    @ApiResponses(value = {
            @ApiResponse(code = 202, message = "Rebuild/sync systemjob triggered.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response rebuild() {
        SystemJob rebuildJob = rebuildIndexRangesJobFactory.create(this.deflector);
        try {
            this.systemJobManager.submit(rebuildJob);
        } catch (SystemJobConcurrencyException e) {
            LOG.error("Concurrency level of this job reached: " + e.getMessage());
            throw new WebApplicationException(403);
        }

        return Response.status(Response.Status.ACCEPTED).build();
    }

}
