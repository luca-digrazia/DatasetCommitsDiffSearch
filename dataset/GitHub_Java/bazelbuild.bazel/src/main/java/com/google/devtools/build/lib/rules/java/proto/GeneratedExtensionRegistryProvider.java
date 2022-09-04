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

package com.google.devtools.build.lib.rules.java.proto;

import com.google.common.base.Verify;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.BuiltinProvider;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.skylarkbuildapi.java.GeneratedExtensionRegistryProviderApi;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;

/**
 * A {@link TransitiveInfoProvider} for {@link Artifact}s created and used to generate the proto
 * extension registry. This provider is used to ensure that if multiple registries are generated
 * from a target, that the top most target produces a registry that is a superset of any child
 * registries.
 */
@Immutable
public final class GeneratedExtensionRegistryProvider extends NativeInfo
    implements GeneratedExtensionRegistryProviderApi<Artifact> {

  public static final Provider PROVIDER = new Provider();

  private final Label generatingRuleLabel;
  private final boolean isLite;
  private final Artifact classJar;
  private final Artifact srcJar;
  private final NestedSet<Artifact> inputs;

  /** @return the rule label for which this registry was built. */
  @Override
  public Label getGeneratingRuleLabel() {
    return generatingRuleLabel;
  }

  /** @return if this registry was generated for lite or full runtime. */
  @Override
  public boolean isLite() {
    return isLite;
  }

  /** @return the class jar generated by the registry. */
  @Override
  public Artifact getClassJar() {
    return classJar;
  }

  /** @return the source jar generated by the registry. */
  @Override
  public Artifact getSrcJar() {
    return srcJar;
  }

  /** @return the proto jars used to generate the registry. */
  @Override
  public NestedSet<Artifact> getInputs() {
    return inputs;
  }

  public static Builder builder() {
    return new GeneratedExtensionRegistryProvider.Builder();
  }

  GeneratedExtensionRegistryProvider(
      Label generatingRuleLabel,
      boolean isLite,
      Artifact classJar,
      Artifact srcJar,
      NestedSet<Artifact> inputs) {
    super(PROVIDER);
    this.generatingRuleLabel = generatingRuleLabel;
    this.isLite = isLite;
    this.classJar = classJar;
    this.srcJar = srcJar;
    this.inputs = inputs;
  }

  /** A builder for {@link GeneratedExtensionRegistryProvider}. */
  public static class Builder {
    private Label generatingRuleLabel = null;
    private boolean isLite = false;
    private Artifact classJar = null;
    private Artifact srcJar = null;
    private NestedSet<Artifact> inputs = null;

    /** Sets the rule label for which this registry was built. */
    public Builder setGeneratingRuleLabel(Label label) {
      this.generatingRuleLabel = label;
      return this;
    }

    /** Indicates this registry was built for lite runtime if <tt>true</tt>, full otherwise. */
    public Builder setLite(boolean lite) {
      this.isLite = lite;
      return this;
    }

    /** Sets the class jar containing the generated extension registry. */
    public Builder setClassJar(Artifact classJar) {
      this.classJar = classJar;
      return this;
    }

    /** Sets the source jar containing the generated extension registry. */
    public Builder setSrcJar(Artifact srcJar) {
      this.srcJar = srcJar;
      return this;
    }

    /** Sets the transitive set of protos used to produce the generated extension registry. */
    public Builder setInputs(NestedSet<Artifact> inputs) {
      this.inputs = inputs;
      return this;
    }

    public GeneratedExtensionRegistryProvider build() {
      Verify.verify(!inputs.isEmpty());
      return new GeneratedExtensionRegistryProvider(
          generatingRuleLabel, isLite, classJar, srcJar, inputs);
    }
  }

  /** Provider class for {@link GeneratedExtensionRegistryProvider} objects. */
  public static class Provider extends BuiltinProvider<GeneratedExtensionRegistryProvider>
      implements GeneratedExtensionRegistryProviderApi.Provider<Artifact> {
    private Provider() {
      super(NAME, GeneratedExtensionRegistryProvider.class);
    }

    public String getName() {
      return NAME;
    }

    @Override
    public GeneratedExtensionRegistryProvider create(
        Label generatingRuleLabel,
        boolean isLite,
        Artifact classJar,
        Artifact srcJar,
        SkylarkNestedSet inputs)
        throws EvalException {
      return new GeneratedExtensionRegistryProvider(
          generatingRuleLabel,
          isLite,
          classJar,
          srcJar,
          NestedSetBuilder.<Artifact>stableOrder()
              .addTransitive(inputs.getSetFromParam(Artifact.class, "inputs"))
              .build());
    }
  }
}
