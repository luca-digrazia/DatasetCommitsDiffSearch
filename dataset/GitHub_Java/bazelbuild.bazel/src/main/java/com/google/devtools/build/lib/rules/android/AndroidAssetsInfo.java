// Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.devtools.build.lib.rules.android.AndroidSkylarkData.fromNoneable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.BuiltinProvider;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.skylarkbuildapi.android.AndroidAssetsInfoApi;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import java.util.Optional;
import javax.annotation.Nullable;

/** Provides information about transitive Android assets. */
public final class AndroidAssetsInfo extends NativeInfo
    implements AndroidAssetsInfoApi<Artifact, ParsedAndroidAssets> {

  public static final Provider PROVIDER = new Provider();

  private final Label label;
  @Nullable private final Artifact validationResult;
  private final NestedSet<ParsedAndroidAssets> directParsedAssets;
  private final NestedSet<ParsedAndroidAssets> transitiveParsedAssets;
  private final NestedSet<Artifact> transitiveAssets;
  private final NestedSet<Artifact> transitiveSymbols;
  /**
   * Whether the local assets have been specified. This field is needed to distinguish between the
   * situation when the local assets haven't been specified and the {@link #directParsedAssets}
   * contains assets form the target's dependencies.
   */
  private final boolean hasLocalAssets;
  private final NestedSet<Artifact> transitiveCompiledSymbols;

  static AndroidAssetsInfo empty(Label label) {
    return new AndroidAssetsInfo(
        label,
        null,
        NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER),
        NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER),
        NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER),
        NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER),
        NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER));
  }

  public static AndroidAssetsInfo of(
      Label label,
      @Nullable Artifact validationResult,
      NestedSet<ParsedAndroidAssets> directParsedAssets,
      NestedSet<ParsedAndroidAssets> transitiveParsedAssets,
      NestedSet<Artifact> transitiveAssets,
      NestedSet<Artifact> transitiveSymbols,
      NestedSet<Artifact> transitiveCompiledSymbols) {
    return new AndroidAssetsInfo(
        label,
        validationResult,
        directParsedAssets,
        transitiveParsedAssets,
        transitiveAssets,
        transitiveSymbols,
        transitiveCompiledSymbols);
  }

  private AndroidAssetsInfo(
      Label label,
      @Nullable Artifact validationResult,
      NestedSet<ParsedAndroidAssets> directParsedAssets,
      NestedSet<ParsedAndroidAssets> transitiveParsedAssets,
      NestedSet<Artifact> transitiveAssets,
      NestedSet<Artifact> transitiveSymbols,
      NestedSet<Artifact> transitiveCompiledSymbols) {
    super(PROVIDER);
    this.label = label;
    this.hasLocalAssets = validationResult != null;
    this.validationResult = validationResult;
    this.directParsedAssets = directParsedAssets;
    this.transitiveParsedAssets = transitiveParsedAssets;
    this.transitiveAssets = transitiveAssets;
    this.transitiveSymbols = transitiveSymbols;
    this.transitiveCompiledSymbols = transitiveCompiledSymbols;
  }

  @Override
  public Label getLabel() {
    return label;
  }

  @Nullable
  @Override
  public Artifact getValidationResult() {
    return validationResult;
  }

  @Override
  public NestedSet<ParsedAndroidAssets> getDirectParsedAssets() {
    return directParsedAssets;
  }

  @Override
  public ImmutableList<Artifact> getLocalAssets() {
    return getLocalParsedAndroidAssets().map(AndroidAssets::getAssets).orElse(null);
  }

  @Override
  public String getLocalAssetDir() {
    return getLocalParsedAndroidAssets().map(AndroidAssets::getAssetDirAsString).orElse(null);
  }

  @Override
  public NestedSet<ParsedAndroidAssets> getTransitiveParsedAssets() {
    return transitiveParsedAssets;
  }

  @Override
  public NestedSet<Artifact> getAssets() {
    return transitiveAssets;
  }

  @Override
  public NestedSet<Artifact> getSymbols() {
    return transitiveSymbols;
  }

  private Optional<ParsedAndroidAssets> getLocalParsedAndroidAssets() {
    return hasLocalAssets && getDirectParsedAssets().isSingleton()
        ? Optional.of(Iterables.getOnlyElement(getDirectParsedAssets()))
        : Optional.empty();
  }

  @Override
  public NestedSet<Artifact> getCompiledSymbols() {
    return transitiveCompiledSymbols;
  }

  /** The provider can construct the Android IDL provider. */
  public static class Provider extends BuiltinProvider<AndroidAssetsInfo>
      implements AndroidAssetsInfoApi.Provider<Artifact, ParsedAndroidAssets> {

    private Provider() {
      super(NAME, AndroidAssetsInfo.class);
    }

    @Override
    public AndroidAssetsInfo createInfo(
        Label label,
        Object validationResult,
        SkylarkNestedSet directParsedAssets,
        SkylarkNestedSet transitiveParsedAssets,
        SkylarkNestedSet transitiveAssets,
        SkylarkNestedSet transitiveSymbols,
        SkylarkNestedSet transitiveCompiledSymbols)
        throws EvalException {
      return new AndroidAssetsInfo(
          label,
          fromNoneable(validationResult, Artifact.class),
          nestedSet(directParsedAssets, ParsedAndroidAssets.class),
          nestedSet(transitiveParsedAssets, ParsedAndroidAssets.class),
          nestedSet(transitiveAssets, Artifact.class),
          nestedSet(transitiveSymbols, Artifact.class),
          nestedSet(transitiveCompiledSymbols, Artifact.class));
    }

    private <T> NestedSet<T> nestedSet(SkylarkNestedSet from, Class<T> with) {
      return NestedSetBuilder.<T>naiveLinkOrder().addTransitive(from.getSet(with)).build();
    }
  }
}
