/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.bytecode.Bytecodes.AALOAD;
import static com.oracle.graal.bytecode.Bytecodes.AASTORE;
import static com.oracle.graal.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.graal.bytecode.Bytecodes.ALOAD;
import static com.oracle.graal.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.graal.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.graal.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.graal.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.graal.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.ARETURN;
import static com.oracle.graal.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.graal.bytecode.Bytecodes.ATHROW;
import static com.oracle.graal.bytecode.Bytecodes.BALOAD;
import static com.oracle.graal.bytecode.Bytecodes.BASTORE;
import static com.oracle.graal.bytecode.Bytecodes.BIPUSH;
import static com.oracle.graal.bytecode.Bytecodes.BREAKPOINT;
import static com.oracle.graal.bytecode.Bytecodes.CALOAD;
import static com.oracle.graal.bytecode.Bytecodes.CASTORE;
import static com.oracle.graal.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.graal.bytecode.Bytecodes.D2F;
import static com.oracle.graal.bytecode.Bytecodes.D2I;
import static com.oracle.graal.bytecode.Bytecodes.D2L;
import static com.oracle.graal.bytecode.Bytecodes.DADD;
import static com.oracle.graal.bytecode.Bytecodes.DALOAD;
import static com.oracle.graal.bytecode.Bytecodes.DASTORE;
import static com.oracle.graal.bytecode.Bytecodes.DCMPG;
import static com.oracle.graal.bytecode.Bytecodes.DCMPL;
import static com.oracle.graal.bytecode.Bytecodes.DCONST_0;
import static com.oracle.graal.bytecode.Bytecodes.DCONST_1;
import static com.oracle.graal.bytecode.Bytecodes.DDIV;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.graal.bytecode.Bytecodes.DMUL;
import static com.oracle.graal.bytecode.Bytecodes.DNEG;
import static com.oracle.graal.bytecode.Bytecodes.DREM;
import static com.oracle.graal.bytecode.Bytecodes.DRETURN;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.graal.bytecode.Bytecodes.DSUB;
import static com.oracle.graal.bytecode.Bytecodes.DUP;
import static com.oracle.graal.bytecode.Bytecodes.DUP2;
import static com.oracle.graal.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.graal.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.graal.bytecode.Bytecodes.DUP_X1;
import static com.oracle.graal.bytecode.Bytecodes.DUP_X2;
import static com.oracle.graal.bytecode.Bytecodes.F2D;
import static com.oracle.graal.bytecode.Bytecodes.F2I;
import static com.oracle.graal.bytecode.Bytecodes.F2L;
import static com.oracle.graal.bytecode.Bytecodes.FADD;
import static com.oracle.graal.bytecode.Bytecodes.FALOAD;
import static com.oracle.graal.bytecode.Bytecodes.FASTORE;
import static com.oracle.graal.bytecode.Bytecodes.FCMPG;
import static com.oracle.graal.bytecode.Bytecodes.FCMPL;
import static com.oracle.graal.bytecode.Bytecodes.FCONST_0;
import static com.oracle.graal.bytecode.Bytecodes.FCONST_1;
import static com.oracle.graal.bytecode.Bytecodes.FCONST_2;
import static com.oracle.graal.bytecode.Bytecodes.FDIV;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.graal.bytecode.Bytecodes.FMUL;
import static com.oracle.graal.bytecode.Bytecodes.FNEG;
import static com.oracle.graal.bytecode.Bytecodes.FREM;
import static com.oracle.graal.bytecode.Bytecodes.FRETURN;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.graal.bytecode.Bytecodes.FSUB;
import static com.oracle.graal.bytecode.Bytecodes.GETFIELD;
import static com.oracle.graal.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.graal.bytecode.Bytecodes.GOTO;
import static com.oracle.graal.bytecode.Bytecodes.GOTO_W;
import static com.oracle.graal.bytecode.Bytecodes.I2B;
import static com.oracle.graal.bytecode.Bytecodes.I2C;
import static com.oracle.graal.bytecode.Bytecodes.I2D;
import static com.oracle.graal.bytecode.Bytecodes.I2F;
import static com.oracle.graal.bytecode.Bytecodes.I2L;
import static com.oracle.graal.bytecode.Bytecodes.I2S;
import static com.oracle.graal.bytecode.Bytecodes.IADD;
import static com.oracle.graal.bytecode.Bytecodes.IALOAD;
import static com.oracle.graal.bytecode.Bytecodes.IAND;
import static com.oracle.graal.bytecode.Bytecodes.IASTORE;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_0;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_1;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_2;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_3;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_4;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_5;
import static com.oracle.graal.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.graal.bytecode.Bytecodes.IDIV;
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
import static com.oracle.graal.bytecode.Bytecodes.IINC;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.graal.bytecode.Bytecodes.IMUL;
import static com.oracle.graal.bytecode.Bytecodes.INEG;
import static com.oracle.graal.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.graal.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.graal.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.graal.bytecode.Bytecodes.IOR;
import static com.oracle.graal.bytecode.Bytecodes.IREM;
import static com.oracle.graal.bytecode.Bytecodes.IRETURN;
import static com.oracle.graal.bytecode.Bytecodes.ISHL;
import static com.oracle.graal.bytecode.Bytecodes.ISHR;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.graal.bytecode.Bytecodes.ISUB;
import static com.oracle.graal.bytecode.Bytecodes.IUSHR;
import static com.oracle.graal.bytecode.Bytecodes.IXOR;
import static com.oracle.graal.bytecode.Bytecodes.JSR;
import static com.oracle.graal.bytecode.Bytecodes.JSR_W;
import static com.oracle.graal.bytecode.Bytecodes.L2D;
import static com.oracle.graal.bytecode.Bytecodes.L2F;
import static com.oracle.graal.bytecode.Bytecodes.L2I;
import static com.oracle.graal.bytecode.Bytecodes.LADD;
import static com.oracle.graal.bytecode.Bytecodes.LALOAD;
import static com.oracle.graal.bytecode.Bytecodes.LAND;
import static com.oracle.graal.bytecode.Bytecodes.LASTORE;
import static com.oracle.graal.bytecode.Bytecodes.LCMP;
import static com.oracle.graal.bytecode.Bytecodes.LCONST_0;
import static com.oracle.graal.bytecode.Bytecodes.LCONST_1;
import static com.oracle.graal.bytecode.Bytecodes.LDC;
import static com.oracle.graal.bytecode.Bytecodes.LDC2_W;
import static com.oracle.graal.bytecode.Bytecodes.LDC_W;
import static com.oracle.graal.bytecode.Bytecodes.LDIV;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.graal.bytecode.Bytecodes.LMUL;
import static com.oracle.graal.bytecode.Bytecodes.LNEG;
import static com.oracle.graal.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.graal.bytecode.Bytecodes.LOR;
import static com.oracle.graal.bytecode.Bytecodes.LREM;
import static com.oracle.graal.bytecode.Bytecodes.LRETURN;
import static com.oracle.graal.bytecode.Bytecodes.LSHL;
import static com.oracle.graal.bytecode.Bytecodes.LSHR;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.graal.bytecode.Bytecodes.LSUB;
import static com.oracle.graal.bytecode.Bytecodes.LUSHR;
import static com.oracle.graal.bytecode.Bytecodes.LXOR;
import static com.oracle.graal.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.graal.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.graal.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.NEW;
import static com.oracle.graal.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.NOP;
import static com.oracle.graal.bytecode.Bytecodes.POP;
import static com.oracle.graal.bytecode.Bytecodes.POP2;
import static com.oracle.graal.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.graal.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.graal.bytecode.Bytecodes.RET;
import static com.oracle.graal.bytecode.Bytecodes.RETURN;
import static com.oracle.graal.bytecode.Bytecodes.SALOAD;
import static com.oracle.graal.bytecode.Bytecodes.SASTORE;
import static com.oracle.graal.bytecode.Bytecodes.SIPUSH;
import static com.oracle.graal.bytecode.Bytecodes.SWAP;
import static com.oracle.graal.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.graal.bytecode.Bytecodes.nameOf;
import static com.oracle.graal.compiler.common.GraalOptions.DeoptALot;
import static com.oracle.graal.compiler.common.GraalOptions.NewInfopoints;
import static com.oracle.graal.compiler.common.GraalOptions.PrintProfilingInformation;
import static com.oracle.graal.compiler.common.GraalOptions.ResolveClassBeforeStaticInvoke;
import static com.oracle.graal.compiler.common.GraalOptions.StressInvokeWithExceptionNode;
import static com.oracle.graal.compiler.common.type.StampFactory.objectNonNull;
import static com.oracle.graal.java.BytecodeParserOptions.DumpDuringGraphBuilding;
import static com.oracle.graal.java.BytecodeParserOptions.TraceInlineDuringParsing;
import static com.oracle.graal.java.BytecodeParserOptions.TraceParserPlugins;
import static com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_DURING_PARSING;
import static com.oracle.graal.nodes.type.StampTool.isPointerNonNull;
import static java.lang.String.format;
import static jdk.vm.ci.common.JVMCIError.guarantee;
import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateRecompile;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.JavaSubroutineMismatch;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;
import static jdk.vm.ci.meta.DeoptimizationReason.TransferToInterpreter;
import static jdk.vm.ci.meta.DeoptimizationReason.UnreachedCode;
import static jdk.vm.ci.meta.DeoptimizationReason.Unresolved;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.compiler.common.type.CheckedJavaType;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocationIdentity;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;

import com.oracle.graal.bytecode.BytecodeLookupSwitch;
import com.oracle.graal.bytecode.BytecodeStream;
import com.oracle.graal.bytecode.BytecodeSwitch;
import com.oracle.graal.bytecode.BytecodeTableSwitch;
import com.oracle.graal.bytecode.Bytecodes;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.compiler.common.type.AbstractPointerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.BeginNode;
import com.oracle.graal.nodes.BeginStateSplitNode;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ControlSplitNode;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.EntryMarkerNode;
import com.oracle.graal.nodes.EntryProxyNode;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.FullInfopointNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.KillingBeginNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNegationNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.LoopExitNode;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.SimpleInfopointNode;
import com.oracle.graal.nodes.StartNode;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.AndNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.calc.DivNode;
import com.oracle.graal.nodes.calc.FloatConvertNode;
import com.oracle.graal.nodes.calc.IntegerBelowNode;
import com.oracle.graal.nodes.calc.IntegerDivNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.calc.IntegerLessThanNode;
import com.oracle.graal.nodes.calc.IntegerRemNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.calc.LeftShiftNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.NarrowNode;
import com.oracle.graal.nodes.calc.NegateNode;
import com.oracle.graal.nodes.calc.NormalizeCompareNode;
import com.oracle.graal.nodes.calc.ObjectEqualsNode;
import com.oracle.graal.nodes.calc.OrNode;
import com.oracle.graal.nodes.calc.RemNode;
import com.oracle.graal.nodes.calc.RightShiftNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.nodes.calc.SubNode;
import com.oracle.graal.nodes.calc.UnsignedRightShiftNode;
import com.oracle.graal.nodes.calc.XorNode;
import com.oracle.graal.nodes.calc.ZeroExtendNode;
import com.oracle.graal.nodes.extended.BranchProbabilityNode;
import com.oracle.graal.nodes.extended.BytecodeExceptionNode;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.extended.IntegerSwitchNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import com.oracle.graal.nodes.graphbuilderconf.NodePlugin;
import com.oracle.graal.nodes.java.ArrayLengthNode;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.nodes.java.ExceptionObjectNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.MonitorEnterNode;
import com.oracle.graal.nodes.java.MonitorExitNode;
import com.oracle.graal.nodes.java.MonitorIdNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.java.NewInstanceNode;
import com.oracle.graal.nodes.java.NewMultiArrayNode;
import com.oracle.graal.nodes.java.RegisterFinalizerNode;
import com.oracle.graal.nodes.java.StoreFieldNode;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.nodes.java.TypeCheckNode;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.OptimisticOptimizations;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
public class BytecodeParser implements GraphBuilderContext {

    /**
     * The minimum value to which {@link BytecodeParserOptions#TraceBytecodeParserLevel} must be set
     * to trace the bytecode instructions as they are parsed.
     */
    public static final int TRACELEVEL_INSTRUCTIONS = 1;

    /**
     * The minimum value to which {@link BytecodeParserOptions#TraceBytecodeParserLevel} must be set
     * to trace the frame state before each bytecode instruction as it is parsed.
     */
    public static final int TRACELEVEL_STATE = 2;

    /**
     * Meters the number of actual bytecodes parsed.
     */
    public static final DebugMetric BytecodesParsed = Debug.metric("BytecodesParsed");

    protected static final DebugMetric EXPLICIT_EXCEPTIONS = Debug.metric("ExplicitExceptions");

    /**
     * A scoped object for tasks to be performed after parsing an intrinsic such as processing
     * {@linkplain BytecodeFrame#isPlaceholderBci(int) placeholder} frames states.
     */
    static class IntrinsicScope implements AutoCloseable {
        FrameState stateBefore;
        final Mark mark;
        final BytecodeParser parser;

        /**
         * Creates a scope for root parsing an intrinsic.
         *
         * @param parser the parsing context of the intrinsic
         */
        IntrinsicScope(BytecodeParser parser) {
            this.parser = parser;
            assert parser.parent == null;
            assert parser.bci() == 0;
            mark = null;
        }

        /**
         * Creates a scope for parsing an intrinsic during graph builder inlining.
         *
         * @param parser the parsing context of the (non-intrinsic) method calling the intrinsic
         * @param args the arguments to the call
         */
        IntrinsicScope(BytecodeParser parser, JavaKind[] argSlotKinds, ValueNode[] args) {
            assert !parser.parsingIntrinsic();
            this.parser = parser;
            mark = parser.getGraph().getMark();
            stateBefore = parser.frameState.create(parser.bci(), parser.getNonIntrinsicAncestor(), false, argSlotKinds, args);
        }

        public void close() {
            IntrinsicContext intrinsic = parser.intrinsicContext;
            if (intrinsic != null && intrinsic.isPostParseInlined()) {
                return;
            }

            processPlaceholderFrameStates(intrinsic);
        }

        /**
         * Fixes up the {@linkplain BytecodeFrame#isPlaceholderBci(int) placeholder} frame states
         * added to the graph while parsing/inlining the intrinsic for which this object exists.
         */
        private void processPlaceholderFrameStates(IntrinsicContext intrinsic) {
            FrameState stateAfterReturn = null;
            StructuredGraph graph = parser.getGraph();
            for (Node node : graph.getNewNodes(mark)) {
                if (node instanceof FrameState) {
                    FrameState frameState = (FrameState) node;
                    if (BytecodeFrame.isPlaceholderBci(frameState.bci)) {
                        if (frameState.bci == BytecodeFrame.AFTER_BCI) {
                            FrameStateBuilder frameStateBuilder = parser.frameState;
                            if (frameState.stackSize() != 0) {
                                assert frameState.usages().count() == 1;
                                ValueNode returnVal = frameState.stackAt(0);
                                assert returnVal == frameState.usages().first();

                                /*
                                 * Swap the top-of-stack value with the side-effect return value
                                 * using the frame state.
                                 */
                                JavaKind returnKind = parser.currentInvokeReturnType.getJavaKind();
                                ValueNode tos = frameStateBuilder.pop(returnKind);
                                assert tos.getStackKind() == returnVal.getStackKind();
                                FrameState newFrameState = frameStateBuilder.create(parser.stream.nextBCI(), parser.getNonIntrinsicAncestor(), false, new JavaKind[]{returnKind},
                                                new ValueNode[]{returnVal});
                                frameState.replaceAndDelete(newFrameState);
                                frameStateBuilder.push(returnKind, tos);
                            } else {
                                if (stateAfterReturn == null) {
                                    if (intrinsic != null) {
                                        assert intrinsic.isCompilationRoot();
                                        stateAfterReturn = graph.add(new FrameState(BytecodeFrame.INVALID_FRAMESTATE_BCI));
                                    } else {
                                        stateAfterReturn = frameStateBuilder.create(parser.stream.nextBCI(), null);
                                    }
                                }
                                frameState.replaceAndDelete(stateAfterReturn);
                            }
                        } else if (frameState.bci == BytecodeFrame.BEFORE_BCI) {
                            if (stateBefore == null) {
                                stateBefore = graph.start().stateAfter();
                            }
                            if (stateBefore != frameState) {
                                frameState.replaceAndDelete(stateBefore);
                            }
                        } else {
                            assert frameState.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI;
                        }
                    }
                }
            }
        }
    }

    private static class Target {
        FixedNode fixed;
        FrameStateBuilder state;

        Target(FixedNode fixed, FrameStateBuilder state) {
            this.fixed = fixed;
            this.state = state;
        }
    }

    @SuppressWarnings("serial")
    public static class BytecodeParserError extends JVMCIError {

        public BytecodeParserError(Throwable cause) {
            super(cause);
        }

        public BytecodeParserError(String msg, Object... args) {
            super(msg, args);
        }
    }

    private final GraphBuilderPhase.Instance graphBuilderInstance;
    protected final StructuredGraph graph;

    private BciBlockMapping blockMap;
    private LocalLiveness liveness;
    protected final int entryBCI;
    private final BytecodeParser parent;

    private LineNumberTable lnt;
    private int previousLineNumber;
    private int currentLineNumber;

    private ValueNode methodSynchronizedObject;

    private ValueNode returnValue;
    private FixedWithNextNode beforeReturnNode;
    private ValueNode unwindValue;
    private FixedWithNextNode beforeUnwindNode;

    protected FixedWithNextNode lastInstr;                 // the last instruction added
    private boolean controlFlowSplit;
    private final InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(this);

    private FixedWithNextNode[] firstInstructionArray;
    private FrameStateBuilder[] entryStateArray;

    protected BytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        this.graphBuilderInstance = graphBuilderInstance;
        this.graph = graph;
        this.graphBuilderConfig = graphBuilderInstance.graphBuilderConfig;
        this.optimisticOpts = graphBuilderInstance.optimisticOpts;
        this.metaAccess = graphBuilderInstance.metaAccess;
        this.stampProvider = graphBuilderInstance.stampProvider;
        this.constantReflection = graphBuilderInstance.constantReflection;
        this.stream = new BytecodeStream(method.getCode());
        this.profilingInfo = graph.useProfilingInfo() ? method.getProfilingInfo() : null;
        this.constantPool = method.getConstantPool();
        this.method = method;
        this.intrinsicContext = intrinsicContext;
        this.entryBCI = entryBCI;
        this.parent = parent;

        assert method.getCode() != null : "method must contain bytecodes: " + method;

        if (graphBuilderConfig.insertNonSafepointDebugInfo() && !parsingIntrinsic()) {
            lnt = method.getLineNumberTable();
            previousLineNumber = -1;
        }
    }

    protected GraphBuilderPhase.Instance getGraphBuilderInstance() {
        return graphBuilderInstance;
    }

    public ValueNode getReturnValue() {
        return returnValue;
    }

    public FixedWithNextNode getBeforeReturnNode() {
        return this.beforeReturnNode;
    }

    public ValueNode getUnwindValue() {
        return unwindValue;
    }

    public FixedWithNextNode getBeforeUnwindNode() {
        return this.beforeUnwindNode;
    }

    @SuppressWarnings("try")
    protected void buildRootMethod() {
        FrameStateBuilder startFrameState = new FrameStateBuilder(this, method, graph);
        startFrameState.initializeForMethodStart(graphBuilderConfig.eagerResolving() || intrinsicContext != null, graphBuilderConfig.getPlugins().getParameterPlugins());

        try (IntrinsicScope s = intrinsicContext != null ? new IntrinsicScope(this) : null) {
            build(graph.start(), startFrameState);
        }

        cleanupFinalGraph();
        ComputeLoopFrequenciesClosure.compute(graph);
    }

    @SuppressWarnings("try")
    protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
        if (PrintProfilingInformation.getValue() && profilingInfo != null) {
            TTY.println("Profiling info for " + method.format("%H.%n(%p)"));
            TTY.println(MetaUtil.indent(profilingInfo.toString(method, CodeUtil.NEW_LINE), "  "));
        }

        try (Indent indent = Debug.logAndIndent("build graph for %s", method)) {

            // compute the block map, setup exception handlers and get the entrypoint(s)
            BciBlockMapping newMapping = BciBlockMapping.create(stream, method);
            this.blockMap = newMapping;
            this.firstInstructionArray = new FixedWithNextNode[blockMap.getBlockCount()];
            this.entryStateArray = new FrameStateBuilder[blockMap.getBlockCount()];

            /*
             * Configure the assertion checking behavior of the FrameStateBuilder. This needs to be
             * done only when assertions are enabled, so it is wrapped in an assertion itself.
             */
            assert computeKindVerification(startFrameState);

            try (Scope s = Debug.scope("LivenessAnalysis")) {
                int maxLocals = method.getMaxLocals();
                liveness = LocalLiveness.compute(stream, blockMap.getBlocks(), maxLocals, blockMap.getLoopCount());
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            lastInstr = startInstruction;
            this.setCurrentFrameState(startFrameState);
            stream.setBCI(0);

            BciBlock startBlock = blockMap.getStartBlock();
            if (this.parent == null) {
                StartNode startNode = graph.start();
                if (method.isSynchronized()) {
                    assert !parsingIntrinsic();
                    startNode.setStateAfter(createFrameState(BytecodeFrame.BEFORE_BCI, startNode));
                } else {
                    if (!parsingIntrinsic()) {
                        if (graph.method() != null && graph.method().isJavaLangObjectInit()) {
                            /*
                             * Don't clear the receiver when Object.<init> is the compilation root.
                             * The receiver is needed as input to RegisterFinalizerNode.
                             */
                        } else {
                            frameState.clearNonLiveLocals(startBlock, liveness, true);
                        }
                        assert bci() == 0;
                        startNode.setStateAfter(createFrameState(bci(), startNode));
                    } else {
                        if (startNode.stateAfter() == null) {
                            FrameState stateAfterStart = createStateAfterStartOfReplacementGraph();
                            startNode.setStateAfter(stateAfterStart);
                        }
                    }
                }
            }

            if (method.isSynchronized()) {
                // add a monitor enter to the start block
                methodSynchronizedObject = synchronizedObject(frameState, method);
                frameState.clearNonLiveLocals(startBlock, liveness, true);
                assert bci() == 0;
                genMonitorEnter(methodSynchronizedObject, bci());
            }

            finishPrepare(lastInstr);

            if (GraalOptions.OldInfopoints.getValue() && graphBuilderConfig.insertNonSafepointDebugInfo() && !parsingIntrinsic()) {
                genInfoPointNode(InfopointReason.METHOD_START, null);
            }

            currentBlock = blockMap.getStartBlock();
            setEntryState(startBlock, frameState);
            if (startBlock.isLoopHeader) {
                appendGoto(startBlock);
            } else {
                setFirstInstruction(startBlock, lastInstr);
            }

            BciBlock[] blocks = blockMap.getBlocks();
            for (BciBlock block : blocks) {
                processBlock(block);
            }

            if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue() && this.beforeReturnNode != startInstruction) {
                Debug.dump(graph, "Bytecodes parsed: " + method.getDeclaringClass().getUnqualifiedName() + "." + method.getName());
            }
        }
    }

    private boolean computeKindVerification(FrameStateBuilder startFrameState) {
        if (blockMap.hasJsrBytecodes) {
            /*
             * The JSR return address is an int value, but stored using the astore bytecode. Instead
             * of weakening the kind assertion checking for all methods, we disable it completely
             * for methods that contain a JSR bytecode.
             */
            startFrameState.disableKindVerification();
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.canChangeStackKind(this)) {
                /*
                 * We have a plugin that can change the kind of values, so no kind assertion
                 * checking is possible.
                 */
                startFrameState.disableKindVerification();
            }
        }
        return true;
    }

    /**
     * Hook for subclasses to modify the graph start instruction or append new instructions to it.
     *
     * @param startInstr the start instruction of the graph
     */
    protected void finishPrepare(FixedWithNextNode startInstr) {
    }

    protected void cleanupFinalGraph() {
        GraphUtil.normalizeLoops(graph);

        // Remove dead parameters.
        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            if (param.hasNoUsages()) {
                assert param.inputs().isEmpty();
                param.safeDelete();
            }
        }

        // Remove redundant begin nodes.
        for (BeginNode beginNode : graph.getNodes(BeginNode.TYPE)) {
            Node predecessor = beginNode.predecessor();
            if (predecessor instanceof ControlSplitNode) {
                // The begin node is necessary.
            } else {
                if (beginNode.hasUsages()) {
                    reanchorGuardedNodes(beginNode);
                }
                GraphUtil.unlinkFixedNode(beginNode);
                beginNode.safeDelete();
            }
        }
    }

    /**
     * Removes {@link GuardedNode}s from {@code beginNode}'s usages and re-attaches them to an
     * appropriate preceeding {@link GuardingNode}.
     */
    protected void reanchorGuardedNodes(BeginNode beginNode) {
        // Find the new guarding node
        GuardingNode guarding = null;
        Node pred = beginNode.predecessor();
        while (pred != null) {
            if (pred instanceof BeginNode) {
                if (pred.predecessor() instanceof ControlSplitNode) {
                    guarding = (GuardingNode) pred;
                    break;
                }
            } else if (pred.getNodeClass().getAllowedUsageTypes().contains(InputType.Guard)) {
                guarding = (GuardingNode) pred;
                break;
            }
            pred = pred.predecessor();
        }

        // Reset the guard for all of beginNode's usages
        for (Node usage : beginNode.usages().snapshot()) {
            GuardedNode guarded = (GuardedNode) usage;
            assert guarded.getGuard() == beginNode;
            guarded.setGuard(guarding);
        }
        assert beginNode.hasNoUsages() : beginNode;
    }

    /**
     * Creates the frame state after the start node of a graph for an {@link IntrinsicContext
     * intrinsic} that is the parse root (either for root compiling or for post-parse inlining).
     */
    private FrameState createStateAfterStartOfReplacementGraph() {
        assert parent == null;
        assert frameState.getMethod().equals(intrinsicContext.getIntrinsicMethod());
        assert bci() == 0;
        assert frameState.stackSize() == 0;
        FrameState stateAfterStart;
        if (intrinsicContext.isPostParseInlined()) {
            stateAfterStart = graph.add(new FrameState(BytecodeFrame.BEFORE_BCI));
        } else {
            ResolvedJavaMethod original = intrinsicContext.getOriginalMethod();
            ValueNode[] locals;
            if (original.getMaxLocals() == frameState.localsSize() || original.isNative()) {
                locals = new ValueNode[original.getMaxLocals()];
                for (int i = 0; i < locals.length; i++) {
                    ValueNode node = frameState.locals[i];
                    if (node == FrameState.TWO_SLOT_MARKER) {
                        node = null;
                    }
                    locals[i] = node;
                }
            } else {
                locals = new ValueNode[original.getMaxLocals()];
                int parameterCount = original.getSignature().getParameterCount(!original.isStatic());
                for (int i = 0; i < parameterCount; i++) {
                    ValueNode param = frameState.locals[i];
                    if (param == FrameState.TWO_SLOT_MARKER) {
                        param = null;
                    }
                    locals[i] = param;
                    assert param == null || param instanceof ParameterNode || param.isConstant();
                }
            }
            ValueNode[] stack = {};
            int stackSize = 0;
            ValueNode[] locks = {};
            List<MonitorIdNode> monitorIds = Collections.emptyList();
            stateAfterStart = graph.add(new FrameState(null, original, 0, locals, stack, stackSize, locks, monitorIds, false, false));
        }
        return stateAfterStart;
    }

    /**
     * @param type the unresolved type of the constant
     */
    protected void handleUnresolvedLoadConstant(JavaType type) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
        assert !graphBuilderConfig.eagerResolving();
        append(new FixedGuardNode(graph.unique(new IsNullNode(object)), Unresolved, InvalidateRecompile));
        frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER));
    }

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
        assert !graphBuilderConfig.eagerResolving();
        AbstractBeginNode successor = graph.add(new BeginNode());
        DeoptimizeNode deopt = graph.add(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        append(new IfNode(graph.unique(new IsNullNode(object)), successor, deopt, 1));
        lastInstr = successor;
        frameState.push(JavaKind.Int, appendConstant(JavaConstant.INT_0));
    }

    /**
     * @param type the type being instantiated
     */
    protected void handleUnresolvedNewInstance(JavaType type) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param type the type of the array being instantiated
     * @param length the length of the array
     */
    protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param type the type being instantiated
     * @param dims the dimensions for the multi-array
     */
    protected void handleUnresolvedNewMultiArray(JavaType type, ValueNode[] dims) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param field the unresolved field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param field the unresolved field
     * @param value the value being stored to the field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param type
     */
    protected void handleUnresolvedExceptionType(JavaType type) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    /**
     * @param javaMethod
     * @param invokeKind
     */
    protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
        assert !graphBuilderConfig.eagerResolving();
        append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
    }

    private AbstractBeginNode handleException(ValueNode exceptionObject, int bci) {
        assert bci == BytecodeFrame.BEFORE_BCI || bci == bci() : "invalid bci";
        Debug.log("Creating exception dispatch edges at %d, exception object=%s, exception seen=%s", bci, exceptionObject, (profilingInfo == null ? "" : profilingInfo.getExceptionSeen(bci)));

        FrameStateBuilder dispatchState = frameState.copy();
        dispatchState.clearStack();

        AbstractBeginNode dispatchBegin;
        if (exceptionObject == null) {
            ExceptionObjectNode newExceptionObject = graph.add(new ExceptionObjectNode(metaAccess));
            dispatchBegin = newExceptionObject;
            dispatchState.push(JavaKind.Object, dispatchBegin);
            dispatchState.setRethrowException(true);
            newExceptionObject.setStateAfter(dispatchState.create(bci, newExceptionObject));
        } else {
            dispatchBegin = graph.add(new BeginNode());
            dispatchState.push(JavaKind.Object, exceptionObject);
            dispatchState.setRethrowException(true);
        }
        this.controlFlowSplit = true;
        FixedWithNextNode finishedDispatch = finishInstruction(dispatchBegin, dispatchState);

        createHandleExceptionTarget(finishedDispatch, bci, dispatchState);

        return dispatchBegin;
    }

    protected void createHandleExceptionTarget(FixedWithNextNode finishedDispatch, int bci, FrameStateBuilder dispatchState) {
        BciBlock dispatchBlock = currentBlock.exceptionDispatchBlock();
        /*
         * The exception dispatch block is always for the last bytecode of a block, so if we are not
         * at the endBci yet, there is no exception handler for this bci and we can unwind
         * immediately.
         */
        if (bci != currentBlock.endBci || dispatchBlock == null) {
            dispatchBlock = blockMap.getUnwindBlock();
        }

        FixedNode target = createTarget(dispatchBlock, dispatchState);
        finishedDispatch.setNext(target);
    }

    protected ValueNode genLoadIndexed(ValueNode array, ValueNode index, JavaKind kind) {
        return LoadIndexedNode.create(array, index, kind, metaAccess, constantReflection);
    }

    protected void genStoreIndexed(ValueNode array, ValueNode index, JavaKind kind, ValueNode value) {
        add(new StoreIndexedNode(array, index, kind, value));
    }

    protected ValueNode genIntegerAdd(ValueNode x, ValueNode y) {
        return AddNode.create(x, y);
    }

    protected ValueNode genIntegerSub(ValueNode x, ValueNode y) {
        return SubNode.create(x, y);
    }

    protected ValueNode genIntegerMul(ValueNode x, ValueNode y) {
        return MulNode.create(x, y);
    }

    protected ValueNode genFloatAdd(ValueNode x, ValueNode y) {
        return AddNode.create(x, y);
    }

    protected ValueNode genFloatSub(ValueNode x, ValueNode y) {
        return SubNode.create(x, y);
    }

    protected ValueNode genFloatMul(ValueNode x, ValueNode y) {
        return MulNode.create(x, y);
    }

    protected ValueNode genFloatDiv(ValueNode x, ValueNode y) {
        return DivNode.create(x, y);
    }

    protected ValueNode genFloatRem(ValueNode x, ValueNode y) {
        return new RemNode(x, y);
    }

    protected ValueNode genIntegerDiv(ValueNode x, ValueNode y) {
        return new IntegerDivNode(x, y);
    }

    protected ValueNode genIntegerRem(ValueNode x, ValueNode y) {
        return new IntegerRemNode(x, y);
    }

    protected ValueNode genNegateOp(ValueNode x) {
        return (new NegateNode(x));
    }

    protected ValueNode genLeftShift(ValueNode x, ValueNode y) {
        return new LeftShiftNode(x, y);
    }

    protected ValueNode genRightShift(ValueNode x, ValueNode y) {
        return new RightShiftNode(x, y);
    }

    protected ValueNode genUnsignedRightShift(ValueNode x, ValueNode y) {
        return new UnsignedRightShiftNode(x, y);
    }

    protected ValueNode genAnd(ValueNode x, ValueNode y) {
        return AndNode.create(x, y);
    }

    protected ValueNode genOr(ValueNode x, ValueNode y) {
        return OrNode.create(x, y);
    }

    protected ValueNode genXor(ValueNode x, ValueNode y) {
        return XorNode.create(x, y);
    }

    protected ValueNode genNormalizeCompare(ValueNode x, ValueNode y, boolean isUnorderedLess) {
        return NormalizeCompareNode.create(x, y, isUnorderedLess, constantReflection);
    }

    protected ValueNode genFloatConvert(FloatConvert op, ValueNode input) {
        return FloatConvertNode.create(op, input);
    }

    protected ValueNode genNarrow(ValueNode input, int bitCount) {
        return NarrowNode.create(input, bitCount);
    }

    protected ValueNode genSignExtend(ValueNode input, int bitCount) {
        return SignExtendNode.create(input, bitCount);
    }

    protected ValueNode genZeroExtend(ValueNode input, int bitCount) {
        return ZeroExtendNode.create(input, bitCount);
    }

    protected void genGoto() {
        appendGoto(currentBlock.getSuccessor(0));
        assert currentBlock.numNormalSuccessors() == 1;
    }

    protected LogicNode genObjectEquals(ValueNode x, ValueNode y) {
        return ObjectEqualsNode.create(x, y, constantReflection);
    }

    protected LogicNode genIntegerEquals(ValueNode x, ValueNode y) {
        return IntegerEqualsNode.create(x, y, constantReflection);
    }

    protected LogicNode genIntegerLessThan(ValueNode x, ValueNode y) {
        return IntegerLessThanNode.create(x, y, constantReflection);
    }

    protected ValueNode genUnique(ValueNode x) {
        return (ValueNode) graph.unique((Node & ValueNumberable) x);
    }

    protected ValueNode genIfNode(LogicNode condition, FixedNode falseSuccessor, FixedNode trueSuccessor, double d) {
        return new IfNode(condition, falseSuccessor, trueSuccessor, d);
    }

    protected void genThrow() {
        if (GraalOptions.OldInfopoints.getValue() && graphBuilderConfig.insertNonSafepointDebugInfo() && !parsingIntrinsic()) {
            genInfoPointNode(InfopointReason.BYTECODE_POSITION, null);
        }

        ValueNode exception = frameState.pop(JavaKind.Object);
        FixedGuardNode nullCheck = append(new FixedGuardNode(graph.unique(new IsNullNode(exception)), NullCheckException, InvalidateReprofile, true));
        PiNode nonNullException = graph.unique(new PiNode(exception, exception.stamp().join(objectNonNull())));
        nonNullException.setGuard(nullCheck);
        lastInstr.setNext(handleException(nonNullException, bci()));
    }

    protected ValueNode createCheckCast(CheckedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck, boolean forStoreCheck) {
        return CheckCastNode.create(type, object, profileForTypeCheck, forStoreCheck);
    }

    protected ValueNode createInstanceOf(CheckedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck) {
        return InstanceOfNode.create(type, object, profileForTypeCheck);
    }

    protected ValueNode genConditional(ValueNode x) {
        return new ConditionalNode((LogicNode) x);
    }

    protected NewInstanceNode createNewInstance(ResolvedJavaType type, boolean fillContents) {
        return new NewInstanceNode(type, fillContents);
    }

    protected NewArrayNode createNewArray(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        return new NewArrayNode(elementType, length, fillContents);
    }

    protected NewMultiArrayNode createNewMultiArray(ResolvedJavaType type, ValueNode[] dimensions) {
        return new NewMultiArrayNode(type, dimensions);
    }

    protected ValueNode genLoadField(ValueNode receiver, ResolvedJavaField field) {
        return new LoadFieldNode(receiver, field);
    }

    protected ValueNode emitExplicitNullCheck(ValueNode receiver) {
        if (StampTool.isPointerNonNull(receiver.stamp())) {
            return receiver;
        }
        BytecodeExceptionNode exception = graph.add(new BytecodeExceptionNode(metaAccess, NullPointerException.class));
        AbstractBeginNode falseSucc = graph.add(new BeginNode());
        PiNode nonNullReceiver = graph.unique(new PiNode(receiver, receiver.stamp().join(objectNonNull())));
        nonNullReceiver.setGuard(falseSucc);
        append(new IfNode(graph.unique(new IsNullNode(receiver)), exception, falseSucc, 0.01));
        lastInstr = falseSucc;

        exception.setStateAfter(createFrameState(bci(), exception));
        exception.setNext(handleException(exception, bci()));
        return nonNullReceiver;
    }

    protected void emitExplicitBoundsCheck(ValueNode index, ValueNode length) {
        AbstractBeginNode trueSucc = graph.add(new BeginNode());
        BytecodeExceptionNode exception = graph.add(new BytecodeExceptionNode(metaAccess, ArrayIndexOutOfBoundsException.class, index));
        append(new IfNode(graph.unique(IntegerBelowNode.create(index, length, constantReflection)), trueSucc, exception, 0.99));
        lastInstr = trueSucc;

        exception.setStateAfter(createFrameState(bci(), exception));
        exception.setNext(handleException(exception, bci()));
    }

    protected ValueNode genArrayLength(ValueNode x) {
        return ArrayLengthNode.create(x, constantReflection);
    }

    protected void genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
        StoreFieldNode storeFieldNode = new StoreFieldNode(receiver, field, value);
        append(storeFieldNode);
        storeFieldNode.setStateAfter(this.createFrameState(stream.nextBCI(), storeFieldNode));
    }

    /**
     * Ensure that concrete classes are at least linked before generating an invoke. Interfaces may
     * never be linked so simply return true for them.
     *
     * @param target
     * @return true if the declared holder is an interface or is linked
     */
    private static boolean callTargetIsResolved(JavaMethod target) {
        if (target instanceof ResolvedJavaMethod) {
            ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
            ResolvedJavaType resolvedType = resolvedTarget.getDeclaringClass();
            return resolvedType.isInterface() || resolvedType.isLinked();
        }
        return false;
    }

    protected void genInvokeStatic(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
            ResolvedJavaType holder = resolvedTarget.getDeclaringClass();
            if (!holder.isInitialized() && ResolveClassBeforeStaticInvoke.getValue()) {
                handleUnresolvedInvoke(target, InvokeKind.Static);
            } else {
                ValueNode[] args = frameState.popArguments(resolvedTarget.getSignature().getParameterCount(false));
                appendInvoke(InvokeKind.Static, resolvedTarget, args);
            }
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Static);
        }
    }

    protected void genInvokeInterface(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
            appendInvoke(InvokeKind.Interface, (ResolvedJavaMethod) target, args);
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Interface);
        }
    }

    protected void genInvokeDynamic(JavaMethod target) {
        if (target instanceof ResolvedJavaMethod) {
            JavaConstant appendix = constantPool.lookupAppendix(stream.readCPI4(), Bytecodes.INVOKEDYNAMIC);
            if (appendix != null) {
                frameState.push(JavaKind.Object, ConstantNode.forConstant(appendix, metaAccess, graph));
            }
            ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(false));
            appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Static);
        }
    }

    protected void genInvokeVirtual(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            /*
             * Special handling for runtimes that rewrite an invocation of MethodHandle.invoke(...)
             * or MethodHandle.invokeExact(...) to a static adapter. HotSpot does this - see
             * https://wikis.oracle.com/display/HotSpotInternals/Method+handles +and+invokedynamic
             */
            boolean hasReceiver = !((ResolvedJavaMethod) target).isStatic();
            JavaConstant appendix = constantPool.lookupAppendix(stream.readCPI(), Bytecodes.INVOKEVIRTUAL);
            if (appendix != null) {
                frameState.push(JavaKind.Object, ConstantNode.forConstant(appendix, metaAccess, graph));
            }
            ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(hasReceiver));
            if (hasReceiver) {
                appendInvoke(InvokeKind.Virtual, (ResolvedJavaMethod) target, args);
            } else {
                appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
            }
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Virtual);
        }

    }

    protected void genInvokeSpecial(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            assert target != null;
            assert target.getSignature() != null;
            ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
            appendInvoke(InvokeKind.Special, (ResolvedJavaMethod) target, args);
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Special);
        }
    }

    private InvokeKind currentInvokeKind;
    private JavaType currentInvokeReturnType;
    protected FrameStateBuilder frameState;
    protected BciBlock currentBlock;
    protected final BytecodeStream stream;
    protected final GraphBuilderConfiguration graphBuilderConfig;
    protected final ResolvedJavaMethod method;
    protected final ProfilingInfo profilingInfo;
    protected final OptimisticOptimizations optimisticOpts;
    protected final ConstantPool constantPool;
    protected final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final StampProvider stampProvider;
    protected final IntrinsicContext intrinsicContext;

    public InvokeKind getInvokeKind() {
        return currentInvokeKind;
    }

    public JavaType getInvokeReturnType() {
        return currentInvokeReturnType;
    }

    private boolean forceInliningEverything;

    public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean inlineEverything) {
        boolean previous = forceInliningEverything;
        forceInliningEverything = previous || inlineEverything;
        try {
            appendInvoke(invokeKind, targetMethod, args);
        } finally {
            forceInliningEverything = previous;
        }
    }

    private void appendInvoke(InvokeKind initialInvokeKind, ResolvedJavaMethod initialTargetMethod, ValueNode[] args) {
        ResolvedJavaMethod targetMethod = initialTargetMethod;
        InvokeKind invokeKind = initialInvokeKind;
        if (initialInvokeKind.isIndirect()) {
            ResolvedJavaType contextType = this.frameState.getMethod().getDeclaringClass();
            ResolvedJavaMethod specialCallTarget = MethodCallTargetNode.findSpecialCallTarget(initialInvokeKind, args[0], initialTargetMethod, contextType);
            if (specialCallTarget != null) {
                invokeKind = InvokeKind.Special;
                targetMethod = specialCallTarget;
            }
        }

        JavaKind resultType = targetMethod.getSignature().getReturnKind();
        if (DeoptALot.getValue()) {
            append(new DeoptimizeNode(DeoptimizationAction.None, RuntimeConstraint));
            frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, graph));
            return;
        }

        JavaType returnType = targetMethod.getSignature().getReturnType(method.getDeclaringClass());
        if (graphBuilderConfig.eagerResolving() || parsingIntrinsic()) {
            returnType = returnType.resolve(targetMethod.getDeclaringClass());
        }
        if (invokeKind.hasReceiver()) {
            args[0] = emitExplicitExceptions(args[0], null);

            if (args[0].isNullConstant()) {
                append(new DeoptimizeNode(InvalidateRecompile, NullCheckException));
                return;
            }
        }

        InlineInfo inlineInfo = null;
        try {
            currentInvokeReturnType = returnType;
            currentInvokeKind = invokeKind;
            if (tryNodePluginForInvocation(args, targetMethod)) {
                if (TraceParserPlugins.getValue()) {
                    traceWithContext("used node plugin for %s", targetMethod.format("%h.%n(%p)"));
                }
                return;
            }

            if (invokeKind.isDirect()) {
                if (tryInvocationPlugin(args, targetMethod, resultType)) {
                    if (TraceParserPlugins.getValue()) {
                        traceWithContext("used invocation plugin for %s", targetMethod.format("%h.%n(%p)"));
                    }
                    return;
                }

                inlineInfo = tryInline(args, targetMethod, returnType);
                if (inlineInfo == SUCCESSFULLY_INLINED) {
                    return;
                }
            }
        } finally {
            currentInvokeReturnType = null;
            currentInvokeKind = null;
        }

        JavaTypeProfile profile = null;
        if (invokeKind.isIndirect() && profilingInfo != null && this.optimisticOpts.useTypeCheckHints()) {
            profile = profilingInfo.getTypeProfile(bci());
        }
        MethodCallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, targetMethod, args, returnType, profile));

        Invoke invoke;
        if (omitInvokeExceptionEdge(callTarget, inlineInfo)) {
            invoke = createInvoke(callTarget, resultType);
        } else {
            invoke = createInvokeWithException(callTarget, resultType);
            AbstractBeginNode beginNode = graph.add(new KillingBeginNode(LocationIdentity.any()));
            invoke.setNext(beginNode);
            lastInstr = beginNode;
        }

        for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            plugin.notifyNotInlined(this, targetMethod, invoke);
        }
    }

    /**
     * If the method returns true, the invocation of the given {@link MethodCallTargetNode call
     * target} does not need an exception edge.
     *
     * @param callTarget The call target.
     */
    protected boolean omitInvokeExceptionEdge(MethodCallTargetNode callTarget, InlineInfo lastInlineInfo) {
        if (lastInlineInfo == InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION) {
            return false;
        } else if (lastInlineInfo == InlineInfo.DO_NOT_INLINE_NO_EXCEPTION || graphBuilderConfig.omitAllExceptionEdges()) {
            return true;
        } else {
            // be conservative if information was not recorded (could result in endless
            // recompiles otherwise)
            return (!StressInvokeWithExceptionNode.getValue() && optimisticOpts.useExceptionProbability() && profilingInfo != null && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE);
        }
    }

    /**
     * Contains all the assertion checking logic around the application of an
     * {@link InvocationPlugin}. This class is only loaded when assertions are enabled.
     */
    class InvocationPluginAssertions {
        final InvocationPlugin plugin;
        final ValueNode[] args;
        final ResolvedJavaMethod targetMethod;
        final JavaKind resultType;
        final int beforeStackSize;
        final boolean needsNullCheck;
        final int nodeCount;
        final Mark mark;

        InvocationPluginAssertions(InvocationPlugin plugin, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            guarantee(assertionsEnabled(), "%s should only be loaded and instantiated if assertions are enabled", getClass().getSimpleName());
            this.plugin = plugin;
            this.targetMethod = targetMethod;
            this.args = args;
            this.resultType = resultType;
            this.beforeStackSize = frameState.stackSize();
            this.needsNullCheck = !targetMethod.isStatic() && args[0].getStackKind() == JavaKind.Object && !StampTool.isPointerNonNull(args[0].stamp());
            this.nodeCount = graph.getNodeCount();
            this.mark = graph.getMark();
        }

        String error(String format, Object... a) {
            return String.format(format, a) + String.format("%n\tplugin at %s", plugin.getApplySourceLocation(metaAccess));
        }

        boolean check(boolean pluginResult) {
            if (pluginResult == true) {
                int expectedStackSize = beforeStackSize + resultType.getSlotCount();
                assert expectedStackSize == frameState.stackSize() : error("plugin manipulated the stack incorrectly: expected=%d, actual=%d", expectedStackSize, frameState.stackSize());
                NodeIterable<Node> newNodes = graph.getNewNodes(mark);
                assert !needsNullCheck || isPointerNonNull(args[0].stamp()) : error("plugin needs to null check the receiver of %s: receiver=%s", targetMethod.format("%H.%n(%p)"), args[0]);
                for (Node n : newNodes) {
                    if (n instanceof StateSplit) {
                        StateSplit stateSplit = (StateSplit) n;
                        assert stateSplit.stateAfter() != null || !stateSplit.hasSideEffect() : error("%s node added by plugin for %s need to have a non-null frame state: %s",
                                        StateSplit.class.getSimpleName(), targetMethod.format("%H.%n(%p)"), stateSplit);
                    }
                }
                try {
                    graphBuilderConfig.getPlugins().getInvocationPlugins().checkNewNodes(BytecodeParser.this, plugin, newNodes);
                } catch (Throwable t) {
                    throw new AssertionError(error("Error in plugin"), t);
                }
            } else {
                assert nodeCount == graph.getNodeCount() : error("plugin that returns false must not create new nodes");
                assert beforeStackSize == frameState.stackSize() : error("plugin that returns false must not modify the stack");
            }
            return true;
        }
    }

    private boolean tryInvocationPlugin(ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
        InvocationPlugin plugin = graphBuilderConfig.getPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
        if (plugin != null) {

            if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
                // Self recursive intrinsic means the original
                // method should be called.
                assert !targetMethod.hasBytecodes() : "TODO: when does this happen?";
                return false;
            }

            InvocationPluginAssertions assertions = assertionsEnabled() ? new InvocationPluginAssertions(plugin, args, targetMethod, resultType) : null;
            if (plugin.execute(this, targetMethod, invocationPluginReceiver.init(targetMethod, args), args)) {
                assert assertions.check(true);
                return true;
            }
            assert assertions.check(false);
        }
        return false;
    }

    private boolean tryNodePluginForInvocation(ValueNode[] args, ResolvedJavaMethod targetMethod) {
        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleInvoke(this, targetMethod, args)) {
                return true;
            }
        }
        return false;
    }

    private static final InlineInfo SUCCESSFULLY_INLINED = new InlineInfo(null, false);

    /**
     * Try to inline a method. If the method was inlined, returns {@link #SUCCESSFULLY_INLINED}.
     * Otherwise, it returns the {@link InlineInfo} that lead to the decision to not inline it, or
     * {@code null} if there is no {@link InlineInfo} for this method.
     */
    private InlineInfo tryInline(ValueNode[] args, ResolvedJavaMethod targetMethod, JavaType returnType) {
        boolean canBeInlined = forceInliningEverything || parsingIntrinsic() || targetMethod.canBeInlined();
        if (!canBeInlined) {
            return null;
        }

        if (forceInliningEverything) {
            if (inline(targetMethod, targetMethod, false, args)) {
                return SUCCESSFULLY_INLINED;
            } else {
                return null;
            }
        }

        for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            InlineInfo inlineInfo = plugin.shouldInlineInvoke(this, targetMethod, args, returnType);
            if (inlineInfo != null) {
                if (inlineInfo.getMethodToInline() != null) {
                    if (inline(targetMethod, inlineInfo.getMethodToInline(), inlineInfo.isIntrinsic(), args)) {
                        return SUCCESSFULLY_INLINED;
                    }
                }
                /* Do not inline, and do not ask the remaining plugins. */
                return inlineInfo;
            }
        }
        return null;
    }

    public void intrinsify(ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, ValueNode[] args) {
        boolean res = inline(targetMethod, substitute, true, args);
        assert res : "failed to inline " + substitute;
    }

    private boolean inline(ResolvedJavaMethod targetMethod, ResolvedJavaMethod inlinedMethod, boolean isIntrinsic, ValueNode[] args) {
        if (TraceInlineDuringParsing.getValue() || TraceParserPlugins.getValue()) {
            if (targetMethod.equals(inlinedMethod)) {
                traceWithContext("inlining call to %s", inlinedMethod.format("%h.%n(%p)"));
            } else {
                traceWithContext("inlining call to %s as intrinsic for %s", inlinedMethod.format("%h.%n(%p)"), targetMethod.format("%h.%n(%p)"));
            }
        }
        IntrinsicContext intrinsic = this.intrinsicContext;
        if (intrinsic != null && intrinsic.isCallToOriginal(targetMethod)) {
            if (intrinsic.isCompilationRoot()) {
                // A root compiled intrinsic needs to deoptimize
                // if the slow path is taken. During frame state
                // assignment, the deopt node will get its stateBefore
                // from the start node of the intrinsic
                append(new DeoptimizeNode(InvalidateRecompile, RuntimeConstraint));
                return true;
            } else {
                // Otherwise inline the original method. Any frame state created
                // during the inlining will exclude frame(s) in the
                // intrinsic method (see HIRFrameStateBuilder.create(int bci)).
                if (intrinsic.getOriginalMethod().isNative()) {
                    return false;
                }
                parseAndInlineCallee(intrinsic.getOriginalMethod(), args, null);
                return true;
            }
        } else {
            if (intrinsic == null && isIntrinsic) {
                assert !inlinedMethod.equals(targetMethod);
                intrinsic = new IntrinsicContext(targetMethod, inlinedMethod, INLINE_DURING_PARSING);
            }
            if (inlinedMethod.hasBytecodes()) {
                for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
                    plugin.notifyBeforeInline(inlinedMethod);
                }
                parseAndInlineCallee(inlinedMethod, args, intrinsic);
                for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
                    plugin.notifyAfterInline(inlinedMethod);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Prints a line to {@link TTY} with a prefix indicating the current parse context. The prefix
     * is of the form:
     *
     * <pre>
     * {SPACE * n} {name of method being parsed} "(" {file name} ":" {line number} ")"
     * </pre>
     *
     * where {@code n} is the current inlining depth.
     *
     * @param format a format string
     * @param args arguments to the format string
     */

    protected void traceWithContext(String format, Object... args) {
        StackTraceElement where = method.asStackTraceElement(bci());
        TTY.println(format("%s%s (%s:%d) %s", nSpaces(getDepth()), method.isConstructor() ? method.format("%h.%n") : method.getName(), where.getFileName(), where.getLineNumber(), format(format, args)));
    }

    protected BytecodeParserError asParserError(Throwable e) {
        if (e instanceof BytecodeParserError) {
            return (BytecodeParserError) e;
        }
        BytecodeParser bp = this;
        BytecodeParserError res = new BytecodeParserError(e);
        while (bp != null) {
            res.addContext("parsing " + bp.method.asStackTraceElement(bp.bci()));
            bp = bp.parent;
        }
        return res;
    }

    @SuppressWarnings("try")
    protected void parseAndInlineCallee(ResolvedJavaMethod targetMethod, ValueNode[] args, IntrinsicContext calleeIntrinsicContext) {
        try (IntrinsicScope s = calleeIntrinsicContext != null && !parsingIntrinsic() ? new IntrinsicScope(this, targetMethod.getSignature().toParameterKinds(!targetMethod.isStatic()), args) : null) {

            BytecodeParser parser = graphBuilderInstance.createBytecodeParser(graph, this, targetMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, calleeIntrinsicContext);
            FrameStateBuilder startFrameState = new FrameStateBuilder(parser, targetMethod, graph);
            if (!targetMethod.isStatic()) {
                args[0] = nullCheckedValue(args[0]);
            }
            startFrameState.initializeFromArgumentsArray(args);
            parser.build(this.lastInstr, startFrameState);

            FixedWithNextNode calleeBeforeReturnNode = parser.getBeforeReturnNode();
            this.lastInstr = calleeBeforeReturnNode;
            JavaKind calleeReturnKind = targetMethod.getSignature().getReturnKind();
            if (calleeBeforeReturnNode != null) {
                ValueNode calleeReturnValue = parser.getReturnValue();
                if (calleeReturnValue != null) {
                    frameState.push(calleeReturnKind.getStackKind(), calleeReturnValue);
                }
            }

            FixedWithNextNode calleeBeforeUnwindNode = parser.getBeforeUnwindNode();
            if (calleeBeforeUnwindNode != null) {
                ValueNode calleeUnwindValue = parser.getUnwindValue();
                assert calleeUnwindValue != null;
                calleeBeforeUnwindNode.setNext(handleException(calleeUnwindValue, bci()));
            }

            // Record inlined method dependency in the graph
            graph.recordInlinedMethod(targetMethod);
        }
    }

    public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, JavaType returnType, JavaTypeProfile profile) {
        return new MethodCallTargetNode(invokeKind, targetMethod, args, returnType, profile);
    }

    protected InvokeNode createInvoke(CallTargetNode callTarget, JavaKind resultType) {
        InvokeNode invoke = append(new InvokeNode(callTarget, bci()));
        frameState.pushReturn(resultType, invoke);
        invoke.setStateAfter(createFrameState(stream.nextBCI(), invoke));
        return invoke;
    }

    protected InvokeWithExceptionNode createInvokeWithException(CallTargetNode callTarget, JavaKind resultType) {
        if (currentBlock != null && stream.nextBCI() > currentBlock.endBci) {
            /*
             * Clear non-live locals early so that the exception handler entry gets the cleared
             * state.
             */
            frameState.clearNonLiveLocals(currentBlock, liveness, false);
        }

        AbstractBeginNode exceptionEdge = handleException(null, bci());
        InvokeWithExceptionNode invoke = append(new InvokeWithExceptionNode(callTarget, exceptionEdge, bci()));
        frameState.pushReturn(resultType, invoke);
        invoke.setStateAfter(createFrameState(stream.nextBCI(), invoke));
        return invoke;
    }

    protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
        if (parsingIntrinsic() && returnVal != null) {
            if (returnVal instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) returnVal;
                FrameState stateAfter = stateSplit.stateAfter();
                if (stateSplit.hasSideEffect()) {
                    assert stateSplit != null;
                    if (stateAfter.bci == BytecodeFrame.AFTER_BCI) {
                        assert stateAfter.usages().count() == 1;
                        assert stateAfter.usages().first() == stateSplit;
                        stateAfter.replaceAtUsages(graph.add(new FrameState(BytecodeFrame.AFTER_BCI, returnVal)));
                        GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                    } else {
                        /*
                         * This must be the return value from within a partial intrinsification.
                         */
                        assert !BytecodeFrame.isPlaceholderBci(stateAfter.bci);
                    }
                } else {
                    assert stateAfter == null;
                }
            }
        }
        if (parent == null) {
            frameState.setRethrowException(false);
            frameState.clearStack();
            beforeReturn(returnVal, returnKind);
            append(new ReturnNode(returnVal));
        } else {
            if (blockMap.getReturnCount() == 1 || !controlFlowSplit) {
                // There is only a single return.
                beforeReturn(returnVal, returnKind);
                this.returnValue = returnVal;
                this.beforeReturnNode = this.lastInstr;
                this.lastInstr = null;
            } else {
                frameState.setRethrowException(false);
                frameState.clearStack();
                if (returnVal != null) {
                    frameState.push(returnKind, returnVal);
                }
                assert blockMap.getReturnCount() > 1;
                appendGoto(blockMap.getReturnBlock());
            }
        }
    }

    private void beforeReturn(ValueNode x, JavaKind kind) {
        if (graph.method() != null && graph.method().isJavaLangObjectInit()) {
            ValueNode receiver = frameState.loadLocal(0, JavaKind.Object);
            if (RegisterFinalizerNode.mayHaveFinalizer(receiver, graph.getAssumptions())) {
                append(new RegisterFinalizerNode(receiver));
            }
        }
        if (GraalOptions.OldInfopoints.getValue() && graphBuilderConfig.insertNonSafepointDebugInfo() && !parsingIntrinsic()) {
            genInfoPointNode(InfopointReason.METHOD_END, x);
        }

        synchronizedEpilogue(BytecodeFrame.AFTER_BCI, x, kind);
    }

    protected MonitorEnterNode createMonitorEnterNode(ValueNode x, MonitorIdNode monitorId) {
        return new MonitorEnterNode(x, monitorId);
    }

    protected void genMonitorEnter(ValueNode x, int bci) {
        MonitorIdNode monitorId = graph.add(new MonitorIdNode(frameState.lockDepth(true)));
        MonitorEnterNode monitorEnter = append(createMonitorEnterNode(x, monitorId));
        frameState.pushLock(x, monitorId);
        monitorEnter.setStateAfter(createFrameState(bci, monitorEnter));
    }

    protected void genMonitorExit(ValueNode x, ValueNode escapedReturnValue, int bci) {
        if (frameState.lockDepth(false) == 0) {
            throw bailout("unbalanced monitors: too many exits");
        }
        MonitorIdNode monitorId = frameState.peekMonitorId();
        ValueNode lockedObject = frameState.popLock();
        if (GraphUtil.originalValue(lockedObject) != GraphUtil.originalValue(x)) {
            throw bailout(String.format("unbalanced monitors: mismatch at monitorexit, %s != %s", GraphUtil.originalValue(x), GraphUtil.originalValue(lockedObject)));
        }
        MonitorExitNode monitorExit = append(new MonitorExitNode(x, monitorId, escapedReturnValue));
        monitorExit.setStateAfter(createFrameState(bci, monitorExit));
    }

    protected void genJsr(int dest) {
        BciBlock successor = currentBlock.getJsrSuccessor();
        assert successor.startBci == dest : successor.startBci + " != " + dest + " @" + bci();
        JsrScope scope = currentBlock.getJsrScope();
        int nextBci = getStream().nextBCI();
        if (!successor.getJsrScope().pop().equals(scope)) {
            throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
        }
        if (successor.getJsrScope().nextReturnAddress() != nextBci) {
            throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
        }
        ConstantNode nextBciNode = getJsrConstant(nextBci);
        frameState.push(JavaKind.Object, nextBciNode);
        appendGoto(successor);
    }

    protected void genRet(int localIndex) {
        BciBlock successor = currentBlock.getRetSuccessor();
        ValueNode local = frameState.loadLocal(localIndex, JavaKind.Object);
        JsrScope scope = currentBlock.getJsrScope();
        int retAddress = scope.nextReturnAddress();
        ConstantNode returnBciNode = getJsrConstant(retAddress);
        LogicNode guard = IntegerEqualsNode.create(local, returnBciNode, constantReflection);
        guard = graph.unique(guard);
        append(new FixedGuardNode(guard, JavaSubroutineMismatch, InvalidateReprofile));
        if (!successor.getJsrScope().equals(scope.pop())) {
            throw new JsrNotSupportedBailout("unstructured control flow (ret leaves more than one scope)");
        }
        appendGoto(successor);
    }

    private ConstantNode getJsrConstant(long bci) {
        JavaConstant nextBciConstant = new RawConstant(bci);
        Stamp nextBciStamp = StampFactory.forConstant(nextBciConstant);
        ConstantNode nextBciNode = new ConstantNode(nextBciConstant, nextBciStamp);
        return graph.unique(nextBciNode);
    }

    protected void genIntegerSwitch(ValueNode value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        if (value.isConstant()) {
            JavaConstant constant = (JavaConstant) value.asConstant();
            int constantValue = constant.asInt();
            for (int i = 0; i < keys.length; ++i) {
                if (keys[i] == constantValue) {
                    appendGoto(actualSuccessors.get(keySuccessors[i]));
                    return;
                }
            }
            appendGoto(actualSuccessors.get(keySuccessors[keys.length]));
        } else {
            this.controlFlowSplit = true;
            double[] successorProbabilities = successorProbabilites(actualSuccessors.size(), keySuccessors, keyProbabilities);
            IntegerSwitchNode switchNode = append(new IntegerSwitchNode(value, actualSuccessors.size(), keys, keyProbabilities, keySuccessors));
            for (int i = 0; i < actualSuccessors.size(); i++) {
                switchNode.setBlockSuccessor(i, createBlockTarget(successorProbabilities[i], actualSuccessors.get(i), frameState));
            }
        }
    }

    /**
     * Helper function that sums up the probabilities of all keys that lead to a specific successor.
     *
     * @return an array of size successorCount with the accumulated probability for each successor.
     */
    private static double[] successorProbabilites(int successorCount, int[] keySuccessors, double[] keyProbabilities) {
        double[] probability = new double[successorCount];
        for (int i = 0; i < keySuccessors.length; i++) {
            probability[keySuccessors[i]] += keyProbabilities[i];
        }
        return probability;
    }

    protected ConstantNode appendConstant(JavaConstant constant) {
        assert constant != null;
        return ConstantNode.forConstant(constant, metaAccess, graph);
    }

    @Override
    public <T extends ValueNode> T append(T v) {
        if (v.graph() != null) {
            return v;
        }
        T added = graph.addOrUnique(v);
        if (added == v) {
            updateLastInstruction(v);
        }
        return added;
    }

    public <T extends ValueNode> T recursiveAppend(T v) {
        if (v.graph() != null) {
            return v;
        }
        T added = graph.addOrUniqueWithInputs(v);
        if (added == v) {
            updateLastInstruction(v);
        }
        return added;
    }

    private <T extends ValueNode> void updateLastInstruction(T v) {
        if (v instanceof FixedNode) {
            FixedNode fixedNode = (FixedNode) v;
            lastInstr.setNext(fixedNode);
            if (fixedNode instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                lastInstr = fixedWithNextNode;
            } else {
                lastInstr = null;
            }
        }
    }

    private Target checkLoopExit(FixedNode target, BciBlock targetBlock, FrameStateBuilder state) {
        if (currentBlock != null) {
            long exits = currentBlock.loops & ~targetBlock.loops;
            if (exits != 0) {
                LoopExitNode firstLoopExit = null;
                LoopExitNode lastLoopExit = null;

                int pos = 0;
                ArrayList<BciBlock> exitLoops = new ArrayList<>(Long.bitCount(exits));
                do {
                    long lMask = 1L << pos;
                    if ((exits & lMask) != 0) {
                        exitLoops.add(blockMap.getLoopHeader(pos));
                        exits &= ~lMask;
                    }
                    pos++;
                } while (exits != 0);

                Collections.sort(exitLoops, new Comparator<BciBlock>() {

                    @Override
                    public int compare(BciBlock o1, BciBlock o2) {
                        return Long.bitCount(o2.loops) - Long.bitCount(o1.loops);
                    }
                });

                int bci = targetBlock.startBci;
                if (targetBlock instanceof ExceptionDispatchBlock) {
                    bci = ((ExceptionDispatchBlock) targetBlock).deoptBci;
                }
                FrameStateBuilder newState = state.copy();
                for (BciBlock loop : exitLoops) {
                    LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(loop);
                    LoopExitNode loopExit = graph.add(new LoopExitNode(loopBegin));
                    if (lastLoopExit != null) {
                        lastLoopExit.setNext(loopExit);
                    }
                    if (firstLoopExit == null) {
                        firstLoopExit = loopExit;
                    }
                    lastLoopExit = loopExit;
                    Debug.log("Target %s Exits %s, scanning framestates...", targetBlock, loop);
                    newState.insertLoopProxies(loopExit, getEntryState(loop));
                    loopExit.setStateAfter(newState.create(bci, loopExit));
                }

                lastLoopExit.setNext(target);
                return new Target(firstLoopExit, newState);
            }
        }
        return new Target(target, state);
    }

    private FrameStateBuilder getEntryState(BciBlock block) {
        return entryStateArray[block.id];
    }

    private void setEntryState(BciBlock block, FrameStateBuilder entryState) {
        this.entryStateArray[block.id] = entryState;
    }

    private void setFirstInstruction(BciBlock block, FixedWithNextNode firstInstruction) {
        this.firstInstructionArray[block.id] = firstInstruction;
    }

    private FixedWithNextNode getFirstInstruction(BciBlock block) {
        return firstInstructionArray[block.id];
    }

    private FixedNode createTarget(double probability, BciBlock block, FrameStateBuilder stateAfter) {
        assert probability >= 0 && probability <= 1.01 : probability;
        if (isNeverExecutedCode(probability)) {
            return graph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
        } else {
            assert block != null;
            return createTarget(block, stateAfter);
        }
    }

    private FixedNode createTarget(BciBlock block, FrameStateBuilder state) {
        return createTarget(block, state, false, false);
    }

    private FixedNode createTarget(BciBlock block, FrameStateBuilder state, boolean canReuseInstruction, boolean canReuseState) {
        assert block != null && state != null;
        assert !block.isExceptionEntry || state.stackSize() == 1;

        if (getFirstInstruction(block) == null) {
            /*
             * This is the first time we see this block as a branch target. Create and return a
             * placeholder that later can be replaced with a MergeNode when we see this block again.
             */
            FixedNode targetNode;
            if (canReuseInstruction && (block.getPredecessorCount() == 1 || !controlFlowSplit) && !block.isLoopHeader && (currentBlock.loops & ~block.loops) == 0) {
                setFirstInstruction(block, lastInstr);
                lastInstr = null;
            } else {
                setFirstInstruction(block, graph.add(new BeginNode()));
            }
            targetNode = getFirstInstruction(block);
            Target target = checkLoopExit(targetNode, block, state);
            FixedNode result = target.fixed;
            FrameStateBuilder currentEntryState = target.state == state ? (canReuseState ? state : state.copy()) : target.state;
            setEntryState(block, currentEntryState);
            currentEntryState.clearNonLiveLocals(block, liveness, true);

            Debug.log("createTarget %s: first visit, result: %s", block, targetNode);
            return result;
        }

        // We already saw this block before, so we have to merge states.
        if (!getEntryState(block).isCompatibleWith(state)) {
            throw bailout("stacks do not match; bytecodes would not verify");
        }

        if (getFirstInstruction(block) instanceof LoopBeginNode) {
            assert (block.isLoopHeader && currentBlock.getId() >= block.getId()) : "must be backward branch";
            /*
             * Backward loop edge. We need to create a special LoopEndNode and merge with the loop
             * begin node created before.
             */
            LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(block);
            LoopEndNode loopEnd = graph.add(new LoopEndNode(loopBegin));
            Target target = checkLoopExit(loopEnd, block, state);
            FixedNode result = target.fixed;
            getEntryState(block).merge(loopBegin, target.state);

            Debug.log("createTarget %s: merging backward branch to loop header %s, result: %s", block, loopBegin, result);
            return result;
        }
        assert currentBlock == null || currentBlock.getId() < block.getId() : "must not be backward branch";
        assert getFirstInstruction(block).next() == null : "bytecodes already parsed for block";

        if (getFirstInstruction(block) instanceof AbstractBeginNode && !(getFirstInstruction(block) instanceof AbstractMergeNode)) {
            /*
             * This is the second time we see this block. Create the actual MergeNode and the End
             * Node for the already existing edge.
             */
            AbstractBeginNode beginNode = (AbstractBeginNode) getFirstInstruction(block);

            // The EndNode for the already existing edge.
            EndNode end = graph.add(new EndNode());
            // The MergeNode that replaces the placeholder.
            AbstractMergeNode mergeNode = graph.add(new MergeNode());
            FixedNode next = beginNode.next();

            if (beginNode.predecessor() instanceof ControlSplitNode) {
                beginNode.setNext(end);
            } else {
                beginNode.replaceAtPredecessor(end);
                beginNode.safeDelete();
            }

            mergeNode.addForwardEnd(end);
            mergeNode.setNext(next);

            setFirstInstruction(block, mergeNode);
        }

        AbstractMergeNode mergeNode = (AbstractMergeNode) getFirstInstruction(block);

        // The EndNode for the newly merged edge.
        EndNode newEnd = graph.add(new EndNode());
        Target target = checkLoopExit(newEnd, block, state);
        FixedNode result = target.fixed;
        getEntryState(block).merge(mergeNode, target.state);
        mergeNode.addForwardEnd(newEnd);

        Debug.log("createTarget %s: merging state, result: %s", block, result);
        return result;
    }

    /**
     * Returns a block begin node with the specified state. If the specified probability is 0, the
     * block deoptimizes immediately.
     */
    private AbstractBeginNode createBlockTarget(double probability, BciBlock block, FrameStateBuilder stateAfter) {
        FixedNode target = createTarget(probability, block, stateAfter);
        AbstractBeginNode begin = BeginNode.begin(target);

        assert !(target instanceof DeoptimizeNode && begin instanceof BeginStateSplitNode && ((BeginStateSplitNode) begin).stateAfter() != null) : "We are not allowed to set the stateAfter of the begin node, because we have to deoptimize "
                        + "to a bci _before_ the actual if, so that the interpreter can update the profiling information.";
        return begin;
    }

    private ValueNode synchronizedObject(FrameStateBuilder state, ResolvedJavaMethod target) {
        if (target.isStatic()) {
            return appendConstant(getConstantReflection().asJavaClass(target.getDeclaringClass()));
        } else {
            return state.loadLocal(0, JavaKind.Object);
        }
    }

    @SuppressWarnings("try")
    protected void processBlock(BciBlock block) {
        // Ignore blocks that have no predecessors by the time their bytecodes are parsed
        FixedWithNextNode firstInstruction = getFirstInstruction(block);
        if (firstInstruction == null) {
            Debug.log("Ignoring block %s", block);
            return;
        }
        try (Indent indent = Debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, firstInstruction, block.isLoopHeader)) {

            lastInstr = firstInstruction;
            frameState = getEntryState(block);
            setCurrentFrameState(frameState);
            currentBlock = block;

            if (firstInstruction instanceof AbstractMergeNode) {
                setMergeStateAfter(block, firstInstruction);
            }

            if (block == blockMap.getReturnBlock()) {
                handleReturnBlock();
            } else if (block == blockMap.getUnwindBlock()) {
                handleUnwindBlock();
            } else if (block instanceof ExceptionDispatchBlock) {
                createExceptionDispatch((ExceptionDispatchBlock) block);
            } else {
                frameState.setRethrowException(false);
                iterateBytecodesForBlock(block);
            }
        }
    }

    private void handleUnwindBlock() {
        if (parent == null) {
            frameState.setRethrowException(false);
            createUnwind();
        } else {
            ValueNode exception = frameState.pop(JavaKind.Object);
            this.unwindValue = exception;
            this.beforeUnwindNode = this.lastInstr;
        }
    }

    private void handleReturnBlock() {
        JavaKind returnKind = method.getSignature().getReturnKind().getStackKind();
        ValueNode x = returnKind == JavaKind.Void ? null : frameState.pop(returnKind);
        assert frameState.stackSize() == 0;
        beforeReturn(x, returnKind);
        this.returnValue = x;
        this.beforeReturnNode = this.lastInstr;
    }

    private void setMergeStateAfter(BciBlock block, FixedWithNextNode firstInstruction) {
        AbstractMergeNode abstractMergeNode = (AbstractMergeNode) firstInstruction;
        if (abstractMergeNode.stateAfter() == null) {
            int bci = block.startBci;
            if (block instanceof ExceptionDispatchBlock) {
                bci = ((ExceptionDispatchBlock) block).deoptBci;
            }
            abstractMergeNode.setStateAfter(createFrameState(bci, abstractMergeNode));
        }
    }

    private void createUnwind() {
        assert frameState.stackSize() == 1 : frameState;
        ValueNode exception = frameState.pop(JavaKind.Object);
        synchronizedEpilogue(BytecodeFrame.AFTER_EXCEPTION_BCI, null, null);
        append(new UnwindNode(exception));
    }

    private void synchronizedEpilogue(int bci, ValueNode currentReturnValue, JavaKind currentReturnValueKind) {
        if (method.isSynchronized()) {
            if (currentReturnValue != null) {
                frameState.push(currentReturnValueKind, currentReturnValue);
            }
            genMonitorExit(methodSynchronizedObject, currentReturnValue, bci);
            assert !frameState.rethrowException();
        }
        if (frameState.lockDepth(false) != 0) {
            throw bailout("unbalanced monitors: too few exits exiting frame");
        }
    }

    private void createExceptionDispatch(ExceptionDispatchBlock block) {
        assert frameState.stackSize() == 1 : frameState;
        if (block.handler.isCatchAll()) {
            assert block.getSuccessorCount() == 1;
            appendGoto(block.getSuccessor(0));
            return;
        }

        JavaType catchType = block.handler.getCatchType();
        if (graphBuilderConfig.eagerResolving()) {
            catchType = lookupType(block.handler.catchTypeCPI(), INSTANCEOF);
        }
        if (catchType instanceof ResolvedJavaType) {
            CheckedJavaType checkedCatchType = CheckedJavaType.create(graph.getAssumptions(), (ResolvedJavaType) catchType);

            if (graphBuilderConfig.getSkippedExceptionTypes() != null) {
                for (ResolvedJavaType skippedType : graphBuilderConfig.getSkippedExceptionTypes()) {
                    if (skippedType.isAssignableFrom(checkedCatchType.getType())) {
                        BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                        ValueNode exception = frameState.stack[0];
                        FixedNode trueSuccessor = graph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                        FixedNode nextDispatch = createTarget(nextBlock, frameState);
                        append(new IfNode(graph.unique(InstanceOfNode.create(checkedCatchType, exception, null)), trueSuccessor, nextDispatch, 0));
                        return;
                    }
                }
            }

            BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
            ValueNode exception = frameState.stack[0];
            CheckCastNode checkCast = graph.add(new CheckCastNode(checkedCatchType, exception, null, false));
            frameState.pop(JavaKind.Object);
            frameState.push(JavaKind.Object, checkCast);
            FixedNode catchSuccessor = createTarget(block.getSuccessor(0), frameState);
            frameState.pop(JavaKind.Object);
            frameState.push(JavaKind.Object, exception);
            FixedNode nextDispatch = createTarget(nextBlock, frameState);
            checkCast.setNext(catchSuccessor);
            append(new IfNode(graph.unique(InstanceOfNode.create(checkedCatchType, exception, null)), checkCast, nextDispatch, 0.5));
        } else {
            handleUnresolvedExceptionType(catchType);
        }
    }

    private void appendGoto(BciBlock successor) {
        FixedNode targetInstr = createTarget(successor, frameState, true, true);
        if (lastInstr != null && lastInstr != targetInstr) {
            lastInstr.setNext(targetInstr);
        }
    }

    @SuppressWarnings("try")
    protected void iterateBytecodesForBlock(BciBlock block) {
        if (block.isLoopHeader) {
            // Create the loop header block, which later will merge the backward branches of
            // the loop.
            controlFlowSplit = true;
            LoopBeginNode loopBegin = appendLoopBegin(this.lastInstr);
            lastInstr = loopBegin;

            // Create phi functions for all local variables and operand stack slots.
            frameState.insertLoopPhis(liveness, block.loopId, loopBegin, forceLoopPhis(), stampFromValueForForcedPhis());
            loopBegin.setStateAfter(createFrameState(block.startBci, loopBegin));

            /*
             * We have seen all forward branches. All subsequent backward branches will merge to the
             * loop header. This ensures that the loop header has exactly one non-loop predecessor.
             */
            setFirstInstruction(block, loopBegin);
            /*
             * We need to preserve the frame state builder of the loop header so that we can merge
             * values for phi functions, so make a copy of it.
             */
            setEntryState(block, frameState.copy());

            Debug.log("  created loop header %s", loopBegin);
        } else if (lastInstr instanceof MergeNode) {
            /*
             * All inputs of non-loop phi nodes are known by now. We can infer the stamp for the
             * phi, so that parsing continues with more precise type information.
             */
            frameState.inferPhiStamps((AbstractMergeNode) lastInstr);
        }
        assert lastInstr.next() == null : "instructions already appended at block " + block;
        Debug.log("  frameState: %s", frameState);

        lastInstr = finishInstruction(lastInstr, frameState);

        int endBCI = stream.endBCI();

        stream.setBCI(block.startBci);
        int bci = block.startBci;
        BytecodesParsed.add(block.endBci - bci);

        /* Reset line number for new block */
        if (graphBuilderConfig.insertSimpleDebugInfo()) {
            previousLineNumber = -1;
        }

        while (bci < endBCI) {
            if (GraalOptions.OldInfopoints.getValue() && graphBuilderConfig.insertNonSafepointDebugInfo() && !parsingIntrinsic()) {
                currentLineNumber = lnt != null ? lnt.getLineNumber(bci) : (graphBuilderConfig.insertFullDebugInfo() ? -1 : bci);
                if (currentLineNumber != previousLineNumber) {
                    genInfoPointNode(InfopointReason.BYTECODE_POSITION, null);
                    previousLineNumber = currentLineNumber;
                }
            }

            // read the opcode
            int opcode = stream.currentBC();
            assert traceState();
            assert traceInstruction(bci, opcode, bci == block.startBci);
            if (parent == null && bci == entryBCI) {
                if (block.getJsrScope() != JsrScope.EMPTY_SCOPE) {
                    throw new BailoutException("OSR into a JSR scope is not supported");
                }
                EntryMarkerNode x = append(new EntryMarkerNode());
                frameState.insertProxies(value -> graph.unique(new EntryProxyNode(value, x)));
                x.setStateAfter(createFrameState(bci, x));
            }

            try (DebugCloseable context = openNodeContext()) {
                processBytecode(bci, opcode);
            } catch (BailoutException e) {
                // Don't wrap bailouts as parser errors
                throw e;
            } catch (Throwable e) {
                throw asParserError(e);
            }

            if (lastInstr == null || lastInstr.next() != null) {
                break;
            }

            stream.next();
            bci = stream.currentBCI();

            assert block == currentBlock;
            assert checkLastInstruction();
            lastInstr = finishInstruction(lastInstr, frameState);
            if (bci < endBCI) {
                if (bci > block.endBci) {
                    assert !block.getSuccessor(0).isExceptionEntry;
                    assert block.numNormalSuccessors() == 1;
                    // we fell through to the next block, add a goto and break
                    appendGoto(block.getSuccessor(0));
                    break;
                }
            }
        }
    }

    private DebugCloseable openNodeContext() {
        if (NewInfopoints.getValue() && graphBuilderConfig.insertNonSafepointDebugInfo() && !parsingIntrinsic()) {
            return graph.withNodeContext(createBytecodePosition());
        }
        return null;
    }

    /* Also a hook for subclasses. */
    protected boolean forceLoopPhis() {
        return graph.isOSR();
    }

    /* Hook for subclasses. */
    protected boolean stampFromValueForForcedPhis() {
        return false;
    }

    protected boolean checkLastInstruction() {
        if (lastInstr instanceof BeginNode) {
            // ignore
        } else if (lastInstr instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) lastInstr;
            if (stateSplit.hasSideEffect()) {
                assert stateSplit.stateAfter() != null : "side effect " + lastInstr + " requires a non-null stateAfter";
            }
        }
        return true;
    }

    /* Also a hook for subclasses. */
    protected boolean disableLoopSafepoint() {
        return parsingIntrinsic();
    }

    private LoopBeginNode appendLoopBegin(FixedWithNextNode fixedWithNext) {
        EndNode preLoopEnd = graph.add(new EndNode());
        LoopBeginNode loopBegin = graph.add(new LoopBeginNode());
        if (disableLoopSafepoint()) {
            loopBegin.disableSafepoint();
        }
        fixedWithNext.setNext(preLoopEnd);
        // Add the single non-loop predecessor of the loop header.
        loopBegin.addForwardEnd(preLoopEnd);
        return loopBegin;
    }

    /**
     * Hook for subclasses to modify the last instruction or add other instructions.
     *
     * @param instr The last instruction (= fixed node) which was added.
     * @param state The current frame state.
     * @return Returns the (new) last instruction.
     */
    protected FixedWithNextNode finishInstruction(FixedWithNextNode instr, FrameStateBuilder state) {
        return instr;
    }

    private void genInfoPointNode(InfopointReason reason, ValueNode escapedReturnValue) {
        if (!parsingIntrinsic()) {
            if (graphBuilderConfig.insertFullDebugInfo()) {
                append(new FullInfopointNode(reason, createFrameState(bci(), null), escapedReturnValue));
            } else {
                BytecodePosition position = createBytecodePosition();
                // Update the previous infopoint position if no new fixed nodes were inserted
                if (lastInstr instanceof SimpleInfopointNode) {
                    SimpleInfopointNode lastInfopoint = (SimpleInfopointNode) lastInstr;
                    if (lastInfopoint.getReason() == reason) {
                        lastInfopoint.setPosition(position);
                    }
                }
                append(new SimpleInfopointNode(reason, position));
            }
        }
    }

    private boolean traceState() {
        if (Debug.isEnabled() && BytecodeParserOptions.TraceBytecodeParserLevel.getValue() >= TRACELEVEL_STATE && Debug.isLogEnabled()) {
            frameState.traceState();
        }
        return true;
    }

    protected void genIf(ValueNode x, Condition cond, ValueNode y) {
        assert x.getStackKind() == y.getStackKind();
        assert currentBlock.getSuccessorCount() == 2;
        BciBlock trueBlock = currentBlock.getSuccessor(0);
        BciBlock falseBlock = currentBlock.getSuccessor(1);
        if (trueBlock == falseBlock) {
            // The target block is the same independent of the condition.
            appendGoto(trueBlock);
            return;
        }

        ValueNode a = x;
        ValueNode b = y;

        // Check whether the condition needs to mirror the operands.
        if (cond.canonicalMirror()) {
            a = y;
            b = x;
        }

        // Create the logic node for the condition.
        LogicNode condition = createLogicNode(cond, a, b);

        // Check whether the condition needs to negate the result.
        boolean negate = cond.canonicalNegate();

        // Remove a logic negation node and fold it into the negate boolean.
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode logicNegationNode = (LogicNegationNode) condition;
            negate = !negate;
            condition = logicNegationNode.getValue();
        }

        if (condition instanceof LogicConstantNode) {
            genConstantTargetIf(trueBlock, falseBlock, negate, condition);
        } else {
            if (condition.graph() == null) {
                condition = graph.unique(condition);
            }

            // Need to get probability based on current bci.
            double probability = branchProbability();

            if (negate) {
                BciBlock tmpBlock = trueBlock;
                trueBlock = falseBlock;
                falseBlock = tmpBlock;
                probability = 1 - probability;
            }

            if (isNeverExecutedCode(probability)) {
                append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, true));
                appendGoto(falseBlock);
                return;
            } else if (isNeverExecutedCode(1 - probability)) {
                append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, false));
                appendGoto(trueBlock);
                return;
            }

            int oldBci = stream.currentBCI();
            int trueBlockInt = checkPositiveIntConstantPushed(trueBlock);
            if (trueBlockInt != -1) {
                int falseBlockInt = checkPositiveIntConstantPushed(falseBlock);
                if (falseBlockInt != -1) {
                    if (tryGenConditionalForIf(trueBlock, falseBlock, condition, oldBci, trueBlockInt, falseBlockInt)) {
                        return;
                    }
                }
            }

            this.controlFlowSplit = true;
            FixedNode trueSuccessor = createTarget(trueBlock, frameState, false, false);
            FixedNode falseSuccessor = createTarget(falseBlock, frameState, false, true);
            ValueNode ifNode = genIfNode(condition, trueSuccessor, falseSuccessor, probability);
            append(ifNode);
            if (parsingIntrinsic()) {
                if (x instanceof BranchProbabilityNode) {
                    ((BranchProbabilityNode) x).simplify(null);
                } else if (y instanceof BranchProbabilityNode) {
                    ((BranchProbabilityNode) y).simplify(null);
                }
            }
        }
    }

    private boolean tryGenConditionalForIf(BciBlock trueBlock, BciBlock falseBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt) {
        if (gotoOrFallThroughAfterConstant(trueBlock) && gotoOrFallThroughAfterConstant(falseBlock) && trueBlock.getSuccessor(0) == falseBlock.getSuccessor(0)) {
            genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, false);
            return true;
        } else if (this.parent != null && returnAfterConstant(trueBlock) && returnAfterConstant(falseBlock)) {
            genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, true);
            return true;
        }
        return false;
    }

    private void genConditionalForIf(BciBlock trueBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt, boolean genReturn) {
        ConstantNode trueValue = graph.unique(ConstantNode.forInt(trueBlockInt));
        ConstantNode falseValue = graph.unique(ConstantNode.forInt(falseBlockInt));
        ValueNode conditionalNode = ConditionalNode.create(condition, trueValue, falseValue);
        if (conditionalNode.graph() == null) {
            conditionalNode = graph.addOrUnique(conditionalNode);
        }
        if (genReturn) {
            JavaKind returnKind = method.getSignature().getReturnKind().getStackKind();
            this.genReturn(conditionalNode, returnKind);
        } else {
            frameState.push(JavaKind.Int, conditionalNode);
            appendGoto(trueBlock.getSuccessor(0));
            stream.setBCI(oldBci);
        }
    }

    private LogicNode createLogicNode(Condition cond, ValueNode a, ValueNode b) {
        LogicNode condition;
        assert !a.getStackKind().isNumericFloat();
        if (cond == Condition.EQ || cond == Condition.NE) {
            if (a.getStackKind() == JavaKind.Object) {
                condition = genObjectEquals(a, b);
            } else {
                condition = genIntegerEquals(a, b);
            }
        } else {
            assert a.getStackKind() != JavaKind.Object && !cond.isUnsigned();
            condition = genIntegerLessThan(a, b);
        }
        return condition;
    }

    private void genConstantTargetIf(BciBlock trueBlock, BciBlock falseBlock, boolean negate, LogicNode condition) {
        LogicConstantNode constantLogicNode = (LogicConstantNode) condition;
        boolean value = constantLogicNode.getValue();
        if (negate) {
            value = !value;
        }
        BciBlock nextBlock = falseBlock;
        if (value) {
            nextBlock = trueBlock;
        }
        int startBci = nextBlock.startBci;
        int targetAtStart = stream.readUByte(startBci);
        if (targetAtStart == Bytecodes.GOTO && nextBlock.getPredecessorCount() == 1) {
            // This is an empty block. Skip it.
            BciBlock successorBlock = nextBlock.successors.get(0);
            appendGoto(successorBlock);
            assert nextBlock.numNormalSuccessors() == 1;
        } else {
            appendGoto(nextBlock);
        }
    }

    private int checkPositiveIntConstantPushed(BciBlock block) {
        stream.setBCI(block.startBci);
        int currentBC = stream.currentBC();
        if (currentBC >= Bytecodes.ICONST_0 && currentBC <= Bytecodes.ICONST_5) {
            int constValue = currentBC - Bytecodes.ICONST_0;
            return constValue;
        }
        return -1;
    }

    private boolean gotoOrFallThroughAfterConstant(BciBlock block) {
        stream.setBCI(block.startBci);
        int currentBCI = stream.nextBCI();
        stream.setBCI(currentBCI);
        int currentBC = stream.currentBC();
        return stream.currentBCI() > block.endBci || currentBC == Bytecodes.GOTO || currentBC == Bytecodes.GOTO_W;
    }

    private boolean returnAfterConstant(BciBlock block) {
        stream.setBCI(block.startBci);
        int currentBCI = stream.nextBCI();
        stream.setBCI(currentBCI);
        int currentBC = stream.currentBC();
        return currentBC == Bytecodes.IRETURN;
    }

    public StampProvider getStampProvider() {
        return stampProvider;
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public void push(JavaKind slotKind, ValueNode value) {
        assert value.isAlive();
        frameState.push(slotKind, value);
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    /**
     * Gets the graph being processed by this builder.
     */
    public StructuredGraph getGraph() {
        return graph;
    }

    public BytecodeParser getParent() {
        return parent;
    }

    public IntrinsicContext getIntrinsic() {
        return intrinsicContext;
    }

    @Override
    public String toString() {
        Formatter fmt = new Formatter();
        BytecodeParser bp = this;
        String indent = "";
        while (bp != null) {
            if (bp != this) {
                fmt.format("%n%s", indent);
            }
            fmt.format("%s [bci: %d, intrinsic: %s]", bp.method.asStackTraceElement(bp.bci()), bp.bci(), bp.parsingIntrinsic());
            fmt.format("%n%s", new BytecodeDisassembler().disassemble(bp.method, bp.bci(), bp.bci() + 10));
            bp = bp.parent;
            indent += " ";
        }
        return fmt.toString();
    }

    public BailoutException bailout(String string) {
        FrameState currentFrameState = createFrameState(bci(), null);
        StackTraceElement[] elements = GraphUtil.approxSourceStackTraceElement(currentFrameState);
        BailoutException bailout = new BailoutException(string);
        throw GraphUtil.createBailoutException(string, bailout, elements);
    }

    private FrameState createFrameState(int bci, StateSplit forStateSplit) {
        if (currentBlock != null && bci > currentBlock.endBci) {
            frameState.clearNonLiveLocals(currentBlock, liveness, false);
        }
        return frameState.create(bci, forStateSplit);
    }

    public void setStateAfter(StateSplit sideEffect) {
        assert sideEffect.hasSideEffect();
        FrameState stateAfter = createFrameState(stream.nextBCI(), sideEffect);
        sideEffect.setStateAfter(stateAfter);
    }

    private BytecodePosition createBytecodePosition() {
        return frameState.createBytecodePosition(bci());
    }

    public void setCurrentFrameState(FrameStateBuilder frameState) {
        this.frameState = frameState;
    }

    protected final BytecodeStream getStream() {
        return stream;
    }

    public int bci() {
        return stream.currentBCI();
    }

    public void loadLocal(int index, JavaKind kind) {
        ValueNode value = frameState.loadLocal(index, kind);
        frameState.push(kind, value);
    }

    public void storeLocal(JavaKind kind, int index) {
        ValueNode value = frameState.pop(kind);
        frameState.storeLocal(index, kind, value);
    }

    private void genLoadConstant(int cpi, int opcode) {
        Object con = lookupConstant(cpi, opcode);

        if (con instanceof JavaType) {
            // this is a load of class constant which might be unresolved
            JavaType type = (JavaType) con;
            if (type instanceof ResolvedJavaType) {
                frameState.push(JavaKind.Object, appendConstant(getConstantReflection().asJavaClass((ResolvedJavaType) type)));
            } else {
                handleUnresolvedLoadConstant(type);
            }
        } else if (con instanceof JavaConstant) {
            JavaConstant constant = (JavaConstant) con;
            frameState.push(constant.getJavaKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    private void genLoadIndexed(JavaKind kind) {
        ValueNode index = frameState.pop(JavaKind.Int);
        ValueNode array = emitExplicitExceptions(frameState.pop(JavaKind.Object), index);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleLoadIndexed(this, array, index, kind)) {
                return;
            }
        }

        frameState.push(kind, append(genLoadIndexed(array, index, kind)));
    }

    private void genStoreIndexed(JavaKind kind) {
        ValueNode value = frameState.pop(kind);
        ValueNode index = frameState.pop(JavaKind.Int);
        ValueNode array = emitExplicitExceptions(frameState.pop(JavaKind.Object), index);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleStoreIndexed(this, array, index, kind, value)) {
                return;
            }
        }

        genStoreIndexed(array, index, kind, value);
    }

    private void genArithmeticOp(JavaKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case IADD:
            case LADD:
                v = genIntegerAdd(x, y);
                break;
            case FADD:
            case DADD:
                v = genFloatAdd(x, y);
                break;
            case ISUB:
            case LSUB:
                v = genIntegerSub(x, y);
                break;
            case FSUB:
            case DSUB:
                v = genFloatSub(x, y);
                break;
            case IMUL:
            case LMUL:
                v = genIntegerMul(x, y);
                break;
            case FMUL:
            case DMUL:
                v = genFloatMul(x, y);
                break;
            case FDIV:
            case DDIV:
                v = genFloatDiv(x, y);
                break;
            case FREM:
            case DREM:
                v = genFloatRem(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genIntegerDivOp(JavaKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case IDIV:
            case LDIV:
                v = genIntegerDiv(x, y);
                break;
            case IREM:
            case LREM:
                v = genIntegerRem(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genNegateOp(JavaKind kind) {
        ValueNode x = frameState.pop(kind);
        frameState.push(kind, append(genNegateOp(x)));
    }

    private void genShiftOp(JavaKind kind, int opcode) {
        ValueNode s = frameState.pop(JavaKind.Int);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case ISHL:
            case LSHL:
                v = genLeftShift(x, s);
                break;
            case ISHR:
            case LSHR:
                v = genRightShift(x, s);
                break;
            case IUSHR:
            case LUSHR:
                v = genUnsignedRightShift(x, s);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genLogicOp(JavaKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case IAND:
            case LAND:
                v = genAnd(x, y);
                break;
            case IOR:
            case LOR:
                v = genOr(x, y);
                break;
            case IXOR:
            case LXOR:
                v = genXor(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genCompareOp(JavaKind kind, boolean isUnorderedLess) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        frameState.push(JavaKind.Int, append(genNormalizeCompare(x, y, isUnorderedLess)));
    }

    private void genFloatConvert(FloatConvert op, JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        frameState.push(to, append(genFloatConvert(op, input)));
    }

    private void genSignExtend(JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to, append(genSignExtend(input, to.getBitCount())));
    }

    private void genZeroExtend(JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to, append(genZeroExtend(input, to.getBitCount())));
    }

    private void genNarrow(JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        frameState.push(to, append(genNarrow(input, to.getBitCount())));
    }

    private void genIncrement() {
        int index = getStream().readLocalIndex();
        int delta = getStream().readIncrement();
        ValueNode x = frameState.loadLocal(index, JavaKind.Int);
        ValueNode y = appendConstant(JavaConstant.forInt(delta));
        frameState.storeLocal(index, JavaKind.Int, append(genIntegerAdd(x, y)));
    }

    private void genIfZero(Condition cond) {
        ValueNode y = appendConstant(JavaConstant.INT_0);
        ValueNode x = frameState.pop(JavaKind.Int);
        genIf(x, cond, y);
    }

    private void genIfNull(Condition cond) {
        ValueNode y = appendConstant(JavaConstant.NULL_POINTER);
        ValueNode x = frameState.pop(JavaKind.Object);
        genIf(x, cond, y);
    }

    private void genIfSame(JavaKind kind, Condition cond) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        genIf(x, cond, y);
    }

    protected JavaType lookupType(int cpi, int bytecode) {
        maybeEagerlyResolve(cpi, bytecode);
        JavaType result = constantPool.lookupType(cpi, bytecode);
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaType;
        return result;
    }

    private JavaMethod lookupMethod(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        JavaMethod result = constantPool.lookupMethod(cpi, opcode);
        /*
         * In general, one cannot assume that the declaring class being initialized is useful, since
         * the actual concrete receiver may be a different class (except for static calls). Also,
         * interfaces are initialized only under special circumstances, so that this assertion would
         * often fail for interface calls.
         */
        assert !graphBuilderConfig.unresolvedIsError() || (result instanceof ResolvedJavaMethod && (opcode != INVOKESTATIC || ((ResolvedJavaMethod) result).getDeclaringClass().isInitialized())) : result;
        return result;
    }

    private JavaField lookupField(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        JavaField result = constantPool.lookupField(cpi, opcode);
        if (graphBuilderConfig.eagerResolving()) {
            assert result instanceof ResolvedJavaField : "Not resolved: " + result;
            ResolvedJavaType declaringClass = ((ResolvedJavaField) result).getDeclaringClass();
            if (!declaringClass.isInitialized()) {
                assert declaringClass.isInterface() : "Declaring class not initialized but not an interface? " + declaringClass;
                declaringClass.initialize();
            }
        }
        assert !graphBuilderConfig.unresolvedIsError() || (result instanceof ResolvedJavaField && ((ResolvedJavaField) result).getDeclaringClass().isInitialized()) : result;
        return result;
    }

    private Object lookupConstant(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        Object result = constantPool.lookupConstant(cpi);
        assert !graphBuilderConfig.eagerResolving() || !(result instanceof JavaType) || (result instanceof ResolvedJavaType) : result;
        return result;
    }

    private void maybeEagerlyResolve(int cpi, int bytecode) {
        if (graphBuilderConfig.eagerResolving() || intrinsicContext != null) {
            constantPool.loadReferencedType(cpi, bytecode);
        }
    }

    private JavaTypeProfile getProfileForTypeCheck(CheckedJavaType type) {
        if (parsingIntrinsic() || profilingInfo == null || !optimisticOpts.useTypeCheckHints() || type.isExactType()) {
            return null;
        } else {
            return profilingInfo.getTypeProfile(bci());
        }
    }

    private void genCheckCast() {
        int cpi = getStream().readCPI();
        JavaType type = lookupType(cpi, CHECKCAST);
        ValueNode object = frameState.pop(JavaKind.Object);

        if (!(type instanceof ResolvedJavaType)) {
            handleUnresolvedCheckCast(type, object);
            return;
        }
        CheckedJavaType checkedType = CheckedJavaType.create(graph.getAssumptions(), (ResolvedJavaType) type);
        JavaTypeProfile profile = getProfileForTypeCheck(checkedType);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleCheckCast(this, object, checkedType.getType(), profile)) {
                return;
            }
        }

        ValueNode checkCastNode = null;
        if (profile != null) {
            if (CheckCastNode.findSynonym(checkedType, object) == object) {
                // Don't insert a profiled type check if the checkcast would simply go away
                checkCastNode = object;
            } else if (profile.getNullSeen().isFalse()) {
                object = appendNullCheck(object);
                ResolvedJavaType singleType = profile.asSingleType();
                if (singleType != null && checkedType.getType().isAssignableFrom(singleType)) {
                    LogicNode typeCheck = append(TypeCheckNode.create(singleType, object));
                    if (typeCheck.isTautology()) {
                        checkCastNode = object;
                    } else {
                        FixedGuardNode fixedGuard = append(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile, false));
                        checkCastNode = append(new PiNode(object, StampFactory.exactNonNull(singleType), fixedGuard));
                    }
                }
            }
        }
        if (checkCastNode == null) {
            checkCastNode = append(createCheckCast(checkedType, object, profile, false));
        }
        frameState.push(JavaKind.Object, checkCastNode);
    }

    private ValueNode appendNullCheck(ValueNode object) {
        if (object.stamp() instanceof AbstractPointerStamp) {
            AbstractPointerStamp stamp = (AbstractPointerStamp) object.stamp();
            if (stamp.nonNull()) {
                return object;
            }
        }

        IsNullNode isNull = append(new IsNullNode(object));
        FixedGuardNode fixedGuard = append(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true));
        return append(new PiNode(object, object.stamp().join(StampFactory.objectNonNull()), fixedGuard));
    }

    private void genInstanceOf() {
        int cpi = getStream().readCPI();
        JavaType type = lookupType(cpi, INSTANCEOF);
        ValueNode object = frameState.pop(JavaKind.Object);

        if (!(type instanceof ResolvedJavaType)) {
            handleUnresolvedInstanceOf(type, object);
            return;
        }
        CheckedJavaType resolvedType = CheckedJavaType.create(graph.getAssumptions(), (ResolvedJavaType) type);
        JavaTypeProfile profile = getProfileForTypeCheck(resolvedType);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleInstanceOf(this, object, resolvedType.getType(), profile)) {
                return;
            }
        }

        ValueNode instanceOfNode = null;
        if (profile != null) {
            if (profile.getNullSeen().isFalse()) {
                object = appendNullCheck(object);
                ResolvedJavaType singleType = profile.asSingleType();
                if (singleType != null) {
                    LogicNode typeCheck = append(TypeCheckNode.create(singleType, object));
                    if (!typeCheck.isTautology()) {
                        append(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));
                    }
                    instanceOfNode = LogicConstantNode.forBoolean(resolvedType.getType().isAssignableFrom(singleType));
                }
            }
        }
        if (instanceOfNode == null) {
            instanceOfNode = createInstanceOf(resolvedType, object, profile);
        }
        frameState.push(JavaKind.Int, append(genConditional(genUnique(instanceOfNode))));
    }

    void genNewInstance(int cpi) {
        JavaType type = lookupType(cpi, NEW);

        if (!(type instanceof ResolvedJavaType) || !((ResolvedJavaType) type).isInitialized()) {
            handleUnresolvedNewInstance(type);
            return;
        }
        ResolvedJavaType resolvedType = (ResolvedJavaType) type;

        ResolvedJavaType[] skippedExceptionTypes = this.graphBuilderConfig.getSkippedExceptionTypes();
        if (skippedExceptionTypes != null) {
            for (ResolvedJavaType exceptionType : skippedExceptionTypes) {
                if (exceptionType.isAssignableFrom(resolvedType)) {
                    append(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, TransferToInterpreter));
                    return;
                }
            }
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewInstance(this, resolvedType)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewInstance(resolvedType, true)));
    }

    /**
     * Gets the kind of array elements for the array type code that appears in a
     * {@link Bytecodes#NEWARRAY} bytecode.
     *
     * @param code the array type code
     * @return the kind from the array type code
     */
    private static Class<?> arrayTypeCodeToClass(int code) {
        switch (code) {
            case 4:
                return boolean.class;
            case 5:
                return char.class;
            case 6:
                return float.class;
            case 7:
                return double.class;
            case 8:
                return byte.class;
            case 9:
                return short.class;
            case 10:
                return int.class;
            case 11:
                return long.class;
            default:
                throw new IllegalArgumentException("unknown array type code: " + code);
        }
    }

    private void genNewPrimitiveArray(int typeCode) {
        ResolvedJavaType elementType = metaAccess.lookupJavaType(arrayTypeCodeToClass(typeCode));
        ValueNode length = frameState.pop(JavaKind.Int);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewArray(this, elementType, length)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewArray(elementType, length, true)));
    }

    private void genNewObjectArray(int cpi) {
        JavaType type = lookupType(cpi, ANEWARRAY);
        ValueNode length = frameState.pop(JavaKind.Int);

        if (!(type instanceof ResolvedJavaType)) {
            handleUnresolvedNewObjectArray(type, length);
            return;
        }
        ResolvedJavaType resolvedType = (ResolvedJavaType) type;

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewArray(this, resolvedType, length)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewArray(resolvedType, length, true)));
    }

    private void genNewMultiArray(int cpi) {
        JavaType type = lookupType(cpi, MULTIANEWARRAY);
        int rank = getStream().readUByte(bci() + 3);
        ValueNode[] dims = new ValueNode[rank];
        for (int i = rank - 1; i >= 0; i--) {
            dims[i] = frameState.pop(JavaKind.Int);
        }

        if (!(type instanceof ResolvedJavaType)) {
            handleUnresolvedNewMultiArray(type, dims);
            return;
        }
        ResolvedJavaType resolvedType = (ResolvedJavaType) type;

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewMultiArray(this, resolvedType, dims)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewMultiArray(resolvedType, dims)));
    }

    private void genGetField(JavaField field) {
        ValueNode receiver = emitExplicitExceptions(frameState.pop(JavaKind.Object), null);

        if (!(field instanceof ResolvedJavaField) || !((ResolvedJavaField) field).getDeclaringClass().isInitialized()) {
            handleUnresolvedLoadField(field, receiver);
            return;
        }
        ResolvedJavaField resolvedField = (ResolvedJavaField) field;

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleLoadField(this, receiver, resolvedField)) {
                return;
            }
        }

        frameState.push(field.getJavaKind(), append(genLoadField(receiver, resolvedField)));
    }

    /**
     * @param receiver the receiver of an object based operation
     * @param index the index of an array based operation that is to be tested for out of bounds.
     *            This is null for a non-array operation.
     * @return the receiver value possibly modified to have a tighter stamp
     */
    protected ValueNode emitExplicitExceptions(ValueNode receiver, ValueNode index) {
        assert receiver != null;
        if (graphBuilderConfig.omitAllExceptionEdges() || profilingInfo == null ||
                        (optimisticOpts.useExceptionProbabilityForOperations() && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE && !GraalOptions.StressExplicitExceptionCode.getValue())) {
            return receiver;
        }

        ValueNode nonNullReceiver = emitExplicitNullCheck(receiver);
        if (index != null) {
            ValueNode length = append(genArrayLength(nonNullReceiver));
            emitExplicitBoundsCheck(index, length);
        }
        EXPLICIT_EXCEPTIONS.increment();
        return nonNullReceiver;
    }

    private void genPutField(JavaField field) {
        ValueNode value = frameState.pop(field.getJavaKind());
        ValueNode receiver = emitExplicitExceptions(frameState.pop(JavaKind.Object), null);

        if (!(field instanceof ResolvedJavaField) || !((ResolvedJavaField) field).getDeclaringClass().isInitialized()) {
            handleUnresolvedStoreField(field, value, receiver);
            return;
        }
        ResolvedJavaField resolvedField = (ResolvedJavaField) field;

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleStoreField(this, receiver, resolvedField, value)) {
                return;
            }
        }

        genStoreField(receiver, resolvedField, value);
    }

    private void genGetStatic(JavaField field) {
        if (!(field instanceof ResolvedJavaField) || !((ResolvedJavaType) field.getDeclaringClass()).isInitialized()) {
            handleUnresolvedLoadField(field, null);
            return;
        }
        ResolvedJavaField resolvedField = (ResolvedJavaField) field;

        /*
         * Javac does not allow use of "$assertionsDisabled" for a field name but Eclipse does, in
         * which case a suffix is added to the generated field.
         */
        if ((parsingIntrinsic() || graphBuilderConfig.omitAssertions()) && resolvedField.isSynthetic() && resolvedField.getName().startsWith("$assertionsDisabled")) {
            frameState.push(field.getJavaKind(), ConstantNode.forBoolean(true, graph));
            return;
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleLoadStaticField(this, resolvedField)) {
                return;
            }
        }

        frameState.push(field.getJavaKind(), append(genLoadField(null, resolvedField)));
    }

    private void genPutStatic(JavaField field) {
        ValueNode value = frameState.pop(field.getJavaKind());
        if (!(field instanceof ResolvedJavaField) || !((ResolvedJavaType) field.getDeclaringClass()).isInitialized()) {
            handleUnresolvedStoreField(field, value, null);
            return;
        }
        ResolvedJavaField resolvedField = (ResolvedJavaField) field;

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleStoreStaticField(this, resolvedField, value)) {
                return;
            }
        }

        genStoreField(null, resolvedField, value);
    }

    private double[] switchProbability(int numberOfCases, int bci) {
        double[] prob = (profilingInfo == null ? null : profilingInfo.getSwitchProbabilities(bci));
        if (prob != null) {
            assert prob.length == numberOfCases;
        } else {
            Debug.log("Missing probability (switch) in %s at bci %d", method, bci);
            prob = new double[numberOfCases];
            for (int i = 0; i < numberOfCases; i++) {
                prob[i] = 1.0d / numberOfCases;
            }
        }
        assert allPositive(prob);
        return prob;
    }

    private static boolean allPositive(double[] a) {
        for (double d : a) {
            if (d < 0) {
                return false;
            }
        }
        return true;
    }

    static class SuccessorInfo {
        final int blockIndex;
        int actualIndex;

        SuccessorInfo(int blockSuccessorIndex) {
            this.blockIndex = blockSuccessorIndex;
            actualIndex = -1;
        }
    }

    private void genSwitch(BytecodeSwitch bs) {
        int bci = bci();
        ValueNode value = frameState.pop(JavaKind.Int);

        int nofCases = bs.numberOfCases();
        double[] keyProbabilities = switchProbability(nofCases + 1, bci);

        Map<Integer, SuccessorInfo> bciToBlockSuccessorIndex = new HashMap<>();
        for (int i = 0; i < currentBlock.getSuccessorCount(); i++) {
            assert !bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessor(i).startBci);
            if (!bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessor(i).startBci)) {
                bciToBlockSuccessorIndex.put(currentBlock.getSuccessor(i).startBci, new SuccessorInfo(i));
            }
        }

        ArrayList<BciBlock> actualSuccessors = new ArrayList<>();
        int[] keys = new int[nofCases];
        int[] keySuccessors = new int[nofCases + 1];
        int deoptSuccessorIndex = -1;
        int nextSuccessorIndex = 0;
        boolean constantValue = value.isConstant();
        for (int i = 0; i < nofCases + 1; i++) {
            if (i < nofCases) {
                keys[i] = bs.keyAt(i);
            }

            if (!constantValue && isNeverExecutedCode(keyProbabilities[i])) {
                if (deoptSuccessorIndex < 0) {
                    deoptSuccessorIndex = nextSuccessorIndex++;
                    actualSuccessors.add(null);
                }
                keySuccessors[i] = deoptSuccessorIndex;
            } else {
                int targetBci = i >= nofCases ? bs.defaultTarget() : bs.targetAt(i);
                SuccessorInfo info = bciToBlockSuccessorIndex.get(targetBci);
                if (info.actualIndex < 0) {
                    info.actualIndex = nextSuccessorIndex++;
                    actualSuccessors.add(currentBlock.getSuccessor(info.blockIndex));
                }
                keySuccessors[i] = info.actualIndex;
            }
        }

        genIntegerSwitch(value, actualSuccessors, keys, keyProbabilities, keySuccessors);

    }

    protected boolean isNeverExecutedCode(double probability) {
        return probability == 0 && optimisticOpts.removeNeverExecutedCode();
    }

    protected double branchProbability() {
        if (profilingInfo == null) {
            return 0.5;
        }
        assert assertAtIfBytecode();
        double probability = profilingInfo.getBranchTakenProbability(bci());
        if (probability < 0) {
            assert probability == -1 : "invalid probability";
            Debug.log("missing probability in %s at bci %d", method, bci());
            probability = 0.5;
        }

        if (!optimisticOpts.removeNeverExecutedCode()) {
            if (probability == 0) {
                probability = 0.0000001;
            } else if (probability == 1) {
                probability = 0.999999;
            }
        }
        return probability;
    }

    private boolean assertAtIfBytecode() {
        int bytecode = stream.currentBC();
        switch (bytecode) {
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
            case IFNULL:
            case IFNONNULL:
                return true;
        }
        assert false : String.format("%x is not an if bytecode", bytecode);
        return true;
    }

    public final void processBytecode(int bci, int opcode) {
        int cpi;

        // @formatter:off
        // Checkstyle: stop
        switch (opcode) {
            case NOP            : /* nothing to do */ break;
            case ACONST_NULL    : frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER)); break;
            case ICONST_M1      : // fall through
            case ICONST_0       : // fall through
            case ICONST_1       : // fall through
            case ICONST_2       : // fall through
            case ICONST_3       : // fall through
            case ICONST_4       : // fall through
            case ICONST_5       : frameState.push(JavaKind.Int, appendConstant(JavaConstant.forInt(opcode - ICONST_0))); break;
            case LCONST_0       : // fall through
            case LCONST_1       : frameState.push(JavaKind.Long, appendConstant(JavaConstant.forLong(opcode - LCONST_0))); break;
            case FCONST_0       : // fall through
            case FCONST_1       : // fall through
            case FCONST_2       : frameState.push(JavaKind.Float, appendConstant(JavaConstant.forFloat(opcode - FCONST_0))); break;
            case DCONST_0       : // fall through
            case DCONST_1       : frameState.push(JavaKind.Double, appendConstant(JavaConstant.forDouble(opcode - DCONST_0))); break;
            case BIPUSH         : frameState.push(JavaKind.Int, appendConstant(JavaConstant.forInt(stream.readByte()))); break;
            case SIPUSH         : frameState.push(JavaKind.Int, appendConstant(JavaConstant.forInt(stream.readShort()))); break;
            case LDC            : // fall through
            case LDC_W          : // fall through
            case LDC2_W         : genLoadConstant(stream.readCPI(), opcode); break;
            case ILOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Int); break;
            case LLOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Long); break;
            case FLOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Float); break;
            case DLOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Double); break;
            case ALOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Object); break;
            case ILOAD_0        : // fall through
            case ILOAD_1        : // fall through
            case ILOAD_2        : // fall through
            case ILOAD_3        : loadLocal(opcode - ILOAD_0, JavaKind.Int); break;
            case LLOAD_0        : // fall through
            case LLOAD_1        : // fall through
            case LLOAD_2        : // fall through
            case LLOAD_3        : loadLocal(opcode - LLOAD_0, JavaKind.Long); break;
            case FLOAD_0        : // fall through
            case FLOAD_1        : // fall through
            case FLOAD_2        : // fall through
            case FLOAD_3        : loadLocal(opcode - FLOAD_0, JavaKind.Float); break;
            case DLOAD_0        : // fall through
            case DLOAD_1        : // fall through
            case DLOAD_2        : // fall through
            case DLOAD_3        : loadLocal(opcode - DLOAD_0, JavaKind.Double); break;
            case ALOAD_0        : // fall through
            case ALOAD_1        : // fall through
            case ALOAD_2        : // fall through
            case ALOAD_3        : loadLocal(opcode - ALOAD_0, JavaKind.Object); break;
            case IALOAD         : genLoadIndexed(JavaKind.Int   ); break;
            case LALOAD         : genLoadIndexed(JavaKind.Long  ); break;
            case FALOAD         : genLoadIndexed(JavaKind.Float ); break;
            case DALOAD         : genLoadIndexed(JavaKind.Double); break;
            case AALOAD         : genLoadIndexed(JavaKind.Object); break;
            case BALOAD         : genLoadIndexed(JavaKind.Byte  ); break;
            case CALOAD         : genLoadIndexed(JavaKind.Char  ); break;
            case SALOAD         : genLoadIndexed(JavaKind.Short ); break;
            case ISTORE         : storeLocal(JavaKind.Int, stream.readLocalIndex()); break;
            case LSTORE         : storeLocal(JavaKind.Long, stream.readLocalIndex()); break;
            case FSTORE         : storeLocal(JavaKind.Float, stream.readLocalIndex()); break;
            case DSTORE         : storeLocal(JavaKind.Double, stream.readLocalIndex()); break;
            case ASTORE         : storeLocal(JavaKind.Object, stream.readLocalIndex()); break;
            case ISTORE_0       : // fall through
            case ISTORE_1       : // fall through
            case ISTORE_2       : // fall through
            case ISTORE_3       : storeLocal(JavaKind.Int, opcode - ISTORE_0); break;
            case LSTORE_0       : // fall through
            case LSTORE_1       : // fall through
            case LSTORE_2       : // fall through
            case LSTORE_3       : storeLocal(JavaKind.Long, opcode - LSTORE_0); break;
            case FSTORE_0       : // fall through
            case FSTORE_1       : // fall through
            case FSTORE_2       : // fall through
            case FSTORE_3       : storeLocal(JavaKind.Float, opcode - FSTORE_0); break;
            case DSTORE_0       : // fall through
            case DSTORE_1       : // fall through
            case DSTORE_2       : // fall through
            case DSTORE_3       : storeLocal(JavaKind.Double, opcode - DSTORE_0); break;
            case ASTORE_0       : // fall through
            case ASTORE_1       : // fall through
            case ASTORE_2       : // fall through
            case ASTORE_3       : storeLocal(JavaKind.Object, opcode - ASTORE_0); break;
            case IASTORE        : genStoreIndexed(JavaKind.Int   ); break;
            case LASTORE        : genStoreIndexed(JavaKind.Long  ); break;
            case FASTORE        : genStoreIndexed(JavaKind.Float ); break;
            case DASTORE        : genStoreIndexed(JavaKind.Double); break;
            case AASTORE        : genStoreIndexed(JavaKind.Object); break;
            case BASTORE        : genStoreIndexed(JavaKind.Byte  ); break;
            case CASTORE        : genStoreIndexed(JavaKind.Char  ); break;
            case SASTORE        : genStoreIndexed(JavaKind.Short ); break;
            case POP            : // fall through
            case POP2           : // fall through
            case DUP            : // fall through
            case DUP_X1         : // fall through
            case DUP_X2         : // fall through
            case DUP2           : // fall through
            case DUP2_X1        : // fall through
            case DUP2_X2        : // fall through
            case SWAP           : frameState.stackOp(opcode); break;
            case IADD           : // fall through
            case ISUB           : // fall through
            case IMUL           : genArithmeticOp(JavaKind.Int, opcode); break;
            case IDIV           : // fall through
            case IREM           : genIntegerDivOp(JavaKind.Int, opcode); break;
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : genArithmeticOp(JavaKind.Long, opcode); break;
            case LDIV           : // fall through
            case LREM           : genIntegerDivOp(JavaKind.Long, opcode); break;
            case FADD           : // fall through
            case FSUB           : // fall through
            case FMUL           : // fall through
            case FDIV           : // fall through
            case FREM           : genArithmeticOp(JavaKind.Float, opcode); break;
            case DADD           : // fall through
            case DSUB           : // fall through
            case DMUL           : // fall through
            case DDIV           : // fall through
            case DREM           : genArithmeticOp(JavaKind.Double, opcode); break;
            case INEG           : genNegateOp(JavaKind.Int); break;
            case LNEG           : genNegateOp(JavaKind.Long); break;
            case FNEG           : genNegateOp(JavaKind.Float); break;
            case DNEG           : genNegateOp(JavaKind.Double); break;
            case ISHL           : // fall through
            case ISHR           : // fall through
            case IUSHR          : genShiftOp(JavaKind.Int, opcode); break;
            case IAND           : // fall through
            case IOR            : // fall through
            case IXOR           : genLogicOp(JavaKind.Int, opcode); break;
            case LSHL           : // fall through
            case LSHR           : // fall through
            case LUSHR          : genShiftOp(JavaKind.Long, opcode); break;
            case LAND           : // fall through
            case LOR            : // fall through
            case LXOR           : genLogicOp(JavaKind.Long, opcode); break;
            case IINC           : genIncrement(); break;
            case I2F            : genFloatConvert(FloatConvert.I2F, JavaKind.Int, JavaKind.Float); break;
            case I2D            : genFloatConvert(FloatConvert.I2D, JavaKind.Int, JavaKind.Double); break;
            case L2F            : genFloatConvert(FloatConvert.L2F, JavaKind.Long, JavaKind.Float); break;
            case L2D            : genFloatConvert(FloatConvert.L2D, JavaKind.Long, JavaKind.Double); break;
            case F2I            : genFloatConvert(FloatConvert.F2I, JavaKind.Float, JavaKind.Int); break;
            case F2L            : genFloatConvert(FloatConvert.F2L, JavaKind.Float, JavaKind.Long); break;
            case F2D            : genFloatConvert(FloatConvert.F2D, JavaKind.Float, JavaKind.Double); break;
            case D2I            : genFloatConvert(FloatConvert.D2I, JavaKind.Double, JavaKind.Int); break;
            case D2L            : genFloatConvert(FloatConvert.D2L, JavaKind.Double, JavaKind.Long); break;
            case D2F            : genFloatConvert(FloatConvert.D2F, JavaKind.Double, JavaKind.Float); break;
            case L2I            : genNarrow(JavaKind.Long, JavaKind.Int); break;
            case I2L            : genSignExtend(JavaKind.Int, JavaKind.Long); break;
            case I2B            : genSignExtend(JavaKind.Byte, JavaKind.Int); break;
            case I2S            : genSignExtend(JavaKind.Short, JavaKind.Int); break;
            case I2C            : genZeroExtend(JavaKind.Char, JavaKind.Int); break;
            case LCMP           : genCompareOp(JavaKind.Long, false); break;
            case FCMPL          : genCompareOp(JavaKind.Float, true); break;
            case FCMPG          : genCompareOp(JavaKind.Float, false); break;
            case DCMPL          : genCompareOp(JavaKind.Double, true); break;
            case DCMPG          : genCompareOp(JavaKind.Double, false); break;
            case IFEQ           : genIfZero(Condition.EQ); break;
            case IFNE           : genIfZero(Condition.NE); break;
            case IFLT           : genIfZero(Condition.LT); break;
            case IFGE           : genIfZero(Condition.GE); break;
            case IFGT           : genIfZero(Condition.GT); break;
            case IFLE           : genIfZero(Condition.LE); break;
            case IF_ICMPEQ      : genIfSame(JavaKind.Int, Condition.EQ); break;
            case IF_ICMPNE      : genIfSame(JavaKind.Int, Condition.NE); break;
            case IF_ICMPLT      : genIfSame(JavaKind.Int, Condition.LT); break;
            case IF_ICMPGE      : genIfSame(JavaKind.Int, Condition.GE); break;
            case IF_ICMPGT      : genIfSame(JavaKind.Int, Condition.GT); break;
            case IF_ICMPLE      : genIfSame(JavaKind.Int, Condition.LE); break;
            case IF_ACMPEQ      : genIfSame(JavaKind.Object, Condition.EQ); break;
            case IF_ACMPNE      : genIfSame(JavaKind.Object, Condition.NE); break;
            case GOTO           : genGoto(); break;
            case JSR            : genJsr(stream.readBranchDest()); break;
            case RET            : genRet(stream.readLocalIndex()); break;
            case TABLESWITCH    : genSwitch(new BytecodeTableSwitch(getStream(), bci())); break;
            case LOOKUPSWITCH   : genSwitch(new BytecodeLookupSwitch(getStream(), bci())); break;
            case IRETURN        : genReturn(frameState.pop(JavaKind.Int), JavaKind.Int); break;
            case LRETURN        : genReturn(frameState.pop(JavaKind.Long), JavaKind.Long); break;
            case FRETURN        : genReturn(frameState.pop(JavaKind.Float), JavaKind.Float); break;
            case DRETURN        : genReturn(frameState.pop(JavaKind.Double), JavaKind.Double); break;
            case ARETURN        : genReturn(frameState.pop(JavaKind.Object), JavaKind.Object); break;
            case RETURN         : genReturn(null, JavaKind.Void); break;
            case GETSTATIC      : cpi = stream.readCPI(); genGetStatic(lookupField(cpi, opcode)); break;
            case PUTSTATIC      : cpi = stream.readCPI(); genPutStatic(lookupField(cpi, opcode)); break;
            case GETFIELD       : cpi = stream.readCPI(); genGetField(lookupField(cpi, opcode)); break;
            case PUTFIELD       : cpi = stream.readCPI(); genPutField(lookupField(cpi, opcode)); break;
            case INVOKEVIRTUAL  : cpi = stream.readCPI(); genInvokeVirtual(lookupMethod(cpi, opcode)); break;
            case INVOKESPECIAL  : cpi = stream.readCPI(); genInvokeSpecial(lookupMethod(cpi, opcode)); break;
            case INVOKESTATIC   : cpi = stream.readCPI(); genInvokeStatic(lookupMethod(cpi, opcode)); break;
            case INVOKEINTERFACE: cpi = stream.readCPI(); genInvokeInterface(lookupMethod(cpi, opcode)); break;
            case INVOKEDYNAMIC  : cpi = stream.readCPI4(); genInvokeDynamic(lookupMethod(cpi, opcode)); break;
            case NEW            : genNewInstance(stream.readCPI()); break;
            case NEWARRAY       : genNewPrimitiveArray(stream.readLocalIndex()); break;
            case ANEWARRAY      : genNewObjectArray(stream.readCPI()); break;
            case ARRAYLENGTH    : genArrayLength(); break;
            case ATHROW         : genThrow(); break;
            case CHECKCAST      : genCheckCast(); break;
            case INSTANCEOF     : genInstanceOf(); break;
            case MONITORENTER   : genMonitorEnter(frameState.pop(JavaKind.Object), stream.nextBCI()); break;
            case MONITOREXIT    : genMonitorExit(frameState.pop(JavaKind.Object), null, stream.nextBCI()); break;
            case MULTIANEWARRAY : genNewMultiArray(stream.readCPI()); break;
            case IFNULL         : genIfNull(Condition.EQ); break;
            case IFNONNULL      : genIfNull(Condition.NE); break;
            case GOTO_W         : genGoto(); break;
            case JSR_W          : genJsr(stream.readBranchDest()); break;
            case BREAKPOINT     : throw new BailoutException("concurrent setting of breakpoint");
            default             : throw new BailoutException("Unsupported opcode %d (%s) [bci=%d]", opcode, nameOf(opcode), bci);
        }
        // @formatter:on
        // Checkstyle: resume
    }

    private void genArrayLength() {
        ValueNode array = emitExplicitExceptions(frameState.pop(JavaKind.Object), null);
        frameState.push(JavaKind.Int, append(genArrayLength(array)));
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public FrameStateBuilder getFrameStateBuilder() {
        return frameState;
    }

    protected boolean traceInstruction(int bci, int opcode, boolean blockStart) {
        if (Debug.isEnabled() && BytecodeParserOptions.TraceBytecodeParserLevel.getValue() >= TRACELEVEL_INSTRUCTIONS && Debug.isLogEnabled()) {
            traceInstructionHelper(bci, opcode, blockStart);
        }
        return true;
    }

    private void traceInstructionHelper(int bci, int opcode, boolean blockStart) {
        StringBuilder sb = new StringBuilder(40);
        sb.append(blockStart ? '+' : '|');
        if (bci < 10) {
            sb.append("  ");
        } else if (bci < 100) {
            sb.append(' ');
        }
        sb.append(bci).append(": ").append(Bytecodes.nameOf(opcode));
        for (int i = bci + 1; i < stream.nextBCI(); ++i) {
            sb.append(' ').append(stream.readUByte(i));
        }
        if (!currentBlock.getJsrScope().isEmpty()) {
            sb.append(' ').append(currentBlock.getJsrScope());
        }
        Debug.log("%s", sb);
    }

    public boolean parsingIntrinsic() {
        return intrinsicContext != null;
    }

    public BytecodeParser getNonIntrinsicAncestor() {
        BytecodeParser ancestor = parent;
        while (ancestor != null && ancestor.parsingIntrinsic()) {
            ancestor = ancestor.parent;
        }
        return ancestor;
    }

    static String nSpaces(int n) {
        return n == 0 ? "" : format("%" + n + "s", "");
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }
}
