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
package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.AuthenticatedController;
import lib.json.Json;
import org.graylog2.rest.models.system.responses.GrokPatternSummary;
import org.graylog2.restclient.lib.APIException;
import org.graylog2.restclient.models.ExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Result;

import javax.inject.Inject;
import java.io.IOException;

public class GrokPatternsApiController extends AuthenticatedController {
    private static final Logger log = LoggerFactory.getLogger(GrokPatternsApiController.class);

    private final ExtractorService extractorService;

    @Inject
    public GrokPatternsApiController(ExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    public Result index() {
        try {
            return ok(Json.toJsonString(extractorService.allGrokPatterns())).as("application/json");
        } catch (APIException | IOException e) {
            log.error("Unable to get grok patterns");
        }
        return internalServerError();
    }
    
    
    public Result create() {
        final JsonNode json = request().body().asJson();
        final GrokPatternSummary grokPattern = Json.fromJson(json, GrokPatternSummary.class);
        // remove an empty string to force creation of a new object
        grokPattern.id = null;
        try {
            extractorService.createGrokPattern(grokPattern);
        } catch (APIException | IOException e) {
            return internalServerError();
        }

        return ok();
    }
    
    public Result update() {

        final JsonNode json = request().body().asJson();
        final GrokPatternSummary grokPattern = Json.fromJson(json, GrokPatternSummary.class);
        try {
            extractorService.updateGrokPattern(grokPattern);
        } catch (APIException | IOException e) {
            return internalServerError();
        }

        return ok();
    }
    
    public Result delete(String patternId) {
        final GrokPatternSummary grokPattern = new GrokPatternSummary();
        grokPattern.id = patternId;
        try {
            extractorService.deleteGrokPattern(grokPattern);
        } catch (APIException | IOException e) {
            return internalServerError();
        }
        return ok();
    }
}
