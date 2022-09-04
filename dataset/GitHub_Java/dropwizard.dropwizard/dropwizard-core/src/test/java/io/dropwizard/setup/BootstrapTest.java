package io.dropwizard.setup;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.UniformReservoir;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.jackson.Jackson;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class BootstrapTest {
    private final Application<Configuration> application = new Application<Configuration>() {
        @Override
        public void run(Configuration configuration, Environment environment) throws Exception {
        }
    };
    private Bootstrap<Configuration> bootstrap;

    @BeforeEach
    public void setUp() {
        bootstrap = new Bootstrap<>(application);
    }

    @Test
    public void hasAnApplication() throws Exception {
        assertThat(bootstrap.getApplication())
                .isEqualTo(application);
    }

    @Test
    public void hasAnObjectMapper() throws Exception {
        assertThat(bootstrap.getObjectMapper())
                .isNotNull();
    }

    @Test
    public void hasHealthCheckRegistry() {
        assertThat(bootstrap.getHealthCheckRegistry())
            .isNotNull();
    }

    @Test
    public void defaultsToUsingFilesForConfiguration() throws Exception {
        assertThat(bootstrap.getConfigurationSourceProvider())
                .isInstanceOfAny(FileConfigurationSourceProvider.class);
    }

    @Test
    public void defaultsToUsingTheDefaultClassLoader() throws Exception {
        assertThat(bootstrap.getClassLoader())
                .isEqualTo(Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void comesWithJvmInstrumentation() throws Exception {
        bootstrap.registerMetrics();
        assertThat(bootstrap.getMetricRegistry().getNames())
                .contains("jvm.buffers.mapped.capacity", "jvm.threads.count", "jvm.memory.heap.usage",
                        "jvm.attribute.vendor", "jvm.classloader.loaded", "jvm.filedescriptor");
    }

    @Test
    public void defaultsToDefaultConfigurationFactoryFactory() throws Exception {
        assertThat(bootstrap.getConfigurationFactoryFactory())
                .isInstanceOf(DefaultConfigurationFactoryFactory.class);
    }

    @Test
    public void bringsYourOwnMetricRegistry() {
        final MetricRegistry newRegistry = new MetricRegistry() {
            @Override
            public Histogram histogram(String name) {
                Histogram existed = (Histogram) getMetrics().get(name);
                return existed != null ? existed : new Histogram(new UniformReservoir());
            }
        };
        bootstrap.setMetricRegistry(newRegistry);
        bootstrap.registerMetrics();

        assertThat(newRegistry.getNames())
                .contains("jvm.buffers.mapped.capacity", "jvm.threads.count", "jvm.memory.heap.usage",
                        "jvm.attribute.vendor", "jvm.classloader.loaded", "jvm.filedescriptor");
    }

    @Test
    public void allowsAccessToJmxReporter() {
        final MetricRegistry newRegistry = new MetricRegistry();
        bootstrap.setMetricRegistry(newRegistry);
        assertThat(bootstrap.getJmxReporter()).isNull();
        bootstrap.registerMetrics();
        assertThat(bootstrap.getJmxReporter()).isNotNull();
    }

    @Test
    public void canUseCustomValidatorFactory() throws Exception {
        ValidatorFactory factory = Validation
                .byProvider(HibernateValidator.class)
                .configure()
                .buildValidatorFactory();
        bootstrap.setValidatorFactory(factory);

        assertThat(bootstrap.getValidatorFactory()).isSameAs(factory);
    }

    @Test
    public void canUseCustomObjectMapper() {
        final ObjectMapper minimalObjectMapper = Jackson.newMinimalObjectMapper();
        bootstrap.setObjectMapper(minimalObjectMapper);
        assertThat(bootstrap.getObjectMapper()).isSameAs(minimalObjectMapper);
    }

    @Test
    public void canUseCustomHealthCheckRegistry() {
        final HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();
        bootstrap.setHealthCheckRegistry(healthCheckRegistry);
        assertThat(bootstrap.getHealthCheckRegistry()).isSameAs(healthCheckRegistry);
    }

}
