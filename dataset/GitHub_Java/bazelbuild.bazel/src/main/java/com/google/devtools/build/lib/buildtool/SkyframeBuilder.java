// Copyright 2014 Google Inc. All rights reserved.
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
package com.google.devtools.build.lib.buildtool;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionCacheChecker;
import com.google.devtools.build.lib.actions.ActionExecutionStatusReporter;
import com.google.devtools.build.lib.actions.ActionInputFileCache;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.BuildFailedException;
import com.google.devtools.build.lib.actions.BuilderUtils;
import com.google.devtools.build.lib.actions.Executor;
import com.google.devtools.build.lib.actions.ResourceManager;
import com.google.devtools.build.lib.actions.TestExecException;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.TargetCompleteEvent;
import com.google.devtools.build.lib.rules.test.TestProvider;
import com.google.devtools.build.lib.skyframe.ActionExecutionInactivityWatchdog;
import com.google.devtools.build.lib.skyframe.ActionExecutionValue;
import com.google.devtools.build.lib.skyframe.Builder;
import com.google.devtools.build.lib.skyframe.SkyFunctions;
import com.google.devtools.build.lib.skyframe.SkyframeActionExecutor;
import com.google.devtools.build.lib.skyframe.SkyframeExecutor;
import com.google.devtools.build.lib.skyframe.TargetCompletionValue;
import com.google.devtools.build.lib.util.AbruptExitException;
import com.google.devtools.build.lib.util.BlazeClock;
import com.google.devtools.build.skyframe.CycleInfo;
import com.google.devtools.build.skyframe.ErrorInfo;
import com.google.devtools.build.skyframe.EvaluationProgressReceiver;
import com.google.devtools.build.skyframe.EvaluationResult;
import com.google.devtools.build.skyframe.SkyFunctionName;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Builder} implementation driven by Skyframe.
 */
@VisibleForTesting
public class SkyframeBuilder implements Builder {

  private final SkyframeExecutor skyframeExecutor;
  private final boolean keepGoing;
  private final int numJobs;
  private final boolean checkOutputFiles;
  private final ActionInputFileCache fileCache;
  private final ActionCacheChecker actionCacheChecker;
  private final int progressReportInterval;

  @VisibleForTesting
  public SkyframeBuilder(SkyframeExecutor skyframeExecutor, ActionCacheChecker actionCacheChecker,
      boolean keepGoing, int numJobs, boolean checkOutputFiles,
      ActionInputFileCache fileCache, int progressReportInterval) {
    this.skyframeExecutor = skyframeExecutor;
    this.actionCacheChecker = actionCacheChecker;
    this.keepGoing = keepGoing;
    this.numJobs = numJobs;
    this.checkOutputFiles = checkOutputFiles;
    this.fileCache = fileCache;
    this.progressReportInterval = progressReportInterval;
  }

  @Override
  public void buildArtifacts(Set<Artifact> artifacts,
      Set<ConfiguredTarget> parallelTests,
      Set<ConfiguredTarget> exclusiveTests,
      Collection<ConfiguredTarget> targetsToBuild,
      Executor executor,
      Set<ConfiguredTarget> builtTargets,
      boolean explain,
      Range<Long> lastExecutionTimeRange)
      throws BuildFailedException, AbruptExitException, TestExecException, InterruptedException {
    skyframeExecutor.prepareExecution(checkOutputFiles, lastExecutionTimeRange);
    skyframeExecutor.setFileCache(fileCache);
    // Note that executionProgressReceiver accesses builtTargets concurrently (after wrapping in a
    // synchronized collection), so unsynchronized access to this variable is unsafe while it runs.
    ExecutionProgressReceiver executionProgressReceiver =
        new ExecutionProgressReceiver(Preconditions.checkNotNull(builtTargets),
            countTestActions(exclusiveTests), skyframeExecutor.getEventBus());
    ResourceManager.instance().setEventBus(skyframeExecutor.getEventBus());

    boolean success = false;
    EvaluationResult<?> result;

    ActionExecutionStatusReporter statusReporter = ActionExecutionStatusReporter.create(
        skyframeExecutor.getReporter(), executor, skyframeExecutor.getEventBus());

    AtomicBoolean isBuildingExclusiveArtifacts = new AtomicBoolean(false);
    ActionExecutionInactivityWatchdog watchdog = new ActionExecutionInactivityWatchdog(
        executionProgressReceiver.createInactivityMonitor(statusReporter),
        executionProgressReceiver.createInactivityReporter(statusReporter,
            isBuildingExclusiveArtifacts), progressReportInterval);

    skyframeExecutor.setActionExecutionProgressReportingObjects(executionProgressReceiver,
        executionProgressReceiver, statusReporter);
    watchdog.start();

    try {
      result = skyframeExecutor.buildArtifacts(executor, artifacts, targetsToBuild, parallelTests,
          /*exclusiveTesting=*/false, keepGoing, explain, numJobs, actionCacheChecker,
          executionProgressReceiver);
      // progressReceiver is finished, so unsynchronized access to builtTargets is now safe.
      success = processResult(result, keepGoing, skyframeExecutor);

      Preconditions.checkState(
          !success || result.keyNames().size()
              == (artifacts.size() + targetsToBuild.size() + parallelTests.size()),
          "Build reported as successful but not all artifacts and targets built: %s, %s",
          result, artifacts);

      // Run exclusive tests: either tagged as "exclusive" or is run in an invocation with
      // --test_output=streamed.
      isBuildingExclusiveArtifacts.set(true);
      for (ConfiguredTarget exclusiveTest : exclusiveTests) {
        // Since only one artifact is being built at a time, we don't worry about an artifact being
        // built and then the build being interrupted.
        result = skyframeExecutor.buildArtifacts(executor, ImmutableSet.<Artifact>of(),
            targetsToBuild, ImmutableSet.of(exclusiveTest), /*exclusiveTesting=*/true, keepGoing,
            explain, numJobs, actionCacheChecker, null);
        boolean exclusiveSuccess = processResult(result, keepGoing, skyframeExecutor);
        Preconditions.checkState(!exclusiveSuccess || !result.keyNames().isEmpty(),
            "Build reported as successful but test %s not executed: %s",
            exclusiveTest, result);
        success &= exclusiveSuccess;
      }
    } finally {
      watchdog.stop();
      ResourceManager.instance().unsetEventBus();
      skyframeExecutor.setActionExecutionProgressReportingObjects(null, null, null);
      statusReporter.unregisterFromEventBus();
    }

    if (!success) {
      throw new BuildFailedException();
    }
  }

  private static boolean resultHasCatastrophicError(EvaluationResult<?> result) {
    for (ErrorInfo errorInfo : result.errorMap().values()) {
      if (errorInfo.isCatastrophic()) {
        return true;
      }
    }
    // An unreported catastrophe manifests with hasError() being true but no errors visible.
    return result.hasError() && result.errorMap().isEmpty();
  }

  /**
   * Process the Skyframe update, taking into account the keepGoing setting.
   *
   * Returns false if the update() failed, but we should continue. Returns true on success.
   * Throws on fail-fast failures.
   */
  private static boolean processResult(EvaluationResult<?> result, boolean keepGoing,
      SkyframeExecutor skyframeExecutor) throws BuildFailedException, TestExecException {
    if (result.hasError()) {
      boolean hasCycles = false;
      for (Map.Entry<SkyKey, ErrorInfo> entry : result.errorMap().entrySet()) {
        Iterable<CycleInfo> cycles = entry.getValue().getCycleInfo();
        skyframeExecutor.reportCycles(cycles, entry.getKey());
        hasCycles |= !Iterables.isEmpty(cycles);
      }
      if (keepGoing && !resultHasCatastrophicError(result)) {
        return false;
      }
      if (hasCycles || result.errorMap().isEmpty()) {
        // error map may be empty in the case of a catastrophe.
        throw new BuildFailedException();
      } else {
        // Need to wrap exception for rethrowCause.
        BuilderUtils.rethrowCause(
          new Exception(Preconditions.checkNotNull(result.getError().getException())));
      }
    }
    return true;
  }

  private static int countTestActions(Iterable<ConfiguredTarget> testTargets) {
    int count = 0;
    for (ConfiguredTarget testTarget : testTargets) {
      count += TestProvider.getTestStatusArtifacts(testTarget).size();
    }
    return count;
  }

  /**
   * Listener for executed actions and built artifacts. We use a listener so that we have an
   * accurate set of successfully run actions and built artifacts, even if the build is interrupted.
   */
  private static final class ExecutionProgressReceiver implements EvaluationProgressReceiver,
      SkyframeActionExecutor.ProgressSupplier, SkyframeActionExecutor.ActionCompletedReceiver {
    private static final NumberFormat PROGRESS_MESSAGE_NUMBER_FORMATTER;

    // Must be thread-safe!
    private final Set<ConfiguredTarget> builtTargets;
    private final Set<SkyKey> enqueuedActions = Sets.newConcurrentHashSet();
    private final Set<Action> completedActions = Sets.newConcurrentHashSet();
    private final Object activityIndicator = new Object();
    /** Number of exclusive tests. To be accounted for in progress messages. */
    private final int exclusiveTestsCount;
    private final EventBus eventBus;

    static {
      PROGRESS_MESSAGE_NUMBER_FORMATTER = NumberFormat.getIntegerInstance(Locale.ENGLISH);
      PROGRESS_MESSAGE_NUMBER_FORMATTER.setGroupingUsed(true);
    }

    /**
     * {@code builtTargets} is accessed through a synchronized set, and so no other access to it
     * is permitted while this receiver is active.
     */
    ExecutionProgressReceiver(Set<ConfiguredTarget> builtTargets, int exclusiveTestsCount,
                              EventBus eventBus) {
      this.builtTargets = Collections.synchronizedSet(builtTargets);
      this.exclusiveTestsCount = exclusiveTestsCount;
      this.eventBus = eventBus;
    }

    @Override
    public void invalidated(SkyValue node, InvalidationState state) {}

    @Override
    public void enqueueing(SkyKey skyKey) {
      if (ActionExecutionValue.isReportWorthyAction(skyKey)) {
        // Remember all enqueued actions for the benefit of progress reporting.
        // We discover most actions early in the build, well before we start executing them.
        // Some of these will be cache hits and won't be executed, so we'll need to account for them
        // in the evaluated method too.
        enqueuedActions.add(skyKey);
      }
    }

    @Override
    public void evaluated(SkyKey skyKey, SkyValue node, EvaluationState state) {
      SkyFunctionName type = skyKey.functionName();
      if (type == SkyFunctions.TARGET_COMPLETION) {
        TargetCompletionValue val = (TargetCompletionValue) node;
        ConfiguredTarget target = val.getConfiguredTarget();
        builtTargets.add(target);
        eventBus.post(TargetCompleteEvent.createSuccessful(target));
      } else if (type == SkyFunctions.ACTION_EXECUTION) {
        // Remember all completed actions, regardless of having been cached or really executed.
        actionCompleted((Action) skyKey.argument());
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method adds the action to {@link #completedActions} and notifies the
     * {@link #activityIndicator}.
     *
     * <p>We could do this only in the {@link #evaluated} method too, but as it happens the action
     * executor tells the reporter about the completed action before the node is inserted into the
     * graph, so the reporter would find out about the completed action sooner than we could
     * have updated {@link #completedActions}, which would result in incorrect numbers on the
     * progress messages. However we have to store completed actions in {@link #evaluated} too,
     * because that's the only place we get notified about completed cached actions.
     */
    @Override
    public void actionCompleted(Action a) {
      if (ActionExecutionValue.isReportWorthyAction(a)) {
        completedActions.add(a);
        synchronized (activityIndicator) {
          activityIndicator.notifyAll();
        }
      }
    }

    @Override
    public String getProgressString() {
      return String.format("[%s / %s]",
          PROGRESS_MESSAGE_NUMBER_FORMATTER.format(completedActions.size()),
          PROGRESS_MESSAGE_NUMBER_FORMATTER.format(exclusiveTestsCount + enqueuedActions.size()));
    }

    ActionExecutionInactivityWatchdog.InactivityMonitor createInactivityMonitor(
        final ActionExecutionStatusReporter statusReporter) {
      return new ActionExecutionInactivityWatchdog.InactivityMonitor() {

        @Override
        public boolean hasStarted() {
          return !enqueuedActions.isEmpty();
        }

        @Override
        public int getPending() {
          return statusReporter.getCount();
        }

        @Override
        public int waitForNextCompletion(int timeoutMilliseconds) throws InterruptedException {
          synchronized (activityIndicator) {
            int before = completedActions.size();
            long startTime = BlazeClock.instance().currentTimeMillis();
            while (true) {
              activityIndicator.wait(timeoutMilliseconds);

              int completed = completedActions.size() - before;
              long now = 0;
              if (completed > 0 || (startTime + timeoutMilliseconds) <= (now = BlazeClock.instance()
                  .currentTimeMillis())) {
                // Some actions completed, or timeout fully elapsed.
                return completed;
              } else {
                // Spurious Wakeup -- no actions completed and there's still time to wait.
                timeoutMilliseconds -= now - startTime;  // account for elapsed wait time
                startTime = now;
              }
            }
          }
        }
      };
    }

    ActionExecutionInactivityWatchdog.InactivityReporter createInactivityReporter(
        final ActionExecutionStatusReporter statusReporter,
        final AtomicBoolean isBuildingExclusiveArtifacts) {
      return new ActionExecutionInactivityWatchdog.InactivityReporter() {
        @Override
        public void maybeReportInactivity() {
          // Do not report inactivity if we are currently running an exclusive test or a streaming
          // action (in practice only tests can stream and it implicitly makes them exclusive).
          if (!isBuildingExclusiveArtifacts.get()) {
            statusReporter.showCurrentlyExecutingActions(
                ExecutionProgressReceiver.this.getProgressString() + " ");
          }
        }
      };
    }
  }
}
