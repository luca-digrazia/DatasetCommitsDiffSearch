package io.dropwizard.server;

import io.dropwizard.Configuration;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyContainerHolder;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests that the {@link JerseyEnvironment#getUrlPattern()} is set by the following priority order:
 * <ol>
 *     <li>YAML defined value</li>
 *     <li>{@link io.dropwizard.Application#run(Configuration, Environment)} defined value</li>
 *     <li>Default value defined by {@link DropwizardResourceConfig#urlPattern}</li>
 * </ol>
 */
public class AbstractServerFactoryTest {

    private final JerseyContainerHolder holder = mock(JerseyContainerHolder.class);
    private final DropwizardResourceConfig config = new DropwizardResourceConfig();
    private final JerseyEnvironment jerseyEnvironment = new JerseyEnvironment(holder, config);
    private final Environment environment = mock(Environment.class, RETURNS_DEEP_STUBS);
    private final TestServerFactory serverFactory = new TestServerFactory();

    private static final String DEFAULT_PATTERN = "/*";
    private static final String RUN_SET_PATTERN = "/set/from/run/*";
    private static final String YAML_SET_PATTERN = "/set/from/yaml/*";

    @Before
    public void before() {
        when(this.environment.jersey()).thenReturn(this.jerseyEnvironment);
    }

    @Test
    public void usesYamlDefinedPattern() {
        this.serverFactory.setJerseyRootPath(YAML_SET_PATTERN);
        this.jerseyEnvironment.setUrlPattern(RUN_SET_PATTERN);

        this.serverFactory.build(this.environment);

        assertThat(this.jerseyEnvironment.getUrlPattern()).isEqualTo(YAML_SET_PATTERN);
    }

    @Test
    public void usesRunDefinedPatternWhenNoYaml() {
        this.jerseyEnvironment.setUrlPattern(RUN_SET_PATTERN);

        this.serverFactory.build(this.environment);

        assertThat(this.jerseyEnvironment.getUrlPattern()).isEqualTo(RUN_SET_PATTERN);
    }

    @Test
    public void usesDefaultPatternWhenNoneSet() {
        this.serverFactory.build(this.environment);

        assertThat(this.jerseyEnvironment.getUrlPattern()).isEqualTo(DEFAULT_PATTERN);
    }

    @Test
    public void yamlPatternEndsWithSlashStar() {
        assertPatternEndsWithSlashStar("/missing/slash/star");
    }

    @Test
    public void yamlPatternEndsWithStar() {
        assertPatternEndsWithSlashStar("/missing/star/");
    }

    private void assertPatternEndsWithSlashStar(String jerseyRootPath) {
        this.serverFactory.setJerseyRootPath(jerseyRootPath);
        this.serverFactory.build(this.environment);
        assertThat(this.jerseyEnvironment.getUrlPattern()).endsWith("/*");
    }

    /**
     * Test implementation of {@link AbstractServerFactory} used to run {@link #createAppServlet}, which triggers the
     * setting of {@link JerseyEnvironment#setUrlPattern(String)}.
     */
    public static class TestServerFactory extends AbstractServerFactory {
        @Override
        public Server build(Environment environment) {
            // mimics the current default + simple server factory build() methods
            ThreadPool threadPool = createThreadPool(environment.metrics());
            Server server = buildServer(environment.lifecycle(), threadPool);
            this.createAppServlet(server,
                                  environment.jersey(),
                                  environment.getObjectMapper(),
                                  environment.getValidator(),
                                  environment.getApplicationContext(),
                                  environment.getJerseyServletContainer(),
                                  environment.metrics());
            return server;
        }
    }
}
