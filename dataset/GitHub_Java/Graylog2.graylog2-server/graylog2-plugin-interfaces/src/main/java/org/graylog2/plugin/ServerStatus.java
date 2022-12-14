/**
 * The MIT License
 * Copyright (c) 2012 TORCH GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graylog2.plugin;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import org.graylog2.plugin.lifecycles.Lifecycle;
import org.graylog2.plugin.system.NodeId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
@Singleton
public class ServerStatus {
    private static final Logger LOG = LoggerFactory.getLogger(ServerStatus.class);
    private final EventBus eventBus;

    public enum Capability {
        SERVER,
        RADIO,
        MASTER,
        STATSMODE,
        LOCALMODE
    }

    private final NodeId nodeId;
    private Lifecycle lifecycle;
    private final DateTime startedAt;
    private final Set<Capability> capabilitySet;

    private final AtomicBoolean isProcessing = new AtomicBoolean(true);
    private final AtomicBoolean processingPauseLocked = new AtomicBoolean(false);
    private final CountDownLatch runningLatch = new CountDownLatch(1);

    @Inject
    public ServerStatus(BaseConfiguration configuration, Set<Capability> capabilities, EventBus eventBus) {
        this.eventBus = eventBus;
        this.nodeId = new NodeId(configuration.getNodeIdFile());
        this.startedAt = new DateTime(DateTimeZone.UTC);
        this.capabilitySet = Sets.newHashSet(capabilities); // copy, because we support adding more capabilities later

        setLifecycle(Lifecycle.UNINITIALIZED);
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Lifecycle lifecycle) {
        // special case the two lifecycle states that influence whether processing is enabled or not.
        switch (lifecycle) {
            case RUNNING:
                isProcessing.set(true);
                runningLatch.countDown();
                break;
            case UNINITIALIZED:
            case STARTING:
            case PAUSED:
                isProcessing.set(false);
                break;
        }
        this.lifecycle = lifecycle;
        eventBus.post(this.lifecycle);
    }

    public void awaitRunning(final Runnable runnable) {
        LOG.debug("Waiting for server to enter RUNNING state");
        Uninterruptibles.awaitUninterruptibly(runningLatch);
        LOG.debug("Server entered RUNNING state");

        try {
            LOG.debug("Executing awaitRunning callback");
            runnable.run();
        } catch (Exception e) {
            LOG.error("awaitRunning callback failed", e);
        }
    }

    public DateTime getStartedAt() {
        return startedAt;
    }

    public DateTimeZone getTimezone() {
        return DateTimeZone.getDefault();
    }

    public ServerStatus addCapability(Capability capability) {
        this.capabilitySet.add(capability);
        return this;
    }

    public ServerStatus addCapabilities(Capability... capabilities) {
        this.capabilitySet.addAll(Lists.newArrayList(capabilities));
        return this;
    }

    public boolean hasCapability(Capability capability) {
        return this.capabilitySet.contains(capability);
    }

    public boolean hasCapabilities(Capability... capabilities) {
        return this.capabilitySet.containsAll(Lists.newArrayList(capabilities));
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }

    public void pauseMessageProcessing() {
        pauseMessageProcessing(true);
    }

    public void pauseMessageProcessing(boolean locked) {
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

        setLifecycle(Lifecycle.RUNNING);
    }

    public boolean processingPauseLocked() {
        return processingPauseLocked.get();
    }

    public void unlockProcessingPause() {
        processingPauseLocked.set(false);
    }

    public void setStatsMode(boolean statsMode) {
        if (statsMode)
            addCapability(Capability.STATSMODE);
    }

    public void setLocalMode(boolean localMode) {
        if (localMode)
            addCapability(Capability.LOCALMODE);
    }
}
