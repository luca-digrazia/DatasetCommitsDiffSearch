package io.quarkus.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

/**
 * When used on a method, then an implementation of {@link javax.ws.rs.container.ContainerResponseContext} is generated
 * that calls the annotated method with the proper arguments
 *
 * The idea behind using this is to make it much to write a {@code ContainerResponseFilter} as all the necessary information
 * is passed as arguments to the method instead of forcing the author to use a mix of {@code @Context} and programmatic CDI
 * look-ups.
 * <p>
 * An example filter could look like this:
 *
 * <pre>
 * public class CustomContainerResponseFilter {
 *
 *     private final SomeBean someBean;
 *
 *     // SomeBean will be automatically injected by CDI as long as SomeBean is a bean itself
 *     public CustomContainerResponseFilter(SomeBean someBean) {
 *         this.someBean = someBean;
 *     }
 *
 *     &#64;ContainerResponseFilter
 *     public void whatever(SimplifiedResourceInfo resourceInfo) {
 *         // do something
 *     }
 * }
 * </pre>
 *
 * Methods annotated with {@code ContainerRequestFilter} can declare any of the following parameters (in any order)
 * <ul>
 * <li>{@link ContainerRequestContext}
 * <li>{@link ContainerResponseContext}
 * <li>{@link HttpServerRequest}
 * <li>{@link HttpServerResponse}
 * <li>{@link ResourceInfo}
 * <li>{@link io.quarkus.rest.server.runtime.spi.SimplifiedResourceInfo}
 * <li>{@link Throwable} - The thrown exception - or {@code null} if no exception was thrown
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ContainerResponseFilter {

    /**
     * The priority with which this response filter will be executed
     */
    int priority() default Priorities.USER;
}
