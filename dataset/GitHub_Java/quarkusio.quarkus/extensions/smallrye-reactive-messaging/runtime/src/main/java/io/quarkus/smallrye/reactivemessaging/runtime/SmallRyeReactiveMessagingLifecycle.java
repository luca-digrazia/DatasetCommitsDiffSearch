package io.quarkus.smallrye.reactivemessaging.runtime;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.extension.MediatorManager;

@Dependent
public class SmallRyeReactiveMessagingLifecycle {

    @Inject
    MediatorManager mediatorManager;

    void onApplicationStart(@Observes StartupEvent event) {
        try {
            mediatorManager.initializeAndRun();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
