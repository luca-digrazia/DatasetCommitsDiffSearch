package io.quarkus.registry.catalog;

import java.util.Collection;
import java.util.Map;

public interface Platform {

    String getPlatformKey();

    String getName();

    Collection<PlatformStream> getStreams();

    Map<String, Object> getMetadata();

    PlatformStream getStream(String id);

    default PlatformStream getRecommendedStream() {
        final Collection<PlatformStream> streams = getStreams();
        if (streams.isEmpty()) {
            throw new RuntimeException("Platform " + getPlatformKey() + " does not include any stream");
        }
        return streams.iterator().next();
    }
}
