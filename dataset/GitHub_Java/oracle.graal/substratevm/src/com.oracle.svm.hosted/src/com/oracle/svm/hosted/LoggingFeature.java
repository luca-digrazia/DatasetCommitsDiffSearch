/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.hosted;

import java.util.logging.LogManager;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeClassInitialization;
import org.graalvm.nativeimage.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

@AutomaticFeature
public class LoggingFeature implements Feature {

    public static class Options {
        @Option(help = "When enabled, logging feature details are printed.", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> TraceLoggingFeature = new HostedOptionKey<>(false);
    }

    private final boolean trace = LoggingFeature.Options.TraceLoggingFeature.getValue();

    private boolean reflectionConfigured = false;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        /* Ensure that the log manager is initialized and the initial configuration is read. */
        LogManager.getLogManager();

        /*
         * Rerunning the initialization of SimpleFormatter at run time is required so that
         * SimpleFormatter.format is correctly set to a custom provided value instead of the
         * LoggingSupport.DEFAULT_FORMAT default value.
         */

        trace("Registering " + java.util.logging.SimpleFormatter.class + " for runtime re-initialization.");
        RuntimeClassInitialization.rerunClassInitialization(java.util.logging.SimpleFormatter.class);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (!reflectionConfigured && access.getMetaAccess().optionalLookupJavaType(java.util.logging.Logger.class).isPresent()) {
            registerForReflection(java.util.logging.ConsoleHandler.class);
            registerForReflection(java.util.logging.FileHandler.class);
            registerForReflection(java.util.logging.SimpleFormatter.class);

            reflectionConfigured = true;

            access.requireAnalysisIteration();
        }
    }

    private void registerForReflection(Class<?> clazz) {
        try {
            trace("Registering " + clazz + " for reflection.");
            RuntimeReflection.register(clazz);
            RuntimeReflection.register(clazz.getConstructor());
        } catch (NoSuchMethodException e) {
            VMError.shouldNotReachHere(e);
        }
    }

    private void trace(String msg) {
        if (trace) {
            System.out.println("LoggingFeature: " + msg);
        }
    }

}
