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

package com.google.devtools.build.skydoc.fakebuildapi.android;

import com.google.devtools.build.lib.skylarkbuildapi.android.AndroidDeviceBrokerInfoApi;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Printer;

/**
 * Fake implementation of {@link AndroidDeviceBrokerInfoApi}.
 */
public class FakeAndroidDeviceBrokerInfo implements AndroidDeviceBrokerInfoApi {

  @Override
  public String toProto() throws EvalException {
    return "";
  }

  @Override
  public String toJson() throws EvalException {
    return "";
  }

  @Override
  public void repr(Printer printer) {}

  /**
   * Fake implementation of {@link AndroidDeviceBrokerInfoApiProvider}.
   */
  public static class FakeAndroidDeviceBrokerInfoProvider
      implements AndroidDeviceBrokerInfoApiProvider {

    @Override
    public AndroidDeviceBrokerInfoApi createInfo(String type) throws EvalException {
      return new FakeAndroidDeviceBrokerInfo();
    }

    @Override
    public void repr(Printer printer) {}
  }
}
