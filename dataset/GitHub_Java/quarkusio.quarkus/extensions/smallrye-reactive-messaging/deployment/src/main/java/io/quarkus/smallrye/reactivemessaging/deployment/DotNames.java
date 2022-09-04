package io.quarkus.smallrye.reactivemessaging.deployment;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.jandex.DotName;

import io.smallrye.reactive.messaging.annotations.Broadcast;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import io.smallrye.reactive.messaging.annotations.Merge;
import io.smallrye.reactive.messaging.annotations.OnOverflow;

public final class DotNames {

    static final DotName VOID = DotName.createSimple(void.class.getName());
    static final DotName INCOMING = DotName.createSimple(Incoming.class.getName());
    static final DotName OUTGOING = DotName.createSimple(Outgoing.class.getName());
    static final DotName CHANNEL = DotName.createSimple(org.eclipse.microprofile.reactive.messaging.Channel.class.getName());
    static final DotName DEPRECATED_CHANNEL = DotName.createSimple(Channel.class.getName());
    static final DotName EMITTER = DotName.createSimple(org.eclipse.microprofile.reactive.messaging.Emitter.class.getName());
    static final DotName DEPRECATED_EMITTER = DotName.createSimple(Emitter.class.getName());
    static final DotName ON_OVERFLOW = DotName
            .createSimple(org.eclipse.microprofile.reactive.messaging.OnOverflow.class.getName());
    static final DotName DEPRECATED_ON_OVERFLOW = DotName.createSimple(OnOverflow.class.getName());
    static final DotName ACKNOWLEDGMENT = DotName.createSimple(Acknowledgment.class.getName());
    static final DotName MERGE = DotName.createSimple(Merge.class.getName());
    static final DotName BROADCAST = DotName.createSimple(Broadcast.class.getName());

    private DotNames() {
    }

}
