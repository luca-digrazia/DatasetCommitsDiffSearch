package org.hswebframework.web.oauth2.server.code;

import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimplePermission;
import org.hswebframework.web.oauth2.server.OAuth2Client;
import org.hswebframework.web.oauth2.server.RedisHelper;
import org.hswebframework.web.oauth2.server.impl.RedisAccessTokenManager;
import org.junit.Test;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.function.BiPredicate;

import static org.junit.Assert.*;

public class DefaultAuthorizationCodeGranterTest {


    @Test
    public void testPermission() {
        BiPredicate<Permission, String> predicate = DefaultAuthorizationCodeGranter.createPredicate("user:info device:query");

        {
            SimplePermission permission=new SimplePermission();
            permission.setId("user");
            permission.setActions(Collections.singleton("info"));


            assertTrue(predicate.test(permission,"info"));
            assertFalse(predicate.test(permission,"info2"));
        }

        {
            SimplePermission permission=new SimplePermission();
            permission.setId("device");
            permission.setActions(Collections.singleton("query"));


            assertTrue(predicate.test(permission,"query"));
            assertFalse(predicate.test(permission,"query2"));
        }

    }

    @Test
    public void testRequestToken() {

        DefaultAuthorizationCodeGranter codeGranter = new DefaultAuthorizationCodeGranter(
                new RedisAccessTokenManager(RedisHelper.factory), RedisHelper.factory
        );

        OAuth2Client client = new OAuth2Client();
        client.setClientId("test");
        client.setClientSecret("test");

        codeGranter
                .requestCode(new AuthorizationCodeRequest(client, new SimpleAuthentication(), Collections.emptyMap()))
                .doOnNext(System.out::println)
                .flatMap(response -> codeGranter
                        .requestToken(new AuthorizationCodeTokenRequest(client, Collections.singletonMap("code", response.getCode()))))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

    }

}