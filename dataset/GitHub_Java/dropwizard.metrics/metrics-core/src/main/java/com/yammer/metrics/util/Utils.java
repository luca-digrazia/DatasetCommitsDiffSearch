package com.yammer.metrics.util;

import com.yammer.metrics.MetricsRegistry;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

public class Utils {
    private static final ThreadPools THREAD_POOLS = new ThreadPools();

    private Utils() { /* unused */ }

    public static Map<String, Map<String, Metric>> sortMetrics(Map<MetricName, Metric> metrics) {
        final Map<String, Map<String, Metric>> sortedMetrics =
                new TreeMap<String, Map<String, Metric>>();
        for (Entry<MetricName, Metric> entry : metrics.entrySet()) {
            final String qualifiedTypeName = entry.getKey().getGroup() + "." + entry.getKey().getType();
            final String scopedName;
            if (entry.getKey().hasScope()) {
                scopedName = qualifiedTypeName + "." + entry.getKey().getScope();
            } else {
                scopedName = qualifiedTypeName;
            }
            Map<String, Metric> subMetrics = sortedMetrics.get(scopedName);
            if (subMetrics == null) {
                subMetrics = new TreeMap<String, Metric>();
                sortedMetrics.put(scopedName, subMetrics);
            }
            subMetrics.put(entry.getKey().getName(), entry.getValue());
        }
        return sortedMetrics;
    }

    /**
     * Creates a new scheduled thread pool of a given size with the given name.
     *
     * @param poolSize the number of threads to create
     * @param name the name of the pool
     * @return a new {@link ScheduledExecutorService}
     * @deprecated Get a thread pool via {@link MetricsRegistry#threadPools()} instead
     */
    public static ScheduledExecutorService newScheduledThreadPool(int poolSize, String name) {
        return THREAD_POOLS.newScheduledThreadPool(poolSize, name);
    }

    /**
     * Shuts down all thread pools created by this class in an orderly fashion.
     * @deprecated Shut down the thread pools object of the relevant {@link MetricsRegistry} instead
     */
    public static void shutdownThreadPools() {
        THREAD_POOLS.shutdownThreadPools();
    }
}
