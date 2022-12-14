package io.quarkus.qrs.test.resource.basic.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

@Path("/")
public class UriInfoRelativizeResource {
   @Produces("text/plain")
   @GET
   @Path("{path : .*}")
   public String relativize(@Context UriInfo info, @QueryParam("to") String to) {
      return info.relativize(URI.create(to)).toString();
   }
}
