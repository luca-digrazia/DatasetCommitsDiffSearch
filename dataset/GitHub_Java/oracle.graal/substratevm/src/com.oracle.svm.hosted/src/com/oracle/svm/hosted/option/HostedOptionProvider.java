/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.option;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;

public interface HostedOptionProvider {
    EconomicMap<OptionKey<?>, Object> getHostedValues();

    EconomicMap<OptionKey<?>, Object> getRuntimeValues();

    default List<String> getAppliedArguments() {
        List<String> result = new ArrayList<>();
        HostedOptionProviderHelper.addArguments(result, HostedOptionParser.HOSTED_OPTION_PREFIX, getHostedValues());
        HostedOptionProviderHelper.addArguments(result, HostedOptionParser.RUNTIME_OPTION_PREFIX, getRuntimeValues());
        return result;
    }
}

class HostedOptionProviderHelper {
    static void addArguments(List<String> result, String prefix, EconomicMap<OptionKey<?>, Object> values) {
        MapCursor<OptionKey<?>, Object> cursor = values.getEntries();
        while (cursor.advance()) {
            OptionKey<?> key = cursor.getKey();
            OptionDescriptor descriptor = key.getDescriptor();
            String name = descriptor.getName();
            Object value = cursor.getValue();
            result.add(prefix + name + "=" + value);
        }
    }
}
