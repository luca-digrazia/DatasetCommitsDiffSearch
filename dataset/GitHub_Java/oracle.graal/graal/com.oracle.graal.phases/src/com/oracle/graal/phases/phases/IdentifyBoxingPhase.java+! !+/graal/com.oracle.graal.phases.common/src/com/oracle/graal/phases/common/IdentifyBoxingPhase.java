/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.phases;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;

public class IdentifyBoxingPhase extends Phase {

    private final BoxingMethodPool pool;

    public IdentifyBoxingPhase(BoxingMethodPool pool) {
        this.pool = pool;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            tryIntrinsify(invoke);
        }
    }

    public void tryIntrinsify(Invoke invoke) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return;
        }
        MethodCallTargetNode callTarget = invoke.methodCallTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        if (pool.isSpecialMethod(targetMethod)) {
            assert callTarget.arguments().size() == 1 : "boxing/unboxing method must have exactly one argument";
            Kind returnKind = callTarget.returnKind();
            ValueNode sourceValue = callTarget.arguments().get(0);

            // Check whether this is a boxing or an unboxing.
            Node newNode = null;
            if (returnKind == Kind.Object) {
                // We have a boxing method here.
                assert Modifier.isStatic(targetMethod.accessFlags()) : "boxing method must be static";
                Kind sourceKind = targetMethod.signature().argumentKindAt(0);
                newNode = invoke.graph().add(new BoxNode(sourceValue, targetMethod.holder(), sourceKind, invoke.bci()));
            } else {
                // We have an unboxing method here.
                assert !Modifier.isStatic(targetMethod.accessFlags()) : "unboxing method must be an instance method";
                newNode = invoke.graph().add(new UnboxNode(returnKind, sourceValue));
            }

            // Intrinsify the invoke to the special node.
            invoke.intrinsify(newNode);
        }
    }
}
