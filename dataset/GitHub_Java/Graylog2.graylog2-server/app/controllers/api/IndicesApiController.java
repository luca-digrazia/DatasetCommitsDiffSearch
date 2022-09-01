/**
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
 *
 */
package controllers.api;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import controllers.AuthenticatedController;
import lib.json.Json;
import org.graylog2.restclient.lib.APIException;
import org.graylog2.restclient.lib.ApiClient;
import org.graylog2.restclient.lib.DateTools;
import org.graylog2.restclient.models.ClusterService;
import org.graylog2.restclient.models.Index;
import org.graylog2.restclient.models.IndexService;
import org.graylog2.restclient.models.api.responses.system.indices.IndexerFailureSummary;
import org.graylog2.restclient.models.api.responses.system.indices.IndexerFailuresResponse;
import org.joda.time.DateTime;
import play.mvc.Result;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class IndicesApiController extends AuthenticatedController {
    private final ClusterService clusterService;
    private final IndexService indexService;

    @Inject
    public IndicesApiController(ClusterService clusterService, IndexService indexService) {
        this.clusterService = clusterService;
        this.indexService = indexService;
    }

    public Result failures(Integer limit, Integer offset) {
        try {
            IndexerFailuresResponse failures = clusterService.getIndexerFailures(limit, offset);

            // dynatable AJAX format.
            List<Map<String, Object>> records = Lists.newArrayList();
            for (IndexerFailureSummary failure : failures.failures) {
                Map<String, Object> record = Maps.newHashMap();
                record.put("timestamp", DateTools.inUserTimeZone(DateTime.parse(failure.timestamp)).toString());
                record.put("errorMessage", failure.message);
                record.put("index", failure.index);
                record.put("deadLetter", failure.written);
                record.put("letterId", failure.letterId);

                records.add(record);
            }

            Map<String, Object> result = Maps.newHashMap();
            result.put("records", records);
            result.put("queryRecordCount", failures.total);
            result.put("totalRecordCount", failures.total);

            return ok(Json.toJsonString(result)).as("application/json");
        } catch (APIException e) {
            String message = "Could not get indexer failures. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    public Result indexInfo(String indexName) {
        try {
            final List<Index> indexes = indexService.all();
            final ImmutableMap<String, Index> map =
                    Maps.uniqueIndex(indexes,
                                     new Function<Index, String>() {
                                         @Nullable
                                         @Override
                                         public String apply(Index input) {
                                             return input.getName();
                                         }
                                     });

            final Index index = map.get(indexName);
            if (index == null) {
                return notFound();
            }
            final String currentTarget = indexService.getDeflectorInfo().currentTarget;
            return ok(views.html.partials.indices.index_info.render(currentTarget, index));

        } catch (APIException | IOException e) {
            return internalServerError();
        }
    }
}
