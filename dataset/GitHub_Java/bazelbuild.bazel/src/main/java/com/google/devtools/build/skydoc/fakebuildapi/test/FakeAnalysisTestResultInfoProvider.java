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

package com.google.devtools.build.skydoc.fakebuildapi.test;

import com.google.devtools.build.lib.skylarkbuildapi.test.AnalysisTestResultInfoApi;
import com.google.devtools.build.lib.skylarkbuildapi.test.AnalysisTestResultInfoApi.AnalysisTestResultInfoProviderApi;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;

/**
 * Fake implementation of {@link AnalysisTestResultInfoProviderApi}.
 */
public class FakeAnalysisTestResultInfoProvider implements AnalysisTestResultInfoProviderApi {

  @Override
  public AnalysisTestResultInfoApi testResultInfo(Boolean success, String message) {
    return null;
  }

  @Override
  public void repr(SkylarkPrinter printer) {}
}
