package io.dropwizard.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk7.Jdk7Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

/**
 * A utility class for Jackson.
 */
public class Jackson {
    private Jackson() { /* singleton */ }

    /**
     * Creates a new {@link ObjectMapper} with Guava, Logback, and Joda Time support, as well as
     * support for {@link JsonSnakeCase}. Also includes all {@link Discoverable} interface implementations.
     */
    public static ObjectMapper newObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();

        return configure(mapper);
    }

    /**
     * Creates a new {@link ObjectMapper} with a custom {@link com.fasterxml.jackson.core.JsonFactory}
     * and with support for Guava, Logback, and Joda Time support, as well as
     * support for {@link JsonSnakeCase}. Also includes all {@link Discoverable} interface implementations.
     *
     * @param jsonFactory instance of {@link com.fasterxml.jackson.core.JsonFactory} to use
     *                    for the created {@link com.fasterxml.jackson.databind.ObjectMapper} instance.
     *                    Must not be {@code null}
     */
    public static ObjectMapper newObjectMapper(JsonFactory jsonFactory) {
        final ObjectMapper mapper = new ObjectMapper(jsonFactory);

        return configure(mapper);
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        mapper.registerModule(new GuavaModule());
        mapper.registerModule(new LogbackModule());
        mapper.registerModule(new GuavaExtrasModule());
        mapper.registerModule(new JodaModule());
        mapper.registerModule(new AfterburnerModule());
        mapper.registerModule(new FuzzyEnumModule());
        mapper.registerModule(new Jdk7Module());
        mapper.setPropertyNamingStrategy(new AnnotationSensitivePropertyNamingStrategy());
        mapper.setSubtypeResolver(new DiscoverableSubtypeResolver());

        return mapper;
    }
}
