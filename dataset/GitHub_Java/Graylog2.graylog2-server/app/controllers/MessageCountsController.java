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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.inject.Inject;
import lib.APIException;
import models.MessageCountHistogram;
import models.MessagesService;
import models.api.results.DateHistogramResult;
import play.mvc.Result;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MessageCountsController extends AuthenticatedController {
    @Inject
    private MessagesService messagesService;

    public Result total() {
        int countResult = messagesService.total();

        Map<String, Integer> result = Maps.newHashMap();
        result.put("events", countResult);

        return ok(new Gson().toJson(result)).as("application/json");
    }

	public Result histogram(String timerange) {
    	int range;
    	try {
    		range = Integer.parseInt(timerange);
    	} catch (NumberFormatException e) {
    		return badRequest("Invalid timerange.");
    	}
		
		try {
			MessageCountHistogram count = new MessageCountHistogram("minute", range);
			DateHistogramResult histogramResult = count.histogram();
			
			List<Map<String, Object>> lines = Lists.newArrayList();
			Map<String, Object> r = Maps.newTreeMap();
			r.put("color", "#26ADE4");
			r.put("name", "Messages");
			r.put("data", histogramResult.getFormattedResults());
			
			lines.add(r);
			
			return ok(new Gson().toJson(lines)).as("application/json");
		} catch (IOException e) {
			return internalServerError("io exception");
		} catch (APIException e) {
			return internalServerError("api exception " + e);
		}
	}
	
}