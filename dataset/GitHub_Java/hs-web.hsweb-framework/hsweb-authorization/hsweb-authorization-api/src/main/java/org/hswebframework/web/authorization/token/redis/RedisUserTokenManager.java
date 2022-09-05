package org.hswebframework.web.authorization.token.redis;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.hswebframework.web.authorization.token.AllopatricLoginMode;
import org.hswebframework.web.authorization.token.TokenState;
import org.hswebframework.web.authorization.token.UserToken;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class RedisUserTokenManager implements UserTokenManager {

    private ReactiveRedisOperations<Object, Object> operations;

    private ReactiveHashOperations<Object, String, Object> userTokenStore;

    private ReactiveSetOperations<Object, Object> userTokenMapping;

    public RedisUserTokenManager(ReactiveRedisOperations<Object, Object> operations) {
        this.operations = operations;
        this.userTokenStore = operations.opsForHash();
        this.userTokenMapping = operations.opsForSet();
    }

    @Getter
    @Setter
    private Map<String, AllopatricLoginMode> allopatricLoginModes = new HashMap<>();

    @Getter
    @Setter
    //异地登录模式，默认允许异地登录
    private AllopatricLoginMode allopatricLoginMode = AllopatricLoginMode.allow;

    private String getTokenRedisKey(String key) {
        return "user-token:".concat(key);
    }

    private String getUserRedisKey(String key) {
        return "user-token-user:".concat(key);
    }

    @Override
    public Mono<UserToken> getByToken(String token) {
        return userTokenStore
                .entries(getTokenRedisKey(token))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .filter(map -> !map.isEmpty())
                .map(SimpleUserToken::of);
    }

    @Override
    public Flux<UserToken> getByUserId(String userId) {
        String redisKey = getUserRedisKey(userId);
        return userTokenMapping
                .members(redisKey)
                .map(String::valueOf)
                .flatMap(token -> getByToken(token)
                        .switchIfEmpty(Mono.defer(() -> userTokenMapping
                                .remove(redisKey, userId)
                                .then(Mono.empty()))));
    }

    @Override
    public Mono<Boolean> userIsLoggedIn(String userId) {
        return getByUserId(userId)
                .hasElements();
    }

    @Override
    public Mono<Boolean> tokenIsLoggedIn(String token) {
        return operations.hasKey(getTokenRedisKey(token));
    }

    @Override
    public Mono<Integer> totalUser() {

        return totalToken();
    }

    @Override
    public Mono<Integer> totalToken() {
        return operations.scan(ScanOptions
                .scanOptions()
                .match("user-token:*")
                .build())
                .count()
                .map(Long::intValue);
    }

    @Override
    public Flux<UserToken> allLoggedUser() {
        return operations.scan(ScanOptions
                .scanOptions()
                .match("user-token:*")
                .build())
                .map(String::valueOf)
                .flatMap(this::getByToken);
    }

    @Override
    public Mono<Void> signOutByUserId(String userId) {
        String key = getUserRedisKey(userId);
        return getByUserId(key)
                .map(UserToken::getToken)
                .map(this::getTokenRedisKey)
                .concatWithValues(key)
                .as(operations::delete)
                .then();
    }

    @Override
    public Mono<Void> signOutByToken(String token) {
        //delete token
        // srem user token
        return getByToken(token)
                .flatMap(t -> operations.delete(getTokenRedisKey(t.getToken()))
                        .then(userTokenMapping.remove(getUserRedisKey(t.getToken()),token))).then();
    }

    @Override
    public Mono<Void> changeUserState(String userId, TokenState state) {

        return getByUserId(userId)
                .flatMap(token -> changeTokenState(token.getToken(), state))
                .then();
    }

    @Override
    public Mono<Void> changeTokenState(String token, TokenState state) {
        return userTokenStore
                .put(getTokenRedisKey(token), "state", state.getValue())
                .then();
    }

    @Override
    public Mono<UserToken> signIn(String token, String type, String userId, long maxInactiveInterval) {
        return Mono.defer(() -> {
            Mono<UserToken> doSign = Mono.defer(() -> {
                Map<String, Object> map = new HashMap<>();
                map.put("token", token);
                map.put("type", type);
                map.put("userId", userId);
                map.put("maxInactiveInterval", maxInactiveInterval);
                map.put("state", TokenState.normal.getValue());
                map.put("signInTime", System.currentTimeMillis());
                map.put("lastRequestTime", System.currentTimeMillis());

                String key = getTokenRedisKey(token);
                return userTokenStore
                        .putAll(key, map)
                        .then(Mono.defer(() -> {
                            if (maxInactiveInterval > 0) {
                                return operations.expire(key, Duration.ofMillis(maxInactiveInterval));
                            }
                            return Mono.empty();
                        }))
                        .then(userTokenMapping.add(getUserRedisKey(userId), token))
                        .thenReturn(SimpleUserToken.of(map));
            });

            AllopatricLoginMode mode = allopatricLoginModes.getOrDefault(type, allopatricLoginMode);
            if (mode == AllopatricLoginMode.deny) {
                return userIsLoggedIn(userId)
                        .flatMap(r -> {
                            if (r) {
                                return Mono.error(new AccessDenyException("已在其他地方登录", TokenState.deny.getValue(), null));
                            }
                            return doSign;
                        });

            } else if (mode == AllopatricLoginMode.offlineOther) {
                return getByUserId(userId)
                        .flatMap(userToken -> {
                            if (type.equals(userToken.getType())) {
                                return this.changeTokenState(userToken.getToken(), TokenState.offline);
                            }
                            return Mono.empty();
                        })
                        .then(doSign);
            }

            return doSign;
        });
    }


    @Override
    public Mono<Void> touch(String token) {
        return getByToken(token)
                .flatMap(userToken -> {
                    if (userToken.getMaxInactiveInterval() > 0) {
                        return userTokenStore
                                .increment(getTokenRedisKey(token), token, 1L)
                                .then(operations
                                        .expire(getTokenRedisKey(token), Duration.ofMillis(userToken.getMaxInactiveInterval()))
                                        .then());
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> checkExpiredToken() {

        return operations.scan(ScanOptions
                .scanOptions()
                .match("user-token-user:*").build())
                .map(String::valueOf)
                .flatMap(key -> userTokenMapping.members(key)
                        .map(String::valueOf)
                        .flatMap(token -> operations.hasKey(getTokenRedisKey(token))
                                .flatMap(exists -> {
                                    if (!exists) {
                                        return userTokenMapping.remove(key, token);
                                    }
                                    return Mono.empty();
                                })))
                .then();
    }
}
