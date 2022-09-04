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

package org.graylog2.indexer.results;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.graylog2.plugin.Message;

import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Sets;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class SearchResult extends IndexQueryResult {

	private final int totalResults;
	private final List<ResultMessage> results;
	private final Set<String> fields;
    private final Set<String> usedIndices;

	public SearchResult(SearchHits searchHits, Set<String> usedIndices, String originalQuery, TimeValue took) {
        super(originalQuery, took);

		this.results = buildResults(searchHits);
		this.fields = extractFields(searchHits);
		this.totalResults = (int) searchHits.getTotalHits();
        this.usedIndices = usedIndices;
	}
	
	public int getTotalResults() {
		return totalResults;
	}
	
	public List<ResultMessage> getResults() {
		return results;
	}
	
	public Set<String> getFields() {
		return fields;
	}
	
	private List<ResultMessage> buildResults(SearchHits hits) {
		List<ResultMessage> r = Lists.newArrayList();
		
		Iterator<SearchHit> i = hits.iterator();
		while(i.hasNext()) {
			r.add(ResultMessage.parseFromSource(i.next()));
		}
		
		return r;
	}
	
	private Set<String> extractFields(SearchHits hits) {
		Set<String> fields = Sets.newHashSet();
		
		Iterator<SearchHit> i = hits.iterator();
		while(i.hasNext()) {
			for (String field : i.next().sourceAsMap().keySet()) {
				if (!Message.RESERVED_FIELDS.contains(field)) {
					fields.add(field);
				}
			}
		}
		
		// Because some fields actually make sense in this result and some don't.
		fields.add("message");
		fields.add("source");
		fields.remove("streams");
		fields.remove("full_message");
		
		return fields;
	}

    public Set<String> getUsedIndices() {
        return usedIndices;
    }

}
