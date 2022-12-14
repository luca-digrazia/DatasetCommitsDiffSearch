package org.hswebframework.web.socket.handler;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.container.AuthenticationContainer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.function.Function;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
public class WebSocketUtils {


    public static Authentication getAuthentication(AuthenticationContainer container, WebSocketSession session) {
        Authentication authentication = Authentication
                .current()
                .orElseGet(() -> ((Authentication) session.getAttributes().get(Authentication.class.getName())));

        if (authentication != null) return authentication;
        HttpHeaders headers = session.getHandshakeHeaders();
        List<String> cookies = headers.get("Cookie");
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        String[] cookie = cookies.get(0).split("[;]");
        Map<String, Set<String>> sessionId = new HashMap<>();
        for (String aCookie : cookie) {
            String[] tmp = aCookie.split("[=]");
            if (tmp.length == 2)
                sessionId.computeIfAbsent(tmp[0].trim(), k -> new HashSet<>())
                        .add(tmp[1].trim());
        }

        Function<Set<String>, Optional<Authentication>> userGetter = set ->
                set == null ? Optional.empty() : set.stream()
                        .map(container::getByToken)
                        .filter(Objects::nonNull).findFirst();

        return userGetter.apply(sessionId.get("SESSION"))
                .orElseGet(() -> userGetter.apply(sessionId.get("JSESSIONID")).orElse(null));

    }
}
