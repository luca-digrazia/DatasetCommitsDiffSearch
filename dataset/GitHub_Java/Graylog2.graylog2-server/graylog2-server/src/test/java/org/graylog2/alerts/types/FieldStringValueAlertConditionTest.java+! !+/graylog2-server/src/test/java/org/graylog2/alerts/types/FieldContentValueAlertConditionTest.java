/**
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
package org.graylog2.alerts.types;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.graylog2.Configuration;
import org.graylog2.alerts.AlertConditionTest;
import org.graylog2.indexer.ranges.IndexRange;
import org.graylog2.indexer.ranges.IndexRangeImpl;
import org.graylog2.indexer.results.SearchResult;
import org.graylog2.indexer.searches.Sorting;
import org.graylog2.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.alarms.AlertCondition;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class FieldStringValueAlertConditionTest extends AlertConditionTest {

    @Test
    public void testConstructor() throws Exception {
        final Map<String, Object> parameters = getParametersMap(0, "field", "value");

        final FieldStringValueAlertCondition condition = getCondition(parameters);

        assertNotNull(condition);
        assertNotNull(condition.getDescription());
    }

    @Test
    public void testRunMatchingMessagesInStream() throws Exception {
        final SearchHits searchHits = mock(SearchHits.class);

        final SearchHit searchHit = mock(SearchHit.class);
        final HashMap<String, Object> source = Maps.newHashMap();
        source.put("message", "something is in here");

        when(searchHit.getSource()).thenReturn(source);
        when(searchHit.getIndex()).thenReturn("graylog_test");
        when(searchHits.iterator()).thenReturn(Iterators.singletonIterator(searchHit));

        final HashMap<String, Object> fields = Maps.newHashMap();
        fields.put("index", "graylog_test");
        fields.put("started_at", DateTime.now().minusDays(1).getMillis());

        final Set<IndexRange> indexRanges = Sets.<IndexRange>newHashSet(new IndexRangeImpl(fields));
        final SearchResult searchResult = spy(new SearchResult(searchHits,
                                                           indexRanges,
                                                           "message:something",
                                                           null,
                                                           new TimeValue(100, TimeUnit.MILLISECONDS)));
        when(searchResult.getTotalResults()).thenReturn(1l);
        when(searches.search(
                anyString(),
                anyString(),
                any(RelativeRange.class),
                anyInt(),
                anyInt(),
                any(Sorting.class)))
            .thenReturn(searchResult);
        final FieldStringValueAlertCondition condition = getCondition(getParametersMap(0, "message", "something"));

        alertLastTriggered(-1);

        final AlertCondition.CheckResult result = alertService.triggered(condition);

        assertTriggered(condition, result);
    }


    @Test
    public void testRunNoMatchingMessages() throws Exception {
        final SearchHits searchHits = mock(SearchHits.class);
        when(searchHits.iterator()).thenReturn(Collections.<SearchHit>emptyIterator());

        final HashMap<String, Object> fields = Maps.newHashMap();
        fields.put("index", "graylog_test");
        fields.put("started_at", DateTime.now().minusDays(1).getMillis());

        final Set<IndexRange> indexRanges = Sets.<IndexRange>newHashSet(new IndexRangeImpl(fields));
        final SearchResult searchResult = spy(new SearchResult(searchHits,
                                                               indexRanges,
                                                               "message:something",
                                                               null,
                                                               new TimeValue(100, TimeUnit.MILLISECONDS)));
        when(searches.search(
                anyString(),
                anyString(),
                any(RelativeRange.class),
                anyInt(),
                anyInt(),
                any(Sorting.class)))
                .thenReturn(searchResult);
        final FieldStringValueAlertCondition condition = getCondition(getParametersMap(0, "message", "something"));

        alertLastTriggered(-1);

        final AlertCondition.CheckResult result = alertService.triggered(condition);

        assertNotTriggered(result);
    }

    protected FieldStringValueAlertCondition getCondition(Map<String, Object> parameters) {
        return new FieldStringValueAlertCondition(
                searches,
                mock(Configuration.class),
                stream,
                CONDITION_ID,
                Tools.iso8601(),
                STREAM_CREATOR,
                parameters);
    }

    protected Map<String, Object> getParametersMap(Integer grace, String field, String value) {
        Map<String, Object> parameters = new HashMap<>();

        parameters.put("grace", grace);
        parameters.put("field", field);
        parameters.put("value", value);

        return parameters;
    }

}