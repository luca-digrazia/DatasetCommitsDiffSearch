package org.graylog.plugins.enterprise.search.engine;

import org.graylog.plugins.enterprise.search.SearchType;

/**
 *
 * @param <S> the SearchType implementation this handler deals with
 * @param <Q> the backend-specific query builder
 * @param <R> the backend-specific result type
 */
@SuppressWarnings("unchecked")
public interface SearchTypeHandler<S extends SearchType, Q, R> {

    default void generateQueryPart(SearchType searchType, Q queryBuilder) {
        // We need to typecast manually here, because '? extends SearchType' and 'SearchType' are never compatible
        // and thus the compiler won't accept the types at their call sites
        // This allows us to get proper types in the implementing classes instead of having to cast there.
        doGenerateQueryPart((S) searchType, queryBuilder);
    }

    void doGenerateQueryPart(S searchType, Q queryBuilder);

    default SearchType.Result extractResult(SearchType searchType, R queryResult) {
        // see above for the reason for typecasting
        return doExtractResult((S) searchType, queryResult);
    }

    SearchType.Result doExtractResult(S searchType, R queryResult);
}
