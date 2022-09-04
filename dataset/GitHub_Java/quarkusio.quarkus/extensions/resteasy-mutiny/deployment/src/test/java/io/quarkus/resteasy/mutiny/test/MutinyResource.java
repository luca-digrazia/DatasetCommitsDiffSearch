package io.quarkus.resteasy.mutiny.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.annotations.Stream;

import io.quarkus.resteasy.mutiny.test.annotations.Async;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("/")
public class MutinyResource {
    @Path("uni")
    @GET
    public Uni<String> uni() {
        return Uni.createFrom().item("hello");
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Path("multi")
    @GET
    @Stream
    public Multi<String> multi() {
        return Multi.createFrom().items("hello", "world");
    }

    @Path("injection")
    @GET
    public Uni<Integer> injection(@Context Integer value) {
        return Uni.createFrom().item(value);
    }

    @Path("injection-async")
    @GET
    public Uni<Integer> injectionAsync(@Async @Context Integer value) {
        return Uni.createFrom().item(value);
    }

    @Path("web-failure")
    @GET
    public Uni<String> failing() {
        return Uni.createFrom().item("not ok")
                .onItem().failWith(s -> new WebApplicationException(
                        Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(s).build()));
    }

    @Path("app-failure")
    @GET
    public Uni<String> failingBecauseOfApplicationCode() {
        return Uni.createFrom().item("not ok")
                .onItem().apply(s -> {
                    throw new IllegalStateException("BOOM!");
                });
    }
}
