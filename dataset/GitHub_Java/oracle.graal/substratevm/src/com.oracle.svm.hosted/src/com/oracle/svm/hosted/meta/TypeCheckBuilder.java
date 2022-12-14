/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.calc.UnsignedMath;

import com.oracle.svm.core.util.VMError;

public class TypeCheckBuilder {
    private static final int SLOT_CAPACITY = 1 << 16;

    private final HostedType objectType;
    private final List<HostedType> allTypes;
    private List<HostedType> allReachableTypes;
    private List<HostedType> allReachableRoots;
    private List<HostedType> heightOrderedTypes;
    private int numClassSlots = -1;
    private int numInterfaceSlots = -1;

    private Map<HostedType, int[]> classIDMap = new HashMap<>();
    private Map<HostedType, int[]> interfaceIDMap = new HashMap<>();
    private Map<HostedType, List<HostedType>> subtypeMap = new HashMap<>();
    private Map<HostedType, Integer> numClassDescendants = new HashMap<>();

    public TypeCheckBuilder(List<HostedType> types, HostedType objectType) {
        this.allTypes = types;
        this.objectType = objectType;
    }

    public boolean calculateIDs() {
        computeMetadata();
        computeClassSlots();
        computeInterfaceSlots();
        generateTypeCheckSlots();
        assert compareTypeIDResults();
        return true;
    }

    private void computeMetadata() {
        allReachableTypes = allTypes.stream().filter(t -> shouldIncludeType(t)).collect(Collectors.toList());
        computeSubtypeInformation();

        Map<HostedType, Set<HostedType>> parentMap = new HashMap<>();
        for (HostedType type : allReachableTypes) {
            for (HostedType subtype : subtypeMap.get(type)) {
                assert shouldIncludeType(subtype);
                parentMap.computeIfAbsent(subtype, k -> new HashSet<>()).add(type);
            }
        }
        allReachableRoots = allReachableTypes.stream().filter(t -> !parentMap.containsKey(t)).collect(Collectors.toList());
        generateMaxHeight(allReachableRoots);
    }

    private void computeSubtypeInformation() {
        /*
         * We cannot use the sub-type information from the HostType because there are some minor
         * differences regarding array types. Therefore we build the sub-type information from
         * scratch.
         */
        Map<HostedType, Set<HostedType>> allTypeCheckSubTypes = new HashMap<>();
        allReachableTypes.stream().forEach(t -> allTypeCheckSubTypes.put(t, new HashSet<>()));

        Set<HostedType> descendantsToCompute = new HashSet<>();
        for (HostedType type : allReachableTypes) {

            if (type.getSuperclass() != null) {
                HostedType superClass;
                if (!type.isArray()) {
                    /* Non-arrays get their normal superclass. */
                    superClass = type.getSuperclass();
                } else {
                    /*
                     * For arrays, the superclass is always Object, but for type checking purposes
                     * if is useful to create a different graph.
                     */
                    HostedType baseType = type.getBaseType();
                    int dim = type.getArrayDimension();
                    assert dim >= 1;
                    if (baseType.isInterface()) {
                        /* Want object of appropriate depth. */
                        superClass = getHighestDimArrayType(objectType, dim);
                        /* For interface arrays have to compute all possible descendants. */
                        descendantsToCompute.add(baseType);
                    } else if (baseType.isPrimitive()) {
                        /* Superclass should be object of one less dimension. */
                        superClass = getHighestDimArrayType(objectType, dim - 1);
                    } else {
                        // is an instance class
                        HostedType baseSuperClass = baseType.getSuperclass();
                        if (baseSuperClass == null) {
                            /*
                             * This means this in an object array. In this case its parent should be
                             * the object type of one less dimension.
                             */
                            superClass = getHighestDimArrayType(objectType, dim - 1);
                        } else {
                            /* Otherwise make the super class the equivalent base superclass. */
                            superClass = baseSuperClass.getArrayClass(dim);
                            while (superClass == null || !allTypeCheckSubTypes.containsKey(superClass)) {
                                /*
                                 * Not all array type check supertypes are reachable. In this case,
                                 * make its superclass the first reachable super class.
                                 */
                                baseSuperClass = baseSuperClass.getSuperclass();
                                if (baseSuperClass == null) {
                                    /* means object type of the array size wasn't reachable */
                                    superClass = getHighestDimArrayType(objectType, dim - 1);
                                } else {
                                    superClass = baseSuperClass.getArrayClass(dim);
                                }
                            }
                        }
                    }
                }

                allTypeCheckSubTypes.get(superClass).add(type);
            }

            if (type.isInterface()) {
                assert type.getSuperclass() == null;
                allTypeCheckSubTypes.get(objectType).add(type);
            }

            for (HostedInterface interfaceType : type.getInterfaces()) {
                if (!type.isArray()) {
                    allTypeCheckSubTypes.get(interfaceType).add(type);
                } else {
                    /*
                     * For arrays, the two implemented interfaces are Serializable and Cloneable.
                     * For these, the implemented interface should be of one less dimension.
                     */
                    int dim = type.getArrayDimension();
                    HostedType baseType = interfaceType.getBaseType();
                    HostedType arrayInterfaceType = getHighestDimArrayType(baseType, dim - 1);
                    allTypeCheckSubTypes.get(arrayInterfaceType).add(type);
                }
            }

        }

        Map<HostedType, Set<HostedType>> descendantsMap = computeNonArrayDescendants(descendantsToCompute);
        for (HostedType type : allReachableTypes) {
            Set<HostedType> typeCheckSubTypeSet = allTypeCheckSubTypes.get(type);
            if (type.isArray() && type.getBaseType().isInterface()) {
                /*
                 * For array interfaces, also adding as subtypes all arrays of the same dimension
                 * which implement this interfaces.
                 */
                int dim = type.getArrayDimension();
                assert dim >= 1;
                Set<HostedType> descendants = descendantsMap.get(type.getBaseType());
                for (HostedType descendant : descendants) {
                    assert !descendant.isArray();
                    HostedType arrayDescendant = descendant.getArrayClass(dim);
                    if (arrayDescendant != null && allTypeCheckSubTypes.containsKey(arrayDescendant)) {
                        typeCheckSubTypeSet.add(arrayDescendant);
                    }
                }
            }
            List<HostedType> typeCheckSubTypes = typeCheckSubTypeSet.stream().sorted().collect(Collectors.toList());
            subtypeMap.put(type, typeCheckSubTypes);
        }
    }

    private static HostedType getHighestDimArrayType(HostedType type, int dimMax) {
        int dim = dimMax;
        HostedType result;
        do {
            result = type.getArrayClass(dim);
            dim--;
        } while (result == null || !(shouldIncludeType(result)));

        return result;
    }

    private Map<HostedType, Set<HostedType>> computeNonArrayDescendants(Set<HostedType> types) {
        Map<HostedType, Set<HostedType>> descendantsMap = new HashMap<>();
        for (HostedType type : types) {
            computeNonArrayDescendants(type, descendantsMap);
        }

        return descendantsMap;
    }

    private void computeNonArrayDescendants(HostedType type, Map<HostedType, Set<HostedType>> descendantsMap) {
        if (descendantsMap.containsKey(type)) {
            // already computed this map
            return;
        }
        Set<HostedType> descendants = new HashSet<>();
        for (HostedType child : type.subTypes) {
            if (child.isArray()) {
                continue; // only care about non-array subtypes
            }
            descendants.add(child);
            computeNonArrayDescendants(child, descendantsMap);
            descendants.addAll(descendantsMap.get(child));
        }
        descendantsMap.put(type, descendants);

    }

    private boolean compareTypeIDResults() {
        int numTypes = allReachableTypes.size();
        for (int i = 0; i < numTypes; i++) {
            HostedType superType = allReachableTypes.get(i);
            for (int j = 0; j < numTypes; j++) {
                HostedType checkedType = allReachableTypes.get(j);
                boolean legacyCheck = legacyCheckAssignable(superType, checkedType);
                boolean newCheck = newCheckAssignable(superType, checkedType);
                boolean checksMatch = legacyCheck == newCheck;
                VMError.guarantee(checksMatch, "Type checks do not match.");
            }
        }
        return true;
    }

    private static boolean legacyCheckAssignable(HostedType superType, HostedType checkedType) {
        int[] matches = superType.getAssignableFromMatches();
        int checkedID = checkedType.getTypeID();
        for (int i = 0; i < matches.length; i += 2) {
            int assignableStart = matches[i];
            int assignableEnd = assignableStart + matches[i + 1] - 1;
            if (checkedID >= assignableStart && checkedID <= assignableEnd) {
                return true;
            }
        }
        return false;
    }

    private static boolean newCheckAssignable(HostedType superType, HostedType checkedType) {
        int typeCheckStart = Short.toUnsignedInt(superType.getTypeCheckStart());
        int typeCheckRange = Short.toUnsignedInt(superType.getTypeCheckRange());
        int typeCheckSlot = Short.toUnsignedInt(superType.getTypeCheckSlot());
        int checkedTypeID = Short.toUnsignedInt(checkedType.getTypeCheckSlots()[typeCheckSlot]);
        if (UnsignedMath.belowThan(checkedTypeID - typeCheckStart, typeCheckRange)) {
            return true;
        }
        return false;
    }

    private void generateTypeCheckSlots() {
        int numSlots = getNumTypeCheckSlots();
        for (HostedType type : allReachableTypes) {
            short[] typeCheckSlots = new short[numSlots];
            int[] slots = classIDMap.get(type);
            for (int i = 0; i < slots.length; i++) {
                typeCheckSlots[i] = getShortValue(slots[i]);
            }
            slots = interfaceIDMap.get(type);
            if (slots != null) {
                for (int i = 0; i < slots.length; i++) {
                    typeCheckSlots[numClassSlots + i] = getShortValue(slots[i]);
                }
            }

            type.setTypeCheckSlots(typeCheckSlots);
        }

    }

    public int getNumTypeCheckSlots() {
        assert numClassSlots != -1 && numInterfaceSlots != -1;
        return numClassSlots + numInterfaceSlots;
    }

    private void computeClassSlots() {
        computeNumClassDescendants();
        calculateClassIDs();
    }

    private void computeInterfaceSlots() {
        Graph interfaceGraph = Graph.buildInterfaceGraph(heightOrderedTypes, subtypeMap);
        interfaceGraph.mergeDuplicates();
        interfaceGraph.generateDescendantIndex();
        calculateInterfaceIDs(interfaceGraph);
    }

    private void computeNumClassDescendants() {
        for (int i = heightOrderedTypes.size() - 1; i >= 0; i--) {
            HostedType type = heightOrderedTypes.get(i);
            if (isInterface(type)) {
                continue;
            }
            int numDescendants = 0;
            for (HostedType child : subtypeMap.get(type)) {
                if (isInterface(child)) {
                    continue;
                }
                /* Adding child and its descendants. */
                numDescendants += 1 + numClassDescendants.get(child);
            }
            numClassDescendants.put(type, numDescendants);
        }
    }

    /**
     * Calculating the maximum height of each node. This allows one to create the interface graph in
     * one iteration of the nodes.
     */
    private void generateMaxHeight(List<HostedType> roots) {

        /* Set initial height of all nodes to an impossible height */
        Map<HostedType, Integer> heightMap = new HashMap<>();
        allReachableTypes.forEach(t -> heightMap.put(t, Integer.MIN_VALUE));

        /* Find the max height of each tree. */
        for (HostedType root : roots) {
            maxHeightHelper(0, root, heightMap);
        }

        /* Now create a sorted array from this information. */
        heightOrderedTypes = allReachableTypes.stream().sorted(Comparator.comparingInt(heightMap::get)).collect(Collectors.toList());
    }

    /**
     * Helper method to assist with determining the maximum height of each node.
     */
    private void maxHeightHelper(int depth, HostedType node, Map<HostedType, Integer> heightMap) {
        assert shouldIncludeType(node);
        heightMap.compute(node, (k, currentDepth) -> Integer.max(depth, currentDepth));

        for (HostedType subtype : subtypeMap.get(node)) {
            maxHeightHelper(depth + 1, subtype, heightMap);
        }
    }

    private static boolean isInterface(HostedType type) {
        return type.isInterface() || (type.isArray() && type.getBaseType().isInterface());
    }

    private static boolean shouldIncludeType(HostedType type) {
        return type.getWrapped().isReachable();
    }

    /**
     * This method calculates the information needed to complete a type check against a
     * non-interface type. Due to Java's single inheritance property, the superclass type hierarchy
     * forms a tree. As pointed out in "Determining type, part, color and time relationships" by
     * Schubert et al., this properly allows one to assign each type an ID such that for checks
     * against non-interface types can be accomplished through a range check.
     * <p>
     * In our algorithm, in order to guarantee ID information can fit into two bytes, the type ids
     * are spread out into multiple slots when the two byte capacity is exceeded.
     */
    private void calculateClassIDs() {
        ArrayList<Integer> currentIDs = new ArrayList<>();
        ArrayList<Integer> reservedIDs = new ArrayList<>();
        currentIDs.add(-1);
        reservedIDs.add(SLOT_CAPACITY);
        for (HostedType root : allReachableRoots) {
            assert !isInterface(root);
            classIdHelper(root, currentIDs, reservedIDs);
        }

        /* Recording the number of slots reserved for class IDs. */
        assert numClassSlots == -1;
        numClassSlots = currentIDs.size();

        /* Setting class slot for interfaces - will integrate this with classIDHelper eventually. */
        for (HostedType type : allReachableTypes) {
            if (isInterface(type)) {
                int dim = type.getArrayDimension();
                assert !classIDMap.containsKey(type);
                classIDMap.put(type, classIDMap.get(objectType.getArrayClass(dim)));
            }
        }
    }

    private static final class ClassIDState {
        final int reservedID;
        final int slotNum;
        final int assignedID;
        final int maxSubtypeID;

        private ClassIDState(int reservedID, int slotNum, int assignedID, int maxSubtypeID) {
            this.reservedID = reservedID;
            this.slotNum = slotNum;
            this.assignedID = assignedID;
            this.maxSubtypeID = maxSubtypeID;
        }
    }

    /**
     * This method assigns ids to class types. interfaces are performed using the information
     * calculated in {@link #computeInterfaceSlots()}.
     */
    private void classIdHelper(HostedType type, ArrayList<Integer> currentIDs, ArrayList<Integer> reservedIDs) {
        assert shouldIncludeType(type);
        boolean isTypeInterface = isInterface(type);
        assert !isTypeInterface;

        ClassIDState state = generateClassIDState(type, currentIDs, reservedIDs);
        int reservedID = state.reservedID;
        int slotNum = state.slotNum;
        int assignedID = state.assignedID;
        int maxSubtypeID = state.maxSubtypeID;

        assert !classIDMap.containsKey(type);
        classIDMap.put(type, currentIDs.stream().mapToInt(n -> n).toArray());

        /* Now assigning IDs to children. */
        for (HostedType subtype : subtypeMap.get(type)) {
            if (isInterface(subtype)) {

                /*
                 * Interface types should only be linked to interface subtypes and non reachable
                 * subtypes should not be taken into consideration.
                 */
                continue;
            }
            classIdHelper(subtype, currentIDs, reservedIDs);

            assert currentIDs.get(slotNum) >= assignedID; // IDs should always be increasing.
        }

        /* Determining range of values assigned to subtypes. */
        if (!isTypeInterface) {
            type.setTypeCheckSlot(getShortValue(slotNum));
            int currentID = currentIDs.get(slotNum);
            assert currentID == maxSubtypeID;
            type.setTypeCheckRange(getShortValue(assignedID), getShortValue(currentID - assignedID + 1));
        }
        if (reservedID != -1) {
            currentIDs.set(slotNum, reservedID);
            reservedIDs.set(slotNum, reservedID + 1); // setting back to original value
        }
    }

    private ClassIDState generateClassIDState(HostedType type, ArrayList<Integer> currentIDs, ArrayList<Integer> reservedIDs) {
        int reservedID = -1;
        int slotNum = currentIDs.size() - 1;
        int numDescendants = numClassDescendants.getOrDefault(type, 0);
        int assignedID = currentIDs.get(slotNum) + 1; // need start at the next free stop
        int currentReservedID = reservedIDs.get(slotNum); // max value allowed at this spot
        assert assignedID < currentReservedID;

        if (assignedID + 1 == currentReservedID) {
            /* No more space left. Making filled slot's value different than predecessor. */
            currentIDs.set(slotNum, assignedID);
            slotNum++;
            assignedID = 1;
            currentIDs.add(0);
            currentReservedID = SLOT_CAPACITY;
            reservedIDs.add(currentReservedID);
        }
        int maxSubtypeID = assignedID + numDescendants;
        if (maxSubtypeID >= currentReservedID) {
            /* Means this types descendants will overfill this type. */
            reservedID = currentReservedID - 1;
            if (assignedID + 1 == reservedID) {
                /*
                 * Not enough space for reserved ID + new-slot filler -- move on to next slot. Also,
                 * making filled slot's value different than predecessor.
                 */
                currentIDs.set(slotNum, reservedID);
                slotNum++;
                assignedID = 1;
                currentIDs.add(0);
                maxSubtypeID = assignedID + numDescendants;
                currentReservedID = SLOT_CAPACITY;
                reservedIDs.add(currentReservedID);
                if (maxSubtypeID >= currentReservedID) {
                    reservedID = currentReservedID - 1;
                    reservedIDs.set(slotNum, reservedID);
                    maxSubtypeID = reservedID - 1;
                } else {
                    reservedID = -1;
                }
            } else {
                reservedIDs.set(slotNum, reservedID);
                maxSubtypeID = reservedID - 1;
            }
        }

        currentIDs.set(slotNum, assignedID);

        return new ClassIDState(reservedID, slotNum, assignedID, maxSubtypeID);
    }

    /**
     * Returns the short equivalent of the integer value while ensuring the value does not exceed
     * the slot capacity.
     */
    private static short getShortValue(int intValue) {
        assert SLOT_CAPACITY <= 1 << 16 && intValue < SLOT_CAPACITY;
        return (short) intValue;
    }

    /**
     * This method assigns the ids used within typechecks against an interface. The problem of
     * determining a type assignment where each interface typecheck can be performed by via range
     * check can be mapped to determining whether a boolean matrix satisfies the consecutive ones
     * properly (C1P). Given a matrix of boolean values, the consecutive ones property holds if the
     * columns of the matrix can be reordered so that, within each row, all of the set columns are
     * contiguous.
     *
     * <p>
     * When mapping type checks to a boolean matrix, the columns/rows are the types against which
     * the check will be performed. An row-column entry is true if row.isAssignableFrom(column)
     * should be true.
     * <p>
     * Multiple slots are usually needed because it may not be possible to:
     * <ul>
     * <li>Have the C1P satisfied with all constraints in one array</li>
     * <li>Assign id values within a 2-byte range</li>
     * </ul>
     *
     * <p>
     * Each slot is its own independent matrix and encapsulates a subset of the type checks.
     *
     * <p>
     * For validating a given's slot C1P property and determining a valid C1P ordering we use the
     * algorithm described in "A Simple Test for the Consecutive Ones Property" by Wen-Lain Hsu.
     */
    private void calculateInterfaceIDs(Graph graph) {
        assert graph.interfaceNodes != null;

        // initializing first slot
        ArrayList<InterfaceSlot> slots = new ArrayList<>();
        slots.add(new InterfaceSlot(slots.size()));

        // assigning interfaces to interface slots
        for (Node node : graph.interfaceNodes) {

            // first trying to adding grouping to existing slot
            boolean foundAssignment = false;
            for (InterfaceSlot slot : slots) {
                if (slot.tryAddGrouping(node)) {
                    foundAssignment = true;
                    node.type.setTypeCheckSlot(getShortValue(slot.id + numClassSlots));
                    break;
                }
            }
            if (!foundAssignment) {
                // a new slot is needed to satisfy this grouping
                InterfaceSlot newSlot = new InterfaceSlot(slots.size());
                boolean success = newSlot.tryAddGrouping(node);
                assert success : "must be able to add first node";
                node.type.setTypeCheckSlot(getShortValue(newSlot.id + numClassSlots));
                slots.add(newSlot);
            }
        }

        // initializing all interface slots
        int numSlots = slots.size();
        assert numInterfaceSlots == -1;
        numInterfaceSlots = numSlots;
        for (Node node : graph.nodes) {
            assert !interfaceIDMap.containsKey(node.type);
            interfaceIDMap.put(node.type, new int[numSlots]);
        }

        // assigning slot IDs
        for (InterfaceSlot slot : slots) {
            List<Set<Integer>> c1POrder = slot.getC1POrder();
            int slotId = slot.id;
            int id = 1;
            for (Set<Integer> group : c1POrder) {
                for (Integer nodeID : group) {
                    HostedType type = graph.nodes[nodeID].type;
                    interfaceIDMap.get(type)[slotId] = id;
                }
                id++;
            }
        }

        // now computing ranges for each interface
        for (Node interfaceNode : graph.interfaceNodes) {
            int minId = Integer.MAX_VALUE;
            int maxId = Integer.MIN_VALUE;
            HostedType type = interfaceNode.type;
            int slotId = Short.toUnsignedInt(type.getTypeCheckSlot()) - numClassSlots;
            Set<Integer> idCheck = new HashSet<>();
            for (Node descendant : interfaceNode.sortedDescendants) {
                int id = interfaceIDMap.get(descendant.type)[slotId];
                assert id != 0;
                minId = Integer.min(minId, id);
                maxId = Integer.max(maxId, id);
                idCheck.add(id);
            }
            type.setTypeCheckRange(getShortValue(minId), getShortValue(maxId - minId + 1));
        }

        // relaying information to duplicates
        for (Node node : graph.nodes) {
            if (node.duplicates != null) {
                for (HostedType duplicate : node.duplicates) {
                    assert !interfaceIDMap.containsKey(duplicate);
                    interfaceIDMap.put(duplicate, interfaceIDMap.get(node.type));
                }
            }
        }
    }

    /**
     * This class is used to represent a type which is part of the interface graph ({@link Graph}).
     */
    private static final class Node {
        Node[] sortedAncestors;
        Node[] sortedDescendants;

        int id;
        final HostedType type;
        final boolean isInterface;

        Set<HostedType> duplicates;

        private Node(int id, HostedType type, boolean isInterface) {
            this.id = id;
            this.type = type;
            this.isInterface = isInterface;
        }
    }

    /**
     * This is the "interface graph" used to represent interface subtyping dependencies. Within this
     * graph, each node has a direct edge to all of the interfaces it implements.
     */
    private static class Graph {
        Node[] nodes;
        Node[] interfaceNodes;

        /*
         * This method tries to merge classes which implement the same interfaces into a single
         * node.
         *
         * Because typechecks against interfaces is partitioned from typechecks against class types,
         * it is possible for multiple classes to be represented by the same interface node,
         * provided they implement the same interfaces. Merging these "duplicate" classes into a
         * single node has many benefits, including improving build-time performance and potentially
         * reducing the number of interface slots.
         */
        void mergeDuplicates() {
            Map<Integer, ArrayList<Node>> interfaceHashMap = new HashMap<>();
            Map<Integer, ArrayList<Node>> classHashMap = new HashMap<>();
            Map<Node, Set<HostedType>> duplicateMap = new HashMap<>();

            /*
             * First group each node based on a hash of its ancestors. This hashing reduces the
             * number of nodes which need to be checked against for duplicates.
             */
            for (Node node : nodes) {
                Node[] ancestors = node.sortedAncestors;

                int length = ancestors.length;
                assert length != 0;

                boolean isNodeInterface = node.isInterface;
                if (length == 1) {
                    /*
                     * If a node has a single interface, and it is a class, then it should be merged
                     * into the interface.
                     */
                    if (!isNodeInterface) {
                        Node ancestor = ancestors[0];
                        recordDuplicateRelation(duplicateMap, ancestor, node);
                        nodes[node.id] = null;
                    }
                } else {
                    int hash = getDuplicateHash(ancestors);
                    /*
                     * Have separate maps for interfaces and classes so that, when possible, classes
                     * are merged into the appropriate interface node. Note that it is not possible
                     * to merge interfaces into each other.
                     */
                    Map<Integer, ArrayList<Node>> destinationMap = isNodeInterface ? interfaceHashMap : classHashMap;
                    destinationMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(node);
                }
            }

            /* First trying to merge classes into a matching interface. */
            for (Map.Entry<Integer, ArrayList<Node>> entry : interfaceHashMap.entrySet()) {
                ArrayList<Node> interfaces = entry.getValue();
                ArrayList<Node> classes = classHashMap.get(entry.getKey());
                if (classes == null) {
                    /*
                     * Not guaranteed any classes will exist which can be merged into this
                     * interface.
                     */
                    continue;
                }
                for (Node interfaceNode : interfaces) {
                    for (int i = 0; i < classes.size(); i++) {
                        Node classNode = classes.get(i);
                        if (classNode == null) {
                            /*
                             * It is possible for this class to have already be merged into another
                             * interface.
                             */
                            continue;
                        }
                        if (tryMergeNodes(duplicateMap, interfaceNode, classNode)) {
                            classes.set(i, null);
                        }
                    }
                }
            }

            /* Next, trying to merge classes into one another. */
            for (Map.Entry<Integer, ArrayList<Node>> entry : classHashMap.entrySet()) {
                ArrayList<Node> classes = entry.getValue();
                int numClasses = classes.size();
                for (int i = 0; i < numClasses - 1; i++) {
                    Node classNode = classes.get(i);
                    if (classNode == null) {
                        /* Class may have been already merged. */
                        continue;
                    }
                    for (int j = i + 1; j < numClasses; j++) {
                        Node duplicateCandidate = classes.get(j);
                        if (duplicateCandidate == null) {
                            /* Class may have been already merged. */
                            continue;
                        }
                        if (tryMergeNodes(duplicateMap, classNode, duplicateCandidate)) {
                            classes.set(j, null);
                        }
                    }
                }
            }

            /* Recording all duplicates within the merged node. */
            for (Map.Entry<Node, Set<HostedType>> entry : duplicateMap.entrySet()) {
                entry.getKey().duplicates = entry.getValue();
            }

            /* Removing all empty nodes from the array. */
            ArrayList<Node> compactedNodeArray = new ArrayList<>();
            for (Node node : nodes) {
                if (node == null) {
                    continue;
                }
                /* Have to recalculate the ids. */
                node.id = compactedNodeArray.size();
                compactedNodeArray.add(node);
            }
            nodes = compactedNodeArray.toArray(new Node[0]);
        }

        /**
         * This hash is used to help quickly identify potential duplicates within the graph.
         */
        private static int getDuplicateHash(Node[] ancestors) {
            int length = ancestors.length;
            return (length << 16) + Arrays.stream(ancestors).mapToInt(n -> n.id).sum();
        }

        private boolean tryMergeNodes(Map<Node, Set<HostedType>> duplicateMap, Node node, Node duplicateCandidate) {
            if (areDuplicates(node, duplicateCandidate)) {
                /* removing node b and marking it as a duplicate of node a */
                recordDuplicateRelation(duplicateMap, node, duplicateCandidate);

                int duplicateIdx = duplicateCandidate.id;
                assert !nodes[duplicateIdx].isInterface; // shouldn't be removing interfaces
                /* removing the node from the map */
                nodes[duplicateIdx] = null;
                return true;
            }
            return false;
        }

        /**
         * Two nodes are duplicates if their ancestors exactly match.
         */
        private static boolean areDuplicates(Node a, Node b) {
            Node[] aAncestors = a.sortedAncestors;
            Node[] bAncestors = b.sortedAncestors;
            if (aAncestors.length != bAncestors.length) {
                return false;
            }
            for (int i = 0; i < aAncestors.length; i++) {
                if (aAncestors[i] != bAncestors[i]) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Recording duplicate information which later will be placed into the merged nodes.
         */
        private static void recordDuplicateRelation(Map<Node, Set<HostedType>> duplicateMap, Node node, Node duplicate) {
            assert !duplicateMap.containsKey(duplicate) : "By removing this node, I am losing record of some duplicates.";
            duplicateMap.computeIfAbsent(node, k -> new HashSet<>()).add(duplicate.type);
        }

        /**
         * Computing the descendants for each node of interface type. This information is needed to
         * determine which nodes must be assigned contiguous type ids.
         */
        public void generateDescendantIndex() {
            Map<Node, Set<Node>> descendantMap = new HashMap<>();
            Node[] emptyDescendant = new Node[0];
            ArrayList<Node> interfaceList = new ArrayList<>();

            // iterating through children before parents
            for (int i = nodes.length - 1; i >= 0; i--) {
                Node node = nodes[i];
                if (node.isInterface) {
                    // recording descendant information
                    Set<Node> descendants = descendantMap.computeIfAbsent(node, k -> new HashSet<>());
                    descendants.add(node);
                    Node[] descendantArray = descendants.toArray(new Node[0]);
                    Arrays.sort(descendantArray, Comparator.comparingInt(n -> n.id));
                    node.sortedDescendants = descendantArray;
                    interfaceList.add(node);

                } else {
                    // non-interface nodes don't have any requirements
                    node.sortedDescendants = emptyDescendant;
                }
                /*
                 * Relaying descendants to ancestors, but only need to add oneself, not all
                 * ancestors, due to the guarantees about the interface graph
                 */
                for (Node ancestor : node.sortedAncestors) {
                    descendantMap.computeIfAbsent(ancestor, k -> new HashSet<>()).add(node);
                }
            }
            this.interfaceNodes = interfaceList.toArray(new Node[0]);
            int maxDescendants = Integer.MIN_VALUE;
            int maxAncestors = Integer.MIN_VALUE;
            for (Node node : interfaceList) {
                maxDescendants = Math.max(node.sortedDescendants.length, maxDescendants);
                maxAncestors = Math.max(node.sortedAncestors.length, maxAncestors);
            }
            maxDescendants = Integer.MIN_VALUE;
            maxAncestors = Integer.MIN_VALUE;
            for (Node node : nodes) {
                maxDescendants = Math.max(node.sortedDescendants.length, maxDescendants);
                maxAncestors = Math.max(node.sortedAncestors.length, maxAncestors);
            }
        }

        Graph(Node[] nodes) {
            this.nodes = nodes;
        }

        /*
         * Given the set of reachable types ordered by maximum height, this method creates the
         * appropriate interface graph.
         */
        private static Graph buildInterfaceGraph(List<HostedType> heightOrderedTypes, Map<HostedType, List<HostedType>> subtypeMap) {
            Map<HostedType, Set<Node>> interfaceAncestors = new HashMap<>();

            /* By the time a node is reached, it will have all needed parent information. */
            ArrayList<Node> nodes = new ArrayList<>();
            for (HostedType type : heightOrderedTypes) {

                boolean isTypeInterface = isInterface(type);
                Set<Node> ancestors = interfaceAncestors.computeIfAbsent(type, k -> isTypeInterface ? new HashSet<>() : null);
                if (ancestors == null) {
                    /* This node does not need to be part of the interface graph */
                    continue;
                }

                int id = nodes.size();
                Node newNode = new Node(id, type, isTypeInterface);
                nodes.add(newNode);

                if (isTypeInterface) {
                    ancestors.add(newNode);
                }
                Node[] sortedAncestors = ancestors.toArray(new Node[0]);
                Arrays.sort(sortedAncestors, Comparator.comparingInt(n -> n.id));
                newNode.sortedAncestors = sortedAncestors;

                /* Passing ancestor information to children. */
                for (HostedType child : subtypeMap.get(type)) {
                    interfaceAncestors.computeIfAbsent(child, k -> new HashSet<>()).addAll(ancestors);
                }
            }

            Node[] nodeArray = nodes.toArray(new Node[0]);
            int maxAncestors = -1;
            for (Node node : nodeArray) {
                maxAncestors = Math.max(maxAncestors, node.sortedAncestors.length);
            }

            return new Graph(nodeArray);
        }

    }

    /**
     * This class represents a single ordering constraint. For range type checks to be possible, all
     * nodes contained within a contiguous group must have contiguous type ids.
     */
    private static final class ContiguousGroup {
        final int[] sortedGroupIds;

        PrimeMatrix primeMatrix;

        /* Using a timestamp to limit the number of times each group is checked. */
        int lastTimeStamp = -1; // hasn't been checked yet

        private ContiguousGroup(int[] sortedGroupIds) {
            this.sortedGroupIds = sortedGroupIds;
        }
    }

    /**
     * This class manages the a single slot and its constraints.
     */
    private static final class InterfaceSlot {

        final int id;
        int currentTimeStamp;
        int numReservedIDs;

        /**
         * The prime matrices currently associated with this slot. See {@link PrimeMatrix} for its
         * definition.
         */
        Set<PrimeMatrix> matrices = new HashSet<>();
        /**
         * A map from an id to all of the ContiguousGroups which contain that id.
         */
        Map<Integer, Set<ContiguousGroup>> columnToGroupingMap = new HashMap<>();

        private InterfaceSlot(int id) {
            this.id = id;
            currentTimeStamp = 0;
            numReservedIDs = 1; // Initially one id is reserved for nodes not utilizing this slot.
        }

        /**
         * Attempts to add an interface's constraints to this slot. If it can't, then the internal
         * state is unchanged.
         *
         * @return whether the interface's constraints were added to this slot
         */
        public boolean tryAddGrouping(Node interfaceNode) {
            // first, create new grouping requirement representing this node
            int[] sortedGroupIds = Arrays.stream(interfaceNode.sortedDescendants).mapToInt(n -> n.id).toArray();
            ContiguousGroup newGrouping = new ContiguousGroup(sortedGroupIds);

            /* Next, determining which, if any, primeMatrices this new grouping links together. */
            int timestamp = ++currentTimeStamp;
            ArrayList<ContiguousGroup> edges = new ArrayList<>();
            Set<PrimeMatrix> linkedPrimeMatrices = new HashSet<>();

            for (int column : sortedGroupIds) {
                Set<ContiguousGroup> groupings = columnToGroupingMap.get(column);
                if (groupings != null) {
                    for (ContiguousGroup existingGrouping : groupings) {
                        // only check group if it hasn't been checked already during this phase
                        if (existingGrouping.lastTimeStamp != timestamp) {
                            existingGrouping.lastTimeStamp = timestamp;
                            // seeing if the two groups strictly overlap
                            boolean strictlyOverlap = strictlyOverlaps(newGrouping, existingGrouping);
                            if (strictlyOverlap) {
                                edges.add(existingGrouping);
                                linkedPrimeMatrices.add(existingGrouping.primeMatrix);
                            }
                        }
                    }
                }
            }

            // creating new prime matrix represent the new connected subgraph
            PrimeMatrix newPrimeMatrix = new PrimeMatrix(newGrouping);
            newGrouping.primeMatrix = newPrimeMatrix;

            // link in connected prime matrices and check if new prime matrix satisfies consecutive
            // ones property
            boolean satisfiesC1P = newPrimeMatrix.incorporateMatrices(linkedPrimeMatrices, edges);
            if (!satisfiesC1P) {
                // Not successful: do not add any of this information.
                return false;
            }

            // check the number of IDs needed still fit within the given capacity
            int numIDsDelta = newPrimeMatrix.c1POrdering.size() - linkedPrimeMatrices.stream().mapToInt(n -> n.c1POrdering.size()).sum();
            assert numIDsDelta >= 0; // new constraints cannot reduce number of IDs.
            int newNumReservedIDs = numReservedIDs + numIDsDelta;
            if (newNumReservedIDs > SLOT_CAPACITY) {
                // too large -- cannot add this constraint.
                return false;
            }

            /* Was successful -> need to update metadata. */
            // update size
            numReservedIDs = newNumReservedIDs;

            // add new prime matrix
            matrices.add(newPrimeMatrix);

            // removed invalidated prime matrices and update primeMatrix links in existing
            // ContiguousGroups
            for (PrimeMatrix removedMatrix : linkedPrimeMatrices) {
                // removing old matrix from set
                matrices.remove(removedMatrix);
                for (ContiguousGroup grouping : removedMatrix.containedGroups) {
                    grouping.primeMatrix = newPrimeMatrix;
                }
            }

            // add new relation to proper columnToGroupingMap keys
            for (int connection : sortedGroupIds) {
                columnToGroupingMap.computeIfAbsent(connection, k -> new HashSet<>()).add(newGrouping);
            }

            return true;
        }

        /**
         * This method is used to determine whether the two groups "strictly overlap", meaning:
         * <ol>
         * <li>There is a least one overlapping value</li>
         * <li>One group is not a subset of the other</li>
         * </ol>
         *
         * <p>
         * Note this method is assuming a and b are sorted, are not identical, and there is at least
         * one element of overlap.
         */
        private static boolean strictlyOverlaps(ContiguousGroup a, ContiguousGroup b) {
            int[] aArray = a.sortedGroupIds;
            int[] bArray = b.sortedGroupIds;
            int aLength = aArray.length;
            int bLength = bArray.length;
            int aIdx = 0;
            int bIdx = 0;
            int numMatches = 0;
            while (aIdx < aLength && bIdx < bLength) {
                int aValue = aArray[aIdx];
                int bValue = bArray[bIdx];
                if (aValue == bValue) {
                    numMatches++;
                    aIdx++;
                    bIdx++;
                } else if (aValue < bValue) {
                    aIdx++;
                } else {
                    // aValue > bValue
                    bIdx++;
                }
            }
            int minLength = Math.min(aLength, bLength);
            assert numMatches != 0 && numMatches <= minLength; // must have at least one element of
                                                               // overlap
            assert !(aLength == bLength && numMatches == aLength); // the groups shouldn't be
                                                                   // exactly the same, or else they
                                                                   // could have been merged
            return numMatches != minLength;
        }

        /**
         * Getting a valid C1P order for all nodes within this slot. Nodes part of the same set can
         * be assigned the same ID value.
         */
        public List<Set<Integer>> getC1POrder() {
            /*
             * Order prime matrices based on the # of nodes, in decreasing order.
             *
             * By doing so, each retrieved matrix ordering will either be <ul> <li> Non-intersecting
             * with the previously added nodes. In this case the matrix's C1P ordering can be added
             * to the end </li> <li> A subset of one set previously added nodes contains the all of
             * the matrix's nodes. In this case, that set can be split and the new C1P ordering can
             * be added in this spot </li> </ul>
             */
            List<PrimeMatrix> sizeOrderedMatrices = matrices.stream().sorted(Comparator.comparingInt(n -> -(n.containedNodes.size()))).collect(Collectors.toList());

            List<Set<Integer>> c1POrdering = new ArrayList<>();
            Set<Integer> coveredNodes = new HashSet<>();
            for (PrimeMatrix matrix : sizeOrderedMatrices) {

                /* The new ordering constraints which must be applied. */
                List<Set<Integer>> newOrderingConstraints = matrix.c1POrdering;

                assert matrix.containedNodes.size() > 0; // can't have an empty matrix
                Integer matrixRepresentativeNode = matrix.containedNodes.stream().findAny().get();
                boolean hasOverlap = coveredNodes.contains(matrixRepresentativeNode);
                if (!hasOverlap) {
                    // no overlap -> just add nodes to end of the list
                    c1POrdering.addAll(newOrderingConstraints);
                    coveredNodes.addAll(matrix.containedNodes);

                } else {
                    /*
                     * when there is overlap, all overlapping nodes will be in one set in the
                     * current list.
                     */
                    assert coveredNodes.containsAll(matrix.containedNodes);

                    assert verifyC1POrderingProperty(c1POrdering, matrix);

                    for (int i = 0; i < c1POrdering.size(); i++) {
                        Set<Integer> item = c1POrdering.get(i);
                        /* It is enough to use one node to find where the overlap is */
                        hasOverlap = item.contains(matrixRepresentativeNode);
                        if (hasOverlap) {
                            item.removeAll(matrix.containedNodes);
                            c1POrdering.addAll(i + 1, newOrderingConstraints);
                            if (item.size() == 0) {
                                c1POrdering.remove(i);
                            }
                            break;
                        }
                    }
                }
            }

            return c1POrdering;
        }

        /**
         * Verifying assumption that all of the overlap will be confined to one set within the
         * current c1POrdering.
         */
        static boolean verifyC1POrderingProperty(List<Set<Integer>> c1POrdering, PrimeMatrix matrix) {
            ArrayList<Integer> overlappingSets = new ArrayList<>();
            for (int i = 0; i < c1POrdering.size(); i++) {
                Set<Integer> item = c1POrdering.get(i);
                boolean hasOverlap = item.stream().anyMatch(n -> matrix.containedNodes.contains(n));
                if (hasOverlap) {
                    overlappingSets.add(i);
                }
            }
            return overlappingSets.size() == 1;
        }
    }

    /**
     * Within consecutive one property (C1P) testing literature, in a graph where each
     * {@link ContiguousGroup} is a node and edges are between nodes that that "strictly overlap",
     * the graph can be decomposed into connected subgraphs, known as prime matrices.
     * <p>
     * Once the graph's prime matrices have been identified, it is sufficient to test each prime
     * matrix individually for the C1P property.
     */
    private static class PrimeMatrix {

        final ContiguousGroup initialGroup;
        List<ContiguousGroup> containedGroups;

        /* all of the strictly ordered edges within this prime matrix */
        Map<ContiguousGroup, Set<ContiguousGroup>> edgeMap;

        /**
         * To verify the consecutive ones property (C1P), two data structures are needed, the
         * current ordering (c1POrdering) and another keeping track of all of the nodes contained in
         * the current ordering (containedNodes).
         */
        List<Set<Integer>> c1POrdering;
        Set<Integer> containedNodes;

        PrimeMatrix(ContiguousGroup initialGroup) {
            this.initialGroup = initialGroup;
            containedGroups = new ArrayList<>();
            containedGroups.add(initialGroup);
            edgeMap = new HashMap<>();
        }

        public void initializeC1PInformation() {
            this.containedNodes = new HashSet<>();
            this.c1POrdering = new ArrayList<>();
        }

        public void copyC1PInformation(PrimeMatrix src) {
            this.containedNodes = new HashSet<>(src.containedNodes);
            this.c1POrdering = new ArrayList<>();
            for (Set<Integer> entry : src.c1POrdering) {
                this.c1POrdering.add(new HashSet<>(entry));
            }
        }

        /**
         * Adding in other prime matrices which strictly overlap the {@link #initialGroup}. All of
         * these prime matrices need to be combined into a single matrix, provided that an ordering
         * can be created where all of the contained groups satisfy the consecutive one property.
         *
         * @param matrices the other prime matrices which need to be combined into this matrix
         * @param edges the links between the {@link #initialGroup} and groups within the other
         *            prime matrices
         */
        public boolean incorporateMatrices(Set<PrimeMatrix> matrices, List<ContiguousGroup> edges) {
            assert containedGroups.size() == 1 : "Matrices can only be combined once";

            /*
             * Finding the prime matrix being combined with the most ContainedGroups. By placing
             * this prime matrix at the front of the spanning tree, it does not need to be rechecked
             * for the consecutive ones property.
             */
            PrimeMatrix largestMatrix = null;
            int largestMatrixSize = Integer.MIN_VALUE;
            for (PrimeMatrix matrix : matrices) {

                int matrixSize = matrix.containedGroups.size();
                assert matrixSize > 0;
                if (matrixSize > largestMatrixSize) {
                    largestMatrixSize = matrixSize;
                    largestMatrix = matrix;
                }
            }

            /* initializing the consecutive one property information */
            if (largestMatrix != null) {
                copyC1PInformation(largestMatrix);
            } else {
                initializeC1PInformation();
            }
            PrimeMatrix finalLargestMatrix = largestMatrix;

            /*
             * To verify the C1P for the combined prime matrix, each ContiguousGroup requirement
             * must be added, one by one, in the order of a travel of the matrix's spanning tree to
             * see if a valid ordering can be created.
             */
            List<ContiguousGroup> spanningTree = computeSpanningTree(edges, largestMatrix);
            int expectedNumNodes = 1 + matrices.stream().filter(n -> n != finalLargestMatrix).mapToInt(n -> n.containedGroups.size()).sum();
            assert spanningTree.size() == expectedNumNodes;
            for (ContiguousGroup grouping : spanningTree) {
                if (!addGroupAndCheckC1P(grouping)) {
                    return false;
                }
            }

            /*
             * At this point the consecutive ones property has been satisfied for the combined
             * matrix.
             */

            /* Updating the contained groups and adding the connect prime matrix's edges. */
            for (PrimeMatrix matrix : matrices) {
                List<ContiguousGroup> otherGroup = matrix.containedGroups;
                assert otherGroup.stream().noneMatch(containedGroups::contains) : "the intersection between all prime matrices should be null";
                containedGroups.addAll(otherGroup);

                Map<ContiguousGroup, Set<ContiguousGroup>> otherEdgeMap = matrix.edgeMap;
                for (Map.Entry<ContiguousGroup, Set<ContiguousGroup>> entry : otherEdgeMap.entrySet()) {
                    ContiguousGroup key = entry.getKey();
                    edgeMap.computeIfAbsent(key, k -> new HashSet<>()).addAll(entry.getValue());
                }
            }

            /*
             * Adding the edges between the initialGroup and ContiguousGroups within the other prime
             * matrices.
             */
            edgeMap.put(initialGroup, new HashSet<>());
            for (ContiguousGroup edge : edges) {
                edgeMap.get(initialGroup).add(edge);
                edgeMap.computeIfAbsent(edge, k -> new HashSet<>()).add(initialGroup);
            }

            return true;
        }

        /**
         * Computing the combined matrix's spanning tree for all groups which are not part of the
         * largest matrix.
         *
         * @param edges Connections from the {@link #initialGroup} to relationships in other
         *            matrices.
         */
        List<ContiguousGroup> computeSpanningTree(List<ContiguousGroup> edges, PrimeMatrix largestMatrix) {
            List<ContiguousGroup> list = new ArrayList<>();
            /*
             * adding initial group first since it is the only relation which has cross-matrix edges
             */
            list.add(initialGroup);

            Set<PrimeMatrix> coveredMatrices = new HashSet<>();
            if (largestMatrix != null) {
                coveredMatrices.add(largestMatrix);
            }
            /* Appending spanning tree for each uncovered matrix. */
            for (ContiguousGroup edge : edges) {
                PrimeMatrix matrix = edge.primeMatrix;
                if (!coveredMatrices.contains(matrix)) {
                    coveredMatrices.add(matrix);
                    list.addAll(matrix.getSpanningTree(edge));
                }
            }
            return list;
        }

        /**
         * Creating spanning for matrix starting from the provided node.
         * <p>
         * Note by virtue of the prime matrix graph property, all nodes are connected, i.e., are
         * reachable from any given node within the matrix.
         */
        List<ContiguousGroup> getSpanningTree(ContiguousGroup startingNode) {
            Set<ContiguousGroup> seenNodes = new HashSet<>();
            List<ContiguousGroup> list = new ArrayList<>();
            getSpanningTreeHelper(startingNode, list, seenNodes);
            return list;
        }

        private void getSpanningTreeHelper(ContiguousGroup node, List<ContiguousGroup> list, Set<ContiguousGroup> seenNodes) {
            list.add(node);
            seenNodes.add(node);
            Set<ContiguousGroup> edges = this.edgeMap.get(node);
            if (edges != null) {
                for (ContiguousGroup edge : edges) {
                    if (!seenNodes.contains(edge)) {
                        getSpanningTreeHelper(edge, list, seenNodes);
                    }
                }
            }
        }

        /**
         * When trying to find a valid c1POrdering, a "color" is assigned to each set based on the
         * new set of grouping constraints being added.
         */
        enum SetColor {
            EMPTY, // no nodes in the set are colored
            PARTIAL, // some nodes in the set are colored
            FULL; // all nodes in the set are colored

            /**
             * Return the color of the provide set, based on what is "colored" by the colored set.
             */
            private static SetColor getSetColor(Set<Integer> set, Set<Integer> coloredSet) {
                long numColoredNodes = set.stream().filter(coloredSet::contains).count();
                if (numColoredNodes == 0) {
                    return SetColor.EMPTY;
                } else if (numColoredNodes == set.size()) {
                    return SetColor.FULL;
                } else {
                    return SetColor.PARTIAL;
                }
            }

            /**
             * Removes all nodes from the provided set and returns them as a new set.
             *
             * @return Set of colored nodes from original set.
             */
            private static Set<Integer> splitOffColored(Set<Integer> set, Set<Integer> coloredSet) {
                Set<Integer> coloredNodes = set.stream().filter(coloredSet::contains).collect(Collectors.toSet());
                // assuming that this is invoked a partially colored set
                assert coloredNodes.size() != 0 && coloredNodes.size() < set.size();
                set.removeAll(coloredNodes);

                return coloredNodes;
            }
        }

        /**
         * Attempts to add a new grouping restraint to the {@link #c1POrdering}.
         * <p>
         * The code here follows the algorithm proposed in "A Simple Test for the Consecutive Ones
         * Property" by Wen-Lain Hsu.
         *
         * @return if the new grouping constraint was able to be added.
         */
        public boolean addGroupAndCheckC1P(ContiguousGroup grouping) {
            Set<Integer> newGroup = Arrays.stream(grouping.sortedGroupIds).boxed().collect(Collectors.toSet());

            /* Nodes that are part of this grouping, but aren't part of the current c1POrdering */
            Set<Integer> uncoveredNodes = newGroup.stream().filter(n -> !containedNodes.contains(n)).collect(Collectors.toSet());

            int numSets = c1POrdering.size();
            if (numSets == 0) {
                /* add the initial ordering requirement */
                c1POrdering.add(uncoveredNodes);

            } else if (numSets == 1) {
                /*
                 * always possible to add this constraint by computing A - (A ^ B), (A ^ B), and B -
                 * (A ^ B)
                 */
                // nodes which are only in the original group
                c1POrdering.get(0).removeAll(newGroup);
                // nodes which are in both groups (i.e. the intersection)
                c1POrdering.add(newGroup.stream().filter(containedNodes::contains).collect(Collectors.toSet()));
                // nodes which are only in the new group
                c1POrdering.add(uncoveredNodes);

            } else {
                /*
                 * More than one set is already present, need to use coloring to try to find a valid
                 * ordering.
                 */
                // COLUMN-PARTITION Algorithm Step 2

                /*
                 * Within the current ordering, recording the color of each set and which are the
                 * leftmost and rightmost colored sets
                 */
                SetColor[] setColors = new SetColor[c1POrdering.size()];
                int leftIntersect = Integer.MIN_VALUE;
                int rightIntersect = Integer.MIN_VALUE;
                for (int i = 0; i < c1POrdering.size(); i++) {
                    SetColor color = SetColor.getSetColor(c1POrdering.get(i), newGroup);
                    setColors[i] = color;
                    if (color != SetColor.EMPTY) {
                        if (leftIntersect == Integer.MIN_VALUE) {
                            leftIntersect = i;
                        }
                        rightIntersect = i;
                    }
                }
                /*
                 * Properties of the prime matrix and spanning tree means the new grouping must have
                 * an overlap with the current c1POrdering.
                 */
                assert leftIntersect != Integer.MIN_VALUE && rightIntersect != Integer.MIN_VALUE;

                // checking if all sets in between the intersections are full
                for (int i = leftIntersect + 1; i < rightIntersect; i++) {
                    if (setColors[i] != SetColor.FULL) {
                        // C1P cannot be satisfied
                        return false;
                    }
                }

                SetColor rightColor = setColors[rightIntersect];
                SetColor leftColor = setColors[leftIntersect];
                if (uncoveredNodes.size() == 0) {
                    // COLUMN-PARTITION Algorithm STEP 2.1
                    splitColoredNodes(rightColor, newGroup, rightIntersect, rightIntersect);
                    if (leftIntersect != rightIntersect) {
                        splitColoredNodes(leftColor, newGroup, leftIntersect, leftIntersect + 1);
                    }

                } else {
                    // COLUMN-PARTITION Algorithm STEP 2.2
                    // checking that either the left or right intersect are a subset of the the new
                    // grouping
                    if (leftIntersect == 0 && leftColor == SetColor.FULL) {
                        // splitting the right intersect to make sure nodes part of this collection
                        // are on the left size
                        splitColoredNodes(rightColor, newGroup, rightIntersect, rightIntersect);
                        c1POrdering.add(0, uncoveredNodes); // this is being added to the front of
                                                            // the list
                    } else if (rightIntersect == (numSets - 1) && rightColor == SetColor.FULL) {
                        splitColoredNodes(leftColor, newGroup, leftIntersect, leftIntersect + 1);
                        c1POrdering.add(uncoveredNodes); // this is being added to the end of the
                                                         // list
                    } else {
                        // could not find a valid ordering
                        return false;
                    }
                }
            }

            // recording that these nodes have been covered now
            containedNodes.addAll(uncoveredNodes);
            return true;
        }

        /**
         * If only partially colored, putting the colored nodes into a new set and inserting them
         * into a new place within the c1POrdering.
         */
        private void splitColoredNodes(SetColor color, Set<Integer> coloredSet, int srcIndex, int dstIndex) {
            assert color != SetColor.EMPTY;
            if (color != SetColor.FULL) {
                Set<Integer> newSet = SetColor.splitOffColored(c1POrdering.get(srcIndex), coloredSet);
                c1POrdering.add(dstIndex, newSet);
            }
        }
    }
}
