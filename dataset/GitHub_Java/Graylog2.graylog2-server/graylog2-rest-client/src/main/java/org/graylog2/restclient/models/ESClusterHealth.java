/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.restclient.models;

import org.graylog2.restclient.lib.ClusterHealthStatus;
import org.graylog2.restclient.models.api.responses.system.ESClusterHealthResponse;

import java.util.Locale;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class ESClusterHealth {

    private final ClusterHealthStatus status;
    private final int relocatingShards;
    private final int unassignedShards;
    private final int activeShards;
    private final int initializingShards;

    public ESClusterHealth(ESClusterHealthResponse r) {
        this.status = ClusterHealthStatus.valueOf(r.status.toUpperCase(Locale.ENGLISH));
        this.activeShards = r.shards.active;
        this.initializingShards = r.shards.initializing;
        this.relocatingShards = r.shards.relocating;
        this.unassignedShards = r.shards.unassigned;
    }

    public ClusterHealthStatus getStatus() {
        return status;
    }

    public boolean isGreen() {
        return this.status == ClusterHealthStatus.GREEN;
    }

    public boolean isYellow() {
        return this.status == ClusterHealthStatus.YELLOW;
    }

    public boolean isRed() {
        return this.status == ClusterHealthStatus.RED;
    }

    public int getRelocatingShards() {
        return relocatingShards;
    }

    public int getUnassignedShards() {
        return unassignedShards;
    }

    public int getActiveShards() {
        return activeShards;
    }

    public int getInitializingShards() {
        return initializingShards;
    }
}
