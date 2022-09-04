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

package com.google.devtools.build.lib.rules.java.proto;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.rules.java.JavaCommon;
import com.google.devtools.build.lib.rules.java.JavaSemantics;
import com.google.devtools.build.lib.rules.java.JavaToolchainProvider;

/** Helper class to centralize Javac flags handling. */
public class ProtoJavacOpts {

  /**
   * Returns javacopts for compiling the Java source files generated by the proto compiler.
   *
   * <p>See java_toolchain.compatible_javacopts for the javacopts required for protos.
   */
  public static ImmutableList<String> constructJavacOpts(RuleContext ruleContext) {
    JavaToolchainProvider toolchain = JavaToolchainProvider.from(ruleContext);
    return ImmutableList.<String>builder()
        .addAll(toolchain.getJavacOptions(ruleContext))
        .addAll(toolchain.getCompatibleJavacOptions(JavaSemantics.PROTO_JAVACOPTS_KEY))
        .addAll(JavaCommon.computePerPackageJavacOpts(ruleContext, toolchain))
        .build();
  }

  // Static access only
  private ProtoJavacOpts() {}
}
