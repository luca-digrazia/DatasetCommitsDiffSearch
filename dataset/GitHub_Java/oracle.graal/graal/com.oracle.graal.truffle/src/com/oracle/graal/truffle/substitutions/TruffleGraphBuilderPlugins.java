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
package com.oracle.graal.truffle.substitutions;

import static java.lang.Character.toUpperCase;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LocationIdentity;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.ConditionAnchorNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.PiArrayNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.extended.BoxNode;
import com.oracle.graal.nodes.extended.BranchProbabilityNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.nodes.virtual.EnsureVirtualizedNode;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.StableOptionValue;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerAddExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerMulExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerMulHighNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerSubExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.UnsignedMulHighNode;
import com.oracle.graal.truffle.FrameWithBoxing;
import com.oracle.graal.truffle.FrameWithoutBoxing;
import com.oracle.graal.truffle.OptimizedAssumption;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCompilerOptions;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;
import com.oracle.graal.truffle.nodes.IsCompilationConstantNode;
import com.oracle.graal.truffle.nodes.ObjectLocationIdentity;
import com.oracle.graal.truffle.nodes.asserts.NeverPartOfCompilationNode;
import com.oracle.graal.truffle.nodes.frame.ForceMaterializeNode;
import com.oracle.graal.truffle.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.truffle.nodes.frame.NewFrameNode;
import com.oracle.graal.truffle.nodes.frame.VirtualFrameGetNode;
import com.oracle.graal.truffle.nodes.frame.VirtualFrameIsNode;
import com.oracle.graal.truffle.nodes.frame.VirtualFrameSetNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

/**
 * Provides {@link InvocationPlugin}s for Truffle classes.
 */
public class TruffleGraphBuilderPlugins {

    public static class Options {
        @Option(help = "Intrinsify get/set/is methods of FrameWithoutBoxing to improve Truffle compilation time", type = OptionType.Debug)//
        public static final OptionValue<Boolean> TruffleIntrinsifyFrameAccess = new StableOptionValue<>(true);
    }

    public static void registerInvocationPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, SnippetReflectionProvider snippetReflection) {

        registerOptimizedAssumptionPlugins(plugins, snippetReflection);
        registerExactMathPlugins(plugins);
        registerCompilerDirectivesPlugins(plugins, canDelayIntrinsification);
        registerCompilerAssertsPlugins(plugins, canDelayIntrinsification);
        registerOptimizedCallTargetPlugins(plugins, snippetReflection, canDelayIntrinsification);

        if (TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue()) {
            registerFrameWithoutBoxingPlugins(plugins, canDelayIntrinsification, snippetReflection);
        } else {
            registerFrameWithBoxingPlugins(plugins, canDelayIntrinsification);
        }

    }

    public static void registerOptimizedAssumptionPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, OptimizedAssumption.class);
        InvocationPlugin plugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant() && b.getAssumptions() != null) {
                    Constant constant = receiver.get().asConstant();
                    OptimizedAssumption assumption = snippetReflection.asObject(OptimizedAssumption.class, (JavaConstant) constant);
                    if (assumption.isValid()) {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                        }
                        b.getAssumptions().record(new AssumptionValidAssumption(assumption));
                    } else {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                            b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.None));
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        };
        r.register1("isValid", Receiver.class, plugin);
        r.register1("check", Receiver.class, plugin);
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, ExactMath.class);
        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
            Class<?> type = kind.toJavaClass();
            r.register2("addExact", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerAddExactNode(x, y));
                    return true;
                }
            });
            r.register2("subtractExact", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerSubExactNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyExact", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerMulExactNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHigh", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerMulHighNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHighUnsigned", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new UnsignedMulHighNode(x, y));
                    return true;
                }
            });
        }
    }

    public static void registerCompilerDirectivesPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, CompilerDirectives.class);
        r.register0("inInterpreter", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                return true;
            }
        });
        r.register0("inCompiledCode", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });
        r.register0("transferToInterpreter", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register0("transferToInterpreterAndInvalidate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register1("interpreterOnly", Runnable.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register1("interpreterOnly", Callable.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });
        r.register1("bailout", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                if (canDelayIntrinsification) {
                    /*
                     * We do not want to bailout yet, since we are still parsing individual methods
                     * and constant folding could still eliminate the call to bailout(). However, we
                     * also want to stop parsing, since we are sure that we will never need the
                     * graph beyond the bailout point.
                     *
                     * Therefore, we manually emit the call to bailout, which will be intrinsified
                     * later when intrinsifications can no longer be delayed. The call is followed
                     * by a NeverPartOfCompilationNode, which is a control sink and therefore stops
                     * any further parsing.
                     */
                    StampPair returnStamp = b.getInvokeReturnStamp(b.getAssumptions());
                    CallTargetNode callTarget = b.add(new MethodCallTargetNode(InvokeKind.Static, targetMethod, new ValueNode[]{message}, returnStamp, null));
                    b.add(new InvokeNode(callTarget, b.bci()));

                    b.add(new NeverPartOfCompilationNode("intrinsification of call to bailout() will abort entire compilation"));
                    return true;
                }

                if (message.isConstant()) {
                    throw b.bailout(message.asConstant().toValueString());
                }
                throw b.bailout("bailout (message is not compile-time constant, so no additional information is available)");
            }
        });
        r.register1("isCompilationConstant", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                } else {
                    b.addPush(JavaKind.Boolean, new IsCompilationConstantNode(value));
                }
                return true;
            }
        });
        r.register1("isPartialEvaluationConstant", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                }
                return true;
            }
        });
        r.register1("materialize", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new ForceMaterializeNode(value));
                return true;
            }
        });
        r.register1("ensureVirtualized", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, false));
                return true;
            }
        });
        r.register1("ensureVirtualizedHere", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, true));
                return true;
            }
        });
    }

    public static void registerCompilerAssertsPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, CompilerAsserts.class);
        r.register1("partialEvaluationConstant", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode curValue = value;
                if (curValue instanceof BoxNode) {
                    BoxNode boxNode = (BoxNode) curValue;
                    curValue = boxNode.getValue();
                }
                if (curValue.isConstant()) {
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(curValue);
                    if (curValue instanceof ValuePhiNode) {
                        ValuePhiNode valuePhi = (ValuePhiNode) curValue;
                        sb.append(" (");
                        for (Node n : valuePhi.inputs()) {
                            sb.append(n);
                            sb.append("; ");
                        }
                        sb.append(")");
                    }
                    Debug.dump(Debug.BASIC_LOG_LEVEL, value.graph(), "Graph before bailout at node %s", sb);
                    throw b.bailout("Partial evaluation did not reduce value to a constant, is a regular compiler node: " + sb);
                }
            }
        });
        r.register0("neverPartOfCompilation", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NeverPartOfCompilationNode("CompilerAsserts.neverPartOfCompilation()"));
                return true;
            }
        });
        r.register1("neverPartOfCompilation", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                if (message.isConstant()) {
                    String messageString = message.asConstant().toValueString();
                    b.add(new NeverPartOfCompilationNode(messageString));
                    return true;
                } else {
                    throw b.bailout("message for never part of compilation is non-constant");
                }
            }
        });
    }

    public static void registerOptimizedCallTargetPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, OptimizedCallTarget.class);
        r.register2("createFrame", FrameDescriptor.class, Object[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode descriptor, ValueNode args) {
                if (canDelayIntrinsification) {
                    return false;
                }
                if (!descriptor.isConstant()) {
                    throw b.bailout("Parameter 'descriptor' is not a compile-time constant");
                }
                FrameDescriptor constantDescriptor = snippetReflection.asObject(FrameDescriptor.class, descriptor.asJavaConstant());

                ValueNode nonNullArguments = b.add(new PiNode(args, StampFactory.objectNonNull(StampTool.typeReferenceOrNull(args))));
                Class<?> frameClass = TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue() ? FrameWithoutBoxing.class : FrameWithBoxing.class;
                NewFrameNode newFrame = new NewFrameNode(b.getMetaAccess(), snippetReflection, b.getGraph(), b.getMetaAccess().lookupJavaType(frameClass), constantDescriptor, descriptor,
                                nonNullArguments);
                b.addPush(JavaKind.Object, newFrame);
                return true;
            }
        });
        r.register2("castArrayFixedLength", Object[].class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode args, ValueNode length) {
                b.addPush(JavaKind.Object, new PiArrayNode(args, length, args.stamp()));
                return true;
            }
        });
        registerUnsafeCast(r, canDelayIntrinsification);
    }

    private static final EnumMap<JavaKind, Integer> accessorKindToTag;

    static {
        accessorKindToTag = new EnumMap<>(JavaKind.class);
        accessorKindToTag.put(JavaKind.Object, (int) FrameWithoutBoxing.OBJECT_TAG);
        accessorKindToTag.put(JavaKind.Long, (int) FrameWithoutBoxing.LONG_TAG);
        accessorKindToTag.put(JavaKind.Int, (int) FrameWithoutBoxing.INT_TAG);
        accessorKindToTag.put(JavaKind.Double, (int) FrameWithoutBoxing.DOUBLE_TAG);
        accessorKindToTag.put(JavaKind.Float, (int) FrameWithoutBoxing.FLOAT_TAG);
        accessorKindToTag.put(JavaKind.Boolean, (int) FrameWithoutBoxing.BOOLEAN_TAG);
        accessorKindToTag.put(JavaKind.Byte, (int) FrameWithoutBoxing.BYTE_TAG);
    }

    public static void registerFrameWithoutBoxingPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, FrameWithoutBoxing.class);
        registerFrameMethods(r);
        registerUnsafeCast(r, canDelayIntrinsification);
        registerUnsafeLoadStorePlugins(r, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object);

        if (Options.TruffleIntrinsifyFrameAccess.getValue()) {
            for (Map.Entry<JavaKind, Integer> kindAndTag : accessorKindToTag.entrySet()) {
                registerFrameAccessors(r, kindAndTag.getKey(), kindAndTag.getValue(), snippetReflection);
            }
        }
    }

    public static void registerFrameWithBoxingPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, FrameWithBoxing.class);
        registerFrameMethods(r);
        registerUnsafeCast(r, canDelayIntrinsification);
    }

    /**
     * We intrinisify the getXxx, setXxx, and isXxx methods for all type tags. The intrinsic nodes
     * are lightweight fixed nodes without a {@link FrameState}. No {@link FrameState} is important
     * for partial evaluation performance, because creating and later on discarding FrameStates for
     * the setXxx methods have a high compile time cost.
     *
     * Intrinsification requires the following conditions: (1) the accessed frame is directly the
     * {@link NewFrameNode}, (2) the accessed FrameSlot is a constant, and (3) the FrameDescriptor
     * was never materialized before. All three conditions together guarantee that the escape
     * analysis can virtualize the access. The condition (3) is necessary because a possible
     * materialization of the frame can prevent escape analysis - so in that case a FrameState for
     * setXxx methods is actually necessary since they stores can be state-changing memory
     * operations.
     *
     * Note that we do not register an intrinsification for {@link FrameWithoutBoxing#getValue}. It
     * is a complicated method to intrinsify, and it is not used frequently enough to justify the
     * complexity of an intrinisification.
     */
    private static void registerFrameAccessors(Registration r, JavaKind accessKind, int accessTag, SnippetReflectionProvider snippetReflection) {
        String nameSuffix = accessKind.name();
        r.register2("get" + nameSuffix, Receiver.class, FrameSlot.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                FrameSlot frameSlot = maybeGetConstantFrameSlot(snippetReflection, frameNode.get(false), frameSlotNode);
                if (frameSlot != null) {
                    b.addPush(accessKind, new VirtualFrameGetNode((NewFrameNode) frameNode.get(), frameSlot, accessKind, accessTag));
                    return true;
                }
                return false;
            }
        });

        r.register3("set" + nameSuffix, Receiver.class, FrameSlot.class, accessKind == JavaKind.Object ? Object.class : accessKind.toJavaClass(), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                FrameSlot frameSlot = maybeGetConstantFrameSlot(snippetReflection, frameNode.get(false), frameSlotNode);
                if (frameSlot != null) {
                    b.add(new VirtualFrameSetNode((NewFrameNode) frameNode.get(), frameSlot, accessTag, value));
                    return true;
                }
                return false;
            }
        });

        r.register2("is" + nameSuffix, Receiver.class, FrameSlot.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                FrameSlot frameSlot = maybeGetConstantFrameSlot(snippetReflection, frameNode.get(false), frameSlotNode);
                if (frameSlot != null) {
                    b.addPush(JavaKind.Boolean, new VirtualFrameIsNode((NewFrameNode) frameNode.get(), frameSlot, accessTag));
                    return true;
                }
                return false;
            }
        });
    }

    static FrameSlot maybeGetConstantFrameSlot(SnippetReflectionProvider snippetReflection, ValueNode frameNode, ValueNode frameSlotNode) {
        if (frameSlotNode.isConstant() && frameNode instanceof NewFrameNode && ((NewFrameNode) frameNode).getIntrinsifyAccessors()) {
            return snippetReflection.asObject(FrameSlot.class, frameSlotNode.asJavaConstant());
        }
        return null;
    }

    private static void registerFrameMethods(Registration r) {
        r.register1("getArguments", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                if (frame.get(false) instanceof NewFrameNode) {
                    b.push(JavaKind.Object, ((NewFrameNode) frame.get()).getArguments());
                    return true;
                }
                return false;
            }
        });

        r.register1("getFrameDescriptor", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                if (frame.get(false) instanceof NewFrameNode) {
                    b.push(JavaKind.Object, ((NewFrameNode) frame.get()).getDescriptor());
                    return true;
                }
                return false;
            }
        });

        r.register1("materialize", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode frame = receiver.get();
                if (Options.TruffleIntrinsifyFrameAccess.getValue() && frame instanceof NewFrameNode && ((NewFrameNode) frame).getIntrinsifyAccessors()) {
                    JavaConstant speculation = b.getGraph().getSpeculationLog().speculate(((NewFrameNode) frame).getIntrinsifyAccessorsSpeculation());
                    b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.TransferToInterpreter, speculation));
                    return true;
                }

                b.addPush(JavaKind.Object, new MaterializeFrameNode(frame));
                return true;
            }
        });
    }

    public static void registerUnsafeCast(Registration r, boolean canDelayIntrinsification) {
        r.register4("unsafeCast", Object.class, Class.class, boolean.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode clazz, ValueNode condition, ValueNode nonNull) {
                if (clazz.isConstant() && nonNull.isConstant()) {
                    ConstantReflectionProvider constantReflection = b.getConstantReflection();
                    ResolvedJavaType javaType = constantReflection.asJavaType(clazz.asConstant());
                    if (javaType == null) {
                        b.push(JavaKind.Object, object);
                    } else {
                        TypeReference type = TypeReference.createTrusted(b.getAssumptions(), javaType);
                        if (javaType.isArray()) {
                            type = type.asExactReference();
                        }
                        Stamp piStamp = StampFactory.object(type, nonNull.asJavaConstant().asInt() != 0);
                        ConditionAnchorNode valueAnchorNode = null;
                        if (condition.isConstant() && condition.asJavaConstant().asInt() == 1) {
                            // Nothing to do.
                        } else {
                            boolean skipAnchor = false;
                            LogicNode compareNode = CompareNode.createCompareNode(object.graph(), Condition.EQ, condition, ConstantNode.forBoolean(true, object.graph()), constantReflection);

                            if (compareNode instanceof LogicConstantNode) {
                                LogicConstantNode logicConstantNode = (LogicConstantNode) compareNode;
                                if (logicConstantNode.getValue()) {
                                    skipAnchor = true;
                                }
                            }

                            if (!skipAnchor) {
                                valueAnchorNode = b.add(new ConditionAnchorNode(compareNode));
                            }
                        }
                        b.addPush(JavaKind.Object, new PiNode(object, piStamp, valueAnchorNode));
                    }
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    throw b.bailout("unsafeCast arguments could not reduce to a constant: " + clazz + ", " + nonNull);
                }
            }
        });
    }

    public static void registerUnsafeLoadStorePlugins(Registration r, JavaKind... kinds) {
        for (JavaKind kind : kinds) {
            String kindName = kind.getJavaName();
            kindName = toUpperCase(kindName.charAt(0)) + kindName.substring(1);
            String getName = "unsafeGet" + kindName;
            String putName = "unsafePut" + kindName;
            r.register4(getName, Object.class, long.class, boolean.class, Object.class, new CustomizedUnsafeLoadPlugin(kind));
            r.register4(putName, Object.class, long.class, kind == JavaKind.Object ? Object.class : kind.toJavaClass(), Object.class, new CustomizedUnsafeStorePlugin(kind));
        }
    }

    static class CustomizedUnsafeLoadPlugin implements InvocationPlugin {

        private final JavaKind returnKind;

        CustomizedUnsafeLoadPlugin(JavaKind returnKind) {
            this.returnKind = returnKind;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode offset, ValueNode condition, ValueNode location) {
            if (location.isConstant()) {
                LocationIdentity locationIdentity;
                if (location.isNullConstant()) {
                    locationIdentity = LocationIdentity.any();
                } else {
                    locationIdentity = ObjectLocationIdentity.create(location.asJavaConstant());
                }
                LogicNode compare = b.add(CompareNode.createCompareNode(Condition.EQ, condition, ConstantNode.forBoolean(true, object.graph()), b.getConstantReflection()));
                b.addPush(returnKind, b.add(new UnsafeLoadNode(object, offset, returnKind, locationIdentity, compare)));
                return true;
            }
            // TODO: should we throw b.bailout() here?
            return false;
        }
    }

    static class CustomizedUnsafeStorePlugin implements InvocationPlugin {

        private final JavaKind kind;

        CustomizedUnsafeStorePlugin(JavaKind kind) {
            this.kind = kind;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode offset, ValueNode value, ValueNode location) {
            ValueNode locationArgument = location;
            if (locationArgument.isConstant()) {
                LocationIdentity locationIdentity;
                if (locationArgument.isNullConstant()) {
                    locationIdentity = LocationIdentity.any();
                } else {
                    locationIdentity = ObjectLocationIdentity.create(locationArgument.asJavaConstant());
                }

                b.add(new UnsafeStoreNode(object, offset, value, kind, locationIdentity, null));
                return true;
            }
            // TODO: should we throw b.bailout() here?
            return false;
        }
    }
}
