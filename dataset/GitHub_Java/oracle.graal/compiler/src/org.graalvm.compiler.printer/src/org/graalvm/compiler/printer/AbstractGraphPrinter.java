/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.printer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.graph.Edges;
import static org.graalvm.compiler.graph.Edges.Type.Inputs;
import static org.graalvm.compiler.graph.Edges.Type.Successors;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

abstract class AbstractGraphPrinter implements GraphPrinter {
    private static final Charset utf8 = Charset.forName("UTF-8");

    private static final int CONSTANT_POOL_MAX_SIZE = 8000;

    private static final int BEGIN_GROUP = 0x00;
    private static final int BEGIN_GRAPH = 0x01;
    private static final int CLOSE_GROUP = 0x02;

    private static final int POOL_NEW = 0x00;
    private static final int POOL_STRING = 0x01;
    private static final int POOL_ENUM = 0x02;
    private static final int POOL_CLASS = 0x03;
    private static final int POOL_METHOD = 0x04;
    private static final int POOL_NULL = 0x05;
    private static final int POOL_NODE_CLASS = 0x06;
    private static final int POOL_FIELD = 0x07;
    private static final int POOL_SIGNATURE = 0x08;
    private static final int POOL_NODE_SOURCE_POSITION = 0x09;

    private static final int PROPERTY_POOL = 0x00;
    private static final int PROPERTY_INT = 0x01;
    private static final int PROPERTY_LONG = 0x02;
    private static final int PROPERTY_DOUBLE = 0x03;
    private static final int PROPERTY_FLOAT = 0x04;
    private static final int PROPERTY_TRUE = 0x05;
    private static final int PROPERTY_FALSE = 0x06;
    private static final int PROPERTY_ARRAY = 0x07;
    private static final int PROPERTY_SUBGRAPH = 0x08;

    private static final int KLASS = 0x00;
    private static final int ENUM_KLASS = 0x01;

    static final int CURRENT_MAJOR_VERSION = 4;
    static final int CURRENT_MINOR_VERSION = 0;

    static final byte[] MAGIC_BYTES = {'B', 'I', 'G', 'V'};

    private final ConstantPool constantPool;
    private final ByteBuffer buffer;
    private final WritableByteChannel channel;

    AbstractGraphPrinter(WritableByteChannel channel) throws IOException {
        constantPool = new ConstantPool();
        buffer = ByteBuffer.allocateDirect(256 * 1024);
        this.channel = channel;
        writeVersion();
    }

    @SuppressWarnings("all")
    @Override
    public void print(Graph graph, Map<Object, Object> properties, int id, String format, Object... args) throws IOException {
        writeByte(BEGIN_GRAPH);
        if (CURRENT_MAJOR_VERSION >= 3) {
            writeInt(id);
            writeString(format);
            writeInt(args.length);
            for (Object a : args) {
                writePropertyObject(a);
            }
        } else {
            writePoolObject(formatTitle(id, format, args));
        }
        writeGraph(new GraphInfo(graph), properties);
        flush();
    }

    @Override
    public void beginGroup(String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException {
        writeByte(BEGIN_GROUP);
        writePoolObject(name);
        writePoolObject(shortName);
        writePoolObject(method);
        writeInt(bci);
        writeProperties(properties);
    }

    @Override
    public void endGroup() throws IOException {
        writeByte(CLOSE_GROUP);
    }

    @Override
    public void close() {
        try {
            flush();
            channel.close();
        } catch (IOException ex) {
            throw new Error(ex);
        }
    }

    abstract Graph findGraph(Object obj);

    abstract ResolvedJavaMethod findMethod(Object obj);

    abstract void findEdges(NodeClass<?> nodeClass, boolean dumpInputs, List<String> names, List<Boolean> direct, List<InputType> types);

    abstract int findNodeId(Node n);

    abstract int findNodesCount(GraphInfo info);

    abstract Iterable<Node> findNodes(GraphInfo info);

    abstract void findNodeProperties(Node node, Map<Object, Object> props, GraphInfo info);

    abstract List<Node> findBlockNodes(GraphInfo info, Block block);

    abstract int findBlockId(Block sux);

    abstract List<Block> findBlockSuccessors(Block block);

    private void writeVersion() throws IOException {
        writeBytesRaw(MAGIC_BYTES);
        writeByte(CURRENT_MAJOR_VERSION);
        writeByte(CURRENT_MINOR_VERSION);
    }

    private void flush() throws IOException {
        buffer.flip();
        /*
         * Try not to let interrupted threads aborting the write. There's still a race here but an
         * interrupt that's been pending for a long time shouldn't stop this writing.
         */
        boolean interrupted = Thread.interrupted();
        try {
            channel.write(buffer);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        buffer.compact();
    }

    private void ensureAvailable(int i) throws IOException {
        assert buffer.capacity() >= i : "Can not make " + i + " bytes available, buffer is too small";
        while (buffer.remaining() < i) {
            flush();
        }
    }

    final void writeByte(int b) throws IOException {
        ensureAvailable(1);
        buffer.put((byte) b);
    }

    final void writeInt(int b) throws IOException {
        ensureAvailable(4);
        buffer.putInt(b);
    }

    final void writeLong(long b) throws IOException {
        ensureAvailable(8);
        buffer.putLong(b);
    }

    final void writeDouble(double b) throws IOException {
        ensureAvailable(8);
        buffer.putDouble(b);
    }

    final void writeFloat(float b) throws IOException {
        ensureAvailable(4);
        buffer.putFloat(b);
    }

    final void writeShort(char b) throws IOException {
        ensureAvailable(2);
        buffer.putChar(b);
    }

    final void writeString(String str) throws IOException {
        byte[] bytes = str.getBytes(utf8);
        writeBytes(bytes);
    }

    final void writeBytes(byte[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            writeBytesRaw(b);
        }
    }

    private void writeBytesRaw(byte[] b) throws IOException {
        int bytesWritten = 0;
        while (bytesWritten < b.length) {
            int toWrite = Math.min(b.length - bytesWritten, buffer.capacity());
            ensureAvailable(toWrite);
            buffer.put(b, bytesWritten, toWrite);
            bytesWritten += toWrite;
        }
    }

    private void writeInts(int[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            int sizeInBytes = b.length * 4;
            ensureAvailable(sizeInBytes);
            buffer.asIntBuffer().put(b);
            buffer.position(buffer.position() + sizeInBytes);
        }
    }

    private void writeDoubles(double[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            int sizeInBytes = b.length * 8;
            ensureAvailable(sizeInBytes);
            buffer.asDoubleBuffer().put(b);
            buffer.position(buffer.position() + sizeInBytes);
        }
    }

    final void writePoolObject(Object object) throws IOException {
        if (object == null) {
            writeByte(POOL_NULL);
            return;
        }
        Character id = constantPool.get(object);
        if (id == null) {
            addPoolEntry(object);
        } else {
            if (object instanceof Enum<?>) {
                writeByte(POOL_ENUM);
            } else if (object instanceof Class<?> || object instanceof JavaType) {
                writeByte(POOL_CLASS);
            } else if (object instanceof NodeClass) {
                writeByte(POOL_NODE_CLASS);
            } else if (object instanceof ResolvedJavaField) {
                writeByte(POOL_FIELD);
            } else if (object instanceof Signature) {
                writeByte(POOL_SIGNATURE);
            } else if (CURRENT_MAJOR_VERSION >= 4 && object instanceof NodeSourcePosition) {
                writeByte(POOL_NODE_SOURCE_POSITION);
            } else {
                if (findMethod(object) != null) {
                    writeByte(POOL_METHOD);
                } else {
                    writeByte(POOL_STRING);
                }
            }
            writeShort(id.charValue());
        }
    }

    private static String getClassName(Class<?> klass) {
        if (!klass.isArray()) {
            return klass.getName();
        }
        return getClassName(klass.getComponentType()) + "[]";
    }

    final void writeGraph(GraphInfo graph, Map<Object, Object> properties) throws IOException {
        writeProperties(properties);
        writeNodes(graph);
        writeBlocks(graph.blocks, graph);
    }

    static final class GraphInfo {
        final Graph graph;
        final ControlFlowGraph cfg;
        final BlockMap<List<Node>> blockToNodes;
        final NodeMap<Block> nodeToBlocks;
        final List<Block> blocks;

        private GraphInfo(Graph graph) {
            this.graph = graph;
            StructuredGraph.ScheduleResult scheduleResult = null;
            if (graph instanceof StructuredGraph) {

                StructuredGraph structuredGraph = (StructuredGraph) graph;
                scheduleResult = structuredGraph.getLastSchedule();
                if (scheduleResult == null) {

                    // Also provide a schedule when an error occurs
                    if (GraalDebugConfig.Options.PrintGraphWithSchedule.getValue(graph.getOptions()) || Debug.contextLookup(Throwable.class) != null) {
                        try {
                            SchedulePhase schedule = new SchedulePhase(graph.getOptions());
                            schedule.apply(structuredGraph);
                            scheduleResult = structuredGraph.getLastSchedule();
                        } catch (Throwable t) {
                        }
                    }

                }
            }
            cfg = scheduleResult == null ? Debug.contextLookup(ControlFlowGraph.class) : scheduleResult.getCFG();
            blockToNodes = scheduleResult == null ? null : scheduleResult.getBlockToNodesMap();
            nodeToBlocks = scheduleResult == null ? null : scheduleResult.getNodeToBlockMap();
            blocks = cfg == null ? null : Arrays.asList(cfg.getBlocks());
        }
    }

    private void writeNodes(GraphInfo info) throws IOException {
        Map<Object, Object> props = new HashMap<>();

        writeInt(findNodesCount(info));

        for (Node node : findNodes(info)) {
            NodeClass<?> nodeClass = node.getNodeClass();
            findNodeProperties(node, props, info);

            writeInt(findNodeId(node));
            writePoolObject(nodeClass);
            writeByte(node.predecessor() == null ? 0 : 1);
            writeProperties(props);
            writeEdges(node, Inputs);
            writeEdges(node, Successors);

            props.clear();
        }
    }

    private void writeEdges(Node node, Edges.Type type) throws IOException {
        NodeClass<?> nodeClass = node.getNodeClass();
        Edges edges = nodeClass.getEdges(type);
        final long[] curOffsets = edges.getOffsets();
        for (int i = 0; i < edges.getDirectCount(); i++) {
            writeNodeRef(Edges.getNode(node, curOffsets, i));
        }
        for (int i = edges.getDirectCount(); i < edges.getCount(); i++) {
            NodeList<Node> list = Edges.getNodeList(node, curOffsets, i);
            if (list == null) {
                writeShort((char) 0);
            } else {
                int listSize = list.count();
                assert listSize == ((char) listSize);
                writeShort((char) listSize);
                for (Node edge : list) {
                    writeNodeRef(edge);
                }
            }
        }
    }

    private void writeNodeRef(Node node) throws IOException {
        writeInt(findNodeId(node));
    }

    private void writeBlocks(List<Block> blocks, GraphInfo info) throws IOException {
        if (blocks != null) {
            for (Block block : blocks) {
                List<Node> nodes = findBlockNodes(info, block);
                if (nodes == null) {
                    writeInt(0);
                    return;
                }
            }
            writeInt(blocks.size());
            for (Block block : blocks) {
                List<Node> nodes = findBlockNodes(info, block);
                List<Node> extraNodes = new LinkedList<>();
                writeInt(findBlockId(block));
                for (Node node : nodes) {
                    if (node instanceof AbstractMergeNode) {
                        AbstractMergeNode merge = (AbstractMergeNode) node;
                        for (PhiNode phi : merge.phis()) {
                            if (!nodes.contains(phi)) {
                                extraNodes.add(phi);
                            }
                        }
                    }
                }
                writeInt(nodes.size() + extraNodes.size());
                for (Node node : nodes) {
                    writeInt(findNodeId(node));
                }
                for (Node node : extraNodes) {
                    writeInt(findNodeId(node));
                }
                final List<Block> successors = findBlockSuccessors(block);
                writeInt(successors.size());
                for (Block sux : successors) {
                    writeInt(findBlockId(sux));
                }
            }
        } else {
            writeInt(0);
        }
    }

    private void writeEdgesInfo(NodeClass<?> nodeClass, boolean dumpInputs) throws IOException {
        List<String> names = new ArrayList<>();
        List<Boolean> direct = new ArrayList<>();
        List<InputType> types = new ArrayList<>();
        findEdges(nodeClass, dumpInputs, names, direct, types);
        writeShort((char) names.size());
        for (int i = 0; i < names.size(); i++) {
            writeByte(direct.get(i) ? 0 : 1);
            writePoolObject(names.get(i));
            if (dumpInputs) {
                writePoolObject(types.get(i));
            }
        }
    }

    @SuppressWarnings("all")
    private void addPoolEntry(Object object) throws IOException {
        char index = constantPool.add(object);
        writeByte(POOL_NEW);
        writeShort(index);
        if (object instanceof Class<?>) {
            Class<?> klass = (Class<?>) object;
            writeByte(POOL_CLASS);
            writeString(getClassName(klass));
            if (klass.isEnum()) {
                writeByte(ENUM_KLASS);
                Object[] enumConstants = klass.getEnumConstants();
                writeInt(enumConstants.length);
                for (Object o : enumConstants) {
                    writePoolObject(((Enum<?>) o).name());
                }
            } else {
                writeByte(KLASS);
            }
        } else if (object instanceof Enum<?>) {
            writeByte(POOL_ENUM);
            writePoolObject(object.getClass());
            writeInt(((Enum<?>) object).ordinal());
        } else if (object instanceof JavaType) {
            JavaType type = (JavaType) object;
            writeByte(POOL_CLASS);
            writeString(type.toJavaName());
            writeByte(KLASS);
        } else if (object instanceof NodeClass) {
            NodeClass<?> nodeClass = (NodeClass<?>) object;
            writeByte(POOL_NODE_CLASS);
            if (CURRENT_MAJOR_VERSION >= 3) {
                writePoolObject(nodeClass.getJavaClass());
                writeString(nodeClass.getNameTemplate());
            } else {
                writeString(nodeClass.getJavaClass().getSimpleName());
                String nameTemplate = nodeClass.getNameTemplate();
                writeString(nameTemplate.isEmpty() ? nodeClass.shortName() : nameTemplate);
            }
            writeEdgesInfo(nodeClass, true);
            writeEdgesInfo(nodeClass, false);
        } else if (object instanceof ResolvedJavaField) {
            writeByte(POOL_FIELD);
            ResolvedJavaField field = ((ResolvedJavaField) object);
            writePoolObject(field.getDeclaringClass());
            writePoolObject(field.getName());
            writePoolObject(field.getType().getName());
            writeInt(field.getModifiers());
        } else if (object instanceof Signature) {
            writeByte(POOL_SIGNATURE);
            Signature signature = ((Signature) object);
            int args = signature.getParameterCount(false);
            writeShort((char) args);
            for (int i = 0; i < args; i++) {
                writePoolObject(signature.getParameterType(i, null).getName());
            }
            writePoolObject(signature.getReturnType(null).getName());
        } else if (CURRENT_MAJOR_VERSION >= 4 && object instanceof NodeSourcePosition) {
            writeByte(POOL_NODE_SOURCE_POSITION);
            NodeSourcePosition pos = (NodeSourcePosition) object;
            ResolvedJavaMethod method = pos.getMethod();
            writePoolObject(method);
            final int bci = pos.getBCI();
            writeInt(bci);
            StackTraceElement ste = method.asStackTraceElement(bci);
            if (ste != null) {
                writePoolObject(ste.getFileName());
                writeInt(ste.getLineNumber());
            } else {
                writePoolObject(null);
            }
            writePoolObject(pos.getCaller());
        } else {
            ResolvedJavaMethod method = findMethod(object);
            if (method == null) {
                writeByte(POOL_STRING);
                writeString(object.toString());
                return;
            }
            writeByte(POOL_METHOD);
            writePoolObject(method.getDeclaringClass());
            writePoolObject(method.getName());
            writePoolObject(method.getSignature());
            writeInt(method.getModifiers());
            writeBytes(method.getCode());
        }
    }

    final void writePropertyObject(Object obj) throws IOException {
        if (obj instanceof Integer) {
            writeByte(PROPERTY_INT);
            writeInt(((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            writeByte(PROPERTY_LONG);
            writeLong(((Long) obj).longValue());
        } else if (obj instanceof Double) {
            writeByte(PROPERTY_DOUBLE);
            writeDouble(((Double) obj).doubleValue());
        } else if (obj instanceof Float) {
            writeByte(PROPERTY_FLOAT);
            writeFloat(((Float) obj).floatValue());
        } else if (obj instanceof Boolean) {
            if (((Boolean) obj).booleanValue()) {
                writeByte(PROPERTY_TRUE);
            } else {
                writeByte(PROPERTY_FALSE);
            }
        } else if (obj != null && obj.getClass().isArray()) {
            Class<?> componentType = obj.getClass().getComponentType();
            if (componentType.isPrimitive()) {
                if (componentType == Double.TYPE) {
                    writeByte(PROPERTY_ARRAY);
                    writeByte(PROPERTY_DOUBLE);
                    writeDoubles((double[]) obj);
                } else if (componentType == Integer.TYPE) {
                    writeByte(PROPERTY_ARRAY);
                    writeByte(PROPERTY_INT);
                    writeInts((int[]) obj);
                } else {
                    writeByte(PROPERTY_POOL);
                    writePoolObject(obj);
                }
            } else {
                writeByte(PROPERTY_ARRAY);
                writeByte(PROPERTY_POOL);
                Object[] array = (Object[]) obj;
                writeInt(array.length);
                for (Object o : array) {
                    writePoolObject(o);
                }
            }
        } else {
            Graph g = findGraph(obj);
            if (g == null) {
                writeByte(PROPERTY_POOL);
                writePoolObject(obj);
            } else {
                writeByte(PROPERTY_SUBGRAPH);
                writeGraph(new GraphInfo(g), null);
            }
        }
    }

    final void writeProperties(Map<Object, Object> props) throws IOException {
        if (props == null) {
            writeShort((char) 0);
            return;
        }
        // properties
        writeShort((char) props.size());
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            writePoolObject(key);
            writePropertyObject(entry.getValue());
        }
    }

    private static final class ConstantPool extends LinkedHashMap<Object, Character> {

        private final LinkedList<Character> availableIds;
        private char nextId;
        private static final long serialVersionUID = -2676889957907285681L;

        ConstantPool() {
            super(50, 0.65f);
            availableIds = new LinkedList<>();
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Object, Character> eldest) {
            if (size() > CONSTANT_POOL_MAX_SIZE) {
                availableIds.addFirst(eldest.getValue());
                return true;
            }
            return false;
        }

        private Character nextAvailableId() {
            if (!availableIds.isEmpty()) {
                return availableIds.removeFirst();
            }
            return nextId++;
        }

        public char add(Object obj) {
            Character id = nextAvailableId();
            put(obj, id);
            return id;
        }
    }

}
