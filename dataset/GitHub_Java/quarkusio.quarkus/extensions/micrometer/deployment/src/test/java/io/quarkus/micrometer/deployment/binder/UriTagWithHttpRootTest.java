package io.quarkus.micrometer.deployment.binder;

import static io.restassured.RestAssured.when;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.micrometer.test.ServletEndpoint;
import io.quarkus.micrometer.test.Util;
import io.quarkus.micrometer.test.VertxWebEndpoint;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class UriTagWithHttpRootTest {
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource("test-logging.properties")
            .overrideConfigKey("quarkus.http.root-path", "/foo")
            .overrideConfigKey("quarkus.micrometer.binder-enabled-default", "false")
            .overrideConfigKey("quarkus.micrometer.binder.http-client.enabled", "true")
            .overrideConfigKey("quarkus.micrometer.binder.http-server.enabled", "true")
            .overrideConfigKey("quarkus.micrometer.binder.vertx.enabled", "true")
            .overrideConfigKey("pingpong/mp-rest/url", "${test.url}")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Util.class,
                            PingPongResource.class,
                            PingPongResource.PingPongRestClient.class,
                            ServletEndpoint.class,
                            VertxWebEndpoint.class));

    @Inject
    MeterRegistry registry;

    @Test
    public void testMetricFactoryCreatedMetrics() throws Exception {
        RestAssured.basePath = "/";

        // If you invoke requests, http server and client meters should be registered
        // Leading context root (/foo) should be stripped from resulting _server_ tag

        when().get("/foo/ping/one").then().statusCode(200);
        when().get("/foo/ping/two").then().statusCode(200);
        when().get("/foo/ping/three").then().statusCode(200);
        when().get("/foo/vertx/item/123").then().statusCode(200);
        when().get("/foo/servlet/12345").then().statusCode(200);

        // URIs for server: /ping/{message}, /pong/{message}, /vertx/item/{id}
        Assertions.assertEquals(1, registry.find("http.server.requests").tag("uri", "/ping/{message}").timers().size(),
                "/ping/{message} should be returned by JAX-RS. Found:\n"
                        + Util.listMeters(registry.find("http.server.requests").meters(), "uri"));
        Assertions.assertEquals(1, registry.find("http.server.requests").tag("uri", "/pong/{message}").timers().size(),
                "/pong/{message} should be returned by JAX-RS. Found:\n"
                        + Util.listMeters(registry.find("http.server.requests").meters(), "uri"));
        Assertions.assertEquals(1, registry.find("http.server.requests").tag("uri", "/vertx/item/{id}").timers().size(),
                "Vert.x Web template path (/vertx/item/:id) should be detected/translated to /vertx/item/{id}. Found:\n"
                        + Util.listMeters(registry.find("http.server.requests").meters(), "uri"));
    }

    @Path("/")
    @Singleton
    public static class PingPongResource {

        @RegisterRestClient(configKey = "pingpong")
        public interface PingPongRestClient {

            @Path("/foo/pong/{message}")
            @GET
            String pingpong(@PathParam("message") String message);
        }

        @Inject
        @RestClient
        PingPongRestClient pingRestClient;

        @GET
        @Path("pong/{message}")
        public String pong(@PathParam("message") String message) {
            return message;
        }

        @GET
        @Path("ping/{message}")
        public String ping(@PathParam("message") String message) {
            return pingRestClient.pingpong(message);
        }
    }
}
