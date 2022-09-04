package io.dropwizard.client;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient.HttpClientMetricNameStrategies;
import com.codahale.metrics.httpclient.InstrumentedHttpClientConnectionManager;
import com.codahale.metrics.httpclient.InstrumentedHttpRequestExecutor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import io.dropwizard.util.Duration;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicListHeaderIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import java.net.ProxySelector;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class HttpClientBuilderTest {
    private final Class<?> httpClientBuilderClass;
    private final Class<?> httpClientClass;
    private final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", SSLConnectionSocketFactory.getSocketFactory())
            .build();
    private HttpClientConfiguration configuration;
    private HttpClientBuilder builder;
    private InstrumentedHttpClientConnectionManager connectionManager;
    private org.apache.http.impl.client.HttpClientBuilder apacheBuilder;

    public HttpClientBuilderTest() throws ClassNotFoundException {
        this.httpClientBuilderClass = Class.forName("org.apache.http.impl.client.HttpClientBuilder");
        this.httpClientClass = Class.forName("org.apache.http.impl.client.InternalHttpClient");
    }

    @Before
    public void setUp() {
        final MetricRegistry metricRegistry = new MetricRegistry();
        configuration = new HttpClientConfiguration();
        builder = new HttpClientBuilder(metricRegistry);
        connectionManager = spy(new InstrumentedHttpClientConnectionManager(metricRegistry, registry));
        apacheBuilder = org.apache.http.impl.client.HttpClientBuilder.create();

        initMocks(this);
    }

    @Test
    public void setsTheMaximumConnectionPoolSize() throws Exception {
        configuration.setMaxConnections(412);
        final CloseableHttpClient client = builder.using(configuration)
                .createClient(apacheBuilder, builder.configureConnectionManager(connectionManager), "test");

        assertThat(client).isNotNull();
        assertThat(spyHttpClientBuilderField("connManager", apacheBuilder)).isSameAs(connectionManager);
        verify(connectionManager).setMaxTotal(412);
    }


    @Test
    public void setsTheMaximumRoutePoolSize() throws Exception {
        configuration.setMaxConnectionsPerRoute(413);
        final CloseableHttpClient client = builder.using(configuration)
                .createClient(apacheBuilder, builder.configureConnectionManager(connectionManager), "test");

        assertThat(client).isNotNull();
        assertThat(spyHttpClientBuilderField("connManager", apacheBuilder)).isSameAs(connectionManager);
        verify(connectionManager).setDefaultMaxPerRoute(413);
    }

    @Test
    public void setsTheUserAgent() throws Exception {
        configuration.setUserAgent(Optional.of("qwerty"));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(spyHttpClientBuilderField("userAgent", apacheBuilder)).isEqualTo("qwerty");
    }

    @Test
    public void canUseACustomDnsResolver() throws Exception {
        final DnsResolver resolver = mock(DnsResolver.class);
        final InstrumentedHttpClientConnectionManager manager =
                builder.using(resolver).createConnectionManager(registry, "test");

        // Yes, this is gross. Thanks, Apache!
        final Field connectionOperatorField =
                FieldUtils.getField(PoolingHttpClientConnectionManager.class, "connectionOperator", true);
        final Object connectOperator = connectionOperatorField.get(manager);
        final Field dnsResolverField = FieldUtils.getField(connectOperator.getClass(), "dnsResolver", true);
        assertThat(dnsResolverField.get(connectOperator)).isEqualTo(resolver);
    }


    @Test
    public void usesASystemDnsResolverByDefault() throws Exception {
        final InstrumentedHttpClientConnectionManager manager = builder.createConnectionManager(registry, "test");

        // Yes, this is gross. Thanks, Apache!
        final Field connectionOperatorField =
                FieldUtils.getField(PoolingHttpClientConnectionManager.class, "connectionOperator", true);
        final Object connectOperator = connectionOperatorField.get(manager);
        final Field dnsResolverField = FieldUtils.getField(connectOperator.getClass(), "dnsResolver", true);
        assertThat(dnsResolverField.get(connectOperator)).isInstanceOf(SystemDefaultDnsResolver.class);
    }


    @Test
    public void doesNotReuseConnectionsIfKeepAliveIsZero() throws Exception {
        configuration.setKeepAlive(Duration.seconds(0));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(spyHttpClientBuilderField("reuseStrategy", apacheBuilder))
                .isInstanceOf(NoConnectionReuseStrategy.class);
    }


    @Test
    public void reusesConnectionsIfKeepAliveIsNonZero() throws Exception {
        configuration.setKeepAlive(Duration.seconds(1));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(spyHttpClientBuilderField("reuseStrategy", apacheBuilder))
                .isInstanceOf(DefaultConnectionReuseStrategy.class);
    }

    @Test
    public void usesKeepAliveForPersistentConnections() throws Exception {
        configuration.setKeepAlive(Duration.seconds(1));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        final DefaultConnectionKeepAliveStrategy strategy =
                (DefaultConnectionKeepAliveStrategy) spyHttpClientBuilderField("keepAliveStrategy", apacheBuilder);
        final HttpContext context = mock(HttpContext.class);
        final HttpResponse response = mock(HttpResponse.class);
        when(response.headerIterator(HTTP.CONN_KEEP_ALIVE)).thenReturn(mock(HeaderIterator.class));

        assertThat(strategy.getKeepAliveDuration(response, context)).isEqualTo(1000);
    }

    @Test
    public void usesDefaultForNonPersistentConnections() throws Exception {
        configuration.setKeepAlive(Duration.seconds(1));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        final Field field = FieldUtils.getField(httpClientBuilderClass, "keepAliveStrategy", true);
        final DefaultConnectionKeepAliveStrategy strategy = (DefaultConnectionKeepAliveStrategy) field.get(apacheBuilder);
        final HttpContext context = mock(HttpContext.class);
        final HttpResponse response = mock(HttpResponse.class);
        final HeaderIterator iterator = new BasicListHeaderIterator(
                ImmutableList.<Header>of(new BasicHeader(HttpHeaders.CONNECTION, "timeout=50")),
                HttpHeaders.CONNECTION
        );
        when(response.headerIterator(HTTP.CONN_KEEP_ALIVE)).thenReturn(iterator);

        assertThat(strategy.getKeepAliveDuration(response, context)).isEqualTo(50000);
    }

    @Test
    public void ignoresCookiesByDefault() throws Exception {
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(((RequestConfig) spyHttpClientBuilderField("defaultRequestConfig", apacheBuilder)).getCookieSpec())
                .isEqualTo(CookieSpecs.IGNORE_COOKIES);
    }

    @Test
    public void usesBestMatchCookiePolicyIfCookiesAreEnabled() throws Exception {
        configuration.setCookiesEnabled(true);
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(((RequestConfig) spyHttpClientBuilderField("defaultRequestConfig", apacheBuilder)).getCookieSpec())
                .isEqualTo(CookieSpecs.BEST_MATCH);
    }

    @Test
    public void setsTheSocketTimeout() throws Exception {
        configuration.setTimeout(Duration.milliseconds(500));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(((RequestConfig) spyHttpClientBuilderField("defaultRequestConfig", apacheBuilder)).getSocketTimeout())
                .isEqualTo(500);
    }

    @Test
    public void setsTheConnectTimeout() throws Exception {
        configuration.setConnectionTimeout(Duration.milliseconds(500));
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(((RequestConfig) spyHttpClientBuilderField("defaultRequestConfig", apacheBuilder)).getConnectTimeout())
                .isEqualTo(500);
    }
    
    @Test
    public void setsTheConnectionRequestTimeout() throws Exception {
    	configuration.setConnectionRequestTimeout(Duration.milliseconds(123));
    	
    	assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();
    	assertThat(((RequestConfig) spyHttpClientBuilderField("defaultRequestConfig", apacheBuilder)).getConnectionRequestTimeout())
        		.isEqualTo(123);
    }

    @Test
    public void disablesNaglesAlgorithm() throws Exception {
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(((SocketConfig) spyHttpClientBuilderField("defaultSocketConfig", apacheBuilder)).isTcpNoDelay()).isTrue();
    }

    @Test
    public void disablesStaleConnectionCheck() throws Exception {
        assertThat(builder.using(configuration).createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(((RequestConfig) spyHttpClientBuilderField("defaultRequestConfig", apacheBuilder))
                .isStaleConnectionCheckEnabled()).isFalse();
    }

    @Test
    public void usesTheDefaultRoutePlanner() throws Exception {
        final CloseableHttpClient httpClient = builder.using(configuration)
                .createClient(apacheBuilder, connectionManager, "test");

        assertThat(httpClient).isNotNull();
        assertThat(spyHttpClientBuilderField("routePlanner", apacheBuilder)).isNull();
        assertThat(spyHttpClientField("routePlanner", httpClient)).isInstanceOf(DefaultRoutePlanner.class);
    }

    @Test
    public void usesACustomRoutePlanner() throws Exception {
        ProxySelector proxySelector = (ProxySelector) Class.forName("sun.net.spi.DefaultProxySelector").newInstance();
        final HttpRoutePlanner routePlanner = new SystemDefaultRoutePlanner(proxySelector);
        final CloseableHttpClient httpClient = builder.using(configuration).using(routePlanner)
                .createClient(apacheBuilder, connectionManager, "test");

        assertThat(httpClient).isNotNull();
        assertThat(spyHttpClientBuilderField("routePlanner", apacheBuilder)).isSameAs(routePlanner);
        assertThat(spyHttpClientField("routePlanner", httpClient)).isSameAs(routePlanner);
    }

    @Test
    public void usesACustomHttpRequestRetryHandler() throws Exception {
        final HttpRequestRetryHandler customHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                return false;
            }
        };

        configuration.setRetries(1);
        assertThat(builder.using(configuration).using(customHandler)
                .createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(spyHttpClientBuilderField("retryHandler", apacheBuilder)).isSameAs(customHandler);
    }

    @Test
    public void usesCredentialsProvider() throws Exception {
        final CredentialsProvider credentialsProvider = new CredentialsProvider() {
            @Override
            public void setCredentials(AuthScope authscope, Credentials credentials) {
            }

            @Override
            public Credentials getCredentials(AuthScope authscope) {
                return null;
            }

            @Override
            public void clear() {
            }
        };

        assertThat(builder.using(configuration).using(credentialsProvider)
                .createClient(apacheBuilder, connectionManager, "test")).isNotNull();

        assertThat(spyHttpClientBuilderField("credentialsProvider", apacheBuilder)).isSameAs(credentialsProvider);
    }

    @Test
    public void usesACustomHttpClientMetricNameStrategy() throws Exception {
        assertThat(builder.using(HttpClientMetricNameStrategies.HOST_AND_METHOD)
                .createClient(apacheBuilder, connectionManager, "test"))
                .isNotNull();
        assertThat(FieldUtils.getField(InstrumentedHttpRequestExecutor.class,
                "metricNameStrategy", true)
                .get(spyHttpClientBuilderField("requestExec", apacheBuilder)))
                .isSameAs(HttpClientMetricNameStrategies.HOST_AND_METHOD);
    }

    @Test
    public void usesMethodOnlyHttpClientMetricNameStrategyByDefault() throws Exception {
        assertThat(builder.createClient(apacheBuilder, connectionManager, "test"))
                .isNotNull();
        assertThat(FieldUtils.getField(InstrumentedHttpRequestExecutor.class,
                "metricNameStrategy", true)
                .get(spyHttpClientBuilderField("requestExec", apacheBuilder)))
                .isSameAs(HttpClientMetricNameStrategies.METHOD_ONLY);
    }

    private Object spyHttpClientBuilderField(final String fieldName, final Object obj) throws Exception {
        final Field field = FieldUtils.getField(httpClientBuilderClass, fieldName, true);
        return field.get(obj);
    }

    private Object spyHttpClientField(final String fieldName, final Object obj) throws Exception {
        final Field field = FieldUtils.getField(httpClientClass, fieldName, true);
        return field.get(obj);
    }
}
