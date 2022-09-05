// Copyright 2015 Google Inc. All rights reserved.
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
package com.google.devtools.build.lib.rules.android;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

/**
 * A provider for targets that build _deploy.jar files.
 */
@Immutable
public final class AndroidDeployJarProvider implements TransitiveInfoProvider {

  private final Artifact deployJar;


  public AndroidDeployJarProvider(Artifact deployJar) {
    this.deployJar = deployJar;
  }

  /**
   * Returns the deploy jar.
   */
  public Artifact getDeployJar() {
    return deployJar;
  }
}
