/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.net.URL;
import java.util.HashMap;

@TargetClass(java.lang.Package.class)
public final class Target_java_lang_Package {

    @Inject
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = Target_java_lang_Package.PackageJfrIDRecomputation.class)
    public int jfrID;

    @Platforms(Platform.HOSTED_ONLY.class)
    static HashMap<Package, Integer> jfrIdsMap = new HashMap<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setJfrID(Package pkg, Integer id) {
        jfrIdsMap.put(pkg, id);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    class PackageJfrIDRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Package cl = (Package) receiver;
            return jfrIdsMap.get(cl);
        }
    }

    @Alias
    @SuppressWarnings({"unused"})
    Target_java_lang_Package(String name,
                             String spectitle, String specversion, String specvendor,
                             String impltitle, String implversion, String implvendor,
                             URL sealbase, ClassLoader loader) {
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static Package getSystemPackage(String name) {
        Target_java_lang_Package pkg = new Target_java_lang_Package(name, null, null, null,
                null, null, null, null, null);
        return SubstrateUtil.cast(pkg, Package.class);
    }
}
