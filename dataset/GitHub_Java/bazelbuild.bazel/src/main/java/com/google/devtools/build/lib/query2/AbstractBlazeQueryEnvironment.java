// Copyright 2015 Google Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.cmdline.ResolvedTargets;
import com.google.devtools.build.lib.cmdline.TargetParsingException;
import com.google.devtools.build.lib.events.ErrorSensingEventHandler;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.pkgcache.PackageProvider;
import com.google.devtools.build.lib.pkgcache.TargetPatternEvaluator;
import com.google.devtools.build.lib.pkgcache.TransitivePackageLoader;
import com.google.devtools.build.lib.query2.engine.QueryEnvironment;
import com.google.devtools.build.lib.query2.engine.QueryEvalResult;
import com.google.devtools.build.lib.query2.engine.QueryException;
import com.google.devtools.build.lib.query2.engine.QueryExpression;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.util.BinaryPredicate;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link QueryEnvironment} that can evaluate queries to produce a result, and implements as much
 * of QueryEnvironment as possible while remaining mostly agnostic as to the objects being stored.
 */
public abstract class AbstractBlazeQueryEnvironment<T> implements QueryEnvironment<T> {
  protected final ErrorSensingEventHandler eventHandler;
  private final TargetPatternEvaluator targetPatternEvaluator;
  private final Map<String, Set<T>> letBindings = new HashMap<>();
  protected final Map<String, ResolvedTargets<Target>> resolvedTargetPatterns = new HashMap<>();
  protected final boolean keepGoing;
  protected final boolean strictScope;

  protected final BinaryPredicate<Rule, Attribute> dependencyFilter;
  private final Predicate<Label> labelFilter;

  private final Set<Setting> settings;
  private final List<QueryFunction> extraFunctions;

  protected AbstractBlazeQueryEnvironment(TargetPatternEvaluator targetPatternEvaluator,
      boolean keepGoing,
      boolean strictScope,
      Predicate<Label> labelFilter,
      EventHandler eventHandler,
      Set<Setting> settings,
      Iterable<QueryFunction> extraFunctions) {
    this.eventHandler = new ErrorSensingEventHandler(eventHandler);
    this.targetPatternEvaluator = targetPatternEvaluator;
    this.keepGoing = keepGoing;
    this.strictScope = strictScope;
    this.dependencyFilter = constructDependencyFilter(settings);
    this.labelFilter = labelFilter;
    this.settings = Sets.immutableEnumSet(settings);
    this.extraFunctions = ImmutableList.copyOf(extraFunctions);
  }

  private static BinaryPredicate<Rule, Attribute> constructDependencyFilter(Set<Setting> settings) {
    BinaryPredicate<Rule, Attribute> specifiedFilter =
        settings.contains(Setting.NO_HOST_DEPS) ? Rule.NO_HOST_DEPS : Rule.ALL_DEPS;
    if (settings.contains(Setting.NO_IMPLICIT_DEPS)) {
      specifiedFilter = Rule.and(specifiedFilter, Rule.NO_IMPLICIT_DEPS);
    }
    if (settings.contains(Setting.NO_NODEP_DEPS)) {
      specifiedFilter = Rule.and(specifiedFilter, Rule.NO_NODEP_ATTRIBUTES);
    }
    return specifiedFilter;
  }

  public static AbstractBlazeQueryEnvironment<Target> newQueryEnvironment(
      TransitivePackageLoader transitivePackageLoader, PackageProvider packageProvider,
      TargetPatternEvaluator targetPatternEvaluator, boolean keepGoing, boolean orderedResults,
      int loadingPhaseThreads,
      EventHandler eventHandler, Set<Setting> settings, Iterable<QueryFunction> functions) {
    return newQueryEnvironment(transitivePackageLoader, packageProvider,
        targetPatternEvaluator, keepGoing, /*strictScope=*/true, orderedResults,
        loadingPhaseThreads, Rule.ALL_LABELS, eventHandler, settings, functions);
  }

  public static AbstractBlazeQueryEnvironment<Target> newQueryEnvironment(
      TransitivePackageLoader transitivePackageLoader, PackageProvider packageProvider,
      TargetPatternEvaluator targetPatternEvaluator, boolean keepGoing, boolean strictScope,
      boolean orderedResults, int loadingPhaseThreads, Predicate<Label> labelFilter,
      EventHandler eventHandler, Set<Setting> settings, Iterable<QueryFunction> functions) {
    return new BlazeQueryEnvironment(transitivePackageLoader, packageProvider,
        targetPatternEvaluator, keepGoing, strictScope, loadingPhaseThreads,
        labelFilter, eventHandler, settings, functions);
  }

  /**
   * Evaluate the specified query expression in this environment.
   *
   * @return a {@link QueryEvalResult} object that contains the resulting set of targets and a bit
   *   to indicate whether errors occured during evaluation; note that the
   *   success status can only be false if {@code --keep_going} was in effect
   * @throws QueryException if the evaluation failed and {@code --nokeep_going} was in
   *   effect
   */
  public QueryEvalResult<T> evaluateQuery(QueryExpression expr) throws QueryException {
    // Some errors are reported as QueryExceptions and others as ERROR events
    // (if --keep_going).
    eventHandler.resetErrors();
    resolvedTargetPatterns.clear();

    // In the --nokeep_going case, errors are reported in the order in which the patterns are
    // specified; using a linked hash set here makes sure that the left-most error is reported.
    Set<String> targetPatternSet = new LinkedHashSet<>();
    expr.collectTargetPatterns(targetPatternSet);
    try {
      resolvedTargetPatterns.putAll(preloadOrThrow(targetPatternSet));
    } catch (TargetParsingException e) {
      // Unfortunately, by evaluating the patterns in parallel, we lose some location information.
      throw new QueryException(expr, e.getMessage());
    }

    Set<T> resultNodes;
    try {
      resultNodes = expr.eval(this);
    } catch (QueryException e) {
      throw new QueryException(e, expr);
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

    return new QueryEvalResult<>(!eventHandler.hasErrors(), resultNodes);
  }

  public QueryEvalResult<T> evaluateQuery(String query) throws QueryException {
    return evaluateQuery(QueryExpression.parse(query, this));
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

  public abstract Target getTarget(Label label) throws TargetNotFoundException, QueryException;

  @Override
  public Set<T> getVariable(String name) {
    return letBindings.get(name);
  }

  @Override
  public Set<T> setVariable(String name, Set<T> value) {
    return letBindings.put(name, value);
  }

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
      throws QueryException {
    if (!resolvedTargetPatterns.containsKey(pattern)) {
      try {
        resolvedTargetPatterns.putAll(preloadOrThrow(ImmutableList.of(pattern)));
      } catch (TargetParsingException e) {
        // Will skip the target and keep going if -k is specified.
        resolvedTargetPatterns.put(pattern, ResolvedTargets.<Target>empty());
        reportBuildFileError(caller, e.getMessage());
      }
    }
    return getTargetsMatchingPattern(caller, pattern);
  }

  private Map<String, ResolvedTargets<Target>> preloadOrThrow(Collection<String> patterns)
      throws TargetParsingException {
    try {
      // Note that this may throw a RuntimeException if deps are missing in Skyframe and this is
      // being called from within a SkyFunction.
      return targetPatternEvaluator.preloadTargetPatterns(
          eventHandler, patterns, keepGoing);
    } catch (InterruptedException e) {
      // TODO(bazel-team): Propagate the InterruptedException from here [skyframe-loading].
      throw new TargetParsingException("interrupted");
    }
  }

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

}
