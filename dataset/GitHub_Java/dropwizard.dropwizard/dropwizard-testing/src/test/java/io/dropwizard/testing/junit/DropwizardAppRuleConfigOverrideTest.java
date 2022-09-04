package io.dropwizard.testing.junit;

import io.dropwizard.testing.app.TestApplication;
import io.dropwizard.testing.app.TestConfiguration;
import org.junit.ClassRule;
import org.junit.Test;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.assertj.core.api.Assertions.assertThat;

public class DropwizardAppRuleConfigOverrideTest {
    @SuppressWarnings("deprecation")
    @ClassRule
    public static final DropwizardAppRule<TestConfiguration> RULE =
        new DropwizardAppRule<>(TestApplication.class, resourceFilePath("test-config.yaml"),
            "app-rule",
            config("app-rule", "message", "A new way to say Hooray!"),
            config("app-rule", "extra", () -> "supplied"),
            config("extra", () -> "supplied again"));

    @Test
    public void supportsConfigAttributeOverrides() {
        final String content = RULE.client().target("http://localhost:" + RULE.getLocalPort() + "/test")
            .request().get(String.class);

        assertThat(content).isEqualTo("A new way to say Hooray!");
    }

    @Test
    public void supportsSuppliedConfigAttributeOverrides() throws Exception {
        assertThat(System.getProperty("app-rule.extra")).isEqualTo("supplied");
        assertThat(System.getProperty("dw.extra")).isEqualTo("supplied again");
    }
}
