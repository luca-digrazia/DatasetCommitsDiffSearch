// Copyright 2014 Google Inc. All rights reserved.
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

import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.Options;
import com.google.devtools.build.lib.analysis.config.BuildOptions;
import com.google.devtools.build.lib.analysis.config.ConfigurationEnvironment;
import com.google.devtools.build.lib.analysis.config.ConfigurationFragmentFactory;
import com.google.devtools.build.lib.analysis.config.InvalidConfigurationException;

/**
 * A loader that creates ObjcConfiguration instances based on Objective-C configurations and
 * command-line options.
 */
public class ObjcConfigurationLoader implements ConfigurationFragmentFactory {
  @Override
  public ObjcConfiguration create(ConfigurationEnvironment env, BuildOptions buildOptions)
      throws InvalidConfigurationException {
    Options options = buildOptions.get(BuildConfiguration.Options.class);
    ObjcCommandLineOptions objcOptions = buildOptions.get(ObjcCommandLineOptions.class);

    return new ObjcConfiguration(objcOptions, options);
  }

  @Override
  public Class<? extends BuildConfiguration.Fragment> creates() {
    return ObjcConfiguration.class;
  }
}
