package io.quarkus.resteasy.reactive.server.test.stream;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.hamcrest.Matchers;
import org.jboss.resteasy.reactive.client.impl.MultiInvoker;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;

public class StreamTestCase {

    @TestHTTPResource
    URI uri;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamResource.class));

    @Test
    public void testStreaming() throws Exception {
        RestAssured.get("/stream/text/stream")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));
        RestAssured.get("/stream/text/collect")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));

        RestAssured.get("/stream/byte-arrays/stream")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));
        RestAssured.get("/stream/byte-arrays/collect")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));

        RestAssured.get("/stream/char-arrays/stream")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));
        RestAssured.get("/stream/char-arrays/collect")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));

        RestAssured.get("/stream/buffer/stream")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));
        RestAssured.get("/stream/buffer/collect")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("foobar"));
    }

    @Test
    public void testClientStreaming() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(uri.toString() + "stream/text/stream");
        Multi<String> multi = target.request().rx(MultiInvoker.class).get(String.class);
        List<String> list = multi.collectItems().asList().await().atMost(Duration.ofSeconds(5));
        Assertions.assertEquals(2, list.size());
        Assertions.assertEquals("foo", list.get(0));
        Assertions.assertEquals("bar", list.get(1));
    }

    @Test
    public void testInfiniteStreamClosedByClientImmediately() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(uri.toString() + "stream/infinite/stream");
        Multi<String> multi = target.request().rx(MultiInvoker.class).get(String.class);
        Cancellable cancellable = multi.subscribe().with(item -> {
            System.err.println("Received " + item);
        });
        // immediately cancel
        cancellable.cancel();
        // give it some time and check
        Thread.sleep(2000);

        WebTarget checkTarget = client.target(uri.toString() + "stream/infinite/stream-was-cancelled");
        String check = checkTarget.request().get(String.class);
        Assertions.assertEquals("OK", check);
    }

    @Test
    public void testInfiniteStreamClosedByClientAfterRegistration() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(uri.toString() + "stream/infinite/stream");
        Multi<String> multi = target.request().rx(MultiInvoker.class).get(String.class);
        // cancel after two items
        CountDownLatch latch = new CountDownLatch(2);
        Cancellable cancellable = multi.subscribe().with(item -> {
            System.err.println("Received " + item);
            latch.countDown();
        });
        Assertions.assertTrue(latch.await(30, TimeUnit.SECONDS));
        // now cancel
        cancellable.cancel();
        // give it some time and check
        Thread.sleep(2000);

        WebTarget checkTarget = client.target(uri.toString() + "stream/infinite/stream-was-cancelled");
        String check = checkTarget.request().get(String.class);
        Assertions.assertEquals("OK", check);
    }
}
