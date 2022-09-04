/**
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
package org.graylog2.rest.resources.system.indexer;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.graylog2.indexer.Deflector;
import org.graylog2.indexer.cluster.Cluster;
import org.graylog2.indexer.indices.IndexStatistics;
import org.graylog2.indexer.indices.Indices;
import org.graylog2.indexer.ranges.RebuildIndexRangesJob;
import org.graylog2.rest.resources.RestResource;
import org.graylog2.security.RestPermissions;
import org.graylog2.system.jobs.SystemJob;
import org.graylog2.system.jobs.SystemJobConcurrencyException;
import org.graylog2.system.jobs.SystemJobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.nullToEmpty;

@RequiresAuthentication
@Api(value = "Indexer/Indices", description = "Index information")
@Path("/system/indexer/indices")
public class IndicesResource extends RestResource {
    private static final Logger LOG = LoggerFactory.getLogger(IndicesResource.class);

    @Inject
    private RebuildIndexRangesJob.Factory rebuildIndexRangesJobFactory;
    @Inject
    private Indices indices;
    @Inject
    private Cluster cluster;
    @Inject
    private Deflector deflector;
    @Inject
    private SystemJobManager systemJobManager;

    @GET
    @Timed
    @Path("/{index}")
    @ApiOperation(value = "Get information of an index and its shards.")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> single(@ApiParam(name = "index") @PathParam("index") String index) {
        checkPermission(RestPermissions.INDICES_READ, index);

        if (!deflector.isGraylog2Index(index)) {
            LOG.info("Index [{}] doesn't look like an index managed by Graylog2.", index);
            throw new NotFoundException();
        }

        final ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();
        try {
            final IndexStatistics stats = indices.getIndexStats(index);
            if (stats == null) {
                LOG.error("Index [{}] not found.", index);
                throw new NotFoundException();
            }

            final ImmutableList.Builder<Map<String, Object>> routing = ImmutableList.builder();
            for (ShardRouting shardRouting : stats.getShardRoutings()) {
                routing.add(shardRouting(shardRouting));
            }

            result.put("primary_shards", indexStats(stats.getPrimaries()));
            result.put("all_shards", indexStats(stats.getTotal()));
            result.put("routing", routing.build());
            result.put("is_reopened", indices.isReopened(index));
        } catch (Exception e) {
            LOG.error("Could not get indices information.", e);
            throw new InternalServerErrorException(e);
        }

        return result.build();
    }

    @GET
    @Timed
    @Path("/closed")
    @ApiOperation(value = "Get a list of closed indices that can be reopened.")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> closed() {
        Set<String> closedIndices;
        try {
            closedIndices = Sets.filter(indices.getClosedIndices(), new Predicate<String>() {
                @Override
                public boolean apply(String indexName) {
                    return isPermitted(RestPermissions.INDICES_READ, indexName);
                }
            });
        } catch (Exception e) {
            LOG.error("Could not get closed indices.", e);
            throw new InternalServerErrorException(e);
        }

        return ImmutableMap.of(
                "indices", closedIndices,
                "total", closedIndices.size());
    }

    @POST
    @Timed
    @Path("/{index}/reopen")
    @ApiOperation(value = "Reopen a closed index. This will also trigger an index ranges rebuild job.")
    @Produces(MediaType.APPLICATION_JSON)
    public void reopen(@ApiParam(name = "index") @PathParam("index") String index) {
        checkPermission(RestPermissions.INDICES_CHANGESTATE, index);

        if (!deflector.isGraylog2Index(index)) {
            LOG.info("Index [{}] doesn't look like an index managed by Graylog2.", index);
            throw new NotFoundException();
        }

        indices.reopenIndex(index);

        // Trigger index ranges rebuild job.
        final SystemJob rebuildJob = rebuildIndexRangesJobFactory.create(deflector);
        try {
            systemJobManager.submit(rebuildJob);
        } catch (SystemJobConcurrencyException e) {
            LOG.error("Concurrency level of this job reached: " + e.getMessage());
            throw new ForbiddenException();
        }
    }

    @POST
    @Timed
    @Path("/{index}/close")
    @ApiOperation(value = "Close an index. This will also trigger an index ranges rebuild job.")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "You cannot close the current deflector target index.")
    })
    public void close(@ApiParam(name = "index") @PathParam("index") String index) {
        checkPermission(RestPermissions.INDICES_CHANGESTATE, index);

        if (!deflector.isGraylog2Index(index)) {
            LOG.info("Index [{}] doesn't look like an index managed by Graylog2.", index);
            throw new NotFoundException();
        }

        if (deflector.getCurrentActualTargetIndex().equals(index)) {
            throw new ForbiddenException();
        }

        // Close index.
        indices.close(index);

        // Trigger index ranges rebuild job.
        final SystemJob rebuildJob = rebuildIndexRangesJobFactory.create(deflector);
        try {
            systemJobManager.submit(rebuildJob);
        } catch (SystemJobConcurrencyException e) {
            LOG.error("Concurrency level of this job reached: " + e.getMessage());
            throw new ForbiddenException();
        }
    }

    @DELETE
    @Timed
    @Path("/{index}")
    @ApiOperation(value = "Delete an index. This will also trigger an index ranges rebuild job.")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "You cannot delete the current deflector target index.")
    })
    public void delete(@ApiParam(name = "index") @PathParam("index") String index) {
        checkPermission(RestPermissions.INDICES_DELETE, index);

        if (!deflector.isGraylog2Index(index)) {
            LOG.info("Index [{}] doesn't look like an index managed by Graylog2.", index);
            throw new NotFoundException();
        }

        if (deflector.getCurrentActualTargetIndex().equals(index)) {
            throw new ForbiddenException();
        }

        // Delete index.
        indices.delete(index);

        // Trigger index ranges rebuild job.
        final SystemJob rebuildJob = rebuildIndexRangesJobFactory.create(deflector);
        try {
            systemJobManager.submit(rebuildJob);
        } catch (SystemJobConcurrencyException e) {
            LOG.error("Concurrency level of this job reached: " + e.getMessage());
            throw new ForbiddenException();
        }

    }

    private Map<String, Object> shardRouting(ShardRouting route) {
        final ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();

        result.put("id", route.shardId().getId());
        result.put("state", route.state().name().toLowerCase());
        result.put("active", route.active());
        result.put("primary", route.primary());
        result.put("node_id", route.currentNodeId());
        result.put("node_name", cluster.nodeIdToName(route.currentNodeId()));
        result.put("node_hostname", cluster.nodeIdToHostName(route.currentNodeId()));
        result.put("relocating_to", nullToEmpty(route.relocatingNodeId()));

        return result.build();
    }

    private Map<String, Object> indexStats(final CommonStats stats) {
        final ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();

        result.put("flush", ImmutableMap.<String, Object>of(
                "total", stats.getFlush().getTotal(),
                "time_seconds", stats.getFlush().getTotalTime().getSeconds()
        ));

        result.put("get", ImmutableMap.<String, Object>of(
                "total", stats.getGet().getCount(),
                "time_seconds", stats.getGet().getTime().getSeconds()
        ));

        result.put("index", ImmutableMap.<String, Object>of(
                "total", stats.getIndexing().getTotal().getIndexCount(),
                "time_seconds", stats.getIndexing().getTotal().getIndexTime().getSeconds()
        ));

        result.put("merge", ImmutableMap.<String, Object>of(
                "total", stats.getMerge().getTotal(),
                "time_seconds", stats.getMerge().getTotalTime().getSeconds()
        ));

        result.put("refresh", ImmutableMap.<String, Object>of(
                "total", stats.getRefresh().getTotal(),
                "time_seconds", stats.getRefresh().getTotalTime().getSeconds()
        ));

        result.put("search_query", ImmutableMap.<String, Object>of(
                "total", stats.getSearch().getTotal().getQueryCount(),
                "time_seconds", stats.getSearch().getTotal().getQueryTime().getSeconds()
        ));

        result.put("search_fetch", ImmutableMap.<String, Object>of(
                "total", stats.getSearch().getTotal().getFetchCount(),
                "time_seconds", stats.getSearch().getTotal().getFetchTime().getSeconds()
        ));

        result.put("open_search_contexts", stats.getSearch().getOpenContexts());
        result.put("store_size_bytes", stats.getStore().getSize().getBytes());
        result.put("segments", stats.getSegments().getCount());

        result.put("documents", ImmutableMap.<String, Object>of(
                "count", stats.getDocs().getCount(),
                "deleted", stats.getDocs().getDeleted()
        ));

        return result.build();
    }
}