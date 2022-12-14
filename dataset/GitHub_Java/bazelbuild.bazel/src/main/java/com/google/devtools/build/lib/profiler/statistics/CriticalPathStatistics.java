// Copyright 2015 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.profiler.statistics;

import com.google.common.base.Predicate;
import com.google.devtools.build.lib.profiler.ProfileInfo;
import com.google.devtools.build.lib.profiler.ProfileInfo.CriticalPathEntry;
import com.google.devtools.build.lib.profiler.ProfileInfo.Task;
import com.google.devtools.build.lib.profiler.ProfilerTask;
import com.google.devtools.build.lib.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps a predefined list of {@link CriticalPathEntry}'s cumulative durations and allows
 * iterating over pairs of their descriptions and relative durations.
 */
//TODO(bazel-team): Add remote vs build stats recorded by Logging.CriticalPathStats
public final class CriticalPathStatistics implements Iterable<Pair<String, Double>> {

  private static final EnumSet<ProfilerTask> FILTER_NONE = EnumSet.noneOf(ProfilerTask.class);
  /** Always filter out ACTION_LOCK and WAIT tasks to simulate unlimited resource critical path
   * (TODO see comments inside formatExecutionPhaseStatistics() method).
   */
  private static final EnumSet<ProfilerTask> DEFAULT_FILTER =
      EnumSet.of(ProfilerTask.ACTION_LOCK, ProfilerTask.WAIT);

  private static final List<Pair<String, EnumSet<ProfilerTask>>> FILTERS =
      Arrays.asList(
          Pair.of("the builder overhead", EnumSet.allOf(ProfilerTask.class)),
          Pair.of(
              "the VFS calls",
              ProfilerTask.allSatisfying(
                  new Predicate<ProfilerTask>() {
                    @Override
                    public boolean apply(ProfilerTask task) {
                      return DEFAULT_FILTER.contains(task) || task.name().startsWith("VFS_");
                    }
                  })),
          typeFilter("the dependency checking", ProfilerTask.ACTION_CHECK),
          typeFilter("the execution setup", ProfilerTask.ACTION_EXECUTE),
          typeFilter("local execution", ProfilerTask.SPAWN, ProfilerTask.LOCAL_EXECUTION),
          typeFilter("the include scanner", ProfilerTask.SCANNER),
          typeFilter(
              "Remote execution (cumulative)",
              ProfilerTask.REMOTE_EXECUTION,
              ProfilerTask.PROCESS_TIME,
              ProfilerTask.LOCAL_PARSE,
              ProfilerTask.UPLOAD_TIME,
              ProfilerTask.REMOTE_QUEUE,
              ProfilerTask.REMOTE_SETUP,
              ProfilerTask.FETCH),
          typeFilter("  file uploads", ProfilerTask.UPLOAD_TIME, ProfilerTask.REMOTE_SETUP),
          typeFilter("  file fetching", ProfilerTask.FETCH),
          typeFilter("  process time", ProfilerTask.PROCESS_TIME),
          typeFilter("  remote queueing", ProfilerTask.REMOTE_QUEUE),
          typeFilter("  remote execution parse", ProfilerTask.LOCAL_PARSE),
          typeFilter("  other remote activities", ProfilerTask.REMOTE_EXECUTION));

  private final List<Long> criticalPathDurations;

  /**
   * The actual critical path.
   */
  private final CriticalPathEntry totalPath;

  /**
   * Unlimited resource critical path. Essentially, we assume that if we remove all scheduling
   * delays caused by resource semaphore contention, each action execution time would not change
   * (even though load now would be substantially higher - so this assumption might be incorrect
   * but it is the path excluding scheduling delays).
   */
  private final CriticalPathEntry optimalPath;

  private final long workerWaitTime;
  private final long mainThreadWaitTime;

  public CriticalPathStatistics(ProfileInfo info) {
    totalPath = info.getCriticalPath(FILTER_NONE);
    info.analyzeCriticalPath(FILTER_NONE, totalPath);

    optimalPath = info.getCriticalPath(DEFAULT_FILTER);
    info.analyzeCriticalPath(DEFAULT_FILTER, optimalPath);

    if (totalPath == null || totalPath.isComponent()) {
      this.workerWaitTime = 0;
      this.mainThreadWaitTime = 0;
      criticalPathDurations = Collections.emptyList();
      return;
    }
    // Worker thread pool scheduling delays for the actual critical path.
    long workerWaitTime = 0;
    long mainThreadWaitTime = 0;
    for (CriticalPathEntry entry = totalPath; entry != null; entry = entry.next) {
      workerWaitTime += info.getActionWaitTime(entry.task);
      mainThreadWaitTime += info.getActionQueueTime(entry.task);
    }
    this.workerWaitTime = workerWaitTime;
    this.mainThreadWaitTime = mainThreadWaitTime;

    criticalPathDurations = getCriticalPathDurations(info);
  }

  /**
   * @return the critical path obtained by not filtering out any {@link ProfilerTask}
   */
  public CriticalPathEntry getTotalPath() {
    return totalPath;
  }

  /**
   * @return the critical path obtained by filtering out any lock and wait tasks (see
   *    {@link #DEFAULT_FILTER})
   */
  public CriticalPathEntry getOptimalPath() {
    return optimalPath;
  }

  /**
   * @see ProfileInfo#getActionWaitTime(Task)
   * @return the sum of all action wait times on the total critical path
   */
  public long getWorkerWaitTime() {
    return workerWaitTime;
  }

  /**
   * @see ProfileInfo#getActionQueueTime(Task)
   * @return the mainThreadWaitTime
   */
  public long getMainThreadWaitTime() {
    return mainThreadWaitTime;
  }

  @Override
  public Iterator<Pair<String, Double>> iterator() {
    return new Iterator<Pair<String, Double>>() {

      Iterator<Long> durations = criticalPathDurations.iterator();
      Iterator<Pair<String, EnumSet<ProfilerTask>>> filters = FILTERS.iterator();
      boolean overheadFilter = true;

      @Override
      public boolean hasNext() {
        return durations.hasNext();
      }

      @Override
      public Pair<String, Double> next() {
        long duration = durations.next();
        String description = filters.next().first;
        double relativeDuration;
        if (overheadFilter) {
          overheadFilter = false;
          relativeDuration = (double) duration / totalPath.cumulativeDuration;
        } else {
          relativeDuration =
              (double) (optimalPath.cumulativeDuration - duration) / optimalPath.cumulativeDuration;
        }
        return Pair.of(description, relativeDuration);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Extract all {@link CriticalPathEntry} durations for the filters defined by {@link #FILTERS}.
   */
  private static List<Long> getCriticalPathDurations(ProfileInfo info) {
    List<Long> list = new ArrayList<>(FILTERS.size());

    for (Pair<String, EnumSet<ProfilerTask>> filter : FILTERS) {
      list.add(info.getCriticalPath(filter.second).cumulativeDuration);
    }
    return list;
  }

  /**
   * Returns set of profiler tasks to be filtered from critical path.
   * Also always filters out ACTION_LOCK and WAIT tasks to simulate
   * unlimited resource critical path (see comments inside formatExecutionPhaseStatistics()
   * method).
   */
  private static Pair<String, EnumSet<ProfilerTask>> typeFilter(
      String description, ProfilerTask... tasks) {
    EnumSet<ProfilerTask> filter = EnumSet.of(ProfilerTask.ACTION_LOCK, ProfilerTask.WAIT);
    Collections.addAll(filter, tasks);
    return Pair.of(description, filter);
  }
}

