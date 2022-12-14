package io.quarkus.rest.deployment.processor;

import java.util.Map;
import java.util.Objects;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.rest.ExceptionMapper;

final class ClassLevelExceptionMappersBuildItem extends SimpleBuildItem {

    /**
     * The key is the DotName of the class which contains methods annotated with {@link ExceptionMapper}
     * and the value is a map of from exception class name to generated exception mapper class name
     */
    private final Map<DotName, Map<String, String>> mappers;

    ClassLevelExceptionMappersBuildItem(Map<DotName, Map<String, String>> mappers) {
        this.mappers = Objects.requireNonNull(mappers);
    }

    public Map<DotName, Map<String, String>> getMappers() {
        return mappers;
    }
}
