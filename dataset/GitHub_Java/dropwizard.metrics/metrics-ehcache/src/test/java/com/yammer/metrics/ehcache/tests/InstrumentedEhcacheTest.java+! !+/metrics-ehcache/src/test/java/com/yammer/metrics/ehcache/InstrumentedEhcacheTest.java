package com.yammer.metrics.ehcache.tests;

import com.yammer.metrics.MetricRegistry;
import com.yammer.metrics.Timer;
import com.yammer.metrics.ehcache.InstrumentedEhcache;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.junit.Before;
import org.junit.Test;

import static com.yammer.metrics.MetricRegistry.name;
import static org.fest.assertions.api.Assertions.assertThat;

public class InstrumentedEhcacheTest {
    private static final CacheManager MANAGER = CacheManager.create();

    private final MetricRegistry registry = new MetricRegistry();
    private Ehcache cache;

    @Before
    public void setUp() throws Exception {
        final Cache c = new Cache(new CacheConfiguration("test", 100));
        MANAGER.addCache(c);
        this.cache = InstrumentedEhcache.instrument(registry, c);
    }

    @Test
    public void measuresGetsAndPuts() throws Exception {
        cache.get("woo");

        cache.put(new Element("woo", "whee"));

        final Timer gets = registry.timer(name(Cache.class, "test", "gets"));

        assertThat(gets.getCount())
                .isEqualTo(1);

        final Timer puts = registry.timer(name(Cache.class, "test", "puts"));

        assertThat(puts.getCount())
                .isEqualTo(1);
    }
}
