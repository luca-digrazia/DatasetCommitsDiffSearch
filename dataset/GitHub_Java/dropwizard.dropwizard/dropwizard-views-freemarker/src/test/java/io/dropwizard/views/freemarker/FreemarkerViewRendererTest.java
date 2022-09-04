package io.dropwizard.views.freemarker;

import com.codahale.metrics.MetricRegistry;

import freemarker.template.Configuration;
import io.dropwizard.logging.BootstrapLogging;
import io.dropwizard.views.ViewMessageBodyWriter;
import io.dropwizard.views.ViewRenderExceptionMapper;
import io.dropwizard.views.ViewRenderer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class FreemarkerViewRendererTest extends JerseyTest {
    static {
        BootstrapLogging.bootstrap();
    }

    @Path("/test/")
    @Produces(MediaType.TEXT_HTML)
    public static class ExampleResource {
        @GET
        @Path("/absolute")
        public AbsoluteView showAbsolute() {
            return new AbsoluteView("yay");
        }

        @GET
        @Path("/relative")
        public RelativeView showRelative() {
            return new RelativeView();
        }

        @GET
        @Path("/bad")
        public BadView showBad() {
            return new BadView();
        }

        @GET
        @Path("/error")
        public ErrorView showError() {
            return new ErrorView();
        }

        @POST
        @Path("/auto-escaping")
        public AutoEscapingView showUserInputSafely(@FormParam("input") String userInput) {
            return new AutoEscapingView(userInput);
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected Application configure() {
        ResourceConfig config = new ResourceConfig();
        final ViewRenderer renderer = new FreemarkerViewRenderer(Configuration.VERSION_2_3_30);
        config.register(new ViewMessageBodyWriter(new MetricRegistry(), Collections.singletonList(renderer)));
        config.register(new ExampleResource());
        config.register(new ViewRenderExceptionMapper());
        return config;
    }

    @Test
    void rendersViewsWithAbsoluteTemplatePaths() {
        final String response = target("/test/absolute")
                .request().get(String.class);
        assertThat(response).isEqualTo("Woop woop. yay\n");
    }

    @Test
    void rendersViewsWithRelativeTemplatePaths() {
        final String response = target("/test/relative")
                .request().get(String.class);
        assertThat(response).isEqualTo("Ok.\n");
    }

    @Test
    void returnsA500ForViewsWithBadTemplatePaths() {
        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> target("/test/bad").request().get(String.class))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(500))
            .satisfies(e -> assertThat(e.getResponse().readEntity(String.class))
                .isEqualTo(ViewRenderExceptionMapper.TEMPLATE_ERROR_MSG));
    }

    @Test
    @Disabled("Flaky on JUnit5")
    public void returnsA500ForViewsThatCantCompile() {
        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> target("/test/error").request().get(String.class))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(500))
            .satisfies(e -> assertThat(e.getResponse().readEntity(String.class))
                .isEqualTo(ViewRenderExceptionMapper.TEMPLATE_ERROR_MSG));
    }

    @Test
    void rendersViewsUsingUnsafeInputWithAutoEscapingEnabled() {
        final String unsafe = "<script>alert(\"hello\")</script>";
        final Response response = target("/test/auto-escaping")
            .request().post(Entity.form(new Form("input", unsafe)));
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getHeaderString("content-type")).isEqualToIgnoringCase(MediaType.TEXT_HTML);
        assertThat(response.readEntity(String.class)).doesNotContain(unsafe);
    }
}
