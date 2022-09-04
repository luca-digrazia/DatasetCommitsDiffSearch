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
package models.dashboards;

import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import lib.APIException;
import lib.ApiClient;
import lib.timeranges.InvalidRangeParametersException;
import models.User;
import models.UserService;
import models.api.requests.dashboards.AddWidgetRequest;
import models.api.responses.dashboards.DashboardSummaryResponse;
import models.api.responses.dashboards.DashboardWidgetResponse;
import models.dashboards.widgets.DashboardWidget;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;

import java.io.IOException;
import java.util.List;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class Dashboard {

    private static final Logger LOG = LoggerFactory.getLogger(Dashboard.class);

    public interface Factory {
        Dashboard fromSummaryResponse(DashboardSummaryResponse dsr);
    }

    private final String id;
    private final String title;
    private final String description;
    private final DateTime createdAt;
    private final User creatorUser;

    private List<DashboardWidget> widgets;

    private final ApiClient api;

    @AssistedInject
    private Dashboard(UserService userService, ApiClient api, @Assisted DashboardSummaryResponse dsr) {
        this.id = dsr.id;
        this.title = dsr.title;
        this.description = dsr.description;
        this.createdAt = DateTime.parse(dsr.createdAt);
        this.creatorUser = userService.load(dsr.creatorUserId);
        this.api = api;
        this.widgets = parseWidgets(dsr.widgets);
    }

    public void addWidget(DashboardWidget widget, User user) throws APIException, IOException {
        AddWidgetRequest request = new AddWidgetRequest(widget, user);

        api.post().path("/dashboards/{0}/widgets", id)
                .body(request)
                .expect(Http.Status.CREATED)
                .execute();
    }

    public List<DashboardWidget> getWidgets() {
        return widgets;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public DateTime getCreatedAt() {
        return createdAt;
    }

    public User getCreatorUser() {
        return creatorUser;
    }

    private List<DashboardWidget> parseWidgets(List<DashboardWidgetResponse> widgetDefinitions) {
        List<DashboardWidget> widgets = Lists.newArrayList();

        for (DashboardWidgetResponse w : widgetDefinitions) {
            try {
                widgets.add(DashboardWidget.factory(w));
            } catch (DashboardWidget.NoSuchWidgetTypeException e) {
                LOG.error("Skipping not supported widget: [{}]", w.type, e);
                continue;
            } catch (InvalidRangeParametersException e) {
                LOG.error("Skipping widget with invalid timerange parameters: [{}]", w.id, e);
                continue;
            }
        }

        return widgets;
    }

}
