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
package org.graylog.plugins.views.search.elasticsearch.searchtypes;

import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.MetricAggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.graylog.plugins.views.search.Query;
import org.graylog.plugins.views.search.SearchJob;
import org.graylog.plugins.views.search.SearchType;
import org.graylog.plugins.views.search.elasticsearch.ESGeneratedQueryContext;
import org.graylog.plugins.views.search.searchtypes.FieldMetric;
import org.graylog.plugins.views.search.Query;
import org.graylog.plugins.views.search.SearchJob;
import org.graylog.plugins.views.search.SearchType;
import org.graylog.plugins.views.search.elasticsearch.ESGeneratedQueryContext;

import javax.annotation.Nullable;
import java.util.Locale;

public class ESFieldMetric implements ESSearchTypeHandler<FieldMetric> {
    @Override
    public void doGenerateQueryPart(SearchJob job, Query query, FieldMetric fieldMetric, ESGeneratedQueryContext queryContext) {
        final SearchSourceBuilder queryBuilder = queryContext.searchSourceBuilder(fieldMetric);
        switch (fieldMetric.operation()) {
            case AVG:
                queryBuilder.aggregation(AggregationBuilders.avg(aggName(fieldMetric)).field(fieldMetric.field()));
                break;
            case CARDINALITY:
                queryBuilder.aggregation(AggregationBuilders.cardinality(aggName(fieldMetric)).field(fieldMetric.field()));
                break;
            case COUNT:
                queryBuilder.aggregation(AggregationBuilders.count(aggName(fieldMetric)).field(fieldMetric.field()));
                break;
            case MAX:
                queryBuilder.aggregation(AggregationBuilders.max(aggName(fieldMetric)).field(fieldMetric.field()));
                break;
            case MIN:
                queryBuilder.aggregation(AggregationBuilders.min(aggName(fieldMetric)).field(fieldMetric.field()));
                break;
            case SUM:
                queryBuilder.aggregation(AggregationBuilders.sum(aggName(fieldMetric)).field(fieldMetric.field()));
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + fieldMetric.operation().toString());
        }
    }

    @Override
    public SearchType.Result doExtractResult(SearchJob job, Query query, FieldMetric fieldMetric, SearchResult queryResult, MetricAggregation aggregations, ESGeneratedQueryContext queryContext) {
        final String id = fieldMetric.id();
        final SearchType.Result result;

        switch (fieldMetric.operation()) {
            case AVG:
                result = doubleResult(id, aggregations.getAvgAggregation(aggName(fieldMetric)).getAvg());
                break;
            case CARDINALITY:
                result = longResult(id, aggregations.getCardinalityAggregation(aggName(fieldMetric)).getCardinality());
                break;
            case COUNT:
                result = longResult(id, aggregations.getValueCountAggregation(aggName(fieldMetric)).getValueCount());
                break;
            case MAX:
                result = doubleResult(id, aggregations.getMaxAggregation(aggName(fieldMetric)).getMax());
                break;
            case MIN:
                result = doubleResult(id, aggregations.getMinAggregation(aggName(fieldMetric)).getMin());
                break;
            case SUM:
                result = doubleResult(id, aggregations.getSumAggregation(aggName(fieldMetric)).getSum());
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + fieldMetric.operation().toString());
        }

        return result;
    }

    private SearchType.Result doubleResult(String id, @Nullable Double value) {
        // The value can be null if an aggregation is done on an invalid field type. (e.g. AVG on a non-numeric field)
        // TODO: Implement error metadata for search type results - https://github.com/Graylog2/graylog-plugin-enterprise/issues/50
        if (value == null) {
            return FieldMetric.DoubleResult.builder().id(id).value(0.0).build();
        }
        return FieldMetric.DoubleResult.builder()
                .id(id)
                .value(value)
                .build();
    }

    private SearchType.Result longResult(String id, @Nullable Long value) {
        // The value can be null if an aggregation is done on an invalid field type. (e.g. AVG on a non-numeric field)
        // TODO: Implement error metadata for search type results - https://github.com/Graylog2/graylog-plugin-enterprise/issues/50
        if (value == null) {
            return FieldMetric.LongResult.builder().id(id).value(0L).build();
        }
        return FieldMetric.LongResult.builder()
                .id(id)
                .value(value)
                .build();
    }

    private String aggName(FieldMetric fieldMetric) {
        return String.format(Locale.ENGLISH, "field-metric-%s", fieldMetric.id());
    }
}
