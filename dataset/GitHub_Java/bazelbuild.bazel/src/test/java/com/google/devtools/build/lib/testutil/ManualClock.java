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

package com.google.devtools.build.lib.testutil;

import com.google.devtools.build.lib.util.Clock;

import java.util.concurrent.TimeUnit;

/**
 * A fake clock for testing.
 */
public final class ManualClock implements Clock {
  private long currentTimeMillis = 0L;

  @Override
  public long currentTimeMillis() {
    return currentTimeMillis;
  }

  /**
   * Nano time should not be confused with wall time. Nano time is only mean to compute time
   * differences. Because of this, we shift the time returned by 1000s, to test that the users
   * of this class do not rely on nanoTime == currentTimeMillis.
   */
  @Override
  public long nanoTime() {
    return TimeUnit.MILLISECONDS.toNanos(currentTimeMillis)
        + TimeUnit.SECONDS.toNanos(1000);
  }

  public void advanceMillis(long time) {
    currentTimeMillis += time;
  }
}
