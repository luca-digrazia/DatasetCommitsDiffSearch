/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class ArrayIndexOfDispatchNode extends FixedWithNextNode implements Canonicalizable, Lowerable, MemoryAccess, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<ArrayIndexOfDispatchNode> TYPE = NodeClass.create(ArrayIndexOfDispatchNode.class);

    protected final ForeignCallSignature stubCallDescriptor;
    protected final JavaKind arrayKind;
    protected final JavaKind valueKind;
    protected final boolean findTwoConsecutive;

    @Input protected ValueNode arrayPointer;
    @Input protected ValueNode arrayLength;
    @Input protected ValueNode fromIndex;
    @Input protected NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) private MemoryKill lastLocationAccess;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    public ArrayIndexOfDispatchNode(@ConstantNodeParameter ForeignCallSignature stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive, ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, stubCallDescriptor, arrayKind, valueKind, findTwoConsecutive, arrayPointer, arrayLength, fromIndex, searchValues);
    }

    public ArrayIndexOfDispatchNode(NodeClass<? extends ArrayIndexOfDispatchNode> type, @ConstantNodeParameter ForeignCallSignature stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive, ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(type, StampFactory.forKind(JavaKind.Int));
        this.stubCallDescriptor = stubCallDescriptor;
        this.arrayKind = arrayKind;
        this.valueKind = valueKind;
        this.findTwoConsecutive = findTwoConsecutive;
        this.arrayPointer = arrayPointer;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);

    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    public ForeignCallSignature getStubCallDescriptor() {
        return stubCallDescriptor;
    }

    public ValueNode[] getStubCallArgs() {
        ValueNode[] ret = new ValueNode[searchValues.size() + 3];
        ret[0] = arrayPointer;
        ret[1] = arrayLength;
        ret[2] = fromIndex;
        for (int i = 0; i < searchValues.size(); i++) {
            ret[3 + i] = searchValues.get(i);
        }
        return ret;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    public ValueNode getArrayPointer() {
        return arrayPointer;
    }

    public ValueNode getFromIndex() {
        return fromIndex;
    }

    public NodeInputList<ValueNode> getSearchValues() {
        return searchValues;
    }

    public int getNumberOfValues() {
        return searchValues.size();
    }

    public JavaKind getArrayKind() {
        return arrayKind;
    }

    public JavaKind getValueKind() {
        return valueKind;
    }

    public JavaKind getComparisonKind() {
        return findTwoConsecutive ? (valueKind == JavaKind.Byte ? JavaKind.Char : JavaKind.Int) : valueKind;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!findTwoConsecutive && arrayPointer.isConstant() && ((ConstantNode) arrayPointer).getStableDimension() > 0 && fromIndex.isConstant()) {
            ConstantReflectionProvider provider = tool.getConstantReflection();
            JavaConstant arrayConstant = arrayPointer.asJavaConstant();
            int length = provider.readArrayLength(arrayConstant);
            int fromIndexConstant = fromIndex.asJavaConstant().asInt();

            if (searchValues.size() == 1 && searchValues.get(0).isConstant()) {
                int ch = searchValues.get(0).asJavaConstant().asInt();
                assert ch < Character.MIN_SUPPLEMENTARY_CODE_POINT;
                if (arrayKind == JavaKind.Byte) {
                    // Java 9+
                    if (valueKind == JavaKind.Byte && length < GraalOptions.StringIndexOfLimit.getValue(tool.getOptions())) {
                        for (int i = fromIndexConstant; i < length; i++) {
                            if ((provider.readArrayElement(arrayConstant, i).asInt() & 0xFF) == ch) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        return ConstantNode.forInt(-1);
                    } else if (length < GraalOptions.StringIndexOfLimit.getValue(tool.getOptions()) * 2) {
                        assert valueKind == JavaKind.Char;
                        length >>= 1;
                        for (int i = fromIndexConstant; i < length; i++) {
                            int b0 = provider.readArrayElement(arrayConstant, i * 2).asInt() & 0xFF;
                            int b1 = provider.readArrayElement(arrayConstant, i * 2 + 1).asInt() & 0xFF;
                            char c;
                            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                                c = (char) ((b0 << 8) | b1);
                            } else {
                                c = (char) (b0 | (b1 << 8));
                            }
                            if (c == ch) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        return ConstantNode.forInt(-1);
                    }
                } else if (arrayKind == JavaKind.Char && length < GraalOptions.StringIndexOfLimit.getValue(tool.getOptions())) {
                    // Java 8
                    assert valueKind == JavaKind.Char;
                    for (int i = fromIndexConstant; i < length; i++) {
                        if ((provider.readArrayElement(arrayConstant, i).asInt() & 0xFFFF) == ch) {
                            return ConstantNode.forInt(i);
                        }
                    }
                    return ConstantNode.forInt(-1);
                }
            }
        }
        return this;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    private static native int arrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1);

    @NodeIntrinsic
    private static native int arrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1);

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1) {
        return arrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1) {
        return arrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }
}
