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
package com.google.devtools.build.lib.skylarkbuildapi.android;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.skylarkbuildapi.ProviderApi;
import com.google.devtools.build.lib.skylarkbuildapi.StructApi;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkConstructor;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkDict;

/** */
@SkylarkModule(
    name = "AndroidFeatureFlagSetInfo",
    doc = "Information about the android_binary feature flags",
    category = SkylarkModuleCategory.PROVIDER)
public interface AndroidFeatureFlagSetProviderApi extends StructApi {

  public static final String NAME = "AndroidFeatureFlagSet";

  @SkylarkCallable(
      name = "flags",
      doc = "Returns the flags contained by the provider.",
      structField = true)
  ImmutableMap<Label, String> getFlagMap();

  /** The provider implementing this can construct the AndroidIdeInfo provider. */
  @SkylarkModule(name = "Provider", doc = "", documented = false)
  public interface Provider extends ProviderApi {

    @SkylarkCallable(
        name = NAME,
        doc = "The <code>AndroidFeatureFlagSetProvider</code> constructor.",
        parameters = {
          @Param(
              name = "flags",
              doc = "Map of flags",
              positional = true,
              named = false,
              type = SkylarkDict.class),
        },
        selfCall = true)
    @SkylarkConstructor(objectType = AndroidFeatureFlagSetProviderApi.class)
    AndroidFeatureFlagSetProviderApi create(SkylarkDict<Label, String> flags) throws EvalException;
  }
}
