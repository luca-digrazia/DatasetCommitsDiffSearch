package org.graylog.plugins.views.migrations.V20191125144500_MigrateDashboardsToViewsSupport.dashboardwidgets;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.graylog.plugins.views.migrations.V20191125144500_MigrateDashboardsToViewsSupport.ViewWidget;

import java.util.Collections;
import java.util.Optional;

public interface WidgetConfigWithQueryAndStreams extends WidgetConfig {
    @JsonProperty
    String query();

    @JsonProperty
    Optional<String> streamId();

    default ViewWidget.Builder createViewWidget(String id) {
        final ViewWidget.Builder viewWidgetBuilder = ViewWidget.builder()
                .id(id)
                .query(query())
                .timerange(timerange());
        return streamId().map(streamId -> viewWidgetBuilder.streams(Collections.singleton(streamId))).orElse(viewWidgetBuilder);
    }
}
