// Copyright 2019 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.android.desugar.nest.functional.testsrc.complexcase;

/** A nest for testing private interface methods desugaring. */
public interface Alpha {

  long VAL = 1000L;

  static long publicStaticMethod(Alpha alpha, Bravo bravo, long x, int y) {
    return Alpha.VAL + bravo.abstractMethod(x, y) + privateStaticMethod(alpha, bravo, x, y);
  }

  static long privateStaticMethod(Alpha alpha, Bravo bravo, long x, int y) {
    return Bravo.VAL + alpha.abstractMethod(x, y) + Bravo.publicStaticMethod(alpha, bravo, x, y);
  }

  default long defaultMethod(Bravo bravo, long x, int y) {
    return Alpha.VAL + bravo.abstractMethod(x, y) + privateInstanceMethod(bravo, x, y);
  }

  private long privateInstanceMethod(Bravo bravo, long x, int y) {
    return Bravo.VAL + abstractMethod(x, y) + bravo.defaultMethod(this, x, y);
  }

  long abstractMethod(long x, int y);
}
