/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DetectedChange {

    private final List<ParserMethod> changedMethodBodies = new ArrayList<>();
    private final List<ParserMethod> addedMethods = new ArrayList<>();
    private final List<ParserMethod> removedMethods = new ArrayList<>();

    void addMethodBodyChange(ParserMethod newMethod) {
        changedMethodBodies.add(newMethod);
    }

    List<ParserMethod> getChangedMethodBodies() {
        return Collections.unmodifiableList(changedMethodBodies);
    }

    List<ParserMethod> getAddedMethods() {
        return Collections.unmodifiableList(addedMethods);
    }

    List<ParserMethod> getRemovedMethods() {
        return Collections.unmodifiableList(removedMethods);
    }

    List<ParserMethod> getAddedAndRemovedMethods() {
        ArrayList<ParserMethod> result = new ArrayList<>(addedMethods);
        result.addAll(removedMethods);
        return Collections.unmodifiableList(result);
    }

    public void addNewMethods(List<ParserMethod> methods) {
        addedMethods.addAll(methods);
    }

    public void addRemovedMethods(List<ParserMethod> methods) {
        removedMethods.addAll(methods);
    }
}
