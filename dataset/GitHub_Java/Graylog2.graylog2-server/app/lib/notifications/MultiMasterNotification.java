/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
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
 *
 */
package lib.notifications;

import com.google.common.collect.Maps;
import models.SystemJob;

import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class MultiMasterNotification implements NotificationType {

    private static final String TITLE = "Multiple graylog2-server masters in the cluster";
    private static final String DESCRIPTION = "There were multiple graylog2-server instances configured as master " +
            "in your Graylog2 cluster. The cluster handles this automatically by launching new nodes as slaves if " +
            "there already is a master but you should still fix this. Check the graylog2.conf of every node and " +
            "make sure that only one instance has is_master set to true. Close this notification if you think you " +
            "resolved the problem. It will pop back up if you start a second master node again.";

    @Override
    public Map<SystemJob.Type, String> options() {
        return Maps.newHashMap();
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

}
