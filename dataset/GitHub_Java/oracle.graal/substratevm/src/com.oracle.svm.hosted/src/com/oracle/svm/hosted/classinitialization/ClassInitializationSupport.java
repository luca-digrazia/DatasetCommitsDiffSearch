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
package com.oracle.svm.hosted.classinitialization;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Interface for the class initialization required by the native-image.
 */
public interface ClassInitializationSupport extends RuntimeClassInitializationSupport {

    /**
     * The initialization kind for a class. The order of the enum values matters, {@link #max}
     * depends on it.
     */
    enum InitKind {
        /** Class is initialized during image building, so it is already initialized at runtime. */
        EAGER,
        /** Class is initialized both at runtime and during image building. */
        RERUN,
        /** Class should be initialized at runtime and not during image building. */
        DELAY;

        InitKind max(InitKind other) {
            return this.ordinal() > other.ordinal() ? this : other;
        }

        boolean isDelayed() {
            return this.equals(DELAY);
        }

        public static final String SEPARATOR = ":";

        String suffix() {
            return SEPARATOR + name().toLowerCase();
        }

        Consumer<Class<?>> classConsumer(ClassInitializationSupport support) {
            if (this == DELAY) {
                return cls -> support.delay(cls, "from command line");
            } else if (this == RERUN) {
                return cls -> support.rerun(cls, "from command line");
            } else {
                return cls -> support.eager(cls, "from command line");
            }
        }

        Consumer<String> stringConsumer(ClassInitializationSupport support) {
            if (this == DELAY) {
                return name -> support.delay(name, "from command line");
            } else if (this == RERUN) {
                return name -> support.rerun(name, "from command line");
            } else {
                return name -> support.eager(name, "from command line");
            }
        }

        static Pair<String, InitKind> strip(String input) {
            Optional<InitKind> it = Arrays.stream(values()).filter(x -> input.endsWith(x.suffix())).findAny();
            assert it.isPresent();
            return Pair.create(input.substring(0, input.length() - it.get().suffix().length()), it.get());
        }

    }

    /**
     * Returns an init kind for {@code clazz}.
     */
    InitKind specifiedInitKindFor(Class<?> clazz);

    /**
     * Returns all classes of a single {@link InitKind}.
     */
    Set<Class<?>> classesWithKind(InitKind kind);

    /**
     * Returns true if the provided type should be initialized at runtime.
     */
    boolean shouldInitializeAtRuntime(ResolvedJavaType type);

    /**
     * Returns true if the provided class should be initialized at runtime.
     */
    boolean shouldInitializeAtRuntime(Class<?> clazz);

    /**
     * Initializes the class during image building, unless initialization must be delayed to
     * runtime.
     */
    void maybeInitializeHosted(ResolvedJavaType type);

    /**
     * Initializes the class during image building, and reports an error if the user requested to
     * delay initialization to runtime.
     */
    void forceInitializeHosted(Class<?> clazz, String reason);

    /**
     * Check that all registered classes are here, regardless if the AnalysisType got actually
     * marked as used. Class initialization can have side effects on other classes without the class
     * being used itself, e.g., a class initializer can write a static field in another class.
     */
    boolean checkDelayedInitialization();

    void setUnsupportedFeatures(UnsupportedFeatures o);

    class ClassOrPackageConfig {
        private final String name;
        private final EconomicSet<String> reasons;
        private final InitKind kind;

        ClassOrPackageConfig(String name, EconomicSet<String> reasons, InitKind kind) {
            this.name = name;
            this.reasons = reasons;
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public EconomicSet<String> getReasons() {
            return reasons;
        }

        public InitKind getKind() {
            return kind;
        }
    }

    List<ClassOrPackageConfig> getClassInitializationConfiguration();
}
