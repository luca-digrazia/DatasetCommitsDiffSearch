/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.aarch64;

import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerPlatformSpecificUnsafePlugins;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AbsNode;
import org.graalvm.compiler.nodes.calc.IntegerMulHighNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafeAccessPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafeGetPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafePutPlugin;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.FusedMultiplyAddNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class AArch64GraphBuilderPlugins implements TargetGraphBuilderPlugins {
    @Override
    public void register(Plugins plugins, Replacements replacements, Architecture arch, boolean explicitUnsafeNullChecks, boolean registerForeignCallMath,
                    boolean emitJDK9StringSubstitutions, boolean useFMAIntrinsics) {
        register(plugins, replacements, explicitUnsafeNullChecks, registerForeignCallMath, emitJDK9StringSubstitutions, useFMAIntrinsics);
    }

    public static void register(Plugins plugins, Replacements replacements, boolean explicitUnsafeNullChecks,
                    boolean registerForeignCallMath, boolean emitJDK9StringSubstitutions, boolean useFMAIntrinsics) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Int, replacements);
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Long, replacements);
                registerMathPlugins(invocationPlugins, registerForeignCallMath, useFMAIntrinsics);
                if (emitJDK9StringSubstitutions) {
                    registerStringLatin1Plugins(invocationPlugins, replacements);
                    registerStringUTF16Plugins(invocationPlugins, replacements);
                }
                registerUnsafePlugins(invocationPlugins, replacements, explicitUnsafeNullChecks);
                // This is temporarily disabled until we implement correct emitting of the CAS
                // instructions of the proper width.
                registerPlatformSpecificUnsafePlugins(invocationPlugins, replacements, explicitUnsafeNullChecks,
                                new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object});
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind, Replacements replacements) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, replacements);
        r.register1("numberOfLeadingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode folded = AArch64CountLeadingZerosNode.tryFold(value);
                if (folded != null) {
                    b.addPush(JavaKind.Int, folded);
                } else {
                    b.addPush(JavaKind.Int, new AArch64CountLeadingZerosNode(value));
                }
                return true;
            }
        });
        r.register1("numberOfTrailingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode folded = AArch64CountTrailingZerosNode.tryFold(value);
                if (folded != null) {
                    b.addPush(JavaKind.Int, folded);
                } else {
                    b.addPush(JavaKind.Int, new AArch64CountTrailingZerosNode(value));
                }
                return true;
            }
        });
        r.register1("bitCount", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new AArch64BitCountNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, boolean registerForeignCallMath, boolean useFMAIntrinsics) {
        Registration r = new Registration(plugins, Math.class);
        if (registerForeignCallMath) {
            registerUnaryMath(r, "sin", SIN);
            registerUnaryMath(r, "cos", COS);
            registerUnaryMath(r, "tan", TAN);
            registerUnaryMath(r, "exp", EXP);
            registerUnaryMath(r, "log", LOG);
            registerUnaryMath(r, "log10", LOG10);
            r.register2("pow", Double.TYPE, Double.TYPE, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, BinaryMathIntrinsicNode.BinaryOperation.POW)));
                    return true;
                }
            });
        }
        registerRound(r, "rint", RoundingMode.NEAREST);
        registerRound(r, "ceil", RoundingMode.UP);
        registerRound(r, "floor", RoundingMode.DOWN);
        if (useFMAIntrinsics && JavaVersionUtil.JAVA_SPEC > 8) {
            registerFMA(r);
        }
        registerIntegerAbs(r);

        if (JavaVersionUtil.JAVA_SPEC >= 10) {
            r.register2("multiplyHigh", Long.TYPE, Long.TYPE, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(JavaKind.Long, b.append(new IntegerMulHighNode(x, y)));
                    return true;
                }
            });
        }
    }

    private static void registerFMA(Registration r) {
        r.register3("fma", Double.TYPE, Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b,
                            ResolvedJavaMethod targetMethod,
                            Receiver receiver,
                            ValueNode na,
                            ValueNode nb,
                            ValueNode nc) {
                b.push(JavaKind.Double, b.append(new FusedMultiplyAddNode(na, nb, nc)));
                return true;
            }
        });
        r.register3("fma", Float.TYPE, Float.TYPE, Float.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b,
                            ResolvedJavaMethod targetMethod,
                            Receiver receiver,
                            ValueNode na,
                            ValueNode nb,
                            ValueNode nc) {
                b.push(JavaKind.Float, b.append(new FusedMultiplyAddNode(na, nb, nc)));
                return true;
            }
        });
    }

    private static void registerIntegerAbs(Registration r) {
        r.register1("abs", Integer.TYPE, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
        r.register1("abs", Long.TYPE, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Long, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static void registerRound(Registration r, String name, RoundingMode mode) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(new AArch64RoundNode(arg, mode)));
                return true;
            }
        });
    }

    private static final class ArrayCompareToPlugin implements InvocationPlugin {
        private final JavaKind valueKind;
        private final JavaKind otherKind;
        private final boolean swapped;

        private ArrayCompareToPlugin(JavaKind valueKind, JavaKind otherKind, boolean swapped) {
            this.valueKind = valueKind;
            this.otherKind = otherKind;
            this.swapped = swapped;
        }

        private ArrayCompareToPlugin(JavaKind valueKind, JavaKind otherKind) {
            this(valueKind, otherKind, false);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode other) {
            ValueNode valueLength = b.add(new ArrayLengthNode(value));
            ValueNode otherLength = b.add(new ArrayLengthNode(other));
            if (swapped) {
                /*
                 * Swapping array arguments because intrinsic expects order to be byte[]/char[] but
                 * kind arguments stay in original order.
                 */
                b.addPush(JavaKind.Int, new ArrayCompareToNode(other, value, otherLength, valueLength, valueKind, otherKind));
            } else {
                b.addPush(JavaKind.Int, new ArrayCompareToNode(value, other, valueLength, otherLength, valueKind, otherKind));
            }
            return true;
        }
    }

    private static void registerStringLatin1Plugins(InvocationPlugins plugins, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringLatin1", replacements);
            r.setAllowOverwrite(true);
            r.register2("compareTo", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Byte, JavaKind.Byte));
            r.register2("compareToUTF16", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Byte, JavaKind.Char));
        }
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringUTF16", replacements);
            r.setAllowOverwrite(true);
            r.register2("compareTo", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Char));
            r.register2("compareToLatin1", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Byte, true));
        }
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, Replacements replacements, boolean explicitUnsafeNullChecks) {
        registerUnsafePlugins(new Registration(plugins, Unsafe.class), explicitUnsafeNullChecks,
                        new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object}, "Object");
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Registration r = new Registration(plugins, "jdk.internal.misc.Unsafe", replacements);
            registerUnsafePlugins(r, explicitUnsafeNullChecks, new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object},
                            JavaVersionUtil.JAVA_SPEC <= 11 ? "Object" : "Reference");
            registerUnsafeUnalignedPlugins(r, explicitUnsafeNullChecks);
        }
    }

    private static void registerUnsafeUnalignedPlugins(Registration r, boolean explicitUnsafeNullChecks) {
        for (JavaKind kind : new JavaKind[]{JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long}) {
            Class<?> javaClass = kind.toJavaClass();
            r.registerOptional3("get" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, explicitUnsafeNullChecks));
            r.registerOptional4("put" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, explicitUnsafeNullChecks));
        }
    }

    private static void registerUnsafePlugins(Registration r, boolean explicitUnsafeNullChecks, JavaKind[] unsafeJavaKinds, String objectKindName) {

        for (JavaKind kind : unsafeJavaKinds) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
            String kindName = kind == JavaKind.Object ? objectKindName : kind.name();
            r.register4("getAndSet" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafeAccessPlugin(kind, explicitUnsafeNullChecks) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndWriteNode(obj, offset, value, kind, loc));
                    return true;
                }
            });

            if (kind != JavaKind.Boolean && kind.isNumericInteger()) {
                r.register4("getAndAdd" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafeAccessPlugin(kind, explicitUnsafeNullChecks) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode delta) {
                        // Emits a null-check for the otherwise unused receiver
                        unsafe.get();
                        createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndAddNode(b.add(new OffsetAddressNode(obj, offset)), delta, kind, loc));
                        return true;
                    }
                });
            }
        }
    }
}
