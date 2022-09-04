package org.graylog2.rest.resources.streams.alerts;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Maps;
import org.apache.commons.mail.EmailException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.alarmcallbacks.AlarmCallbackConfiguration;
import org.graylog2.alarmcallbacks.AlarmCallbackConfigurationService;
import org.graylog2.alarmcallbacks.AlarmCallbackFactory;
import org.graylog2.alarmcallbacks.EmailAlarmCallback;
import org.graylog2.alerts.AbstractAlertCondition;
import org.graylog2.alerts.types.DummyAlertCondition;
import org.graylog2.indexer.Indexer;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackConfigurationException;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackException;
import org.graylog2.plugin.alarms.transports.TransportConfigurationException;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.rest.documentation.annotations.*;
import org.graylog2.rest.resources.RestResource;
import org.graylog2.security.RestPermissions;
import org.graylog2.streams.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
@RequiresAuthentication
@Api(value = "Alert Receivers", description = "Manage stream alert receivers")
@Path("/streams/{streamId}/alerts")
public class StreamAlertReceiverResource extends RestResource {
    private static final Logger LOG = LoggerFactory.getLogger(StreamAlertReceiverResource.class);

    private final StreamService streamService;
    private final AlarmCallbackConfigurationService alarmCallbackConfigurationService;
    private final EmailAlarmCallback emailAlarmCallback;
    private final AlarmCallbackFactory alarmCallbackFactory;
    private final Indexer indexer;

    @Inject
    public StreamAlertReceiverResource(StreamService streamService,
                                       AlarmCallbackConfigurationService alarmCallbackConfigurationService,
                                       EmailAlarmCallback emailAlarmCallback,
                                       AlarmCallbackFactory alarmCallbackFactory,
                                       Indexer indexer) {
        this.streamService = streamService;
        this.alarmCallbackConfigurationService = alarmCallbackConfigurationService;
        this.emailAlarmCallback = emailAlarmCallback;
        this.alarmCallbackFactory = alarmCallbackFactory;
        this.indexer = indexer;
    }

    @POST
    @Timed
    @Path("receivers")
    @ApiOperation(value = "Add an alert receiver")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Stream not found."),
            @ApiResponse(code = 400, message = "Invalid ObjectId.")
    })
    public Response addReceiver(
            @ApiParam(title = "streamId", description = "The stream id this new alert condition belongs to.", required = true) @PathParam("streamId") String streamid,
            @ApiParam(title = "entity", description = "Name/ID of user or email address to add as alert receiver.", required = true) @QueryParam("entity") String entity,
            @ApiParam(title = "type", description = "Type: users or emails", required = true) @QueryParam("type") String type
    ) {
        checkPermission(RestPermissions.STREAMS_EDIT, streamid);

        if(type == null || (!type.equals("users") && !type.equals("emails"))) {
            LOG.warn("No such type: [{}]", type);
            throw new WebApplicationException(400);
        }

        Stream stream;
        try {
            stream = streamService.load(streamid);
        } catch (org.graylog2.database.NotFoundException e) {
            throw new WebApplicationException(404);
        }

        // Maybe the list already contains this receiver?
        if (stream.getAlertReceivers().containsKey(type) || stream.getAlertReceivers().get(type) != null) {
            if (stream.getAlertReceivers().get(type).contains(entity)) {
                return Response.status(Response.Status.CREATED).build();
            }
        }

        streamService.addAlertReceiver(stream, type, entity);

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE @Timed
    @Path("receivers")
    @ApiOperation(value = "Remove an alert receiver")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Stream not found."),
            @ApiResponse(code = 400, message = "Invalid ObjectId.")
    })
    public Response removeReceiver(
            @ApiParam(title = "streamId", description = "The stream id this new alert condition belongs to.", required = true) @PathParam("streamId") String streamid,
            @ApiParam(title = "entity", description = "Name/ID of user or email address to remove from alert receivers.", required = true) @QueryParam("entity") String entity,
            @ApiParam(title = "type", description = "Type: users or emails", required = true) @QueryParam("type") String type) {
        checkPermission(RestPermissions.STREAMS_EDIT, streamid);

        if(!type.equals("users") && !type.equals("emails")) {
            LOG.warn("No such type: [{}]", type);
            throw new WebApplicationException(400);
        }

        Stream stream;
        try {
            stream = streamService.load(streamid);
        } catch (org.graylog2.database.NotFoundException e) {
            throw new WebApplicationException(404);
        }

        streamService.removeAlertReceiver(stream, type, entity);

        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET @Timed
    @Path("sendDummyAlert")
    @ApiOperation(value = "Send a test mail for a given stream")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Stream not found."),
            @ApiResponse(code = 400, message = "Invalid ObjectId.")
    })
    public Response sendDummyAlert(@ApiParam(title = "streamId",
            description = "The stream id this new alert condition belongs to.",
            required = true) @PathParam("streamId") String streamid)
            throws TransportConfigurationException, EmailException {
        checkPermission(RestPermissions.STREAMS_EDIT, streamid);

        Stream stream;
        try {
            stream = streamService.load(streamid);
        } catch (org.graylog2.database.NotFoundException e) {
            throw new WebApplicationException(404);
        }

        Map<String, Object> parameters = Maps.newHashMap();
        DummyAlertCondition dummyAlertCondition = new DummyAlertCondition(stream, null, Tools.iso8601(), getSubject().getPrincipal().toString(), parameters);

        try {
            AbstractAlertCondition.CheckResult checkResult = dummyAlertCondition.runCheck(indexer);
            List<AlarmCallbackConfiguration> callConfigurations = alarmCallbackConfigurationService.getForStream(stream);
            if (callConfigurations.size() > 0)
                for (AlarmCallbackConfiguration configuration : callConfigurations) {
                    AlarmCallback alarmCallback = alarmCallbackFactory.create(configuration);
                    alarmCallback.call(stream, checkResult);
                }
            else
                emailAlarmCallback.call(stream, checkResult);

        } catch (AlarmCallbackException | ClassNotFoundException | AlarmCallbackConfigurationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
