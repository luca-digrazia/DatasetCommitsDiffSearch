/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
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

import com.google.common.collect.Maps;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.facet.terms.TermsFacet;

import java.util.List;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class TermsResult extends IndexQueryResult {

    private final long total;
    private final long missing;
    private final long other;
    private final Map<String, Integer> terms;

    public TermsResult(TermsFacet f, String originalQuery, TimeValue took) {
        super(originalQuery, took);

        this.total = f.getTotalCount();
        this.missing = f.getMissingCount();
        this.other = f.getOtherCount();

        this.terms = buildTermsMap(f.getEntries());
    }

    private Map<String, Integer> buildTermsMap(List<? extends TermsFacet.Entry> entries) {
        Map<String, Integer> terms = Maps.newHashMap();

        for(TermsFacet.Entry term : entries) {
            terms.put(term.getTerm().string(), term.getCount());
        }

        return terms;
    }

    public long getTotal() {
        return total;
    }

    public long getMissing() {
        return missing;
    }

    public long getOther() {
        return other;
    }

    public Map<String, Integer> getTerms() {
        return terms;
    }

}
