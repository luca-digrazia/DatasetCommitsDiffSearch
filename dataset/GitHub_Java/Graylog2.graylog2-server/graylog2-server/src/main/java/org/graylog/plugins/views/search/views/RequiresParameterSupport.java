package org.graylog.plugins.views.search.views;

import org.graylog.plugins.views.Requirement;
import org.graylog.plugins.views.search.Search;
import org.graylog.plugins.views.search.SearchRequiresParameterSupport;
import org.graylog.plugins.views.search.db.SearchDbService;
import org.graylog.plugins.views.Requirement;
import org.graylog.plugins.views.search.Search;
import org.graylog.plugins.views.search.SearchRequiresParameterSupport;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

public class RequiresParameterSupport implements Requirement<ViewDTO> {
    private final SearchDbService searchDbService;
    private final SearchRequiresParameterSupport searchRequiresParameterSupport;

    @Inject
    public RequiresParameterSupport(SearchDbService searchDbService, SearchRequiresParameterSupport searchRequiresParameterSupport) {
        this.searchDbService = searchDbService;
        this.searchRequiresParameterSupport = searchRequiresParameterSupport;
    }

    @Override
    public Map<String, PluginMetadataSummary> test(ViewDTO view) {
        final Optional<Search> optionalSearch = searchDbService.get(view.searchId());
        return optionalSearch.map(searchRequiresParameterSupport::test)
                .orElseThrow(() -> new IllegalStateException("Search " + view.searchId() + " for view " + view + " is missing."));
    }
}
