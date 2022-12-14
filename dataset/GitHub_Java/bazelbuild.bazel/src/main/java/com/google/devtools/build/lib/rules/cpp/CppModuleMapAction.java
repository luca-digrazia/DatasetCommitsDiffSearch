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
package com.google.devtools.build.lib.rules.cpp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Executor;
import com.google.devtools.build.lib.actions.ResourceSet;
import com.google.devtools.build.lib.analysis.actions.AbstractFileWriteAction;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.util.Fingerprint;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates C++ module map artifact genfiles. These are then passed to Clang to
 * do dependency checking.
 */
public class CppModuleMapAction extends AbstractFileWriteAction {

  private static final String GUID = "4f407081-1951-40c1-befc-d6b4daff5de3";

  // C++ module map of the current target
  private final CppModuleMap cppModuleMap;
  
  /**
   * If set, the paths in the module map are relative to the current working directory instead
   * of relative to the module map file's location. 
   */
  private final boolean moduleMapHomeIsCwd;

  // Headers and dependencies list
  private final ImmutableList<Artifact> privateHeaders;
  private final ImmutableList<Artifact> publicHeaders;
  private final ImmutableList<CppModuleMap> dependencies;
  private final ImmutableList<PathFragment> additionalExportedHeaders;
  private final boolean compiledModule;

  public CppModuleMapAction(ActionOwner owner, CppModuleMap cppModuleMap,
      Iterable<Artifact> privateHeaders, Iterable<Artifact> publicHeaders,
      Iterable<CppModuleMap> dependencies, Iterable<PathFragment> additionalExportedHeaders,
      boolean compiledModule, boolean moduleMapHomeIsCwd) {
    super(owner, ImmutableList.<Artifact>of(), cppModuleMap.getArtifact(),
        /*makeExecutable=*/false);
    this.cppModuleMap = cppModuleMap;
    this.moduleMapHomeIsCwd = moduleMapHomeIsCwd;
    this.privateHeaders = ImmutableList.copyOf(privateHeaders);
    this.publicHeaders = ImmutableList.copyOf(publicHeaders);
    this.dependencies = ImmutableList.copyOf(dependencies);
    this.additionalExportedHeaders = ImmutableList.copyOf(additionalExportedHeaders);
    this.compiledModule = compiledModule;
  }

  @Override
  public DeterministicWriter newDeterministicWriter(EventHandler eventHandler, Executor executor)  {
    return new DeterministicWriter() {
      @Override
      public void writeOutputFile(OutputStream out) throws IOException {
        StringBuilder content = new StringBuilder();
        PathFragment fragment = cppModuleMap.getArtifact().getExecPath();
        int segmentsToExecPath = fragment.segmentCount() - 1;

        // For details about the different header types, see:
        // http://clang.llvm.org/docs/Modules.html#header-declaration
        String leadingPeriods = moduleMapHomeIsCwd ? "" : Strings.repeat("../", segmentsToExecPath);
        content.append("module \"").append(cppModuleMap.getName()).append("\" {\n");
        content.append("  export *\n");
        for (Artifact artifact : privateHeaders) {
          appendHeader(content, "private", artifact.getExecPath(), leadingPeriods,
              /*canCompile=*/true);
        }
        for (Artifact artifact : publicHeaders) {
          appendHeader(content, "", artifact.getExecPath(), leadingPeriods, /*canCompile=*/true);
        }
        for (PathFragment additionalExportedHeader : additionalExportedHeaders) {
          appendHeader(content, "", additionalExportedHeader, leadingPeriods, /*canCompile*/false);
        }
        for (CppModuleMap dep : dependencies) {
          content.append("  use \"").append(dep.getName()).append("\"\n");
        }
        content.append("}");
        for (CppModuleMap dep : dependencies) {
          content.append("\nextern module \"")
              .append(dep.getName())
              .append("\" \"")
              .append(leadingPeriods)
              .append(dep.getArtifact().getExecPath())
              .append("\"");
        }
        out.write(content.toString().getBytes(StandardCharsets.ISO_8859_1));
      }
    };
  }
  
  private void appendHeader(StringBuilder content, String visibilitySpecifier, PathFragment path,
      String leadingPeriods, boolean canCompile) {
    content.append("  ");
    if (!visibilitySpecifier.isEmpty()) {
      content.append(visibilitySpecifier).append(" ");
    }
    if (!canCompile || !shouldCompileHeader(path)) {
      content.append("textual ");
    }
    content.append("header \"").append(leadingPeriods).append(path).append("\"\n");
  }
  
  private boolean shouldCompileHeader(PathFragment path) {
    return compiledModule && !CppFileTypes.CPP_TEXTUAL_INCLUDE.matches(path);
  }

  @Override
  public String getMnemonic() {
    return "CppModuleMap";
  }

  @Override
  protected String computeKey() {
    Fingerprint f = new Fingerprint();
    f.addString(GUID);
    f.addInt(privateHeaders.size());
    for (Artifact artifact : privateHeaders) {
      f.addPath(artifact.getRootRelativePath());
    }
    f.addInt(publicHeaders.size());
    for (Artifact artifact : publicHeaders) {
      f.addPath(artifact.getRootRelativePath());
    }
    f.addInt(dependencies.size());
    for (CppModuleMap dep : dependencies) {
      f.addPath(dep.getArtifact().getExecPath());
    }
    f.addInt(additionalExportedHeaders.size());
    for (PathFragment path : additionalExportedHeaders) {
      f.addPath(path);
    }
    f.addPath(cppModuleMap.getArtifact().getExecPath());
    f.addString(cppModuleMap.getName());
    f.addBoolean(moduleMapHomeIsCwd);
    f.addBoolean(compiledModule);
    return f.hexDigestAndReset();
  }

  @Override
  public ResourceSet estimateResourceConsumptionLocal() {
    return new ResourceSet(/*memoryMb=*/0, /*cpuUsage=*/0, /*ioUsage=*/0.02);
  }

  @VisibleForTesting
  public Collection<Artifact> getPublicHeaders() {
    return publicHeaders;
  }

  @VisibleForTesting
  public Collection<Artifact> getPrivateHeaders() {
    return privateHeaders;
  }
  
  @VisibleForTesting
  public ImmutableList<PathFragment> getAdditionalExportedHeaders() {
    return additionalExportedHeaders;
  }

  @VisibleForTesting
  public Collection<Artifact> getDependencyArtifacts() {
    List<Artifact> artifacts = new ArrayList<>();
    for (CppModuleMap map : dependencies) {
      artifacts.add(map.getArtifact());
    }
    return artifacts;
  }
}
