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

package com.google.devtools.build.lib.bazel.repository;

import com.google.devtools.build.lib.bazel.rules.workspace.NewHttpArchiveRule;
import com.google.devtools.build.lib.packages.ExternalPackage;
import com.google.devtools.build.lib.packages.PackageIdentifier.RepositoryName;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.skyframe.RepositoryValue;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionException;
import com.google.devtools.build.skyframe.SkyFunctionName;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Downloads an archive from a URL, decompresses it, creates a WORKSPACE file, and adds a BUILD
 * file for it.
 */
public class NewHttpArchiveFunction extends HttpArchiveFunction {

  @Override
  public SkyFunctionName getSkyFunctionName() {
    return SkyFunctionName.computed(NewHttpArchiveRule.NAME);
  }

  @Nullable
  @Override
  public SkyValue compute(SkyKey skyKey, SkyFunction.Environment env)
      throws RepositoryFunctionException {
    RepositoryName repositoryName = (RepositoryName) skyKey.argument();
    Rule rule = RepositoryFunction.getRule(repositoryName, NewHttpArchiveRule.NAME, env);
    if (rule == null) {
      return null;
    }

    // Download.
    Path outputDirectory = getOutputBase().getRelative(ExternalPackage.NAME)
        .getRelative(rule.getName());
    RepositoryValue downloadedFileValue;
    try {
      downloadedFileValue = (RepositoryValue) env.getValueOrThrow(
          HttpDownloadFunction.key(rule, outputDirectory), IOException.class);
    } catch (IOException e) {
      throw new RepositoryFunctionException(e, SkyFunctionException.Transience.PERSISTENT);
    }
    if (downloadedFileValue == null) {
      return null;
    }

    // Decompress.
    Path decompressedDirectory;
    try {
      decompressedDirectory = DecompressorFactory.create(
          rule.getTargetKind(), rule.getName(), downloadedFileValue.getPath()).decompress();
    } catch (DecompressorFactory.DecompressorException e) {
      throw new RepositoryFunctionException(
          new IOException(e.getMessage()), SkyFunctionException.Transience.TRANSIENT);
    }

    // Add WORKSPACE and BUILD files.
    NewLocalRepositoryFunction.createWorkspaceFile(decompressedDirectory, rule);
    if (NewLocalRepositoryFunction.createBuildFile(
        rule, getWorkspace(), decompressedDirectory, env) == null) {
      return null;
    }

    return new RepositoryValue(
        decompressedDirectory, downloadedFileValue.getRepositoryDirectory());
  }
}
