package org.jboss.resteasy.reactive.server.providers.serialisers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.common.providers.serialisers.ByteArrayMessageBodyHandler;
import org.jboss.resteasy.reactive.common.providers.serialisers.MessageReaderUtil;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.spi.LazyMethod;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveMessageBodyReader;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveMessageBodyWriter;

@Provider
public class ServerByteArrayMessageBodyHandler extends ByteArrayMessageBodyHandler
        implements ResteasyReactiveMessageBodyWriter<byte[]>, ResteasyReactiveMessageBodyReader<byte[]> {

    @Override
    public boolean isWriteable(Class<?> type, LazyMethod target, MediaType mediaType) {
        return true;
    }

    @Override
    public void writeResponse(byte[] o, ResteasyReactiveRequestContext context) throws WebApplicationException {
        // FIXME: use response encoding
        context.serverResponse().end(o);
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, LazyMethod lazyMethod, MediaType mediaType) {
        return true;
    }

    @Override
    public byte[] readFrom(Class<byte[]> type, Type genericType, MediaType mediaType, InputStream entityStream)
            throws WebApplicationException, IOException {
        return MessageReaderUtil.readBytes(entityStream);
    }
}
