package io.quarkus.qrs.test.resource.basic.resource;

import javax.ws.rs.Path;

@Path("blah")
public class SubResourceLocatorImpFoo implements SubResourceLocatorFoo {
   @Override
   public Object getFoo(String val) {
      return "hello";
   }
}
