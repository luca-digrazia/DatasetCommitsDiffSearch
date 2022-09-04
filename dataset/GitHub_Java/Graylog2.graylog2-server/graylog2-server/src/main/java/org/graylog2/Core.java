/*
 * Copyright 2012-2014 TORCH GmbH
 *
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

package org.graylog2;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.cliffc.high_scale_lib.Counter;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.glassfish.jersey.server.internal.scanning.PackageNamesScanner;
import org.graylog2.alerts.AlertService;
import org.graylog2.alerts.AlertServiceImpl;
import org.graylog2.blacklists.BlacklistCache;
import org.graylog2.buffers.OutputBuffer;
import org.graylog2.buffers.processors.ServerProcessBufferProcessor;
import org.graylog2.cluster.NodeService;
import org.graylog2.cluster.NodeServiceImpl;
import org.graylog2.dashboards.DashboardRegistry;
import org.graylog2.dashboards.DashboardService;
import org.graylog2.dashboards.DashboardServiceImpl;
import org.graylog2.database.MongoConnection;
import org.graylog2.indexer.Deflector;
import org.graylog2.indexer.IndexFailureService;
import org.graylog2.indexer.IndexFailureServiceImpl;
import org.graylog2.indexer.Indexer;
import org.graylog2.indexer.ranges.IndexRangeService;
import org.graylog2.indexer.ranges.IndexRangeServiceImpl;
import org.graylog2.initializers.Initializers;
import org.graylog2.inputs.*;
import org.graylog2.inputs.gelf.gelf.GELFChunkManager;
import org.graylog2.jersey.container.netty.NettyContainer;
import org.graylog2.metrics.MongoDbMetricsReporter;
import org.graylog2.metrics.jersey2.MetricsDynamicBinding;
import org.graylog2.notifications.NotificationService;
import org.graylog2.notifications.NotificationServiceImpl;
import org.graylog2.outputs.OutputRegistry;
import org.graylog2.periodical.Periodicals;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.InputHost;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.Version;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.transports.Transport;
import org.graylog2.plugin.buffers.Buffer;
import org.graylog2.plugin.filters.MessageFilter;
import org.graylog2.plugin.indexer.MessageGateway;
import org.graylog2.plugin.initializers.Initializer;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.lifecycles.Lifecycle;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.rest.AnyExceptionClassMapper;
import org.graylog2.plugin.rest.JacksonPropertyExceptionMapper;
import org.graylog2.plugins.PluginLoader;
import org.graylog2.rest.CORSFilter;
import org.graylog2.rest.ObjectMapperProvider;
import org.graylog2.rest.RestAccessLogFilter;
import org.graylog2.savedsearches.SavedSearchService;
import org.graylog2.savedsearches.SavedSearchServiceImpl;
import org.graylog2.security.AccessTokenService;
import org.graylog2.security.AccessTokenServiceImpl;
import org.graylog2.security.ShiroSecurityBinding;
import org.graylog2.security.ShiroSecurityContextFactory;
import org.graylog2.security.ldap.LdapConnector;
import org.graylog2.security.ldap.LdapSettingsService;
import org.graylog2.security.ldap.LdapSettingsServiceImpl;
import org.graylog2.security.realm.LdapUserAuthenticator;
import org.graylog2.shared.ProcessingHost;
import org.graylog2.shared.ServerStatus;
import org.graylog2.shared.buffers.ProcessBuffer;
import org.graylog2.shared.buffers.processors.ProcessBufferProcessor;
import org.graylog2.shared.filters.FilterRegistry;
import org.graylog2.shared.inputs.InputRegistry;
import org.graylog2.shared.stats.ThroughputStats;
import org.graylog2.streams.StreamRuleService;
import org.graylog2.streams.StreamRuleServiceImpl;
import org.graylog2.streams.StreamService;
import org.graylog2.streams.StreamServiceImpl;
import org.graylog2.system.activities.Activity;
import org.graylog2.system.activities.ActivityWriter;
import org.graylog2.system.activities.SystemMessageService;
import org.graylog2.system.activities.SystemMessageServiceImpl;
import org.graylog2.system.jobs.SystemJobManager;
import org.graylog2.system.shutdown.GracefulShutdown;
import org.graylog2.users.UserService;
import org.graylog2.users.UserServiceImpl;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server core, handling and holding basically everything.
 * 
 * (Du kannst das Geraet nicht bremsen, schon garnicht mit bloßen Haenden.)
 * 
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class Core implements GraylogServer, InputHost, ProcessingHost {

    private static final Logger LOG = LoggerFactory.getLogger(Core.class);

    @Inject
    private MongoConnection mongoConnection;
    @Inject
    private Configuration configuration;
    @Inject
    private ServerStatus serverStatus;
    private RulesEngineImpl rulesEngine;
    private GELFChunkManager gelfChunkManager;

    @Inject
    @Named("scheduler")
    private ScheduledExecutorService scheduler;

    @Inject
    @Named("daemonScheduler")
    private ScheduledExecutorService daemonScheduler;

    public static final Version GRAYLOG2_VERSION = ServerVersion.VERSION;
    public static final String GRAYLOG2_CODENAME = "Moose";

    private Indexer indexer;

    private Counter benchmarkCounter = new Counter();
    private Counter throughputCounter = new Counter();
    private AtomicReference<ConcurrentHashMap<String, Counter>> streamThroughput =
            new AtomicReference<ConcurrentHashMap<String, Counter>>(new ConcurrentHashMap<String, Counter>());
    @Inject
    private FilterRegistry filterRegistry;

    private List<MessageFilter> filters = Lists.newArrayList();
    private List<Transport> transports = Lists.newArrayList();
    private List<AlarmCallback> alarmCallbacks = Lists.newArrayList();

    private Initializers initializers;
    private ServerInputRegistry inputs;

    @Inject
    private OutputRegistry outputs;
    private Periodicals periodicals;

    private ProcessBuffer processBuffer;
    private OutputBuffer outputBuffer;
    private AtomicInteger outputBufferWatermark = new AtomicInteger();
    private AtomicInteger processBufferWatermark = new AtomicInteger();
    
    private Cache inputCache;
    private Cache outputCache;
    
    private Deflector deflector;
    
    @Inject
    private ActivityWriter activityWriter;

    private SystemJobManager systemJobManager;

    private boolean localMode = false;
    private boolean statsMode = false;

    private AtomicBoolean isProcessing = new AtomicBoolean(true);
    private AtomicBoolean processingPauseLocked = new AtomicBoolean(false);
    
    @Inject
    private MetricRegistry metricRegistry;
    private LdapUserAuthenticator ldapUserAuthenticator;
    private LdapConnector ldapConnector;
    private DefaultSecurityManager securityManager;
    private MongoDbMetricsReporter metricsReporter;
    private AtomicReference<HashMap<String, Counter>> currentStreamThroughput = new AtomicReference<HashMap<String, Counter>>();

    @Inject
    private ProcessBuffer.Factory processBufferFactory;
    @Inject
    private ServerProcessBufferProcessor.Factory processBufferProcessorFactory;
    @Inject
    private OutputBuffer.Factory outputBufferFactory;

    @Inject
    private ThroughputStats throughputStats;

    @Inject
    private DashboardRegistry dashboardRegistry;

    public void initialize() {
        if (configuration.isMetricsCollectionEnabled()) {
            metricsReporter = MongoDbMetricsReporter.forRegistry(this, metricRegistry).build();
            metricsReporter.start(1, TimeUnit.SECONDS);
        }

        if (this.configuration.getRestTransportUri() == null) {
                String guessedIf;
                try {
                    guessedIf = Tools.guessPrimaryNetworkAddress().getHostAddress();
                } catch (Exception e) {
                    LOG.error("Could not guess primary network address for rest_transport_uri. Please configure it in your graylog2.conf.", e);
                    throw new RuntimeException("No rest_transport_uri.");
                }

                String transportStr = "http://" + guessedIf + ":" + configuration.getRestListenUri().getPort();
                LOG.info("No rest_transport_uri set. Falling back to [{}].", transportStr);
                this.configuration.setRestTransportUri(transportStr);
        }

        initializers = new Initializers(this);
        inputs = new ServerInputRegistry(this);
        periodicals = new Periodicals(this, scheduler, daemonScheduler);

        if (isMaster()) {
            dashboardRegistry.loadPersisted(this);
        }

        systemJobManager = new SystemJobManager(this);

        inputCache = new BasicCache();
        outputCache = new BasicCache();

        outputBuffer = outputBufferFactory.create(this, outputCache);
        outputBuffer.initialize();

        int processBufferProcessorCount = configuration.getProcessBufferProcessors();

        ProcessBufferProcessor[] processors = new ProcessBufferProcessor[processBufferProcessorCount];

        for (int i = 0; i < processBufferProcessorCount; i++) {
            processors[i] = processBufferProcessorFactory.create(this, outputBuffer, i, processBufferProcessorCount);
        }

        processBuffer = processBufferFactory.create(this, inputCache);
        processBuffer.initialize(processors, this.getConfiguration().getRingSize(),
                this.getConfiguration().getProcessorWaitStrategy(),
                this.getConfiguration().getProcessBufferProcessors()
        );

        gelfChunkManager = new GELFChunkManager(this);

        indexer = new Indexer(this);
        indexer.start();

        final Core core = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                String msg = "SIGNAL received. Shutting down.";
                LOG.info(msg);
                activityWriter.write(new Activity(msg, Core.class));

                GracefulShutdown gs = new GracefulShutdown(core);
                gs.run();
            }
        });
    }

    private void registerTransport(Transport transport) {
        this.transports.add(transport);
    }
    
    public void registerAlarmCallback(AlarmCallback alarmCallback) {
        this.alarmCallbacks.add(alarmCallback);
    }

    @Override
    public void run() {

        gelfChunkManager.start();
        BlacklistCache.initialize(this);

        // Set up deflector.
        LOG.info("Setting up deflector.");
        deflector = new Deflector(this);
        deflector.setUp();

        // Load and register plugins.
        registerPlugins(MessageInput.class, "inputs");

        // Ramp it all up. (both plugins and built-in types)
        initializers().initialize();
        outputs().initialize();

        // Load persisted inputs.
        inputs().launchAllPersisted();

        /*
        // Initialize all registered inputs.
        for (MessageInput input : this.inputs) {
            try {
                // This is a plugin. Initialize with custom config from Mongo.
                input.initialize(PluginConfiguration.load(this, input.getClass().getCanonicalName()), this);
                LOG.debug("Initialized input: {}", input.getName());
            } catch (MessageInputConfigurationException e) {
                LOG.error("Could not initialize input <{}>.", input.getClass().getCanonicalName(), e);
            }
        }}
        */
    }

    public void setLdapConnector(LdapConnector ldapConnector) {
        this.ldapConnector = ldapConnector;
    }

    public LdapConnector getLdapConnector() {
        return ldapConnector;
    }

    public DefaultSecurityManager getSecurityManager() {
        return securityManager;
    }

    public void setSecurityManager(DefaultSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void incrementStreamThroughput(String streamId) {
        final ConcurrentHashMap<String, Counter> counterMap = streamThroughput.get();
        Counter counter;
        synchronized (counterMap) {
            counter = counterMap.get(streamId);
            if (counter == null) {
                counter = new Counter();
                counterMap.put(streamId, counter);
            }
        }
        counter.increment();
    }

    public Map<String, Counter> cycleStreamThroughput() {
        return streamThroughput.getAndSet(new ConcurrentHashMap<String, Counter>());
    }

    public void setCurrentStreamThroughput(HashMap<String, Counter> throughput) {
        currentStreamThroughput.set(throughput);
    }

    public HashMap<String, Counter> getCurrentStreamThroughput() {
        return currentStreamThroughput.get();
    }

    private class Graylog2Binder extends AbstractBinder {

        @Override
        protected void configure() {
            bind(Core.this).to(Core.class);
            bind(metricRegistry).to(MetricRegistry.class);
            bind(throughputStats).to(ThroughputStats.class);
            bind(new StreamServiceImpl(mongoConnection)).to(StreamService.class);
            bind(new StreamRuleServiceImpl(mongoConnection)).to(StreamRuleService.class);
            bind(new DashboardServiceImpl(mongoConnection)).to(DashboardService.class);
            bind(new NodeServiceImpl(mongoConnection)).to(NodeService.class);
            bind(new LdapSettingsServiceImpl(mongoConnection)).to(LdapSettingsService.class);
            bind(new SystemMessageServiceImpl(mongoConnection)).to(SystemMessageService.class);
            bind(new NotificationServiceImpl(mongoConnection)).to(NotificationService.class);
            bind(new InputServiceImpl(mongoConnection)).to(InputService.class);
            bind(new AlertServiceImpl(mongoConnection)).to(AlertService.class);
            bind(new UserServiceImpl(mongoConnection, configuration)).to(UserService.class);
            bind(new AccessTokenServiceImpl(mongoConnection)).to(AccessTokenService.class);
            bind(new IndexRangeServiceImpl(mongoConnection, getActivityWriter())).to(IndexRangeService.class);
            bind(new SavedSearchServiceImpl(mongoConnection)).to(SavedSearchService.class);
            bind(new IndexFailureServiceImpl(mongoConnection)).to(IndexFailureService.class);
            bind(dashboardRegistry).to(DashboardRegistry.class);
            bind(activityWriter).to(ActivityWriter.class);
        }
    }


    public void startRestApi() throws IOException {
        final ExecutorService bossExecutor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setNameFormat("restapi-boss-%d")
                        .build());

        final ExecutorService workerExecutor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setNameFormat("restapi-worker-%d")
                        .build());

        final ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                bossExecutor,
                workerExecutor
        ));

        ResourceConfig rc = new ResourceConfig()
                .property(NettyContainer.PROPERTY_BASE_URI, configuration.getRestListenUri())
                .registerClasses(MetricsDynamicBinding.class,
                        JacksonPropertyExceptionMapper.class,
                        AnyExceptionClassMapper.class,
                        ShiroSecurityBinding.class,
                        RestAccessLogFilter.class)
                .register(new Graylog2Binder())
                .register(ObjectMapperProvider.class)
                .register(JacksonJsonProvider.class)
                .registerFinder(new PackageNamesScanner(new String[]{"org.graylog2.rest.resources"}, true));

        if (configuration.isRestEnableGzip())
            EncodingFilter.enableFor(rc, GZipEncoder.class);

        if (configuration.isRestEnableCors()) {
            LOG.info("Enabling CORS for REST API");
            rc.register(CORSFilter.class);
        }

        /*rc = rc.registerFinder(new PackageNamesScanner(new String[]{"org.graylog2.rest.resources"}, true));*/

        final NettyContainer jerseyHandler = ContainerFactory.createContainer(NettyContainer.class, rc);
        jerseyHandler.setSecurityContextFactory(new ShiroSecurityContextFactory(this));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("chunks", new ChunkedWriteHandler());
                pipeline.addLast("jerseyHandler", jerseyHandler);
                return pipeline;
            }
        }) ;
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.bind(new InetSocketAddress(
                configuration.getRestListenUri().getHost(),
                configuration.getRestListenUri().getPort()
        ));

        LOG.info("Started REST API at <{}>", configuration.getRestListenUri());
    }


    private <A> void registerPlugins(Class<A> type, String subDirectory) {
        PluginLoader<A> pl = new PluginLoader<A>(configuration.getPluginDir(), subDirectory, type);
        for (A plugin : pl.getPlugins()) {
            LOG.info("Loaded <{}> plugin [{}].", type.getSimpleName(), plugin.getClass().getCanonicalName());

            if (plugin instanceof MessageFilter) {
                filterRegistry.register((MessageFilter) plugin);
            } else if (plugin instanceof MessageInput) {
                inputs.register(plugin.getClass(), ((MessageInput) plugin).getName());
            } else if (plugin instanceof MessageOutput) {
                outputs.register((MessageOutput) plugin);
            } else if (plugin instanceof AlarmCallback) {
                registerAlarmCallback((AlarmCallback) plugin);
            } else if (plugin instanceof Initializer) {
                initializers.register((Initializer) plugin);
            } else if (plugin instanceof Transport) {
                registerTransport((Transport) plugin);
            } else {
                LOG.error("Could not load plugin [{}] - Not supported type.", plugin.getClass().getCanonicalName());
            }
        }
    }

    public MongoConnection getMongoConnection() {
        return mongoConnection;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setRulesEngine(RulesEngineImpl engine) {
        rulesEngine = engine;
    }

    public RulesEngineImpl getRulesEngine() {
        return rulesEngine;
    }

    public Indexer getIndexer() {
        return indexer;
    }

    public GELFChunkManager getGELFChunkManager() {
        return this.gelfChunkManager;
    }

    @Override
    public Buffer getProcessBuffer() {
        return this.processBuffer;
    }

    @Override
    public Buffer getOutputBuffer() {
        return this.outputBuffer;
    }
    
    public AtomicInteger outputBufferWatermark() {
        return outputBufferWatermark;
    }
    
    public AtomicInteger processBufferWatermark() {
        return processBufferWatermark;
    }
    
    public List<Transport> getTransports() {
        return this.transports;
    }
    
    public List<AlarmCallback> getAlarmCallbacks() {
        return this.alarmCallbacks;
    }

    public Deflector getDeflector() {
        return this.deflector;
    }

    public ActivityWriter getActivityWriter() {
        return this.activityWriter;
    }

    public SystemJobManager getSystemJobManager() {
        return this.systemJobManager;
    }

    public void setLdapAuthenticator(LdapUserAuthenticator authenticator) {
        this.ldapUserAuthenticator = authenticator;
    }

    public LdapUserAuthenticator getLdapAuthenticator() {
        return ldapUserAuthenticator;
    }

    @Override
    public boolean isMaster() {
        return this.configuration.isMaster();
    }
    
    @Override
    public String getNodeId() {
        return serverStatus.getNodeId().toString();
    }
    
    @Override
    public MessageGateway getMessageGateway() {
        return this.indexer.getMessageGateway();
    }
    
    public void setLocalMode(boolean mode) {
        this.localMode = mode;
    }
   
    public boolean isLocalMode() {
        return localMode;
    }

    public void setStatsMode(boolean mode) {
        this.statsMode = mode;
    }
   
    public boolean isStatsMode() {
        return statsMode;
    }
    
    public Cache getInputCache() {
        return inputCache;
    }
    
    public Cache getOutputCache() {
        return outputCache;
    }
    
    public DateTime getStartedAt() {
    	return serverStatus.getStartedAt();
    }

    public void pauseMessageProcessing(boolean locked) {
        isProcessing.set(false);
        setLifecycle(Lifecycle.PAUSED);

        // Never override pause lock if already locked.
        if (!processingPauseLocked.get()) {
            processingPauseLocked.set(locked);
        }
    }

    public void resumeMessageProcessing() throws ProcessingPauseLockedException {
        if (processingPauseLocked()) {
            throw new ProcessingPauseLockedException("Processing pause is locked. Wait until the locking task has finished " +
                    "or manually unlock if you know what you are doing.");
        }

        isProcessing.set(true);
        setLifecycle(Lifecycle.RUNNING);
    }

    public boolean processingPauseLocked() {
        return processingPauseLocked.get();
    }

    public void unlockProcessingPause() {
        processingPauseLocked.set(false);
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }

    public MetricRegistry metrics() {
        return metricRegistry;
    }

    /**
     * Shortcut to delete an index. This is for plugin compat. We seriously need proper DI.
     *
     * @param indexName The name of the index to delete.
     */
    @Override
    public void deleteIndexShortcut(String indexName) {
        getIndexer().indices().delete(indexName);
    }

    /**
     * Shortcut to close an index. This is for plugin compat. We seriously need proper DI.
     *
     * @param indexName The name of the index to close.
     */
    @Override
    public void closeIndexShortcut(String indexName) {
        getIndexer().indices().close(indexName);
    }

    public Initializers initializers() {
        return initializers;
    }

    public InputRegistry inputs() {
        return inputs;
    }

    public OutputRegistry outputs() {
        return outputs;
    }

    public Periodicals periodicals() {
        return periodicals;
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public boolean isRadio() {
        return false;
    }

    public Lifecycle getLifecycle() {
        return serverStatus.getLifecycle();
    }

    public void setLifecycle(Lifecycle lifecycle) {
        serverStatus.setLifecycle(lifecycle);
    }

}
