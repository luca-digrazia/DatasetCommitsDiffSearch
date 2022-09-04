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
package org.graylog2.dashboards;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.graylog2.Core;
import org.graylog2.dashboards.widgets.DashboardWidget;
import org.graylog2.database.NotFoundException;
import org.graylog2.database.Persisted;
import org.graylog2.database.ValidationException;
import org.graylog2.database.validators.DateValidator;
import org.graylog2.database.validators.FilledStringValidator;
import org.graylog2.database.validators.Validator;
import org.graylog2.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.plugin.Tools;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class Dashboard extends Persisted {

    private static final Logger LOG = LoggerFactory.getLogger(Dashboard.class);

    public static final String COLLECTION = "dashboards";

    public static final String EMBEDDED_WIDGETS = "widgets";

    private Map<String, DashboardWidget> widgets = Maps.newHashMap();

    public Dashboard(Map<String, Object> fields, Core core) {
        super(core, fields);
    }

    protected Dashboard(ObjectId id, Map<String, Object> fields, Core core) {
        super(core, id, fields);
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    public static Dashboard load(ObjectId id, Core core) throws NotFoundException {
        BasicDBObject o = (BasicDBObject) get(id, core, COLLECTION);

        if (o == null) {
            throw new NotFoundException();
        }

        return new Dashboard((ObjectId) o.get("_id"), o.toMap(), core);
    }

    public static List<Dashboard> all(Core core) {
        List<Dashboard> dashboards = Lists.newArrayList();

        List<DBObject> results = query(new BasicDBObject(), core, COLLECTION);
        for (DBObject o : results) {
            Map<String, Object> fields = o.toMap();
            Dashboard dashboard = new Dashboard((ObjectId) o.get("_id"), fields, core);

            // Add all widgets of this dashboard.
            if (fields.containsKey(EMBEDDED_WIDGETS)) {
                for (BasicDBObject widgetFields : (List<BasicDBObject>) fields.get(EMBEDDED_WIDGETS)) {
                    DashboardWidget widget = null;
                    try {
                        widget = DashboardWidget.fromPersisted(core, widgetFields);
                    } catch (DashboardWidget.NoSuchWidgetTypeException e) {
                        LOG.error("No such widget type: [{}] - Dashboard: [" + dashboard.getId() + "]", widgetFields.get("type"), e);
                        continue;
                    } catch (InvalidRangeParametersException e) {
                        LOG.error("Invalid range parameters of widget in dashboard: [{}]", dashboard.getId(), e);
                        continue;
                    }
                    dashboard.addPersistedWidget(widget);
                }
            }


            dashboards.add(dashboard);
        }

        return dashboards;
    }

    public void addPersistedWidget(DashboardWidget widget) {
        widgets.put(widget.getId(), widget);
    }

    public void addWidget(DashboardWidget widget) throws ValidationException {
        embed(EMBEDDED_WIDGETS, widget);
        widgets.put(widget.getId(), widget);
    }

    public void removeWidget(String widgetId) {
        removeEmbedded(EMBEDDED_WIDGETS, widgetId);
        widgets.remove(widgetId);
    }

    public DashboardWidget getWidget(String widgetId) {
        return widgets.get(widgetId);
    }

    @Override
    protected Map<String, Validator> getValidations() {
        return new HashMap<String, Validator>() {{
            put("title", new FilledStringValidator());
            put("description", new FilledStringValidator());
            put("creator_user_id", new FilledStringValidator());
            put("created_at", new DateValidator());
        }};
    }

    @Override
    protected Map<String, Validator> getEmbeddedValidations(String key) {
        return Maps.newHashMap();
    }

    public Map<String, Object> asMap() {
        // We work on the result a bit to allow correct JSON serializing.
        Map<String, Object> result = Maps.newHashMap(fields);

        result.remove("_id");
        result.put("id", ((ObjectId) fields.get("_id")).toStringMongod());

        result.remove("created_at");
        result.put("created_at", (Tools.getISO8601String((DateTime) fields.get("created_at"))));

        // TODO this sucks and should be done somewhere globally.

        return result;
    }

}
