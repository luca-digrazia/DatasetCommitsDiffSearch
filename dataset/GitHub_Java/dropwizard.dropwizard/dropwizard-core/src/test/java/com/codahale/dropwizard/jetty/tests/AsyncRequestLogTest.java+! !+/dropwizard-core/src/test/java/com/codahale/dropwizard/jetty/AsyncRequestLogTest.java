package com.codahale.dropwizard.jetty.tests;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import com.codahale.dropwizard.jetty.AsyncRequestLog;
import com.codahale.metrics.Clock;
import com.google.common.net.HttpHeaders;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AsyncRequestLogTest {
    private final Clock clock = mock(Clock.class);
    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private final AppenderAttachableImpl<ILoggingEvent> appenders = new AppenderAttachableImpl<>();
    private final AsyncRequestLog asyncRequestLog = new AsyncRequestLog(clock, appenders, TimeZone.getTimeZone("UTC"));

    private final Request request = mock(Request.class);
    private final Response response = mock(Response.class);
    private final HttpChannelState channelState = mock(HttpChannelState.class);

    @Before
    public void setUp() throws Exception {
        when(channelState.isInitial()).thenReturn(true);

        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getTimeStamp()).thenReturn(TimeUnit.SECONDS.toMillis(1353042047));
        when(request.getMethod()).thenReturn("GET");
        when(request.getUri()).thenReturn(new HttpURI("/test/things?yay"));
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getHttpChannelState()).thenReturn(channelState);
        when(request.getDispatchTime()).thenReturn(TimeUnit.SECONDS.toMillis(1353042048));

        when(response.getStatus()).thenReturn(200);
        when(response.getContentCount()).thenReturn(8290L);

        when(clock.getTime()).thenReturn(TimeUnit.SECONDS.toMillis(1353042049));

        appenders.addAppender(appender);

        asyncRequestLog.start();
    }

    @After
    public void tearDown() throws Exception {
        if (asyncRequestLog.isRunning()) {
            asyncRequestLog.stop();
        }
    }

    @Test
    public void startsAndStops() throws Exception {
        asyncRequestLog.stop();

        verify(appender, timeout(1000)).stop();
    }

    @Test
    public void logsRequests() throws Exception {
        final ILoggingEvent event = logAndCapture();

        assertThat(event.getFormattedMessage())
                .isEqualTo("10.0.0.1 - - [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" 200 8290 1000 2000");

        assertThat(event.getLevel())
                .isEqualTo(Level.INFO);
    }

    @Test
    public void logsForwardedFor() throws Exception {
        when(request.getHeader(HttpHeaders.X_FORWARDED_FOR)).thenReturn("123.123.123.123");

        final ILoggingEvent event = logAndCapture();
        assertThat(event.getFormattedMessage())
                .isEqualTo("123.123.123.123 - - [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" 200 8290 1000 2000");
    }

    @Test
    public void logsPrincipal() throws Exception {
        final Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("coda");

        final UserIdentity identity = mock(UserIdentity.class);
        when(identity.getUserPrincipal()).thenReturn(principal);

        final Authentication.User user = mock(Authentication.User.class);
        when(user.getUserIdentity()).thenReturn(identity);

        when(request.getAuthentication()).thenReturn(user);

        final ILoggingEvent event = logAndCapture();
        assertThat(event.getFormattedMessage())
                .isEqualTo("10.0.0.1 - coda [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" 200 8290 1000 2000");
    }

    @Test
    public void logsAsyncContinuations() throws Exception {
        when(channelState.isInitial()).thenReturn(false);

        final ILoggingEvent event = logAndCapture();

        assertThat(event.getFormattedMessage())
                .isEqualTo("10.0.0.1 - - [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" Async 8290 1000 2000");
    }

    private ILoggingEvent logAndCapture() {
        asyncRequestLog.log(request, response);

        final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(appender, timeout(1000)).doAppend(captor.capture());

        return captor.getValue();
    }
}
