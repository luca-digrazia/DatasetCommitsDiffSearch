/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.verifier;

import java.util.HashMap;
import java.util.Iterator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.replacements.verifier.InjectedDependencies.Dependency;

public class InjectedDependencies implements Iterable<Dependency> {

    public abstract static class Dependency {

        public final String name;
        public final String type;

        private Dependency(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public abstract String inject(ExecutableElement inject);
    }

    private static final class InjectedDependency extends Dependency {

        private InjectedDependency(String name, String type) {
            super(name, type);
        }

        @Override
        public String inject(ExecutableElement inject) {
            return String.format("injection.getInjectedArgument(%s.class)", type);
        }
    }

    private static final class StampDependency extends Dependency {

        private StampDependency() {
            super("returnStamp", "com.oracle.graal.compiler.common.type.Stamp");
        }

        @Override
        public String inject(ExecutableElement inject) {
            NodeIntrinsic nodeIntrinsic = inject.getAnnotation(NodeIntrinsic.class);
            return String.format("injection.getReturnStamp(%s.class, %s)", GeneratedPlugin.getErasedType(inject.getReturnType()), nodeIntrinsic != null && nodeIntrinsic.returnStampIsNonNull());
        }
    }

    public enum WellKnownDependency {
        CONSTANT_REFLECTION("b.getConstantReflection()", "jdk.vm.ci.meta.ConstantReflectionProvider"),
        META_ACCESS("b.getMetaAccess()", "jdk.vm.ci.meta.MetaAccessProvider"),
        RETURN_STAMP(new StampDependency()),
        SNIPPET_REFLECTION(new InjectedDependency("snippetReflection", "com.oracle.graal.api.replacements.SnippetReflectionProvider")),
        STAMP_PROVIDER("b.getStampProvider()", "com.oracle.graal.nodes.spi.StampProvider"),
        STRUCTURED_GRAPH("b.getGraph()", "com.oracle.graal.nodes.StructuredGraph");

        private final String expr;
        private final String type;
        private final Dependency generateMember;

        private WellKnownDependency(String expr, String type) {
            this.expr = expr;
            this.type = type;
            this.generateMember = null;
        }

        private WellKnownDependency(Dependency generateMember) {
            this.expr = generateMember.name;
            this.type = generateMember.type;
            this.generateMember = generateMember;
        }

        private TypeMirror getType(ProcessingEnvironment env) {
            return env.getElementUtils().getTypeElement(type).asType();
        }
    }

    private final HashMap<String, Dependency> deps;

    public InjectedDependencies() {
        deps = new HashMap<>();
    }

    public String use(WellKnownDependency wellKnown) {
        if (wellKnown.generateMember != null) {
            deps.put(wellKnown.type, wellKnown.generateMember);
        }
        return wellKnown.expr;
    }

    public String use(ProcessingEnvironment env, DeclaredType type) {
        for (WellKnownDependency wellKnown : WellKnownDependency.values()) {
            if (env.getTypeUtils().isAssignable(wellKnown.getType(env), type)) {
                return use(wellKnown);
            }
        }

        String typeName = type.toString();
        Dependency ret = deps.get(typeName);
        if (ret == null) {
            ret = new InjectedDependency("injected" + type.asElement().getSimpleName(), typeName);
            deps.put(typeName, ret);
        }
        return ret.name;
    }

    public Iterator<Dependency> iterator() {
        return deps.values().iterator();
    }

    public boolean isEmpty() {
        return deps.isEmpty();
    }
}
