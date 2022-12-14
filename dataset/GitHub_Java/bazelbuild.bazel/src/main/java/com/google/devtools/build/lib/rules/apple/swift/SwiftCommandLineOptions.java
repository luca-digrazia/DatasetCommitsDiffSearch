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

package com.google.devtools.build.lib.rules.apple.swift;

import com.google.devtools.build.lib.analysis.config.FragmentOptions;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import java.util.List;

/** Command-line options for building with Swift tools. */
@AutoCodec(strategy = AutoCodec.Strategy.PUBLIC_FIELDS)
public class SwiftCommandLineOptions extends FragmentOptions {
  @Option(
    name = "swiftcopt",
    allowMultiple = true,
    defaultValue = "",
    documentationCategory = OptionDocumentationCategory.OUTPUT_PARAMETERS,
    effectTags = {OptionEffectTag.ACTION_COMMAND_LINES},
    help = "Additional options to pass to Swift compilation."
  )
  public List<String> copts;

  @Option(
    name = "swift_whole_module_optimization",
    defaultValue = "false",
    documentationCategory = OptionDocumentationCategory.OUTPUT_PARAMETERS,
    effectTags = {OptionEffectTag.ACTION_COMMAND_LINES},
    help = "Whether to enable Whole Module Optimization"
  )
  public boolean enableWholeModuleOptimization;
}
