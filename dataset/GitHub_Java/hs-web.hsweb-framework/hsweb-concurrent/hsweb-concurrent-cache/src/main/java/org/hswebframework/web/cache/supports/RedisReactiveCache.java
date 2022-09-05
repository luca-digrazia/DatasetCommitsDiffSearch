package org.hswebframework.web.cache.supports;

import org.hswebframework.web.cache.ReactiveCache;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.StreamSupport;

@SuppressWarnings("all")
public class RedisReactiveCache<E> implements ReactiveCache<E> {

    private ReactiveRedisOperations<Object, Object> operations;

    private String redisKey;

    private ReactiveCache localCache;

    private String topicName;

    public RedisReactiveCache(String redisKey, ReactiveRedisOperations<Object, Object> operations, ReactiveCache<E> localCache) {
        this.operations = operations;
        this.localCache = localCache;
        this.redisKey = redisKey;
        operations.listenToChannel(topicName = ("_cache_changed:" + redisKey))
                .map(ReactiveSubscription.Message::getMessage)
                .cast(String.class)
                .subscribe(s -> {
                    if (s.equals("___all")) {
                        localCache.clear().subscribe();
                        return;
                    }
                    //清空本地缓存
                    localCache.evict(s).subscribe();
                });
    }

    @Override
    public Flux<E> getFlux(Object key) {
        return localCache
                .getFlux(key)
                .switchIfEmpty(operations
                        .opsForHash()
                        .get(redisKey, key)
                        .flatMapIterable(r -> {
                            if (r instanceof Iterable) {
                                return ((Iterable) r);
                            }
                            return Collections.singletonList(r);
                        })
                        .map(Function.identity()));
    }

    @Override
    public Mono<E> getMono(Object key) {
        return localCache.getMono(key)
                .switchIfEmpty(operations.opsForHash()
                        .get(redisKey, key)
                        .flatMap(r -> localCache.put(key, Mono.just(r))
                                .thenReturn(r)));
    }

    @Override
    public Mono<Void> put(Object key, Publisher<E> data) {
        if (data instanceof Mono) {
            return ((Mono) data)
                    .flatMap(r -> {
                        return operations.opsForHash()
                                .put(redisKey, key, r)
                                .then(localCache.put(key, data))
                                .then(operations.convertAndSend(topicName, key));

                    }).then();
        }
        if (data instanceof Flux) {
            return ((Flux) data)
                    .collectList()
                    .flatMap(r -> {
                        return operations.opsForHash()
                                .put(redisKey, key, r)
                                .then(localCache.put(key, data))
                                .then(operations.convertAndSend(topicName, key));

                    }).then();
        }
        return Mono.error(new UnsupportedOperationException("unsupport publisher:" + data));
    }

    @Override
    public Mono<Void> evictAll(Iterable<?> key) {
        return operations.opsForHash()
                .remove(redisKey, StreamSupport.stream(key.spliterator(), false).toArray())
                .then(localCache.evictAll(key))
                .flatMap(nil -> Flux.fromIterable(key).flatMap(k -> operations.convertAndSend(topicName, key)))
                .then();
    }

    @Override
    public Flux<E> getAll(Object... keys) {
        if (keys.length == 0) {
            return operations
                    .opsForHash()
                    .values(redisKey)
                    .map(r -> (E) r);
        }
        return operations.opsForHash()
                .multiGet(redisKey, Arrays.asList(keys))
                .flatMapIterable(Function.identity())
                .map(r -> (E) r);
    }


    @Override
    public Mono<Void> evict(Object key) {
        return operations
                .opsForHash()
                .remove(redisKey, key)
                .then(localCache.evict(key))
                .then(operations.convertAndSend(topicName, key))
                .then();
    }

    @Override
    public Mono<Void> clear() {
        return operations
                .opsForHash()
                .delete(redisKey)
                .then(localCache.clear())
                .then(operations.convertAndSend(topicName, "___all"))
                .then();
    }
}
