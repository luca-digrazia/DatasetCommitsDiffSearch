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
package org.graylog2.shared.journal;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import kafka.common.KafkaException;
import kafka.common.OffsetOutOfRangeException;
import kafka.common.TopicAndPartition;
import kafka.log.CleanerConfig;
import kafka.log.Log;
import kafka.log.LogConfig;
import kafka.log.LogManager;
import kafka.message.ByteBufferMessageSet;
import kafka.message.Message;
import kafka.message.MessageAndOffset;
import kafka.message.MessageSet;
import kafka.utils.KafkaScheduler;
import kafka.utils.SystemTime$;
import kafka.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.Map$;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.graylog2.plugin.Tools.bytesToHex;

public class KafkaJournal extends AbstractIdleService implements Journal {
    private static final Logger log = LoggerFactory.getLogger(KafkaJournal.class);
    private final LogManager logManager;
    private final Log kafkaLog;
    private final File commmitedReadOffsetFile;
    private final AtomicLong committedOffset = new AtomicLong(Long.MIN_VALUE);
    private final ScheduledExecutorService scheduler;
    private final OffsetFileFlusher offsetFlusher;
    private long nextReadOffset = 0L;
    private final KafkaScheduler kafkaScheduler;

    @Inject
    public KafkaJournal(@Named("journalDirectory") String journalDirName, @Named("scheduler") ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;

        // TODO all of these configuration values need tweaking
        // these are the default values as per kafka 0.8.1.1
        final LogConfig defaultConfig =
                new LogConfig(
                        1024 * 1024,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Integer.MAX_VALUE,
                        1024 * 1024,
                        4096,
                        60 * 1000,
                        24 * 60 * 60 * 1000L,
                        0.5,
                        false
                );
        // these are the default values as per kafka 0.8.1.1, except we don't turn on the cleaner
        // Cleaner really is log compaction with respect to "deletes" in the log.
        // we never insert a message twice, at least not on purpose, so we do not "clean" logs, ever.
        final CleanerConfig cleanerConfig =
                new CleanerConfig(
                        1,
                        4 * 1024 * 1024L,
                        0.9d,
                        1024 * 1024,
                        32 * 1024 * 1024,
                        5 * 1024 * 1024L,
                        TimeUnit.SECONDS.toMillis(15),
                        false,
                        "MD5");
        final File journalDirectory = new File(journalDirName);
        if (!journalDirectory.exists() && !journalDirectory.mkdirs()) {
            log.error("Cannot create journal directory at {}, please check the permissions", journalDirectory.getAbsolutePath());
        }
        // TODO add check for directory, etc
        commmitedReadOffsetFile = new File(journalDirectory, "graylog2-committed-read-offset");
        try {
            if (!commmitedReadOffsetFile.createNewFile()) {
                final String line = Files.readFirstLine(commmitedReadOffsetFile, Charsets.UTF_8);
                // the file contains the last offset graylog2 has successfully processed.
                // thus the nextReadOffset is one beyond that number
                if (line != null) {
                    committedOffset.set(Long.parseLong(line.trim()));
                    nextReadOffset = committedOffset.get() + 1;
                }
            }
        } catch (IOException e) {
            log.error("Cannot access offset file", e);
            Throwables.propagate(e);
        }
        try {
            kafkaScheduler = new KafkaScheduler(2, "kafka-journal-scheduler-", false);
            kafkaScheduler.startup();
            logManager = new LogManager(
                    new File[]{journalDirectory},
                    Map$.MODULE$.<String, LogConfig>empty(),
                    defaultConfig,
                    cleanerConfig,
                    TimeUnit.SECONDS.toMillis(60),
                    TimeUnit.SECONDS.toMillis(60),
                    TimeUnit.SECONDS.toMillis(60),
                    kafkaScheduler, // TODO use our own scheduler here?
                    SystemTime$.MODULE$);

            final TopicAndPartition topicAndPartition = new TopicAndPartition("messagejournal", 0);
            final Option<Log> messageLog = logManager.getLog(topicAndPartition);
            if (messageLog.isEmpty()) {
                kafkaLog = logManager.createLog(topicAndPartition, logManager.defaultConfig());
            } else {
                kafkaLog = messageLog.get();
            }
            log.info("Initialized Kafka based journal at {}", journalDirName);
            offsetFlusher = new OffsetFileFlusher();
        } catch (KafkaException e) {
            // most likely failed to grab lock
            log.error("Unable to start logmanager.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an opaque object which can be passed to {@link #write(java.util.List)} for a bulk journal write.
     *
     * @param idBytes      a byte array which represents the key for the entry
     * @param messageBytes the journal entry's payload, i.e. the message itself
     * @return a journal entry to be passed to {@link #write(java.util.List)}
     */
    @Override
    public Entry createEntry(byte[] idBytes, byte[] messageBytes) {
        return new Entry(idBytes, messageBytes);
    }

    /**
     * Writes the list of entries to the journal.
     * @param entries journal entries to be written
     * @return the last position written to in the journal
     */
    @Override
    public long write(List<Entry> entries) {
        final long[] payloadSize = {0L};

        final List<Message> messages = Lists.newArrayList();
        for (final Entry entry : entries) {
            final byte[] messageBytes = entry.getMessageBytes();
            final byte[] idBytes = entry.getIdBytes();

            payloadSize[0] += messageBytes.length;
            messages.add(new Message(messageBytes, idBytes));

            if (log.isInfoEnabled()) {
                log.info("Message {} contains bytes {}", bytesToHex(idBytes), bytesToHex(messageBytes));
            }
        }

        final ByteBufferMessageSet messageSet = new ByteBufferMessageSet(JavaConversions.asScalaBuffer(messages));

        final Log.LogAppendInfo appendInfo = kafkaLog.append(messageSet, true);
        log.info("Wrote {} messages to journal: {} bytes, log position {} to {}",
                 entries.size(), payloadSize[0], appendInfo.firstOffset(), appendInfo.lastOffset());
        return appendInfo.lastOffset();
    }

    /**
     * Writes a single message to the journal and returns the new write position
     * @param idBytes byte array congaing the message id
     * @param messageBytes encoded message payload
     * @return the last position written to in the journal
     */
    @Override
    public long write(byte[] idBytes, byte[] messageBytes) {
        final Entry journalEntry = createEntry(idBytes, messageBytes);
        return write(Collections.singletonList(journalEntry));
    }

    @Override
    public List<JournalReadEntry> read() { // TODO this currently only reads 0 or 1 messages, use some decent limit instead
        final long maxOffset = nextReadOffset + 1;
        final List<JournalReadEntry> messages = Lists.newArrayListWithCapacity((int) (maxOffset - nextReadOffset));
        try {
            final MessageSet messageSet = kafkaLog.read(nextReadOffset, 10 * 1024, Option.<Object>apply(maxOffset));

            final Iterator<MessageAndOffset> iterator = messageSet.iterator();
            while (iterator.hasNext()) {
                final MessageAndOffset messageAndOffset = iterator.next();

                final byte[] payloadBytes = Utils.readBytes(messageAndOffset.message().payload());
                if (log.isTraceEnabled()) {
                    final byte[] keyBytes = Utils.readBytes(messageAndOffset.message().key());
                    log.trace("Read message {} contains {}", bytesToHex(keyBytes), bytesToHex(payloadBytes));
                }
                messages.add(new JournalReadEntry(payloadBytes, messageAndOffset.offset()));
                nextReadOffset = messageAndOffset.nextOffset();
            }

        } catch (OffsetOutOfRangeException e) {
            log.warn("Offset out of range, no messages available starting at offset {}", nextReadOffset);
        }
        return messages;
    }

    /**
     * Upon fully processing, and persistently storing, a batch of messages, the system should mark the message with the
     * highest offset as committed. A background job will write the last position to disk periodically.
     * @param offset the offset of the latest committed message
     */
    @Override
    public void markJournalOffsetCommitted(long offset) {   // TODO do we need to handle out of order processing of messages here?
        long prev;
        // the caller will ideally already make sure it only calls this for the maximum value it processed,
        // but let's try to be safe here.
        int i = 0;
        do {
            prev = committedOffset.get();
            // at least warn if this spins often, that would be a sign of very high contention, which should not happen
            if (++i % 10 == 0) {
                log.warn("Committing journal offset spins {} times now, this might be a bug. Continuing to try update.", i);
            }
        } while (!committedOffset.compareAndSet(prev, Math.max(offset, prev)));
    }

    @Override
    protected void startUp() throws Exception {
        // start the background threads
        logManager.startup();
        // regularly write the currently committed read offset to disk
        scheduler.scheduleAtFixedRate(offsetFlusher, 1, 1, TimeUnit.SECONDS); // TODO make configurable
    }

    @Override
    protected void shutDown() throws Exception {
        kafkaScheduler.shutdown();
        logManager.shutdown();
        // final flush
        offsetFlusher.run();
    }

    private class OffsetFileFlusher implements Runnable {
        @Override
        public void run() {
            try (final FileOutputStream fos = new FileOutputStream(commmitedReadOffsetFile)) {
                fos.write(String.valueOf(committedOffset.get()).getBytes(Charsets.UTF_8));
                // flush stream
                fos.flush();
                // actually sync to disk
                fos.getFD().sync();
            } catch (SyncFailedException e) {
                log.error("Cannot sync "+commmitedReadOffsetFile.getAbsolutePath()+" to disk. Continuing anyway," +
                                  " but there is no guarantee that the file has been written.", e);
            } catch (IOException e) {
                log.error("Cannot write "+commmitedReadOffsetFile.getAbsolutePath()+" to disk.", e);
            }
        }
    }
}