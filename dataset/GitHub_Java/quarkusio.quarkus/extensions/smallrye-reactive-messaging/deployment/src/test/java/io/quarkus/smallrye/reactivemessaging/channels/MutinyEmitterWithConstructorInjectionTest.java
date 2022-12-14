package io.quarkus.smallrye.reactivemessaging.channels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.vertx.core.Vertx;

public class MutinyEmitterWithConstructorInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MutinyEmitterExampleWithConstructorInjection.class));

    @Inject
    MutinyEmitterExampleWithConstructorInjection example;

    @Test
    public void testMutinyEmitter() {
        example.run();
        List<String> list = example.list();
        assertEquals(6, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));
        assertEquals("c", list.get(2));
        assertEquals("d", list.get(3));
        assertEquals("e", list.get(4));
        assertEquals("f", list.get(5));
    }

    @ApplicationScoped
    public static class MutinyEmitterExampleWithConstructorInjection {

        MutinyEmitter<String> emitter;

        @SuppressWarnings("unused")
        @Inject
        public MutinyEmitterExampleWithConstructorInjection(
                Vertx vertx, // Not used on purpose
                @Channel("sink") MutinyEmitter<String> e) {
            emitter = e;
        }

        private final List<String> list = new CopyOnWriteArrayList<>();

        public void run() {
            // Payloads
            emitter.send("a").await().atMost(Duration.ofSeconds(1));
            emitter.sendAndForget("b");
            emitter.sendAndAwait("c");

            // Message
            emitter.send(Message.of("d")).await().atMost(Duration.ofSeconds(1));
            emitter.sendAndForget(Message.of("e"));
            emitter.sendAndAwait(Message.of("f"));

            emitter.complete();
        }

        @Incoming("sink")
        public void consume(String s) {
            list.add(s);
        }

        public List<String> list() {
            return list;
        }

    }

}
