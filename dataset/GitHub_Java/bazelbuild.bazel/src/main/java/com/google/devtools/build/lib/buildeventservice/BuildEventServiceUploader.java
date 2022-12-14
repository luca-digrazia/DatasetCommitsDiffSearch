// Copyright 2019 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.buildeventservice;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static com.google.devtools.build.v1.BuildStatus.Result.COMMAND_FAILED;
import static com.google.devtools.build.v1.BuildStatus.Result.COMMAND_SUCCEEDED;
import static com.google.devtools.build.v1.BuildStatus.Result.UNKNOWN_STATUS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceTransport.BuildEventLogger;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceTransport.ExitFunction;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.AckReceivedCommand;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.EventLoopCommand;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.OpenStreamCommand;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.SendBuildEventCommand;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.SendLastBuildEventCommand;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.SendRegularBuildEventCommand;
import com.google.devtools.build.lib.buildeventservice.BuildEventServiceUploaderCommands.StreamCompleteCommand;
import com.google.devtools.build.lib.buildeventservice.client.BuildEventServiceClient;
import com.google.devtools.build.lib.buildeventservice.client.BuildEventServiceClient.StreamContext;
import com.google.devtools.build.lib.buildeventstream.ArtifactGroupNamer;
import com.google.devtools.build.lib.buildeventstream.BuildCompletingEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEvent.LocalFile;
import com.google.devtools.build.lib.buildeventstream.BuildEventArtifactUploader;
import com.google.devtools.build.lib.buildeventstream.BuildEventContext;
import com.google.devtools.build.lib.buildeventstream.BuildEventProtocolOptions;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.PathConverter;
import com.google.devtools.build.lib.clock.Clock;
import com.google.devtools.build.lib.util.ExitCode;
import com.google.devtools.build.lib.util.LoggingUtil;
import com.google.devtools.build.lib.util.Sleeper;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.v1.BuildStatus.Result;
import com.google.devtools.build.v1.PublishBuildToolEventStreamRequest;
import com.google.devtools.build.v1.PublishLifecycleEventRequest;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;

/**
 * Uploader of Build Events to the Build Event Service (BES).
 *
 * <p>The purpose is of this class is to manage the interaction between the BES client and the BES
 * server. It implements the event loop pattern based on the commands defined by {@link
 * BuildEventServiceUploaderCommands}.
 */
// TODO(lpino): This class should be package-private but there are unit tests that are in the
//  different packages and rely on this.
@VisibleForTesting
public final class BuildEventServiceUploader implements Runnable {
  private static final Logger logger = Logger.getLogger(BuildEventServiceUploader.class.getName());

  /** Configuration knobs related to RPC retries. Values chosen by good judgement. */
  private static final int MAX_NUM_RETRIES = 4;

  private static final int DELAY_MILLIS = 1000;

  private final BuildEventServiceClient besClient;
  private final BuildEventArtifactUploader localFileUploader;
  private final BuildEventServiceProtoUtil besProtoUtil;
  private final BuildEventProtocolOptions buildEventProtocolOptions;
  private final boolean publishLifecycleEvents;
  private final Duration closeTimeout;
  private final ExitFunction exitFunc;
  private final Sleeper sleeper;
  private final Clock clock;
  private final BuildEventLogger buildEventLogger;
  private final ArtifactGroupNamer namer;

  /**
   * The event queue contains two types of events: - Build events, sorted by sequence number, that
   * should be sent to the server - Command events that are used by {@link #publishBuildEvents()} to
   * change state.
   */
  private final BlockingDeque<EventLoopCommand> eventQueue = new LinkedBlockingDeque<>();

  /**
   * Computes sequence numbers for build events. As per the BES protocol, sequence numbers must be
   * consecutive monotonically increasing natural numbers.
   */
  private final AtomicLong nextSeqNum = new AtomicLong(1);

  private final Object lock = new Object();

  @GuardedBy("lock")
  private Result buildStatus = UNKNOWN_STATUS;

  /**
   * Initialized only after the first call to {@link #close()} or if the upload fails before that.
   * The {@code null} state is used throughout the code to make multiple calls to {@link #close()}
   * idempotent.
   */
  @GuardedBy("lock")
  private SettableFuture<Void> closeFuture;

  /**
   * The thread that calls the lifecycle RPCs and does the build event upload. It's started lazily
   * on the first call to {@link #enqueueEvent(BuildEvent)} or {@link #close()} (which ever comes
   * first).
   */
  @GuardedBy("lock")
  private Thread uploadThread;

  @GuardedBy("lock")
  private boolean interruptCausedByTimeout;

  private StreamContext streamContext;

  BuildEventServiceUploader(
      BuildEventServiceClient besClient,
      BuildEventArtifactUploader localFileUploader,
      BuildEventServiceProtoUtil besProtoUtil,
      BuildEventProtocolOptions buildEventProtocolOptions,
      boolean publishLifecycleEvents,
      Duration closeTimeout,
      ExitFunction exitFunc,
      Sleeper sleeper,
      Clock clock,
      BuildEventLogger buildEventLogger,
      ArtifactGroupNamer namer) {
    this.besClient = Preconditions.checkNotNull(besClient);
    this.localFileUploader = Preconditions.checkNotNull(localFileUploader);
    this.besProtoUtil = Preconditions.checkNotNull(besProtoUtil);
    this.buildEventProtocolOptions = buildEventProtocolOptions;
    this.publishLifecycleEvents = publishLifecycleEvents;
    this.closeTimeout = Preconditions.checkNotNull(closeTimeout);
    this.exitFunc = Preconditions.checkNotNull(exitFunc);
    this.sleeper = Preconditions.checkNotNull(sleeper);
    this.clock = Preconditions.checkNotNull(clock);
    this.buildEventLogger = Preconditions.checkNotNull(buildEventLogger);
    this.namer = namer;
  }

  BuildEventArtifactUploader getLocalFileUploader() {
    return localFileUploader;
  }

  /** Enqueues an event for uploading to a BES backend. */
  void enqueueEvent(BuildEvent event) {
    // This needs to happen outside a synchronized block as it may trigger
    // stdout/stderr and lead to a deadlock. See b/109725432
    ListenableFuture<PathConverter> localFileUploadFuture =
        uploadReferencedLocalFiles(event.referencedLocalFiles());

    synchronized (lock) {
      if (closeFuture != null) {
        // Close has been called and thus we silently ignore any further events and cancel
        // any pending file uploads
        closeFuture.addListener(
            () -> {
              if (!localFileUploadFuture.isDone()) {
                localFileUploadFuture.cancel(true);
              }
            },
            MoreExecutors.directExecutor());
        return;
      }
      // BuildCompletingEvent marks the end of the build in the BEP event stream.
      if (event instanceof BuildCompletingEvent) {
        this.buildStatus = extractBuildStatus((BuildCompletingEvent) event);
      }
      ensureUploadThreadStarted();
      eventQueue.addLast(
          new SendRegularBuildEventCommand(
              event,
              localFileUploadFuture,
              nextSeqNum.getAndIncrement(),
              Timestamps.fromMillis(clock.currentTimeMillis())));
    }
  }

  /**
   * Gracefully stops the BES upload. All events enqueued before the call to close will be uploaded
   * and events enqueued after the call will be discarded.
   *
   * <p>The returned future completes when the upload completes. It's guaranteed to never fail.
   */
  public ListenableFuture<Void> close() {
    synchronized (lock) {
      if (closeFuture != null) {
        return closeFuture;
      }
      ensureUploadThreadStarted();

      closeFuture = SettableFuture.create();

      // Enqueue the last event which will terminate the upload.
      eventQueue.addLast(
          new SendLastBuildEventCommand(nextSeqNum.getAndIncrement(), currentTime()));

      if (!closeTimeout.isZero()) {
        startCloseTimer(closeFuture, closeTimeout);
      }
      return closeFuture;
    }
  }

  /** Stops the upload immediately. Enqueued events that have not been sent yet will be lost. */
  private void closeOnTimeout() {
    synchronized (lock) {
      if (uploadThread != null) {
        if (uploadThread.isInterrupted()) {
          return;
        }

        interruptCausedByTimeout = true;
        uploadThread.interrupt();
      }
    }
  }

  @Override
  public void run() {
    try {
      if (publishLifecycleEvents) {
        publishLifecycleEvent(besProtoUtil.buildEnqueued(currentTime()));
        publishLifecycleEvent(besProtoUtil.invocationStarted(currentTime()));
      }

      try {
        publishBuildEvents();
      } finally {
        if (publishLifecycleEvents) {
          Result buildStatus;
          synchronized (lock) {
            buildStatus = this.buildStatus;
          }
          publishLifecycleEvent(besProtoUtil.invocationFinished(currentTime(), buildStatus));
          publishLifecycleEvent(besProtoUtil.buildFinished(currentTime(), buildStatus));
        }
      }
      exitFunc.accept(
          "The Build Event Protocol upload finished successfully",
          /*cause=*/ null,
          ExitCode.SUCCESS);
      synchronized (lock) {
        // Invariant: closeFuture is not null.
        // publishBuildEvents() only terminates successfully after SendLastBuildEventCommand
        // has been sent successfully and that event is only added to the eventQueue during a
        // call to close() which initializes the closeFuture.
        closeFuture.set(null);
      }
    } catch (InterruptedException e) {
      try {
        logger.info("Aborting the BES upload due to having received an interrupt");
        synchronized (lock) {
          Preconditions.checkState(
              interruptCausedByTimeout, "Unexpected interrupt on BES uploader thread");
          exitFunc.accept(
              "The Build Event Protocol upload timed out",
              e,
              ExitCode.TRANSIENT_BUILD_EVENT_SERVICE_UPLOAD_ERROR);
        }
      } finally {
        // TODO(buchgr): Due to b/113035235 exitFunc needs to be called before the close future
        // completes.
        failCloseFuture(e);
      }
    } catch (StatusException e) {
      try {
        String message =
            "The Build Event Protocol upload failed: " + besClient.userReadableError(e);
        logger.info(message);
        ExitCode code =
            shouldRetryStatus(e.getStatus())
                ? ExitCode.TRANSIENT_BUILD_EVENT_SERVICE_UPLOAD_ERROR
                : ExitCode.PERSISTENT_BUILD_EVENT_SERVICE_UPLOAD_ERROR;
        exitFunc.accept(message, e, code);
      } finally {
        failCloseFuture(e);
      }
    } catch (LocalFileUploadException e) {
      Throwables.throwIfUnchecked(e.getCause());
      try {
        String message =
            "The Build Event Protocol local file upload failed: " + e.getCause().getMessage();
        logger.info(message);
        exitFunc.accept(message, e.getCause(), ExitCode.TRANSIENT_BUILD_EVENT_SERVICE_UPLOAD_ERROR);
      } finally {
        failCloseFuture(e.getCause());
      }
    } catch (Throwable e) {
      failCloseFuture(e);
      logger.severe("BES upload failed due to a RuntimeException / Error. This is a bug.");
      throw e;
    } finally {
      localFileUploader.shutdown();
    }
  }

  private BuildEventStreamProtos.BuildEvent createSerializedRegularBuildEvent(
      PathConverter pathConverter,
      SendRegularBuildEventCommand buildEvent) {
    BuildEventContext ctx =
        new BuildEventContext() {
          @Override
          public PathConverter pathConverter() {
            return pathConverter;
          }

          @Override
          public ArtifactGroupNamer artifactGroupNamer() {
            return namer;
          }

          @Override
          public BuildEventProtocolOptions getOptions() {
            return buildEventProtocolOptions;
          }
        };
    BuildEventStreamProtos.BuildEvent serializedBepEvent =
        buildEvent.getEvent().asStreamProto(ctx);
    buildEventLogger.log(serializedBepEvent);
    return serializedBepEvent;
  }

  private void publishBuildEvents()
      throws StatusException, LocalFileUploadException, InterruptedException {
    eventQueue.addFirst(new OpenStreamCommand());

    // Every build event sent to the server needs to be acknowledged by it. This queue stores
    // the build events that have been sent and still have to be acknowledged by the server.
    // The build events are stored in the order they were sent.
    ConcurrentLinkedDeque<SendBuildEventCommand> ackQueue = new ConcurrentLinkedDeque<>();
    boolean lastEventSent = false;
    int acksReceived = 0;
    int retryAttempt = 0;

    try {
      // {@link BuildEventServiceUploaderCommands#OPEN_STREAM} is the first event and opens a
      // bidi streaming RPC for sending build events and receiving ACKs.
      // {@link BuildEventServiceUploaderCommands#SEND_REGULAR_BUILD_EVENT} sends a build event to
      // the server. Sending of the Nth build event does
      // does not wait for the ACK of the N-1th build event to have been received.
      // {@link BuildEventServiceUploaderCommands#SEND_LAST_BUILD_EVENT} sends the last build event
      // and half closes the RPC.
      // {@link BuildEventServiceUploaderCommands#ACK_RECEIVED} is executed for every ACK from
      // the server and checks that the ACKs are in the correct order.
      // {@link BuildEventServiceUploaderCommands#STREAM_COMPLETE} checks that all build events
      // have been sent and all ACKs have been received. If not it invokes a retry logic that may
      // decide to re-send every build event for which an ACK has not been received. If so, it
      // adds an OPEN_STREAM event.
      while (true) {
        EventLoopCommand event = eventQueue.takeFirst();
        switch (event.type()) {
          case OPEN_STREAM:
            {
              // Invariant: the eventQueue only contains events of type SEND_REGULAR_BUILD_EVENT
              // or SEND_LAST_BUILD_EVENT
              logger.info(
                  String.format("Starting publishBuildEvents: eventQueue=%d", eventQueue.size()));
              streamContext =
                  besClient.openStream(
                      (ack) -> eventQueue.addLast(new AckReceivedCommand(ack.getSequenceNumber())));
              addStreamStatusListener(
                  streamContext.getStatus(),
                  (status) -> eventQueue.addLast(new StreamCompleteCommand(status)));
            }
            break;

          case SEND_REGULAR_BUILD_EVENT:
            {
              // Invariant: the eventQueue may contain events of any type
              SendRegularBuildEventCommand buildEvent = (SendRegularBuildEventCommand) event;
              ackQueue.addLast(buildEvent);

              PathConverter pathConverter = waitForLocalFileUploads(buildEvent);

              BuildEventStreamProtos.BuildEvent serializedRegularBuildEvent =
                  createSerializedRegularBuildEvent(pathConverter, buildEvent);

              PublishBuildToolEventStreamRequest request =
                  besProtoUtil.bazelEvent(
                      buildEvent.getSequenceNumber(),
                      buildEvent.getCreationTime(),
                      Any.pack(serializedRegularBuildEvent));

              streamContext.sendOverStream(request);
            }
            break;

          case SEND_LAST_BUILD_EVENT:
            {
              // Invariant: the eventQueue may contain events of any type
              SendBuildEventCommand lastEvent = (SendLastBuildEventCommand) event;
              ackQueue.addLast(lastEvent);
              lastEventSent = true;
              PublishBuildToolEventStreamRequest request =
                  besProtoUtil.streamFinished(
                      lastEvent.getSequenceNumber(), lastEvent.getCreationTime());
              streamContext.sendOverStream(request);
              streamContext.halfCloseStream();
            }
            break;

          case ACK_RECEIVED:
            {
              // Invariant: the eventQueue may contain events of any type
              AckReceivedCommand ackEvent = (AckReceivedCommand) event;
              if (!ackQueue.isEmpty()) {
                SendBuildEventCommand expected = ackQueue.removeFirst();
                long actualSeqNum = ackEvent.getSequenceNumber();
                if (expected.getSequenceNumber() == actualSeqNum) {
                  acksReceived++;
                } else {
                  ackQueue.addFirst(expected);
                  String message =
                      String.format(
                          "Expected ACK with seqNum=%d but received ACK with seqNum=%d",
                          expected.getSequenceNumber(), actualSeqNum);
                  logger.info(message);
                  streamContext.abortStream(Status.FAILED_PRECONDITION.withDescription(message));
              }
              } else {
                String message =
                    String.format(
                        "Received ACK (seqNum=%d) when no ACK was expected",
                        ackEvent.getSequenceNumber());
                logger.info(message);
                streamContext.abortStream(Status.FAILED_PRECONDITION.withDescription(message));
              }
            }
            break;

          case STREAM_COMPLETE:
            {
              // Invariant: the eventQueue only contains events of type SEND_REGULAR_BUILD_EVENT
              // or SEND_LAST_BUILD_EVENT
              streamContext = null;
              StreamCompleteCommand completeEvent = (StreamCompleteCommand) event;
              Status streamStatus = completeEvent.status();
              if (streamStatus.isOk()) {
                if (lastEventSent && ackQueue.isEmpty()) {
                  logger.info("publishBuildEvents was successful");
                  // Upload successful. Break out from the while(true) loop.
                  return;
                } else {
                  throw (lastEventSent
                          ? ackQueueNotEmptyStatus(ackQueue.size())
                          : lastEventNotSentStatus())
                      .asException();
                }
              }

              if (!shouldRetryStatus(streamStatus)) {
                logger.info(
                    String.format("Not retrying publishBuildEvents: status='%s'", streamStatus));
                throw streamStatus.asException();
              }
              if (retryAttempt == MAX_NUM_RETRIES) {
                logger.info(
                    String.format(
                        "Not retrying publishBuildEvents, no more attempts left: status='%s'",
                        streamStatus));
                throw streamStatus.asException();
              }

              // Retry logic
              // Adds events from the ackQueue to the front of the eventQueue, so that the
              // events in the eventQueue are sorted by sequence number (ascending).
              SendBuildEventCommand unacked;
              while ((unacked = ackQueue.pollLast()) != null) {
                eventQueue.addFirst(unacked);
              }

              long sleepMillis = retrySleepMillis(retryAttempt);
              logger.info(
                  String.format(
                      "Retrying stream: status='%s', sleepMillis=%d", streamStatus, sleepMillis));
              sleeper.sleepMillis(sleepMillis);

              // If we made progress, meaning the server ACKed events that we sent, then reset
              // the retry counter to 0.
              if (acksReceived > 0) {
                retryAttempt = 0;
              } else {
                retryAttempt++;
              }
              acksReceived = 0;
              eventQueue.addFirst(new OpenStreamCommand());
          }
            break;
        }
      }
    } catch (InterruptedException | LocalFileUploadException e) {
      int limit = 30;
      logger.info(
          String.format(
              "Publish interrupt. Showing up to %d items from queues: ack_queue_size: %d, "
                  + "ack_queue: %s, event_queue_size: %d, event_queue: %s",
              limit,
              ackQueue.size(),
              Iterables.limit(ackQueue, limit),
              eventQueue.size(),
              Iterables.limit(eventQueue, limit)));
      if (streamContext != null) {
        streamContext.abortStream(Status.CANCELLED);
      }
      throw e;
    } finally {
      // Cancel all local file uploads that may still be running
      // of events that haven't been uploaded.
      EventLoopCommand event;
      while ((event = ackQueue.pollFirst()) != null) {
        if (event instanceof SendRegularBuildEventCommand) {
          cancelLocalFileUpload((SendRegularBuildEventCommand) event);
        }
      }
      while ((event = eventQueue.pollFirst()) != null) {
        if (event instanceof SendRegularBuildEventCommand) {
          cancelLocalFileUpload((SendRegularBuildEventCommand) event);
        }
      }
    }
  }

  private void cancelLocalFileUpload(SendRegularBuildEventCommand event) {
    ListenableFuture<PathConverter> localFileUploaderFuture = event.localFileUploadProgress();
    if (!localFileUploaderFuture.isDone()) {
      localFileUploaderFuture.cancel(true);
    }
  }

  /** Sends a {@link PublishLifecycleEventRequest} to the BES backend. */
  private void publishLifecycleEvent(PublishLifecycleEventRequest request)
      throws StatusException, InterruptedException {
    int retryAttempt = 0;
    StatusException cause = null;
    while (retryAttempt <= MAX_NUM_RETRIES) {
      try {
        besClient.publish(request);
        return;
      } catch (StatusException e) {
        if (!shouldRetryStatus(e.getStatus())) {
          logger.info(
              String.format(
                  "Not retrying publishLifecycleEvent: status='%s'", e.getStatus().toString()));
          throw e;
        }

        cause = e;

        long sleepMillis = retrySleepMillis(retryAttempt);
        logger.info(
            String.format(
                "Retrying publishLifecycleEvent: status='%s', sleepMillis=%d",
                e.getStatus().toString(), sleepMillis));
        sleeper.sleepMillis(sleepMillis);
        retryAttempt++;
      }
    }

    // All retry attempts failed
    throw cause;
  }

  private ListenableFuture<PathConverter> uploadReferencedLocalFiles(
      Collection<LocalFile> localFiles) {
    Map<Path, LocalFile> localFileMap = new TreeMap<>();
    for (LocalFile localFile : localFiles) {
      // It is possible for targets to have duplicate artifacts (same path but different owners)
      // in their output groups. Since they didn't trigger an artifact conflict they are the
      // same file, so just skip either one
      localFileMap.putIfAbsent(localFile.path, localFile);
    }
    return localFileUploader.upload(localFileMap);
  }

  private void ensureUploadThreadStarted() {
    synchronized (lock) {
      if (uploadThread == null) {
        uploadThread = new Thread(this, "bes-uploader");
        uploadThread.start();
      }
    }
  }

  private void startCloseTimer(ListenableFuture<Void> closeFuture, Duration closeTimeout) {
    Thread closeTimer =
        new Thread(
            () -> {
              // Call closeOnTimeout() if the future does not complete within closeTimeout
              try {
                getUninterruptibly(closeFuture, closeTimeout.toMillis(), TimeUnit.MILLISECONDS);
              } catch (TimeoutException e) {
                closeOnTimeout();
              } catch (ExecutionException e) {
                if (e.getCause() instanceof TimeoutException) {
                  // This is likely due to an internal timeout doing the local file uploading.
                  closeOnTimeout();
                } else {
                  // This code only cares about calling closeOnTimeout() if the closeFuture does
                  // not complete within closeTimeout.
                  String failureMsg = "BES close failure";
                  logger.severe(failureMsg);
                  LoggingUtil.logToRemote(Level.SEVERE, failureMsg, e);
                }
              }
            },
            "bes-uploader-close-timer");
    closeTimer.start();
  }

  private void failCloseFuture(Throwable cause) {
    synchronized (lock) {
      if (closeFuture == null) {
        closeFuture = SettableFuture.create();
      }
      closeFuture.setException(cause);
    }
  }

  private PathConverter waitForLocalFileUploads(SendRegularBuildEventCommand orderedBuildEvent)
      throws LocalFileUploadException, InterruptedException {
    try {
      // Wait for the local file upload to have been completed.
      return orderedBuildEvent.localFileUploadProgress().get();
    } catch (ExecutionException e) {
      logger.log(
          Level.WARNING,
          String.format(
              "Failed to upload local files referenced by build event: %s", e.getMessage()),
          e);
      Throwables.throwIfUnchecked(e.getCause());
      throw new LocalFileUploadException(e.getCause());
    }
  }

  private Timestamp currentTime() {
    return Timestamps.fromMillis(clock.currentTimeMillis());
  }

  private static Result extractBuildStatus(BuildCompletingEvent event) {
    if (event.getExitCode() != null && event.getExitCode().getNumericExitCode() == 0) {
      return COMMAND_SUCCEEDED;
    } else {
      return COMMAND_FAILED;
    }
  }

  private static Status lastEventNotSentStatus() {
    return Status.FAILED_PRECONDITION.withDescription(
        "Server closed stream with status OK but not all events have been sent");
  }

  private static Status ackQueueNotEmptyStatus(int ackQueueSize) {
    return Status.FAILED_PRECONDITION.withDescription(
        String.format(
            "Server closed stream with status OK but not all ACKs have been"
                + " received (ackQueue=%d)",
            ackQueueSize));
  }

  private static void addStreamStatusListener(
      ListenableFuture<Status> stream, Consumer<Status> onDone) {
    Futures.addCallback(
        stream,
        new FutureCallback<Status>() {
          @Override
          public void onSuccess(Status result) {
            onDone.accept(result);
          }

          @Override
          public void onFailure(Throwable t) {}
        },
        MoreExecutors.directExecutor());
  }

  private static boolean shouldRetryStatus(Status status) {
    return !status.getCode().equals(Code.INVALID_ARGUMENT)
        && !status.getCode().equals(Code.FAILED_PRECONDITION);
  }

  private static long retrySleepMillis(int attempt) {
    // This somewhat matches the backoff used for gRPC connection backoffs.
    return (long) (DELAY_MILLIS * Math.pow(1.6, attempt));
  }

  /**
   * This method is only used in tests. Once TODO(b/113035235) is fixed the close future will also
   * carry error messages.
   */
  @VisibleForTesting // productionVisibility = Visibility.PRIVATE
  public void throwUploaderError() throws Throwable {
    synchronized (lock) {
      checkState(closeFuture != null && closeFuture.isDone());
      try {
        closeFuture.get();
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  /** Thrown when encountered problems while uploading build event artifacts. */
  private class LocalFileUploadException extends Exception {
    LocalFileUploadException(Throwable cause) {
      super(cause);
    }
  }
}

