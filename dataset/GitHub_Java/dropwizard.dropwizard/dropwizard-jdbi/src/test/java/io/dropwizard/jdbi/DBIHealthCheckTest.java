package io.dropwizard.jdbi;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;

import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DBIHealthCheckTest {

    @Test
    public void testItTimesOutProperly() throws Exception {
        Optional<String> validationQuery = Optional.of("select 1");
        DBI dbi = mock(DBI.class);
        Handle handle = mock(Handle.class);
        when(dbi.open()).thenReturn(handle);
        Mockito.doAnswer(invocation -> {
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (Exception ignored) {
            }
            return null;
        }).when(handle).execute(validationQuery.get());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        DBIHealthCheck dbiHealthCheck = new DBIHealthCheck(executorService,
                Duration.milliseconds(5),
                dbi,
                validationQuery);
        HealthCheck.Result result = dbiHealthCheck.check();
        executorService.shutdown();
        assertThat(result.isHealthy()).isFalse();
    }

    @Test
    public void testItCallsIsValidWhenValidationQueryIsMissing() throws Exception {
        DBI dbi = mock(DBI.class);
        Handle handle = mock(Handle.class);
        Connection connection = mock(Connection.class);
        when(dbi.open()).thenReturn(handle);
        when(handle.getConnection()).thenReturn(connection);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        DBIHealthCheck dbiHealthCheck = new DBIHealthCheck(executorService,
            Duration.milliseconds(5),
            dbi,
            Optional.empty());
        HealthCheck.Result result = dbiHealthCheck.check();
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        assertThat(result.isHealthy()).isFalse();
        verify(connection).isValid(anyInt());
    }
}
