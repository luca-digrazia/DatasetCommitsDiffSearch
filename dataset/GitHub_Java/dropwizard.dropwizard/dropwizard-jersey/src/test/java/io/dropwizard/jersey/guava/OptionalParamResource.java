package io.dropwizard.jersey.guava;

import com.google.common.base.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.MediaType;

@Path("/optional-param/")
@Produces(MediaType.TEXT_PLAIN)
public class OptionalParamResource {
    @GET
    public String showWithQueryParam(@QueryParam("id") Optional<Integer> id) {
        return id.or(-1).toString();
    }

    @POST
    public String showWithFormParam(@FormParam("id") Optional<Integer> id) {
        return id.or(-1).toString();
    }
}
