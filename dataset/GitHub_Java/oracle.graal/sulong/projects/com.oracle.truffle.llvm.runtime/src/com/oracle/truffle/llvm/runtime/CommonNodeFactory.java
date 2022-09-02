package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebugGlobalVariable;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugManagedValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMFrameValueAccess;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugBuilder;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugSimpleObjectBuilder;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMFrameValueAccessImpl;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMToDebugDeclarationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMToDebugValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMPointerDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessGlobalVariableStorageNode;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

public class CommonNodeFactory {

    public CommonNodeFactory(){
    }

    public static LLVMLoadNode createLoadNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1LoadNodeGen.create(null);
            case I8:
                return LLVMI8LoadNodeGen.create(null);
            case I16:
                return LLVMI16LoadNodeGen.create(null);
            case I32:
                return LLVMI32LoadNodeGen.create(null);
            case I64:
                return LLVMI64LoadNodeGen.create(null);
            case FLOAT:
                return LLVMFloatLoadNodeGen.create(null);
            case DOUBLE:
                return LLVMDoubleLoadNodeGen.create(null);
            case POINTER:
                return LLVMPointerDirectLoadNodeGen.create(null);
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMStoreNode createStoreNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1StoreNodeGen.create(null, null);
            case I8:
                return LLVMI8StoreNodeGen.create(null, null);
            case I16:
                return LLVMI16StoreNodeGen.create(null, null);
            case I32:
                return LLVMI32StoreNodeGen.create(null, null);
            case I64:
                return LLVMI64StoreNodeGen.create(null, null);
            case FLOAT:
                return LLVMFloatStoreNodeGen.create(null, null);
            case DOUBLE:
                return LLVMDoubleStoreNodeGen.create(null, null);
            case POINTER:
                return LLVMPointerStoreNodeGen.create(null, null);
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static TruffleObject toGenericDebuggerValue(Object llvmType, Object value, DataLayout dataLayout) {
        final TruffleObject complexObject = asDebuggerIRValue(llvmType, value, dataLayout);
        if (complexObject != null) {
            return complexObject;
        }

        return LLVMDebugManagedValue.create(llvmType, value);
    }

    private static TruffleObject asDebuggerIRValue(Object llvmType, Object value, DataLayout dataLayout) {
        final Type type;
        if (llvmType instanceof Type) {
            type = (Type) llvmType;
        } else {
            return null;
        }

        // e.g. debugger symbols
        if (type instanceof MetaType) {
            return null;
        }

        final LLVMSourceType sourceType = LLVMSourceTypeFactory.resolveType(type, dataLayout);
        if (sourceType == null) {
            return null;
        }

        // after frame-nulling the actual vector length does not correspond to the type anymore
        if (value instanceof LLVMVector && ((LLVMVector) value).getLength() == 0) {
            return null;
        }

        // after frame-nulling the actual bitsize does not correspond to the type anymore
        if (value instanceof LLVMIVarBit && ((LLVMIVarBit) value).getBitSize() == 0) {
            return null;
        }

        final LLVMDebugValue debugValue = createDebugValueBuilder().build(value);
        if (debugValue == LLVMDebugValue.UNAVAILABLE) {
            return null;
        }

        return LLVMDebugObject.instantiate(sourceType, 0L, debugValue, null);
    }

    public static LLVMStatementNode createBasicBlockNode(LLVMStatementNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId,
                                                  String blockName, LLVMContext context) {
        return LLVMBasicBlockNode.createBasicBlockNode(context, statementNodes, terminatorNode, blockId, blockName);
    }

    public static LLVMFrameValueAccess createDebugFrameValue(FrameSlot slot, boolean isDeclaration) {
        final LLVMDebugValue.Builder builder = getDebugDynamicValueBuilder(isDeclaration).createBuilder();
        return new LLVMFrameValueAccessImpl(slot, builder);
    }

    // these have no internal state but are used often, so we cache and reuse them
    private static LLVMDebugBuilder debugDeclarationBuilder = null;
    private static LLVMDebugBuilder debugValueBuilder = null;

    private static LLVMDebugBuilder getDebugDynamicValueBuilder(boolean isDeclaration) {
        if (isDeclaration) {
            if (debugDeclarationBuilder == null) {
                debugDeclarationBuilder = CommonNodeFactory::createDebugDeclarationBuilder;
            }
            return debugDeclarationBuilder;
        } else {
            if (debugValueBuilder == null) {
                debugValueBuilder = CommonNodeFactory::createDebugValueBuilder;
            }
            return debugValueBuilder;
        }
    }

    public static LLVMDebugObjectBuilder createDebugStaticValue(LLVMExpressionNode valueNode, boolean isGlobal) {
        LLVMDebugValue.Builder toDebugNode = createDebugValueBuilder();

        Object value = null;
        if (isGlobal) {
            assert valueNode instanceof LLVMAccessGlobalVariableStorageNode;
            LLVMAccessGlobalVariableStorageNode node = (LLVMAccessGlobalVariableStorageNode) valueNode;
            value = new LLVMDebugGlobalVariable(node.getDescriptor());
        } else {
            try {
                value = valueNode.executeGeneric(null);
            } catch (Throwable ignored) {
                // constant values should not need frame access
            }
        }

        if (value != null) {
            return LLVMDebugSimpleObjectBuilder.create(toDebugNode, value);
        } else {
            return LLVMDebugObjectBuilder.UNAVAILABLE;
        }
    }

    public static LLVMDebugValue.Builder createDebugDeclarationBuilder() {
        return LLVMToDebugDeclarationNodeGen.create();
    }

    public static LLVMDebugValue.Builder createDebugValueBuilder() {
        return LLVMToDebugValueNodeGen.create();
    }
}
