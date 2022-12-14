package com.yammer.metrics.core;

import java.lang.ref.SoftReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// TODO: 12/10/10 <coda> -- this could use a unit and scale unit (e.g., "requests", TimeUnit.SECONDS)

/**
 * A meter metric which measures mean throughput and one-, five-, and
 * fifteen-minute exponentially-weighted moving average throughputs.
 *
 * @author coda
 * @see <a href="http://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average">EMA</a>
 */
public class MeterMetric implements Metric {
	private static final ScheduledExecutorService TICK_THREAD =
			Executors.newScheduledThreadPool(2, new NamedThreadFactory("meter-tick"));
	private static final long INTERVAL = 5; // seconds
	private static final double INTERVAL_IN_NS = TimeUnit.SECONDS.toNanos(INTERVAL);
	private static final double ONE_MINUTE_FACTOR = 1 / Math.exp(TimeUnit.SECONDS.toMinutes(INTERVAL));
	private static final double FIVE_MINUTE_FACTOR = ONE_MINUTE_FACTOR / 5;
	private static final double FIFTEEN_MINUTE_FACTOR = ONE_MINUTE_FACTOR / 15;

	/**
	 * Creates a new {@link MeterMetric}.
	 *
	 * @param unit the scale unit of the new meter
	 * @return a new {@link MeterMetric}
	 */
	public static MeterMetric newMeter(TimeUnit unit) {
		return newMeter(INTERVAL, TimeUnit.SECONDS, unit);
	}

	/**
	 * Creates a new {@link MeterMetric} with a given tick interval.
	 *
	 * @param interval the duration of a meter tick
	 * @param intervalUnit the unit of {@code interval}
	 * @param unit the scale unit of the new meter
	 * @return a new {@link MeterMetric}
	 */
	public static MeterMetric newMeter(long interval, TimeUnit intervalUnit, TimeUnit unit) {
		final MeterMetric meter = new MeterMetric(unit);
		final SoftReference<MeterMetric> reference = new SoftReference<MeterMetric>(meter);
		final Runnable job = new Runnable() {
			@Override
			public void run() {
				// This will throw a NullPointerException if the meter has been
				// collected. This is actually the desired behavior, as the
				// NPE will cause the ScheduledExecutorService to deschedule
				// this job.
				reference.get().tick();
			}
		};
		TICK_THREAD.scheduleAtFixedRate(job, interval, interval, intervalUnit);
		return meter;
	}

	private final AtomicLong uncounted = new AtomicLong();
	private final AtomicLong count = new AtomicLong();
	private final long startTime = System.nanoTime();
	private final TimeUnit unit;
	private volatile boolean initialized;
	private volatile double _oneMinuteRate;
	private volatile double _fiveMinuteRate;
	private volatile double _fifteenMinuteRate;

	private MeterMetric(TimeUnit unit) {
		initialized = false;
		_oneMinuteRate = _fiveMinuteRate = _fifteenMinuteRate = 0.0;
		this.unit = unit;
	}

	/**
	 * Returns the meter's scale unit.
	 *
	 * @return the meter's scale unit
	 */
	public TimeUnit getUnit() {
		return unit;
	}

	/**
	 * Updates the moving averages.
	 */
	void tick() {
		final long count = uncounted.getAndSet(0);
		if (initialized) {
			_oneMinuteRate += (ONE_MINUTE_FACTOR * ((count / INTERVAL_IN_NS) - _oneMinuteRate));
			_fiveMinuteRate += (FIVE_MINUTE_FACTOR * ((count / INTERVAL_IN_NS) - _fiveMinuteRate));
			_fifteenMinuteRate += (FIFTEEN_MINUTE_FACTOR * ((count / INTERVAL_IN_NS) - _fifteenMinuteRate));
		} else {
			_oneMinuteRate = _fiveMinuteRate = _fifteenMinuteRate = count / INTERVAL_IN_NS;
			initialized = true;
		}
	}

	/**
	 * Mark the occurence of an event.
	 */
	public void mark() {
		mark(1);
	}

	/**
	 * Mark the occurence of a given number of events.
	 *
	 * @param n the number of events
	 */
	public void mark(long n) {
		count.addAndGet(n);
		uncounted.addAndGet(n);
	}

	/**
	 * Returns the number of events which have been marked.
	 *
	 * @return the number of events which have been marked
	 */
	public long count() {
		return count.get();
	}

	/**
	 * Returns the fifteen-minute exponentially-weighted moving average rate at
	 * which events have occured since the meter was created.
	 * <p>
	 * This rate has the same exponential decay factor as the fifteen-minute load
	 * average in the {@code top} Unix command.
	 *
	 * @return the fifteen-minute exponentially-weighted moving average rate at
	 *         which events have occured since the meter was created
	 */
	public double fifteenMinuteRate() {
		return convertNsRate(_fifteenMinuteRate);
	}

	/**
	 * Returns the five-minute exponentially-weighted moving average rate at
	 * which events have occured since the meter was created.
	 * <p>
	 * This rate has the same exponential decay factor as the five-minute load
	 * average in the {@code top} Unix command.
	 *
	 * @return the five-minute exponentially-weighted moving average rate at
	 *         which events have occured since the meter was created
	 */
	public double fiveMinuteRate() {
		return convertNsRate(_fiveMinuteRate);
	}

	/**
	 * Returns the mean rate at which events have occured since the meter was
	 * created.
	 *
	 * @return the mean rate at which events have occured since the meter was
	 *         created
	 */
	public double meanRate() {
		if (count() == 0) {
			return 0.0;
		} else {
			final long elapsed = (System.nanoTime() - startTime);
			return convertNsRate(count() / (double) elapsed);
		}
	}

	/**
	 * Returns the one-minute exponentially-weighted moving average rate at
	 * which events have occured since the meter was created.
	 * <p>
	 * This rate has the same exponential decay factor as the one-minute load
	 * average in the {@code top} Unix command.
	 *
	 * @return the one-minute exponentially-weighted moving average rate at
	 *         which events have occured since the meter was created
	 */
	public double oneMinuteRate() {
		return convertNsRate(_oneMinuteRate);
	}

	private double convertNsRate(double ratePerNs) {
		return ratePerNs * (double) unit.toNanos(1);
	}
}
