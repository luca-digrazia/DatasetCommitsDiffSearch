package io.quarkus.qrs.test.resource.basic.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MultipleGetResource {
    private List<String> todoList = new ArrayList<>();

    @GET
    public List<String> getAll() {
        return todoList;
    }

    @GET
    public List<String> findNotCompleted() {
        return todoList;
    }
}
