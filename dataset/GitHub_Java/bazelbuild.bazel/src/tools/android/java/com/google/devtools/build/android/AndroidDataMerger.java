// Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.build.android.AndroidResourceMerger.MergingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Handles the Merging of ParsedAndroidData. */
class AndroidDataMerger {

  public static class MergeConflictException extends RuntimeException {

    private MergeConflictException(String message) {
      super(message);
    }

    static MergeConflictException withMessage(String message) {
      return new MergeConflictException(message);
    }
  }

  private static final Logger logger = Logger.getLogger(AndroidDataMerger.class.getCanonicalName());

  /** Interface for comparing paths. */
  interface SourceChecker {
    boolean checkEquality(DataSource one, DataSource two) throws IOException;
  }

  /** Compares two paths by the contents of the files. */
  static class ContentComparingChecker implements SourceChecker {

    static SourceChecker create() {
      return new ContentComparingChecker();
    }

    @Override
    public boolean checkEquality(DataSource one, DataSource two) throws IOException {
      // TODO(corysmith): Is there a filesystem hash we can use?
      if (one.getFileSize() != two.getFileSize()) {
        return false;
      }
      try (final InputStream oneStream = one.newBufferedInputStream();
          final InputStream twoStream = two.newBufferedInputStream()) {
        int bytesRead = 0;
        while (true) {
          int oneByte = oneStream.read();
          int twoByte = twoStream.read();
          bytesRead++;
          if (oneByte == -1 || twoByte == -1) {
            if (oneByte == twoByte) {
              return true;
            } else {
              // getFileSize did not return correct size.
              logger.severe(
                  String.format(
                      "Filesystem size of %s (%s) or %s (%s) is inconsistent with bytes read %s.",
                      one, one.getFileSize(), two, two.getFileSize(), bytesRead));
              return false;
            }
          }
          if (oneByte != twoByte) {
            return false;
          }
        }
      }
    }
  }

  static class NoopSourceChecker implements SourceChecker {
    static SourceChecker create() {
      return new NoopSourceChecker();
    }

    @Override
    public boolean checkEquality(DataSource one, DataSource two) {
      return false;
    }
  }

  private final SourceChecker deDuplicator;
  private final ListeningExecutorService executorService;
  private final AndroidDataDeserializer deserializer;

  /** Creates a merger with no path deduplication and a default {@link ExecutorService}. */
  @VisibleForTesting
  static AndroidDataMerger createWithDefaults() {
    return createWithDefaultThreadPool(NoopSourceChecker.create());
  }

  /** Creates a merger with a custom deduplicator and a default {@link ExecutorService}. */
  @VisibleForTesting
  static AndroidDataMerger createWithDefaultThreadPool(SourceChecker deDuplicator) {
    return new AndroidDataMerger(
        deDuplicator, MoreExecutors.newDirectExecutorService(), AndroidDataDeserializer.create());
  }

  /** Creates a merger with a file contents hashing deduplicator. */
  static AndroidDataMerger createWithPathDeduplictor(
      ListeningExecutorService executorService, AndroidDataDeserializer deserializer) {
    return new AndroidDataMerger(ContentComparingChecker.create(), executorService, deserializer);
  }

  private AndroidDataMerger(
      SourceChecker deDuplicator,
      ListeningExecutorService executorService,
      AndroidDataDeserializer deserializer) {
    this.deDuplicator = deDuplicator;
    this.executorService = executorService;
    this.deserializer = deserializer;
  }

  /**
   * Loads a list of dependency {@link SerializedAndroidData} and merge with the primary {@link
   * ParsedAndroidData}.
   *
   * @see AndroidDataMerger#merge(ParsedAndroidData, ParsedAndroidData, UnvalidatedAndroidData,
   *     boolean, boolean) for details.
   */
  UnwrittenMergedAndroidData loadAndMerge(
      List<? extends SerializedAndroidData> transitive,
      List<? extends SerializedAndroidData> direct,
      ParsedAndroidData primary,
      Path primaryManifest,
      boolean allowPrimaryOverrideAll,
      boolean throwOnResourceConflict) {
    Stopwatch timer = Stopwatch.createStarted();
    try {
      logger.fine(
          String.format("Merged dependencies read in %sms", timer.elapsed(TimeUnit.MILLISECONDS)));
      timer.reset().start();
      return doMerge(
          ParsedAndroidData.loadedFrom(transitive, executorService, deserializer),
          ParsedAndroidData.loadedFrom(direct, executorService, deserializer),
          primary,
          primaryManifest,
          allowPrimaryOverrideAll,
          throwOnResourceConflict);
    } finally {
      logger.fine(String.format("Resources merged in %sms", timer.elapsed(TimeUnit.MILLISECONDS)));
    }
  }

  /**
   * Merges DataResources into an UnwrittenMergedAndroidData.
   *
   * <p>This method has two basic states, library and binary. These are distinguished by
   * allowPrimaryOverrideAll, which allows the primary data to overwrite any value in the closure, a
   * trait associated with binaries, as a binary is a leaf node. The other semantics are slightly
   * more complicated: a given resource can be overwritten only if it resides in the direct
   * dependencies of primary data. This forces an explicit simple priority for each resource,
   * instead of the more subtle semantics of multiple layers of libraries with potential overwrites.
   *
   * <p>The UnwrittenMergedAndroidData contains only one of each DataKey in both the direct and
   * transitive closure.
   *
   * <p>The merge semantics for overwriting resources (non id and styleable) are as follows:
   *
   * <pre>
   *   Key:
   *     A(): package A
   *     A(foo): package A with resource symbol foo
   *     A() -> B(): a dependency relationship of B.deps = [:A]
   *     A(),B() -> C(): a dependency relationship of C.deps = [:A,:B]
   *
   *   For android library (allowPrimaryOverrideAll = False)
   *
   *     A() -> B(foo) -> C(foo) == Valid
   *     A() -> B() -> C(foo) == Valid
   *     A() -> B() -> C(foo),D(foo) == Conflict
   *     A(foo) -> B(foo) -> C() == Conflict
   *     A(foo) -> B() -> C(foo) == Conflict
   *     A(foo),B(foo) -> C() -> D() == Conflict
   *     A() -> B(foo),C(foo) -> D() == Conflict
   *     A(foo),B(foo) -> C() -> D(foo) == Conflict
   *     A() -> B(foo),C(foo) -> D(foo) == Conflict
   *
   *   For android binary (allowPrimaryOverrideAll = True)
   *
   *     A() -> B(foo) -> C(foo) == Valid
   *     A() -> B() -> C(foo) == Valid
   *     A() -> B() -> C(foo),D(foo) == Conflict
   *     A(foo) -> B(foo) -> C() == Conflict
   *     A(foo) -> B() -> C(foo) == Valid
   *     A(foo),B(foo) -> C() -> D() == Conflict
   *     A() -> B(foo),C(foo) -> D() == Conflict
   *     A(foo),B(foo) -> C() -> D(foo) == Valid
   *     A() -> B(foo),C(foo) -> D(foo) == Valid
   * </pre>
   *
   * <p>Combining resources are much simpler -- since a combining (id and styleable) resource does
   * not get replaced when redefined, they are simply combined:
   *
   * <pre>
   *     A(foo) -> B(foo) -> C(foo) == Valid
   *
   * </pre>
   *
   * @param transitive The transitive dependencies to merge.
   * @param direct The direct dependencies to merge.
   * @param primaryData The primary data to merge against.
   * @param allowPrimaryOverrideAll Boolean that indicates if the primary data will be considered
   *     the ultimate source of truth, provided it doesn't conflict with itself.
   * @return An UnwrittenMergedAndroidData, containing DataResource objects that can be written to
   *     disk for aapt processing or serialized for future merge passes.
   * @throws MergingException if there are issues with parsing resources from primaryData.
   * @throws MergeConflictException if there are merge conflicts
   */
  UnwrittenMergedAndroidData merge(
      ParsedAndroidData transitive,
      ParsedAndroidData direct,
      UnvalidatedAndroidData primaryData,
      boolean allowPrimaryOverrideAll,
      boolean throwOnResourceConflict) {
    try {
      // Extract the primary resources.
      ParsedAndroidData parsedPrimary = ParsedAndroidData.from(primaryData);
      return doMerge(
          transitive,
          direct,
          parsedPrimary,
          primaryData.getManifest(),
          allowPrimaryOverrideAll,
          throwOnResourceConflict);
    } catch (IOException e) {
      throw MergingException.wrapException(e);
    }
  }

  private UnwrittenMergedAndroidData doMerge(
      ParsedAndroidData transitive,
      ParsedAndroidData direct,
      ParsedAndroidData primary,
      Path primaryManifest,
      boolean allowPrimaryOverrideAll,
      boolean throwOnResourceConflict) {
    try {
      // Create the builders for the final parsed data.
      ParsedAndroidData mergedPrimary =
          primary
              .overwrite(direct, false)
              .combine(direct)
              .overwrite(transitive, !allowPrimaryOverrideAll)
              .combine(transitive);
      // Filter out all the resources that are in the primary, as they only need to be written once.
      // This also removes conflicts that have keys in the primary -- those conflicts are recorded
      // in the overwrite steps above.
      ParsedAndroidData mergedTransitive = direct.union(transitive).filterBy(mergedPrimary);

      Set<MergeConflict> conflicts =
          Sets.union(mergedPrimary.conflicts(), mergedTransitive.conflicts());

      if (!conflicts.isEmpty()) {
        List<String> messages = new ArrayList<>();
        for (MergeConflict conflict : conflicts) {
          if (conflict.isValidWith(deDuplicator)) {
            messages.add(conflict.toConflictMessage());
          }
        }
        if (!messages.isEmpty()) {
          String conflictMessage = Joiner.on("").join(messages);
          if (throwOnResourceConflict) {
            throw MergeConflictException.withMessage(conflictMessage);
          }
          logger.warning(conflictMessage);
        }
      }
      return UnwrittenMergedAndroidData.of(primaryManifest, mergedPrimary, mergedTransitive);
    } catch (IOException e) {
      throw MergingException.wrapException(e);
    }
  }
}
