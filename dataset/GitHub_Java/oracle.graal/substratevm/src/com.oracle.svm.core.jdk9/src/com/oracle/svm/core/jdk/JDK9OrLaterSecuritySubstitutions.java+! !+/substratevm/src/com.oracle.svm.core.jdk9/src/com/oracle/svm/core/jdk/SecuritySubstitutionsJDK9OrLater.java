/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.security.Policy;
import java.security.Provider;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "sun.security.jca.ProviderConfig", innerClass = "ProviderLoader", onlyWith = JDK9OrLater.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_sun_security_jca_ProviderConfig_ProviderLoader {
    @Substitute
    private Provider legacyLoad(String classname) {
        throw VMError.unsupportedFeature("JDK9OrLater: sun.security.jca.ProviderConfig.legacyLoad(String classname)");
    }
}

@TargetClass(value = java.security.Policy.class, onlyWith = JDK9OrLater.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_security_Policy {
    @Substitute
    static Policy getPolicyNoCheck() {
        throw VMError.unsupportedFeature("JDK9OrLater: java.security.Policy.getPolicyNoCheck()");
    }
}

public final class JDK9OrLaterSecuritySubstitutions {
}

