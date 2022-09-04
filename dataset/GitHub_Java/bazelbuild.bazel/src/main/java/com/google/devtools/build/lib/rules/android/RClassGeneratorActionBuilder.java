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

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.rules.android.AndroidConfiguration.AndroidAaptVersion;
import com.google.devtools.build.lib.rules.android.AndroidDataConverter.JoinerType;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import java.util.function.Function;

/** Builds up the spawn action for $android_rclass_generator. */
public class RClassGeneratorActionBuilder {

  @AutoCodec @VisibleForSerialization
  static final AndroidDataConverter<ValidatedAndroidData> AAPT_CONVERTER =
      AndroidDataConverter.<ValidatedAndroidData>builder(JoinerType.COLON_COMMA)
          .with(chooseDepsToArg(AndroidAaptVersion.AAPT))
          .build();

  @AutoCodec @VisibleForSerialization
  static final AndroidDataConverter<ValidatedAndroidData> AAPT2_CONVERTER =
      AndroidDataConverter.<ValidatedAndroidData>builder(JoinerType.COLON_COMMA)
          .with(chooseDepsToArg(AndroidAaptVersion.AAPT2))
          .build();

  private ResourceDependencies dependencies;

  private Artifact classJarOut;

  private AndroidAaptVersion version;

  public RClassGeneratorActionBuilder withDependencies(ResourceDependencies resourceDeps) {
    this.dependencies = resourceDeps;
    return this;
  }

  public RClassGeneratorActionBuilder targetAaptVersion(AndroidAaptVersion version) {
    this.version = version;
    return this;
  }

  public RClassGeneratorActionBuilder setClassJarOut(Artifact classJarOut) {
    this.classJarOut = classJarOut;
    return this;
  }

  public ResourceContainer build(AndroidDataContext dataContext, ResourceContainer primary) {
    build(dataContext, primary.getRTxt(), ProcessedAndroidManifest.from(primary));

    return primary.toBuilder().setJavaClassJar(classJarOut).build();
  }

  public ResourceApk build(AndroidDataContext dataContext, ProcessedAndroidData data) {
    build(dataContext, data.getRTxt(), data.getManifest());

    return data.withValidatedResources(classJarOut);
  }

  private void build(
      AndroidDataContext dataContext, Artifact rTxt, ProcessedAndroidManifest manifest) {
    BusyBoxActionBuilder builder =
        BusyBoxActionBuilder.create(dataContext, "GENERATE_BINARY_R")
            .addInput("--primaryRTxt", rTxt)
            .addInput("--primaryManifest", manifest.getManifest())
            .maybeAddFlag("--packageForR", manifest.getPackage());

    if (dependencies != null && !dependencies.getResourceContainers().isEmpty()) {
      builder
          .addTransitiveFlagForEach(
              "--library",
              dependencies.getResourceContainers(),
              version == AndroidAaptVersion.AAPT2 ? AAPT2_CONVERTER : AAPT_CONVERTER)
          .addTransitiveInputValues(
              version == AndroidAaptVersion.AAPT2
                  ? dependencies.getTransitiveAapt2RTxt()
                  : dependencies.getTransitiveRTxt())
          .addTransitiveInputValues(dependencies.getTransitiveManifests());
    }

    builder
        .addOutput("--classJarOutput", classJarOut)
        .addLabelFlag("--targetLabel")
        .buildAndRegister("Generating R Classes", "RClassGenerator");
  }

  private static Function<ValidatedAndroidData, String> chooseDepsToArg(
      final AndroidAaptVersion version) {
    // Use an anonymous inner class for serialization.
    return new Function<ValidatedAndroidData, String>() {
      @Override
      public String apply(ValidatedAndroidData container) {
        Artifact rTxt =
            version == AndroidAaptVersion.AAPT2 ? container.getAapt2RTxt() : container.getRTxt();
        return (rTxt != null ? rTxt.getExecPath() : "")
            + ","
            + (container.getManifest() != null ? container.getManifest().getExecPath() : "");
      }
    };
  }
}
