package org.jboss.shamrock.vertx.runtime;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Produces a configured Vert.x instance.
 * It also exposes the Vert.x event bus.
 */
@ApplicationScoped
public class VertxProducer {

    private volatile VertxConfiguration conf;
    private volatile Vertx vertx;

    private void initialize() {
        if (conf == null) {
            this.vertx = Vertx.vertx();
            return;
        }

        VertxOptions options = convertToVertxOptions(conf);

        if (!conf.useAsyncDNS) {
            System.setProperty("vertx.disableDnsResolver", "true");
        }

        System.setProperty("vertx.cacheDirBase", System.getProperty("java.io.tmpdir"));

        if (options.isClustered()) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Vertx.clusteredVertx(options, ar -> {
                if (ar.failed()) {
                    failure.set(ar.cause());
                } else {
                    this.vertx = ar.result();
                }
                latch.countDown();
            });
            try {
                latch.await();
                if (failure.get() != null) {
                    throw new IllegalStateException("Unable to initialize the Vert.x instance", failure.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Unable to initialize the Vert.x instance", e);
            }
        } else {
            this.vertx = Vertx.vertx(options);
        }
    }

    private VertxOptions convertToVertxOptions(VertxConfiguration conf) {
        VertxOptions options = new VertxOptions();
        // Order matters, as the cluster options modifies the event bus options.
        setEventBusOptions(options);
        initializeClusterOptions(options);

        options.setFileSystemOptions(new FileSystemOptions()
                .setFileCachingEnabled(conf.caching)
                .setClassPathResolvingEnabled(conf.classpathResolving));
        options.setWorkerPoolSize(conf.workerPoolSize);
        options.setBlockedThreadCheckInterval(conf.warningExceptionTime.toMillis());
        options.setInternalBlockingPoolSize(conf.internalBlockingPoolSize);
        if (conf.eventLoopsPoolSize.isPresent()) {
            options.setEventLoopPoolSize(conf.eventLoopsPoolSize.getAsInt());
        }
        // TODO - Add the ability to configure these times in ns when long will be supported
        //  options.setMaxEventLoopExecuteTime(conf.maxEventLoopExecuteTime)
        //         .setMaxWorkerExecuteTime(conf.maxWorkerExecuteTime)
        options.setWarningExceptionTime(conf.warningExceptionTime.toNanos());

        return options;
    }

    @PreDestroy
    public void destroy() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private void initializeClusterOptions(VertxOptions options) {
        ClusterConfiguration cluster = conf.cluster;
        options.setClustered(cluster.clustered);
        options.setClusterPingReplyInterval(cluster.pingReplyInterval.toMillis());
        options.setClusterPingInterval(cluster.pingInterval.toMillis());
        if (cluster.host != null) {
            options.setClusterHost(cluster.host);
        }
        if (cluster.port.isPresent()) {
            options.setClusterPort(cluster.port.getAsInt());
        }
        cluster.publicHost.ifPresent(options::setClusterPublicHost);
        if (cluster.publicPort.isPresent()) {
            options.setClusterPort(cluster.publicPort.getAsInt());
        }
    }

    private void setEventBusOptions(VertxOptions options) {
        EventBusConfiguration eb = conf.eventbus;
        EventBusOptions opts = new EventBusOptions();
        opts.setAcceptBacklog(eb.acceptBacklog.orElse(-1));
        opts.setClientAuth(ClientAuth.valueOf(eb.clientAuth.toUpperCase()));
        opts.setConnectTimeout((int) (Math.min(Integer.MAX_VALUE, eb.connectTimeout.toMillis())));
        // todo: use timeUnit cleverly
        opts.setIdleTimeout(eb.idleTimeout.isPresent() ? (int) Math.max(1, Math.min(Integer.MAX_VALUE, eb.idleTimeout.get().getSeconds())) : 0);
        opts.setSendBufferSize(eb.sendBufferSize.orElse(-1));
        opts.setSoLinger(eb.soLinger.orElse(-1));
        opts.setSsl(eb.ssl);
        opts.setReceiveBufferSize(eb.receiveBufferSize.orElse(-1));
        opts.setReconnectAttempts(eb.reconnectAttempts);
        opts.setReconnectInterval(eb.reconnectInterval.toMillis());
        opts.setReuseAddress(eb.reuseAddress);
        opts.setReusePort(eb.reusePort);
        opts.setTrafficClass(eb.trafficClass.orElse(-1));
        opts.setTcpKeepAlive(eb.tcpKeepAlive);
        opts.setTcpNoDelay(eb.tcpNoDelay);
        opts.setTrustAll(eb.trustAll);

        // Certificates and trust.
        if (eb.keyCertificatePem != null) {
            List<String> certs = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            eb.keyCertificatePem.certs.ifPresent(s ->
                    certs.addAll(Pattern.compile(",").splitAsStream(s).map(String::trim).collect(Collectors.toList()))
            );
            eb.keyCertificatePem.keys.ifPresent(s ->
                    keys.addAll(Pattern.compile(",").splitAsStream(s).map(String::trim).collect(Collectors.toList()))
            );
            PemKeyCertOptions o = new PemKeyCertOptions()
                    .setCertPaths(certs)
                    .setKeyPaths(keys);
            opts.setPemKeyCertOptions(o);
        }

        if (eb.keyCertificateJks != null) {
            JksOptions o = new JksOptions();
            eb.keyCertificateJks.path.ifPresent(o::setPath);
            eb.keyCertificateJks.password.ifPresent(o::setPassword);
            opts.setKeyStoreOptions(o);
        }

        if (eb.keyCertificatePfx != null) {
            PfxOptions o = new PfxOptions();
            eb.keyCertificatePfx.path.ifPresent(o::setPath);
            eb.keyCertificatePfx.password.ifPresent(o::setPassword);
            opts.setPfxKeyCertOptions(o);
        }

        if (eb.trustCertificatePem != null) {
            eb.trustCertificatePem.certs.ifPresent(s -> {
                PemTrustOptions o = new PemTrustOptions();
                Pattern.compile(",").splitAsStream(s).map(String::trim).forEach(o::addCertPath);
                opts.setPemTrustOptions(o);
            });
        }

        if (eb.trustCertificateJks != null) {
            JksOptions o = new JksOptions();
            eb.trustCertificateJks.path.ifPresent(o::setPath);
            eb.trustCertificateJks.password.ifPresent(o::setPassword);
            opts.setTrustStoreOptions(o);
        }

        if (eb.trustCertificatePfx != null) {
            PfxOptions o = new PfxOptions();
            eb.trustCertificatePfx.path.ifPresent(o::setPath);
            eb.trustCertificatePfx.password.ifPresent(o::setPassword);
            opts.setPfxTrustOptions(o);
        }
        options.setEventBusOptions(opts);
    }

    @Singleton
    @Produces
    public synchronized Vertx vertx() {
        if (vertx != null) {
            return vertx;
        }
        initialize();
        return this.vertx;
    }

    @Singleton
    @Produces
    public synchronized EventBus eventbus() {
        if (vertx == null) {
            initialize();
        }
        return this.vertx.eventBus();
    }

    void configure(VertxConfiguration config) {
        this.conf = config;
    }

    void registerMessageConsumers(List<Map<String, String>> messageConsumers) {
        if (!messageConsumers.isEmpty()) {
            EventBus eventBus = eventbus();
            CountDownLatch latch = new CountDownLatch(messageConsumers.size());
            for (Map<String, String> messageConsumer : messageConsumers) {
                EventConsumerInvoker invoker = createInvoker(messageConsumer.get("invokerClazz"));
                String address = messageConsumer.get("address");
                MessageConsumer<Object> consumer;
                if (Boolean.valueOf(messageConsumer.get("local"))) {
                    consumer = eventBus.localConsumer(address);
                } else {
                    consumer = eventBus.consumer(address);
                }
                consumer.handler(m -> invoker.invoke(m));
                consumer.completionHandler(ar -> {
                    if (ar.succeeded()) {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Unable to register all message consumer methods", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private EventConsumerInvoker createInvoker(String invokerClassName) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = VertxProducer.class.getClassLoader();
            }
            Class<? extends EventConsumerInvoker> invokerClazz = (Class<? extends EventConsumerInvoker>) cl.loadClass(invokerClassName);
            return invokerClazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to create invoker: " + invokerClassName, e);
        }
    }
}
