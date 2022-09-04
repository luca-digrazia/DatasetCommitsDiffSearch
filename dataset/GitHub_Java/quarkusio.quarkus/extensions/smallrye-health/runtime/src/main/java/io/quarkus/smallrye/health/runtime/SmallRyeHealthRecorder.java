package io.quarkus.smallrye.health.runtime;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.spi.HealthCheckResponseProvider;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class SmallRyeHealthRecorder {

    public void registerHealthCheckResponseProvider(Class<? extends HealthCheckResponseProvider> providerClass) {
        try {
            HealthCheckResponse.setResponseProvider(providerClass.getConstructor().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to instantiate service " + providerClass + " using the no-arg constructor.");
        }
    }

    public Handler<RoutingContext> uiHandler(String healthUiFinalDestination, String healthUiPath,
            SmallRyeHealthRuntimeConfig runtimeConfig) {

        if (runtimeConfig.enable) {
            return new SmallRyeHealthStaticHandler(healthUiFinalDestination, healthUiPath);
        } else {
            return new SmallRyeHealthNotFoundHandler();
        }
    }
}
