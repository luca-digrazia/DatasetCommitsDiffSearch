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

package com.google.devtools.build.lib.skylarkinterface.processor.testsources;

import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.syntax.Environment;

/**
 * Test case for a SkylarkCallable method which specifies skylark-info parameters in the incorrect
 * relative order.
 */
public class SkylarkInfoParamsWrongOrder {

  @SkylarkCallable(
    name = "skylark_info_params_wrong_order",
    documented = false,
    parameters = {
      @Param(name = "some_param", type = String.class, named = true)
    },
    useLocation = true,
    useEnvironment = true
  )
  public String threeArgMethod(
      // Note environment should come after location.
      String someParam, Environment environment, Location location) {
    return "bar";
  }
}
