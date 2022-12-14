package io.quarkus.vertx.http;

import java.util.function.Consumer;

import javax.enterprise.event.Observes;
import javax.inject.Singleton;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.restassured.RestAssured;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class NonApplicationAndRootPathTest {
    private static final String APP_PROPS = "" +
            "quarkus.http.root-path=/api\n";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(APP_PROPS), "application.properties")
                    .addClasses(MyObserver.class))
            .addBuildChainCustomizer(buildCustomizer());

    static Consumer<BuildChainBuilder> buildCustomizer() {
        return new Consumer<BuildChainBuilder>() {
            @Override
            public void accept(BuildChainBuilder builder) {
                builder.addBuildStep(new BuildStep() {
                    @Override
                    public void execute(BuildContext context) {
                        context.produce(new RouteBuildItem.Builder()
                                .route("/non-app")
                                .handler(new MyHandler())
                                .blockingRoute()
                                .nonApplicationRoute()
                                .build());
                    }
                }).produces(RouteBuildItem.class)
                        .build();
            }
        };
    }

    public static class MyHandler implements Handler<RoutingContext> {
        @Override
        public void handle(RoutingContext routingContext) {
            routingContext.response()
                    .setStatusCode(200)
                    .end(routingContext.request().path());
        }
    }

    @Test
    public void testNonApplicationEndpointOnRootPathWithRedirect() {
        RestAssured.given().get("/api/non-app").then().statusCode(200).body(Matchers.equalTo("/api/q/non-app"));
    }

    @Test
    public void testNonApplicationEndpointDirect() {
        RestAssured.given().get("/api/q/non-app").then().statusCode(200).body(Matchers.equalTo("/api/q/non-app"));
    }

    @Singleton
    static class MyObserver {

        void test(@Observes String event) {
            //Do Nothing
        }

    }
}
