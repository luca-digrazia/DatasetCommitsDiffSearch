/*
 * Copyright 2013 TORCH UG
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
package controllers;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.inject.Inject;
import lib.APIException;
import lib.ApiClient;
import lib.BreadcrumbList;
import lib.ExclusiveInputException;
import models.Node;
import models.NodeService;
import models.api.responses.system.InputTypeSummaryResponse;
import models.api.results.MessageResult;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class InputsController extends AuthenticatedController {

    @Inject
    private NodeService nodeService;

    public Result manage(String nodeId) {
        // TODO: account field attributes using JS (greater than, listen_address, ...)
        // TODO: persist inputs

        Node node = nodeService.loadNode(nodeId);

        if (node == null) {
            String message = "Did not find node.";
            return status(404, views.html.errors.error.render(message, new RuntimeException(), request()));
        }

        BreadcrumbList bc = new BreadcrumbList();
        bc.addCrumb("System", routes.SystemController.index(0));
        bc.addCrumb("Nodes", routes.SystemController.nodes());
        bc.addCrumb(node.getShortNodeId(), routes.SystemController.node(node.getNodeId()));
        bc.addCrumb("Inputs", routes.InputsController.manage(node.getNodeId()));

        try {
            return ok(views.html.system.inputs.manage.render(
                    currentUser(),
                    bc,
                    node,
                    node.getAllInputTypeInformation()
            ));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch system information. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        }
    }

    public Result launch(String nodeId) {
        Map<String, Object> configuration = Maps.newHashMap();
        Map<String, String[]> form = request().body().asFormUrlEncoded();

        String inputType = form.get("type")[0];
        String inputTitle = form.get("title")[0];

        try {
            final Node node = nodeService.loadNode(nodeId);
            InputTypeSummaryResponse inputInfo = node.getInputTypeInformation(inputType);

            for (Map.Entry<String, String[]> f : form.entrySet()) {
                if (!f.getKey().startsWith("configuration_")) {
                    continue;
                }

                String key = f.getKey().substring("configuration_".length());
                Object value;

                if (f.getValue().length > 0) {
                    String stringValue = f.getValue()[0];

                    // Decide what to cast to. (string, bool, number)
                    switch((String) inputInfo.requestedConfiguration.get(key).get("type")) {
                        case "text":
                            value = stringValue;
                            break;
                        case "number":
                            value = Integer.parseInt(stringValue);
                            break;
                        case "boolean":
                            value = stringValue.equals("true");
                            break;
                        case "dropdown":
                            value = stringValue;
                            break;
                        default: continue;
                    }

                } else {
                    continue;
                }

                configuration.put(key, value);
            }

            try {
                if (! node.launchInput(inputTitle, inputType, configuration, currentUser(), inputInfo.isExclusive)) {
                    return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, new RuntimeException("Could not launch input " + inputTitle), request()));
                }
            } catch (ExclusiveInputException e) {
                flash("error", "This input is exclusive and already running.");
                return redirect(routes.InputsController.manage(nodeId));
            }

            return redirect(routes.InputsController.manage(nodeId));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not launch input. We expected HTTP 202, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        }
    }

    public Result terminate(String nodeId, String inputId) {
        if (! nodeService.loadNode(nodeId).terminateInput(inputId)) {
            flash("Could not terminate input " + inputId);
        }
        return redirect(routes.InputsController.manage(nodeId));
    }

    public Result recentMessage(String nodeId, String inputId) {
        try {
            Node node = nodeService.loadNode(nodeId);
            MessageResult recentlyReceivedMessage = node.getInput(inputId).getRecentlyReceivedMessage(nodeId);

            if (recentlyReceivedMessage == null) {
                return notFound();
            }

            Map<String, Object> result = Maps.newHashMap();
            result.put("id", recentlyReceivedMessage.getId());
            result.put("fields", recentlyReceivedMessage.getFields());

            return ok(new Gson().toJson(result)).as("application/json");
        } catch (IOException e) {
            return status(500);
        } catch (APIException e) {
            return status(e.getHttpCode());
        }
    }

}
