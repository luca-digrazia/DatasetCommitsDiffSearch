package org.graylog2.streams;

import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.graylog2.Configuration;
import org.graylog2.notifications.NotificationService;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.plugin.streams.StreamRule;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class CachedStreamRouter extends StreamRouter {
    private static final AtomicReference<LoadingCache<String, List<Stream>>> CACHED_STREAMS = new AtomicReference<>();
    private static final AtomicReference<LoadingCache<Stream, List<StreamRule>>> CACHED_STREAM_RULES = new AtomicReference<>();
    private final LoadingCache<String, List<Stream>> cachedStreams;
    private final LoadingCache<Stream, List<StreamRule>> cachedStreamRules;

    @Inject
    public CachedStreamRouter(StreamService streamService,
                              StreamRuleService streamRuleService,
                              MetricRegistry metricRegistry,
                              Configuration configuration,
                              NotificationService notificationService) {
        super(streamService, streamRuleService, metricRegistry, configuration, notificationService);

        CACHED_STREAMS.compareAndSet(null, buildStreamsLoadingCache());
        CACHED_STREAM_RULES.compareAndSet(null, buildStreamRulesLoadingCache());

        // The getStreams and getStreamRules methods might be called multiple times per message. Avoid contention on the
        // AtomicReference by storing the LoadingCaches in a field.
        cachedStreams = CACHED_STREAMS.get();
        cachedStreamRules = CACHED_STREAM_RULES.get();
    }

    private LoadingCache<String, List<Stream>> buildStreamsLoadingCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(1, TimeUnit.SECONDS)
                .build(
                        new CacheLoader<String, List<Stream>>() {
                            @Override
                            public List<Stream> load(final String s) throws Exception {
                                return superGetStreams();
                            }
                        }
                );
    }

    private LoadingCache<Stream, List<StreamRule>> buildStreamRulesLoadingCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.SECONDS)
                .build(
                        new CacheLoader<Stream, List<StreamRule>>() {
                            @Override
                            public List<StreamRule> load(final Stream s) throws Exception {
                                return superGetStreamRules(s);
                            }
                        }
                );
    }

    @Override
    protected List<Stream> getStreams() {
        List<Stream> result = null;
        try {
            result = cachedStreams.get("streams");
        } catch (ExecutionException e) {
            LOG.error("Caught exception while fetching from cache", e);
        }
        return result;
    }

    private List<Stream> superGetStreams() {
        return super.getStreams();
    }

    @Override
    protected List<StreamRule> getStreamRules(Stream stream) {
        List<StreamRule> result = null;
        try {
            result = cachedStreamRules.get(stream);
        } catch (ExecutionException e) {
            LOG.error("Caught exception while fetching from cache", e);
        }

        return result;
    }

    private List<StreamRule> superGetStreamRules(Stream stream) {
        return super.getStreamRules(stream);
    }
}
