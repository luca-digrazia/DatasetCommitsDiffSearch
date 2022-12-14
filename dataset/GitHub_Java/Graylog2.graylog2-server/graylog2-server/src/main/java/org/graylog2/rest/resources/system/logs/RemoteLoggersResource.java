package org.graylog2.rest.resources.system.logs;

import org.graylog2.rest.models.system.loggers.responses.LogMessagesSummary;
import org.graylog2.rest.models.system.loggers.responses.LoggersSummary;
import org.graylog2.rest.models.system.loggers.responses.SubsystemSummary;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.PUT;

public interface RemoteLoggersResource {
    @GET("/system/loggers")
    Call<LoggersSummary> loggers();

    @GET("/system/loggers/subsystems")
    Call<SubsystemSummary> subsystems();

    @PUT("/system/loggers/subsystems/{subsystem}/level/{level}")
    Call setSubsystemLoggerLevel(String subsystemTitle, String level);

    @PUT("/system/loggers/{loggerName}/level/{level}")
    Call setSingleLoggerLevel(String loggerName, String level);

    @GET("/system/loggers/messages/recent")
    Call<LogMessagesSummary> messages(int limit, String level);
}
