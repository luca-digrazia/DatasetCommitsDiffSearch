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
package org.graylog2.periodical;

import com.github.joschi.jadconfig.util.Size;
import com.google.common.eventbus.EventBus;
import org.graylog2.plugin.ThrottleState;
import org.graylog2.plugin.periodical.Periodical;
import org.graylog2.shared.buffers.ProcessBuffer;
import org.graylog2.shared.journal.Journal;
import org.graylog2.shared.journal.KafkaJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * The ThrottleStateUpdater publishes the current state buffer state of the journal to other interested parties,
 * chiefly the ThrottleableTransports.
 * <p/>
 * <p>
 * It only includes the necessary information to make a decision about whether to throttle parts of the system,
 * but does not send "throttle" commands. This allows for a flexible approach in picking a throttling strategy.
 * </p>
 * <p>
 * The implementation expects to be called once per second to have a rough estimate about the events per second,
 * over the last second.
 * </p>
 */
public class ThrottleStateUpdaterThread extends Periodical {
    private static final Logger log = LoggerFactory.getLogger(ThrottleStateUpdaterThread.class);
    private final KafkaJournal journal;
    private final ProcessBuffer processBuffer;
    private final EventBus eventBus;
    private final Size retentionSize;

    private boolean firstRun = true;

    private long logStartOffset;
    private long logEndOffset;
    private long previousLogEndOffset;
    private long previousReadOffset;
    private long currentReadOffset;
    private long currentTs;
    private long prevTs;

    @Inject
    public ThrottleStateUpdaterThread(Journal journal,
                                      ProcessBuffer processBuffer,
                                      EventBus eventBus,
                                      @Named("message_journal_max_size") Size retentionSize) {
        this.processBuffer = processBuffer;
        this.eventBus = eventBus;
        this.retentionSize = retentionSize;
        // leave this.journal null, we'll say "don't start" in that case, see startOnThisNode() below.
        if (journal instanceof KafkaJournal) {
            this.journal = (KafkaJournal) journal;
        } else {
            this.journal = null;
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
        return false;
    }

    @Override
    public boolean startOnThisNode() {
        // don't start if we don't have the KafkaJournal
        return journal != null;
    }

    @Override
    public boolean isDaemon() {
        return true;
    }

    @Override
    public int getInitialDelaySeconds() {
        return 1;
    }

    @Override
    public int getPeriodSeconds() {
        return 1;
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

    @Override
    public void doRun() {
        final ThrottleState throttleState = new ThrottleState();
        final long committedOffset = journal.getCommittedOffset();

        prevTs = currentTs;
        currentTs = System.nanoTime();

        previousLogEndOffset = logEndOffset;
        previousReadOffset = currentReadOffset;
        logStartOffset = journal.getLogStartOffset();
        logEndOffset = journal.getLogEndOffset() - 1; // -1 because getLogEndOffset is the next offset that gets assigned
        currentReadOffset = journal.getNextReadOffset() - 1; // just to make it clear which field we read

        // for the first run, don't send an update, there's no previous data available to calc rates
        if (firstRun) {
            firstRun = false;
            return;
        }

        throttleState.appendEventsPerSec = (long) Math.floor((logEndOffset - previousLogEndOffset) / ((currentTs - prevTs) / 1.0E09));
        throttleState.readEventsPerSec = (long) Math.floor((currentReadOffset - previousReadOffset) / ((currentTs - prevTs) / 1.0E09));

        throttleState.journalSize = journal.size();
        throttleState.journalSizeLimit = retentionSize.toBytes();

        throttleState.processBufferCapacity = processBuffer.getRemainingCapacity();

        if (committedOffset == KafkaJournal.DEFAULT_COMMITTED_OFFSET) {
            // nothing committed at all, the entire log is uncommitted, or completely empty.
            throttleState.uncommittedJournalEntries = journal.size() == 0 ? 0 : logEndOffset - logStartOffset;
        } else {
            throttleState.uncommittedJournalEntries = logEndOffset - committedOffset;
        }
        log.debug("ThrottleState: {}", throttleState);
        
        // the journal needs this to provide information to rest clients
        journal.setThrottleState(throttleState);
        
        // publish to interested parties
        eventBus.post(throttleState);

    }
}
