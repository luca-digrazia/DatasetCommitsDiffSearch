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

import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.skylarkbuildapi.FileApi;
import com.google.devtools.build.lib.skylarkbuildapi.ProviderApi;
import com.google.devtools.build.lib.skylarkbuildapi.StructApi;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;

/**
 * Provider of transitively available ZIPs of native libs that should be directly copied into the
 * APK.
 */
@SkylarkModule(name = "AndroidNativeLibsInfo", doc = "", documented = false)
public interface AndroidNativeLibsInfoApi<FileT extends FileApi> extends StructApi {

  /**
   * Name of this info object.
   */
  public static String NAME = "AndroidNativeLibsInfo";

  @SkylarkCallable(
      name = "native_libs",
      doc = "Returns the native libraries produced by the rule.",
      structField = true)
  NestedSet<FileT> getNativeLibs();

  /** Provider for {@link AndroidNativeLibsInfoApi}. */
  @SkylarkModule(name = "Provider", doc = "", documented = false)
  public interface AndroidNativeLibsInfoApiProvider extends ProviderApi {

    @SkylarkCallable(
        name = "AndroidNativeLibsInfo",
        doc = "The <code>AndroidNativeLibsInfo</code> constructor.",
        parameters = {
            @Param(
                name = "native_libs",
                type = SkylarkNestedSet.class,
                generic1 = FileApi.class,
                named = true,
                doc = "The native libraries produced by the rule."
            ),
        },
        selfCall = true)
    public AndroidNativeLibsInfoApi<?> createInfo(SkylarkNestedSet nativeLibs) throws EvalException;
  }
}
