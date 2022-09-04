package io.quarkus.vertx.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.vertx.core.runtime.VertxCoreRecorder;
import io.vertx.core.Vertx;

public class VertxProducerTest {

    private VertxRecorder recorder;
    private VertxProducer producer;

    @BeforeEach
    public void setUp() {
        producer = new VertxProducer();
        recorder = new VertxRecorder();
    }

    @AfterEach
    public void tearDown() {
        recorder.destroy();
    }

    @Test
    public void shouldNotFailWithoutConfig() {
        verifyProducer(VertxCoreRecorder.initialize(null, null));
    }

    private void verifyProducer(Vertx v) {
        assertThat(producer.eventbus(v)).isNotNull();

        assertThat(producer.axle(v)).isNotNull();
        assertFalse(producer.axle(v).isClustered());
        assertThat(producer.axleEventBus(producer.axle(v))).isNotNull();

        assertThat(producer.rx(v)).isNotNull();
        assertFalse(producer.rx(v).isClustered());
        assertThat(producer.rxEventBus(producer.rx(v))).isNotNull();

        assertThat(producer.mutiny(v)).isNotNull();
        assertFalse(producer.mutiny(v).isClustered());
        assertThat(producer.mutinyEventBus(producer.mutiny(v))).isNotNull();

    }
}
