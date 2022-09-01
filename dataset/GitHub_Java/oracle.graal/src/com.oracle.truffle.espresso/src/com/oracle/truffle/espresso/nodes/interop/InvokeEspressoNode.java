package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;

@GenerateUncached
public abstract class InvokeEspressoNode extends Node {
    static final int LIMIT = 4;

    public abstract Object execute(Method method, Object receiver, Object[] arguments) throws ArityException, UnsupportedMessageException, UnsupportedTypeException;

    static ToEspressoNode[] createToEspresso(long argsLength, boolean uncached) {
        ToEspressoNode[] toEspresso = new ToEspressoNode[(int) argsLength];
        for (int i = 0; i < argsLength; i++) {
            toEspresso[i] = uncached
                            ? ToEspressoNodeGen.getUncached()
                            : ToEspressoNodeGen.create();
        }
        return toEspresso;
    }

    static DirectCallNode createDirectCallNode(CallTarget callTarget) {
        return DirectCallNode.create(callTarget);
    }

    @ExplodeLoop
    @Specialization(guards = "method == cachedMethod", limit = "LIMIT")
    Object doCached(Method method, Object receiver, Object[] arguments,
                    @Cached("method") Method cachedMethod,
                    @Cached(value = "createToEspresso(method.getParameterCount(), false)", uncached = "createToEspresso(method.getParameterCount(), true)") ToEspressoNode[] toEspressoNodes,
                    @Cached(value = "createDirectCallNode(method.getCallTarget())", allowUncached = true) DirectCallNode directCallNode)
                    throws ArityException, UnsupportedMessageException, UnsupportedTypeException {

        EspressoError.guarantee(method.isStatic() && receiver == null, "Espresso interop only supports static methods");

        int expectedArity = cachedMethod.getParameterCount();
        if (arguments.length != expectedArity) {
            throw ArityException.create(expectedArity, arguments.length);
        }

        Klass[] parameterKlasses = method.resolveParameterKlasses();

        int parameterCount = arguments.length;
        Object[] convertedArguments = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            convertedArguments[i] = toEspressoNodes[i].execute(arguments[i], parameterKlasses[i]);
        }

        return directCallNode.call(/* static => no receiver */ convertedArguments);
    }

    @ExplodeLoop
    @Specialization
    Object doGeneric(Method method, Object receiver, Object[] arguments,
                     @Cached ToEspressoNode toEspressoNode,
                    @Cached IndirectCallNode indirectCallNode)
                    throws ArityException, UnsupportedMessageException, UnsupportedTypeException {

        EspressoError.guarantee(method.isStatic() && receiver == null, "Espresso interop only supports static methods");

        int expectedArity = method.getParameterCount();
        if (arguments.length != expectedArity) {
            throw ArityException.create(expectedArity, arguments.length);
        }

        Klass[] parameterKlasses = method.resolveParameterKlasses();

        int parameterCount = arguments.length;
        Object[] convertedArguments = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            convertedArguments[i] = toEspressoNode.execute(arguments[i], parameterKlasses[i]);
        }

        return indirectCallNode.call(method.getCallTarget(), /* static => no receiver */ convertedArguments);
    }
}
