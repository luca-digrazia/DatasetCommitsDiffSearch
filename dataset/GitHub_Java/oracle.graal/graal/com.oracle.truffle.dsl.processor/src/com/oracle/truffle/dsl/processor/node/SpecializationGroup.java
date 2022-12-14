/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.node;

import java.util.*;

import com.oracle.truffle.dsl.processor.template.TemplateMethod.Signature;
import com.oracle.truffle.dsl.processor.typesystem.*;

/**
 * Class creates groups of specializations to optimize the layout of generated executeAndSpecialize
 * and generic execute methods.
 */
public final class SpecializationGroup {

    private final List<String> assumptions;
    private final List<TypeData> typeGuards;
    private final List<GuardData> guards;

    private final SpecializationData specialization;
    private final List<SpecializationGroup> children = new ArrayList<>();

    private SpecializationGroup parent;

    private SpecializationGroup(SpecializationData data) {
        this.assumptions = new ArrayList<>();
        this.typeGuards = new ArrayList<>();
        this.guards = new ArrayList<>();
        this.specialization = data;

        this.assumptions.addAll(data.getAssumptions());
        Signature sig = data.getSignature();
        for (int i = 1; i < sig.size(); i++) {
            typeGuards.add(sig.get(i));
        }
        this.guards.addAll(data.getGuards());
    }

    public SpecializationGroup(List<SpecializationGroup> children, List<String> assumptionMatches, List<TypeData> typeGuardsMatches, List<GuardData> guardMatches) {
        this.assumptions = assumptionMatches;
        this.typeGuards = typeGuardsMatches;
        this.guards = guardMatches;
        this.specialization = null;
        updateChildren(children);
    }

    private void updateChildren(List<SpecializationGroup> childs) {
        if (!children.isEmpty()) {
            children.clear();
        }
        this.children.addAll(childs);
        for (SpecializationGroup child : childs) {
            child.parent = this;
        }
    }

    public int getTypeGuardOffset() {
        return (parent != null ? parent.getTypeGuardOffsetRec() : 0);
    }

    private int getTypeGuardOffsetRec() {
        return typeGuards.size() + (parent != null ? parent.getTypeGuardOffsetRec() : 0);
    }

    public SpecializationGroup getParent() {
        return parent;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public List<TypeData> getTypeGuards() {
        return typeGuards;
    }

    public List<GuardData> getGuards() {
        return guards;
    }

    public List<SpecializationGroup> getChildren() {
        return children;
    }

    public SpecializationData getSpecialization() {
        return specialization;
    }

    private static SpecializationGroup combine(List<SpecializationGroup> groups) {
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("empty combinations");
        }
        if (groups.size() == 1) {
            return null;
        }

        List<String> assumptionMatches = new ArrayList<>();
        List<TypeData> typeGuardsMatches = new ArrayList<>();
        List<GuardData> guardMatches = new ArrayList<>();

        SpecializationGroup first = groups.get(0);
        List<SpecializationGroup> others = groups.subList(1, groups.size());

        outer: for (String assumption : first.assumptions) {
            for (SpecializationGroup other : others) {
                if (!other.assumptions.contains(assumption)) {
                    // assumptions can be combined unordered
                    continue outer;
                }
            }
            assumptionMatches.add(assumption);
        }

        int typeGuardIndex = 0;
        outer: for (TypeData typeGuard : first.typeGuards) {
            for (SpecializationGroup other : others) {
                if (typeGuardIndex >= other.typeGuards.size()) {
                    break outer;
                }

                if (!other.typeGuards.get(typeGuardIndex).equals(typeGuard)) {
                    break outer;
                }
            }
            typeGuardsMatches.add(typeGuard);
            typeGuardIndex++;
        }

        outer: for (GuardData guard : first.guards) {
            for (SpecializationGroup other : others) {
                if (!other.guards.contains(guard)) {
                    // we must break here. One guard may depend on the other.
                    break outer;
                }
            }
            guardMatches.add(guard);
        }

        if (assumptionMatches.isEmpty() && typeGuardsMatches.isEmpty() && guardMatches.isEmpty()) {
            return null;
        }

        for (SpecializationGroup group : groups) {
            group.assumptions.removeAll(assumptionMatches);
            group.typeGuards.subList(0, typeGuardIndex).clear();
            group.guards.removeAll(guardMatches);
        }

        List<SpecializationGroup> newChildren = new ArrayList<>(groups);
        return new SpecializationGroup(newChildren, assumptionMatches, typeGuardsMatches, guardMatches);
    }

    public static List<SpecializationGroup> create(List<SpecializationData> specializations) {
        List<SpecializationGroup> groups = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            groups.add(new SpecializationGroup(specialization));
        }
        return createCombinationalGroups(groups);
    }

    @Override
    public String toString() {
        return "SpecializationGroup [assumptions=" + assumptions + ", typeGuards=" + typeGuards + ", guards=" + guards + "]";
    }

    private static List<SpecializationGroup> createCombinationalGroups(List<SpecializationGroup> groups) {
        if (groups.size() <= 1) {
            return groups;
        }
        List<SpecializationGroup> newGroups = new ArrayList<>();

        int i = 0;
        for (i = 0; i < groups.size();) {
            SpecializationGroup combined = null;
            for (int j = groups.size(); j > i + 1; j--) {
                combined = combine(groups.subList(i, j));
                if (combined != null) {
                    break;
                }
            }
            SpecializationGroup newGroup;
            if (combined == null) {
                newGroup = groups.get(i);
                i++;
            } else {
                newGroup = combined;
                List<SpecializationGroup> originalGroups = new ArrayList<>(combined.children);
                combined.updateChildren(createCombinationalGroups(originalGroups));
                i += originalGroups.size();
            }

            newGroups.add(newGroup);

        }

        return newGroups;
    }

    public SpecializationGroup getPreviousGroup() {
        if (parent == null || parent.children.isEmpty()) {
            return null;
        }
        int index = parent.children.indexOf(this);
        if (index <= 0) {
            return null;
        }
        return parent.children.get(index - 1);
    }

    public int getMaxSpecializationIndex() {
        if (specialization != null) {
            return specialization.getNode().getSpecializations().indexOf(specialization);
        } else {
            int max = Integer.MIN_VALUE;
            for (SpecializationGroup childGroup : getChildren()) {
                max = Math.max(max, childGroup.getMaxSpecializationIndex());
            }
            return max;
        }
    }
}
