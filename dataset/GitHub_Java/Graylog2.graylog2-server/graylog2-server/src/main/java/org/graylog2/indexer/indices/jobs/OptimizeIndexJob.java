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
package org.graylog2.indexer.indices.jobs;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.elasticsearch.action.admin.indices.optimize.OptimizeRequest;
import org.graylog2.indexer.Deflector;
import org.graylog2.indexer.Indexer;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.system.activities.Activity;
import org.graylog2.system.activities.ActivityWriter;
import org.graylog2.system.jobs.SystemJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class OptimizeIndexJob extends SystemJob {
    public interface Factory {
        OptimizeIndexJob create(Deflector deflector, String index);
    }

    private static final Logger LOG = LoggerFactory.getLogger(OptimizeIndexJob.class);

    public static final int MAX_CONCURRENCY = 1000;

    private final ActivityWriter activityWriter;
    private final String index;
    private final Deflector deflector;
    private final Indexer indexer;

    @AssistedInject
    public OptimizeIndexJob(@Assisted Deflector deflector,
                            ServerStatus serverStatus,
                            Indexer indexer,
                            ActivityWriter activityWriter,
                            @Assisted String index) {
        super(serverStatus);
        this.deflector = deflector;
        this.indexer = indexer;
        this.activityWriter = activityWriter;
        this.index = index;
    }

    @Override
    public void execute() {
        String msg = "Optimizing index <" + index + ">.";
        activityWriter.write(new Activity(msg, OptimizeIndexJob.class));
        LOG.info(msg);

        // http://www.elasticsearch.org/guide/reference/api/admin-indices-optimize/
        OptimizeRequest or = new OptimizeRequest(index);

        or.maxNumSegments(1);
        or.onlyExpungeDeletes(false);
        or.flush(true);
        or.waitForMerge(true); // This makes us block until the operation finished.

        indexer.getClient().admin().indices().optimize(or).actionGet();
    }

    @Override
    public void requestCancel() {
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public int maxConcurrency() {
        return MAX_CONCURRENCY;
    }

    @Override
    public boolean providesProgress() {
        return false;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Optimises and index for read performance.";
    }

    @Override
    public String getClassName() {
        return this.getClass().getCanonicalName();
    }

}
