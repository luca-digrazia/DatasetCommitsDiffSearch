package org.hswebframework.web.cache.supports;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import org.hswebframework.web.cache.ReactiveCache;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;

@SuppressWarnings("all")
@AllArgsConstructor
public class CaffeineReactiveCache<E> implements ReactiveCache<E> {

    private Cache<Object, Object> cache;

    @Override
    public Flux<E> getFlux(Object key) {
        return (Flux) Flux.defer(() -> {
            Object v = cache.getIfPresent(key);
            if (v == null) {
                return Flux.empty();
            }
            if (v instanceof Iterable) {
                return Flux.fromIterable(((Iterable) v));
            }
            return Flux.just(v);
        });
    }

    @Override
    public Mono<E> getMono(Object key) {
        return Mono.defer(() -> {
            Object v = cache.getIfPresent(key);
            if (v == null) {
                return Mono.empty();
            }
            return (Mono) Mono.just(v);
        });
    }

    @Override
    public Mono<Void> put(Object key, Publisher<E> data) {
        return Mono.defer(() -> {
            if (data instanceof Flux) {
                return ((Flux<E>) data).collectList()
                        .doOnNext(v -> cache.put(key, v))
                        .then();
            }
            if (data instanceof Mono) {
                return ((Mono<E>) data)
                        .doOnNext(v -> cache.put(key, v))
                        .then();
            }
            return Mono.error(new UnsupportedOperationException("unsupport publisher:" + data));
        });
    }

    @Override
    public Mono<Void> evictAll(Iterable<?> key) {
        return Mono.fromRunnable(() -> cache.invalidateAll(key));
    }

    @Override
    public Flux<E> getAll(Object... keys) {
        return Flux.<E>defer(() -> {
            return Flux.fromIterable(cache.getAllPresent(Arrays.asList(keys)).values())
                    .map(e -> (E) e);
        });
    }

    @Override
    public Mono<Void> evict(Object key) {
        return Mono.fromRunnable(() -> cache.invalidate(key));
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> cache.invalidateAll());
    }
}
