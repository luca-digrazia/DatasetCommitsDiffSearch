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
package controllers;

import com.google.inject.Inject;
import lib.APIException;
import lib.ApiClient;
import lib.BreadcrumbList;
import lib.metrics.Metric;
import models.ClusterEntity;
import models.Node;
import models.NodeService;
import models.Radio;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class MetricsController extends AuthenticatedController {

    @Inject
    private NodeService nodeService;

    public Result ofNode(String nodeId, String preFilter) {
        try {
            Node node = nodeService.loadNode(nodeId);

            BreadcrumbList bc = new BreadcrumbList();
            bc.addCrumb("System", routes.SystemController.index(0));
            bc.addCrumb("Nodes", routes.NodesController.nodes());
            bc.addCrumb(node.getShortNodeId(), routes.NodesController.node(node.getNodeId()));
            bc.addCrumb("Metrics", routes.MetricsController.ofNode(node.getNodeId(), ""));

            Map<String, Metric> metrics = node.getMetrics("org.graylog2");
            metrics.putAll(node.getMetrics("com.graylog2"));

            return ok(views.html.system.metrics.of_node.render(currentUser(), bc, node, metrics, preFilter));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch system information. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }

    public Result ofMasterNode(String preFilter) {
        try {
            String masterId = nodeService.loadMasterNode().getNodeId();
            return redirect(routes.MetricsController.ofNode(masterId, preFilter));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch master node information. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        }
    }

    public Result ofRadio(String radioId, String preFilter) {
        try {
            Radio radio = nodeService.loadRadio(radioId);

            BreadcrumbList bc = new BreadcrumbList();
            bc.addCrumb("System", routes.SystemController.index(0));
            bc.addCrumb("Nodes", routes.NodesController.nodes());
            bc.addCrumb(radio.getShortNodeId(), routes.RadiosController.show(radio.getId()));
            bc.addCrumb("Metrics", routes.MetricsController.ofRadio(radio.getId(), ""));

            return ok(views.html.system.metrics.of_node.render(currentUser(), bc, radio, radio.getMetrics("org.graylog2"), preFilter));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch system information. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }


}
