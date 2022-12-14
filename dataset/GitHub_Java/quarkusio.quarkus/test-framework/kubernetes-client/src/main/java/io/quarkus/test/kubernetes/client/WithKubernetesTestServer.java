package io.quarkus.test.kubernetes.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.quarkus.test.common.QuarkusTestResource;

/**
 * Use on your test resource to get a mock {@link KubernetesServer} spawn up, and injectable with {@link KubernetesTestServer}.
 * This annotation is only active when used on a test class, and only for this test class.
 */
@QuarkusTestResource(value = KubernetesServerTestResource.class, restrictToAnnotatedTest = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WithKubernetesTestServer {

    /**
     * Start it with HTTPS
     */
    boolean https() default false;

    /**
     * Start it in CRUD mode
     */
    boolean crud() default true;

    /**
     * Port to use, defaults to any available port
     */
    int port() default 0;

}
