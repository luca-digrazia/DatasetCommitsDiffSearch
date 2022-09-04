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
package org.graylog2.restclient.models.api.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class DashboardSummaryResponse {
    public String id;
    public String title;
    public String description;

    public List<DashboardWidgetResponse> widgets;
    public Map<String, WidgetPositionResponse> positions;

    @JsonProperty("created_at")
    public String createdAt;

    @JsonProperty("creator_user_id")
    public String creatorUserId;

    @JsonProperty("content_pack")
    public String contentPack;
}
