package com.fasterxml.jackson.module.guava.deser;

import com.google.common.base.Optional;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.annotate.JsonCachable;

import java.io.IOException;

@JsonCachable
public class OptionalDeserializer<T> extends JsonDeserializer<Optional<T>> {
    private final JsonDeserializer<T> elementDeserializer;

    public OptionalDeserializer(JsonDeserializer<T> elementDeserializer) {
        this.elementDeserializer = elementDeserializer;
    }

    @Override
    public Optional<T> deserialize(JsonParser jp,
                                   DeserializationContext ctxt) throws IOException {
        if (jp.getCurrentToken() == JsonToken.VALUE_NULL) {
            return Optional.absent();
        }
        return Optional.fromNullable(elementDeserializer.deserialize(jp, ctxt));
    }

    @Override
    public Optional<T> getNullValue() {
        return Optional.absent();
    }

    @Override
    public Optional<T> getEmptyValue() {
        return Optional.absent();
    }
}
