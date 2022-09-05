package org.nlpcn.es4sql.query;

import java.util.List;
import java.util.ArrayList;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.nlpcn.es4sql.domain.Field;
import org.nlpcn.es4sql.domain.MethodField;
import org.nlpcn.es4sql.domain.Order;
import org.nlpcn.es4sql.domain.Select;
import org.nlpcn.es4sql.domain.Where;
import org.nlpcn.es4sql.exception.SqlParseException;
import org.nlpcn.es4sql.query.maker.FilterMaker;
import org.nlpcn.es4sql.query.maker.QueryMaker;

public class DefaultQuery extends Query {

	public DefaultQuery(Client client, Select select) {
		super(client, select);
	}

	@Override
	protected SearchRequestBuilder _explan() throws SqlParseException {

		// set where
		Where where = select.getWhere();

		if (where != null) {
			if (select.isQuery) {
				BoolQueryBuilder boolQuery = QueryMaker.explan(where);
				request.setQuery(boolQuery);
			} else {
				// TODO use regular filter instead of postFilter?
				BoolFilterBuilder boolFilter = FilterMaker.explan(where);
				request.setPostFilter(boolFilter);
			}
		}

		// add source filtering.
		if (select.getFields().size() > 0) {
			setFields(request, select.getFields());
		}

		request.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

		request.setFrom(select.getOffset());

		if (select.getRowCount() > -1) {
			request.setSize(select.getRowCount());
		}

		// add order
		for (Order order : select.getOrderBys()) {
			request.addSort(order.getName(), SortOrder.valueOf(order.getType()));
		}
		return request;
	}


	/**
	 * Set source filtering on a search request.
	 * @param request the search request to filter its source.
	 * @param fields list of fields to source filter.
	 */
	private void setFields(SearchRequestBuilder request, List<Field> fields) {
		ArrayList<String> includeFields = new ArrayList<String>();

		for (Field field : fields) {
			if (field instanceof Field) {
				includeFields.add(field.getName());
			}
		}

		request.setFetchSource(includeFields.toArray(new String[includeFields.size()]), null);
	}
}
