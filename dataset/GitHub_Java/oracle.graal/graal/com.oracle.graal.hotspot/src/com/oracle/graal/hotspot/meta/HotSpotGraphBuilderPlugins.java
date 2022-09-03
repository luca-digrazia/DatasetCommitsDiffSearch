/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.common.util.Util.Java8OrEarlier;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_THREAD_OBJECT_LOCATION;
import static com.oracle.graal.java.BytecodeParserOptions.InlineDuringParsing;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.zip.CRC32;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.bytecode.BytecodeProvider;
import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.hotspot.GraalHotSpotVMConfig;
import com.oracle.graal.hotspot.nodes.CurrentJavaThreadNode;
import com.oracle.graal.hotspot.replacements.AESCryptSubstitutions;
import com.oracle.graal.hotspot.replacements.BigIntegerSubstitutions;
import com.oracle.graal.hotspot.replacements.CRC32Substitutions;
import com.oracle.graal.hotspot.replacements.CallSiteTargetNode;
import com.oracle.graal.hotspot.replacements.CipherBlockChainingSubstitutions;
import com.oracle.graal.hotspot.replacements.ClassGetHubNode;
import com.oracle.graal.hotspot.replacements.IdentityHashCodeNode;
import com.oracle.graal.hotspot.replacements.HotSpotClassSubstitutions;
import com.oracle.graal.hotspot.replacements.ObjectCloneNode;
import com.oracle.graal.hotspot.replacements.ObjectSubstitutions;
import com.oracle.graal.hotspot.replacements.ReflectionGetCallerClassNode;
import com.oracle.graal.hotspot.replacements.ReflectionSubstitutions;
import com.oracle.graal.hotspot.replacements.SHA2Substitutions;
import com.oracle.graal.hotspot.replacements.SHA5Substitutions;
import com.oracle.graal.hotspot.replacements.SHASubstitutions;
import com.oracle.graal.hotspot.replacements.ThreadSubstitutions;
import com.oracle.graal.hotspot.replacements.arraycopy.ArrayCopyNode;
import com.oracle.graal.hotspot.word.HotSpotWordTypes;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DynamicPiNode;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.LeftShiftNode;
import com.oracle.graal.nodes.graphbuilderconf.ForeignCallPlugin;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;
import com.oracle.graal.nodes.java.InstanceOfDynamicNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.options.StableOptionValue;
import com.oracle.graal.replacements.InlineDuringParsingPlugin;
import com.oracle.graal.replacements.InlineGraalDirectivesPlugin;
import com.oracle.graal.replacements.MethodHandlePlugin;
import com.oracle.graal.replacements.NodeIntrinsificationProvider;
import com.oracle.graal.replacements.ReplacementsImpl;
import com.oracle.graal.replacements.StandardGraphBuilderPlugins;
import com.oracle.graal.replacements.WordOperationPlugin;
import com.oracle.graal.serviceprovider.GraalServices;
import com.oracle.graal.word.WordTypes;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Defines the {@link Plugins} used when running on HotSpot.
 */
public class HotSpotGraphBuilderPlugins {

    /**
     * Creates a {@link Plugins} object that should be used when running on HotSpot.
     *
     * @param constantReflection
     * @param snippetReflection
     * @param foreignCalls
     * @param stampProvider
     */
    public static Plugins create(GraalHotSpotVMConfig config, HotSpotWordTypes wordTypes, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection,
                    SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, StampProvider stampProvider, ReplacementsImpl replacements) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(config, metaAccess);

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(metaAccess, snippetReflection, foreignCalls, wordTypes);
        HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);
        HotSpotNodePlugin nodePlugin = new HotSpotNodePlugin(wordOperationPlugin);

        plugins.appendTypePlugin(nodePlugin);
        plugins.appendNodePlugin(nodePlugin);
        plugins.appendNodePlugin(new MethodHandlePlugin(constantReflection.getMethodHandleAccess(), true));

        plugins.appendInlineInvokePlugin(replacements);
        if (InlineDuringParsing.getValue()) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }
        plugins.appendInlineInvokePlugin(new InlineGraalDirectivesPlugin());

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                BytecodeProvider replacementBytecodeProvider = replacements.getReplacementBytecodeProvider();
                registerObjectPlugins(invocationPlugins, replacementBytecodeProvider);
                registerClassPlugins(plugins, config, replacementBytecodeProvider);
                registerSystemPlugins(invocationPlugins, foreignCalls);
                registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config, replacementBytecodeProvider);
                registerCallSitePlugins(invocationPlugins);
                registerReflectionPlugins(invocationPlugins, replacementBytecodeProvider);
                registerConstantPoolPlugins(invocationPlugins, wordTypes, config, replacementBytecodeProvider);
                registerStableOptionPlugins(invocationPlugins, snippetReflection);
                registerAESPlugins(invocationPlugins, config, replacementBytecodeProvider);
                registerCRC32Plugins(invocationPlugins, config, replacementBytecodeProvider);
                registerBigIntegerPlugins(invocationPlugins, config, replacementBytecodeProvider);
                registerSHAPlugins(invocationPlugins, config, replacementBytecodeProvider);
                StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, replacementBytecodeProvider, true);

                for (NodeIntrinsicPluginFactory factory : GraalServices.load(NodeIntrinsicPluginFactory.class)) {
                    factory.registerPlugins(invocationPlugins, nodeIntrinsificationProvider);
                }
            }
        });
        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Object.class, bytecodeProvider);
        r.register1("clone", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Object, new ObjectCloneNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), object));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.registerMethodSubstitution(ObjectSubstitutions.class, "hashCode", Receiver.class);
    }

    private static void registerClassPlugins(Plugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class, bytecodeProvider);

        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getModifiers", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isInterface", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isArray", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isPrimitive", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getSuperclass", Receiver.class);

        if (config.getFieldOffset("ArrayKlass::_component_mirror", Integer.class, "oop", Integer.MAX_VALUE) != Integer.MAX_VALUE) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getComponentType", Receiver.class);
        }

        r.register2("cast", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                ValueNode javaClass = receiver.get();
                LogicNode condition = b.recursiveAppend(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), javaClass, object, true));
                if (condition.isTautology()) {
                    b.addPush(JavaKind.Object, object);
                } else {
                    FixedGuardNode fixedGuard = b.add(new FixedGuardNode(condition, DeoptimizationReason.ClassCastException, DeoptimizationAction.InvalidateReprofile, false));
                    b.addPush(JavaKind.Object, new DynamicPiNode(object, fixedGuard, javaClass));
                }
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = receiver.get();
                ValueNode folded = CallSiteTargetNode.tryFold(GraphUtil.originalValue(callSite), b.getMetaAccess(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    b.addPush(JavaKind.Object, new CallSiteTargetNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), callSite));
                }
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        };
        plugins.register(plugin, ConstantCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, MutableCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, VolatileCallSite.class, "getTarget", Receiver.class);
    }

    private static void registerReflectionPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, reflectionClass, bytecodeProvider);
        r.register0("getCallerClass", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new ReflectionGetCallerClassNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions())));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.registerMethodSubstitution(ReflectionSubstitutions.class, "getClassAccessFlags", Class.class);
    }

    private static final LocationIdentity INSTANCE_KLASS_CONSTANTS = NamedLocationIdentity.immutable("InstanceKlass::_constants");
    private static final LocationIdentity CONSTANT_POOL_LENGTH = NamedLocationIdentity.immutable("ConstantPool::_length");

    /**
     * Emits a node to get the metaspace {@code ConstantPool} pointer given the value of the
     * {@code constantPoolOop} field in a ConstantPool value.
     *
     * @param constantPoolOop value of the {@code constantPoolOop} field in a ConstantPool value
     * @return a node representing the metaspace {@code ConstantPool} pointer associated with
     *         {@code constantPoolOop}
     */
    private static ValueNode getMetaspaceConstantPool(GraphBuilderContext b, ValueNode constantPoolOop, WordTypes wordTypes, GraalHotSpotVMConfig config) {
        // ConstantPool.constantPoolOop is in fact the holder class.
        ClassGetHubNode klass = b.add(new ClassGetHubNode(constantPoolOop));

        boolean notCompressible = false;
        AddressNode constantsAddress = b.add(new OffsetAddressNode(klass, b.add(ConstantNode.forLong(config.instanceKlassConstantsOffset))));
        return WordOperationPlugin.readOp(b, wordTypes.getWordKind(), constantsAddress, INSTANCE_KLASS_CONSTANTS, BarrierType.NONE, notCompressible);
    }

    /**
     * Emits a node representing an element in a metaspace {@code ConstantPool}.
     *
     * @param constantPoolOop value of the {@code constantPoolOop} field in a ConstantPool value
     */
    private static boolean readMetaspaceConstantPoolElement(GraphBuilderContext b, ValueNode constantPoolOop, ValueNode index, JavaKind elementKind, WordTypes wordTypes, GraalHotSpotVMConfig config) {
        ValueNode constants = getMetaspaceConstantPool(b, constantPoolOop, wordTypes, config);
        int shift = CodeUtil.log2(wordTypes.getWordKind().getByteCount());
        ValueNode scaledIndex = b.add(new LeftShiftNode(index, b.add(ConstantNode.forInt(shift))));
        ValueNode offset = b.add(new AddNode(scaledIndex, b.add(ConstantNode.forInt(config.constantPoolSize))));
        AddressNode elementAddress = b.add(new OffsetAddressNode(constants, offset));
        boolean notCompressible = false;
        ValueNode elementValue = WordOperationPlugin.readOp(b, elementKind, elementAddress, NamedLocationIdentity.getArrayLocation(elementKind), BarrierType.NONE, notCompressible);
        b.addPush(elementKind, elementValue);
        return true;
    }

    private static void registerConstantPoolPlugins(InvocationPlugins plugins, WordTypes wordTypes, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, constantPoolClass, bytecodeProvider);

        r.register2("getSize0", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop) {
                boolean notCompressible = false;
                ValueNode constants = getMetaspaceConstantPool(b, constantPoolOop, wordTypes, config);
                AddressNode lengthAddress = b.add(new OffsetAddressNode(constants, b.add(ConstantNode.forLong(config.constantPoolLengthOffset))));
                ValueNode length = WordOperationPlugin.readOp(b, JavaKind.Int, lengthAddress, CONSTANT_POOL_LENGTH, BarrierType.NONE, notCompressible);
                b.addPush(JavaKind.Int, length);
                return true;
            }
        });

        r.register3("getIntAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Int, wordTypes, config);
            }
        });
        r.register3("getLongAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Long, wordTypes, config);
            }
        });
        r.register3("getFloatAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Float, wordTypes, config);
            }
        });
        r.register3("getDoubleAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Double, wordTypes, config);
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, System.class);
        r.register0("currentTimeMillis", new ForeignCallPlugin(foreignCalls, HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS));
        r.register0("nanoTime", new ForeignCallPlugin(foreignCalls, HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS));
        r.register1("identityHashCode", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(object));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.register5("arraycopy", Object.class, int.class, Object.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
                b.add(new ArrayCopyNode(b.bci(), src, srcPos, dst, dstPos, length));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Thread.class, bytecodeProvider);
        r.register0("currentThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                boolean compressible = false;
                ValueNode offset = b.add(ConstantNode.forLong(config.threadObjectOffset));
                AddressNode address = b.add(new OffsetAddressNode(thread, offset));
                ValueNode javaThread = WordOperationPlugin.readOp(b, JavaKind.Object, address, JAVA_THREAD_THREAD_OBJECT_LOCATION, BarrierType.NONE, compressible);
                boolean exactType = false;
                boolean nonNull = true;
                b.addPush(JavaKind.Object, new PiNode(javaThread, metaAccess.lookupJavaType(Thread.class), exactType, nonNull));
                return true;
            }
        });

        r.registerMethodSubstitution(ThreadSubstitutions.class, "isInterrupted", Receiver.class, boolean.class);
    }

    private static void registerStableOptionPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, StableOptionValue.class);
        r.register1("getValue", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    StableOptionValue<?> option = snippetReflection.asObject(StableOptionValue.class, (JavaConstant) receiver.get().asConstant());
                    b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReflection.forObject(option.getValue()), b.getMetaAccess()));
                    return true;
                }
                return false;
            }
        });
    }

    public static final String cbcEncryptName;
    public static final String cbcDecryptName;
    public static final String aesEncryptName;
    public static final String aesDecryptName;

    public static final String reflectionClass;
    public static final String constantPoolClass;

    static {
        if (Java8OrEarlier) {
            cbcEncryptName = "encrypt";
            cbcDecryptName = "decrypt";
            aesEncryptName = "encryptBlock";
            aesDecryptName = "decryptBlock";
            reflectionClass = "sun.reflect.Reflection";
            constantPoolClass = "sun.reflect.ConstantPool";
        } else {
            cbcEncryptName = "implEncrypt";
            cbcDecryptName = "implDecrypt";
            aesEncryptName = "implEncryptBlock";
            aesDecryptName = "implDecryptBlock";
            reflectionClass = "jdk.internal.reflect.Reflection";
            constantPoolClass = "jdk.internal.reflect.ConstantPool";
        }
    }

    private static void registerAESPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useAESIntrinsics) {
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
            String arch = config.osArch;
            String decryptSuffix = arch.equals("sparc") ? "WithOriginalKey" : "";
            Registration r = new Registration(plugins, "com.sun.crypto.provider.CipherBlockChaining", bytecodeProvider);
            r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, cbcEncryptName, Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
            r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, cbcDecryptName, cbcDecryptName + decryptSuffix, Receiver.class, byte[].class, int.class, int.class, byte[].class,
                            int.class);
            r = new Registration(plugins, "com.sun.crypto.provider.AESCrypt", bytecodeProvider);
            r.registerMethodSubstitution(AESCryptSubstitutions.class, aesEncryptName, Receiver.class, byte[].class, int.class, byte[].class, int.class);
            r.registerMethodSubstitution(AESCryptSubstitutions.class, aesDecryptName, aesDecryptName + decryptSuffix, Receiver.class, byte[].class, int.class, byte[].class, int.class);
        }
    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, BigInteger.class, bytecodeProvider);
        if (config.useMultiplyToLenIntrinsic()) {
            assert config.multiplyToLen != 0L;
            if (Java8OrEarlier) {
                try {
                    Method m = BigInteger.class.getDeclaredMethod("multiplyToLen", int[].class, int.class, int[].class, int.class, int[].class);
                    if (Modifier.isStatic(m.getModifiers())) {
                        r.registerMethodSubstitution(BigIntegerSubstitutions.class, "multiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class, int.class,
                                        int[].class);
                    } else {
                        r.registerMethodSubstitution(BigIntegerSubstitutions.class, "multiplyToLen", Receiver.class, int[].class, int.class, int[].class, int.class,
                                        int[].class);
                    }
                } catch (NoSuchMethodException | SecurityException e) {
                    throw new GraalError(e);
                }
            } else {
                r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMultiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class, int.class,
                                int[].class);
            }
        }
        if (config.useMulAddIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMulAdd", int[].class, int[].class, int.class, int.class, int.class);
        }
        if (config.useMontgomeryMultiplyIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMontgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class);
        }
        if (config.useMontgomerySquareIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMontgomerySquare", int[].class, int[].class, int.class, long.class, int[].class);
        }
        if (config.useSquareToLenIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implSquareToLen", int[].class, int.class, int[].class, int.class);
        }
    }

    private static void registerSHAPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useSHA1Intrinsics()) {
            assert config.sha1ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA", bytecodeProvider);
            r.registerMethodSubstitution(SHASubstitutions.class, SHASubstitutions.implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
        if (config.useSHA256Intrinsics()) {
            assert config.sha256ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA2", bytecodeProvider);
            r.registerMethodSubstitution(SHA2Substitutions.class, SHA2Substitutions.implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
        if (config.useSHA512Intrinsics()) {
            assert config.sha512ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA5", bytecodeProvider);
            r.registerMethodSubstitution(SHA5Substitutions.class, SHA5Substitutions.implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useCRC32Intrinsics) {
            Registration r = new Registration(plugins, CRC32.class, bytecodeProvider);
            r.registerMethodSubstitution(CRC32Substitutions.class, "update", int.class, int.class);
            if (Java8OrEarlier) {
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateBytes", int.class, byte[].class, int.class, int.class);
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateByteBuffer", int.class, long.class, int.class, int.class);
            } else {
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateBytes0", int.class, byte[].class, int.class, int.class);
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateByteBuffer0", int.class, long.class, int.class, int.class);
            }
        }
    }
}
