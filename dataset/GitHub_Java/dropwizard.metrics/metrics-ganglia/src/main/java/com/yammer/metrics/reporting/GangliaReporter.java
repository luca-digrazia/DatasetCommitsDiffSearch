package com.yammer.metrics.reporting;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.*;
import com.yammer.metrics.util.MetricPredicate;
import com.yammer.metrics.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.yammer.metrics.core.VirtualMachineMetrics.*;

/**
 * A simple reporter which sends out application metrics to a
 * <a href="hhttp://ganglia.sourceforge.net/">Ganglia</a> server periodically.
 * <p/>
 * NOTE: this reporter only works with Ganglia 3.1 and greater.  The message protool
 * for earlier versions of Ganglia is different.
 * <p/>
 * This code heavily borrows from GangliaWriter in
 * <a href="http://code.google.com/p/jmxtrans/source/browse/trunk/src/com/googlecode/jmxtrans/model/output/GangliaWriter.java">JMXTrans</a>
 * which is based on <a ahref="http://search-hadoop.com/c/Hadoop:/hadoop-common-project/hadoop-common/src/main/java/org/apache/hadoop/metrics/ganglia/GangliaContext31.java">GangliaContext31</a>
 * from Hadoop.
 */
public class GangliaReporter implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GangliaReporter.class);
    private static final int BUFFER_SIZE = 1500;
    private static final int GANGLIA_TMAX = 60;
    private static final int GANGLIA_DMAX = 0;
    private static final String GANGLIA_INT_TYPE = "int32";
    private static final String GANGLIA_DOUBLE_TYPE = "double";
    private final ScheduledExecutorService tickThread;
    private final MetricsRegistry metricsRegistry;
    private final String gangliaHost;
    private final int port;
    private final MetricPredicate predicate;
    private final Locale locale = Locale.US;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private int offset;
    private DatagramSocket socket;
    private String hostName;


    /**
     * Enables the ganglia reporter to send data for the default metrics registry
     * to ganglia server with the specified period.
     *
     * @param period      the period between successive outputs
     * @param unit        the time unit of {@code period}
     * @param gangliaHost the gangliaHost name of ganglia server (carbon-cache agent)
     * @param port        the port number on which the ganglia server is listening
     */
    public static void enable(long period, TimeUnit unit, String gangliaHost, int port) {
        enable(Metrics.defaultRegistry(), period, unit, gangliaHost, port);
    }

    /**
     * Enables the ganglia reporter to send data for the given metrics registry
     * to ganglia server with the specified period.
     *
     * @param metricsRegistry the metrics registry
     * @param period          the period between successive outputs
     * @param unit            the time unit of {@code period}
     * @param gangliaHost     the gangliaHost name of ganglia server (carbon-cache agent)
     * @param port            the port number on which the ganglia server is listening
     */
    public static void enable(MetricsRegistry metricsRegistry, long period, TimeUnit unit, String gangliaHost, int port) {
        enable(metricsRegistry, period, unit, gangliaHost, port, MetricPredicate.ALL);
    }

    /**
     * Enables the ganglia reporter to send data to ganglia server with the
     * specified period.
     *
     * @param metricsRegistry the metrics registry
     * @param period          the period between successive outputs
     * @param unit            the time unit of {@code period}
     * @param gangliaHost     the gangliaHost name of ganglia server (carbon-cache agent)
     * @param port            the port number on which the ganglia server is listening
     * @param predicate       filters metrics to be reported
     */
    public static void enable(MetricsRegistry metricsRegistry, long period, TimeUnit unit, String gangliaHost, int port, MetricPredicate predicate) {
        try {
            final GangliaReporter reporter = new GangliaReporter(metricsRegistry, gangliaHost, port, predicate);
            reporter.start(period, unit);
        } catch (Exception e) {
            log.error("Error creating/starting ganglia reporter:", e);
        }
    }

    /**
     * Creates a new {@link GangliaReporter}.
     *
     * @param gangliaHost is ganglia server
     * @param port        is port on which ganglia server is running
     * @throws java.io.IOException if there is an error connecting to the ganglia server
     */
    public GangliaReporter(String gangliaHost, int port) throws IOException {
        this(Metrics.defaultRegistry(), gangliaHost, port);
    }

    /**
     * Creates a new {@link GangliaReporter}.
     *
     * @param metricsRegistry the metrics registry
     * @param gangliaHost     is ganglia server
     * @param port            is port on which ganglia server is running
     * @throws java.io.IOException if there is an error connecting to the ganglia server
     */
    public GangliaReporter(MetricsRegistry metricsRegistry, String gangliaHost, int port) throws IOException {
        this(metricsRegistry, gangliaHost, port, MetricPredicate.ALL);
    }

    /**
     * Creates a new {@link GangliaReporter}.
     *
     * @param metricsRegistry the metrics registry
     * @param gangliaHost     is ganglia server
     * @param port            is port on which ganglia server is running
     * @param predicate       filters metrics to be reported
     * @throws java.io.IOException if there is an error connecting to the ganglia server
     */
    public GangliaReporter(MetricsRegistry metricsRegistry, String gangliaHost, int port, MetricPredicate predicate) throws IOException {
        this.tickThread = metricsRegistry.threadPools().newScheduledThreadPool(1, "ganglia-reporter");
        this.metricsRegistry = metricsRegistry;
        this.gangliaHost = gangliaHost;
        this.port = port;
        this.hostName = getHostName();
        this.predicate = predicate;
        socket = new DatagramSocket();
    }

    /**
     * Starts sending output to ganglia server.
     *
     * @param period the period between successive displays
     * @param unit   the time unit of {@code period}
     */
    public void start(long period, TimeUnit unit) {
        tickThread.scheduleAtFixedRate(this, period, period, unit);
    }

    @Override
    public void run() {
        printVmMetrics();
        printRegularMetrics();
    }

    private void printRegularMetrics() {
        for (Map.Entry<String, Map<String, Metric>> entry : Utils.sortAndFilterMetrics(metricsRegistry.allMetrics(), this.predicate).entrySet()) {
            for (Map.Entry<String, Metric> subEntry : entry.getValue().entrySet()) {
                final String simpleName = sanitizeName(entry.getKey() + "." + subEntry.getKey());
                final Metric metric = subEntry.getValue();
                if (metric != null) {
                    try {
                        if (metric instanceof GaugeMetric<?>) {
                            printGauge((GaugeMetric<?>) metric, simpleName);
                        } else if (metric instanceof CounterMetric) {
                            printCounter((CounterMetric) metric, simpleName);
                        } else if (metric instanceof HistogramMetric) {
                            printHistogram((HistogramMetric) metric, simpleName);
                        } else if (metric instanceof MeterMetric) {
                            printMetered((MeterMetric) metric, simpleName);
                        } else if (metric instanceof TimerMetric) {
                            printTimer((TimerMetric) metric, simpleName);
                        }
                    } catch (Exception ignored) {
                        log.error("Error printing regular metrics:", ignored);
                    }
                }
            }
        }

    }

    private void sendToGanglia(String metricName, String metricType, String metricValue, String groupName) {
        try {
            sendMetricData(metricType, metricName, metricValue, groupName);
            if (log.isDebugEnabled()) {
                log.debug("Emitting metric " + metricName + ", type " + metricType + ", value " + metricValue + " for gangliaHost: " + gangliaHost + ":" + port);
            }
        } catch (IOException e) {
            log.error("Error sending to ganglia:", e);
        }
    }

    private void sendMetricData(String metricType, String metricName, String metricValue, String groupName) throws IOException {
        offset = 0;
        xdrInt(128); // metric_id = metadata_msg
        xdrString(hostName); // hostname
        xdrString(metricName); // metric name
        xdrInt(0); // spoof = True
        xdrString(metricType); // metric type
        xdrString(metricName); // metric name
        xdrString(""); // units
        xdrInt(3); // slope see gmetric.c
        xdrInt(GANGLIA_TMAX); // tmax, the maximum time between metrics
        xdrInt(GANGLIA_DMAX); // dmax, the maximum data value
        xdrInt(1);
        xdrString("GROUP");    /*Group attribute*/
        xdrString(groupName);  /*Group value*/
        socket.send(new DatagramPacket(buffer, offset, new InetSocketAddress(gangliaHost, port)));

        offset = 0;
        xdrInt(133); // we are sending a string value
        xdrString(hostName); // hostName
        xdrString(metricName); // metric name
        xdrInt(0); // spoof = True
        xdrString("%s"); // format field
        xdrString(metricValue); // metric value
        socket.send(new DatagramPacket(buffer, offset, new InetSocketAddress(gangliaHost, port)));
    }

    /**
     * Puts an integer into the buffer as 4 bytes, big-endian.
     *
     * @param i -  the integer to write to the buffer
     */
    private void xdrInt(int i) {
        buffer[offset++] = (byte) ((i >> 24) & 0xff);
        buffer[offset++] = (byte) ((i >> 16) & 0xff);
        buffer[offset++] = (byte) ((i >> 8) & 0xff);
        buffer[offset++] = (byte) (i & 0xff);
    }

    /**
     * Puts a string into the buffer by first writing the size of the string
     * as an int, followed by the bytes of the string, padded if necessary to
     * a multiple of 4.
     *
     * @param message - the message to write to the buffer
     */
    private void xdrString(String message) {
        byte[] bytes = message.getBytes();
        int len = bytes.length;
        xdrInt(len);
        System.arraycopy(bytes, 0, buffer, offset, len);
        offset += len;
        pad();
    }

    /**
     * Pads the buffer with zero bytes up to the nearest multiple of 4.
     */
    private void pad() {
        int newOffset = ((offset + 3) / 4) * 4;
        while (offset < newOffset) {
            buffer[offset++] = 0;
        }
    }

    private String sanitizeName(String name) {
        return name.replace(' ', '-');
    }

    private void printGauge(GaugeMetric<?> gauge, String name) {
        sendToGanglia(sanitizeName(name), GANGLIA_INT_TYPE, String.format(locale, "%s", gauge.value()), "gauge");
    }

    private void printCounter(CounterMetric counter, String name) {
        sendToGanglia(sanitizeName(name), GANGLIA_INT_TYPE, String.format(locale, "%d", counter.count()), "counter");
    }

    private void printMetered(Metered meter, String name) {
        final String sanitizedName = sanitizeName(name);
        printLongField(sanitizedName + ".count", meter.count(), "metered");
        printDoubleField(sanitizedName + ".meanRate", meter.meanRate(), "metered");
        printDoubleField(sanitizedName + ".1MinuteRate", meter.oneMinuteRate(), "metered");
        printDoubleField(sanitizedName + ".5MinuteRate", meter.fiveMinuteRate(), "metered");
        printDoubleField(sanitizedName + ".15MinuteRate", meter.fifteenMinuteRate(), "metered");
    }

    private void printHistogram(HistogramMetric histogram, String name) {
        final String sanitizedName = sanitizeName(name);
        final double[] percentiles = histogram.percentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);

        printDoubleField(sanitizedName + ".min", histogram.min(), "histo");
        printDoubleField(sanitizedName + ".max", histogram.max(), "histo");
        printDoubleField(sanitizedName + ".mean", histogram.mean(), "histo");
        printDoubleField(sanitizedName + ".stddev", histogram.stdDev(), "histo");
        printDoubleField(sanitizedName + ".median", percentiles[0], "histo");
        printDoubleField(sanitizedName + ".75percentile", percentiles[1], "histo");
        printDoubleField(sanitizedName + ".95percentile", percentiles[2], "histo");
        printDoubleField(sanitizedName + ".98percentile", percentiles[3], "histo");
        printDoubleField(sanitizedName + ".99percentile", percentiles[4], "histo");
        printDoubleField(sanitizedName + ".999percentile", percentiles[5], "histo");
    }

    private void printTimer(TimerMetric timer, String name) {
        printMetered(timer, name);
        final String sanitizedName = sanitizeName(name);
        final double[] percentiles = timer.percentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
        printDoubleField(sanitizedName + ".min", timer.min(), "timer");
        printDoubleField(sanitizedName + ".max", timer.max(), "timer");
        printDoubleField(sanitizedName + ".mean", timer.mean(), "timer");
        printDoubleField(sanitizedName + ".stddev", timer.stdDev(), "timer");
        printDoubleField(sanitizedName + ".median", percentiles[0], "timer");
        printDoubleField(sanitizedName + ".75percentile", percentiles[1], "timer");
        printDoubleField(sanitizedName + ".95percentile", percentiles[2], "timer");
        printDoubleField(sanitizedName + ".98percentile", percentiles[3], "timer");
        printDoubleField(sanitizedName + ".99percentile", percentiles[4], "timer");
        printDoubleField(sanitizedName + ".999percentile", percentiles[5], "timer");
    }

    private void printDoubleField(String name, double value, String groupName) {
        sendToGanglia(sanitizeName(name), GANGLIA_DOUBLE_TYPE, String.format(locale, "%2.2f", value), groupName);
    }

    private void printLongField(String name, long value, String groupName) {
        sendToGanglia(sanitizeName(name), GANGLIA_INT_TYPE, String.format(locale, "%d", value), groupName);
    }

    private void printVmMetrics() {
        printDoubleField("jvm.memory.heap_usage", heapUsage(), "jvm");
        printDoubleField("jvm.memory.non_heap_usage", nonHeapUsage(), "jvm");
        for (Map.Entry<String, Double> pool : memoryPoolUsage().entrySet()) {
            printDoubleField("jvm.memory.memory_pool_usages." + pool.getKey(), pool.getValue(), "jvm");
        }

        printDoubleField("jvm.daemon_thread_count", daemonThreadCount(), "jvm");
        printDoubleField("jvm.thread_count", threadCount(), "jvm");
        printDoubleField("jvm.uptime", uptime(), "jvm");
        printDoubleField("jvm.fd_usage", fileDescriptorUsage(), "jvm");

        for (Map.Entry<Thread.State, Double> entry : threadStatePercentages().entrySet()) {
            printDoubleField("jvm.thread-states." + entry.getKey().toString().toLowerCase(), entry.getValue(), "jvm");
        }

        for (Map.Entry<String, GarbageCollector> entry : garbageCollectors().entrySet()) {
            printLongField("jvm.gc." + entry.getKey() + ".time", entry.getValue().getTime(TimeUnit.MILLISECONDS), "jvm");
            printLongField("jvm.gc." + entry.getKey() + ".runs", entry.getValue().getRuns(), "jvm");
        }
    }

    public String getHostName() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            // for some reason ganglia needs a colon before hostname...
            return "x:" + addr.getHostName();
        } catch (UnknownHostException e) {
            log.error("Unable to get local gangliaHost name: ", e);
            return "unknown";
        }
    }
}
