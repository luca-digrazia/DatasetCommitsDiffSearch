// Copyright 2015 The Bazel Authors. All rights reserved.
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

import static com.google.devtools.build.lib.syntax.Type.STRING;

import com.google.common.base.Strings;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.RunfilesProvider;
import com.google.devtools.build.lib.rules.RuleConfiguredTargetFactory;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.apple.DottedVersion;
import com.google.devtools.build.lib.rules.apple.Platform;

/**
 * Implementation for the "ios_device" rule.
 */
public final class IosDevice implements RuleConfiguredTargetFactory {
  @Override
  public ConfiguredTarget create(RuleContext context) throws InterruptedException {
    String iosVersionAttribute = context.attributes().get("ios_version", STRING);
    DottedVersion iosVersion;
    if (!Strings.isNullOrEmpty(iosVersionAttribute)) {
      iosVersion = DottedVersion.fromString(iosVersionAttribute);
    } else {
      iosVersion = context.getFragment(AppleConfiguration.class)
          .getSdkVersionForPlatform(Platform.IOS_SIMULATOR);
    }

    IosDeviceProvider provider =
        new IosDeviceProvider.Builder()
            .setType(context.attributes().get("type", STRING))
            .setIosVersion(iosVersion)
            .setLocale(context.attributes().get("locale", STRING))
            .build();

    return new RuleConfiguredTargetBuilder(context)
        .add(RunfilesProvider.class, RunfilesProvider.EMPTY)
        .add(IosDeviceProvider.class, provider)
        .add(IosTestSubstitutionProvider.class, provider.iosTestSubstitutionProvider())
        .build();
  }
}
