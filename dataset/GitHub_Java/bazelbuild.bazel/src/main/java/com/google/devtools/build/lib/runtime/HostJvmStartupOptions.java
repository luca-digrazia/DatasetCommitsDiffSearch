// Copyright 2014 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.runtime;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

/**
 * Options that will be evaluated by the blaze client startup code only.
 *
 * The only reason we have this interface is that we'd like to print a nice
 * help page for the client startup options. These options do not affect the
 * server's behavior in any way.
 */
public class HostJvmStartupOptions extends OptionsBase {

  @Option(name = "host_jvm_args",
          defaultValue = "", // NOTE: purely decorative!  See BlazeServerStartupOptions.
          category = "host jvm startup",
          help = "Flags to pass to the JVM executing Blaze.")
  public String hostJvmArgs;

  @Option(name = "host_jvm_profile",
          defaultValue = "", // NOTE: purely decorative!  See BlazeServerStartupOptions.
          category = "host jvm startup",
          help = "Run the JVM executing Blaze in the given profiler. Blaze will search for "
              + "certain hardcoded paths based on the profiler.")
  public String hostJvmProfile;

  @Option(name = "host_jvm_debug",
          defaultValue = "false", // NOTE: purely decorative!  See BlazeServerStartupOptions.
          category = "host jvm startup",
          help = "Run the JVM executing Blaze so that it listens for a connection from a "
              + "JDWP-compliant debugger.")
  public boolean hostJvmDebug;
}
