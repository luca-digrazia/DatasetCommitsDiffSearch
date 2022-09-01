package org.jboss.resteasy.reactive.server.spi;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

public interface QuarkusRestContainerRequestFilter extends ContainerRequestFilter {
    @Override
    default void filter(ContainerRequestContext requestContext) throws IOException {
        filter((QuarkusRestContainerRequestContext) requestContext);
    }

    public void filter(QuarkusRestContainerRequestContext requestContext);
}
