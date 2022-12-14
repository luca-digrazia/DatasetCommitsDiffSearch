package com.yammer.metrics.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.yammer.metrics.core.HistogramMetric.SampleType;

/**
 * A timer metric which aggregates timing durations and provides duration
 * statistics, plus throughput statistics via {@link MeterMetric}.
 *
 * @author coda
 */
public class TimerMetric implements Metered {
    private final TimeUnit durationUnit, rateUnit;
    private final MeterMetric meter;
    private final HistogramMetric histogram = new HistogramMetric(SampleType.BIASED);

    /**
     * Creates a new {@link TimerMetric}.
     *
     * @param durationUnit the scale unit for this timer's duration metrics
     * @param rateUnit the scale unit for this timer's rate metrics
     */
    public TimerMetric(TimeUnit durationUnit, TimeUnit rateUnit) {
        this.durationUnit = durationUnit;
        this.rateUnit = rateUnit;
        this.meter = MeterMetric.newMeter("calls", rateUnit);
        clear();
    }

    /**
     * Returns the timer's duration scale unit.
     *
     * @return the timer's duration scale unit
     */
    public TimeUnit durationUnit() {
        return durationUnit;
    }

    @Override
    public TimeUnit rateUnit() {
        return rateUnit;
    }

    /**
     * Clears all recorded durations.
     */
    public void clear() {
        histogram.clear();
    }

    /**
     * Adds a recorded duration.
     *
     * @param duration the length of the duration
     * @param unit the scale unit of {@code duration}
     */
    public void update(long duration, TimeUnit unit) {
        update(unit.toNanos(duration));
    }

    /**
     * Times and records the duration of event.
     *
     * @param event a {@link Callable} whose {@link Callable#call()} method
     * implements a process whose duration should be timed
     * @param <T> the type of the value returned by {@code event}
     * @return the value returned by {@code event}
     * @throws Exception if {@code event} throws an {@link Exception}
     */
    public <T> T time(Callable<T> event) throws Exception {
        final long startTime = System.nanoTime();
        try {
            return event.call();
        } finally {
            update(System.nanoTime() - startTime);
        }
    }

    @Override
    public long count() { return histogram.count(); }

    @Override
    public double fifteenMinuteRate() { return meter.fifteenMinuteRate(); }

    @Override
    public double fiveMinuteRate() { return meter.fiveMinuteRate(); }

    @Override
    public double meanRate() { return meter.meanRate(); }

    @Override
    public double oneMinuteRate() { return meter.oneMinuteRate(); }

    /**
     * Returns the longest recorded duration.
     *
     * @return the longest recorded duration
     */
    public double max() { return convertFromNS(histogram.max()); }

    /**
     * Returns the shortest recorded duration.
     *
     * @return the shortest recorded duration
     */
    public double min() { return convertFromNS(histogram.min()); }

    /**
     * Returns the arithmetic mean of all recorded durations.
     *
     * @return the arithmetic mean of all recorded durations
     */
    public double mean() { return convertFromNS(histogram.mean()); }

    /**
     * Returns the standard deviation of all recorded durations.
     *
     * @return the standard deviation of all recorded durations
     */
    public double stdDev() { return convertFromNS(histogram.stdDev()); }

    /**
     * Returns an array of durations at the given percentiles.
     *
     * @param percentiles one or more percentiles ({@code 0..1})
     * @return an array of durations at the given percentiles
     */
    public double[] percentiles(double... percentiles) {
        final double[] scores = histogram.percentiles(percentiles);
        for (int i = 0; i < scores.length; i++) {
            scores[i] = convertFromNS(scores[i]);
        }

        return scores;
    }

    @Override
    public String eventType() {
        return meter.eventType();
    }

    /**
     * Returns a list of all recorded durations in the timers's sample.
     *
     * @return a list of all recorded durations in the timers's sample
     */
    public List<Double> values() {
        final List<Double> values = new ArrayList<Double>();
        for (Long value : histogram.values()) {
            values.add(convertFromNS(value));
        }
        return values;
    }

    private void update(long duration) {
        if (duration >= 0) {
            histogram.update(duration);
            meter.mark();
        }
    }

    private double convertFromNS(double ns) {
        return ns / TimeUnit.NANOSECONDS.convert(1, durationUnit);
    }

}
