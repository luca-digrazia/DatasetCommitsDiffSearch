/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test.classfile;

import static com.oracle.graal.bytecode.Bytecodes.ALOAD;
import static com.oracle.graal.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE;
import static com.oracle.graal.bytecode.Bytecodes.BIPUSH;
import static com.oracle.graal.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE;
import static com.oracle.graal.bytecode.Bytecodes.GETFIELD;
import static com.oracle.graal.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.graal.bytecode.Bytecodes.GOTO;
import static com.oracle.graal.bytecode.Bytecodes.GOTO_W;
import static com.oracle.graal.bytecode.Bytecodes.IFEQ;
import static com.oracle.graal.bytecode.Bytecodes.IFGE;
import static com.oracle.graal.bytecode.Bytecodes.IFGT;
import static com.oracle.graal.bytecode.Bytecodes.IFLE;
import static com.oracle.graal.bytecode.Bytecodes.IFLT;
import static com.oracle.graal.bytecode.Bytecodes.IFNE;
import static com.oracle.graal.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.graal.bytecode.Bytecodes.IFNULL;
import static com.oracle.graal.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.graal.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD;
import static com.oracle.graal.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.graal.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.graal.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE;
import static com.oracle.graal.bytecode.Bytecodes.JSR;
import static com.oracle.graal.bytecode.Bytecodes.JSR_W;
import static com.oracle.graal.bytecode.Bytecodes.LDC;
import static com.oracle.graal.bytecode.Bytecodes.LDC2_W;
import static com.oracle.graal.bytecode.Bytecodes.LDC_W;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD;
import static com.oracle.graal.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE;
import static com.oracle.graal.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.NEW;
import static com.oracle.graal.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.graal.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.graal.bytecode.Bytecodes.RET;
import static com.oracle.graal.bytecode.Bytecodes.SIPUSH;
import static com.oracle.graal.bytecode.Bytecodes.TABLESWITCH;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.api.test.Graal;
import com.oracle.graal.bytecode.Bytecode;
import com.oracle.graal.bytecode.BytecodeDisassembler;
import com.oracle.graal.bytecode.BytecodeLookupSwitch;
import com.oracle.graal.bytecode.BytecodeStream;
import com.oracle.graal.bytecode.BytecodeSwitch;
import com.oracle.graal.bytecode.BytecodeTableSwitch;
import com.oracle.graal.bytecode.Bytecodes;
import com.oracle.graal.bytecode.ResolvedJavaMethodBytecode;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.phases.VerifyPhase;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.classfile.ClassfileBytecode;
import com.oracle.graal.replacements.classfile.ClassfileBytecodeProvider;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethodProfile.ProfiledMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests that bytecode exposed via {@link ClassfileBytecode} objects is the same as the bytecode
 * (modulo minor differences in constant pool resolution) obtained directly from
 * {@link ResolvedJavaMethod} objects.
 */
public class ClassfileBytecodeProviderTest extends GraalCompilerTest {

    private static boolean shouldProcess(String classpathEntry) {
        if (classpathEntry.endsWith(".jar")) {
            String name = new File(classpathEntry).getName();
            return name.contains("jvmci") || name.contains("graal");
        }
        return false;
    }

    @Test
    public void testJarLoading() {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        String propertyName = Java8OrEarlier ? "sun.boot.class.path" : "jdk.module.path";
        String bootclasspath = System.getProperty(propertyName);
        Assert.assertNotNull("Cannot find value of " + propertyName, bootclasspath);

        for (String path : bootclasspath.split(File.pathSeparator)) {
            if (shouldProcess(path)) {
                try {
                    final ZipFile zipFile = new ZipFile(new File(path));
                    for (final Enumeration<? extends ZipEntry> entry = zipFile.entries(); entry.hasMoreElements();) {
                        final ZipEntry zipEntry = entry.nextElement();
                        String name = zipEntry.getName();
                        if (name.endsWith(".class") && !name.equals("module-info")) {
                            String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                            try {
                                checkClass(metaAccess, getSnippetReflection(), className);
                            } catch (ClassNotFoundException e) {
                                throw new AssertionError(e);
                            }
                        }
                    }
                } catch (IOException ex) {
                    Assert.fail(ex.toString());
                }
            }
        }
    }

    protected void checkClass(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, String className) throws ClassNotFoundException {
        Class<?> c = Class.forName(className, true, getClass().getClassLoader());
        ClassfileBytecodeProvider cbp = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
        for (Method method : c.getDeclaredMethods()) {
            checkMethod(cbp, metaAccess, method);
        }
    }

    private static void checkMethod(ClassfileBytecodeProvider cbp, MetaAccessProvider metaAccess, Executable executable) {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(executable);
        if (method.hasBytecodes()) {
            ResolvedJavaMethodBytecode expected = new ResolvedJavaMethodBytecode(method);
            Bytecode actual = getBytecode(cbp, method);
            new BytecodeComparer(expected, actual).compare();
        }
    }

    protected static Bytecode getBytecode(ClassfileBytecodeProvider cbp, ResolvedJavaMethod method) {
        try {
            return cbp.getBytecode(method);
        } catch (Throwable e) {
            throw new AssertionError(String.format("Error getting bytecode for %s", method.format("%H.%n(%p)")), e);
        }
    }

    static class BytecodeComparer {

        private Bytecode expected;
        private Bytecode actual;
        private ConstantPool eCp;
        private ConstantPool aCp;
        BytecodeStream eStream;
        BytecodeStream aStream;
        int bci = -1;

        BytecodeComparer(Bytecode expected, Bytecode actual) {
            this.expected = expected;
            this.actual = actual;
            this.eCp = expected.getConstantPool();
            this.aCp = actual.getConstantPool();
            Assert.assertEquals(expected.getMethod().toString(), expected.getCodeSize(), actual.getCodeSize());
            this.eStream = new BytecodeStream(expected.getCode());
            this.aStream = new BytecodeStream(actual.getCode());
        }

        public void compare() {
            try {
                compare0();
            } catch (Throwable e) {
                BytecodeDisassembler dis = new BytecodeDisassembler(true, false);
                Formatter msg = new Formatter();
                msg.format("Error comparing bytecode for %s", expected.getMethod().format("%H.%n(%p)"));
                if (bci >= 0) {
                    msg.format("%nexpected: %s", dis.disassemble(expected, bci, eStream.nextBCI() - 1));
                    msg.format("%nactual:   %s", dis.disassemble(actual, bci, aStream.nextBCI() - 1));
                }
                throw new AssertionError(msg.toString(), e);
            }
        }

        public void compare0() {
            int opcode = eStream.currentBC();
            ResolvedJavaMethod method = expected.getMethod();
            while (opcode != Bytecodes.END) {
                bci = eStream.currentBCI();
                int actualOpcode = aStream.currentBC();
                if (opcode != actualOpcode) {
                    Assert.assertEquals(opcode, actualOpcode);
                }
                if (eStream.nextBCI() > bci + 1) {
                    switch (opcode) {
                        case BIPUSH:
                            Assert.assertEquals(eStream.readByte(), aStream.readByte());
                            break;
                        case SIPUSH:
                            Assert.assertEquals(eStream.readShort(), aStream.readShort());
                            break;
                        case NEW:
                        case CHECKCAST:
                        case INSTANCEOF:
                        case ANEWARRAY: {
                            ResolvedJavaType e = lookupType(eCp, eStream.readCPI(), opcode);
                            ResolvedJavaType a = lookupType(aCp, aStream.readCPI(), opcode);
                            assertEqualTypes(e, a);
                            break;
                        }
                        case GETSTATIC:
                        case PUTSTATIC:
                        case GETFIELD:
                        case PUTFIELD: {
                            ResolvedJavaField e = lookupField(eCp, eStream.readCPI(), method, opcode);
                            ResolvedJavaField a = lookupField(aCp, aStream.readCPI(), method, opcode);
                            assertEqualFields(e, a);
                            break;
                        }
                        case INVOKEVIRTUAL:
                        case INVOKESPECIAL:
                        case INVOKESTATIC: {
                            ResolvedJavaMethod e = lookupMethod(eCp, eStream.readCPI(), opcode);
                            ResolvedJavaMethod a = lookupMethodOrNull(aCp, aStream.readCPI(), opcode);
                            assertEqualMethods(e, a);
                            break;
                        }
                        case INVOKEINTERFACE: {
                            ResolvedJavaMethod e = lookupMethod(eCp, eStream.readCPI(), opcode);
                            ResolvedJavaMethod a = lookupMethod(aCp, aStream.readCPI(), opcode);
                            assertEqualMethods(e, a);
                            break;
                        }
                        case INVOKEDYNAMIC: {
                            // INVOKEDYNAMIC is not supported by ClassfileBytecodeProvider
                            return;
                        }
                        case LDC:
                        case LDC_W:
                        case LDC2_W: {
                            Object e = lookupConstant(eCp, eStream.readCPI(), opcode);
                            Object a = lookupConstant(aCp, aStream.readCPI(), opcode);
                            assertEqualsConstants(e, a);
                            break;
                        }
                        case RET:
                        case ILOAD:
                        case LLOAD:
                        case FLOAD:
                        case DLOAD:
                        case ALOAD:
                        case ISTORE:
                        case LSTORE:
                        case FSTORE:
                        case DSTORE:
                        case ASTORE: {
                            Assert.assertEquals(eStream.readLocalIndex(), aStream.readLocalIndex());
                            break;
                        }
                        case IFEQ:
                        case IFNE:
                        case IFLT:
                        case IFGE:
                        case IFGT:
                        case IFLE:
                        case IF_ICMPEQ:
                        case IF_ICMPNE:
                        case IF_ICMPLT:
                        case IF_ICMPGE:
                        case IF_ICMPGT:
                        case IF_ICMPLE:
                        case IF_ACMPEQ:
                        case IF_ACMPNE:
                        case GOTO:
                        case JSR:
                        case IFNULL:
                        case IFNONNULL:
                        case GOTO_W:
                        case JSR_W: {
                            Assert.assertEquals(eStream.readBranchDest(), aStream.readBranchDest());
                            break;
                        }
                        case LOOKUPSWITCH:
                        case TABLESWITCH: {
                            BytecodeSwitch e = opcode == LOOKUPSWITCH ? new BytecodeLookupSwitch(eStream, bci) : new BytecodeTableSwitch(eStream, bci);
                            BytecodeSwitch a = opcode == LOOKUPSWITCH ? new BytecodeLookupSwitch(aStream, bci) : new BytecodeTableSwitch(aStream, bci);
                            Assert.assertEquals(e.numberOfCases(), a.numberOfCases());
                            for (int i = 0; i < e.numberOfCases(); i++) {
                                Assert.assertEquals(e.keyAt(i), a.keyAt(i));
                                Assert.assertEquals(e.targetAt(i), a.targetAt(i));
                            }
                            Assert.assertEquals(e.defaultTarget(), a.defaultTarget());
                            Assert.assertEquals(e.defaultOffset(), a.defaultOffset());
                            break;
                        }
                        case NEWARRAY: {
                            Assert.assertEquals(eStream.readLocalIndex(), aStream.readLocalIndex());
                            break;
                        }
                        case MULTIANEWARRAY: {
                            ResolvedJavaType e = lookupType(eCp, eStream.readCPI(), opcode);
                            ResolvedJavaType a = lookupType(aCp, aStream.readCPI(), opcode);
                            Assert.assertEquals(e, a);
                            break;
                        }
                    }
                }
                eStream.next();
                aStream.next();
                opcode = eStream.currentBC();
            }
        }

        static Object lookupConstant(ConstantPool cp, int cpi, int opcode) {
            cp.loadReferencedType(cpi, opcode);
            return cp.lookupConstant(cpi);
        }

        static ResolvedJavaField lookupField(ConstantPool cp, int cpi, ResolvedJavaMethod method, int opcode) {
            cp.loadReferencedType(cpi, opcode);
            return (ResolvedJavaField) cp.lookupField(cpi, method, opcode);
        }

        static ResolvedJavaMethod lookupMethod(ConstantPool cp, int cpi, int opcode) {
            cp.loadReferencedType(cpi, opcode);
            return (ResolvedJavaMethod) cp.lookupMethod(cpi, opcode);
        }

        static ResolvedJavaMethod lookupMethodOrNull(ConstantPool cp, int cpi, int opcode) {
            try {
                return lookupMethod(cp, cpi, opcode);
            } catch (NoSuchMethodError e) {
                // A method hidden to reflection
                return null;
            }
        }

        static ResolvedJavaType lookupType(ConstantPool cp, int cpi, int opcode) {
            cp.loadReferencedType(cpi, opcode);
            return (ResolvedJavaType) cp.lookupType(cpi, opcode);
        }

        static void assertEqualsConstants(Object e, Object a) {
            if (!e.equals(a)) {
                Assert.assertEquals(String.valueOf(e), String.valueOf(a));
            }
        }

        static void assertEqualFields(JavaField e, JavaField a) {
            if (!e.equals(a)) {
                Assert.assertEquals(e.format("%H.%n %T"), a.format("%H.%n %T"));
            }
        }

        static void assertEqualTypes(JavaType e, JavaType a) {
            if (!e.equals(a)) {
                Assert.assertEquals(e.toJavaName(), a.toJavaName());
            }
        }

        static void assertEqualMethods(ResolvedJavaMethod e, ResolvedJavaMethod a) {
            if (a != null) {
                if (!e.equals(a)) {
                    if (!e.equals(a)) {
                        if (!e.getDeclaringClass().equals(a.getDeclaringClass())) {

                            if (!typesAreRelated(e, a)) {
                                throw new AssertionError(String.format("%s and %s are unrelated", a.getDeclaringClass().toJavaName(), e.getDeclaringClass().toJavaName()));
                            }
                        }
                        Assert.assertEquals(e.getName(), a.getName());
                        Assert.assertEquals(e.getSignature(), a.getSignature());
                    } else {
                        Assert.assertEquals(e, a);
                    }
                }
            }
        }

        /**
         * The VM can resolve references to methods not available via reflection. For example, the
         * javap output for {@link ProfiledMethod#toString()} includes:
         *
         * <pre>
         *     16: invokeinterface #40, 1 // InterfaceMethod jdk/vm/ci/meta/ResolvedJavaMethod.getName:()Ljava/lang/String;
         * </pre>
         *
         * When resolving via {@code HotSpotConstantPool}, we get:
         *
         * <pre>
         *     16: invokeinterface#4, 1   // jdk.vm.ci.meta.ResolvedJavaMethod.getName:()java.lang.String
         * </pre>
         *
         * However resolving via {@code ClassfileConstantPool}, we get:
         *
         * <pre>
         *     16: invokeinterface#40, 1  // jdk.vm.ci.meta.JavaMethod.getName:()java.lang.String
         * </pre>
         *
         * since the latter relies on {@link ResolvedJavaType#getDeclaredMethods()} which only
         * returns methods originating from class files.
         *
         * We accept such differences for the purpose of this test if the declaring class of two
         * otherwise similar methods are related (i.e. one is a subclass of the other).
         */
        protected static boolean typesAreRelated(ResolvedJavaMethod e, ResolvedJavaMethod a) {
            return a.getDeclaringClass().isAssignableFrom(e.getDeclaringClass()) || e.getDeclaringClass().isAssignableFrom(a.getDeclaringClass());
        }
    }
}
