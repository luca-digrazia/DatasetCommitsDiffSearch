package org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.pivot;

import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.Aggregation;
import io.searchbox.core.search.aggregation.MetricAggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.graylog.plugins.enterprise.search.elasticsearch.ESGeneratedQueryContext;
import org.graylog.plugins.enterprise.search.engine.GeneratedQueryContext;
import org.graylog.plugins.enterprise.search.engine.SearchTypeHandler;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.Pivot;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.PivotSpec;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.SeriesSpec;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.SeriesSpecHandler;

import java.util.stream.Stream;

public abstract class ESPivotSeriesSpecHandler<SPEC_TYPE extends SeriesSpec, AGGREGATION_RESULT extends Aggregation>
        implements SeriesSpecHandler<SPEC_TYPE, AggregationBuilder, SearchResult, AGGREGATION_RESULT, ESPivot, ESGeneratedQueryContext> {

    protected ESPivot.AggTypes aggTypes(ESGeneratedQueryContext queryContext, Pivot pivot) {
        return (ESPivot.AggTypes) queryContext.contextMap().get(pivot.id());
    }

    protected void record(ESGeneratedQueryContext queryContext, Pivot pivot, PivotSpec spec, String name, Class<? extends Aggregation> aggregationClass) {
        aggTypes(queryContext, pivot).record(spec, name, aggregationClass);
    }

    protected Aggregation extractAggregationFromResult(Pivot pivot, PivotSpec spec, MetricAggregation aggregations, ESGeneratedQueryContext queryContext) {
        return aggTypes(queryContext, pivot).getSubAggregation(spec, aggregations);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Stream<Value> handleResult(Pivot pivot, SeriesSpec seriesSpec, Object queryResult, Object aggregationResult, SearchTypeHandler searchTypeHandler, GeneratedQueryContext queryContext) {
        return doHandleResult(pivot, (SPEC_TYPE) seriesSpec, (SearchResult) queryResult, (AGGREGATION_RESULT) aggregationResult, (ESPivot) searchTypeHandler, (ESGeneratedQueryContext) queryContext);
    }

    @Override
    public abstract Stream<Value> doHandleResult(Pivot pivot, SPEC_TYPE seriesSpec, SearchResult searchResult, AGGREGATION_RESULT aggregation_result, ESPivot searchTypeHandler, ESGeneratedQueryContext queryContext);

    public static class Value {

        private final String id;
        private final String key;
        private final Object value;

        public Value(String id, String key, Object value) {
            this.id = id;
            this.key = key;
            this.value = value;
        }

        public static Value create(String id, String key, Object value) {
            return new Value(id, key, value);
        }

        public String id() {
            return id;
        }

        public String key() {
            return key;
        }

        public Object value() {
            return value;
        }
    }
}
