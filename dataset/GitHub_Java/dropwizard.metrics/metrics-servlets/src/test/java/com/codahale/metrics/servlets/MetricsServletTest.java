package com.codahale.metrics.servlets;

import com.codahale.metrics.*;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.eclipse.jetty.testing.ServletTester;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetricsServletTest extends AbstractServletTest {
    private final Clock clock = mock(Clock.class);
    private final MetricRegistry registry = new MetricRegistry();

    @Override
    protected void setUp(ServletTester tester) {
        tester.setAttribute("com.codahale.metrics.servlets.MetricsServlet.registry", registry);
        tester.addServlet(MetricsServlet.class, "/metrics");
    }

    @Before
    public void setUp() throws Exception {
        when(clock.getTick()).thenReturn(100L, 200L, 300L, 400L);

        registry.register("g1", new Gauge<Long>() {
            @Override
            public Long getValue() {
                return 100L;
            }
        });
        registry.counter("c").inc();
        registry.histogram("h").update(1);
        registry.register("m", new Meter(clock)).mark();
        registry.register("t", new Timer(new ExponentiallyDecayingReservoir(), clock))
                .update(1, TimeUnit.SECONDS);

        request.setMethod("GET");
        request.setURI("/metrics");
        request.setVersion("HTTP/1.0");
    }

    @Test
    public void returnsA200() throws Exception {
        processRequest();

        assertThat(response.getStatus())
                .isEqualTo(200);
        assertThat(response.getContent())
                .isEqualTo("{" +
                                   "\"version\":\"3.0.0\"," +
                                   "\"gauges\":{" +
                                       "\"g1\":{\"value\":100}" +
                                   "}," +
                                   "\"counters\":{" +
                                       "\"c\":{\"count\":1}" +
                                   "}," +
                                   "\"histograms\":{" +
                                       "\"h\":{\"count\":1,\"max\":1,\"mean\":1.0,\"min\":1,\"p50\":1.0,\"p75\":1.0,\"p95\":1.0,\"p98\":1.0,\"p99\":1.0,\"p999\":1.0,\"stddev\":0.0}" +
                                   "}," +
                                   "\"meters\":{" +
                                       "\"m\":{\"count\":1,\"m15_rate\":0.0,\"m1_rate\":0.0,\"m5_rate\":0.0,\"mean_rate\":3333333.3333333335,\"units\":\"events/second\"}},\"timers\":{\"t\":{\"count\":1,\"max\":1.0,\"mean\":1.0,\"min\":1.0,\"p50\":1.0,\"p75\":1.0,\"p95\":1.0,\"p98\":1.0,\"p99\":1.0,\"p999\":1.0,\"stddev\":0.0,\"m15_rate\":0.0,\"m1_rate\":0.0,\"m5_rate\":0.0,\"mean_rate\":1.0E7,\"duration_units\":\"seconds\",\"rate_units\":\"calls/second\"}" +
                                   "}" +
                               "}");
        assertThat(response.getContentType())
                .isEqualTo("application/json");
    }

    @Test
    public void optionallyPrettyPrintsTheJson() throws Exception {
        request.setURI("/metrics?pretty=true");

        processRequest();

        assertThat(response.getStatus())
                .isEqualTo(200);
        assertThat(response.getContent())
                .isEqualTo(String.format("{%n" +
                                                 "  \"version\" : \"3.0.0\",%n" +
                                                 "  \"gauges\" : {%n" +
                                                 "    \"g1\" : {%n" +
                                                 "      \"value\" : 100%n" +
                                                 "    }%n" +
                                                 "  },%n" +
                                                 "  \"counters\" : {%n" +
                                                 "    \"c\" : {%n" +
                                                 "      \"count\" : 1%n" +
                                                 "    }%n" +
                                                 "  },%n" +
                                                 "  \"histograms\" : {%n" +
                                                 "    \"h\" : {%n" +
                                                 "      \"count\" : 1,%n" +
                                                 "      \"max\" : 1,%n" +
                                                 "      \"mean\" : 1.0,%n" +
                                                 "      \"min\" : 1,%n" +
                                                 "      \"p50\" : 1.0,%n" +
                                                 "      \"p75\" : 1.0,%n" +
                                                 "      \"p95\" : 1.0,%n" +
                                                 "      \"p98\" : 1.0,%n" +
                                                 "      \"p99\" : 1.0,%n" +
                                                 "      \"p999\" : 1.0,%n" +
                                                 "      \"stddev\" : 0.0%n" +
                                                 "    }%n" +
                                                 "  },%n" +
                                                 "  \"meters\" : {%n" +
                                                 "    \"m\" : {%n" +
                                                 "      \"count\" : 1,%n" +
                                                 "      \"m15_rate\" : 0.0,%n" +
                                                 "      \"m1_rate\" : 0.0,%n" +
                                                 "      \"m5_rate\" : 0.0,%n" +
                                                 "      \"mean_rate\" : 3333333.3333333335,%n" +
                                                 "      \"units\" : \"events/second\"%n" +
                                                 "    }%n" +
                                                 "  },%n" +
                                                 "  \"timers\" : {%n" +
                                                 "    \"t\" : {%n" +
                                                 "      \"count\" : 1,%n" +
                                                 "      \"max\" : 1.0,%n" +
                                                 "      \"mean\" : 1.0,%n" +
                                                 "      \"min\" : 1.0,%n" +
                                                 "      \"p50\" : 1.0,%n" +
                                                 "      \"p75\" : 1.0,%n" +
                                                 "      \"p95\" : 1.0,%n" +
                                                 "      \"p98\" : 1.0,%n" +
                                                 "      \"p99\" : 1.0,%n" +
                                                 "      \"p999\" : 1.0,%n" +
                                                 "      \"stddev\" : 0.0,%n" +
                                                 "      \"m15_rate\" : 0.0,%n" +
                                                 "      \"m1_rate\" : 0.0,%n" +
                                                 "      \"m5_rate\" : 0.0,%n" +
                                                 "      \"mean_rate\" : 1.0E7,%n" +
                                                 "      \"duration_units\" : \"seconds\",%n" +
                                                 "      \"rate_units\" : \"calls/second\"%n" +
                                                 "    }%n" +
                                                 "  }%n" +
                                                 "}"));
        assertThat(response.getContentType())
                .isEqualTo("application/json");
    }

    @Test
    public void constructorWithRegistryAsArgumentIsUsedInPreferenceOverServletConfig() throws Exception {
    	MetricRegistry metricRegistry = mock(MetricRegistry.class);
    	ServletContext servletContext = mock(ServletContext.class);
    	ServletConfig servletConfig = mock(ServletConfig.class);
    	when(servletConfig.getServletContext()).thenReturn(servletContext);
    	
    	MetricsServlet metricsServlet = new MetricsServlet(metricRegistry);
    	metricsServlet.init(servletConfig);
    	
    	verify(servletConfig, times(3)).getServletContext();
    	verify(servletContext, never()).getAttribute(eq(MetricsServlet.METRICS_REGISTRY));
    }

    @Test
    public void constructorWithRegistryAsArgumentUsesServletConfigWhenNull() throws Exception {
    	MetricRegistry metricRegistry = mock(MetricRegistry.class);
    	ServletContext servletContext = mock(ServletContext.class);
    	ServletConfig servletConfig = mock(ServletConfig.class);
    	when(servletConfig.getServletContext()).thenReturn(servletContext);
    	when(servletContext.getAttribute(eq(MetricsServlet.METRICS_REGISTRY)))
    		.thenReturn(metricRegistry);
    	
    	MetricsServlet metricsServlet = new MetricsServlet(null);
    	metricsServlet.init(servletConfig);
    	
    	verify(servletConfig, times(4)).getServletContext();
    	verify(servletContext, times(1)).getAttribute(eq(MetricsServlet.METRICS_REGISTRY));
    }

    @Test(expected = ServletException.class)
    public void constructorWithRegistryAsArgumentUsesServletConfigWhenNullButWrongTypeInContext() throws Exception {
    	ServletContext servletContext = mock(ServletContext.class);
    	ServletConfig servletConfig = mock(ServletConfig.class);
    	when(servletConfig.getServletContext()).thenReturn(servletContext);
    	when(servletContext.getAttribute(eq(MetricsServlet.METRICS_REGISTRY)))
    		.thenReturn("IRELLEVANT_STRING");
    	
    	MetricsServlet metricsServlet = new MetricsServlet(null);
    	metricsServlet.init(servletConfig);
    }
}
