/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.devtools.build.android.desugar.typeadapter.java.time;

/** Converts types between the desugar-mirrored and desugar-shadowed {@link java.time.Period}. */
@SuppressWarnings("AndroidJdkLibsChecker")
public abstract class PeriodConverter {

  private PeriodConverter() {}

  public static j$.time.Period from(java.time.Period period) {
    return period == null
        ? null
        : j$.time.Period.of(period.getYears(), period.getMonths(), period.getDays());
  }

  public static java.time.Period to(j$.time.Period period) {
    return period == null
        ? null
        : java.time.Period.of(period.getYears(), period.getMonths(), period.getDays());
  }
}
