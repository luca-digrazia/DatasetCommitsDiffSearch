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
package com.google.devtools.build.lib.skylarkbuildapi.java;

import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.skylarkbuildapi.FileApi;
import com.google.devtools.build.lib.skylarkbuildapi.core.ProviderApi;
import com.google.devtools.build.lib.skylarkbuildapi.core.StructApi;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkConstructor;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.Depset;
import com.google.devtools.build.lib.syntax.EvalException;

/** Provides information about generated proto extensions. */
@SkylarkModule(
    name = "GeneratedExtensionRegistryProvider",
    doc = "Information about generated proto extensions.",
    category = SkylarkModuleCategory.PROVIDER)
public interface GeneratedExtensionRegistryProviderApi<FileT extends FileApi> extends StructApi {

  /** The name of the provider for this info object. */
  String NAME = "GeneratedExtensionRegistryProvider";

  @SkylarkCallable(name = "rule_label", structField = true, doc = "", documented = false)
  Label getGeneratingRuleLabel();

  @SkylarkCallable(name = "lite", structField = true, doc = "", documented = false)
  boolean isLite();

  @SkylarkCallable(name = "class_jar", structField = true, doc = "", documented = false)
  FileT getClassJar();

  @SkylarkCallable(name = "src_jar", structField = true, doc = "", documented = false)
  FileT getSrcJar();

  @SkylarkCallable(name = "inputs", structField = true, doc = "", documented = false)
  Depset /*<FileT>*/ getInputsForStarlark();

  /** The provider implementing this can construct the GeneratedExtensionRegistryProvider. */
  @SkylarkModule(name = "Provider", doc = "", documented = false)
  interface Provider<FileT extends FileApi> extends ProviderApi {

    @SkylarkCallable(
        name = NAME,
        doc = "The <code>GeneratedExtensionRegistryProvider</code> constructor.",
        parameters = {
          @Param(
              name = "generatingRuleLabel",
              doc = "Rule label for which this registry was built",
              positional = true,
              named = false,
              type = Label.class),
          @Param(
              name = "isLite",
              doc = "If this registry was generated for lite or full runtime",
              positional = true,
              named = false,
              type = Boolean.class),
          @Param(
              name = "classJar",
              doc = "Class jar generated by the registry",
              positional = true,
              named = false,
              type = FileApi.class),
          @Param(
              name = "srcJar",
              doc = "Source jar generated by the registry",
              positional = true,
              named = false,
              type = FileApi.class),
          @Param(
              name = "inputs",
              doc = "Proto jars used to generate the registry",
              positional = true,
              named = false,
              type = Depset.class,
              generic1 = FileApi.class),
        },
        selfCall = true)
    @SkylarkConstructor(
        objectType = GeneratedExtensionRegistryProviderApi.class,
        receiverNameForDoc = NAME)
    GeneratedExtensionRegistryProviderApi<FileT> create(
        Label generatingRuleLabel, boolean isLite, FileT classJar, FileT srcJar, Depset inputs)
        throws EvalException;
  }
}
