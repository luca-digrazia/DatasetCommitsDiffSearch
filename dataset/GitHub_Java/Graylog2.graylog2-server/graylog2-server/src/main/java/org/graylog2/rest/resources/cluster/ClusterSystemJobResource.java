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
package org.graylog2.rest.resources.cluster;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.cluster.Node;
import org.graylog2.cluster.NodeNotFoundException;
import org.graylog2.cluster.NodeService;
import org.graylog2.rest.RemoteInterfaceProvider;
import org.graylog2.rest.models.system.SystemJobSummary;
import org.graylog2.rest.resources.system.jobs.RemoteSystemJobResource;
import org.graylog2.shared.rest.resources.RestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.Response;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresAuthentication
@Api(value = "Cluster/Jobs", description = "Cluster-wide System Jobs")
@Path("/cluster/jobs")
public class ClusterSystemJobResource extends RestResource {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterSystemJobResource.class);

    private final NodeService nodeService;
    private final RemoteInterfaceProvider remoteInterfaceProvider;
    private final String authenticationToken;

    @Inject
    public ClusterSystemJobResource(NodeService nodeService,
                                    RemoteInterfaceProvider remoteInterfaceProvider,
                                    @Context HttpHeaders httpHeaders) throws NodeNotFoundException {
        this.nodeService = nodeService;
        this.remoteInterfaceProvider = remoteInterfaceProvider;

        final List<String> authenticationTokens = httpHeaders.getRequestHeader("Authorization");
        if (authenticationTokens != null && authenticationTokens.size() >= 1) {
            this.authenticationToken = authenticationTokens.get(0);
        } else {
            this.authenticationToken = null;
        }
    }

    @GET
    @Timed
    @ApiOperation(value = "List currently running jobs")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Map<String, List<SystemJobSummary>>> list() throws IOException {
        final Map<String, Node> nodes = nodeService.allActive();
        final Map<String, Map<String, List<SystemJobSummary>>> result = new HashMap<>(nodes.size());
        nodes.entrySet().stream().forEach(entry -> {
            final RemoteSystemJobResource remoteSystemJobResource = remoteInterfaceProvider.get(entry.getValue(), this.authenticationToken, RemoteSystemJobResource.class);
            try {
                final Response<Map<String, List<SystemJobSummary>>> response = remoteSystemJobResource.list().execute();
                if (response.isSuccess()) {
                    result.put(entry.getKey(), response.body());
                } else {
                    LOG.warn("Unable to fetch system jobs from node " + entry.getKey() + ": " + response);
                }
            } catch (IOException e) {
                LOG.warn("Unable to fetch system jobs from node " + entry.getKey() + ": ", e);
            }
        });

        return result;
    }
}
