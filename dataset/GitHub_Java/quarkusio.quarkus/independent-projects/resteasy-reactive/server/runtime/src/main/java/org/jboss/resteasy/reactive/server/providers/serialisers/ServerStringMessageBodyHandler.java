package org.jboss.resteasy.reactive.server.providers.serialisers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.common.providers.serialisers.StringMessageBodyHandler;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.spi.LazyMethod;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveMessageBodyReader;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveMessageBodyWriter;

@Provider
public class ServerStringMessageBodyHandler extends StringMessageBodyHandler
        implements ResteasyReactiveMessageBodyWriter<Object>, ResteasyReactiveMessageBodyReader<String> {

    @Override
    public boolean isWriteable(Class<?> type, LazyMethod target, MediaType mediaType) {
        return true;
    }

    @Override
    public void writeResponse(Object o, ResteasyReactiveRequestContext context) throws WebApplicationException {
        // FIXME: use response encoding
        context.serverResponse().end(o.toString());
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, LazyMethod lazyMethod, MediaType mediaType) {
        return type.equals(String.class);
    }

    @Override
    public String readFrom(Class<String> type, Type genericType, MediaType mediaType, InputStream entityStream)
            throws WebApplicationException, IOException {
        return readFrom(entityStream, true);
    }
}
