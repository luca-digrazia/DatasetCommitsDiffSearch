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
package org.graylog2.buffers.processors;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import com.lmax.disruptor.WorkHandler;
import org.graylog2.Configuration;
import org.graylog2.outputs.CachedOutputRouter;
import org.graylog2.outputs.DefaultMessageOutput;
import org.graylog2.outputs.OutputRouter;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.buffers.MessageEvent;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.shared.stats.ThroughputStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

public class OutputBufferProcessor implements WorkHandler<MessageEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(OutputBufferProcessor.class);

    private final ExecutorService executor;

    private final Configuration configuration;
    private final ThroughputStats throughputStats;
    private final ServerStatus serverStatus;

    //private List<Message> buffer = Lists.newArrayList();

    private final Meter incomingMessages;
    private final Histogram batchSize;
    private final Timer processTime;

    private final OutputRouter outputRouter;
    private final MessageOutput defaultMessageOutput;

    @Inject
    public OutputBufferProcessor(Configuration configuration,
                                 MetricRegistry metricRegistry,
                                 ThroughputStats throughputStats,
                                 ServerStatus serverStatus,
                                 CachedOutputRouter outputRouter,
                                 @DefaultMessageOutput MessageOutput defaultMessageOutput) {
        this.configuration = configuration;
        this.throughputStats = throughputStats;
        this.serverStatus = serverStatus;
        this.outputRouter = outputRouter;
        this.defaultMessageOutput = defaultMessageOutput;

        final String nameFormat = "outputbuffer-processor-executor-%d";
        final int corePoolSize = configuration.getOutputBufferProcessorThreadsCorePoolSize();
        final int maxPoolSize = configuration.getOutputBufferProcessorThreadsMaxPoolSize();
        final int keepAliveTime = configuration.getOutputBufferProcessorKeepAliveTime();
        this.executor = executorService(metricRegistry, nameFormat, corePoolSize, maxPoolSize, keepAliveTime);

        this.incomingMessages = metricRegistry.meter(name(OutputBufferProcessor.class, "incomingMessages"));
        this.batchSize = metricRegistry.histogram(name(OutputBufferProcessor.class, "batchSize"));
        this.processTime = metricRegistry.timer(name(OutputBufferProcessor.class, "processTime"));
    }

    private ExecutorService executorService(final MetricRegistry metricRegistry, final String nameFormat,
                                            final int corePoolSize, final int maxPoolSize, final int keepAliveTime) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(nameFormat).build();
        return new InstrumentedExecutorService(
                new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<Runnable>(), threadFactory),
                metricRegistry,
                name(this.getClass(), "executor-service"));
    }

    /**
     * Each message will be written to one or more outputs.
     * <p>
     * The default output is always being used for every message, but optionally the message can be routed to additional
     * outputs, currently based on the stream outputs that are configured in the system.
     * </p>
     * <p>
     * The stream outputs are time limited so one bad output does not impact throughput too much. Essentially this means
     * that the work of writing to the outputs is performed, but the writer threads will not wait forever for stream
     * outputs to finish their work. <b>This might lead to increased memory usage!</b>
     * </p>
     * <p>
     * The default output, however, is allowed to block and is not subject to time limiting. This is important because it
     * can exert back pressure on the processing pipeline this way, making sure we don't run into excessive heap usage.
     * </p>
     * @param event the message to write to outputs
     * @throws Exception
     */
    @Override
    public void onEvent(MessageEvent event) throws Exception {
        incomingMessages.mark();

        final Message msg = event.getMessage();
        if (msg == null) {
            LOG.debug("Skipping null message.");
            return;
        }
        LOG.debug("Processing message <{}> from OutputBuffer.", msg.getId());

        final Set<MessageOutput> messageOutputs = outputRouter.getOutputsForMessage(msg);
        msg.recordCounter(serverStatus, "matched-outputs", messageOutputs.size());

        // minus one, because the default output does not count against the time limited outputs, and is always included
        final CountDownLatch streamOutputsDoneSignal = new CountDownLatch(messageOutputs.size() - 1);

        Future<?> defaultOutputCompletion = null;
        for (final MessageOutput output : messageOutputs) {
            if (output == null) {
                LOG.error("Got null output!");
                continue;
            }
            if (!output.isRunning()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping stopped output {}", output.getClass().getName());
                }
                continue;
            }

            final boolean isDefaultOutput = defaultMessageOutput.equals(output);

            try {
                LOG.debug("Writing message to [{}].", output.getClass());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Message id for [{}]: <{}>", output.getClass(), msg.getId());
                }
                final Future<?> future = executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try (Timer.Context ignored = processTime.time()) {
                            output.write(msg);
                        } catch (Exception e) {
                            LOG.error("Error in output [" + output.getClass() + "].", e);
                        } finally {
                            // do not touch the latch if this is the default output!
                            // we use the returned future to block on its completion.
                            if (!isDefaultOutput) {
                                streamOutputsDoneSignal.countDown();
                            }
                        }
                    }
                });
                if (isDefaultOutput) {
                    // save the future so we can wait for its completion below, this implements the blocking behavior
                    defaultOutputCompletion = future;
                }

            } catch (Exception e) {
                LOG.error("Could not write message batch to output [" + output.getClass() + "].", e);
                streamOutputsDoneSignal.countDown();
            }
        }

        // Wait until all writer threads for stream outputs have finished or timeout is reached.
        if (!streamOutputsDoneSignal.await(configuration.getOutputModuleTimeout(), TimeUnit.MILLISECONDS)) {
            LOG.warn("Timeout reached. Not waiting any longer for stream output writer threads to complete.");
        }

        // now block until the default output has finished. most batching outputs will already been done because their
        // fast path is really fast (usually an insert into a queue), but the slow flush path might block for a long time
        // this exerts the back pressure to the system
        if (defaultOutputCompletion != null) {
            Uninterruptibles.getUninterruptibly(defaultOutputCompletion);
        } else {
            LOG.error("The default output future was null, this is a bug!");
        }

        if (msg.hasRecordings()) {
            LOG.debug("Message event trace: {}", msg.recordingsAsString());
        }
        if (serverStatus.hasCapability(ServerStatus.Capability.STATSMODE)) {
            throughputStats.getBenchmarkCounter().increment();
        }

        throughputStats.getThroughputCounter().increment();

        LOG.debug("Wrote message <{}> to all outputs. Finished handling.", msg.getId());
    }
}
