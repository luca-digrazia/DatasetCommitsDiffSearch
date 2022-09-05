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
package com.google.devtools.build.lib.query2;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.TargetParsingException;
import com.google.devtools.build.lib.events.ErrorSensingEventHandler;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.packages.DependencyFilter;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.profiler.AutoProfiler;
import com.google.devtools.build.lib.query2.engine.OutputFormatterCallback;
import com.google.devtools.build.lib.query2.engine.QueryEnvironment;
import com.google.devtools.build.lib.query2.engine.QueryEvalResult;
import com.google.devtools.build.lib.query2.engine.QueryException;
import com.google.devtools.build.lib.query2.engine.QueryExpression;
import com.google.devtools.build.lib.query2.engine.QueryExpressionEvalListener;
import com.google.devtools.build.lib.query2.engine.QueryUtil;
import com.google.devtools.build.lib.query2.engine.QueryUtil.AggregateAllCallback;
import com.google.devtools.build.lib.query2.engine.ThreadSafeCallback;
import com.google.devtools.build.lib.query2.engine.VariableContext;
import com.google.devtools.build.lib.util.Preconditions;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * {@link QueryEnvironment} that can evaluate queries to produce a result, and implements as much of
 * QueryEnvironment as possible while remaining mostly agnostic as to the objects being stored.
 */
public abstract class AbstractBlazeQueryEnvironment<T>
    implements QueryEnvironment<T> {
  protected ErrorSensingEventHandler eventHandler;
  protected final boolean keepGoing;
  protected final boolean strictScope;

  protected final DependencyFilter dependencyFilter;
  private final Predicate<Label> labelFilter;

  protected final Set<Setting> settings;
  protected final List<QueryFunction> extraFunctions;
  private final QueryExpressionEvalListener<T> evalListener;

  private static final Logger LOG = Logger.getLogger(AbstractBlazeQueryEnvironment.class.getName());

  protected AbstractBlazeQueryEnvironment(boolean keepGoing,
      boolean strictScope,
      Predicate<Label> labelFilter,
      EventHandler eventHandler,
      Set<Setting> settings,
      Iterable<QueryFunction> extraFunctions,
      QueryExpressionEvalListener<T> evalListener) {
    this.eventHandler = new ErrorSensingEventHandler(eventHandler);
    this.keepGoing = keepGoing;
    this.strictScope = strictScope;
    this.dependencyFilter = constructDependencyFilter(settings);
    this.labelFilter = labelFilter;
    this.settings = Sets.immutableEnumSet(settings);
    this.extraFunctions = ImmutableList.copyOf(extraFunctions);
    this.evalListener = evalListener;
  }

  private static DependencyFilter constructDependencyFilter(
      Set<Setting> settings) {
    DependencyFilter specifiedFilter =
        settings.contains(Setting.NO_HOST_DEPS)
            ? DependencyFilter.NO_HOST_DEPS
            : DependencyFilter.ALL_DEPS;
    if (settings.contains(Setting.NO_IMPLICIT_DEPS)) {
      specifiedFilter = DependencyFilter.and(specifiedFilter, DependencyFilter.NO_IMPLICIT_DEPS);
    }
    if (settings.contains(Setting.NO_NODEP_DEPS)) {
      specifiedFilter = DependencyFilter.and(specifiedFilter, DependencyFilter.NO_NODEP_ATTRIBUTES);
    }
    return specifiedFilter;
  }

  /**
   * Used by {@link #evaluateQuery} to evaluate the given {@code expr}. The caller,
   * {@link #evaluateQuery}, not {@link #evalTopLevelInternal}, is responsible for managing
   * {@code callback}.
   */
  protected void evalTopLevelInternal(QueryExpression expr, OutputFormatterCallback<T> callback)
      throws QueryException, InterruptedException {
    eval(expr, VariableContext.<T>empty(), callback);
  }

  /**
   * Evaluate the specified query expression in this environment, streaming results to the given
   * {@code callback}. {@code callback.start()} will be called before query evaluation and
   * {@code callback.close()} will be unconditionally called at the end of query evaluation
   * (i.e. regardless of whether it was successful).
   *
   * @return a {@link QueryEvalResult} object that contains the resulting set of targets and a bit
   *   to indicate whether errors occurred during evaluation; note that the
   *   success status can only be false if {@code --keep_going} was in effect
   * @throws QueryException if the evaluation failed and {@code --nokeep_going} was in
   *   effect
   */
  public QueryEvalResult evaluateQuery(
      QueryExpression expr,
      final OutputFormatterCallback<T> callback)
          throws QueryException, InterruptedException, IOException {
    EmptinessSensingCallback<T> emptySensingCallback = createEmptinessSensingCallback(callback);
    try (final AutoProfiler p = AutoProfiler.logged("evaluating query", LOG)) {
      // In the --nokeep_going case, errors are reported in the order in which the patterns are
      // specified; using a linked hash set here makes sure that the left-most error is reported.
      Set<String> targetPatternSet = new LinkedHashSet<>();
      expr.collectTargetPatterns(targetPatternSet);
      try {
        preloadOrThrow(expr, targetPatternSet);
      } catch (TargetParsingException e) {
        // Unfortunately, by evaluating the patterns in parallel, we lose some location information.
        throw new QueryException(expr, e.getMessage());
      }
      IOException ioExn = null;
      try {
        callback.start();
        evalTopLevelInternal(expr, emptySensingCallback);
      } catch (QueryException e) {
        throw new QueryException(e, expr);
      } catch (InterruptedException e) {
        throw e;
      } finally {
        try {
          callback.close();
        } catch (IOException e) {
          // Only throw this IOException if we weren't about to throw a different exception.
          ioExn = e;
        }
      }
      if (ioExn != null) {
        throw ioExn;
      }
    }

    if (eventHandler.hasErrors()) {
      if (!keepGoing) {
        // This case represents loading-phase errors reported during evaluation
        // of target patterns that don't cause evaluation to fail per se.
        throw new QueryException("Evaluation of query \"" + expr
            + "\" failed due to BUILD file errors");
      } else {
        eventHandler.handle(Event.warn("--keep_going specified, ignoring errors.  "
            + "Results may be inaccurate"));
      }
    }

    return new QueryEvalResult(!eventHandler.hasErrors(), emptySensingCallback.isEmpty());
  }

  private static <T> EmptinessSensingCallback<T> createEmptinessSensingCallback(
      OutputFormatterCallback<T> callback) {
    return (callback instanceof ThreadSafeCallback)
        ? new ThreadSafeEmptinessSensingCallback<>(callback)
        : new EmptinessSensingCallback<>(callback);
  }

  private static class EmptinessSensingCallback<T> extends OutputFormatterCallback<T> {
    private final OutputFormatterCallback<T> callback;
    private final AtomicBoolean empty = new AtomicBoolean(true);

    private EmptinessSensingCallback(OutputFormatterCallback<T> callback) {
      this.callback = callback;
    }

    @Override
    public void start() throws IOException {
      callback.start();
    }

    @Override
    public void processOutput(Iterable<T> partialResult)
        throws IOException, InterruptedException {
      empty.compareAndSet(true, Iterables.isEmpty(partialResult));
      callback.processOutput(partialResult);
    }

    @Override
    public void close() throws InterruptedException, IOException {
      callback.close();
    }

    boolean isEmpty() {
      return empty.get();
    }
  }

  private static class ThreadSafeEmptinessSensingCallback<T>
      extends EmptinessSensingCallback<T> implements ThreadSafeCallback<T> {
    private ThreadSafeEmptinessSensingCallback(OutputFormatterCallback<T> callback) {
      super(callback);
      Preconditions.checkState(callback instanceof ThreadSafeCallback);
    }
  }

  public QueryExpression transformParsedQuery(QueryExpression queryExpression) {
    return queryExpression;
  }

  public QueryEvalResult evaluateQuery(String query, OutputFormatterCallback<T> callback)
      throws QueryException, InterruptedException, IOException {
    return evaluateQuery(
        QueryExpression.parse(query, this), callback);
  }

  @Override
  public void reportBuildFileError(QueryExpression caller, String message) throws QueryException {
    if (!keepGoing) {
      throw new QueryException(caller, message);
    } else {
      // Keep consistent with evaluateQuery() above.
      eventHandler.handle(Event.error("Evaluation of query \"" + caller + "\" failed: " + message));
    }
  }

  public abstract Target getTarget(Label label)
      throws TargetNotFoundException, QueryException, InterruptedException;

  protected boolean validateScope(Label label, boolean strict) throws QueryException {
    if (!labelFilter.apply(label)) {
      String error = String.format("target '%s' is not within the scope of the query", label);
      if (strict) {
        throw new QueryException(error);
      } else {
        eventHandler.handle(Event.warn(error + ". Skipping"));
        return false;
      }
    }
    return true;
  }

  public Set<T> evalTargetPattern(QueryExpression caller, String pattern)
      throws QueryException, InterruptedException {
    try {
      preloadOrThrow(caller, ImmutableList.of(pattern));
    } catch (TargetParsingException e) {
      // Will skip the target and keep going if -k is specified.
      reportBuildFileError(caller, e.getMessage());
    }
    AggregateAllCallback<T> aggregatingCallback = QueryUtil.newAggregateAllCallback();
    getTargetsMatchingPattern(caller, pattern, aggregatingCallback);
    return aggregatingCallback.getResult();
  }

  /**
   * Perform any work that should be done ahead of time to resolve the target patterns in the query.
   * Implementations may choose to cache the results of resolving the patterns, cache intermediate
   * work, or not cache and resolve patterns on the fly.
   */
  protected abstract void preloadOrThrow(QueryExpression caller, Collection<String> patterns)
      throws QueryException, TargetParsingException, InterruptedException;

  @Override
  public boolean isSettingEnabled(Setting setting) {
    return settings.contains(Preconditions.checkNotNull(setting));
  }

  @Override
  public Iterable<QueryFunction> getFunctions() {
    ImmutableList.Builder<QueryFunction> builder = ImmutableList.builder();
    builder.addAll(DEFAULT_QUERY_FUNCTIONS);
    builder.addAll(extraFunctions);
    return builder.build();
  }

  @Override
  public QueryExpressionEvalListener<T> getEvalListener() {
    return evalListener;
  }
}
