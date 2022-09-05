package com.yammer.metrics.util;

import java.util.concurrent.*;

public class ThreadPools
{
    private final ConcurrentMap<String, ScheduledExecutorService> threadPools =
            new ConcurrentHashMap<String, ScheduledExecutorService>(100);

    /**
     * Creates a new scheduled thread pool of a given size with the given name,
     * or returns an existing thread pool if one was already created with the
     * same name.
     *
     * @param poolSize the number of threads to create
     * @param name the name of the pool
     * @return a new {@link ScheduledExecutorService}
     */
    public ScheduledExecutorService newScheduledThreadPool(int poolSize, String name) {
        final ScheduledExecutorService existing = threadPools.get(name);
        if (existing == null) {
            // We lock here because executors are expensive to create.
            synchronized (threadPools) {
                final ScheduledExecutorService service = Executors.newScheduledThreadPool(poolSize, new NamedThreadFactory(name));
                threadPools.put(name, service);
                return service;
            }
        } else {
            return existing;
        }
    }

    /**
     * Shuts down all thread pools created by this class in an orderly fashion.
     */
    public void shutdownThreadPools() {
        synchronized (threadPools) {
            for (ExecutorService executor : threadPools.values()) {
                executor.shutdown();
            }
            threadPools.clear();
        }
    }

}
