package org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.pivot.buckets;

import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.graylog.plugins.enterprise.search.Query;
import org.graylog.plugins.enterprise.search.elasticsearch.ESGeneratedQueryContext;
import org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.pivot.ESPivot;
import org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.pivot.ESPivotBucketSpecHandler;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.Pivot;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.PivotSort;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.SeriesSort;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.SeriesSpec;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.SortSpec;
import org.graylog.plugins.enterprise.search.searchtypes.pivot.buckets.Values;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ESValuesHandler extends ESPivotBucketSpecHandler<Values, TermsAggregation> {
    @Nonnull
    @Override
    public Optional<AggregationBuilder> doCreateAggregation(String name, Pivot pivot, Values valuesSpec, ESPivot searchTypeHandler, ESGeneratedQueryContext esGeneratedQueryContext, Query query) {
        final List<Terms.Order> ordering = orderListForPivot(pivot, valuesSpec, esGeneratedQueryContext);
        final TermsAggregationBuilder builder = AggregationBuilders.terms(name)
                .minDocCount(1)
                .field(valuesSpec.field())
                .order(ordering.isEmpty() ? Collections.singletonList(Terms.Order.count(false)) : ordering)
                .size(valuesSpec.limit());
        record(esGeneratedQueryContext, pivot, valuesSpec, name, TermsAggregation.class);
        return Optional.of(builder);
    }

    private List<Terms.Order> orderListForPivot(Pivot pivot, Values valuesSpec, ESGeneratedQueryContext esGeneratedQueryContext) {
        return pivot.sort()
                .stream()
                .map(sortSpec -> {
                    if (sortSpec instanceof PivotSort && valuesSpec.field().equals(sortSpec.field())) {
                        return Terms.Order.term(sortSpec.direction().equals(SortSpec.Direction.Ascending));
                    }
                    if (sortSpec instanceof SeriesSort) {
                        final Optional<SeriesSpec> matchingSeriesSpec = pivot.series()
                                .stream()
                                .filter(series -> series.literal().equals(sortSpec.field()))
                                .findFirst();
                        return matchingSeriesSpec
                                .map(seriesSpec -> {
                                    if (seriesSpec.literal().equals("count()")) {
                                        return Terms.Order.count(sortSpec.direction().equals(SortSpec.Direction.Ascending));
                                    }
                                    return Terms.Order.aggregation(esGeneratedQueryContext.seriesName(seriesSpec, pivot), sortSpec.direction().equals(SortSpec.Direction.Ascending));
                                })
                                .orElse(null);
                    }

                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Stream<Bucket> doHandleResult(Pivot pivot, Values bucketSpec,
                                         SearchResult searchResult,
                                         TermsAggregation termsAggregation,
                                         ESPivot searchTypeHandler,
                                         ESGeneratedQueryContext esGeneratedQueryContext) {
        return termsAggregation.getBuckets().stream()
                .map(entry -> Bucket.create(entry.getKeyAsString(), entry));
    }
}
