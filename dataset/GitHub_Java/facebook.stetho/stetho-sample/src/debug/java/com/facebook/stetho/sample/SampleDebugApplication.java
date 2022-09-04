/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.sample;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.util.Log;
import com.facebook.stetho.DumperPluginsProvider;
import com.facebook.stetho.InspectorModulesProvider;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.dumpapp.DumperPlugin;
import com.facebook.stetho.inspector.database.ContentProviderDatabaseDriver;
import com.facebook.stetho.inspector.database.ContentProviderSchema;
import com.facebook.stetho.inspector.database.ContentProviderSchema.Table;
import com.facebook.stetho.inspector.database.DefaultDatabaseFilesProvider;
import com.facebook.stetho.inspector.database.SqliteDatabaseDriver;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.module.Database;
import com.facebook.stetho.inspector.protocol.module.DatabaseConstants;

public class SampleDebugApplication extends SampleApplication {
  private static final String TAG = "SampleDebugApplication";

  @Override
  public void onCreate() {
    super.onCreate();

    long startTime = SystemClock.elapsedRealtime();
    initializeStetho(this);
    long elapsed = SystemClock.elapsedRealtime() - startTime;
    Log.i(TAG, "Stetho initialized in " + elapsed + " ms");
  }

  private void initializeStetho(final Context context) {
    // See also: Stetho.initializeWithDefaults(Context)
    Stetho.initialize(Stetho.newInitializerBuilder(context)
        .enableDumpapp(new DumperPluginsProvider() {
          @Override
          public Iterable<DumperPlugin> get() {
            return new Stetho.DefaultDumperPluginsBuilder(context)
                .provide(new HelloWorldDumperPlugin())
                .provide(new APODDumperPlugin(context.getContentResolver()))
                .finish();
          }
        })
        .enableWebKitInspector(new ExtInspectorModulesProvider(context))
        .build());
  }

  private static class ExtInspectorModulesProvider implements InspectorModulesProvider {

    private Context mContext;

    ExtInspectorModulesProvider(Context context) {
      mContext = context;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public Iterable<ChromeDevtoolsDomain> get() {
      Stetho.DefaultInspectorModulesBuilder builder = new Stetho.DefaultInspectorModulesBuilder(mContext);
      if (Build.VERSION.SDK_INT >= DatabaseConstants.MIN_API_LEVEL) {

        // sample calendars content provider we want to support
        ContentProviderSchema calendarsSchema = new ContentProviderSchema.Builder()
            .table(new Table.Builder()
                .uri(CalendarContract.Calendars.CONTENT_URI)
                .projection(new String[] {
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.IS_PRIMARY,
                })
                .build())
            .build();

        // sample events content provider we want to support
        ContentProviderSchema eventsSchema = new ContentProviderSchema.Builder()
            .table(new Table.Builder()
                .uri(CalendarContract.Events.CONTENT_URI)
                .projection(new String[]{
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.ACCOUNT_NAME,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.CALENDAR_ID,
                })
                .build())
            .build();

        Database database = new Database();
        database.add(new SqliteDatabaseDriver(mContext, new DefaultDatabaseFilesProvider(mContext)));
        database.add(new ContentProviderDatabaseDriver(mContext, calendarsSchema, eventsSchema));
        builder.provide(database);
      }
      return builder.finish();
    }
  }

}
