package io.quarkus.rest.client.reactive;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.resteasy.reactive.client.handlers.ClientResponseRestHandler;
import org.jboss.resteasy.reactive.client.impl.ClientRequestContextImpl;
import org.jboss.resteasy.reactive.client.impl.ClientResponseContextImpl;
import org.jboss.resteasy.reactive.common.jaxrs.ResponseImpl;

public class
MicroProfileRestClientResponseFilter implements ClientResponseFilter {
    private final List<ResponseExceptionMapper<?>> exceptionMappers;

    public MicroProfileRestClientResponseFilter(List<ResponseExceptionMapper<?>> exceptionMappers) {
        if (exceptionMappers == null) {
            throw new NullPointerException("exceptionMappers cannot be null");
        }
        this.exceptionMappers = exceptionMappers;
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        for (ResponseExceptionMapper exceptionMapper : exceptionMappers) {
            if (exceptionMapper.handles(responseContext.getStatus(), responseContext.getHeaders())) {
                // we have an exception mapper, we don't need the response anymore, we can map it to response right away (I hope :D)
                ResponseImpl response = ClientResponseRestHandler.mapToResponse(
                        ((ClientRequestContextImpl) requestContext).getRestClientRequestContext(),
                        (ClientResponseContextImpl) responseContext);
                Throwable throwable = exceptionMapper.toThrowable(response);
                if (throwable != null) {
                    throw new ProcessingException(throwable);
                }
            }
        }
    }
}
