package com.yammer.metrics.reporting;

import com.yammer.metrics.HealthChecks;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.*;
import com.yammer.metrics.core.HealthCheck.Result;
import com.yammer.metrics.util.Utils;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.Thread.State;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static com.yammer.metrics.core.VirtualMachineMetrics.*;

public class MetricsServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsServlet.class);
    public static final String ATTR_NAME_METRICS_REGISTRY = MetricsServlet.class.getSimpleName() + ":" + MetricsRegistry.class.getSimpleName();
    public static final String ATTR_NAME_HEALTHCHECK_REGISTRY = MetricsServlet.class.getSimpleName() + ":" + HealthCheckRegistry.class.getSimpleName();

    private static final String TEMPLATE = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
                                           "        \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                                           "<html>\n" +
                                           "<head>\n" +
                                           "  <title>Metrics</title>\n" +
                                           "</head>\n" +
                                           "<body>\n" +
                                           "  <h1>Operational Menu</h1>\n" +
                                           "  <ul>\n" +
                                           "    <li><a href=\"{0}{1}?pretty=true\">Metrics</a></li>\n" +
                                           "    <li><a href=\"{2}{3}\">Ping</a></li>\n" +
                                           "    <li><a href=\"{4}{5}\">Threads</a></li>\n" +
                                           "    <li><a href=\"{6}{7}\">Healthcheck</a></li>\n" +
                                           "  </ul>\n" +
                                           "</body>\n" +
                                           "</html>";
    public static final String HEALTHCHECK_URI = "/healthcheck";
    public static final String METRICS_URI = "/metrics";
    public static final String PING_URI = "/ping";
    public static final String THREADS_URI = "/threads";
    private MetricsRegistry metricsRegistry;
    private HealthCheckRegistry healthCheckRegistry;
    private JsonFactory factory;
    private String metricsUri, pingUri, threadsUri, healthcheckUri, contextPath;
    private boolean showJvmMetrics;

    public MetricsServlet() {
        this(new JsonFactory(new ObjectMapper()), HEALTHCHECK_URI, METRICS_URI, PING_URI, THREADS_URI, true);
    }

    public MetricsServlet(boolean showJvmMetrics) {
        this(new JsonFactory(new ObjectMapper()), HEALTHCHECK_URI, METRICS_URI, PING_URI, THREADS_URI, showJvmMetrics);
    }

    public MetricsServlet(JsonFactory factory) {
        this(factory, HEALTHCHECK_URI, METRICS_URI, PING_URI, THREADS_URI);
    }

    public MetricsServlet(JsonFactory factory, boolean showJvmMetrics) {
        this(factory, HEALTHCHECK_URI, METRICS_URI, PING_URI, THREADS_URI, showJvmMetrics);
    }

    public MetricsServlet(String healthcheckUri, String metricsUri, String pingUri, String threadsUri) {
        this(new JsonFactory(new ObjectMapper()), healthcheckUri, metricsUri, pingUri, threadsUri);
    }

    public MetricsServlet(JsonFactory factory, String healthcheckUri, String metricsUri, String pingUri, String threadsUri) {
        this(factory, healthcheckUri, metricsUri, pingUri, threadsUri, true);
    }

    public MetricsServlet(JsonFactory factory, String healthcheckUri, String metricsUri, String pingUri, String threadsUri, boolean showJvmMetrics) {
        this(Metrics.defaultRegistry(), HealthChecks.defaultRegistry(), factory, healthcheckUri, metricsUri, pingUri, threadsUri, showJvmMetrics);
    }

    public MetricsServlet(MetricsRegistry metricsRegistry, HealthCheckRegistry healthCheckRegistry, String healthcheckUri, String metricsUri, String pingUri, String threadsUri, boolean showJvmMetrics) {
        this(metricsRegistry, healthCheckRegistry, new JsonFactory(new ObjectMapper()), healthcheckUri, metricsUri, pingUri, threadsUri, showJvmMetrics);
    }

    public MetricsServlet(MetricsRegistry metricsRegistry, HealthCheckRegistry healthCheckRegistry, JsonFactory factory, String healthcheckUri, String metricsUri, String pingUri, String threadsUri, boolean showJvmMetrics) {
        this.metricsRegistry = metricsRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
        this.factory = factory;
        this.healthcheckUri = healthcheckUri;
        this.metricsUri = metricsUri;
        this.pingUri = pingUri;
        this.threadsUri = threadsUri;
        this.showJvmMetrics = showJvmMetrics;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();

        this.contextPath = context.getContextPath();
        this.metricsRegistry = putAttrIfAbsent(context, ATTR_NAME_METRICS_REGISTRY, this.metricsRegistry);
        this.healthCheckRegistry = putAttrIfAbsent(context, ATTR_NAME_HEALTHCHECK_REGISTRY, this.healthCheckRegistry);
        this.metricsUri = getParam(config.getInitParameter("metrics-uri"), this.metricsUri);
        this.pingUri = getParam(config.getInitParameter("ping-uri"), this.pingUri);
        this.threadsUri = getParam(config.getInitParameter("threads-uri"), this.threadsUri);
        this.healthcheckUri = getParam(config.getInitParameter("healthcheck-uri"), this.healthcheckUri);
        final String showJvmMetricsParam = config.getInitParameter("show-jvm-metrics");
        if (showJvmMetricsParam != null) {
            this.showJvmMetrics = Boolean.parseBoolean(showJvmMetricsParam);
        }

        final Object factory = config.getServletContext().getAttribute(JsonFactory.class.getCanonicalName());
        if (factory != null && factory instanceof JsonFactory) {
            this.factory = (JsonFactory) factory;
        }
    }

    private String getParam(String initParam, String defaultValue) {
        return initParam == null ? defaultValue : initParam;
    }

    private <T> T putAttrIfAbsent(ServletContext context, String attrName, T defaultValue) {
        @SuppressWarnings("unchecked")
        T attrValue = (T)context.getAttribute(attrName);
        if (attrValue == null) {
            attrValue = defaultValue;
            context.setAttribute(attrName, attrValue);
        }
        return attrValue;
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        final String uri = req.getPathInfo();
        final String path = this.contextPath + req.getServletPath();
        if (uri == null || uri.equals("/")) {
            handleHome(path, resp);
        } else if (uri.startsWith(metricsUri)) {
            handleMetrics(req.getParameter("class"), Boolean.parseBoolean(req.getParameter("full-samples")), 
                          Boolean.parseBoolean(req.getParameter("pretty")), resp);
        } else if (uri.equals(pingUri)) {
            handlePing(resp);
        } else if (uri.equals(threadsUri)) {
            handleThreadDump(resp);
        } else if (uri.equals(healthcheckUri)) {
            handleHealthCheck(resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleHome(String path, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/html");

        final PrintWriter writer = resp.getWriter();
        writer.println(MessageFormat.format(TEMPLATE, path, metricsUri, path, pingUri, path, threadsUri, path, healthcheckUri));
        writer.close();
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private void handleHealthCheck(HttpServletResponse resp) throws IOException {
        boolean allHealthy = true;
        final Map<String, Result> results = healthCheckRegistry.runHealthChecks();
        for (Result result : results.values()) {
            allHealthy &= result.isHealthy();
        }

        resp.setContentType("text/plain");

        final PrintWriter writer = resp.getWriter();
        if (results.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
            writer.println("! No health checks registered.");
        } else {
            if (allHealthy) {
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            for (Entry<String, Result> entry : results.entrySet()) {
                final Result result = entry.getValue();
                if (result.isHealthy()) {
                    if (result.getMessage() != null) {
                        writer.format("* %s: OK: %s\n", entry.getKey(), result.getMessage());
                    } else {
                        writer.format("* %s: OK\n", entry.getKey());
                    }
                } else {
                    if (result.getMessage() != null) {
                        writer.format("! %s: ERROR\n!  %s\n", entry.getKey(), result.getMessage());
                    }

                    final Throwable error = result.getError();
                    if (error != null) {
                        writer.println();
                        error.printStackTrace(writer);
                        writer.println();
                    }
                }
            }
        }
        writer.close();
    }

    private void handleThreadDump(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/plain");
        final OutputStream output = resp.getOutputStream();
        threadDump(output);
        output.close();
    }

    private void handlePing(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/plain");
        final PrintWriter writer = resp.getWriter();
        writer.println("pong");
        writer.close();
    }

    private void handleMetrics(String classPrefix, boolean showFullSamples, boolean pretty, HttpServletResponse resp)
        throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json");
        final OutputStream output = resp.getOutputStream();
        final JsonGenerator json = factory.createJsonGenerator(output, JsonEncoding.UTF8);
        if (pretty) {
            json.useDefaultPrettyPrinter();
        }
        json.writeStartObject();
        {
            if (showJvmMetrics && ("jvm".equals(classPrefix) || classPrefix == null)) {
                writeVmMetrics(json);
            }

            writeRegularMetrics(json, classPrefix, showFullSamples);
        }
        json.writeEndObject();
        json.close();
    }

    private void writeRegularMetrics(JsonGenerator json, String classPrefix, boolean showFullSamples) throws IOException {
        for (Entry<String, Map<String, Metric>> entry : Utils.sortMetrics(metricsRegistry.allMetrics()).entrySet()) {
            if (classPrefix == null || entry.getKey().startsWith(classPrefix)) {
                json.writeFieldName(entry.getKey());
                json.writeStartObject();
                {
                    for (Entry<String, Metric> subEntry : entry.getValue().entrySet()) {
                        writeMetric(json, subEntry.getKey(), subEntry.getValue(), showFullSamples);
                    }
                }
                json.writeEndObject();
            }
        }
    }

    private void writeMetric(JsonGenerator json, String key, Metric metric, boolean showFullSamples) throws IOException {
        if (metric instanceof GaugeMetric<?>) {
            json.writeFieldName(key);
            writeGauge(json, (GaugeMetric<?>) metric);
        } else if (metric instanceof CounterMetric) {
            json.writeFieldName(key);
            writeCounter(json, (CounterMetric) metric);
        } else if (metric instanceof MeterMetric) {
            json.writeFieldName(key);
            writeMeter(json, (MeterMetric) metric);
        } else if (metric instanceof HistogramMetric) {
            json.writeFieldName(key);
            writeHistogram(json, (HistogramMetric) metric, showFullSamples);
        } else if (metric instanceof TimerMetric) {
            json.writeFieldName(key);
            writeTimer(json, (TimerMetric) metric, showFullSamples);
        }
    }

    private void writeHistogram(JsonGenerator json, HistogramMetric histogram, boolean showFullSamples) throws IOException {
        json.writeStartObject();
        {
            json.writeStringField("type", "histogram");
            json.writeNumberField("count", histogram.count());
            json.writeNumberField("min", histogram.min());
            json.writeNumberField("max", histogram.max());
            json.writeNumberField("mean", histogram.mean());
            json.writeNumberField("std_dev", histogram.stdDev());

            final double[] percentiles = histogram.percentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
            json.writeNumberField("median", percentiles[0]);
            json.writeNumberField("p75", percentiles[1]);
            json.writeNumberField("p95", percentiles[2]);
            json.writeNumberField("p98", percentiles[3]);
            json.writeNumberField("p99", percentiles[4]);
            json.writeNumberField("p999", percentiles[5]);

            if (showFullSamples) {
                json.writeObjectField("values", histogram.values());
            }
        }
        json.writeEndObject();
    }

    private void writeCounter(JsonGenerator json, CounterMetric counter) throws IOException {
        json.writeStartObject();
        {
            json.writeStringField("type", "counter");
            json.writeNumberField("count", counter.count());
        }
        json.writeEndObject();
    }

    private void writeGauge(JsonGenerator json, GaugeMetric<?> gauge) throws IOException {
        json.writeStartObject();
        {
            json.writeStringField("type", "gauge");
            json.writeObjectField("value", evaluateGauge(gauge));
        }
        json.writeEndObject();
    }

    private Object evaluateGauge(GaugeMetric<?> gauge) {
        try {
            return gauge.value();
        } catch (RuntimeException e) {
            LOGGER.warn("Error evaluating gauge", e);
            return "error reading gauge: " + e.getMessage();
        }
    }

    private void writeVmMetrics(JsonGenerator json) throws IOException {
        json.writeFieldName("jvm");
        json.writeStartObject();
        {

            json.writeFieldName("vm");
            json.writeStartObject();
            {
                json.writeStringField("name", vmName());
                json.writeStringField("version", vmVersion());
            }
            json.writeEndObject();
            json.writeFieldName("memory");
            json.writeStartObject();
            {
                json.writeNumberField("heap_usage", heapUsage());
                json.writeNumberField("non_heap_usage", nonHeapUsage());
                json.writeFieldName("memory_pool_usages");
                json.writeStartObject();
                {
                    for (Entry<String, Double> pool : memoryPoolUsage().entrySet()) {
                        json.writeNumberField(pool.getKey(), pool.getValue());
                    }
                }
                json.writeEndObject();
            }
            json.writeEndObject();

            json.writeNumberField("daemon_thread_count", daemonThreadCount());
            json.writeNumberField("thread_count", threadCount());
            json.writeNumberField("current_time", System.currentTimeMillis());
            json.writeNumberField("uptime", uptime());
            json.writeNumberField("fd_usage", fileDescriptorUsage());

            json.writeFieldName("thread-states");
            json.writeStartObject();
            {
                for (Entry<State, Double> entry : threadStatePercentages().entrySet()) {
                    json.writeNumberField(entry.getKey().toString().toLowerCase(), entry.getValue());
                }
            }
            json.writeEndObject();

            json.writeFieldName("garbage-collectors");
            json.writeStartObject();
            {
                for (Entry<String, GarbageCollector> entry : garbageCollectors().entrySet()) {
                    json.writeFieldName(entry.getKey());
                    json.writeStartObject();
                    {
                        final GarbageCollector gc = entry.getValue();
                        json.writeNumberField("runs", gc.getRuns());
                        json.writeNumberField("time", gc.getTime(TimeUnit.MILLISECONDS));
                    }
                    json.writeEndObject();
                }
            }
            json.writeEndObject();

        }
        json.writeEndObject();
    }

    private void writeMeter(JsonGenerator json, MeterMetric meter) throws IOException {
        json.writeStartObject();
        {
            json.writeStringField("type", "meter");
            json.writeStringField("event_type", meter.eventType());
            json.writeStringField("unit", meter.rateUnit().toString().toLowerCase());
            json.writeNumberField("count", meter.count());
            json.writeNumberField("mean", meter.meanRate());
            json.writeNumberField("m1", meter.oneMinuteRate());
            json.writeNumberField("m5", meter.fiveMinuteRate());
            json.writeNumberField("m15", meter.fifteenMinuteRate());
        }
        json.writeEndObject();
    }

    private void writeTimer(JsonGenerator json, TimerMetric timer, boolean showFullSamples) throws IOException {
        json.writeStartObject();
        {
            json.writeStringField("type", "timer");
            json.writeFieldName("duration");
            json.writeStartObject();
            {
                json.writeStringField("unit", timer.durationUnit().toString().toLowerCase());
                json.writeNumberField("min", timer.min());
                json.writeNumberField("max", timer.max());
                json.writeNumberField("mean", timer.mean());
                json.writeNumberField("std_dev", timer.stdDev());

                final double[] percentiles = timer.percentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
                json.writeNumberField("median", percentiles[0]);
                json.writeNumberField("p75", percentiles[1]);
                json.writeNumberField("p95", percentiles[2]);
                json.writeNumberField("p98", percentiles[3]);
                json.writeNumberField("p99", percentiles[4]);
                json.writeNumberField("p999", percentiles[5]);

                if (showFullSamples) {
                    json.writeObjectField("values", timer.values());
                }
            }
            json.writeEndObject();

            json.writeFieldName("rate");
            json.writeStartObject();
            {
                json.writeStringField("unit", timer.rateUnit().toString().toLowerCase());
                json.writeNumberField("count", timer.count());
                json.writeNumberField("mean", timer.meanRate());
                json.writeNumberField("m1", timer.oneMinuteRate());
                json.writeNumberField("m5", timer.fiveMinuteRate());
                json.writeNumberField("m15", timer.fifteenMinuteRate());
            }
            json.writeEndObject();
        }
        json.writeEndObject();
    }
}
