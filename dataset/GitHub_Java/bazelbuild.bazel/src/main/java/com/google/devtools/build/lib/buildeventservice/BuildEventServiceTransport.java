// Copyright 2017 The Bazel Authors. All rights reserved.
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
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.google.devtools.build.lib.events.EventKind.ERROR;
import static com.google.devtools.build.lib.events.EventKind.INFO;
import static com.google.devtools.build.lib.events.EventKind.WARNING;
import static com.google.devtools.build.v1.BuildEvent.EventCase.COMPONENT_STREAM_FINISHED;
import static com.google.devtools.build.v1.BuildStatus.Result.COMMAND_FAILED;
import static com.google.devtools.build.v1.BuildStatus.Result.COMMAND_SUCCEEDED;
import static com.google.devtools.build.v1.BuildStatus.Result.UNKNOWN_STATUS;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.joda.time.Duration.standardSeconds;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.build.lib.buildeventservice.client.BuildEventServiceClient;
import com.google.devtools.build.lib.buildeventstream.ArtifactGroupNamer;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventConverters;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent.PayloadCase;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildFinished;
import com.google.devtools.build.lib.buildeventstream.BuildEventTransport;
import com.google.devtools.build.lib.buildeventstream.PathConverter;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.EventKind;
import com.google.devtools.build.lib.runtime.BlazeModule.ModuleEnvironment;
import com.google.devtools.build.lib.util.AbruptExitException;
import com.google.devtools.build.lib.util.Clock;
import com.google.devtools.build.lib.util.ExitCode;
import com.google.devtools.build.v1.BuildStatus.Result;
import com.google.devtools.build.v1.OrderedBuildEvent;
import com.google.devtools.build.v1.PublishBuildToolEventStreamResponse;
import com.google.devtools.build.v1.PublishLifecycleEventRequest;
import com.google.protobuf.Any;
import io.grpc.Status;
import java.util.Deque;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.joda.time.Duration;

/** A {@link BuildEventTransport} that streams {@link BuildEvent}s to BuildEventService. */
public class BuildEventServiceTransport implements BuildEventTransport {

  static final String UPLOAD_FAILED_MESSAGE = "Build Event Protocol upload failed: %s";
  static final String UPLOAD_SUCCEEDED_MESSAGE =
      "Build Event Protocol upload finished successfully.";

  private static final Logger logger = Logger.getLogger(BuildEventServiceTransport.class.getName());

  /** Max wait time until for the Streaming RPC to finish after all events were enqueued. */
  private static final Duration PUBLISH_EVENT_STREAM_FINISHED_TIMEOUT = standardSeconds(120);

  private final ListeningExecutorService uploaderExecutorService;
  private final Duration uploadTimeout;
  private final boolean publishLifecycleEvents;
  private final boolean bestEffortUpload;
  private final BuildEventServiceClient besClient;
  private final BuildEventServiceProtoUtil besProtoUtil;
  private final ModuleEnvironment moduleEnvironment;
  private final EventHandler commandLineReporter;

  private final PathConverter pathConverter;
  /** Contains all pendingAck events that might be retried in case of failures. */
  private ConcurrentLinkedDeque<OrderedBuildEvent> pendingAck;
  /** Contains all events should be sent ordered by sequence number. */
  private final BlockingDeque<OrderedBuildEvent> pendingSend;
  /** Holds the result status of the BuildEventStreamProtos BuildFinished event. */
  private Result invocationResult;
  /** Used to block until all events have been uploaded. */
  private ListenableFuture<?> uploadComplete;
  /** Used to ensure that the close logic is only invoked once. */
  private SettableFuture<Void> shutdownFuture;
  /**
   * If the call before the current call threw an exception, this field points to it. If the
   * previous call was successful, this field is null. This is useful for error reporting, when an
   * upload times out due to having had to retry several times.
   */
  private volatile Exception lastKnownError;
  /** Returns true if we already reported a warning or error to UI. */
  private volatile boolean errorsReported;

  public BuildEventServiceTransport(
      BuildEventServiceClient besClient,
      Duration uploadTimeout,
      boolean bestEffortUpload,
      boolean publishLifecycleEvents,
      String buildRequestId,
      String invocationId,
      ModuleEnvironment moduleEnvironment,
      Clock clock,
      PathConverter pathConverter,
      EventHandler commandLineReporter,
      @Nullable String projectId) {
    this(
        besClient,
        uploadTimeout,
        bestEffortUpload,
        publishLifecycleEvents,
        moduleEnvironment,
        new BuildEventServiceProtoUtil(buildRequestId, invocationId, projectId, clock),
        pathConverter,
        commandLineReporter);
  }

  @VisibleForTesting
  BuildEventServiceTransport(
      BuildEventServiceClient besClient,
      Duration uploadTimeout,
      boolean bestEffortUpload,
      boolean publishLifecycleEvents,
      ModuleEnvironment moduleEnvironment,
      BuildEventServiceProtoUtil besProtoUtil,
      PathConverter pathConverter,
      EventHandler commandLineReporter) {
    this.besClient = besClient;
    this.besProtoUtil = besProtoUtil;
    this.publishLifecycleEvents = publishLifecycleEvents;
    this.moduleEnvironment = moduleEnvironment;
    this.commandLineReporter = commandLineReporter;
    this.pendingAck = new ConcurrentLinkedDeque<>();
    this.pendingSend = new LinkedBlockingDeque<>();
    // Setting the thread count to 2 instead of 1 is a hack, but necessary as publishEventStream
    // blocks one thread permanently and thus we can't do any other work on the executor. A proper
    // fix would be to remove the spinning loop from publishEventStream and instead implement the
    // loop by publishEventStream re-submitting itself to the executor.
    // TODO(buchgr): Fix it.
    this.uploaderExecutorService = listeningDecorator(Executors.newFixedThreadPool(2));
    this.pathConverter = pathConverter;
    this.invocationResult = UNKNOWN_STATUS;
    this.uploadTimeout = uploadTimeout;
    this.bestEffortUpload = bestEffortUpload;
  }

  @Override
  public synchronized ListenableFuture<Void> close() {
    if (shutdownFuture != null) {
      return shutdownFuture;
    }

    logger.log(Level.INFO, "Closing the build event service transport.");

    // The future is completed once the close succeeded or failed.
    shutdownFuture = SettableFuture.create();

    uploaderExecutorService.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              sendOrderedBuildEvent(besProtoUtil.streamFinished());

              if (errorsReported) {
                // If we encountered errors before and have already reported them, then we should
                // not report them a second time.
                return;
              }

              if (bestEffortUpload) {
                // TODO(buchgr): The code structure currently doesn't allow to enforce a timeout for
                // best effort upload.
                if (!uploadComplete.isDone()) {
                  report(INFO, "Asynchronous Build Event Protocol upload.");
                } else {
                  Throwable uploadError = fromFuture(uploadComplete);

                  if (uploadError != null) {
                    report(WARNING, UPLOAD_FAILED_MESSAGE, uploadError.getMessage());
                  } else {
                    report(INFO, UPLOAD_SUCCEEDED_MESSAGE);
                  }
                }
              } else {
                report(INFO, "Waiting for Build Event Protocol upload to finish.");
                try {
                  if (Duration.ZERO.equals(uploadTimeout)) {
                    uploadComplete.get();
                  } else {
                    uploadComplete.get(uploadTimeout.getMillis(), MILLISECONDS);
                  }
                  report(INFO, UPLOAD_SUCCEEDED_MESSAGE);
                } catch (Exception e) {
                  uploadComplete.cancel(true);
                  reportErrorAndFailBuild(e);
                }
              }
            } finally {
              shutdownFuture.set(null);
              uploaderExecutorService.shutdown();
            }
          }
        });

    return shutdownFuture;
  }

  @Override
  public String name() {
    // TODO(buchgr): Also display the hostname / IP.
    return "Build Event Service";
  }

  @Override
  public synchronized void sendBuildEvent(BuildEvent event, final ArtifactGroupNamer namer) {
    BuildEventStreamProtos.BuildEvent eventProto = event.asStreamProto(
        new BuildEventConverters() {
          @Override
          public PathConverter pathConverter() {
            return pathConverter;
          }
          @Override
          public ArtifactGroupNamer artifactGroupNamer() {
            return namer;
          }
        });
    if (PayloadCase.FINISHED.equals(eventProto.getPayloadCase())) {
      BuildFinished finished = eventProto.getFinished();
      if (finished.hasExitCode() && finished.getExitCode().getCode() == 0) {
        invocationResult = COMMAND_SUCCEEDED;
      } else {
        invocationResult = COMMAND_FAILED;
      }
    }

    sendOrderedBuildEvent(besProtoUtil.bazelEvent(Any.pack(eventProto)));
  }

  private String errorMessageFromException(Throwable t) {
    String message;
    if (t instanceof TimeoutException) {
      message = "Build Event Protocol upload timed out.";
      Exception lastKnownError0 = lastKnownError;
      if (lastKnownError0 != null) {
        // We may at times get a timeout exception due to an underlying error that was retried
        // several times. If such an error exists, report it.
        message += " Transport errors caused the upload to be retried.";
        message += " Last known reason for retry: ";
        message += besClient.userReadableError(lastKnownError0);
        return message;
      }
      return message;
    } else if (t instanceof ExecutionException) {
      message = format(UPLOAD_FAILED_MESSAGE,
          t.getCause() != null
              ? besClient.userReadableError(t.getCause())
              : t.getMessage());
      return message;
    } else {
      message = format(UPLOAD_FAILED_MESSAGE, besClient.userReadableError(t));
      return message;
    }
  }

  private void reportErrorAndFailBuild(Throwable t) {
    checkState(!bestEffortUpload);

    String message = errorMessageFromException(t);

    report(ERROR, message);
    moduleEnvironment.exit(new AbruptExitException(ExitCode.PUBLISH_ERROR));
  }

  private void maybeReportUploadError() {
    if (errorsReported) {
      return;
    }

    Throwable uploadError = fromFuture(uploadComplete);
    if (uploadError != null) {
      errorsReported = true;
      if (bestEffortUpload) {
        report(WARNING, UPLOAD_FAILED_MESSAGE, uploadError.getMessage());
      } else {
        reportErrorAndFailBuild(uploadError);
      }
    }
  }

  private synchronized void sendOrderedBuildEvent(OrderedBuildEvent serialisedEvent) {
    if (uploadComplete != null && uploadComplete.isDone()) {
      maybeReportUploadError();
      return;
    }

    pendingSend.add(serialisedEvent);
    if (uploadComplete == null) {
      uploadComplete = uploaderExecutorService.submit(new BuildEventServiceUpload());
    }
  }

  private synchronized Result getInvocationResult() {
    return invocationResult;
  }

  /**
   * Method responsible for sending all requests to BuildEventService.
   */
  private class BuildEventServiceUpload implements Callable<Void> {
    @Override
    public Void call() throws Exception {
      try {
        publishBuildEnqueuedEvent();
        publishInvocationStartedEvent();
        try {
          publishEventStream0();
        } finally {
          Result result = getInvocationResult();
          publishInvocationFinishedEvent(result);
          publishBuildFinishedEvent(result);
        }
      } finally {
        besClient.shutdown();
      }
      return null;
    }

    private void publishBuildEnqueuedEvent() throws Exception {
      retryOnException(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          publishLifecycleEvent(besProtoUtil.buildEnqueued());
          return null;
        }
      });
    }

    private void publishInvocationStartedEvent() throws Exception {
      retryOnException(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          publishLifecycleEvent(besProtoUtil.invocationStarted());
          return null;
        }
      });
    }

    private void publishEventStream0() throws Exception {
      retryOnException(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          publishEventStream();
          return null;
        }
      });
    }

    private void publishInvocationFinishedEvent(final Result result) throws Exception {
      retryOnException(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          publishLifecycleEvent(besProtoUtil.invocationFinished(result));
          return null;
        }
      });
    }

    private void publishBuildFinishedEvent(final Result result) throws Exception {
      retryOnException(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          publishLifecycleEvent(besProtoUtil.buildFinished(result));
          return null;
        }
      });
    }
  }

  /** Responsible for publishing lifecycle evnts RPC. Safe to retry. */
  private Status publishLifecycleEvent(PublishLifecycleEventRequest request) throws Exception {
    if (publishLifecycleEvents) {
      // Change the status based on BEP data
      return besClient.publish(request);
    }
    return Status.OK;
  }

  /**
   * Used as method reference, responsible for the entire Streaming RPC. Safe to retry. This method
   * it carries states between consecutive calls (pendingAck messages will be added to the head of
   * of the pendingSend queue), but that is intended behavior.
   */
  private Status publishEventStream() throws Exception {
    // Reschedule unacked messages if required, keeping its original order.
    OrderedBuildEvent unacked;
    while ((unacked = pendingAck.pollLast()) != null) {
      pendingSend.addFirst(unacked);
    }
    pendingAck = new ConcurrentLinkedDeque<>();

    return publishEventStream(pendingAck, pendingSend, besClient)
        .get(PUBLISH_EVENT_STREAM_FINISHED_TIMEOUT.getMillis(), TimeUnit.MILLISECONDS);
  }

  /** Method responsible for a single Streaming RPC. */
  private static ListenableFuture<Status> publishEventStream(
      final ConcurrentLinkedDeque<OrderedBuildEvent> pendingAck,
      final BlockingDeque<OrderedBuildEvent> pendingSend,
      final BuildEventServiceClient besClient)
      throws Exception {
    OrderedBuildEvent event;
    ListenableFuture<Status> streamDone = besClient.openStream(ackCallback(pendingAck, besClient));
    try {
      do {
        event = pendingSend.takeFirst();
        pendingAck.add(event);
        besClient.sendOverStream(event);
      } while (!isLastEvent(event));
      besClient.closeStream();
      logger.log(Level.INFO, "Closing the build event stream.");
    } catch (Exception e) {
      logger.log(Level.WARNING, "Aborting publishEventStream.", e);
      besClient.abortStream(Status.INTERNAL.augmentDescription(e.getMessage()));
    }
    return streamDone;
  }

  private static boolean isLastEvent(OrderedBuildEvent event) {
    return event != null && event.getEvent().getEventCase() == COMPONENT_STREAM_FINISHED;
  }

  private static Function<PublishBuildToolEventStreamResponse, Void> ackCallback(
      final Deque<OrderedBuildEvent> pendingAck, final BuildEventServiceClient besClient) {
    return new Function<PublishBuildToolEventStreamResponse, Void>() {
      @Override
      public Void apply(PublishBuildToolEventStreamResponse ack) {
        long pendingSeq =
            pendingAck.isEmpty() ? -1 : pendingAck.peekFirst().getSequenceNumber();
        long ackSeq = ack.getSequenceNumber();
        if (pendingSeq != ackSeq) {
          besClient.abortStream(Status.INTERNAL
              .augmentDescription(format("Expected ack %s but was %s.", pendingSeq, ackSeq)));
        } else {
          pendingAck.removeFirst();
        }
        return null;
      }
    };
  }

  private void retryOnException(Callable<?> c) throws Exception {
    retryOnException(c, 3, 100);
  }

  /**
   * Executes a {@link Callable} retrying on exception thrown.
   */
  // TODO(eduardocolaco): Implement transient/persistent failures
  private void retryOnException(Callable<?> c, final int maxRetries, final long initalDelayMillis)
      throws Exception {
    int tries = 0;
    while (tries <= maxRetries) {
      try {
        c.call();
        lastKnownError = null;
        return;
        // TODO(buchgr): Narrow the exception to not catch InterruptedException and
        // RuntimeException's.
      } catch (Exception e) {
        tries++;
        lastKnownError = e;
        /*
         * Exponential backoff:
         * Retry 1: initalDelayMillis * 2^0
         * Retry 2: initalDelayMillis * 2^1
         * Retry 3: initalDelayMillis * 2^2
         * ...
         */
        long sleepMillis = initalDelayMillis << (tries - 1);
        String message = String.format("Retrying RPC to BES. Attempt %s. Backoff %s ms.",
            tries, sleepMillis);
        logger.log(Level.INFO, message, lastKnownError);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(sleepMillis));
      }
    }
    Preconditions.checkNotNull(lastKnownError);
    throw lastKnownError;
  }

  private void report(EventKind eventKind, String msg, Object... parameters) {
    commandLineReporter.handle(Event.of(eventKind, null, format(msg, parameters)));
  }

  @Nullable
  private static Throwable fromFuture(Future<?> f) {
    if (!f.isDone()) {
      return null;
    }
    try {
      f.get();
      return null;
    } catch (Throwable t) {
      return t;
    }
  }
}
