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
import models.Radio;
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
        try {
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
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }

    public Result manageRadio(String radioId) {
        try {
            Radio radio = nodeService.loadRadio(radioId);

            if (radio == null) {
                String message = "Did not find radio.";
                return status(404, views.html.errors.error.render(message, new RuntimeException(), request()));
            }

            BreadcrumbList bc = new BreadcrumbList();
            bc.addCrumb("System", routes.SystemController.index(0));
            bc.addCrumb("Nodes", routes.SystemController.nodes());
            bc.addCrumb(radio.getShortNodeId(), routes.RadiosController.show(radio.getId()));
            bc.addCrumb("Inputs", routes.InputsController.manageRadio(radio.getId()));

            return ok(views.html.system.inputs.manage_radio.render(
                    currentUser(),
                    bc,
                    radio,
                    radio.getAllInputTypeInformation()
            ));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch system information. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
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
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }

    public Result launchRadio(String radioId) {
        Map<String, Object> configuration = Maps.newHashMap();
        Map<String, String[]> form = request().body().asFormUrlEncoded();

        String inputType = form.get("type")[0];
        String inputTitle = form.get("title")[0];

        // TODO so duplicate. wow. #doge

        try {
            final Radio radio = nodeService.loadRadio(radioId);
            InputTypeSummaryResponse inputInfo = radio.getInputTypeInformation(inputType);

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
                if (!radio.launchInput(inputTitle, inputType, configuration, currentUser(), inputInfo.isExclusive)) {
                    return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, new RuntimeException("Could not launch input " + inputTitle), request()));
                }
            } catch (ExclusiveInputException e) {
                flash("error", "This input is exclusive and already running.");
                return redirect(routes.InputsController.manageRadio(radioId));
            }

            return redirect(routes.InputsController.manageRadio(radioId));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not launch input. We expected HTTP 202, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }

    public Result terminate(String nodeId, String inputId) {
        try {
            if (!nodeService.loadNode(nodeId).terminateInput(inputId)) {
                flash("Could not terminate input " + inputId);
            }
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }

        return redirect(routes.InputsController.manage(nodeId));
    }

    public Result addStaticField(String nodeId, String inputId) {
        Map<String, String[]> form = request().body().asFormUrlEncoded();

        if(form.get("key") == null || form.get("value") == null) {
            flash("error", "Missing parameters.");
            return redirect(routes.InputsController.manage(nodeId));
        }

        String key = form.get("key")[0];
        String value = form.get("value")[0];

        try {
            nodeService.loadNode(nodeId).getInput(inputId).addStaticField(key, value);
            return redirect(routes.InputsController.manage(nodeId));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not add static field. We expected HTTP 202, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }

    public Result removeStaticField(String nodeId, String inputId, String key) {
        try {
            nodeService.loadNode(nodeId).getInput(inputId).removeStaticField(key);
            return redirect(routes.InputsController.manage(nodeId));
        } catch (IOException e) {
            return status(500, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not delete static field. We expected HTTP 204, but got a HTTP " + e.getHttpCode() + ".";
            return status(500, views.html.errors.error.render(message, e, request()));
        } catch (NodeService.NodeNotFoundException e) {
            return status(404, views.html.errors.error.render(ApiClient.ERROR_MSG_NODE_NOT_FOUND, e, request()));
        }
    }

}
