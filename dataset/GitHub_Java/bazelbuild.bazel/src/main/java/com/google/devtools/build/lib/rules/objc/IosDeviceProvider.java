// Copyright 2015 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.objc;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Substitution;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.packages.NativeProvider;
import com.google.devtools.build.lib.rules.apple.DottedVersion;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import javax.annotation.Nullable;

/** Provider that describes a simulator device. */
@Immutable
@SkylarkModule(
    name = "IosDevice",
    category = SkylarkModuleCategory.PROVIDER,
    doc = "<b>Deprecated. Use the new Skylark testing rules instead.</b>"
)
public final class IosDeviceProvider extends NativeInfo {
  /** A builder of {@link IosDeviceProvider}s. */
  public static final class Builder {
    private String type;
    private DottedVersion iosVersion;
    private String locale;
    @Nullable
    private DottedVersion xcodeVersion;

    /**
     * Sets the hardware type of the device, corresponding to the {@code simctl} device type.
     */
    public Builder setType(String type) {
      this.type = type;
      return this;
    }

    /**
     * Sets the iOS version of the simulator to use. This may be different than the iOS sdk version
     * used to build the application.
     */
    public Builder setIosVersion(DottedVersion iosVersion) {
      this.iosVersion = iosVersion;
      return this;
    }

    /**
     * Sets the xcode version to obtain the iOS simulator from. This may be different than the
     * xcode version with which the application was built.
     */
    public Builder setXcodeVersion(@Nullable DottedVersion xcodeVersion) {
      this.xcodeVersion = xcodeVersion;
      return this;
    }

    public Builder setLocale(String locale) {
      this.locale = locale;
      return this;
    }

    public IosDeviceProvider build() {
      return new IosDeviceProvider(this);
    }
  }

  /** Skylark name for the IosDeviceProvider. */
  public static final String SKYLARK_NAME = "IosDevice";

  /** Skylark constructor and identifier for the IosDeviceProvider. */
  public static final NativeProvider<IosDeviceProvider> SKYLARK_CONSTRUCTOR =
      new NativeProvider<IosDeviceProvider>(IosDeviceProvider.class, SKYLARK_NAME) {};

  private final String type;
  private final DottedVersion iosVersion;
  private final DottedVersion xcodeVersion;
  private final String locale;

  private IosDeviceProvider(Builder builder) {
    super(SKYLARK_CONSTRUCTOR);
    this.type = Preconditions.checkNotNull(builder.type);
    this.iosVersion = Preconditions.checkNotNull(builder.iosVersion);
    this.locale = Preconditions.checkNotNull(builder.locale);
    this.xcodeVersion = builder.xcodeVersion;
  }

  @SkylarkCallable(
      name = "ios_version",
      doc = "The iOS version of the simulator to use.",
      structField = true
  )
  public String getIosVersionString() {
    return iosVersion.toString();
  }

  @SkylarkCallable(
      name = "xcode_version",
      doc = "The xcode version to obtain the iOS simulator from, or <code>None</code> if unknown.",
      structField = true,
      allowReturnNones = true
  )
  @Nullable
  public String getXcodeVersionString() {
    return xcodeVersion != null ? xcodeVersion.toString() : null;
  }

  @SkylarkCallable(
      name = "type",
      doc = "The hardware type of the device, corresponding to the simctl device type.",
      structField = true
  )
  public String getType() {
    return type;
  }

  public DottedVersion getIosVersion() {
    return iosVersion;
  }

  @Nullable
  public DottedVersion getXcodeVersion() {
    return xcodeVersion;
  }

  public String getLocale() {
    return locale;
  }

  /**
   * Returns an {@code IosTestSubstitutionProvider} exposing substitutions indicating how to run a
   * test in this particular iOS simulator configuration.
   */
  public IosTestSubstitutionProvider iosTestSubstitutionProvider() {
    return new IosTestSubstitutionProvider(
        ImmutableList.of(
            Substitution.of("%(device_type)s", getType()),
            Substitution.of("%(simulator_sdk)s", getIosVersion().toString()),
            Substitution.of("%(locale)s", getLocale())));
  }
}
