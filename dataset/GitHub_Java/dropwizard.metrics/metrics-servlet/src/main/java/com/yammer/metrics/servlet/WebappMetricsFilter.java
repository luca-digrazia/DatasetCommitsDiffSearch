package com.yammer.metrics.servlet;

import com.yammer.metrics.Counter;
import com.yammer.metrics.Meter;
import com.yammer.metrics.MetricRegistry;
import com.yammer.metrics.Timer;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.yammer.metrics.MetricRegistry.name;

/**
 * {@link Filter} implementation which captures request information and a breakdown of the response
 * codes being returned.
 */
public abstract class WebappMetricsFilter implements Filter {
    private final String otherMetricName;
    private final Map<Integer, String> meterNamesByStatusCode;
    private final String registryAttribute;

    // initialized after call of init method
    private ConcurrentMap<Integer, Meter> metersByStatusCode;
    private Meter otherMeter;
    private Counter activeRequests;
    private Timer requestTimer;


    /**
     * Creates a new instance of the filter.
     *
     * @param registryAttribute      the attribute used to look up the metrics registry in the
     *                               servlet context
     * @param meterNamesByStatusCode A map, keyed by status code, of meter names that we are
     *                               interested in.
     * @param otherMetricName        The name used for the catch-all meter.
     */
    protected WebappMetricsFilter(String registryAttribute,
                                  Map<Integer, String> meterNamesByStatusCode,
                                  String otherMetricName) {
        this.registryAttribute = registryAttribute;
        this.otherMetricName = otherMetricName;
        this.meterNamesByStatusCode = meterNamesByStatusCode;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        final MetricRegistry metricsRegistry = getMetricsFactory(filterConfig);

        this.metersByStatusCode = new ConcurrentHashMap<Integer, Meter>(meterNamesByStatusCode
                .size());
        for (Entry<Integer, String> entry : meterNamesByStatusCode.entrySet()) {
            metersByStatusCode.put(entry.getKey(),
                    metricsRegistry.meter(name(WebappMetricsFilter.class, entry.getValue())));
        }
        this.otherMeter = metricsRegistry.meter(name(WebappMetricsFilter.class,
                                                     otherMetricName));
        this.activeRequests = metricsRegistry.counter(name(WebappMetricsFilter.class,
                                                           "activeRequests"));
        this.requestTimer = metricsRegistry.timer(name(WebappMetricsFilter.class,
                                                       "requests"));

    }

    private MetricRegistry getMetricsFactory(FilterConfig filterConfig) {
        final MetricRegistry metricsRegistry;

        final Object o = filterConfig.getServletContext().getAttribute(this.registryAttribute);
        if (o instanceof MetricRegistry) {
            metricsRegistry = (MetricRegistry) o;
        } else {
            // TODO: 3/10/13 <coda> -- figure out how to coordinate on registry names
            metricsRegistry = new MetricRegistry("servlet");
        }
        return metricsRegistry;
    }

    @Override
    public void destroy() {
        
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        final StatusExposingServletResponse wrappedResponse =
                new StatusExposingServletResponse((HttpServletResponse) response);
        activeRequests.inc();
        final Timer.Context context = requestTimer.time();
        try {
            chain.doFilter(request, wrappedResponse);
        } finally {
            context.stop();
            activeRequests.dec();
            markMeterForStatusCode(wrappedResponse.getStatus());
        }
    }

    private void markMeterForStatusCode(int status) {
        final Meter metric = metersByStatusCode.get(status);
        if (metric != null) {
            metric.mark();
        } else {
            otherMeter.mark();
        }
    }

    private static class StatusExposingServletResponse extends HttpServletResponseWrapper {
        // The Servlet spec says: calling setStatus is optional, if no status is set, the default is 200.
        private int httpStatus = 200;

        public StatusExposingServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int sc) throws IOException {
            httpStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        public void setStatus(int sc) {
            httpStatus = sc;
            super.setStatus(sc);
        }

        public int getStatus() {
            return httpStatus;
        }
    }
}
