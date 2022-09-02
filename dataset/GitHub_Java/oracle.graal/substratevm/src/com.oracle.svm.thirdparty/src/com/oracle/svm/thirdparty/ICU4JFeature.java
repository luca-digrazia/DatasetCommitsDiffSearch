/*
 * Copyright (c) 2017, 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.thirdparty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

/**
 * ICU4JFeature enables ICU4J library ({@link "http://site.icu-project.org/"} to be used in SVM.
 * <p>
 * The main obstacle in using the ICU4J library as is was that the library relies on class loader to
 * fetch localization data from resource files included in the ICU4J jar archive. This feature is
 * not supported by SVM, so the next option was to read the resource files from the file system. The
 * following code addresses several issues that occurred when specifying
 * <code>com.ibm.icu.impl.ICUBinary.dataPath</code> system property in runtime (standard ICU4J
 * feature).
 */
@AutomaticFeature
public final class ICU4JFeature implements Feature {

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ICU4JFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("com.ibm.icu.impl.ClassLoaderUtil") != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        registerShimClass(access, "com.ibm.icu.text.NumberFormatServiceShim");
        registerShimClass(access, "com.ibm.icu.text.CollatorServiceShim");
        registerShimClass(access, "com.ibm.icu.text.BreakIteratorFactory");
    }

    private static void registerShimClass(BeforeAnalysisAccess access, String shimClassName) {
        Class<?> shimClass = access.findClassByName(shimClassName);
        if (shimClass != null) {
            RuntimeReflection.registerForReflectiveInstantiation(shimClass);
        } else {
            throw VMError.shouldNotReachHere(shimClassName + " not found");
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.CalendarUtil$CalendarPreferences"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.CaseMapImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.DayPeriodRules"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.LocaleDisplayNamesImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.Norm2AllModes$NFCSingleton"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.Norm2AllModes$NFKCSingleton"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.Norm2AllModes$NFKC_CFSingleton"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.Norm2AllModes$NFKCSingleton"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.StaticUnicodeSets"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.UBiDiProps"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.UCaseProps"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.UCharacterName"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.UCharacterProperty"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.UPropertyAliases"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.UBiDiProps"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.ZoneMeta"));

        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.coll.CollationRoot"));

        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.locale.KeyTypeData"));

        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.CurrencySpacingEnabledModifier"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.parse.IgnorablesMatcher"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.parse.InfinityMatcher"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.parse.MinusSignMatcher"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.parse.PercentMatcher"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.parse.PermilleMatcher"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.impl.number.parse.PlusSignMatcher"));

        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.BurmeseBreakEngine"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.CjkBreakEngine"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.CurrencyMetaInfo"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.DateFormat$Field"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.DateTimePatternGenerator"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.DateTimePatternGenerator$FormatParser"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.DecimalFormatSymbols"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.LaoBreakEngine"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.LocaleDisplayNames"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.KhmerBreakEngine"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$FCDModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$FCD32ModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFDModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFKDModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFCModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFKCModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFD32ModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFKD32ModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFC32ModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$NFKC32ModeImpl"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.Normalizer$Unicode32"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.NumberingSystem"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.SimpleDateFormat"));
        RuntimeClassInitialization.delayClassInitialization(access.findClassByName("com.ibm.icu.text.ThaiBreakEngine"));
    }

    static class Helper {
        /** Dummy ClassLoader used only for resource loading. */
        // Checkstyle: stop
        static final ClassLoader DUMMY_LOADER = new ClassLoader(null) {
        };
        // CheckStyle: resume
    }
}

@TargetClass(className = "com.ibm.icu.impl.ClassLoaderUtil", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ClassLoaderUtil {
    @Substitute
    // Checkstyle: stop
    public static ClassLoader getClassLoader() {
        return ICU4JFeature.Helper.DUMMY_LOADER;
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUBinary", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUBinary {

    @Alias
    static native void addDataFilesFromPath(String dataPath, List<?> files);

    @Alias @InjectAccessors(IcuDataFilesAccessors.class) static List<?> icuDataFiles;

    static final class IcuDataFilesAccessors {

        private static final String ICU4J_DATA_PATH_SYS_PROP = "com.ibm.icu.impl.ICUBinary.dataPath";
        private static final String ICU4J_DATA_PATH_ENV_VAR = "ICU4J_DATA_PATH";

        private static final String NO_DATA_PATH_ERR_MSG = "No ICU4J data path was set or found. This will likely end up with a MissingResourceException. " +
                        "To take advantage of the ICU4J library, you should either set system property, " +
                        ICU4J_DATA_PATH_SYS_PROP +
                        ", or set environment variable, " +
                        ICU4J_DATA_PATH_ENV_VAR +
                        ", to contain path to your ICU4J icudt directory";

        private static volatile List<?> instance;

        static List<?> get() {

            if (instance == null) {
                // Checkstyle: allow synchronization
                synchronized (IcuDataFilesAccessors.class) {
                    if (instance == null) {

                        instance = new ArrayList<>();

                        String dataPath = System.getProperty(ICU4J_DATA_PATH_SYS_PROP);
                        if (dataPath == null || dataPath.isEmpty()) {
                            dataPath = System.getenv(ICU4J_DATA_PATH_ENV_VAR);
                        }
                        if (dataPath != null && !dataPath.isEmpty()) {
                            addDataFilesFromPath(dataPath, instance);
                        } else {
                            System.err.println(NO_DATA_PATH_ERR_MSG);
                        }
                    }
                }
                // Checkstyle: disallow synchronization
            }
            return instance;
        }
    }
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle {
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true)
    // Checkstyle: stop
    private static ClassLoader ICU_DATA_CLASS_LOADER = ICU4JFeature.Helper.DUMMY_LOADER;
    // Checkstyle: resume

    @SuppressWarnings("unused")
    @Substitute
    // Checkstyle: stop
    private static void addBundleBaseNamesFromClassLoader(final String bn, final ClassLoader root, final Set<String> names) {
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle$WholeBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle_WholeBundle {
    @Alias @RecomputeFieldValue(kind = Kind.Reset)
    // Checkstyle: stop
    ClassLoader loader;
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle$AvailEntry", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle_AvailEntry {
    @Alias @RecomputeFieldValue(kind = Kind.Reset)
    // Checkstyle: stop
    ClassLoader loader;
    // Checkstyle: resume
}
