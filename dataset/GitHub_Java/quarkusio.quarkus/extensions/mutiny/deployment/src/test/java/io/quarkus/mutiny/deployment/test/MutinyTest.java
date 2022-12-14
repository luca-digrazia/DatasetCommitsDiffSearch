package io.quarkus.mutiny.deployment.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;

public class MutinyTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BeanUsingMutiny.class));

    @Inject
    BeanUsingMutiny bean;

    @Test
    public void testUni() {
        String s = bean.greeting().await().indefinitely();
        Assertions.assertEquals(s, "hello");
    }

    @Test
    public void testMulti() {
        List<String> list = bean.stream().collectItems().asList().await().indefinitely();
        Assertions.assertEquals(list.get(0), "hello");
        Assertions.assertEquals(list.get(1), "world");
    }

    @Test
    public void testDroppedException() {
        TestingJulHandler julHandler = new TestingJulHandler();
        Logger logger = LogManager.getLogManager().getLogger("io.quarkus.mutiny.runtime.MutinyInfrastructure");
        logger.addHandler(julHandler);

        AtomicReference<String> item = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Cancellable cancellable = bean.droppedException().subscribe().with(item::set, failure::set);
        cancellable.cancel();

        Assertions.assertNull(item.get());
        Assertions.assertNull(failure.get());

        Assertions.assertEquals(1, julHandler.logRecords.size());
        LogRecord logRecord = julHandler.logRecords.get(0);
        Assertions.assertTrue(logRecord.getMessage().contains("Mutiny had to drop the following exception"));
        Throwable thrown = logRecord.getThrown();
        Assertions.assertTrue(thrown instanceof IOException);
        Assertions.assertEquals("boom", thrown.getMessage());
    }

    @ApplicationScoped
    public static class BeanUsingMutiny {

        public Uni<String> greeting() {
            return Uni.createFrom().item(() -> "hello")
                    .emitOn(Infrastructure.getDefaultExecutor());
        }

        public Multi<String> stream() {
            return Multi.createFrom().items("hello", "world")
                    .emitOn(Infrastructure.getDefaultExecutor());
        }

        public Uni<String> droppedException() {
            return Uni.createFrom()
                    .<String> emitter(uniEmitter -> {
                        // Do not emit anything
                    })
                    .onCancellation().call(() -> Uni.createFrom().failure(new IOException("boom")));
        }
    }

    private static class TestingJulHandler extends Handler {

        private final ArrayList<LogRecord> logRecords = new ArrayList<>();

        public ArrayList<LogRecord> getLogRecords() {
            return logRecords;
        }

        @Override
        public void publish(LogRecord record) {
            logRecords.add(record);
        }

        @Override
        public void flush() {
            // Do nothing
        }

        @Override
        public void close() throws SecurityException {
            // Do nothing
        }
    }
}
