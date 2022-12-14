package org.graylog.plugins.views.migrations.V20191125144500_MigrateDashboardsToViewsSupport;

import org.bson.types.ObjectId;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.database.MongoConnection;
import org.mongojack.JacksonDBCollection;

import javax.inject.Inject;

class ViewService {
    protected final JacksonDBCollection<View, ObjectId> db;

    @Inject
    ViewService(MongoConnection mongoConnection, MongoJackObjectMapperProvider mapper) {
        this.db = JacksonDBCollection.wrap(mongoConnection.getDatabase().getCollection("views"),
                View.class,
                ObjectId.class,
                mapper.get());
    }

    public ObjectId save(View view) {
        return db.insert(view).getSavedId();
    }
}
