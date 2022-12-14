package org.graylog2.plugin.rest;

import org.glassfish.jersey.spi.ExtendedExceptionMapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class WebApplicationExceptionMapper implements ExtendedExceptionMapper<WebApplicationException> {
    @Override
    public boolean isMappable(WebApplicationException e) {
        return true;
    }

    @Override
    public Response toResponse(WebApplicationException exception) {
        return Response.fromResponse(exception.getResponse()).entity(exception.getMessage()).build();
    }
}
