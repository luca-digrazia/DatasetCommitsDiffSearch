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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.cmdline.PackageIdentifier;
import com.google.devtools.build.lib.cmdline.PackageIdentifier.RepositoryName;
import com.google.devtools.build.lib.skyframe.ASTFileLookupValue.ASTLookupInputException;
import com.google.devtools.build.lib.syntax.Environment.Extension;
import com.google.devtools.build.lib.syntax.LoadStatement;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

/**
 * A value that represents a Skylark import lookup result. The lookup value corresponds to
 * exactly one Skylark file, identified by the PathFragment SkyKey argument.
 */
public class SkylarkImportLookupValue implements SkyValue {

  private final Extension environmentExtension;
  /**
   * The immediate Skylark file dependency descriptor class corresponding to this value.
   * Using this reference it's possible to reach the transitive closure of Skylark files
   * on which this Skylark file depends.
   */
  private final SkylarkFileDependency dependency;

  public SkylarkImportLookupValue(
      Extension environmentExtension, SkylarkFileDependency dependency) {
    this.environmentExtension = Preconditions.checkNotNull(environmentExtension);
    this.dependency = Preconditions.checkNotNull(dependency);
  }

  /**
   * Returns the Extension
   */
  public Extension getEnvironmentExtension() {
    return environmentExtension;
  }

  /**
   * Returns the immediate Skylark file dependency corresponding to this import lookup value.
   */
  public SkylarkFileDependency getDependency() {
    return dependency;
  }

  private static void checkInputArgument(PathFragment astFilePathFragment)
      throws ASTLookupInputException {
    if (astFilePathFragment.isAbsolute()) {
      throw new ASTLookupInputException(String.format(
          "Input file '%s' cannot be an absolute path.", astFilePathFragment));
    }
  }

  @VisibleForTesting
  static SkyKey key(PackageIdentifier pkgIdentifier) throws ASTLookupInputException {
    return key(pkgIdentifier.getRepository(), pkgIdentifier.getPackageFragment());
  }

  /**
   * Returns a SkyKey to get a SkylarkImportLookupValue. Note that SkylarkImportLookupValue
   * computations may be inlined to avoid having them in the graph. Callers should confirm whether
   * inlining is desired and either do the computation directly themselves (if inlined) or request
   * this key's value from the environment (if not).
   */
  static SkyKey key(RepositoryName repo, PathFragment fromFile, PathFragment fileToImport)
      throws ASTLookupInputException {
    PathFragment computedPath;
    if (fileToImport.isAbsolute()) {
      computedPath = fileToImport.toRelative();
    } else if (fileToImport.segmentCount() == 1) {
      computedPath = fromFile.getParentDirectory().getRelative(fileToImport);
    } else {
      throw new ASTLookupInputException(String.format(LoadStatement.PATH_ERROR_MSG, fileToImport));
    }
    return key(repo, computedPath);
  }

  private static SkyKey key(RepositoryName repo, PathFragment fileToImport)
      throws ASTLookupInputException {
    // Skylark import lookup keys need to be valid AST file lookup keys.
    checkInputArgument(fileToImport);
    return new SkyKey(
        SkyFunctions.SKYLARK_IMPORTS_LOOKUP,
        PackageIdentifier.create(repo, fileToImport));
  }
}
