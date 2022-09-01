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

package com.google.devtools.build.lib.rules.platform;

import com.google.devtools.build.lib.analysis.platform.ConstraintSettingInfo;
import com.google.devtools.build.lib.analysis.platform.ConstraintValueInfo;
import com.google.devtools.build.lib.analysis.platform.PlatformInfo;
import com.google.devtools.build.lib.analysis.platform.ToolchainInfo;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.ClassObjectConstructor;
import com.google.devtools.build.lib.packages.ToolchainConstructor;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;

/** Skylark namespace used to interact with the platform APIs. */
@SkylarkModule(
  name = "platform_common",
  doc = "Functions for Skylark to interact with the platform APIs."
)
public class PlatformCommon {

  @SkylarkCallable(
    name = PlatformInfo.SKYLARK_NAME,
    doc =
        "The provider constructor for PlatformInfo. The constructor takes the list of "
            + "ConstraintValueInfo providers that defines the platform.",
    structField = true
  )
  public ClassObjectConstructor getPlatformInfoConstructor() {
    return PlatformInfo.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = ConstraintSettingInfo.SKYLARK_NAME,
    doc =
        "The provider constructor for ConstraintSettingInfo. The constructor takes the label that "
            + "uniquely identifies the constraint (and which should always be ctx.label).",
    structField = true
  )
  public ClassObjectConstructor getConstraintSettingInfoConstructor() {
    return ConstraintSettingInfo.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = ConstraintValueInfo.SKYLARK_NAME,
    doc =
        "The provider constructor for ConstraintValueInfo. The constructor takes the label that "
            + "uniquely identifies the constraint value (and which should always be ctx.label), "
            + "and the ConstraintSettingInfo which the value belongs to.",
    structField = true
  )
  public ClassObjectConstructor getConstraintValueInfoConstructor() {
    return ConstraintValueInfo.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkCallable(
    name = ToolchainInfo.SKYLARK_NAME,
    doc =
        "The provider constructor for ToolchainInfo. The constructor takes the type of the "
            + "toolchain, and a map of the toolchain's data.",
    structField = true
  )
  public ClassObjectConstructor getToolchainInfoConstructor() {
    return ToolchainInfo.SKYLARK_CONSTRUCTOR;
  }

  @SkylarkSignature(
    name = "toolchain_type",
    doc =
        "Creates a new ToolchainConstructor instance, which can be used to create new "
            + "ToolchainInfo instances. The constructor takes a list of execution constraints, a "
            + "list of target constraints, and then other data which may be used by a rule author.",
    documented = false,
    objectType = PlatformCommon.class,
    returnType = ToolchainConstructor.class,
    parameters = {
      @Param(name = "self", type = PlatformCommon.class, doc = "the platform_rules instance"),
    },
    useLocation = true
  )
  private static final BuiltinFunction createToolchainType =
      new BuiltinFunction("toolchain_type") {
        @SuppressWarnings("unchecked")
        public ToolchainConstructor invoke(PlatformCommon self, Location loc) throws EvalException {
          return new SkylarkToolchainConstructor(loc);
        }
      };

  static {
    SkylarkSignatureProcessor.configureSkylarkFunctions(PlatformCommon.class);
  }
}
