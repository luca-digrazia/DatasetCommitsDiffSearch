/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.core;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Params;
import org.graalvm.compiler.debug.DebugInitializationParticipant;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.internal.method.MethodMetricsPrinter;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

/**
 * A service provider that may modify the initialization of {@link Debug} based on the values
 * specified for various {@link GraalDebugConfig} options.
 */
@ServiceProvider(DebugInitializationParticipant.class)
public class GraalDebugInitializationParticipant implements DebugInitializationParticipant {

    @Override
    public void apply(Params params) {
        OptionValues options = params.getOptions();
        if (GraalDebugConfig.areDebugScopePatternsEnabled(options)) {
            params.enable = true;
        }
        if ("".equals(GraalDebugConfig.Options.Count.getValue(options))) {
            params.enableUnscopedCounters = true;
        }
        if ("".equals(GraalDebugConfig.Options.MethodMeter.getValue(options))) {
            params.enableUnscopedMethodMetrics = true;
            // mm requires full debugging support
            params.enable = true;
        }
        if ("".equals(GraalDebugConfig.Options.Time.getValue(options))) {
            params.enableUnscopedTimers = true;
        }
        if ("".equals(GraalDebugConfig.Options.TrackMemUse.getValue(options))) {
            params.enableUnscopedMemUseTrackers = true;
        }
        // unscoped counters/timers/mem use trackers/method metrics should respect method filter
        // semantics
        if (!params.enable && (params.enableUnscopedMemUseTrackers || params.enableUnscopedMethodMetrics || params.enableUnscopedCounters || params.enableUnscopedTimers) &&
                        GraalDebugConfig.isNotEmpty(GraalDebugConfig.Options.MethodFilter, options)) {
            params.enable = true;
            params.enableMethodFilter = true;
        }

        if (!params.enable && GraalDebugConfig.Options.DumpOnPhaseChange.getValue(options) != null) {
            params.enable = true;
        }

        if (!params.enableUnscopedMethodMetrics && GraalDebugConfig.Options.MethodMeter.getValue(options) != null) {
            // mm requires full debugging support
            params.enable = true;
        }

        if (GraalDebugConfig.isGlobalMetricsInterceptedByMethodMetricsEnabled(options)) {
            if (!params.enable) {
                TTY.println("WARNING: MethodMeter is disabled but GlobalMetricsInterceptedByMethodMetrics is enabled. Ignoring MethodMeter and GlobalMetricsInterceptedByMethodMetrics.");
            } else {
                parseMethodMetricsDebugValueInterception(params, options);
            }
        }
        if (GraalDebugConfig.isNotEmpty(GraalDebugConfig.Options.MethodMeter, options) || params.enableUnscopedMethodMetrics) {
            if (!MethodMetricsPrinter.methodMetricsDumpingEnabled(options)) {
                TTY.println("WARNING: MethodMeter is enabled but MethodMeter dumping is disabled. Output will not contain MethodMetrics.");
            }
        }
    }

    private static void parseMethodMetricsDebugValueInterception(Params params, OptionValues options) {
        String interceptionGroup = GraalDebugConfig.Options.GlobalMetricsInterceptedByMethodMetrics.getValue(options);
        boolean intercepted = false;
        if (interceptionGroup.contains("Timers")) {
            params.interceptTime = true;
            intercepted = true;
        }
        if (interceptionGroup.contains("Counters")) {
            params.interceptCount = true;
            intercepted = true;
        }
        if (interceptionGroup.contains("MemUseTrackers")) {
            params.interceptMem = true;
            intercepted = true;
        }

        if (!intercepted) {
            TTY.println("WARNING: Ignoring GlobalMetricsInterceptedByMethodMetrics as the supplied argument does not contain Timers/Counters/MemUseTrackers.");
        }
    }
}
