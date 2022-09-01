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

package com.google.devtools.build.lib;

import com.google.common.collect.ImmutableList;

/**
 * Various constants required by Bazel.
 *
 * <p>The extra {@code .toString()} calls are there so that javac doesn't inline these constants
 * so that we can replace this class file in the .jar after Bazel was built.
 */
public class Constants {
  private Constants() {
  }

  public static final String PRODUCT_NAME = "bazel".toString();
  public static final ImmutableList<String> DEFAULT_PACKAGE_PATH = ImmutableList.of("%workspace%");
  public static final String MAIN_RULE_CLASS_PROVIDER =
      "com.google.devtools.build.lib.bazel.rules.BazelRuleClassProvider".toString();
  public static final ImmutableList<String> IGNORED_TEST_WARNING_PREFIXES = ImmutableList.of();
  public static final String RUNFILES_PREFIX = "".toString();

  public static final ImmutableList<String> WATCHFS_BLACKLIST = ImmutableList.of();

  public static final String PRELUDE_FILE_DEPOT_RELATIVE_PATH = "tools/build_rules/prelude_bazel";
}
