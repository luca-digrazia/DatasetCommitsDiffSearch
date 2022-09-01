package org.graylog2.rest.resources.streams.outputs;

import com.codahale.metrics.annotation.Timed;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.graylog2.database.ValidationException;
import org.graylog2.plugin.streams.Output;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.rest.documentation.annotations.*;
import org.graylog2.rest.resources.RestResource;
import org.graylog2.security.RestPermissions;
import org.graylog2.streams.OutputService;
import org.graylog2.streams.StreamService;
import org.graylog2.streams.outputs.AddOutputRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
@RequiresAuthentication
@Api(value = "StreamOutputs", description = "Manage stream outputs for a given stream")
@Path("/streams/{streamid}/outputs")
public class StreamOutputResource extends RestResource {
    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    private final OutputService outputService;
    private final StreamService streamService;

    @Inject
    public StreamOutputResource(OutputService outputService, StreamService streamService) {
        this.outputService = outputService;
        this.streamService = streamService;
    }

    @GET @Timed
    @ApiOperation(value = "Get a list of all outputs for a stream")
    @RequiresPermissions(RestPermissions.STREAM_OUTPUTS_CREATE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such stream on this node.")
    })
    public Response get(@ApiParam(title = "streamid", description = "The id of the stream whose outputs we want.", required = true) @PathParam("streamid") String streamid) throws org.graylog2.database.NotFoundException {
        checkPermission(RestPermissions.STREAMS_READ, streamid);
        checkPermission(RestPermissions.STREAM_OUTPUTS_READ);

        final Stream stream = streamService.load(streamid);

        return Response.status(Response.Status.OK).entity(stream.getOutputs()).build();
    }

    @GET @Path("/{outputId}")
    @Timed
    @ApiOperation(value = "Get specific output of a stream")
    @RequiresPermissions(RestPermissions.STREAM_OUTPUTS_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such stream/output on this node.")
    })
    public Output get(@ApiParam(title = "streamid", description = "The id of the stream whose outputs we want.", required = true) @PathParam("streamid") String streamid,
                            @ApiParam(title = "outputId", description = "The id of the output we want.", required = true) @PathParam("outputId") String outputId) {
        checkPermission(RestPermissions.STREAMS_READ, streamid);
        checkPermission(RestPermissions.STREAM_OUTPUTS_READ, outputId);

        try {
            streamService.load(streamid);
        } catch (org.graylog2.database.NotFoundException e) {
            throw new javax.ws.rs.NotFoundException("Stream not found!");
        }

        try {
            return outputService.load(outputId);
        } catch (org.graylog2.database.NotFoundException e) {
            throw new javax.ws.rs.NotFoundException("Stream output not found!");
        }
    }

    @POST
    @Timed
    @ApiOperation(value = "Associate outputs with a stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid output specification in input.")
    })
    public Response add(@ApiParam(title = "streamid", description = "The id of the stream whose outputs we want.", required = true) @PathParam("streamid") String streamid,
                           @ApiParam(title = "JSON body", required = true) AddOutputRequest request) throws ValidationException, org.graylog2.database.NotFoundException {
        checkPermission(RestPermissions.STREAM_OUTPUTS_CREATE);

        final Stream stream = streamService.load(streamid);

        for (String outputId : request.outputs) {
            final Output output = outputService.load(outputId);
            streamService.addOutput(stream, output);
        }

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE @Path("/{outputId}")
    @Timed
    @ApiOperation(value = "Delete output of a stream")
    @RequiresPermissions(RestPermissions.STREAM_OUTPUTS_DELETE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such stream/output on this node.")
    })
    public Response remove(@ApiParam(title = "streamid", description = "The id of the stream whose outputs we want.", required = true) @PathParam("streamid") String streamid,
                           @ApiParam(title = "outputId", description = "The id of the output that should be deleted", required = true) @PathParam("outputId") String outputId) throws org.graylog2.database.NotFoundException {
        checkPermission(RestPermissions.STREAM_OUTPUTS_DELETE);

        final Stream stream = streamService.load(streamid);
        final Output output = outputService.load(outputId);

        streamService.removeOutput(stream, output);

        return Response.status(Response.Status.OK).build();
    }
}
