package org.hswebframework.web.authorization.basic.web;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.events.AuthorizationSuccessEvent;
import org.hswebframework.web.authorization.token.ParsedToken;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.context.ContextUtils;
import org.hswebframework.web.logger.ReactiveLogger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class UserTokenWebFilter implements WebFilter, BeanPostProcessor {

    private List<ReactiveUserTokenParser> parsers = new ArrayList<>();

    private Map<String, ReactiveUserTokenGenerator> tokenGeneratorMap = new HashMap<>();

    @Autowired
    private UserTokenManager userTokenManager;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, WebFilterChain chain) {

        return chain.filter(exchange)
                .subscriberContext(ContextUtils.acceptContext(ctx ->
                        Flux.fromIterable(parsers)
                                .flatMap(parser -> parser.parseToken(exchange))
                                .subscribe(token -> ctx.put(ParsedToken.class, token))))
                .subscriberContext(ReactiveLogger.start("requestId", exchange.getRequest().getId()))
                ;
    }

    @EventListener
    public void handleUserSign(AuthorizationSuccessEvent event) {
        ReactiveUserTokenGenerator generator = event.<String>getParameter("tokenType")
                .map(tokenGeneratorMap::get)
                .orElseGet(() -> tokenGeneratorMap.get("default"));
        if (generator != null) {
            GeneratedToken token = generator.generate(event.getAuthentication());
            event.getResult().put("token", token.getToken());
            event.getResult().putAll(token.getResponse());
            userTokenManager.signIn(token.getToken(), token.getType(), event.getAuthentication().getUser().getId(), token.getTimeout())
                    .subscribe(t -> {
                        log.debug("user [{}] sign in", t.getUserId());
                    });
        }

    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ReactiveUserTokenGenerator) {
            ReactiveUserTokenGenerator generator = ((ReactiveUserTokenGenerator) bean);
            tokenGeneratorMap.put(generator.getTokenType(), generator);
        }
        if (bean instanceof ReactiveUserTokenParser) {
            parsers.add(((ReactiveUserTokenParser) bean));
        }
        return bean;
    }
}
