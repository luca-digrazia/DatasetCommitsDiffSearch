package io.quarkus.smallrye.metrics.deployment;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import javax.servlet.DispatcherType;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import io.quarkus.undertow.deployment.FilterBuildItem;

/**
 * If resteasy metrics are enabled, register additional filters specific to smallrye metrics.
 */
public class ResteasyMetricsProcessor {
    static final String SMALLRYE_JAXRS_FILTER_CLASS_NAME = "io.smallrye.metrics.jaxrs.JaxRsMetricsFilter";
    static final String SMALLRYE_JAXRS_SERVLET_FILTER_CLASS_NAME = "io.smallrye.metrics.jaxrs.JaxRsMetricsServletFilter";
    static final String SMALLRYE_JAXRS_QUARKUS_FILTER_CLASS_NAME = "io.quarkus.smallrye.metrics.runtime.QuarkusJaxRsMetricsFilter";
    static final String RESTEASY_CONFIG_PROPERTY = "quarkus.resteasy.metrics.enabled";

    static class RestMetricsEnabled implements BooleanSupplier {
        SmallRyeMetricsProcessor.SmallRyeMetricsConfig smConfig;

        public boolean getAsBoolean() {
            boolean resteasyConfigEnabled = ConfigProvider.getConfig().getOptionalValue(RESTEASY_CONFIG_PROPERTY, boolean.class)
                    .orElse(false);
            return smConfig.extensionsEnabled && (smConfig.jaxrsEnabled || resteasyConfigEnabled);
        }
    }

    // Ensure class is present (smallrye metrics extension) and resteasy metrics are enabled
    @BuildStep(onlyIf = RestMetricsEnabled.class)
    void enableMetrics(Optional<MetricsCapabilityBuildItem> metricsCapabilityBuildItem,
            BuildProducer<ResteasyJaxrsProviderBuildItem> jaxRsProviders,
            BuildProducer<FilterBuildItem> servletFilters,
            Capabilities capabilities) {

        warnIfDeprecatedResteasyPropertiesPresent();
        if (metricsCapabilityBuildItem.isPresent()) {
            if (capabilities.isPresent(Capability.SERVLET)) {
                // if running with servlet, use the MetricsFilter implementation from SmallRye
                jaxRsProviders.produce(
                        new ResteasyJaxrsProviderBuildItem(SMALLRYE_JAXRS_FILTER_CLASS_NAME));
                servletFilters.produce(
                        FilterBuildItem.builder("metricsFilter", SMALLRYE_JAXRS_SERVLET_FILTER_CLASS_NAME)
                                .setAsyncSupported(true)
                                .addFilterUrlMapping("*", DispatcherType.FORWARD)
                                .addFilterUrlMapping("*", DispatcherType.INCLUDE)
                                .addFilterUrlMapping("*", DispatcherType.REQUEST)
                                .addFilterUrlMapping("*", DispatcherType.ASYNC)
                                .addFilterUrlMapping("*", DispatcherType.ERROR)
                                .build());
            } else {
                // if running with vert.x, use the MetricsFilter implementation from Quarkus codebase
                jaxRsProviders.produce(
                        new ResteasyJaxrsProviderBuildItem(SMALLRYE_JAXRS_QUARKUS_FILTER_CLASS_NAME));
            }
        }
    }

    private void warnIfDeprecatedResteasyPropertiesPresent() {
        if (ConfigProvider.getConfig().getOptionalValue(RESTEASY_CONFIG_PROPERTY, boolean.class).isPresent()) {
            SmallRyeMetricsProcessor.LOGGER.warn(
                    "`quarkus.resteasy.metrics.enabled` is deprecated and will be removed in a future version. "
                            + "Use `quarkus.smallrye-metrics.jaxrs.enabled` to enable metrics for REST endpoints "
                            + "using the smallrye-metrics extension");
        }
    }
}
