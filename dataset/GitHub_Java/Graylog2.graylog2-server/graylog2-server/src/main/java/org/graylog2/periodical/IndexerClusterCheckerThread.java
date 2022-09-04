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
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.graylog2.notifications.Notification;
import org.graylog2.notifications.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class IndexerClusterCheckerThread extends Periodical {

    private static final Logger LOG = LoggerFactory.getLogger(IndexerClusterCheckerThread.class);

    public static final int MINIMUM_OPEN_FILES_LIMIT = 64000;

    private NotificationService notificationService;

    @Inject
    public IndexerClusterCheckerThread(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void run() {
        if (!notificationService.isFirst(Notification.Type.ES_OPEN_FILES))
            return;
        boolean allHigher = true;
        for (NodeInfo node : core.getIndexer().cluster().getDataNodes()) {
            // Check number of maximum open files.
            if (node.getProcess().getMaxFileDescriptors() < MINIMUM_OPEN_FILES_LIMIT) {

                // Write notification.
                 Notification notification = notificationService.buildNow()
                        .addType(Notification.Type.ES_OPEN_FILES)
                        .addSeverity(Notification.Severity.URGENT);
                final boolean published = notificationService.publishIfFirst(notification);
                if (published) {
                    LOG.warn("Indexer node <{}> open file limit is too low: [{}]. Set it to at least {}.",
                             new Object[] {
                                     node.getNode().getName(),
                                     node.getProcess().getMaxFileDescriptors(),
                                     MINIMUM_OPEN_FILES_LIMIT
                             });
                }
                allHigher = false;
            }
        }
        if (allHigher) {
            Notification notification = notificationService.build().addType(Notification.Type.ES_OPEN_FILES);
            notificationService.fixed(notification);
        }
    }

    @Override
    public boolean runsForever() {
        return false;
    }

    @Override
    public boolean stopOnGracefulShutdown() {
        return true;
    }

    @Override
    public boolean masterOnly() {
        return true;
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
        return 30;
    }
}
