package io.dropwizard.jetty;

import java.util.TimeZone;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import io.dropwizard.logging.LayoutFactory;

/**
 * Factory that creates a {@link RequestLogLayout}
 */
public class RequestLogLayoutFactory implements LayoutFactory<ILoggingEvent> {
    @Override
    public PatternLayoutBase<ILoggingEvent> build(LoggerContext context, TimeZone timeZone) {
        return new RequestLogLayout();
    }
}
