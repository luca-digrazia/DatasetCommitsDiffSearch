package io.dropwizard.jersey.validation;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.logging.BootstrapLogging;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assume.assumeThat;

public class ConstraintViolationExceptionMapperTest extends JerseyTest {
    static {
        BootstrapLogging.bootstrap();
    }

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return DropwizardResourceConfig.forTesting(new MetricRegistry())
                .packages("io.dropwizard.jersey.validation");
    }

    @Test
    public void postInvalidEntityIs422() throws Exception {
        assumeThat(Locale.getDefault().getLanguage(), is("en"));

        final Response response = target("/valid/foo").request(MediaType.APPLICATION_JSON)
                .post(Entity.entity("{}", MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.readEntity(String.class)).isEqualTo("{\"errors\":[\"name may not be empty\"]}");
    }

    @Test
    public void getInvalidReturnIs500() throws Exception {
        // return value is too long and so will fail validation
        final Response response = target("/valid/bar")
                .queryParam("name", "dropwizard").request().get();
        assertThat(response.getStatus()).isEqualTo(500);

        String ret = "{\"errors\":[\"blaze.<return value> length must be between 0 and 3\"]}";
        assertThat(response.readEntity(String.class)).isEqualTo(ret);
    }

    @Test
    public void getInvalidQueryParamsIs400() throws Exception {
        // query parameter is too short and so will fail validation
        final Response response = target("/valid/bar")
                .queryParam("name", "hi").request().get();

        assertThat(response.getStatus()).isEqualTo(400);

        String ret = "{\"errors\":[\"blaze.arg0 length must be between 3 and 2147483647\"]}";
        assertThat(response.readEntity(String.class)).isEqualTo(ret);
    }

    @Test
    public void getInvalidBeanParamsIs400() throws Exception {
        // bean parameter is too short and so will fail validation
        final Response response = target("/valid/zoo")
                .request().get();
        assertThat(response.getStatus()).isEqualTo(400);

        String ret = "{\"errors\":[\"blazer.arg0.name may not be empty\"]}";
        assertThat(response.readEntity(String.class)).isEqualTo(ret);
    }
}
