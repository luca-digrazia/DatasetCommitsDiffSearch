// Copyright 2014 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.skyframe;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.ResolvedTargets;
import com.google.devtools.build.lib.cmdline.TargetParsingException;
import com.google.devtools.build.lib.cmdline.TargetPattern;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.ExtendedEventHandler;
import com.google.devtools.build.lib.packages.Package;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.pkgcache.FilteringPolicies;
import com.google.devtools.build.lib.pkgcache.ParsingFailedEvent;
import com.google.devtools.build.lib.pkgcache.RecursivePackageProvider.PackageBackedRecursivePackageProvider;
import com.google.devtools.build.lib.pkgcache.TargetPatternPreloader;
import com.google.devtools.build.lib.skyframe.TargetPatternValue.TargetPatternKey;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.skyframe.ErrorInfo;
import com.google.devtools.build.skyframe.EvaluationResult;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import com.google.devtools.build.skyframe.WalkableGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/** Skyframe-based target pattern parsing. */
final class SkyframeTargetPatternEvaluator implements TargetPatternPreloader {
  private final SkyframeExecutor<?> skyframeExecutor;

  SkyframeTargetPatternEvaluator(SkyframeExecutor<?> skyframeExecutor) {
    this.skyframeExecutor = skyframeExecutor;
  }

  @Override
  public Map<String, ResolvedTargets<Target>> preloadTargetPatterns(
      ExtendedEventHandler eventHandler,
      PathFragment relativeWorkingDirectory,
      Collection<String> patterns,
      boolean keepGoing,
      boolean useForkJoinPool)
      throws TargetParsingException, InterruptedException {
    String offset = relativeWorkingDirectory.getPathString();
    ImmutableMap.Builder<String, ResolvedTargets<Target>> resultBuilder = ImmutableMap.builder();
    List<PatternLookup> patternLookups = new ArrayList<>();
    List<SkyKey> allKeys = new ArrayList<>();
    for (String pattern : patterns) {
      PatternLookup patternLookup = createPatternLookup(offset, eventHandler, pattern, keepGoing);
      if (patternLookup == null) {
        resultBuilder.put(pattern, ResolvedTargets.failed());
      } else {
        patternLookups.add(patternLookup);
        allKeys.add(patternLookup.skyKey);
      }
    }

    EvaluationResult<SkyValue> result =
        skyframeExecutor.targetPatterns(
            allKeys,
            SkyframeExecutor.DEFAULT_THREAD_COUNT,
            keepGoing,
            eventHandler,
            useForkJoinPool);
    WalkableGraph walkableGraph = Preconditions.checkNotNull(result.getWalkableGraph(), result);
    for (PatternLookup patternLookup : patternLookups) {
      SkyKey key = patternLookup.skyKey;
      SkyValue resultValue = result.get(key);
      if (resultValue != null) {
        try {
          ResolvedTargets<Target> resolvedTargets =
              patternLookup.process(eventHandler, resultValue, walkableGraph, keepGoing);
          resultBuilder.put(patternLookup.pattern, resolvedTargets);
        } catch (TargetParsingException e) {
          if (!keepGoing) {
            throw e;
          }
          eventHandler.handle(
              Event.error("Skipping '" + patternLookup.pattern + "': " + e.getMessage()));
          eventHandler.post(PatternExpandingError.skipped(patternLookup.pattern, e.getMessage()));
          resultBuilder.put(patternLookup.pattern, ResolvedTargets.failed());
        }
      } else {
        String rawPattern = patternLookup.pattern;
        ErrorInfo error = result.errorMap().get(key);
        if (error == null) {
          Preconditions.checkState(!keepGoing);
          continue;
        }
        String errorMessage;
        if (error.getException() != null) {
          // This exception could be a TargetParsingException, a NoSuchPackageException, or
          // potentially a lower-level exception.
          errorMessage = error.getException().getMessage();
        } else if (!error.getCycleInfo().isEmpty()) {
          errorMessage = "cycles detected during target parsing";
          skyframeExecutor
              .getCyclesReporter()
              .reportCycles(error.getCycleInfo(), key, eventHandler);
        } else {
          throw new IllegalStateException(error.toString());
        }
        if (keepGoing) {
          eventHandler.handle(Event.error("Skipping '" + rawPattern + "': " + errorMessage));
          eventHandler.post(PatternExpandingError.skipped(rawPattern, errorMessage));
        } else {
          eventHandler.post(PatternExpandingError.failed(patternLookup.pattern, errorMessage));
          throw new TargetParsingException(errorMessage);
        }
        resultBuilder.put(patternLookup.pattern, ResolvedTargets.failed());
      }
    }
    return resultBuilder.build();
  }

  private PatternLookup createPatternLookup(
      String offset, ExtendedEventHandler eventHandler, String targetPattern, boolean keepGoing)
      throws TargetParsingException {
    try {
      TargetPatternKey key =
          TargetPatternValue.key(targetPattern, FilteringPolicies.NO_FILTER, offset);
      return isSimple(key.getParsedPattern())
          ? new SimpleLookup(targetPattern, key)
          : new NormalLookup(targetPattern, key);
    } catch (TargetParsingException e) {
      // We report a parsing failed exception to the event bus here in case the pattern did not
      // successfully parse (which happens before the SkyKey is created). Otherwise the
      // TargetPatternFunction posts the event.
      eventHandler.post(new ParsingFailedEvent(targetPattern, e.getMessage()));
      if (!keepGoing) {
        throw e;
      }
      eventHandler.handle(Event.error("Skipping '" + targetPattern + "': " + e.getMessage()));
      return null;
    }
  }

  /** Returns true for patterns that can be resolved from a single PackageValue. */
  private static boolean isSimple(TargetPattern targetPattern) {
    switch (targetPattern.getType()) {
      case SINGLE_TARGET:
      case TARGETS_IN_PACKAGE:
        return true;
      case PATH_AS_TARGET:
      case TARGETS_BELOW_DIRECTORY:
        // Both of these require multiple package lookups. PATH_AS_TARGET needs to find the
        // enclosing package, and TARGETS_BELOW_DIRECTORY recursively looks for all packages under a
        // specified directory.
        return false;
    }
    throw new AssertionError();
  }

  private abstract static class PatternLookup {
    protected final String pattern;
    @Nullable private final SkyKey skyKey;

    private PatternLookup(String pattern, SkyKey skyKey) {
      this.pattern = pattern;
      this.skyKey = skyKey;
    }

    public abstract ResolvedTargets<Target> process(
        ExtendedEventHandler eventHandler,
        SkyValue value,
        WalkableGraph walkableGraph,
        boolean keepGoing)
        throws InterruptedException, TargetParsingException;
  }

  private static class NormalLookup extends PatternLookup {
    private final TargetPatternKey key;
    private final TargetPatternsResultBuilder resultBuilder;

    private NormalLookup(String targetPattern, TargetPatternKey key) {
      super(targetPattern, key);
      this.key = key;
      this.resultBuilder = new TargetPatternsResultBuilder();
    }

    @Override
    public ResolvedTargets<Target> process(
        ExtendedEventHandler eventHandler,
        SkyValue value,
        WalkableGraph walkableGraph,
        boolean keepGoing)
        throws InterruptedException {
      TargetPatternValue resultValue = (TargetPatternValue) value;
      ResolvedTargets<Label> results = resultValue.getTargets();
      if (key.isNegative()) {
        resultBuilder.addLabelsOfNegativePattern(results);
      } else {
        resultBuilder.addLabelsOfPositivePattern(results);
      }
      return resultBuilder.build(walkableGraph);
    }
  }

  private static class SimpleLookup extends PatternLookup {
    private final TargetPattern targetPattern;

    private SimpleLookup(String pattern, TargetPatternKey key) {
      this(
          pattern,
          PackageValue.key(key.getParsedPattern().getDirectoryForTargetOrTargetsInPackage()),
          key.getParsedPattern());
    }

    private SimpleLookup(String pattern, PackageValue.Key key, TargetPattern targetPattern) {
      super(pattern, key);
      this.targetPattern = targetPattern;
    }

    @Override
    public ResolvedTargets<Target> process(
        ExtendedEventHandler eventHandler,
        SkyValue value,
        WalkableGraph walkableGraph,
        boolean keepGoing)
        throws InterruptedException, TargetParsingException {
      Package pkg = ((PackageValue) value).getPackage();
      RecursivePackageProviderBackedTargetPatternResolver resolver =
          new RecursivePackageProviderBackedTargetPatternResolver(
              new PackageBackedRecursivePackageProvider(
                  ImmutableMap.of(pkg.getPackageIdentifier(), pkg)),
              eventHandler,
              FilteringPolicies.NO_FILTER,
              /* packageSemaphore= */ null);
      AtomicReference<ResolvedTargets<Target>> result = new AtomicReference<>();
      targetPattern.eval(
          resolver,
          /*blacklistedSubdirectories=*/ ImmutableSet.<PathFragment>of(),
          /*excludedSubdirectories=*/ ImmutableSet.<PathFragment>of(),
          partialResult ->
              result.set(
                  new ResolvedTargets<>(ImmutableSet.copyOf(partialResult), /*hasError=*/ false)),
          TargetParsingException.class);
      return result.get();
    }
  }
}
