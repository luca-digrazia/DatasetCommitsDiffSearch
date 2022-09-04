/**
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
package org.graylog2.rest.resources.search;

import com.codahale.metrics.annotation.Timed;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.glassfish.jersey.server.ChunkedOutput;
import org.graylog2.indexer.IndexHelper;
import org.graylog2.indexer.Indexer;
import org.graylog2.indexer.results.ScrollResult;
import org.graylog2.indexer.searches.SearchesConfig;
import org.graylog2.indexer.searches.SearchesConfigBuilder;
import org.graylog2.indexer.searches.Sorting;
import org.graylog2.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.indexer.searches.timeranges.KeywordRange;
import org.graylog2.indexer.searches.timeranges.TimeRange;
import org.graylog2.rest.documentation.annotations.*;
import org.graylog2.rest.resources.search.responses.SearchResponse;
import org.graylog2.security.RestPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
@RequiresAuthentication
@Api(value = "Search/Keyword", description = "Message search")
@Path("/search/universal/keyword")
public class KeywordSearchResource extends SearchResource {

    private static final Logger LOG = LoggerFactory.getLogger(KeywordSearchResource.class);

    @Inject
    public KeywordSearchResource(Indexer indexer) {
        super(indexer);
    }

    @GET @Timed
    @ApiOperation(value = "Message search with keyword as timerange.",
            notes = "Search for messages in a timerange defined by a keyword like \"yesterday\" or \"2 weeks ago to wednesday\".")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid keyword provided.")
    })
    public SearchResponse searchKeyword(
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "keyword", description = "Range keyword", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "limit", description = "Maximum number of messages to return.", required = false) @QueryParam("limit") int limit,
            @ApiParam(title = "offset", description = "Offset", required = false) @QueryParam("offset") int offset,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter,
            @ApiParam(title = "fields", description = "Comma separated list of fields to return", required = false) @QueryParam("fields") String fields,
            @ApiParam(title = "sort", description = "Sorting (field:asc / field:desc)", required = false) @QueryParam("sort") String sort) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkQueryAndKeyword(query, keyword);

        final List<String> fieldList = parseOptionalFields(fields);
        Sorting sorting = buildSorting(sort);

        final SearchesConfig searchesConfig = SearchesConfigBuilder.newConfig()
                .setQuery(query)
                .setFilter(filter)
                .setFields(fieldList)
                .setRange(buildKeywordTimeRange(keyword))
                .setLimit(limit)
                .setOffset(offset)
                .setSorting(sorting)
                .build();

        try {
            return buildSearchResponse(indexer.searches().search(searchesConfig));
        } catch (IndexHelper.InvalidRangeFormatException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.", e);
            throw new WebApplicationException(400);
        } catch (SearchPhaseExecutionException e) {
            throw createRequestExceptionForParseFailure(query, e);
        }
    }

    @GET @Timed
    @ApiOperation(value = "Message search with keyword as timerange.",
                  notes = "Search for messages in a timerange defined by a keyword like \"yesterday\" or \"2 weeks ago to wednesday\".")
    @Produces("text/csv")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid keyword provided.")
    })
    public ChunkedOutput<ScrollResult.ScrollChunk> searchKeywordChunked(
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "keyword", description = "Range keyword", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "limit", description = "Maximum number of messages to return.", required = false) @QueryParam("limit") int limit,
            @ApiParam(title = "offset", description = "Offset", required = false) @QueryParam("offset") int offset,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter,
            @ApiParam(title = "fields", description = "Comma separated list of fields to return", required = true) @QueryParam("fields") String fields) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkQuery(query);
        final List<String> fieldList = parseFields(fields);
        final TimeRange timeRange = buildKeywordTimeRange(keyword);

        try {
            final ScrollResult scroll = indexer.searches()
                    .scroll(query, timeRange, limit, offset, fieldList, filter);
            final ChunkedOutput<ScrollResult.ScrollChunk> output = new ChunkedOutput<>(ScrollResult.ScrollChunk.class);

            LOG.debug("[{}] Scroll result contains a total of {} messages", scroll.getQueryHash(), scroll.totalHits());
            Runnable scrollIterationAction = createScrollChunkProducer(scroll, output, limit);
            // TODO use a shared executor for async responses here instead of a single thread that's not limited
            new Thread(scrollIterationAction).start();
            return output;
        } catch (SearchPhaseExecutionException e) {
            throw createRequestExceptionForParseFailure(query, e);
        } catch (IndexHelper.InvalidRangeFormatException e) {
            throw new BadRequestException(e);
        }
    }

    @GET @Path("/histogram") @Timed
    @ApiOperation(value = "Datetime histogram of a query using keyword timerange.")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided."),
            @ApiResponse(code = 400, message = "Invalid interval provided.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public String histogramKeyword(
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "interval", description = "Histogram interval / bucket size. (year, quarter, month, week, day, hour or minute)", required = true) @QueryParam("interval") String interval,
            @ApiParam(title = "keyword", description = "Range keyword", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkQueryAndInterval(query, interval);
        interval = interval.toUpperCase();
        validateInterval(interval);

        try {
            return json(buildHistogramResult(
                    indexer.searches().histogram(
                            query,
                            Indexer.DateHistogramInterval.valueOf(interval),
                            filter,
                            buildKeywordTimeRange(keyword)
                    )
            ));
        } catch (IndexHelper.InvalidRangeFormatException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.", e);
            throw new WebApplicationException(400);
        }
    }

    @GET @Path("/terms") @Timed
    @ApiOperation(value = "Most common field terms of a query using a keyword timerange.")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public String termsKeyword(
            @ApiParam(title = "field", description = "Message field of to return terms of", required = true) @QueryParam("field") String field,
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "size", description = "Maximum number of terms to return", required = false) @QueryParam("size") int size,
            @ApiParam(title = "keyword", description = "Range keyword", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkQueryAndField(query, field);

        try {
            return json(buildTermsResult(
                    indexer.searches().terms(field, size, query, filter, buildKeywordTimeRange(keyword))
            ));
        } catch (IndexHelper.InvalidRangeFormatException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.", e);
            throw new WebApplicationException(400);
        }
    }

    @GET @Path("/termsstats") @Timed
    @ApiOperation(value = "Ordered field terms of a query computed on another field using a keyword timerange.")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public String termsStatsRelative(
            @ApiParam(title = "key_field", description = "Message field of to return terms of", required = true) @QueryParam("key_field") String keyField,
            @ApiParam(title = "value_field", description = "Value field used for computation", required = true) @QueryParam("value_field") String valueField,
            @ApiParam(title = "order", description = "What to order on (Allowed values: TERM, REVERSE_TERM, COUNT, REVERSE_COUNT, TOTAL, REVERSE_TOTAL, MIN, REVERSE_MIN, MAX, REVERSE_MAX, MEAN, REVERSE_MEAN)", required = true) @QueryParam("order") String order,
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "size", description = "Maximum number of terms to return", required = false) @QueryParam("size") int size,
            @ApiParam(title = "keyword", description = "Keyword timeframe", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter) throws IndexHelper.InvalidRangeFormatException {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkTermsStatsFields(keyField, valueField, order);
        checkQuery(query);

        try {
            return json(buildTermsStatsResult(
                    indexer.searches().termsStats(keyField, valueField, Indexer.TermsStatsOrder.valueOf(order.toUpperCase()), size, query, filter, buildKeywordTimeRange(keyword))
            ));
        } catch (IndexHelper.InvalidRangeFormatException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.", e);
            throw new WebApplicationException(400);
        }
    }

    @GET @Path("/stats") @Timed
    @ApiOperation(value = "Field statistics for a query using a keyword timerange.",
            notes = "Returns statistics like min/max or standard deviation of numeric fields " +
                    "over the whole query result set.")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided."),
            @ApiResponse(code = 400, message = "Field is not of numeric type.")
    })
    public String statsKeyword(
            @ApiParam(title = "field", description = "Message field of numeric type to return statistics for", required = true) @QueryParam("field") String field,
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "keyword", description = "Range keyword", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkQueryAndField(query, field);

        try {
            return json(buildFieldStatsResult(
                    fieldStats(field, query, filter, buildKeywordTimeRange(keyword))
            ));
        } catch (IndexHelper.InvalidRangeFormatException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.", e);
            throw new WebApplicationException(400);
        }
    }

    @GET @Path("/fieldhistogram") @Timed
    @ApiOperation(value = "Datetime histogram of a query using keyword timerange.")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided."),
            @ApiResponse(code = 400, message = "Invalid interval provided."),
            @ApiResponse(code = 400, message = "Field is not of numeric type.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public String fieldHistogramKeyword(
            @ApiParam(title = "query", description = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
            @ApiParam(title = "field", description = "Field of whose values to get the histogram of", required = true) @QueryParam("field") String field,
            @ApiParam(title = "interval", description = "Histogram interval / bucket size. (year, quarter, month, week, day, hour or minute)", required = true) @QueryParam("interval") String interval,
            @ApiParam(title = "keyword", description = "Range keyword", required = true) @QueryParam("keyword") String keyword,
            @ApiParam(title = "filter", description = "Filter", required = false) @QueryParam("filter") String filter) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);

        checkQueryAndInterval(query, interval);
        interval = interval.toUpperCase();
        validateInterval(interval);
        checkStringSet(field);

        try {
            return json(buildHistogramResult(fieldHistogram(field, query, interval, filter, buildKeywordTimeRange(keyword))));
        } catch (IndexHelper.InvalidRangeFormatException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.", e);
            throw new WebApplicationException(400);
        }
    }

    private TimeRange buildKeywordTimeRange(String keyword) {
        try {
            return new KeywordRange(keyword);
        } catch (InvalidRangeParametersException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.");
            throw new WebApplicationException(400);
        }
    }

}
