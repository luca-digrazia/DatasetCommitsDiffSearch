package com.yammer.dropwizard.auth.oauth;

import com.google.common.base.Optional;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.HttpRequestContext;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.User;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OAuthInjectableTest {

    private final Authenticator<String, User> authenticator = new Authenticator<String, User>() {
        @Override
        public Optional<User> authenticate(String credentials) throws AuthenticationException {
            if ("good".equals(credentials)) {
                return Optional.of(new User(credentials));
            }

            if ("bad".equals(credentials)) {
                throw new AuthenticationException("OH NOE");
            }

            return Optional.absent();
        }
    };

    private final OAuthProvider<User> provider = new OAuthProvider<User>(authenticator, "Realm");

    private AbstractHttpContextInjectable<User> required;
    private AbstractHttpContextInjectable<User> optional;

    private final HttpContext context = mock(HttpContext.class);
    private final HttpRequestContext requestContext = mock(HttpRequestContext.class);

    @Before
    public void setUp() throws Exception {
        when(context.getRequest()).thenReturn(requestContext);

        final Auth requiredAuth = mock(Auth.class);
        when(requiredAuth.required()).thenReturn(true);

        final Auth optionalAuth = mock(Auth.class);
        when(optionalAuth.required()).thenReturn(false);

        this.required = (AbstractHttpContextInjectable<User>) provider.getInjectable(null, requiredAuth, null);
        this.optional = (AbstractHttpContextInjectable<User>) provider.getInjectable(null, optionalAuth, null);
    }

    @Test
    public void requiredAuthWithMissingHeaderReturnsUnauthorized() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn(null);

        try {
            required.getValue(context);
            fail("should have thrown a WebApplicationException but didn't");
        } catch (WebApplicationException e) {
            assertUnauthorized(e);
        }
    }

    @Test
    public void optionalAuthWithMissingHeaderReturnsNull() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn(null);

        assertThat(optional.getValue(context),
                   is(nullValue()));
    }

    @Test
    public void requiredAuthWithBadSchemeReturnsUnauthorized() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Barer WAUGH");

        try {
            required.getValue(context);
            fail("should have thrown a WebApplicationException but didn't");
        } catch (WebApplicationException e) {
            assertUnauthorized(e);
        }
    }

    @Test
    public void optionalAuthWithBadSchemeReturnsNull() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Barer WAUGH");

        assertThat(optional.getValue(context),
                   is(nullValue()));
    }

    @Test
    public void requiredAuthWithNoSchemeReturnsUnauthorized() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Barer_WAUGH");

        try {
            required.getValue(context);
            fail("should have thrown a WebApplicationException but didn't");
        } catch (WebApplicationException e) {
            assertUnauthorized(e);
        }
    }

    @Test
    public void optionalAuthWithNoSchemeReturnsNull() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Barer_WAUGH");

        assertThat(optional.getValue(context),
                   is(nullValue()));
    }

    @Test
    public void requiredAuthWithBadCredsReturnsUnauthorized() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Bearer WAUGH");

        try {
            required.getValue(context);
            fail("should have thrown a WebApplicationException but didn't");
        } catch (WebApplicationException e) {
            assertUnauthorized(e);
        }
    }

    @Test
    public void optionalAuthWithBadCredsReturnsNull() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Bearer WAUGH");

        assertThat(optional.getValue(context),
                   is(nullValue()));
    }

    @Test
    public void requiredAuthWithGoodCredsReturnsAUser() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Bearer good");
        
        assertThat(required.getValue(context),
                   is(new User("good")));
    }

    @Test
    public void optionalAuthWithGoodCredsReturnsAUser() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Bearer good");

        assertThat(optional.getValue(context),
                   is(new User("good")));
    }

    @Test
    public void authenticatorFailureReturnsInternalServerError() throws Exception {
        when(requestContext.getHeaderValue("Authorization")).thenReturn("Bearer bad");

        try {
            required.getValue(context);
            fail("should have thrown a WebApplicationException but didn't");
        } catch (WebApplicationException e) {
            assertThat(e.getResponse().getStatus(),
                       is(500));
        }
    }

    private void assertUnauthorized(WebApplicationException e) {
        final Response response = e.getResponse();

        assertThat(response.getStatus(),
                   is(401));

        assertThat(response.getMetadata().getFirst("WWW-Authenticate").toString(),
                   is("Bearer realm=\"Realm\""));

        assertThat(response.getMetadata().getFirst("Content-Type").toString(),
                   is("text/plain"));

        assertThat(response.getEntity().toString(),
                   is("Credentials are required to access this resource."));
    }
}
