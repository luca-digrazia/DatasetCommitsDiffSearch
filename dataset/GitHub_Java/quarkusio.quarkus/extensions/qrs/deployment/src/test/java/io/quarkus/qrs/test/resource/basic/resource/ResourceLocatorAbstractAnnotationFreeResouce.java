package io.quarkus.qrs.test.resource.basic.resource;

public abstract class ResourceLocatorAbstractAnnotationFreeResouce implements ResourceLocatorRootInterface {

   public String get() {
      return "got";
   }
}
