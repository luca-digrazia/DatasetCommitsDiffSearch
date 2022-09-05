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

package com.google.devtools.build.lib.analysis;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.analysis.AnalysisUtils.checkProvider;
import static org.junit.Assert.fail;

import com.google.auto.value.AutoValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AnalysisUtilsTest {

  @Test
  public void checkProviderSucceedsOnClassAnnotatedWithAutoValue() {
    checkProvider(AutoValuedClass.class);
  }

  @Test
  public void checkProviderFailsOnClassGeneratredByAutoValue() {
    try {
      checkProvider(AutoValue_AnalysisUtilsTest_AutoValuedClass.class);
      fail("Expected IllegalArgumentException, but nothing was thrown.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("generated by @AutoValue");
    }
  }

  // Note: this has to be defined outside of checkProviderFailsOnClassGeneratredByAutoValue() so it
  // can be static, which is required by @AutoValue.
  @AutoValue
  abstract static class AutoValuedClass implements TransitiveInfoProvider {
    abstract int foo();
  }
}
