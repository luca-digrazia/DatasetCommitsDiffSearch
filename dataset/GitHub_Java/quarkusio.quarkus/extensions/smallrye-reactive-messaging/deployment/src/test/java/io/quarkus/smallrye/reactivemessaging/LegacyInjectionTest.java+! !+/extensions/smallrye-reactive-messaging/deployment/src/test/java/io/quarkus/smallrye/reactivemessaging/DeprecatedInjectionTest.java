package io.quarkus.smallrye.reactivemessaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import javax.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class LegacyInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SimpleBean.class, OldChannelConsumer.class, OldEmitterExample.class));

    @Inject
    OldChannelConsumer oldChannelConsumer;

    @Inject
    OldEmitterExample oldEmitterExample;

    @Test
    public void testOldChannelInjection() {
        List<String> consumed = oldChannelConsumer.consume();
        assertEquals(5, consumed.size());
        assertEquals("hello", consumed.get(0));
        assertEquals("with", consumed.get(1));
        assertEquals("SmallRye", consumed.get(2));
        assertEquals("reactive", consumed.get(3));
        assertEquals("message", consumed.get(4));
    }

    @Test
    public void testOldEmitter() {
        oldEmitterExample.run();
        List<String> list = oldEmitterExample.list();
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));
        assertEquals("c", list.get(2));
    }

}
