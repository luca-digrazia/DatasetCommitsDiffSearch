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
package org.graylog2.periodical;

import com.google.inject.Inject;
import org.graylog2.Configuration;
import org.graylog2.cluster.Node;
import org.graylog2.cluster.NodeNotFoundException;
import org.graylog2.cluster.NodeService;
import org.graylog2.notifications.Notification;
import org.graylog2.notifications.NotificationImpl;
import org.graylog2.notifications.NotificationService;
import org.graylog2.system.activities.Activity;
import org.graylog2.system.activities.ActivityWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class NodePingThread extends Periodical {

    private static final Logger LOG = LoggerFactory.getLogger(NodePingThread.class);
    private final NodeService nodeService;
    private final NotificationService notificationService;
    private final Configuration configuration;

    @Inject
    public NodePingThread(NodeService nodeService, NotificationService notificationService, Configuration configuration) {
        this.nodeService = nodeService;
        this.notificationService = notificationService;
        this.configuration = configuration;
    }

    @Override
    public void run() {
        try {
            Node node = nodeService.thisNode(core);
            nodeService.markAsAlive(node, core.isMaster(), configuration.getRestTransportUri());
        } catch (NodeNotFoundException e) {
            LOG.warn("Did not find meta info of this node. Re-registering.");
            nodeService.registerServer(core, core.isMaster(), configuration.getRestTransportUri());
        }
        try {
            // Remove old nodes that are no longer running. (Just some housekeeping)
            nodeService.dropOutdated();

            final ActivityWriter activityWriter = core.getActivityWriter();
            // Check that we still have a master node in the cluster, if not, warn the user.
            if (nodeService.isAnyMasterPresent()) {
                Notification notification = notificationService.build()
                        .addType(Notification.Type.NO_MASTER);
                boolean removedNotification = notificationService.fixed(notification);
                if (removedNotification) {
                    activityWriter.write(
                        new Activity("Notification condition [" + NotificationImpl.Type.NO_MASTER + "] " +
                                             "has been fixed.", NodePingThread.class));
                }
            } else {
                Notification notification = notificationService.buildNow()
                        .addThisNode(core)
                        .addType(Notification.Type.NO_MASTER)
                        .addSeverity(Notification.Severity.URGENT);
                notificationService.publishIfFirst(notification);
                activityWriter.write(
                        new Activity(
                                "No graylog2 master node available. Check the configuration for is_master=true " +
                                        "on at least one node.",
                                NodePingThread.class));
            }

        } catch (Exception e) {
            LOG.warn("Caught exception during node ping.", e);
        }
    }

    @Override
    public boolean runsForever() {
        return false;
    }

    @Override
    public boolean stopOnGracefulShutdown() {
        return false;
    }

    @Override
    public boolean masterOnly() {
        return false;
    }

    @Override
    public boolean startOnThisNode() {
        return true;
    }

    @Override
    public boolean isDaemon() {
        return true;
    }

    @Override
    public int getInitialDelaySeconds() {
        return 0;
    }

    @Override
    public int getPeriodSeconds() {
        return 1;
    }
}
