package com.yammer.metrics.stats;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.exp;

/**
 * An exponentially-weighted moving average.
 *
 * @see <a href="UNIX Load Average Part 1: How It Works">http://www.teamquest.com/pdfs/whitepaper/ldavg1.pdf</a>
 * @see <a href="UNIX Load Average Part 2: Not Your Average Average">http://www.teamquest.com/pdfs/whitepaper/ldavg2.pdf</a>
 */
public class EWMA {
    private static final double M1_ALPHA  = 1 - exp(-5 / 60.0);
    private static final double M5_ALPHA  = 1 - exp(-5 / 60.0 / 5);
    private static final double M15_ALPHA = 1 - exp(-5 / 60.0 / 15);

    private volatile boolean initialized = false;
    private volatile double rate = 0.0;

    private final AtomicLong uncounted = new AtomicLong();
    private final double alpha, interval;

    /**
     * Creates a new EWMA which is equivalent to the UNIX one minute load average and which expects to be ticked every
     * 5 seconds.
     *
     * @return a one-minute EWMA
     */
    public static EWMA oneMinuteEWMA() {
        return new EWMA(M1_ALPHA, 5, TimeUnit.SECONDS);
    }

    /**
     * Creates a new EWMA which is equivalent to the UNIX five minute load average and which expects to be ticked every
     * 5 seconds.
     *
     * @return a five-minute EWMA
     */
    public static EWMA fiveMinuteEWMA() {
        return new EWMA(M5_ALPHA, 5, TimeUnit.SECONDS);
    }

    /**
     * Creates a new EWMA which is equivalent to the UNIX fifteen minute load average and which expects to be ticked
     * every 5 seconds.
     *
     * @return a fifteen-minute EWMA
     */
    public static EWMA fifteenMinuteEWMA() {
        return new EWMA(M15_ALPHA, 5, TimeUnit.SECONDS);
    }

    /**
     * Create a new EWMA with a specific smoothing constant.
     *
     * @param alpha the smoothing constant
     * @param interval the expected tick interval
     * @param intervalUnit the time unit of the tick interval
     */
    public EWMA(double alpha, long interval, TimeUnit intervalUnit) {
        this.interval = intervalUnit.toNanos(interval);
        this.alpha = alpha;
    }

    /**
     * Update the moving average with a new value.
     *
     * @param n the new value
     */
    public void update(long n) {
        uncounted.addAndGet(n);
    }

    /**
     * Mark the passage of time and decay the current rate accordingly.
     */
    public void tick() {
        final long count = uncounted.getAndSet(0);
        double instantRate = count / interval;
        if (initialized) {
            rate += (alpha * (instantRate - rate));
        } else {
            rate = instantRate;
            initialized = true;
        }
    }

    /**
     * Returns the rate in the given units of time.
     *
     * @param rateUnit the unit of time
     * @return the rate
     */
    public double rate(TimeUnit rateUnit) {
        return rate * (double) rateUnit.toNanos(1);
    }
}
