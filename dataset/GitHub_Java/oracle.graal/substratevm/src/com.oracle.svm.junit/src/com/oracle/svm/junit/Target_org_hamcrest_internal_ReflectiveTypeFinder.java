/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.junit;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hamcrest.core.Every;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.internal.ReflectiveTypeFinder;

@TargetClass(value = ReflectiveTypeFinder.class, onlyWith = JUnitFeature.IsEnabled.class)
public final class Target_org_hamcrest_internal_ReflectiveTypeFinder {

    @Substitute
    @SuppressWarnings("static-method")
    public Class<?> findExpectedType(Class<?> fromClass) {
        if (Every.class.isAssignableFrom(fromClass) || IsCollectionContaining.class.isAssignableFrom(fromClass)) {
            return Iterable.class;
        } else {
            /*
             * We don't know what type to return here, so we're just conservatively returning
             * Object. This will not change the overall behavior of the test, the user will just get
             * a ClassCastException instead of a descriptive error message in the failure case.
             */
            return Object.class;
        }
    }
}
