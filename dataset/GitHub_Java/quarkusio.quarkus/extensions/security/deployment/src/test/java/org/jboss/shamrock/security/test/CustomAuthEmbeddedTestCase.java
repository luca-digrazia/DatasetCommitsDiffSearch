package org.jboss.shamrock.security.test;

import io.restassured.RestAssured;
import io.undertow.servlet.ServletExtension;
import org.jboss.shamrock.test.ShamrockUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.equalTo;

/**
 * Tests of a CUSTOM authentication mechanism that uses the BASIC authentication headers
 */
public class CustomAuthEmbeddedTestCase {
    static Class[] testClasses = {
            TestSecureServlet.class, TestApplication.class, RolesEndpointClassLevel.class,
            ParametrizedPathsResource.class, SubjectExposingResource.class
    };
    @RegisterExtension
    static final ShamrockUnitTest config = new ShamrockUnitTest()
            .setArchiveProducer(() ->
                                        ShrinkWrap.create(JavaArchive.class)
                                                .addClasses(testClasses)
                                                .addClasses(CustomAuth.class, CustomAuthExtension.class, CustomAuthFactory.class)
                                                .addAsManifestResource("microprofile-config-custom-auth-embedded.properties", "microprofile-config.properties")
                                                .addAsManifestResource(new StringAsset(CustomAuthExtension.class.getName()), "services/"+ ServletExtension.class.getName())
            );


    // Basic @ServletSecurity tests
    @Test()
    public void testSecureAccessFailure() {
        RestAssured.when().get("/secure-test").then()
                .statusCode(401);
    }
    @Test()
    public void testSecureRoleFailure() {
        RestAssured.given().auth().preemptive().basic("jdoe", "p4ssw0rd")
                .when().get("/secure-test").then()
                .statusCode(403);
    }
    @Test()
    public void testSecureAccessSuccess() {
        RestAssured.given().auth().preemptive().basic("stuart", "test")
                .when().get("/secure-test").then()
                .statusCode(200);
    }

    /**
     * Test access a secured jaxrs resource without any authentication. should see 401 error code.
     */
    @Test
    public void testJaxrsGetFailure() {
        RestAssured.when().get("/jaxrs-secured/rolesClass").then()
                .statusCode(401);
    }
    /**
     * Test access a secured jaxrs resource with authentication, but no authorization. should see 403 error code.
     */
    @Test
    public void testJaxrsGetRoleFailure() {
        RestAssured.given().auth().preemptive().basic("jdoe", "p4ssw0rd")
                .when().get("/jaxrs-secured/rolesClass").then()
                .statusCode(403);
    }
    /**
     * Test access a secured jaxrs resource with authentication, and authorization. should see 200 success code.
     */
    @Test
    public void testJaxrsGetRoleSuccess() {
        RestAssured.given().auth().preemptive().basic("scott", "jb0ss")
                .when().get("/jaxrs-secured/rolesClass").then()
                .statusCode(200);
    }

    /**
     * Test access a secured jaxrs resource with authentication, and authorization. should see 200 success code.
     */
    @Test
    public void testJaxrsPathAdminRoleSuccess() {
        RestAssured.given().auth().preemptive().basic("scott", "jb0ss")
                .when().get("/jaxrs-secured/parameterized-paths/my/banking/admin").then()
                .statusCode(200);
    }
    @Test
    public void testJaxrsPathAdminRoleFailure() {
        RestAssured.given().auth().preemptive().basic("noadmin", "n0Adm1n")
                .when().get("/jaxrs-secured/parameterized-paths/my/banking/admin").then()
                .statusCode(403);
    }
    /**
     * Test access a secured jaxrs resource with authentication, and authorization. should see 200 success code.
     */
    @Test
    public void testJaxrsPathUserRoleSuccess() {
        RestAssured.given().auth().preemptive().basic("stuart", "test")
                .when().get("/jaxrs-secured/parameterized-paths/my/banking/view").then()
                .statusCode(200);
    }

    /**
     * Test access a secured jaxrs resource with authentication, and authorization. should see 200 success code.
     */
    @Test
    public void testJaxrsUserRoleSuccess() {
        RestAssured.given().auth().preemptive().basic("scott", "jb0ss")
                .when().get("/jaxrs-secured/subject/secured").then()
                .statusCode(200)
                .body(equalTo("scott"));
    }

    /**
     * Test access a @PermitAll secured jaxrs resource without any authentication. should see a 200 success code.
     */
    @Test
    public void testJaxrsGetPermitAll() {
        RestAssured.when().get("/jaxrs-secured/subject/unsecured").then()
                .statusCode(200)
                .body(equalTo("anonymous"));
    }

    /**
     * Test access a @DenyAll secured jaxrs resource without authentication. should see a 401 success code.
     */
    @Test
    public void testJaxrsGetDenyAllWithoutAuth() {
        RestAssured.when().get("/jaxrs-secured/subject/denied").then()
                .statusCode(401);
    }

    /**
     * Test access a @DenyAll secured jaxrs resource with authentication. should see a 403 success code.
     */
    @Test
    public void testJaxrsGetDenyAllWithAuth() {
        RestAssured.given().auth().preemptive().basic("scott", "jb0ss")
                .when().get("/jaxrs-secured/subject/denied").then()
                .statusCode(403);
    }
}
