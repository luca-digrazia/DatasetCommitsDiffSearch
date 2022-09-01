package io.quarkus.restclient.registerprovider;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/echo")
public class EchoResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String echo(@QueryParam("message") String message) {
        return message;
    }
}