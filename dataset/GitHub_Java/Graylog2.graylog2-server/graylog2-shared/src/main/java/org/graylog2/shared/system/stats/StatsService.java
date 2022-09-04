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
package org.graylog2.shared.system.stats;

import org.graylog2.shared.system.stats.os.OsProbe;
import org.graylog2.shared.system.stats.os.OsStats;
import org.graylog2.shared.system.stats.process.ProcessProbe;
import org.graylog2.shared.system.stats.process.ProcessStats;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StatsService {
    private final OsProbe osProbe;
    private final ProcessProbe processProbe;

    @Inject
    public StatsService(OsProbe osProbe, ProcessProbe processProbe) {
        this.osProbe = osProbe;
        this.processProbe = processProbe;
    }

    public OsStats osStats() {
        return osProbe.osStats();
    }

    public ProcessStats processStats() {
        return processProbe.processStats();
    }

    public SystemStats systemStats() {
        return SystemStats.create(osStats(), processStats());
    }
}
