package com.yammer.metrics;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Sample} implementation backed by a sliding window that stores only the measurements made
 * in the last {@code N} seconds (or other time unit).
 */
public class SlidingTimeWindowSample implements Sample {
    // allow for this many duplicate ticks before overwriting measurements
    private static final int COLLISION_BUFFER = 100;

    private final Clock clock;
    private final ConcurrentSkipListMap<Long, Long> measurements;
    private final long window;
    private final AtomicLong lastTick;

    /**
     * Creates a new {@link SlidingTimeWindowSample} with the given window of time.
     *
     * @param window     the window of time
     * @param windowUnit the unit of {@code window}
     */
    public SlidingTimeWindowSample(long window, TimeUnit windowUnit) {
        this(window, windowUnit, Clock.defaultClock());
    }

    /**
     * Creates a new {@link SlidingTimeWindowSample} with the given clock and window of time.
     *
     * @param window     the window of time
     * @param windowUnit the unit of {@code window}
     * @param clock      the {@link Clock} to use
     */
    public SlidingTimeWindowSample(long window, TimeUnit windowUnit, Clock clock) {
        this.clock = clock;
        this.measurements = new ConcurrentSkipListMap<Long, Long>();
        this.window = windowUnit.toNanos(window) * COLLISION_BUFFER;
        this.lastTick = new AtomicLong();
    }

    @Override
    public int size() {
        trim();
        return measurements.size();
    }

    @Override
    public void update(long value) {
        measurements.put(getTick(), value);
        trim();
    }

    @Override
    public Snapshot getSnapshot() {
        trim();
        return new Snapshot(measurements.values());
    }

    private long getTick() {
        for (; ; ) {
            final long oldTick = lastTick.get();
            final long tick = clock.getTick() * COLLISION_BUFFER;
            // ensure the tick is strictly incrementing even if there are duplicate ticks
            final long newTick = tick > oldTick ? tick : oldTick + 1;
            if (lastTick.compareAndSet(oldTick, newTick)) {
                return newTick;
            }
        }
    }

    private void trim() {
        measurements.headMap(getTick() - window).clear();
    }
}
