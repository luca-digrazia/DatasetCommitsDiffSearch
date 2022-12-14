/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.rest.resources.alarmcallbacks;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.alarmcallbacks.AlarmCallbackHistory;
import org.graylog2.alarmcallbacks.AlarmCallbackHistoryService;
import org.graylog2.rest.models.alarmcallbacks.AlarmCallbackHistoryListSummary;
import org.graylog2.rest.models.alarmcallbacks.AlarmCallbackHistorySummary;
import org.graylog2.shared.rest.resources.RestResource;
import org.graylog2.shared.security.RestPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@RequiresAuthentication
@Api(value = "AlarmCallbacks", description = "Manage stream alarm callback histories")
@Path("/streams/{streamid}/alerts/{alertId}/history")
public class AlarmCallbackHistoryResource extends RestResource {
    private static final Logger LOG = LoggerFactory.getLogger(AlarmCallbackHistoryResource.class);

    private final AlarmCallbackHistoryService alarmCallbackHistoryService;

    @Inject
    public AlarmCallbackHistoryResource(AlarmCallbackHistoryService alarmCallbackHistoryService) {
        this.alarmCallbackHistoryService = alarmCallbackHistoryService;
    }

    @GET
    @Timed
    @ApiOperation(value = "Get a list of all alarm callbacks for this stream")
    @Produces(MediaType.APPLICATION_JSON)
    public AlarmCallbackHistoryListSummary getForAlert(@ApiParam(name = "streamid", value = "The id of the stream whose alarm callbacks history we want.", required = true)
                                                       @PathParam("streamid") String streamid,
                                                       @ApiParam(name = "alertId", value = "The id of the alert whose callback history we want.", required = true)
                                                       @PathParam("alertId") String alertId,
                                                       @ApiParam(name = "skip", value = "The number of elements to skip (offset).", required = true)
                                                       @QueryParam("skip") @DefaultValue("0") int skip,
                                                       @ApiParam(name = "limit", value = "The maximum number of elements to return.", required = true)
                                                       @QueryParam("limit") @DefaultValue("0") int limit) {
        checkPermission(RestPermissions.STREAMS_READ, streamid);

        final List<AlarmCallbackHistory> historyList = this.alarmCallbackHistoryService.getForAlertId(alertId, skip, limit);

        final List<AlarmCallbackHistorySummary> historySummaryList = new ArrayList<>(historyList.size());
        for (AlarmCallbackHistory alarmCallbackHistory : historyList) {
            historySummaryList.add(AlarmCallbackHistorySummary.create(alarmCallbackHistory.id(),
                    alarmCallbackHistory.alarmcallbackConfigurationId(),
                    alarmCallbackHistory.alertId(),
                    alarmCallbackHistory.alertConditionId(),
                    alarmCallbackHistory.result(),
                    alarmCallbackHistory.createdAt()));
        }

        return AlarmCallbackHistoryListSummary.create(historySummaryList);
    }
}
