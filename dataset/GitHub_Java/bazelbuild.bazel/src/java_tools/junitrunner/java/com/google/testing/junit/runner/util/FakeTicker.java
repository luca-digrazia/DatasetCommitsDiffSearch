// Copyright 2010 The Bazel Authors. All Rights Reserved.
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

package com.google.testing.junit.runner.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Ticker whose value can be advanced programmatically in test.
 * <p> The ticker can be configured so that the time is incremented whenever {@link #read} is
 * called.
 *
 * <p> This class is thread-safe.
 */
public class FakeTicker extends Ticker {

  private final AtomicLong nanos = new AtomicLong();
  private volatile long autoIncrementStepNanos;

  /** Advances the ticker value by {@code time} in {@code timeUnit}. */
  public FakeTicker advance(long time, TimeUnit timeUnit) {
    return advance(timeUnit.toNanos(time));
  }

  /** Advances the ticker value by {@code nanoseconds}. */
  public FakeTicker advance(long nanoseconds) {
    nanos.addAndGet(nanoseconds);
    return this;
  }

  /**
   * Sets the increment applied to the ticker whenever it is queried.
   *
   * <p>The default behavior is to auto increment by zero. i.e: The ticker is left unchanged when
   * queried.
   */
  public FakeTicker setAutoIncrementStep(long autoIncrementStep, TimeUnit timeUnit) {
    if (autoIncrementStep < 0) {
      throw new IllegalArgumentException("May not auto-increment by a negative amount");
    }
    this.autoIncrementStepNanos = timeUnit.toNanos(autoIncrementStep);
    return this;
  }

  @Override
  public long read() {
    return nanos.getAndAdd(autoIncrementStepNanos);
  }
}

