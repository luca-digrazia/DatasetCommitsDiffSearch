/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A default frame slot based implementation of variables contained in the (default) frame scope.
 */
@SuppressWarnings({"deprecation", "all"})
final class DefaultScopeVariables extends com.oracle.truffle.api.metadata.ScopeProvider.AbstractScope {

    private final RootNode root;

    DefaultScopeVariables(Node node) {
        this.root = node.getRootNode();
    }

    @Override
    public String getName() {
        return root.getName();
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public Object getVariables(Frame frame) {
        List<? extends FrameSlot> slots;
        if (frame == null) {
            slots = root.getFrameDescriptor().getSlots();
        } else {
            slots = frame.getFrameDescriptor().getSlots();
            // Filter out slots with null values:
            List<FrameSlot> nonNulls = null;
            int lastI = 0;
            for (int i = 0; i < slots.size(); i++) {
                if (frame.getValue(slots.get(i)) == null) {
                    if (nonNulls == null) {
                        nonNulls = new ArrayList<>(slots.size());
                    }
                    nonNulls.addAll(slots.subList(lastI, i));
                    lastI = i + 1;
                }
            }
            if (nonNulls != null) {
                if (lastI < slots.size()) {
                    nonNulls.addAll(slots.subList(lastI, slots.size()));
                }
                slots = nonNulls;
            }
        }
        Map<String, FrameSlot> slotsMap;
        if (slots.isEmpty()) {
            slotsMap = Collections.emptyMap();
        } else if (slots.size() == 1) {
            FrameSlot slot = slots.get(0);
            slotsMap = Collections.singletonMap(Objects.toString(slot.getIdentifier()), slot);
        } else {
            slotsMap = new LinkedHashMap<>(slots.size());
            for (FrameSlot slot : slots) {
                slotsMap.put(Objects.toString(slot.getIdentifier()), slot);
            }
        }
        return new VariablesMapObject(slotsMap, frame);
    }

    @Override
    public Object getArguments(Frame frame) {
        Object[] args;
        if (frame == null) {
            args = new Object[0];
        } else {
            args = frame.getArguments();
        }
        return new ArgumentsArrayObject(args);
    }

    @Override
    protected com.oracle.truffle.api.metadata.ScopeProvider.AbstractScope findParent() {
        return null;
    }

    static final class VariablesMapObject implements TruffleObject {

        final Map<String, ? extends FrameSlot> slots;
        final Frame frame;

        private VariablesMapObject(Map<String, ? extends FrameSlot> slots, Frame frame) {
            this.slots = slots;
            this.frame = frame;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return VariablesMapMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof VariablesMapObject;
        }

        @MessageResolution(receiverType = VariablesMapObject.class)
        static class VariablesMapMessageResolution {

            @Resolve(message = "HAS_KEYS")
            abstract static class VarsMapHasKeysNode extends Node {

                public Object access(VariablesMapObject varMap) {
                    return true;
                }
            }

            @Resolve(message = "KEYS")
            abstract static class VarsMapKeysNode extends Node {

                @TruffleBoundary
                public Object access(VariablesMapObject varMap) {
                    return new VariableNamesObject(varMap.slots.keySet());
                }
            }

            @Resolve(message = "READ")
            abstract static class VarsMapReadNode extends Node {

                @TruffleBoundary
                public Object access(VariablesMapObject varMap, String name) {
                    if (varMap.frame == null) {
                        throw UnsupportedMessageException.raise(Message.READ);
                    }
                    FrameSlot slot = varMap.slots.get(name);
                    if (slot == null) {
                        throw UnknownIdentifierException.raise(name);
                    } else {
                        return varMap.frame.getValue(slot);
                    }
                }
            }

            @Resolve(message = "WRITE")
            abstract static class VarsMapWriteNode extends Node {

                @TruffleBoundary
                public Object access(VariablesMapObject varMap, String name, Object value) {
                    if (varMap.frame == null) {
                        throw UnsupportedMessageException.raise(Message.WRITE);
                    }
                    FrameSlot slot = varMap.slots.get(name);
                    if (slot == null) {
                        throw UnknownIdentifierException.raise(name);
                    } else {
                        varMap.frame.setObject(slot, value);
                        return value;
                    }
                }
            }

        }
    }

    static final class VariableNamesObject implements TruffleObject {

        final List<String> names;

        private VariableNamesObject(Set<String> names) {
            this.names = new ArrayList<>(names);
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return VariableNamesMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof VariableNamesObject;
        }

        @MessageResolution(receiverType = VariableNamesObject.class)
        static final class VariableNamesMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class VarNamesHasSizeNode extends Node {

                public Object access(VariableNamesObject varNames) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class VarNamesGetSizeNode extends Node {

                public Object access(VariableNamesObject varNames) {
                    return varNames.names.size();
                }
            }

            @Resolve(message = "READ")
            abstract static class VarNamesReadNode extends Node {

                @TruffleBoundary
                public Object access(VariableNamesObject varNames, int index) {
                    try {
                        return varNames.names.get(index);
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }

        }
    }

    static final class ArgumentsArrayObject implements TruffleObject {

        final Object[] args;

        ArgumentsArrayObject(Object[] args) {
            this.args = args;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ArgumentsArrayMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ArgumentsArrayObject;
        }

        @MessageResolution(receiverType = ArgumentsArrayObject.class)
        static final class ArgumentsArrayMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class ArgsArrHasSizeNode extends Node {

                public Object access(ArgumentsArrayObject argsArr) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class ArgsArrGetSizeNode extends Node {

                public Object access(ArgumentsArrayObject argsArr) {
                    return argsArr.args.length;
                }
            }

            @Resolve(message = "READ")
            abstract static class ArgsArrReadNode extends Node {

                @TruffleBoundary
                public Object access(ArgumentsArrayObject argsArr, int index) {
                    try {
                        return argsArr.args[index];
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }

            @Resolve(message = "WRITE")
            abstract static class ArgsArrWriteNode extends Node {

                @TruffleBoundary
                public Object access(ArgumentsArrayObject argsArr, int index, Object value) {
                    try {
                        argsArr.args[index] = value;
                        return value;
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }

        }
    }
}
