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

package controllers.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import controllers.AuthenticatedController;
import lib.security.RestPermissions;
import org.graylog2.rest.models.dashboards.requests.AddWidgetRequest;
import org.graylog2.restclient.lib.APIException;
import org.graylog2.restclient.lib.ApiClient;
import org.graylog2.restclient.lib.timeranges.InvalidRangeParametersException;
import org.graylog2.restclient.lib.timeranges.TimeRange;
import org.graylog2.restclient.models.User;
import org.graylog2.restclient.models.api.requests.dashboards.CreateDashboardRequest;
import org.graylog2.restclient.models.api.requests.dashboards.UpdateDashboardRequest;
import org.graylog2.restclient.models.api.requests.dashboards.UserSetWidgetPositionsRequest;
import org.graylog2.restclient.models.api.responses.dashboards.DashboardWidgetValueResponse;
import org.graylog2.restclient.models.dashboards.Dashboard;
import org.graylog2.restclient.models.dashboards.DashboardService;
import org.graylog2.restclient.models.dashboards.widgets.ChartWidget;
import org.graylog2.restclient.models.dashboards.widgets.DashboardWidget;
import org.graylog2.restclient.models.dashboards.widgets.FieldChartWidget;
import org.graylog2.restclient.models.dashboards.widgets.QuickvaluesWidget;
import org.graylog2.restclient.models.dashboards.widgets.SearchResultChartWidget;
import org.graylog2.restclient.models.dashboards.widgets.SearchResultCountWidget;
import org.graylog2.restclient.models.dashboards.widgets.StackedChartWidget;
import org.graylog2.restclient.models.dashboards.widgets.StatisticalCountWidget;
import org.graylog2.restclient.models.dashboards.widgets.StreamSearchResultCountWidget;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Duration;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.MutableDateTime;
import org.joda.time.Weeks;
import play.Logger;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static views.helpers.Permissions.isPermitted;

public class DashboardsApiController extends AuthenticatedController {
    private final DashboardService dashboardService;

    @Inject
    public DashboardsApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public Result index() {
        try {
            Map<String, Object> result = Maps.newHashMap();
            for (Dashboard d : dashboardService.getAll()) {
                Map<String, String> dashboard = Maps.newHashMap();

                dashboard.put("title", d.getTitle());
                dashboard.put("description", d.getDescription());
                dashboard.put("created_by", (d.getCreatorUser() == null) ? null : d.getCreatorUser().getName());

                result.put(d.getId(), dashboard);
            }

            return ok(Json.toJson(result));
        } catch (APIException e) {
            String message = "Could not get dashboards. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    public Result listWritable() {
        try {
            Map<String, Object> result = Maps.newHashMap();
            for (Dashboard d : getAllWritable(currentUser())) {
                Map<String, String> dashboard = Maps.newHashMap();

                dashboard.put("title", d.getTitle());
                dashboard.put("description", d.getDescription());
                dashboard.put("created_by", (d.getCreatorUser() == null) ? null : d.getCreatorUser().getName());

                result.put(d.getId(), dashboard);
            }

            return ok(Json.toJson(result));
        } catch (APIException e) {
            String message = "Could not get dashboards. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    private Iterable<? extends Dashboard> getAllWritable(User user) throws IOException, APIException {
        List<Dashboard> writable = Lists.newArrayList();

        for (Dashboard dashboard : dashboardService.getAll()) {
            if (isPermitted(user, RestPermissions.DASHBOARDS_EDIT, dashboard.getId())) {
                writable.add(dashboard);
            }
        }

        return writable;
    }

    public Result create() {
        if (!isPermitted(RestPermissions.DASHBOARDS_CREATE)) {
            return forbidden();
        }

        CreateDashboardRequest cdr = Json.fromJson(request().body().asJson(), CreateDashboardRequest.class);
        if (isNullOrEmpty(cdr.getTitle()) || isNullOrEmpty(cdr.getDescription())) {
            return badRequest("Missing field");
        }

        try {
            final String dashboardId = dashboardService.create(cdr);

            return ok(Json.toJson(dashboardId));
        } catch (APIException e) {
            String message = "Could not create dashboard. We expected HTTP 201, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    public Result update(String id) {
        if (!isPermitted(RestPermissions.DASHBOARDS_EDIT, id)) {
            return forbidden();
        }

        UpdateDashboardRequest udr = Json.fromJson(request().body().asJson(), UpdateDashboardRequest.class);
        if (isNullOrEmpty(udr.title) || isNullOrEmpty(udr.description)) {
            return badRequest("Missing field");
        }

        try {
            Dashboard dashboard = dashboardService.get(id);
            dashboard.update(udr);

            return ok();
        } catch (APIException e) {
            String message = "Could not update dashboard. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    public Result setWidgetPositions(String dashboardId) {

        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            UserSetWidgetPositionsRequest positions = Json.fromJson(request().body().asJson(), UserSetWidgetPositionsRequest.class);
            dashboard.setWidgetPositions(positions);
        } catch (APIException e) {
            String message = "Could not update positions. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }

        return ok();
    }

    public Result widget(String dashboardId, String widgetId) {
        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            DashboardWidget widget = dashboard.getWidget(widgetId);
            if (widget == null) {
                return notFound();
            }

            Map<String, Object> result = Maps.newHashMap();
            result.put("type", widget.getType());
            result.put("id", widget.getId());
            result.put("dashboard_id", widget.getDashboard().getId());
            result.put("description", widget.getDescription());
            result.put("cache_time", widget.getCacheTime());
            result.put("creator_user_id", widget.getCreatorUserId());
            result.put("config", widget.getConfig());

            return ok(Json.toJson(result));
        } catch (APIException e) {
            String message = "Could not get dashboard. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    public Result widgetValue(String dashboardId, String widgetId, int resolution) {
        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            DashboardWidget widget = dashboard.getWidget(widgetId);
            if (widget == null) {
                return notFound();
            }
            DashboardWidgetValueResponse widgetValue = widget.getValue(api());

            Object resultValue;
            if (widget instanceof ChartWidget) {
                resultValue = formatWidgetValueResults(resolution, widget, widgetValue);
            } else {
                resultValue = widgetValue.result;
            }

            Map<String, Object> result = Maps.newHashMap();
            result.put("result", resultValue);
            result.put("took_ms", widgetValue.tookMs);
            result.put("calculated_at", widgetValue.calculatedAt);
            result.put("time_range", widgetValue.computationTimeRange);

            return ok(Json.toJson(result));
        } catch (APIException e) {
            String message = "Could not get dashboard. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    protected List<Map<String, Object>> formatWidgetValueResults(final int maxDataPoints,
                                                                 final DashboardWidget widget,
                                                                 final DashboardWidgetValueResponse widgetValue) {
        final Map<String, Object> widgetConfig = widget.getConfig();
        final String interval = widgetConfig.containsKey("interval") ? (String) widgetConfig.get("interval") : "minute";
        final boolean allQuery = widgetConfig.get("range_type").equals("relative") && widgetConfig.get("range").equals("0");

        if (widget instanceof StackedChartWidget) {
            final List widgetResults = (List) widgetValue.result;
            final List<Map<String, String>> series = (List<Map<String, String>>) widget.getConfig().get("series");
            final ImmutableList.Builder<Map<String, Object>> formattedWidgetResults = ImmutableList.builder();
            int i = 0;

            for (Object widgetResult : widgetResults) {
                final Map<String, String> currentSeries = series.get(i);

                formattedWidgetResults.addAll(formatWidgetValueResults(maxDataPoints,
                        widgetResult,
                        currentSeries.get("statistical_function"),
                        interval,
                        widgetValue.computationTimeRange,
                        allQuery,
                        i + 1));

                i++;
            }

            return formattedWidgetResults.build();
        } else {
            return formatWidgetValueResults(maxDataPoints,
                    widgetValue.result,
                    (String) widgetConfig.get("valuetype"),
                    interval,
                    widgetValue.computationTimeRange,
                    allQuery,
                    null);
        }
    }

    // TODO: Extract common parts of this and the similar method on SearchApiController
    protected List<Map<String, Object>> formatWidgetValueResults(final int maxDataPoints,
                                                                 final Object resultValue,
                                                                 final String functionType,
                                                                 final String interval,
                                                                 final Map<String, Object> timeRange,
                                                                 final boolean allQuery,
                                                                 final Integer seriesNo) {
        final ImmutableList.Builder<Map<String, Object>> pointListBuilder = ImmutableList.builder();

        if (resultValue instanceof Map) {
            final Map<?, ?> resultMap = (Map) resultValue;

            DateTime from;
            if (allQuery) {
                String firstTimestamp = (String) resultMap.entrySet().iterator().next().getKey();
                from = new DateTime(Long.parseLong(firstTimestamp) * 1000, DateTimeZone.UTC);
            } else {
                from = DateTime.parse((String) timeRange.get("from")).withZone(DateTimeZone.UTC);
            }
            final DateTime to = DateTime.parse((String) timeRange.get("to"));
            final MutableDateTime currentTime = new MutableDateTime(from);

            final Duration step = estimateIntervalStep(interval);
            final int dataPoints = (int) ((to.getMillis() - from.getMillis()) / step.getMillis());

            // using the absolute value guarantees, that there will always be enough values for the given resolution
            final int factor = (maxDataPoints != -1 && dataPoints > maxDataPoints) ? dataPoints / maxDataPoints : 1;

            int index = 0;
            floorToBeginningOfInterval(interval, currentTime);
            while (currentTime.isBefore(to) || currentTime.isEqual(to)) {
                if (index % factor == 0) {
                    String timestamp = Long.toString(currentTime.getMillis() / 1000);
                    Object value = resultMap.get(timestamp);
                    if (functionType != null && value != null) {
                        value = ((Map) value).get(functionType);
                    }
                    Object result = value == null ? 0 : value;
                    final ImmutableMap.Builder<String, Object> pointBuilder = ImmutableMap.<String, Object>builder()
                            .put("x", Long.parseLong(timestamp))
                            .put("y", result);

                    if (seriesNo != null) {
                        pointBuilder.put("series", seriesNo);
                    }

                    pointListBuilder.add(pointBuilder.build());
                }
                index++;
                nextStep(interval, currentTime);
            }
        }
        return pointListBuilder.build();
    }

    private void nextStep(String interval, MutableDateTime currentTime) {
        switch (interval) {
            case "minute":
                currentTime.addMinutes(1);
                break;
            case "hour":
                currentTime.addHours(1);
                break;
            case "day":
                currentTime.addDays(1);
                break;
            case "week":
                currentTime.addWeeks(1);
                break;
            case "month":
                currentTime.addMonths(1);
                break;
            case "quarter":
                currentTime.addMonths(3);
                break;
            case "year":
                currentTime.addYears(1);
                break;
            default:
                throw new IllegalArgumentException("Invalid duration specified: " + interval);
        }
    }

    private void floorToBeginningOfInterval(String interval, MutableDateTime currentTime) {
        switch (interval) {
            case "minute":
                currentTime.minuteOfDay().roundFloor();
                break;
            case "hour":
                currentTime.hourOfDay().roundFloor();
                break;
            case "day":
                currentTime.dayOfMonth().roundFloor();
                break;
            case "week":
                currentTime.weekOfWeekyear().roundFloor();
                break;
            case "month":
                currentTime.monthOfYear().roundFloor();
                break;
            case "quarter":
                // set the month to the beginning of the quarter
                int currentQuarter = ((currentTime.getMonthOfYear() - 1) / 3);
                int startOfQuarter = (currentQuarter * 3) + 1;
                currentTime.setMonthOfYear(startOfQuarter);
                currentTime.monthOfYear().roundFloor();
                break;
            case "year":
                currentTime.yearOfCentury().roundFloor();
                break;
            default:
                throw new IllegalArgumentException("Invalid duration specified: " + interval);
        }
    }

    private Duration estimateIntervalStep(String interval) {
        Duration step;
        switch (interval) {
            case "minute":
                step = Minutes.ONE.toStandardDuration();
                break;
            case "hour":
                step = Hours.ONE.toStandardDuration();
                break;
            case "day":
                step = Days.ONE.toStandardDuration();
                break;
            case "week":
                step = Weeks.ONE.toStandardDuration();
                break;
            case "month":
                step = Days.days(31).toStandardDuration();
                break;
            case "quarter":
                step = Days.days(31 * 3).toStandardDuration();
                break;
            case "year":
                step = Days.days(365).toStandardDuration();
                break;
            default:
                throw new IllegalArgumentException("Invalid duration specified: " + interval);
        }
        return step;
    }

    @BodyParser.Of(BodyParser.Json.class)
    public Result addWidget(String dashboardId) {
        try {
            final AddWidgetRequest request = Json.fromJson(request().body().asJson(), AddWidgetRequest.class);

            String query = (String) request.config().get("query");
            String rangeType = (String) request.config().get("range_type");
            String description = request.description();

            Dashboard dashboard = dashboardService.get(dashboardId);

            // Determine timerange type.
            TimeRange timerange;
            try {
                int relative = 0;
                Object relativeTimeRange = request.config().get("relative");
                if (relativeTimeRange != null) {
                    relative = (Integer) relativeTimeRange;
                }

                timerange = TimeRange.factory(
                        rangeType, relative,
                        (String) request.config().get("from"),
                        (String) request.config().get("to"),
                        (String) request.config().get("keyword"));
            } catch (InvalidRangeParametersException e2) {
                return status(400, views.html.errors.error.render("Invalid range parameters provided.", e2, request()));
            } catch (IllegalArgumentException e1) {
                return status(400, views.html.errors.error.render("Invalid range type provided.", e1, request()));
            }

            String streamId = "";

            if (request.config().containsKey("streamId")) {
                streamId = (String) request.config().get("streamId");
            }

            final DashboardWidget widget;
            try {
                final DashboardWidget.Type widgetType = DashboardWidget.Type.valueOf(request.type());
                final Map<String, Boolean> trendInformation;

                switch (widgetType) {
                    case SEARCH_RESULT_COUNT:
                        trendInformation = this.extractCountTrendInformation(request.config());
                        if (trendInformation.get("trend")) {
                            if (!rangeType.equals("relative")) {
                                Logger.error("Cannot add search count widget with trend on a non relative time range");
                                return badRequest();
                            }
                            widget = new SearchResultCountWidget(dashboard, query, timerange, description, trendInformation.get("trend"), trendInformation.get("lowerIsBetter"));
                        } else {
                            widget = new SearchResultCountWidget(dashboard, query, timerange, description);
                        }
                        break;
                    case STREAM_SEARCH_RESULT_COUNT:
                        if (!canReadStream(streamId)) return unauthorized();
                        trendInformation = this.extractCountTrendInformation(request.config());
                        if (trendInformation.get("trend")) {
                            if (!rangeType.equals("relative")) {
                                Logger.error("Cannot add search result count widget with trend on a non relative time range");
                                return badRequest();
                            }
                            widget = new StreamSearchResultCountWidget(dashboard, query, timerange, description, trendInformation.get("trend"), trendInformation.get("lowerIsBetter"), streamId);
                        } else {
                            widget = new StreamSearchResultCountWidget(dashboard, query, timerange, description, streamId);
                        }
                        break;
                    case FIELD_CHART:
                        final Map<String, Object> config = ImmutableMap.of(
                                "field", request.config().get("field"),
                                "valuetype", request.config().get("valuetype"),
                                "renderer", request.config().get("renderer"),
                                "interpolation", request.config().get("interpolation"),
                                "interval", request.config().get("interval")
                        );
                        if (!canReadStream(streamId)) return unauthorized();

                        widget = new FieldChartWidget(dashboard, query, timerange, description, streamId, config);
                        break;
                    case QUICKVALUES:
                        final Boolean showPieChart = request.config().containsKey("show_pie_chart") && request.config().get("show_pie_chart").equals(true);
                        final Boolean showDataTable = request.config().containsKey("show_data_table") && request.config().get("show_data_table").equals(true);
                        if (!canReadStream(streamId)) return unauthorized();
                        widget = new QuickvaluesWidget(dashboard, query, timerange, (String) request.config().get("field"), description, showPieChart, showDataTable, streamId);
                        break;
                    case SEARCH_RESULT_CHART:
                        if (!canReadStream(streamId)) return unauthorized();
                        widget = new SearchResultChartWidget(dashboard, query, timerange, description, streamId, (String) request.config().get("interval"));
                        break;
                    case STATS_COUNT:
                        final String field = (String) request.config().get("field");
                        final String statsFunction = (String) request.config().get("statsFunction");
                        trendInformation = this.extractCountTrendInformation(request.config());
                        if (trendInformation.get("trend")) {
                            if (!rangeType.equals("relative")) {
                                Logger.error("Cannot add statistical count widget with trend on a non relative time range");
                                return badRequest();
                            }
                            widget = new StatisticalCountWidget(dashboard, query, timerange, description, trendInformation.get("trend"), trendInformation.get("lowerIsBetter"), field, statsFunction, streamId);
                        } else {
                            widget = new StatisticalCountWidget(dashboard, query, timerange, description, field, statsFunction, streamId);
                        }
                        break;
                    case STACKED_CHART:
                        Map<String, Object> requestConfig = request.config();
                        String renderer = (String) requestConfig.get("renderer");
                        String interpolation = (String) requestConfig.get("interpolation");
                        String interval = (String) requestConfig.get("interval");
                        List<Map<String, Object>> series = (List<Map<String, Object>>) requestConfig.get("series");

                        if (!canReadStream(streamId)) return unauthorized();

                        widget = new StackedChartWidget(dashboard, timerange, description, streamId, renderer, interpolation, interval, series);
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                Logger.error("No such widget type: " + request.type());
                return badRequest();
            }

            dashboard.addWidget(widget);

            return created();
        } catch (APIException e) {
            String message = "Could not add widget. We got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    private Map<String, Boolean> extractCountTrendInformation(Map<String, Object> config) {
        final Boolean trend = config.containsKey("trend") && config.get("trend").equals(true);
        final Boolean lowerIsBetter = trend && config.containsKey("lower_is_better") && config.get("lower_is_better").equals(true);
        return ImmutableMap.of("trend", trend, "lowerIsBetter", lowerIsBetter);
    }

    private boolean canReadStream(String streamId) {
        if (streamId == null) return true;
        return isPermitted(RestPermissions.STREAMS_READ, streamId);
    }

    public Result removeWidget(String dashboardId, String widgetId) {
        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            dashboard.removeWidget(widgetId);

            return noContent();
        } catch (APIException e) {
            String message = "Could not get dashboard. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    @BodyParser.Of(BodyParser.Json.class)
    public Result updateWidget(String dashboardId, String widgetId) {
        if (!isPermitted(RestPermissions.DASHBOARDS_EDIT, dashboardId)) {
            return redirect(controllers.routes.StartpageController.redirect());
        }

        final AddWidgetRequest addWidgetRequest = Json.fromJson(request().body().asJson(), AddWidgetRequest.class);

        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            DashboardWidget widget = dashboard.getWidget(widgetId);

            widget.updateWidget(api(), addWidgetRequest);

            return ok().as(Http.MimeTypes.JSON);
        } catch (APIException e) {
            String message = "Could not get widget. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    @BodyParser.Of(BodyParser.FormUrlEncoded.class)
    public Result updateWidgetDescription(String dashboardId, String widgetId) {
        String newDescription = flattenFormUrlEncoded(request().body().asFormUrlEncoded()).get("description");

        if (newDescription == null || newDescription.trim().isEmpty()) {
            return badRequest();
        }

        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            DashboardWidget widget = dashboard.getWidget(widgetId);

            widget.updateDescription(api(), newDescription.trim());

            return ok().as(Http.MimeTypes.JSON);
        } catch (APIException e) {
            String message = "Could not get widget. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

    @BodyParser.Of(BodyParser.FormUrlEncoded.class)
    public Result updateWidgetCacheTime(String dashboardId, String widgetId) {
        String newCacheTimeS = flattenFormUrlEncoded(request().body().asFormUrlEncoded()).get("cacheTime");

        if (newCacheTimeS == null) {
            return badRequest();
        }

        int newCacheTime;
        try {
            newCacheTime = Integer.parseInt(newCacheTimeS);
        } catch (NumberFormatException e) {
            return badRequest();
        }

        try {
            Dashboard dashboard = dashboardService.get(dashboardId);
            DashboardWidget widget = dashboard.getWidget(widgetId);

            widget.updateCacheTime(api(), newCacheTime);

            return ok().as(Http.MimeTypes.JSON);
        } catch (APIException e) {
            String message = "Could not get widget. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        }
    }

}
