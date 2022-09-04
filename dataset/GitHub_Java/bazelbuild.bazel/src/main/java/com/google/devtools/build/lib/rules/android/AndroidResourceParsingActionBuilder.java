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
package com.google.devtools.build.lib.rules.android;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ParameterFile.ParameterFileType;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.actions.ActionConstructionContext;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Builder for creating $android_resource_parser action. */
public class AndroidResourceParsingActionBuilder {

  private static final ResourceContainerToArtifacts RESOURCE_CONTAINER_TO_ARTIFACTS =
      new ResourceContainerToArtifacts();

  private static final ResourceContainerToArg RESOURCE_CONTAINER_TO_ARG =
      new ResourceContainerToArg();

  private final RuleContext ruleContext;
  private LocalResourceContainer primary;
  private Artifact output;

  private ResourceContainer resourceContainer;
  private Artifact compiledSymbols;
  private Artifact dataBindingInfoZip;

  /**
   * @param ruleContext The RuleContext that was used to create the SpawnAction.Builder.
   */
  public AndroidResourceParsingActionBuilder(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
  }

  /**
   * Set the resource container to parse.
   */
  public AndroidResourceParsingActionBuilder setParse(LocalResourceContainer primary) {
    this.primary = primary;
    return this;
  }

  /**
   * Set the artifact location for the output protobuf.
   */
  public AndroidResourceParsingActionBuilder setOutput(Artifact output) {
    this.output = output;
    return this;
  }

  /** Set the primary resources. */
  public AndroidResourceParsingActionBuilder withPrimary(ResourceContainer resourceContainer) {
    this.resourceContainer = resourceContainer;
    return this;
  }

  public AndroidResourceParsingActionBuilder setCompiledSymbolsOutput(
      @Nullable Artifact compiledSymbols) {
    this.compiledSymbols = compiledSymbols;
    return this;
  }

  public AndroidResourceParsingActionBuilder setDataBindingInfoZip(Artifact dataBindingInfoZip) {
    this.dataBindingInfoZip = dataBindingInfoZip;
    return this;
  }

  private static class ResourceContainerToArg implements Function<LocalResourceContainer, String> {

    public ResourceContainerToArg() {
    }

    @Override
    public String apply(LocalResourceContainer container) {
      return new StringBuilder()
          .append(convertRoots(container.getResourceRoots()))
          .append(":")
          .append(convertRoots(container.getAssetRoots()))
          .toString();
    }
  }

  private static class ResourceContainerToArtifacts
      implements Function<LocalResourceContainer, NestedSet<Artifact>> {

    public ResourceContainerToArtifacts() {
    }

    @Override
    public NestedSet<Artifact> apply(LocalResourceContainer container) {
      NestedSetBuilder<Artifact> artifacts = NestedSetBuilder.naiveLinkOrder();
      artifacts.addAll(container.getAssets());
      artifacts.addAll(container.getResources());
      return artifacts.build();
    }
  }

  private static String convertRoots(Iterable<PathFragment> roots) {
    return Joiner.on("#").join(Iterables.transform(roots, Functions.toStringFunction()));
  }

  public ResourceContainer build(ActionConstructionContext context) {
    CustomCommandLine.Builder builder = new CustomCommandLine.Builder();

    // Set the busybox tool.
    builder.add("--tool").add("PARSE").add("--");

    NestedSetBuilder<Artifact> inputs = NestedSetBuilder.naiveLinkOrder();

    Preconditions.checkNotNull(primary);
    String resourceDirectories = RESOURCE_CONTAINER_TO_ARG.apply(primary);
    builder.add("--primaryData").add(resourceDirectories);
    inputs.addTransitive(RESOURCE_CONTAINER_TO_ARTIFACTS.apply(primary));

    Preconditions.checkNotNull(output);
    builder.addExecPath("--output", output);

    // Create the spawn action.
    ruleContext.registerAction(
        new SpawnAction.Builder()
            .useParameterFile(ParameterFileType.UNQUOTED)
            .addTransitiveInputs(inputs.build())
            .addOutputs(ImmutableList.of(output))
            .setCommandLine(builder.build())
            .setExecutable(
                ruleContext.getExecutablePrerequisite("$android_resources_busybox", Mode.HOST))
            .setProgressMessage("Parsing Android resources for " + ruleContext.getLabel())
            .setMnemonic("AndroidResourceParser")
            .build(context));

    if (compiledSymbols != null) {
      List<Artifact> outs = new ArrayList<>();
      CustomCommandLine.Builder flatFileBuilder = new CustomCommandLine.Builder();
      flatFileBuilder
          .add("--tool")
          .add("COMPILE_LIBRARY_RESOURCES")
          .add("--")
          .add("--resources")
          .add(resourceDirectories)
          .addExecPath("--output", compiledSymbols);
      outs.add(compiledSymbols);

      // The databinding needs to be processed before compilation, so the stripping happens here.
      if (dataBindingInfoZip != null) {
        flatFileBuilder.addExecPath("--manifest", resourceContainer.getManifest());
        inputs.add(resourceContainer.getManifest());
        if (!Strings.isNullOrEmpty(resourceContainer.getJavaPackage())) {
          flatFileBuilder.add("--packagePath").add(resourceContainer.getJavaPackage());
        }
        builder.addExecPath("--dataBindingInfoOut", dataBindingInfoZip);
        outs.add(dataBindingInfoZip);
      }
      // Create the spawn action.
      ruleContext.registerAction(
          new SpawnAction.Builder()
              .useParameterFile(ParameterFileType.UNQUOTED)
              .addTransitiveInputs(inputs.build())
              .addOutputs(ImmutableList.copyOf(outs))
              .setCommandLine(flatFileBuilder.build())
              .setExecutable(
                  ruleContext.getExecutablePrerequisite("$android_resources_busybox", Mode.HOST))
              .setProgressMessage("Compiling Android resources for " + ruleContext.getLabel())
              .setMnemonic("AndroidResourceCompiler")
              .build(context));
      return resourceContainer
          .toBuilder()
          .setCompiledSymbols(compiledSymbols)
          .setSymbols(output)
          .build();
    } else {
      return resourceContainer.toBuilder().setSymbols(output).build();
    }
  }
}
