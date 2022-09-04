package io.dropwizard.http2;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.jetty.Jetty93InstrumentedConnectionFactory;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.ThreadPool;

/**
 * Builds HTTP/2 over TLS connectors.
 * <p/>
 * <b>Configuration Parameters:</b>
 * <table>
 *     <tr>
 *         <td>Name</td>
 *         <td>Default</td>
 *         <td>Description</td>
 *     </tr>
 *     <tr>
 *         <td>{@code maxConcurrentStreams}</td>
 *         <td><1024</td>
 *         <td>
 *             The maximum number of concurrently open streams allowed on a single HTTP/2 connection.
 *             Larger values increase parallelism, but cost a memory commitment.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code initialStreamSendWindow}</td>
 *         <td>65535</td>
 *         <td>
 *             The initial flow control window size for a new stream. Larger values may allow greater throughput,
 *             but also risk head of line blocking if TCP/IP flow control is triggered.
 *         </td>
 *     </tr>
 * </table>
 * <p/>
 * For more configuration parameters, see {@link HttpsConnectorFactory}.
 *
 * @see HttpsConnectorFactory
 */
@JsonTypeName("http2")
public class Http2ConnectorFactory extends HttpsConnectorFactory {

    private int maxConcurrentStreams = 1024;
    private int initialStreamSendWindow = 65535;

    @JsonProperty
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    @JsonProperty
    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    @JsonProperty
    public int getInitialStreamSendWindow() {
        return initialStreamSendWindow;
    }

    @JsonProperty
    public void setInitialStreamSendWindow(int initialStreamSendWindow) {
        this.initialStreamSendWindow = initialStreamSendWindow;
    }

    /**
     * Supported protocols
     */
    private static final String H2 = "h2";
    private static final String H2_17 = "h2-17";
    private static final String HTTP_1_1 = "http/1.1";

    @Override
    public Connector build(Server server, MetricRegistry metrics, String name, ThreadPool threadPool) {
        logSupportedParameters();

        // Setup connection factories
        final HttpConfiguration httpConfig = buildHttpConfiguration();
        final HttpConnectionFactory http1 = buildHttpConnectionFactory(httpConfig);
        final HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(httpConfig);
        http2.setMaxConcurrentStreams(maxConcurrentStreams);
        http2.setInitialStreamSendWindow(initialStreamSendWindow);

        final NegotiatingServerConnectionFactory alpn = new ALPNServerConnectionFactory(H2, H2_17);
        alpn.setDefaultProtocol(HTTP_1_1); // Speak HTTP 1.1 over TLS if negotiation fails

        final SslContextFactory sslContextFactory = buildSslContextFactory();
        server.addBean(sslContextFactory);

        // We should use ALPN as a negotiation protocol. Old clients that don't support it will be served
        // via HTTPS. New clients, however, that want to use HTTP/2 will use TLS with ALPN extension.
        // If negotiation succeeds, the client and server switch to HTTP/2 protocol.
        final SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "alpn");

        return buildConnector(server, new ScheduledExecutorScheduler(), buildBufferPool(), name, threadPool,
                new Jetty93InstrumentedConnectionFactory(sslConnectionFactory, metrics.timer(httpConnections())),
                alpn, http2, http1);
    }
}
