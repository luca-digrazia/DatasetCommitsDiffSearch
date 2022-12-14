package io.quarkus.rest.runtime.spi;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.MessageBodyReader;

import io.quarkus.rest.runtime.core.LazyMethod;
import io.quarkus.rest.runtime.core.QuarkusRestRequestContext;

public interface QuarkusRestMessageBodyReader<T> extends MessageBodyReader<T> {

    boolean isReadable(Class<?> type, Type genericType,
            LazyMethod lazyMethod, MediaType mediaType);

    T readFrom(Class<T> type, Type genericType, MediaType mediaType,
            QuarkusRestRequestContext context) throws WebApplicationException, IOException;
}
