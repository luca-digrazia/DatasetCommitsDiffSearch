// Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.devtools.build.lib.skylarkbuildapi.FileApi;
import com.google.devtools.build.lib.skylarkbuildapi.ProviderApi;
import com.google.devtools.build.lib.skylarkbuildapi.StructApi;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkConstructor;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;

/** A provider of the final Jar to be dexed for targets that build APKs. */
@SkylarkModule(
    name = "AndroidPreDexJarInfo",
    doc = "Information about the final Jar to be dexed for targets that build APKs.",
    category = SkylarkModuleCategory.PROVIDER)
public interface AndroidPreDexJarProviderApi<FileT extends FileApi> extends StructApi {
  /** Name of this info object. */
  public static String NAME = "AndroidPreDexJarInfo";

  /** Returns the Java package. */
  @SkylarkCallable(name = "pre_dex_jar", structField = true, doc = "", documented = false)
  FileT getPreDexJar();

  /** The provider implementing this can construct the AndroidIdeInfo provider. */
  @SkylarkModule(name = "Provider", doc = "", documented = false)
  public interface Provider<FileT extends FileApi> extends ProviderApi {

    @SkylarkCallable(
        name = NAME,
        doc = "The <code>AndroidPreDexJarInfo</code> constructor.",
        parameters = {
          @Param(
              name = "pre_dex_jar",
              doc = "The jar to be dexed.",
              positional = true,
              named = false,
              type = FileApi.class),
        },
        selfCall = true)
    @SkylarkConstructor(objectType = AndroidPreDexJarProviderApi.class)
    public AndroidPreDexJarProviderApi<FileT> createInfo(FileT preDexJar) throws EvalException;
  }
}
