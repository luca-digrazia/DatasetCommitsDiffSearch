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

package com.google.devtools.build.lib.rules.apple;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import javax.annotation.Nullable;

/**
 * A tuple containing information about a version of xcode and its properties. 
 */
public class XcodeVersionProperties {
  @VisibleForTesting public static final String DEFAULT_IOS_SDK_VERSION = "8.4";

  private final Optional<DottedVersion> xcodeVersion;
  private final DottedVersion defaultIosSdkVersion;

  /**
   * Creates and returns a tuple representing no known xcode property information (defaults are
   * used where applicable).
   */
  // TODO(bazel-team): The xcode version should be a well-defined value, either specified by the
  // user, evaluated on the local system, or set to a sensible default.
  // Unfortunately, until the local system evaluation hook is created, this constraint would break
  // some users.
  public static XcodeVersionProperties unknownXcodeVersionProperties() {
    return new XcodeVersionProperties(null);
  }

  /**
   * Constructor for when only the xcode version is specified, but no property information
   * is specified.
   */
  XcodeVersionProperties(DottedVersion xcodeVersion) {
    this(xcodeVersion, null);
  }

  /**
   * General constructor. Some (nullable) properties may be left unspecified. In these cases,
   * a semi-sensible default will be assigned to the property value. 
   */
  XcodeVersionProperties(DottedVersion xcodeVersion,
      @Nullable String defaultIosSdkVersion) {
    this.xcodeVersion = Optional.fromNullable(xcodeVersion);
    this.defaultIosSdkVersion = (Strings.isNullOrEmpty(defaultIosSdkVersion))
        ? DottedVersion.fromString(DEFAULT_IOS_SDK_VERSION)
        : DottedVersion.fromString(defaultIosSdkVersion);
  }

  /**
   * Returns the xcode version, or {@link Optional#absent} if the xcode version is unknown.
   */
  public Optional<DottedVersion> getXcodeVersion() {
    return xcodeVersion;
  }

  /**
   * Returns the default ios sdk version to use if this xcode version is in use.
   */
  public DottedVersion getDefaultIosSdkVersion() {
    return defaultIosSdkVersion;
  }
}
