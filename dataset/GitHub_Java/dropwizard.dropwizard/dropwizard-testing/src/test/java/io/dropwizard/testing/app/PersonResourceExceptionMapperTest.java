package io.dropwizard.testing.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.JerseyViolationException;
import io.dropwizard.testing.junit.ResourceTestRule;
import io.dropwizard.util.Strings;
import org.glassfish.jersey.spi.ExtendedExceptionMapper;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class PersonResourceExceptionMapperTest {
    private static final PeopleStore PEOPLE_STORE = mock(PeopleStore.class);

    private static final ObjectMapper OBJECT_MAPPER = Jackson.newObjectMapper()
        .registerModule(new GuavaModule());

    @SuppressWarnings("deprecation")
    @ClassRule
    public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
        .addResource(new PersonResource(PEOPLE_STORE))
        .setRegisterDefaultExceptionMappers(false)
        .addProvider(new MyJerseyExceptionMapper())
        .addProvider(new GenericExceptionMapper())
        .setMapper(OBJECT_MAPPER)
        .build();

    @Test
    void testDefaultConstraintViolation() {
        assertThat(RESOURCES.target("/person/blah/index")
            .queryParam("ind", -1).request()
            .get().readEntity(String.class))
            .isEqualTo("Invalid data");
    }

    @Test
    void testDefaultJsonProcessingMapper() {
        assertThat(RESOURCES.target("/person/blah/runtime-exception")
            .request()
            .post(Entity.json("{ \"he: \"ho\"}"))
            .readEntity(String.class))
            .startsWith("Something went wrong: Unexpected character");
    }

    @Test
    void testDefaultExceptionMapper() {
        assertThat(RESOURCES.target("/person/blah/runtime-exception")
            .request()
            .post(Entity.json("{}"))
            .readEntity(String.class))
            .isEqualTo("Something went wrong: I'm an exception!");
    }

    @Test
    void testDefaultEofExceptionMapper() {
        assertThat(RESOURCES.target("/person/blah/eof-exception")
            .request()
            .get().readEntity(String.class))
            .isEqualTo("Something went wrong: I'm an eof exception!");
    }

    private static class MyJerseyExceptionMapper implements ExceptionMapper<JerseyViolationException> {
        @Override
        public Response toResponse(JerseyViolationException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("Invalid data")
                .build();
        }
    }

    private static class GenericExceptionMapper implements ExtendedExceptionMapper<Throwable> {
        @Override
        public boolean isMappable(Throwable throwable) {
            return !(throwable instanceof JerseyViolationException);
        }

        @Override
        public Response toResponse(Throwable exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("Something went wrong: " + Strings.nullToEmpty(exception.getMessage()))
                .build();
        }
    }
}
