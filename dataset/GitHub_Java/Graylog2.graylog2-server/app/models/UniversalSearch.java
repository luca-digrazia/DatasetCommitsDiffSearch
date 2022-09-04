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
package models;

import com.google.common.net.MediaType;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import controllers.routes;
import lib.APIException;
import lib.ApiClient;
import lib.Tools;
import lib.timeranges.TimeRange;
import models.api.responses.*;
import models.api.results.DateHistogramResult;
import models.api.results.SearchResult;
import play.mvc.Call;
import play.mvc.Http.Request;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class UniversalSearch {

    public static final int PER_PAGE = 100;
    public static final int KEITH = 61;  // http://en.wikipedia.org/wiki/61_(number) -> Keith

    public static final SearchSort DEFAULT_SORT = new SearchSort("timestamp", SearchSort.Direction.DESC);

    private final ApiClient api;
    private final String query;
    private final String filter;
    private final TimeRange timeRange;
    private final Integer page;
    private final SearchSort order;

    @AssistedInject
    private UniversalSearch(ApiClient api, @Assisted TimeRange timeRange, @Assisted String query) {
        this(api, timeRange, query, 0);
    }

    @AssistedInject
    private UniversalSearch(ApiClient api, @Assisted TimeRange timeRange, @Assisted String query, @Assisted Integer page) {
        this(api, timeRange, query, page, null, null);
    }

    @AssistedInject
    private UniversalSearch(ApiClient api, @Assisted String query, @Assisted TimeRange timeRange, @Nullable @Assisted("filter") String filter) {
        this(api, timeRange, query, 0, filter, null);
    }

    @AssistedInject
    public UniversalSearch(ApiClient api, @Assisted TimeRange timeRange, @Assisted("query") String query, @Assisted Integer page, @Assisted("filter") String filter) {
        this(api, timeRange, query, page, filter, null);
    }

    @AssistedInject
    public UniversalSearch(ApiClient api, @Assisted TimeRange timeRange, @Assisted String query, @Assisted Integer page, @Assisted SearchSort order) {
        this(api, timeRange, query, page, null, order);
    }

    @AssistedInject
    private UniversalSearch(ApiClient api, @Assisted TimeRange timeRange, @Assisted("query") String query, @Assisted Integer page, @Assisted("filter") String filter, @Assisted SearchSort order) {
        this.filter = filter;
        this.api = api;
        this.query = query;
        this.timeRange = timeRange;

        if (page == 0) {
            this.page = 0;
        } else {
            this.page = page - 1;
        }

        if (order == null) {
            this.order = DEFAULT_SORT;
        } else {
            this.order = order;
        }
    }

    private <T> T doSearch(Class<T> clazz, MediaType mediaType, int pageSize) throws APIException, IOException {
        return api.get(clazz)
                .path("/search/universal/{0}", timeRange.getType().toString().toLowerCase())
                .queryParams(timeRange.getQueryParams())
                .queryParam("query", query)
                .queryParam("limit", pageSize)
                .queryParam("offset", page * pageSize)
                .queryParam("filter", (filter == null ? "*" : filter))
                .queryParam("sort", order.toApiParam())
                .accept(mediaType)
                .timeout(KEITH, TimeUnit.SECONDS)
                .expect(200, 400)
                .execute();
    }

    public SearchResult search() throws IOException, APIException {
        SearchResultResponse response = doSearch(SearchResultResponse.class, MediaType.JSON_UTF_8, PER_PAGE);

        SearchResult result = new SearchResult(
                query,
                response.builtQuery,
                timeRange,
                response.total_results,
                response.time,
                response.messages,
                response.fields,
                response.usedIndices,
                response.error
        );

        return result;
    }

    public String searchAsCsv() throws IOException, APIException {
        return doSearch(String.class, MediaType.CSV_UTF_8, 100000);  // TODO fix huge page size by using scroll searches and streaming results
    }

    public DateHistogramResult dateHistogram(String interval) throws IOException, APIException {
        DateHistogramResponse response = api.get(DateHistogramResponse.class)
                .path("/search/universal/{0}/histogram", timeRange.getType().toString().toLowerCase())
                .queryParam("interval", interval)
                .queryParam("query", query)
                .queryParams(timeRange.getQueryParams())
                .queryParam("filter", (filter == null ? "*" : filter))
                .timeout(KEITH, TimeUnit.SECONDS)
                .execute();
        return new DateHistogramResult(response.query, response.time, response.interval, response.results);
    }

    public FieldStatsResponse fieldStats(String field) throws IOException, APIException {
        return api.get(FieldStatsResponse.class)
                .path("/search/universal/{0}/stats", timeRange.getType().toString().toLowerCase())
                .queryParam("field", field)
                .queryParam("query", query)
                .queryParams(timeRange.getQueryParams())
                .queryParam("filter", (filter == null ? "*" : filter))
                .timeout(KEITH, TimeUnit.SECONDS)
                .execute();
    }

    public FieldTermsResponse fieldTerms(String field) throws IOException, APIException {
        return api.get(FieldTermsResponse.class)
                .path("/search/universal/{0}/terms", timeRange.getType().toString().toLowerCase())
                .queryParam("field", field)
                .queryParam("query", query)
                .queryParams(timeRange.getQueryParams())
                .queryParam("filter", (filter == null ? "*" : filter))
                .timeout(KEITH, TimeUnit.SECONDS)
                .execute();
    }

    public FieldHistogramResponse fieldHistogram(String field, String interval) throws IOException, APIException {
        return api.get(FieldHistogramResponse.class)
                .path("/search/universal/{0}/fieldhistogram", timeRange.getType().toString().toLowerCase())
                .queryParam("field", field)
                .queryParam("interval", interval)
                .queryParam("query", query)
                .queryParams(timeRange.getQueryParams())
                .queryParam("filter", (filter == null ? "*" : filter))
                .timeout(KEITH, TimeUnit.SECONDS)
                .execute();
    }

    public Call getRoute(Request request, int page) {
        return getRoute(request, page, order.getField(), order.getDirection().toString().toLowerCase());
    }


    public Call getRoute(Request request, int page, String sortField, String sortOrder) {
        int relative = Tools.intSearchParamOrEmpty(request, "relative");
        String from = Tools.stringSearchParamOrEmpty(request, "from");
        String to = Tools.stringSearchParamOrEmpty(request, "to");
        String keyword = Tools.stringSearchParamOrEmpty(request, "keyword");
        String interval = Tools.stringSearchParamOrEmpty(request, "interval");

        // TODO we desperately need to pass the streamid and then build the filter here, instead of passing the filter and then trying to reassemble the streamid.
        if (filter != null && filter.startsWith("streams:")) {
            return routes.StreamSearchController.index(
                    filter.split(":")[1],
                    query,
                    timeRange.getType().toString().toLowerCase(),
                    relative,
                    from,
                    to,
                    keyword,
                    interval,
                    page,
                    "",
                    sortField,
                    sortOrder
            );
        } else {
            return routes.SearchController.index(
                    query,
                    timeRange.getType().toString().toLowerCase(),
                    relative,
                    from,
                    to,
                    keyword,
                    interval,
                    page,
                    "",
                    sortField,
                    sortOrder
            );
        }
    }

    public Call getCsvRoute(Request request, Stream stream) {
        int relative = Tools.intSearchParamOrEmpty(request, "relative");
        String from = Tools.stringSearchParamOrEmpty(request, "from");
        String to = Tools.stringSearchParamOrEmpty(request, "to");
        String keyword = Tools.stringSearchParamOrEmpty(request, "keyword");
        if (stream == null) {
            return routes.SearchController.exportAsCsv(
                    query,
                    "",
                    timeRange.getType().toString().toLowerCase(),
                    relative,
                    from,
                    to,
                    keyword
            );
        } else {
            return routes.StreamSearchController.exportAsCsv(
                    query,
                    stream.getId(),
                    timeRange.getType().toString().toLowerCase(),
                    relative,
                    from,
                    to,
                    keyword
            );
        }
    }

    public SearchSort getOrder() {
        return order;
    }

    public interface Factory {
        UniversalSearch queryWithRange(String query, TimeRange timeRange);

        UniversalSearch queryWithRangeAndFilter(String query, TimeRange timeRange, @Assisted("filter") String filter);

        UniversalSearch queryWithRangePageAndOrder(String query, TimeRange timeRange, Integer page, SearchSort order);

        UniversalSearch queryWithFilterRangeAndPage(@Assisted("query") String query, @Assisted("filter") String filter, TimeRange timeRange, Integer page);

        UniversalSearch queryWithFilterRangePageAndOrder(@Assisted("query") String query, @Assisted("filter") String filter, TimeRange timeRange, Integer page, SearchSort order);
    }

}
