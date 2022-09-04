/**
 * Copyright 2013 Lennart Koopmann <lennart@socketfeed.com>
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

package org.graylog2.indexer.searches;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.datehistogram.DateHistogramFacet;
import org.elasticsearch.search.facet.datehistogram.DateHistogramFacetBuilder;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.graylog2.Core;
import org.graylog2.indexer.IndexHelper;
import org.graylog2.indexer.Indexer;
import org.graylog2.indexer.results.DateHistogramResult;
import org.graylog2.indexer.results.SearchResult;
import org.graylog2.indexer.results.TermsResult;
import org.graylog2.plugin.Tools;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.queryString;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class Searches {

	private final Core server;
	private final Client c;

    private final static int LIMIT = 150;

    private final static String TERMS_FACET_NAME = "gl2_terms";

	public Searches(Client client, Core server) {
		this.server = server;
		this.c = client;
	}
	
	public SearchResult search(String query, int timerange, int limit) {
        if(limit <= 0) {
            limit = LIMIT;
        }

		SearchResponse r = c.search(standardSearchRequest(query, limit, timerange, SortOrder.DESC).request()).actionGet();
		return new SearchResult(r.getHits(), query, r.getTook());
	}

    public TermsResult terms(String field, int size, String query, int timerange) {
        if (size == 0) {
            size = 50;
        }

        SearchRequestBuilder srb = standardSearchRequest(query);

        TermsFacetBuilder terms = new TermsFacetBuilder(TERMS_FACET_NAME);
        terms.facetFilter(IndexHelper.getTimestampRangeFilter(timerange));
        terms.global(false);
        terms.field(field);
        terms.size(size);

        srb.addFacet(terms);

        SearchResponse r = c.search(srb.request()).actionGet();

        return new TermsResult(
                (TermsFacet) r.getFacets().facet(TERMS_FACET_NAME),
                query,
                r.getTook()
        );
    }
	
	public DateHistogramResult histogram(String query, Indexer.DateHistogramInterval interval, int timerange) {
		DateHistogramFacetBuilder fb = FacetBuilders.dateHistogramFacet("histogram")
				.field("timestamp")
				.facetFilter(IndexHelper.getTimestampRangeFilter(timerange))
				.interval(interval.toString().toLowerCase());
		
		SearchRequestBuilder srb = c.prepareSearch();
		srb.setIndices(server.getDeflector().getAllDeflectorIndexNames()); // XXX 020: have a method that builds time ranged index requests
		srb.setQuery(queryString(query));
		srb.addFacet(fb);
		
		SearchResponse r = c.search(srb.request()).actionGet();
		return new DateHistogramResult((DateHistogramFacet) r.getFacets().facet("histogram"), query, interval, r.getTook());
	}

    public SearchHit firstOfIndex(String index) {
        return oneOfIndex(index, matchAllQuery(), SortOrder.DESC);
    }

    public SearchHit lastOfIndex(String index) {
        return oneOfIndex(index, matchAllQuery(), SortOrder.ASC);
    }

    private SearchRequestBuilder standardSearchRequest(String query) {
        return standardSearchRequest(query, 0, 0, null);
    }

    private SearchRequestBuilder standardSearchRequest(String query, int limit, int timerange, SortOrder sort) {
        SearchRequestBuilder srb = c.prepareSearch();
        srb.setIndices(server.getDeflector().getAllDeflectorIndexNames()); // XXX 020: have a method that builds time ranged index requests
        srb.setQuery(queryString(query));

        if (limit > 0) {
            srb.setSize(limit);
        }

        if (timerange > 0) {
            srb.setFilter(IndexHelper.getTimestampRangeFilter(timerange));
        }

        if (sort != null) {
            srb.addSort("timestamp", sort);
        }

        return srb;
    }

    private SearchHit oneOfIndex(String index, QueryBuilder q, SortOrder sort) {
        SearchRequestBuilder srb = c.prepareSearch();
        srb.setIndices(index);
        srb.setQuery(q);
        srb.setSize(1);
        srb.addSort("timestamp", sort);

        SearchResponse r = c.search(srb.request()).actionGet();
        if (r.getHits() != null && r.getHits().totalHits() > 0) {
            return r.getHits().getAt(0);
        } else {
            return null;
        }
    }

	
}
