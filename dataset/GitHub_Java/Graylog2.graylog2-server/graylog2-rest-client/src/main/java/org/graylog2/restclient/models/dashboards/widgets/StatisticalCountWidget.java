/**
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
 */
package org.graylog2.restclient.models.dashboards.widgets;

import com.google.common.collect.Maps;
import org.graylog2.restclient.lib.timeranges.TimeRange;
import org.graylog2.restclient.models.dashboards.Dashboard;

import java.util.Map;

public class StatisticalCountWidget extends SearchResultCountWidget {
    private final String field;
    private final String statsFunction;

    public StatisticalCountWidget(Dashboard dashboard, String query, TimeRange timerange, String description, boolean trend, boolean lowerIsBetter, String field, String statsFunction) {
        this(dashboard, null, description, 0, query, timerange, trend, lowerIsBetter, field, statsFunction, null);
    }

    public StatisticalCountWidget(Dashboard dashboard, String query, TimeRange timerange, String description, String field, String statsFunction) {
        this(dashboard, null, description, 0, query, timerange, false, false, field, statsFunction, null);
    }

    public StatisticalCountWidget(Dashboard dashboard, String id, String description, int cacheTime, String query, TimeRange timerange, boolean trend, boolean lowerIsBetter, String field, String statsFunction, String creatorUserId) {
        super(Type.STATS_COUNT, dashboard, id, description, cacheTime, query, timerange, trend, lowerIsBetter, creatorUserId);

        if (statsFunction == null || statsFunction.isEmpty()) {
            throw new RuntimeException("Missing statsFunction for widget [" + id + "] on dashboard [" + dashboard.getId() + "].");
        }

        if (field == null || field.isEmpty()) {
            throw new RuntimeException("Missing field for widget [" + id + "] on dashboard [" + dashboard.getId() + "].");
        }

        this.field = field;
        this.statsFunction = statsFunction;
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = Maps.newHashMap();
        config.putAll(super.getConfig());
        config.put("field", field);
        config.put("stats_function", statsFunction);

        return config;
    }

    public String getField() {
        return field;
    }

    public String getStatsFunction() {
        return statsFunction;
    }
}
