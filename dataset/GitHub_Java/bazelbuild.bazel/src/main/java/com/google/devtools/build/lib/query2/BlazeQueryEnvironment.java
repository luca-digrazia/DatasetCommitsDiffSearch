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
package com.google.devtools.build.lib.query2;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.graph.Digraph;
import com.google.devtools.build.lib.graph.Node;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.NoSuchThingException;
import com.google.devtools.build.lib.packages.OutputFile;
import com.google.devtools.build.lib.packages.Package;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.pkgcache.PackageProvider;
import com.google.devtools.build.lib.pkgcache.TargetEdgeObserver;
import com.google.devtools.build.lib.pkgcache.TargetPatternEvaluator;
import com.google.devtools.build.lib.pkgcache.TargetProvider;
import com.google.devtools.build.lib.pkgcache.TransitivePackageLoader;
import com.google.devtools.build.lib.query2.engine.BlazeQueryEvalResult;
import com.google.devtools.build.lib.query2.engine.QueryEvalResult;
import com.google.devtools.build.lib.query2.engine.QueryException;
import com.google.devtools.build.lib.query2.engine.QueryExpression;
import com.google.devtools.build.lib.query2.engine.SkyframeRestartQueryException;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The environment of a Blaze query. Not thread-safe.
 */
public class BlazeQueryEnvironment extends AbstractBlazeQueryEnvironment<Target> {

  private static final int MAX_DEPTH_FULL_SCAN_LIMIT = 20;
  private final TransitivePackageLoader transitivePackageLoader;
  private final TargetProvider targetProvider;
  private final Digraph<Target> graph = new Digraph<>();
  private final ErrorPrintingTargetEdgeErrorObserver errorObserver;
  private final LabelVisitor labelVisitor;
  protected final int loadingPhaseThreads;

  private final BlazeTargetAccessor accessor = new BlazeTargetAccessor(this);

  /**
   * Note that the correct operation of this class critically depends on the Reporter being a
   * singleton object, shared by all cooperating classes contributing to Query.
   * @param strictScope if true, fail the whole query if a label goes out of scope.
   * @param loadingPhaseThreads the number of threads to use during loading
   *     the packages for the query.
   * @param labelFilter a predicate that determines if a specific label is
   *     allowed to be visited during query execution. If it returns false,
   *     the query execution is stopped with an error message.
   * @param settings a set of enabled settings
   */
  BlazeQueryEnvironment(TransitivePackageLoader transitivePackageLoader,
      PackageProvider packageProvider,
      TargetPatternEvaluator targetPatternEvaluator,
      boolean keepGoing,
      boolean strictScope,
      int loadingPhaseThreads,
      Predicate<Label> labelFilter,
      EventHandler eventHandler,
      Set<Setting> settings,
      Iterable<QueryFunction> extraFunctions) {
    super(targetPatternEvaluator, keepGoing, strictScope, labelFilter, eventHandler, settings,
        extraFunctions
    );
    this.transitivePackageLoader = transitivePackageLoader;
    this.targetProvider = packageProvider;
    this.errorObserver = new ErrorPrintingTargetEdgeErrorObserver(this.eventHandler);
    this.loadingPhaseThreads = loadingPhaseThreads;
    this.labelVisitor = new LabelVisitor(packageProvider, dependencyFilter);
  }

  @Override
  public BlazeQueryEvalResult<Target> evaluateQuery(QueryExpression expr) throws QueryException {
    QueryEvalResult<Target> queryEvalResult = super.evaluateQuery(expr);
    return new BlazeQueryEvalResult<>(queryEvalResult.getSuccess(), queryEvalResult.getResultSet(),
        graph);
  }

  @Override
  public Set<Target> getTargetsMatchingPattern(QueryExpression caller,
      String pattern) throws QueryException {
    // We can safely ignore the boolean error flag. The evaluateQuery() method above wraps the
    // entire query computation in an error sensor.

    Set<Target> targets = new LinkedHashSet<>(resolvedTargetPatterns.get(pattern).getTargets());

    // Sets.filter would be more convenient here, but can't deal with exceptions.
    Iterator<Target> targetIterator = targets.iterator();
    while (targetIterator.hasNext()) {
      Target target = targetIterator.next();
      if (!validateScope(target.getLabel(), strictScope)) {
        targetIterator.remove();
      }
    }

    Set<PathFragment> packages = new HashSet<>();
    for (Target target : targets) {
      packages.add(target.getLabel().getPackageFragment());
    }

    Set<Target> result = new LinkedHashSet<>();
    for (Target target : targets) {
      result.add(getOrCreate(target));

      // Preservation of graph order: it is important that targets obtained via
      // a wildcard such as p:* are correctly ordered w.r.t. each other, so to
      // ensure this, we add edges between any pair of directly connected
      // targets in this set.
      if (target instanceof OutputFile) {
        OutputFile outputFile = (OutputFile) target;
        if (targets.contains(outputFile.getGeneratingRule())) {
          makeEdge(outputFile, outputFile.getGeneratingRule());
        }
      } else if (target instanceof Rule) {
        Rule rule = (Rule) target;
        for (Label label : rule.getLabels(dependencyFilter)) {
          if (!packages.contains(label.getPackageFragment())) {
            continue;  // don't cause additional package loading
          }
          try {
            if (!validateScope(label, strictScope)) {
              continue;  // Don't create edges to targets which are out of scope.
            }
            Target to = getTargetOrThrow(label);
            if (targets.contains(to)) {
              makeEdge(rule, to);
            }
          } catch (NoSuchThingException e) {
            /* ignore */
          } catch (InterruptedException e) {
            throw new QueryException("interrupted");
          }
        }
      }
    }
    return result;
  }

  public Target getTarget(Label label) throws TargetNotFoundException, QueryException {
    // Can't use strictScope here because we are expecting a target back.
    validateScope(label, true);
    try {
      return getNode(getTargetOrThrow(label)).getLabel();
    } catch (NoSuchThingException e) {
      throw new TargetNotFoundException(e);
    } catch (InterruptedException e) {
      throw new QueryException("interrupted");
    }
  }

  private Node<Target> getNode(Target target) {
    return graph.createNode(target);
  }

  private Collection<Node<Target>> getNodes(Iterable<Target> target) {
    Set<Node<Target>> result = new LinkedHashSet<>();
    for (Target t : target) {
      result.add(getNode(t));
    }
    return result;
  }

  @Override
  public Target getOrCreate(Target target) {
    return getNode(target).getLabel();
  }

  @Override
  public Collection<Target> getFwdDeps(Target target) {
    return getTargetsFromNodes(getNode(target).getSuccessors());
  }

  @Override
  public Collection<Target> getReverseDeps(Target target) {
    return getTargetsFromNodes(getNode(target).getPredecessors());
  }

  @Override
  public Set<Target> getTransitiveClosure(Set<Target> targetNodes) {
    for (Target node : targetNodes) {
      checkBuilt(node);
    }
    return getTargetsFromNodes(graph.getFwdReachable(getNodes(targetNodes)));
  }

  /**
   * Checks that the graph rooted at 'targetNode' has been completely built;
   * fails if not.  Callers of {@link #getTransitiveClosure} must ensure that
   * {@link #buildTransitiveClosure} has been called before.
   *
   * <p>It would be inefficient and failure-prone to make getTransitiveClosure
   * call buildTransitiveClosure directly.  Also, it would cause
   * nondeterministic behavior of the operators, since the set of packages
   * loaded (and hence errors reported) would depend on the ordering details of
   * the query operators' implementations.
   */
  private void checkBuilt(Target targetNode) {
    Preconditions.checkState(
        labelVisitor.hasVisited(targetNode.getLabel()),
        "getTransitiveClosure(%s) called without prior call to buildTransitiveClosure()",
        targetNode);
  }

  @Override
  public void buildTransitiveClosure(QueryExpression caller,
                                     Set<Target> targetNodes,
                                     int maxDepth) throws QueryException {
    Set<Target> targets = targetNodes;
    preloadTransitiveClosure(targets, maxDepth);

    try {
      labelVisitor.syncWithVisitor(eventHandler, targets, keepGoing,
          loadingPhaseThreads, maxDepth, errorObserver, new GraphBuildingObserver());
    } catch (InterruptedException e) {
      throw new QueryException(caller, "transitive closure computation was interrupted");
    }

    if (errorObserver.hasErrors()) {
      reportBuildFileError(caller, "errors were encountered while computing transitive closure");
    }
  }

  @Override
  public Set<Target> getNodesOnPath(Target from, Target to) {
    return getTargetsFromNodes(graph.getShortestPath(getNode(from), getNode(to)));
  }

  private void preloadTransitiveClosure(Set<Target> targets, int maxDepth) throws QueryException {
    if (maxDepth >= MAX_DEPTH_FULL_SCAN_LIMIT && transitivePackageLoader != null) {
      // Only do the full visitation if "maxDepth" is large enough. Otherwise, the benefits of
      // preloading will be outweighed by the cost of doing more work than necessary.
      try {
        transitivePackageLoader.sync(eventHandler, targets, ImmutableSet.<Label>of(), keepGoing,
            loadingPhaseThreads, -1);
      } catch (InterruptedException e) {
        throw new QueryException("interrupted");
      }
    }
  }

  /**
   * It suffices to synchronize the modifications of this.graph from within the
   * GraphBuildingObserver, because that's the only concurrent part.
   * Concurrency is always encapsulated within the evaluation of a single query
   * operator (e.g. deps(), somepath(), etc).
   */
  private class GraphBuildingObserver implements TargetEdgeObserver {

    @Override
    public synchronized void edge(Target from, Attribute attribute, Target to) {
      Preconditions.checkState(attribute == null ||
          dependencyFilter.apply(((Rule) from), attribute),
          "Disallowed edge from LabelVisitor: %s --> %s", from, to);
      makeEdge(from, to);
    }

    @Override
    public synchronized void node(Target node) {
      graph.createNode(node);
    }

    @Override
    public void missingEdge(Target target, Label to, NoSuchThingException e) {
      // No - op.
    }
  }

  private void makeEdge(Target from, Target to) {
    graph.addEdge(from, to);
  }

  private Target getTargetOrThrow(Label label)
      throws NoSuchThingException, SkyframeRestartQueryException, InterruptedException {
    Target target = targetProvider.getTarget(eventHandler, label);
    if (target == null) {
      throw new SkyframeRestartQueryException();
    }
    return target;
  }

  // TODO(bazel-team): rename this to getDependentFiles when all implementations
  // of QueryEnvironment is fixed.
  @Override
  public Set<Target> getBuildFiles(final QueryExpression caller, Set<Target> nodes)
      throws QueryException {
    Set<Target> dependentFiles = new LinkedHashSet<>();
    Set<Package> seenPackages = new HashSet<>();
    // Keep track of seen labels, to avoid adding a fake subinclude label that also exists as a
    // real target.
    Set<Label> seenLabels = new HashSet<>();

    // Adds all the package definition files (BUILD files and build
    // extensions) for package "pkg", to "buildfiles".
    for (Target x : nodes) {
      Package pkg = x.getPackage();
      if (seenPackages.add(pkg)) {
        addIfUniqueLabel(getNode(pkg.getBuildFile()), seenLabels, dependentFiles);
        for (Label subinclude
            : Iterables.concat(pkg.getSubincludeLabels(), pkg.getSkylarkFileDependencies())) {
          addIfUniqueLabel(getSubincludeTarget(subinclude, pkg), seenLabels, dependentFiles);

          // Also add the BUILD file of the subinclude.
          try {
            addIfUniqueLabel(getSubincludeTarget(
                subinclude.getLocalTargetLabel("BUILD"), pkg), seenLabels, dependentFiles);
          } catch (Label.SyntaxException e) {
            throw new AssertionError("BUILD should always parse as a target name", e);
          }
        }
      }
    }
    return dependentFiles;
  }

  private static void addIfUniqueLabel(Node<Target> node, Set<Label> labels, Set<Target> nodes) {
    if (labels.add(node.getLabel().getLabel())) {
      nodes.add(node.getLabel());
    }
  }

  private Node<Target> getSubincludeTarget(final Label label, Package pkg) {
    return getNode(new FakeSubincludeTarget(label, pkg.getBuildFile().getLocation()));
  }

  @Override
  public TargetAccessor<Target> getAccessor() {
    return accessor;
  }

  /** Given a set of target nodes, returns the targets. */
  private static Set<Target> getTargetsFromNodes(Iterable<Node<Target>> input) {
    Set<Target> result = new LinkedHashSet<>();
    for (Node<Target> node : input) {
      result.add(node.getLabel());
    }
    return result;
  }
}
