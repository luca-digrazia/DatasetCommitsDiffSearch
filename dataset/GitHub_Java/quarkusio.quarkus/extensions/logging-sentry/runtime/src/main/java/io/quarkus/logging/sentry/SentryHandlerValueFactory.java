package io.quarkus.logging.sentry;

import java.util.Optional;
import java.util.logging.Handler;

import org.jboss.logging.Logger;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;
import io.sentry.jul.SentryHandler;

@Recorder
public class SentryHandlerValueFactory {
    private static final Logger LOG = Logger.getLogger(SentryConfigProvider.class);

    public RuntimeValue<Optional<Handler>> create(final SentryConfig config) {
        final SentryConfigProvider provider = new SentryConfigProvider(config);
        final Lookup lookup = new Lookup(provider, provider);

        if (!config.enable) {
            // Disable Sentry
            Sentry.init(SentryOptions.from(lookup, Dsn.DEFAULT_DSN));
            return new RuntimeValue<>(Optional.empty());
        }
        if (!config.dsn.isPresent()) {
            throw new ConfigurationException(
                    "Configuration key \"quarkus.log.sentry.dsn\" is required when Sentry is enabled, but its value is empty/missing");
        }
        if (!config.inAppPackages.isPresent()) {
            LOG.warn(
                    "No 'quarkus.sentry.in-app-packages' was configured, this option is highly recommended as it affects stacktrace grouping and display on Sentry. See https://quarkus.io/guides/logging-sentry#in-app-packages");
        }

        // Init Sentry
        Sentry.init(SentryOptions.from(lookup, config.dsn.get()));
        SentryHandler handler = new SentryHandler();
        handler.setLevel(config.level);
        return new RuntimeValue<>(Optional.of(handler));
    }
}
