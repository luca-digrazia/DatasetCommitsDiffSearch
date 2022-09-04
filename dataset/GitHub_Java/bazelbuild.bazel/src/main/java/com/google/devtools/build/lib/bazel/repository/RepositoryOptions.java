//Copyright 2016 The Bazel Authors. All rights reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.devtools.build.lib.bazel.repository;

import com.google.devtools.build.lib.util.OptionsUtils;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser.OptionUsageRestrictions;

/**
 * Command-line options for repositories.
 */
public class RepositoryOptions extends OptionsBase {

  @Option(
    name = "experimental_repository_cache",
    defaultValue = "null",
    optionUsageRestrictions = OptionUsageRestrictions.HIDDEN,
    converter = OptionsUtils.PathFragmentConverter.class,
    help =
        "Specifies the cache location of the downloaded values obtained "
            + "during the fetching of external repositories."
  )
  public PathFragment experimentalRepositoryCache;

}