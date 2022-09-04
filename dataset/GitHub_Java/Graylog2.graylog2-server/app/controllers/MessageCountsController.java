/*
 * Copyright 2012-2015 TORCH GmbH, 2015 Graylog, Inc.
 *
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
package controllers;

import com.google.common.collect.Maps;
import com.google.common.net.MediaType;
import lib.json.Json;
import org.graylog2.restclient.models.MessagesService;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.Map;

public class MessageCountsController extends AuthenticatedController {
    private final MessagesService messagesService;

    @Inject
    public MessageCountsController(MessagesService messagesService) {
        this.messagesService = messagesService;
    }

    public Result total() {
        long countResult = messagesService.total();

        Map<String, Long> result = Maps.newHashMap();
        result.put("events", countResult);

        return ok(Json.toJsonString(result)).as(MediaType.JSON_UTF_8.toString());
    }
}
