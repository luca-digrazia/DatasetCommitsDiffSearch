package io.quarkus.qrs.runtime.injection;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import io.quarkus.qrs.runtime.core.QrsRequestContext;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class ContextProducers {

    @Inject
    RoutingContext currentVertxRequest;

    @RequestScoped
    @Produces
    UriInfo uriInfo() {
        return getContext().getUriInfo();
    }

    @RequestScoped
    @Produces
    HttpHeaders headers() {
        return getContext().getHttpHeaders();
    }

    private QrsRequestContext getContext() {
        return (QrsRequestContext) currentVertxRequest.data()
                .get(QrsRequestContext.class.getName());
    }
}
