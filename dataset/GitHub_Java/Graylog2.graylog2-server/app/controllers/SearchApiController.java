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
import lib.SearchTools;
import lib.timeranges.*;
import models.UniversalSearch;
import models.api.responses.FieldStatsResponse;
import models.api.responses.FieldTermsResponse;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class SearchApiController extends AuthenticatedController {

    @Inject
    private UniversalSearch.Factory searchFactory;

    public Result fieldStats(String q, String field, String rangeType, int relative, String from, String to, String keyword, String interval) {
        if (q == null || q.isEmpty()) {
            q = "*";
        }

        // Histogram interval.
        if (interval == null || interval.isEmpty() || !SearchTools.isAllowedDateHistogramInterval(interval)) {
            interval = "hour";
        }

        // Determine timerange type.
        TimeRange timerange;
        try {
            timerange = TimeRange.factory(rangeType, relative, from, to, keyword);
        } catch(InvalidRangeParametersException e2) {
            return status(400, views.html.errors.error.render("Invalid range parameters provided.", e2, request()));
        } catch(IllegalArgumentException e1) {
            return status(400, views.html.errors.error.render("Invalid range type provided.", e1, request()));
        }

        try {
            UniversalSearch search = searchFactory.queryWithRange(q, timerange);
            FieldStatsResponse stats = search.fieldStats(field);

            Map<String, Object> result = Maps.newHashMap();
            result.put("count", stats.count);
            result.put("sum", stats.sum);
            result.put("mean", stats.mean);
            result.put("min", stats.min);
            result.put("max", stats.max);
            result.put("variance", stats.variance);
            result.put("sum_of_squares", stats.sumOfSquares);
            result.put("std_deviation", stats.stdDeviation);

            return ok(new Gson().toJson(result)).as("application/json");
        } catch (IOException e) {
            return internalServerError("io exception");
        } catch (APIException e) {
            if (e.getHttpCode() == 400) {
                // This usually means the field does not have a numeric type. Pass through!
                return badRequest();
            }

            return internalServerError("api exception " + e);
        }
    }

    public Result fieldTerms(String q, String field, String rangeType, int relative, String from, String to, String keyword, String interval) {
        if (q == null || q.isEmpty()) {
            q = "*";
        }

        // Histogram interval.
        if (interval == null || interval.isEmpty() || !SearchTools.isAllowedDateHistogramInterval(interval)) {
            interval = "hour";
        }

        // Determine timerange type.
        TimeRange timerange;
        try {
            timerange = TimeRange.factory(rangeType, relative, from, to, keyword);
        } catch(InvalidRangeParametersException e2) {
            return status(400, views.html.errors.error.render("Invalid range parameters provided.", e2, request()));
        } catch(IllegalArgumentException e1) {
            return status(400, views.html.errors.error.render("Invalid range type provided.", e1, request()));
        }


        ////////////// REMOVE ME
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


        try {
            UniversalSearch search = searchFactory.queryWithRange(q, timerange);;
            FieldTermsResponse terms = search.fieldTerms(field);

            Map<String, Object> result = Maps.newHashMap();
            result.put("total", terms.total);
            result.put("missing", terms.missing);
            result.put("time", terms.time);
            result.put("other", terms.other);
            result.put("terms", terms.terms);

            return ok(new Gson().toJson(result)).as("application/json");
        } catch (IOException e) {
            return internalServerError("io exception");
        } catch (APIException e) {
            if (e.getHttpCode() == 400) {
                // This usually means the field does not have a numeric type. Pass through!
                return badRequest();
            }

            return internalServerError("api exception " + e);
        }
    }

}
