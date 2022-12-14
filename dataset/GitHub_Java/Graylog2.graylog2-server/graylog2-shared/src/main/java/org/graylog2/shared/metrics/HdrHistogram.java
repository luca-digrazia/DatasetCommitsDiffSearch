/**
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.shared.metrics;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Snapshot;
import org.HdrHistogram.AtomicHistogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.PrintStream;

public class HdrHistogram extends com.codahale.metrics.Histogram {
    private static final Logger log = LoggerFactory.getLogger(HdrHistogram.class);
    private final AtomicHistogram hdrHistogram;

    public HdrHistogram(AtomicHistogram hdrHistogram) {
        super(new ExponentiallyDecayingReservoir());
        this.hdrHistogram = hdrHistogram;
        log.info("Histogram approx size {} bytes", hdrHistogram.getEstimatedFootprintInBytes());
    }

    public HdrHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits));
    }

    @Override
    public long getCount() {
        return hdrHistogram.getTotalCount();
    }

    @Override
    public Snapshot getSnapshot() {
        final AtomicHistogram copy = hdrHistogram.copy();
        return new Snapshot() {
            @Override
            public double getValue(double quantile) {
                return copy.getValueAtPercentile(quantile * 100);
            }

            @Override
            public long[] getValues() {
                return new long[0];
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public long getMax() {
                return copy.getMaxValue();
            }

            @Override
            public double getMean() {
                final double mean = copy.getMean();
                return Double.isNaN(mean) ? 0 : mean;
            }

            @Override
            public long getMin() {
                final long minValue = copy.getMinValue();
                return minValue == Long.MAX_VALUE ? 0 : minValue;
            }

            @Override
            public double getStdDev() {
                final double stdDeviation = copy.getStdDeviation();
                return Double.isNaN(stdDeviation) ? 0 : stdDeviation;
            }

            @Override
            public void dump(OutputStream output) {
                copy.outputPercentileDistribution(new PrintStream(output), 1d);
            }
        };
    }


    @Override
    public void update(int value) {
        hdrHistogram.recordValue(value);
    }

    @Override
    public void update(long value) {
        hdrHistogram.recordValue(value);
    }
}
