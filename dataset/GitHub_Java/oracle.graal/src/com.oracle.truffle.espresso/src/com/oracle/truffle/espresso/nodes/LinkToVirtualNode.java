package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.runtime.Intrinsics.PolySigIntrinsics.LinkToVirtual;

public class LinkToVirtualNode extends MHLinkToNode {
    LinkToVirtualNode(Method method) {
        super(method, LinkToVirtual);
    }

    @Override
    protected final Object linkTo(Object[] args) {
        Method target = getTarget(args);
        StaticObjectImpl receiver = (StaticObjectImpl) args[0];
        if (!(target.getRefKind() == REF_invokeSpecial)) {
            target = (receiver.getKlass()).vtableLooup(target.getVTableIndex());
        }
        Object result = callNode.call(target.getCallTarget(), unbasic(args, target.getParsedSignature(), 0, argCount - 1, true));
        return rebasic(result, Signatures.returnType(target.getParsedSignature()));
    }
}
