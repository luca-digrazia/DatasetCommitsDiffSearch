package io.quarkus.qrs.test.simple;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/")
public class RootBResource {

    @GET
    @Path("b")
    public String b() {
        return "b";
    }

}
