package io.quarkus.qrs.test.resource.basic.resource;

import javax.ws.rs.Path;

@Path(value = "/InheritanceTest")
public class InheritenceParentResourceImpl implements InheritenceParentResource {

   public String firstest() {
      return "First";
   }
}
