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
package com.oracle.graal.compiler.test;

import jdk.internal.jvmci.code.BailoutException;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

import org.junit.Test;

import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.OptimisticOptimizations;

/**
 * Exercise handling of unbalanced monitor operations by the parser. Algorithmically Graal assumes
 * that locks are statically block structured but that isn't something enforced by the bytecodes. In
 * HotSpot a dataflow is performed to ensure they are properly structured and methods with
 * unstructured locking aren't compiled and fall back to the interpreter. Having the Graal parser
 * handle this directly is simplifying for targets of Graal since they don't have to provide a data
 * flow that checks this property.
 */
public class UnbalancedMonitorsTest extends GraalCompilerTest implements Opcodes {
    private static final String NAME = "com.oracle.graal.compiler.test.UnbalancedMonitorsTest$UnbalancedMonitors";
    private static AsmLoader LOADER = new AsmLoader(UnbalancedMonitorsTest.class.getClassLoader());

    @Test
    public void runWrongOrder() throws Exception {
        checkForBailout("wrongOrder");
    }

    @Test
    public void runTooFewExits() throws Exception {
        checkForBailout("tooFewExits");
    }

    @Test
    public void runTooManyExits() throws Exception {
        checkForBailout("tooManyExits");
    }

    @Test
    public void runTooFewExitsExceptional() throws Exception {
        checkForBailout("tooFewExitsExceptional");
    }

    @Test
    public void runTooManyExitsExceptional() throws Exception {
        checkForBailout("tooManyExitsExceptional");
    }

    private void checkForBailout(String name) throws ClassNotFoundException {
        ResolvedJavaMethod method = getResolvedJavaMethod(LOADER.findClass(NAME), name);
        try {
            StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
            GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getEagerDefault(new Plugins(new InvocationPlugins(getMetaAccess())));
            graphBuilderConfig = graphBuilderConfig.withOmitAllExceptionEdges(false);
            OptimisticOptimizations optimisticOpts = OptimisticOptimizations.NONE;

            GraphBuilderPhase.Instance graphBuilder = new GraphBuilderPhase.Instance(getMetaAccess(), getProviders().getStampProvider(), null, graphBuilderConfig, optimisticOpts, null);
            graphBuilder.apply(graph);
        } catch (BailoutException e) {
            if (e.getMessage().contains("unbalanced monitors")) {
                return;
            }
            throw e;
        }
        assertTrue("should have bailed out", false);
    }

    // @formatter:off
    // Template class used with Bytecode Outline to generate ASM code
    //    public static class UnbalancedMonitors {
    //
    //        public UnbalancedMonitors() {
    //        }
    //
    //        public Object wrongOrder(Object a, Object b) {
    //            synchronized (a) {
    //                synchronized (b) {
    //                    return b;
    //                }
    //            }
    //        }
    //
    //        public Object tooFewExits(Object a, Object b) {
    //            synchronized (a) {
    //                synchronized (b) {
    //                    return b;
    //                }
    //            }
    //        }
    //
    //        public boolean tooFewExitsExceptional(Object a, Object b) {
    //            synchronized (a) {
    //                synchronized (b) {
    //                    return b.equals(a);
    //                }
    //            }
    //        }
    //    }
    // @formatter:on

    public static byte[] generateClass() {

        ClassWriter cw = new ClassWriter(0);

        cw.visit(52, ACC_SUPER | ACC_PUBLIC, "com/oracle/graal/compiler/test/UnbalancedMonitorsTest$UnbalancedMonitors", null, "java/lang/Object", null);

        cw.visitSource("UnbalancedMonitorsTest.java", null);

        cw.visitInnerClass("com/oracle/graal/compiler/test/UnbalancedMonitorsTest$UnbalancedMonitors", "com/oracle/graal/compiler/test/UnbalancedMonitorsTest", "UnbalancedMonitors", ACC_STATIC);

        visitConstructor(cw);
        visitWrongOrder(cw);
        visitBlockStructured(cw, true, false);
        visitBlockStructured(cw, true, true);
        visitBlockStructured(cw, false, false);
        visitBlockStructured(cw, false, true);
        cw.visitEnd();

        return cw.toByteArray();
    }

    private static void visitBlockStructured(ClassWriter cw, boolean normalReturnError, boolean tooMany) {
        String name = (tooMany ? "tooMany" : "tooFew") + "Exits" + (normalReturnError ? "" : "Exceptional");
        // Generate too many or too few exits down the either the normal or exceptional return paths
        int exceptionalExitCount = normalReturnError ? 1 : (tooMany ? 2 : 0);
        int normalExitCount = normalReturnError ? (tooMany ? 2 : 0) : 1;
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, name, "(Ljava/lang/Object;Ljava/lang/Object;)Z", null, null);
        mv.visitCode();
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, null);
        Label l3 = new Label();
        mv.visitTryCatchBlock(l2, l3, l2, null);
        Label l4 = new Label();
        Label l5 = new Label();
        Label l6 = new Label();
        mv.visitTryCatchBlock(l4, l5, l6, null);
        Label l7 = new Label();
        mv.visitTryCatchBlock(l2, l7, l6, null);
        Label l8 = new Label();
        mv.visitLabel(l8);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 3);
        mv.visitInsn(MONITORENTER);
        mv.visitLabel(l4);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 4);
        mv.visitInsn(MONITORENTER);
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(l1);
        for (int i = 0; i < normalExitCount; i++) {
            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(MONITOREXIT);
        }
        mv.visitLabel(l5);
        mv.visitInsn(IRETURN);
        mv.visitLabel(l2);
        mv.visitFrame(Opcodes.F_FULL, 5, new Object[]{"com/oracle/graal/compiler/test/UnbalancedMonitorsTest$UnbalancedMonitors", "java/lang/Object", "java/lang/Object", "java/lang/Object",
                        "java/lang/Object"}, 1, new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(l3);
        mv.visitInsn(ATHROW);
        mv.visitLabel(l6);
        mv.visitFrame(Opcodes.F_FULL, 4, new Object[]{"com/oracle/graal/compiler/test/UnbalancedMonitorsTest$UnbalancedMonitors", "java/lang/Object", "java/lang/Object", "java/lang/Object"}, 1,
                        new Object[]{"java/lang/Throwable"});
        for (int i = 0; i < exceptionalExitCount; i++) {
            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(MONITOREXIT);
        }
        mv.visitLabel(l7);
        mv.visitInsn(ATHROW);
        Label l9 = new Label();
        mv.visitLabel(l9);
        mv.visitMaxs(2, 5);
        mv.visitEnd();
    }

    private static void visitWrongOrder(ClassWriter cw) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, "wrongOrder", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, null);
        Label l3 = new Label();
        mv.visitTryCatchBlock(l2, l3, l2, null);
        Label l4 = new Label();
        Label l5 = new Label();
        Label l6 = new Label();
        mv.visitTryCatchBlock(l4, l5, l6, null);
        Label l7 = new Label();
        mv.visitTryCatchBlock(l2, l7, l6, null);
        Label l8 = new Label();
        mv.visitLabel(l8);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 3);
        mv.visitInsn(MONITORENTER);
        mv.visitLabel(l4);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 4);
        mv.visitInsn(MONITORENTER);
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(l1);
        // Swapped exit order with exit above
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(l5);
        mv.visitInsn(ARETURN);
        mv.visitLabel(l2);
        mv.visitFrame(Opcodes.F_FULL, 5, new Object[]{"com/oracle/graal/compiler/test/UnbalancedMonitorsTest$UnbalancedMonitors", "java/lang/Object", "java/lang/Object", "java/lang/Object",
                        "java/lang/Object"}, 1, new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(l3);
        mv.visitInsn(ATHROW);
        mv.visitLabel(l6);
        mv.visitFrame(Opcodes.F_FULL, 4, new Object[]{"com/oracle/graal/compiler/test/UnbalancedMonitorsTest$UnbalancedMonitors", "java/lang/Object", "java/lang/Object", "java/lang/Object"}, 1,
                        new Object[]{"java/lang/Throwable"});
        mv.visitVarInsn(ALOAD, 3);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(l7);
        mv.visitInsn(ATHROW);
        Label l9 = new Label();
        mv.visitLabel(l9);
        mv.visitMaxs(2, 5);
        mv.visitEnd();
    }

    private static void visitConstructor(ClassWriter cw) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitInsn(RETURN);
        Label l2 = new Label();
        mv.visitLabel(l2);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    public static class AsmLoader extends ClassLoader {
        Class<?> loaded;

        public AsmLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(NAME)) {
                if (loaded != null) {
                    return loaded;
                }
                byte[] bytes = generateClass();
                return (loaded = defineClass(name, bytes, 0, bytes.length));
            } else {
                return super.findClass(name);
            }
        }
    }
}
