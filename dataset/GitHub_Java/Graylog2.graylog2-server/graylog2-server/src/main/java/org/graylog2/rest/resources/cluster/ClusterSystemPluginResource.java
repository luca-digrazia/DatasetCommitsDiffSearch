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
import com.wordnik.swagger.annotations.ApiParam;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.cluster.Node;
import org.graylog2.cluster.NodeNotFoundException;
import org.graylog2.cluster.NodeService;
import org.graylog2.rest.RemoteInterfaceProvider;
import org.graylog2.rest.models.system.plugins.responses.PluginList;
import org.graylog2.shared.rest.resources.ProxiedResource;
import org.graylog2.shared.rest.resources.system.RemoteSystemPluginResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@RequiresAuthentication
@Api(value = "Cluster/Plugins", description = "Plugin information for any node in the cluster")
@Path("/cluster/{nodeId}/plugins")
@Produces(MediaType.APPLICATION_JSON)
public class ClusterSystemPluginResource extends ProxiedResource {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterSystemResource.class);

    private final NodeService nodeService;
    private final RemoteInterfaceProvider remoteInterfaceProvider;

    @Inject
    public ClusterSystemPluginResource(NodeService nodeService,
                                       RemoteInterfaceProvider remoteInterfaceProvider,
                                       @Context HttpHeaders httpHeaders) throws NodeNotFoundException {
        super(httpHeaders);
        this.nodeService = nodeService;
        this.remoteInterfaceProvider = remoteInterfaceProvider;
    }

    @GET
    @Timed
    @ApiOperation(value = "List all installed plugins on the given node")
    public PluginList list(@ApiParam(name = "nodeId", value = "The id of the node where processing will be paused.", required = true)
                           @PathParam("nodeId") String nodeId) throws IOException, NodeNotFoundException {
        final Node targetNode = nodeService.byNodeId(nodeId);

        final RemoteSystemPluginResource remoteSystemPluginResource = remoteInterfaceProvider.get(targetNode,
                this.authenticationToken,
                RemoteSystemPluginResource.class);
        final Response<PluginList> response = remoteSystemPluginResource.list().execute();
        if (response.isSuccess()) {
            return response.body();
        } else {
            LOG.warn("Unable to get plugin list on node " + nodeId + ": " + response.message());
        }

        return null;
    }
}
