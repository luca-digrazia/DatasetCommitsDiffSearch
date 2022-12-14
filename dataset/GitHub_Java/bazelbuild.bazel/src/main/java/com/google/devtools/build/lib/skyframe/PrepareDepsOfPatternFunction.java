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
package com.google.devtools.build.lib.skyframe;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.cmdline.ResolvedTargets;
import com.google.devtools.build.lib.cmdline.TargetParsingException;
import com.google.devtools.build.lib.cmdline.TargetPattern;
import com.google.devtools.build.lib.cmdline.TargetPatternResolver;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.packages.NoSuchPackageException;
import com.google.devtools.build.lib.packages.NoSuchTargetException;
import com.google.devtools.build.lib.packages.NoSuchThingException;
import com.google.devtools.build.lib.packages.Package;
import com.google.devtools.build.lib.packages.PackageIdentifier;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.pkgcache.FilteringPolicies;
import com.google.devtools.build.lib.pkgcache.FilteringPolicy;
import com.google.devtools.build.lib.pkgcache.PathPackageLocator;
import com.google.devtools.build.lib.pkgcache.TargetPatternResolverUtil;
import com.google.devtools.build.lib.skyframe.EnvironmentBackedRecursivePackageProvider.MissingDepException;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.RootedPath;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionException;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * PrepareDepsOfPatternFunction ensures the graph loads targets matching the pattern and its
 * transitive dependencies.
 */
public class PrepareDepsOfPatternFunction implements SkyFunction {

  private final AtomicReference<PathPackageLocator> pkgPath;

  public PrepareDepsOfPatternFunction(AtomicReference<PathPackageLocator> pkgPath) {
    this.pkgPath = pkgPath;
  }

  @Nullable
  @Override
  public SkyValue compute(SkyKey key, Environment env)
      throws SkyFunctionException, InterruptedException {
    TargetPatternValue.TargetPatternKey patternKey =
        ((TargetPatternValue.TargetPatternKey) key.argument());
    try {
      TargetPattern parsedPattern = patternKey.getParsedPattern();
      DepsOfPatternPreparer preparer =
          new DepsOfPatternPreparer(env, patternKey.getPolicy(), pkgPath.get());
      ImmutableSet<String> excludedSubdirectories = patternKey.getExcludedSubdirectories();
      parsedPattern.eval(preparer, excludedSubdirectories);
    } catch (TargetParsingException e) {
      throw new PrepareDepsOfPatternFunctionException(e);
    } catch (MissingDepException e) {
      // The DepsOfPatternPreparer constructed above might throw MissingDepException to signal
      // when it has a dependency on a missing Environment value.
      return null;
    }
    return PrepareDepsOfPatternValue.INSTANCE;
  }

  @Nullable
  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }

  /**
   * Used to declare all the exception types that can be wrapped in the exception thrown by {@link
   * PrepareDepsOfPatternFunction#compute}.
   */
  private static final class PrepareDepsOfPatternFunctionException extends SkyFunctionException {

    public PrepareDepsOfPatternFunctionException(TargetParsingException e) {
      super(e, Transience.PERSISTENT);
    }
  }

  /**
   * A {@link TargetPatternResolver} backed by an {@link Environment} whose methods do not actually
   * return resolved targets, but that ensures the graph loads the matching targets <b>and</b> their
   * transitive dependencies. Its methods may throw {@link MissingDepException} if the package
   * values this depends on haven't been calculated and added to its environment.
   */
  static class DepsOfPatternPreparer implements TargetPatternResolver<Void> {

    private final EnvironmentBackedRecursivePackageProvider packageProvider;
    private final Environment env;
    private final FilteringPolicy policy;
    private final PathPackageLocator pkgPath;

    public DepsOfPatternPreparer(Environment env, FilteringPolicy policy,
        PathPackageLocator pkgPath) {
      this.env = env;
      this.packageProvider = new EnvironmentBackedRecursivePackageProvider(env);
      this.policy = policy;
      this.pkgPath = pkgPath;
    }

    @Override
    public void warn(String msg) {
      env.getListener().handle(Event.warn(msg));
    }

    @Override
    public Void getTargetOrNull(String targetName) throws InterruptedException {
      // Note:
      // This method is used in just one place, TargetPattern.TargetsInPackage#getWildcardConflict.
      // Returning null tells #getWildcardConflict that there is not a target with a name like
      // "all" or "all-targets", which means that TargetPattern.TargetsInPackage will end up
      // calling DepsOfTargetPreparer#getTargetsInPackage.
      // TODO (bazel-team): Consider replacing this with an isTarget method on the interface.
      return null;
    }

    @Override
    public ResolvedTargets<Void> getExplicitTarget(String targetName)
        throws TargetParsingException, InterruptedException {
      Label label = TargetPatternResolverUtil.label(targetName);
      try {
        Target target = packageProvider.getTarget(env.getListener(), label);
        SkyKey key = TransitiveTargetValue.key(target.getLabel());
        SkyValue token =
            env.getValueOrThrow(key, NoSuchPackageException.class, NoSuchTargetException.class);
        if (token == null) {
          throw new MissingDepException();
        }
        return ResolvedTargets.empty();
      } catch (NoSuchThingException e) {
        throw new TargetParsingException(e.getMessage(), e);
      }
    }

    @Override
    public ResolvedTargets<Void> getTargetsInPackage(String originalPattern, String packageName,
        boolean rulesOnly) throws TargetParsingException, InterruptedException {
      FilteringPolicy actualPolicy = rulesOnly
          ? FilteringPolicies.and(FilteringPolicies.RULES_ONLY, policy)
          : policy;
      return getTargetsInPackage(originalPattern, new PathFragment(packageName), actualPolicy);
    }

    private ResolvedTargets<Void> getTargetsInPackage(String originalPattern,
        PathFragment packageNameFragment, FilteringPolicy policy)
        throws TargetParsingException, InterruptedException {
      TargetPatternResolverUtil.validatePatternPackage(originalPattern, packageNameFragment, this);
      try {
        PackageIdentifier packageId = PackageIdentifier.createInDefaultRepo(packageNameFragment);
        Package pkg = packageProvider.getPackage(env.getListener(), packageId);
        ResolvedTargets<Target> packageTargets =
            TargetPatternResolverUtil.resolvePackageTargets(pkg, policy);
        ImmutableList.Builder<SkyKey> builder = ImmutableList.builder();
        for (Target target : packageTargets.getTargets()) {
          builder.add(TransitiveTargetValue.key(target.getLabel()));
        }
        ImmutableList<SkyKey> skyKeys = builder.build();
        env.getValuesOrThrow(skyKeys, NoSuchPackageException.class, NoSuchTargetException.class);
        if (env.valuesMissing()) {
          throw new MissingDepException();
        }
        return ResolvedTargets.empty();
      } catch (NoSuchThingException e) {
        String message = TargetPatternResolverUtil.getParsingErrorMessage(
            "package contains errors", originalPattern);
        throw new TargetParsingException(message, e);
      }
    }

    @Override
    public boolean isPackage(String packageName) {
      return packageProvider.isPackage(env.getListener(), packageName);
    }

    @Override
    public String getTargetKind(Void target) {
      // Note:
      // This method is used in just one place, TargetPattern.TargetsInPackage#getWildcardConflict.
      // Because DepsOfPatternPreparer#getTargetOrNull always returns null, this method is never
      // called.
      throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedTargets<Void> findTargetsBeneathDirectory(String originalPattern,
        String directory, boolean rulesOnly, ImmutableSet<String> excludedSubdirectories)
        throws TargetParsingException, InterruptedException {
      FilteringPolicy actualPolicy = rulesOnly
          ? FilteringPolicies.and(FilteringPolicies.RULES_ONLY, policy)
          : policy;
      ImmutableSet<PathFragment> excludedPathFragments =
          TargetPatternResolverUtil.getPathFragments(excludedSubdirectories);
      PathFragment pathFragment = TargetPatternResolverUtil.getPathFragment(directory);
      for (Path root : pkgPath.getPathEntries()) {
        RootedPath rootedPath = RootedPath.toRootedPath(root, pathFragment);
        SkyValue token = env.getValue(PrepareDepsOfTargetsUnderDirectoryValue.key(rootedPath,
            excludedPathFragments, actualPolicy));
        if (token == null) {
          // A null token value means there is a missing dependency, because RecursivePkgFunction
          // never throws.
          throw new MissingDepException();
        }
      }
      return ResolvedTargets.empty();
    }
  }
}
