/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.virtual.phases.ea;

import static org.graalvm.compiler.core.common.GraalOptions.ReadEliminationMaxLoopVisits;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.core.common.CompareStrategy;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.MapCursor;
import org.graalvm.compiler.core.common.Pair;
import org.graalvm.compiler.core.common.EconomicMap;
import org.graalvm.compiler.core.common.EconomicSet;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeStoreNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.virtual.phases.ea.PEReadEliminationBlockState.ReadCacheEntry;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class PEReadEliminationClosure extends PartialEscapeClosure<PEReadEliminationBlockState> {

    private static final EnumMap<JavaKind, LocationIdentity> UNBOX_LOCATIONS;

    static {
        UNBOX_LOCATIONS = new EnumMap<>(JavaKind.class);
        for (JavaKind kind : JavaKind.values()) {
            UNBOX_LOCATIONS.put(kind, NamedLocationIdentity.immutable("PEA unbox " + kind.getJavaName()));
        }
    }

    public PEReadEliminationClosure(ScheduleResult schedule, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    LoweringProvider loweringProvider) {
        super(schedule, metaAccess, constantReflection, constantFieldProvider, loweringProvider);
    }

    @Override
    protected PEReadEliminationBlockState getInitialState() {
        return new PEReadEliminationBlockState();
    }

    @Override
    protected boolean processNode(Node node, PEReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        if (super.processNode(node, state, effects, lastFixedNode)) {
            return true;
        }

        if (node instanceof LoadFieldNode) {
            return processLoadField((LoadFieldNode) node, state, effects);
        } else if (node instanceof StoreFieldNode) {
            return processStoreField((StoreFieldNode) node, state, effects);
        } else if (node instanceof LoadIndexedNode) {
            return processLoadIndexed((LoadIndexedNode) node, state, effects);
        } else if (node instanceof StoreIndexedNode) {
            return processStoreIndexed((StoreIndexedNode) node, state, effects);
        } else if (node instanceof ArrayLengthNode) {
            return processArrayLength((ArrayLengthNode) node, state, effects);
        } else if (node instanceof UnboxNode) {
            return processUnbox((UnboxNode) node, state, effects);
        } else if (node instanceof UnsafeLoadNode) {
            return processUnsafeLoad((UnsafeLoadNode) node, state, effects);
        } else if (node instanceof UnsafeStoreNode) {
            return processUnsafeStore((UnsafeStoreNode) node, state, effects);
        } else if (node instanceof MemoryCheckpoint.Single) {
            COUNTER_MEMORYCHECKPOINT.increment();
            LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
            processIdentity(state, identity);
        } else if (node instanceof MemoryCheckpoint.Multi) {
            COUNTER_MEMORYCHECKPOINT.increment();
            for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                processIdentity(state, identity);
            }
        }

        return false;
    }

    private boolean processStore(FixedNode store, ValueNode object, LocationIdentity identity, int index, ValueNode value, PEReadEliminationBlockState state, GraphEffectList effects) {
        ValueNode unproxiedObject = GraphUtil.unproxify(object);
        ValueNode cachedValue = state.getReadCache(object, identity, index, this);

        ValueNode finalValue = getScalarAlias(value);
        boolean result = false;
        if (GraphUtil.unproxify(finalValue) == GraphUtil.unproxify(cachedValue)) {
            effects.deleteNode(store);
            result = true;
        }
        state.killReadCache(identity, index);
        state.addReadCache(unproxiedObject, identity, index, finalValue, this);
        return result;
    }

    private boolean processLoad(FixedNode load, ValueNode object, LocationIdentity identity, int index, PEReadEliminationBlockState state, GraphEffectList effects) {
        ValueNode unproxiedObject = GraphUtil.unproxify(object);
        ValueNode cachedValue = state.getReadCache(unproxiedObject, identity, index, this);
        if (cachedValue != null) {
            effects.replaceAtUsages(load, cachedValue);
            addScalarAlias(load, cachedValue);
            return true;
        } else {
            state.addReadCache(unproxiedObject, identity, index, load, this);
            return false;
        }
    }

    private boolean processUnsafeLoad(UnsafeLoadNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.offset().isConstant()) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && type.isArray()) {
                long offset = load.offset().asJavaConstant().asLong();
                int index = VirtualArrayNode.entryIndexForOffset(offset, load.accessKind(), type.getComponentType(), Integer.MAX_VALUE);
                ValueNode object = GraphUtil.unproxify(load.object());
                LocationIdentity location = NamedLocationIdentity.getArrayLocation(type.getComponentType().getJavaKind());
                ValueNode cachedValue = state.getReadCache(object, location, index, this);
                if (cachedValue != null && load.stamp().isCompatible(cachedValue.stamp())) {
                    effects.replaceAtUsages(load, cachedValue);
                    addScalarAlias(load, cachedValue);
                    return true;
                } else {
                    state.addReadCache(object, location, index, load, this);
                }
            }
        }
        return false;
    }

    private boolean processUnsafeStore(UnsafeStoreNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        ResolvedJavaType type = StampTool.typeOrNull(store.object());
        if (type != null && type.isArray()) {
            LocationIdentity location = NamedLocationIdentity.getArrayLocation(type.getComponentType().getJavaKind());
            if (store.offset().isConstant()) {
                long offset = store.offset().asJavaConstant().asLong();
                int index = VirtualArrayNode.entryIndexForOffset(offset, store.accessKind(), type.getComponentType(), Integer.MAX_VALUE);
                return processStore(store, store.object(), location, index, store.value(), state, effects);
            } else {
                processIdentity(state, location);
            }
        } else {
            state.killReadCache();
        }
        return false;
    }

    private boolean processArrayLength(ArrayLengthNode length, PEReadEliminationBlockState state, GraphEffectList effects) {
        return processLoad(length, length.array(), ARRAY_LENGTH_LOCATION, -1, state, effects);
    }

    private boolean processStoreField(StoreFieldNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (store.isVolatile()) {
            state.killReadCache();
            return false;
        }
        return processStore(store, store.object(), new FieldLocationIdentity(store.field()), -1, store.value(), state, effects);
    }

    private boolean processLoadField(LoadFieldNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.isVolatile()) {
            state.killReadCache();
            return false;
        }
        return processLoad(load, load.object(), new FieldLocationIdentity(load.field()), -1, state, effects);
    }

    private boolean processStoreIndexed(StoreIndexedNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(store.elementKind());
        if (store.index().isConstant()) {
            int index = ((JavaConstant) store.index().asConstant()).asInt();
            return processStore(store, store.array(), arrayLocation, index, store.value(), state, effects);
        } else {
            state.killReadCache(arrayLocation, -1);
        }
        return false;
    }

    private boolean processLoadIndexed(LoadIndexedNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.index().isConstant()) {
            int index = ((JavaConstant) load.index().asConstant()).asInt();
            LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(load.elementKind());
            return processLoad(load, load.array(), arrayLocation, index, state, effects);
        }
        return false;
    }

    private boolean processUnbox(UnboxNode unbox, PEReadEliminationBlockState state, GraphEffectList effects) {
        return processLoad(unbox, unbox.getValue(), UNBOX_LOCATIONS.get(unbox.getBoxingKind()), -1, state, effects);
    }

    private static void processIdentity(PEReadEliminationBlockState state, LocationIdentity identity) {
        if (identity.isAny()) {
            state.killReadCache();
        } else {
            state.killReadCache(identity, -1);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void processInitialLoopState(Loop<Block> loop, PEReadEliminationBlockState initialState) {
        super.processInitialLoopState(loop, initialState);

        if (!initialState.getReadCache().isEmpty()) {
            EconomicMap<ValueNode, Pair<ValueNode, Object>> firstValueSet = null;
            for (PhiNode phi : ((LoopBeginNode) loop.getHeader().getBeginNode()).phis()) {
                ValueNode firstValue = phi.valueAt(0);
                if (firstValue != null && phi.getStackKind().isObject()) {
                    ValueNode unproxified = GraphUtil.unproxify(firstValue);
                    Pair<ValueNode, Object> pair = new Pair<>(unproxified, null);
                    if (firstValueSet == null) {
                        firstValueSet = CollectionsFactory.newMap(CompareStrategy.IDENTITY_WITH_SYSTEM_HASHCODE);
                    }
                    Pair<ValueNode, Object> oldValue = firstValueSet.put(unproxified, pair);
                    pair.setRight(oldValue);
                }
            }

            if (firstValueSet != null) {
                ReadCacheEntry[] entries = new ReadCacheEntry[initialState.getReadCache().size()];
                int z = 0;
                for (ReadCacheEntry entry : initialState.getReadCache().getKeys()) {
                    entries[z++] = entry;
                }

                for (ReadCacheEntry entry : entries) {
                    ValueNode object = entry.object;
                    if (object != null) {
                        Pair<ValueNode, Object> pair = firstValueSet.get(object);
                        while (pair != null) {
                            initialState.addReadCache(pair.getLeft(), entry.identity, entry.index, initialState.getReadCache().get(entry), this);
                            pair = (Pair<ValueNode, Object>) pair.getRight();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, PEReadEliminationBlockState initialState, PEReadEliminationBlockState exitState, GraphEffectList effects) {
        super.processLoopExit(exitNode, initialState, exitState, effects);

        if (exitNode.graph().hasValueProxies()) {
            MapCursor<ReadCacheEntry, ValueNode> entry = exitState.getReadCache().getEntries();
            while (entry.advance()) {
                if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                    ValueNode value = exitState.getReadCache(entry.getKey().object, entry.getKey().identity, entry.getKey().index, this);
                    assert value != null : "Got null from read cache, entry's value:" + entry.getValue();
                    if (!(value instanceof ProxyNode) || ((ProxyNode) value).proxyPoint() != exitNode) {
                        ProxyNode proxy = new ValueProxyNode(value, exitNode);
                        effects.addFloatingNode(proxy, "readCacheProxy");
                        exitState.getReadCache().put(entry.getKey(), proxy);
                    }
                }
            }
        }
    }

    @Override
    protected PEReadEliminationBlockState cloneState(PEReadEliminationBlockState other) {
        return new PEReadEliminationBlockState(other);
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new ReadEliminationMergeProcessor(merge);
    }

    private class ReadEliminationMergeProcessor extends MergeProcessor {

        ReadEliminationMergeProcessor(Block mergeBlock) {
            super(mergeBlock);
        }

        @Override
        protected void merge(List<PEReadEliminationBlockState> states) {
            super.merge(states);

            mergeReadCache(states);
        }

        private void mergeReadCache(List<PEReadEliminationBlockState> states) {
            MapCursor<ReadCacheEntry, ValueNode> cursor = states.get(0).readCache.getEntries();
            while (cursor.advance()) {
                ReadCacheEntry key = cursor.getKey();
                ValueNode value = cursor.getValue();
                boolean phi = false;
                for (int i = 1; i < states.size(); i++) {
                    ValueNode otherValue = states.get(i).readCache.get(key);
                    // e.g. unsafe loads / stores with different access kinds have different stamps
                    // although location, object and offset are the same, in this case we cannot
                    // create a phi nor can we set a common value
                    if (otherValue == null || !value.stamp().isCompatible(otherValue.stamp())) {
                        value = null;
                        phi = false;
                        break;
                    }
                    if (!phi && otherValue != value) {
                        phi = true;
                    }
                }
                if (phi) {
                    PhiNode phiNode = getPhi(key, value.stamp().unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        ValueNode v = states.get(i).getReadCache(key.object, key.identity, key.index, PEReadEliminationClosure.this);
                        assert phiNode.stamp().isCompatible(v.stamp()) : "Cannot create read elimination phi for inputs with incompatible stamps.";
                        setPhiInput(phiNode, i, v);
                    }
                    newState.readCache.put(key, phiNode);
                } else if (value != null) {
                    newState.readCache.put(key, value);
                }
            }
            for (PhiNode phi : getPhis()) {
                if (phi.getStackKind() == JavaKind.Object) {
                    for (ReadCacheEntry entry : states.get(0).readCache.getKeys()) {
                        if (entry.object == getPhiValueAt(phi, 0)) {
                            mergeReadCachePhi(phi, entry.identity, entry.index, states);
                        }
                    }
                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, LocationIdentity identity, int index, List<PEReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[states.size()];
            values[0] = states.get(0).getReadCache(getPhiValueAt(phi, 0), identity, index, PEReadEliminationClosure.this);
            if (values[0] != null) {
                for (int i = 1; i < states.size(); i++) {
                    ValueNode value = states.get(i).getReadCache(getPhiValueAt(phi, i), identity, index, PEReadEliminationClosure.this);
                    // e.g. unsafe loads / stores with same identity and different access kinds see
                    // mergeReadCache(states)
                    if (value == null || !values[i - 1].stamp().isCompatible(value.stamp())) {
                        return;
                    }
                    values[i] = value;
                }

                PhiNode phiNode = getPhi(new ReadCacheEntry(identity, phi, index), values[0].stamp().unrestricted());
                mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
                for (int i = 0; i < values.length; i++) {
                    setPhiInput(phiNode, i, values[i]);
                }
                newState.readCache.put(new ReadCacheEntry(identity, phi, index), phiNode);
            }
        }
    }

    @Override
    protected void processKilledLoopLocations(Loop<Block> loop, PEReadEliminationBlockState initialState, PEReadEliminationBlockState mergedStates) {
        assert initialState != null;
        assert mergedStates != null;
        if (initialState.readCache.size() > 0) {
            LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
            // we have fully processed this loop the first time, remember to cache it the next time
            // it is visited
            if (loopKilledLocations == null) {
                loopKilledLocations = new LoopKillCache(1/* 1.visit */);
                loopLocationKillCache.put(loop, loopKilledLocations);
            } else {
                if (loopKilledLocations.visits() > ReadEliminationMaxLoopVisits.getValue()) {
                    // we have processed the loop too many times, kill all locations so the inner
                    // loop will never be processed more than once again on visit
                    loopKilledLocations.setKillsAll();
                } else {
                    // we have fully processed this loop >1 times, update the killed locations
                    EconomicSet<LocationIdentity> forwardEndLiveLocations = CollectionsFactory.newSet(CompareStrategy.EQUALS);
                    for (ReadCacheEntry entry : initialState.readCache.getKeys()) {
                        forwardEndLiveLocations.add(entry.identity);
                    }
                    for (ReadCacheEntry entry : mergedStates.readCache.getKeys()) {
                        forwardEndLiveLocations.remove(entry.identity);
                    }
                    // every location that is alive before the loop but not after is killed by the
                    // loop
                    for (LocationIdentity location : forwardEndLiveLocations) {
                        loopKilledLocations.rememberLoopKilledLocation(location);
                    }
                    if (Debug.isLogEnabled() && loopKilledLocations != null) {
                        Debug.log("[Early Read Elimination] Setting loop killed locations of loop at node %s with %s",
                                        loop.getHeader().getBeginNode(), forwardEndLiveLocations);
                    }
                }
                // remember the loop visit
                loopKilledLocations.visited();
            }
        }
    }

    @Override
    protected PEReadEliminationBlockState stripKilledLoopLocations(Loop<Block> loop, PEReadEliminationBlockState originalInitialState) {
        PEReadEliminationBlockState initialState = super.stripKilledLoopLocations(loop, originalInitialState);
        LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
        if (loopKilledLocations != null && loopKilledLocations.loopKillsLocations()) {
            Iterator<ReadCacheEntry> it = initialState.readCache.getKeys().iterator();
            while (it.hasNext()) {
                ReadCacheEntry entry = it.next();
                if (loopKilledLocations.containsLocation(entry.identity)) {
                    it.remove();
                }
            }
        }
        return initialState;
    }

}
