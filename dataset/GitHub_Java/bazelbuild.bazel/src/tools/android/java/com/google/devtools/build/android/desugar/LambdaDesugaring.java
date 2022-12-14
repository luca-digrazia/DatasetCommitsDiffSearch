// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android.desugar;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.invoke.MethodHandles.publicLookup;
import static org.objectweb.asm.Opcodes.ASM5;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Visitor that desugars classes with uses of lambdas into Java 7-looking code.  This includes
 * rewriting lambda-related invokedynamic instructions as well as fixing accessibility of methods
 * that javac emits for lambda bodies.
 */
class LambdaDesugaring extends ClassVisitor {

  private final ClassLoader targetLoader;
  private final LambdaClassMaker lambdas;
  private final ImmutableSet.Builder<String> aggregateInterfaceLambdaMethods;
  private final Map<Handle, MethodReferenceBridgeInfo> bridgeMethods = new HashMap<>();

  private String internalName;
  private boolean isInterface;

  public LambdaDesugaring(ClassVisitor dest, ClassLoader targetLoader, LambdaClassMaker lambdas,
      ImmutableSet.Builder<String> aggregateInterfaceLambdaMethods) {
    super(Opcodes.ASM5, dest);
    this.targetLoader = targetLoader;
    this.lambdas = lambdas;
    this.aggregateInterfaceLambdaMethods = aggregateInterfaceLambdaMethods;
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    internalName = name;
    isInterface = BitFlags.isSet(access, Opcodes.ACC_INTERFACE);
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitEnd() {
    for (Map.Entry<Handle, MethodReferenceBridgeInfo> bridge : bridgeMethods.entrySet()) {
      Handle original = bridge.getKey();
      Handle neededMethod = bridge.getValue().bridgeMethod();
      checkState(neededMethod.getTag() == Opcodes.H_INVOKESTATIC
          || neededMethod.getTag() == Opcodes.H_INVOKEVIRTUAL,
          "Cannot generate bridge method %s to reach %s", neededMethod, original);
      checkState(bridge.getValue().referenced() != null,
          "Need referenced method %s to generate bridge %s", original, neededMethod);

      int access = Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL;
      if (neededMethod.getTag() == Opcodes.H_INVOKESTATIC) {
        access |= Opcodes.ACC_STATIC;
      }
      MethodVisitor bridgeMethod =
          super.visitMethod(
              access,
              neededMethod.getName(),
              neededMethod.getDesc(),
              (String) null,
              toInternalNames(bridge.getValue().referenced().getExceptionTypes()));

      // Bridge is a factory method calling a constructor
      if (original.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
        bridgeMethod.visitTypeInsn(Opcodes.NEW, original.getOwner());
        bridgeMethod.visitInsn(Opcodes.DUP);
      }

      int slot = 0;
      if (neededMethod.getTag() != Opcodes.H_INVOKESTATIC) {
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, slot++);
      }
      Type neededType = Type.getMethodType(neededMethod.getDesc());
      for (Type arg : neededType.getArgumentTypes()) {
        bridgeMethod.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
        slot += arg.getSize();
      }
      bridgeMethod.visitMethodInsn(invokeOpcode(original), original.getOwner(), original.getName(),
          original.getDesc(), original.isInterface());
      bridgeMethod.visitInsn(neededType.getReturnType().getOpcode(Opcodes.IRETURN));

      bridgeMethod.visitMaxs(0, 0); // rely on class writer to compute these
      bridgeMethod.visitEnd();
    }
    super.visitEnd();
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    if (name.equals("$deserializeLambda$") && BitFlags.isSet(access, Opcodes.ACC_SYNTHETIC)) {
      // Android doesn't do anything special for lambda serialization so drop the special
      // deserialization hook that javac generates.  This also makes sure we don't reference
      // java/lang/invoke/SerializedLambda, which doesn't exist on Android.
      return null;
    }
    if (name.startsWith("lambda$") && BitFlags.isSet(access, Opcodes.ACC_SYNTHETIC)) {
      if (isInterface && BitFlags.isSet(access, Opcodes.ACC_STATIC)) {
        // There must be a lambda in the interface (which in the absence of hand-written default or
        // static interface methods must mean it's in the <clinit> method or inside another lambda).
        // We'll move this method out of this class, so just record and drop it here.
        // (Note lambda body methods have unique names, so we don't need to remember desc here.)
        aggregateInterfaceLambdaMethods.add(internalName + '#' + name);
        return null;
      }
      if (BitFlags.isSet(access, Opcodes.ACC_PRIVATE)) {
        // Make lambda body method accessible from lambda class
        access &= ~Opcodes.ACC_PRIVATE;
        // Method was private so it can be final, which should help VMs perform dispatch.
        access |= Opcodes.ACC_FINAL;
      }
      // Guarantee unique lambda body method name to avoid accidental overriding. This wouldn't be
      // be necessary for static methods but in visitOuterClass we don't know whether a potential
      // outer lambda$ method is static or not, so we just always do it.
      name = uniqueInPackage(internalName, name);
    }
    MethodVisitor dest = super.visitMethod(access, name, desc, signature, exceptions);
    return new InvokedynamicRewriter(dest);
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    if (name != null && name.startsWith("lambda$")) {
      // Reflect renaming of lambda$ methods.  Proguard gets grumpy if we leave this inconsistent.
      name = uniqueInPackage(owner, name);
    }
    super.visitOuterClass(owner, name, desc);
  }

  static String uniqueInPackage(String owner, String name) {
    String suffix = "$" + owner.substring(owner.lastIndexOf('/') + 1);
    // For idempotency, we only attach the package-unique suffix if it isn't there already.  This
    // prevents a cumulative effect when processing a class more than once (which can happen with
    // Bazel, e.g., when re-importing a deploy.jar).  During reprocessing, invokedynamics are
    // already removed, so lambda$ methods have regular call sites that we would also have to re-
    // adjust if we just blindly appended something to lambda$ method names every time we see them.
    return name.endsWith(suffix) ? name : name + suffix;
  }

  /**
   * Makes {@link #visitEnd} generate a bridge method for the given method handle if the
   * referenced method will be invisible to the generated lambda class.
   *
   * @return struct containing either {@code invokedMethod} or {@code invokedMethod} and a handle
   *     representing the bridge method that will be generated for {@code invokedMethod}.
   */
  private MethodReferenceBridgeInfo queueUpBridgeMethodIfNeeded(Handle invokedMethod)
      throws ClassNotFoundException {
    if (invokedMethod.getName().startsWith("lambda$")) {
      // We adjust lambda bodies to be visible
      return MethodReferenceBridgeInfo.noBridge(invokedMethod);
    }

    // invokedMethod is a method reference if we get here
    Executable invoked = findTargetMethod(invokedMethod);
    if (isVisibleToLambdaClass(invoked, invokedMethod.getOwner())) {
      // Referenced method is visible to the generated class, so nothing to do
      return MethodReferenceBridgeInfo.noBridge(invokedMethod);
    }

    // We need a bridge method if we get here
    checkState(!isInterface,
        "%s is an interface and shouldn't need bridge to %s", internalName, invokedMethod);
    checkState(!invokedMethod.isInterface(),
        "%s's lambda classes can't see interface method: %s", internalName, invokedMethod);
    MethodReferenceBridgeInfo result = bridgeMethods.get(invokedMethod);
    if (result != null) {
      return result; // we're already queued up a bridge method for this method reference
    }

    String name = "bridge$lambda$" + bridgeMethods.size();
    Handle bridgeMethod;
    switch (invokedMethod.getTag()) {
      case Opcodes.H_INVOKESTATIC:
        bridgeMethod = new Handle(invokedMethod.getTag(), internalName, name,
            invokedMethod.getDesc(), /*itf*/ false);
        break;
      case Opcodes.H_INVOKEVIRTUAL:
      case Opcodes.H_INVOKESPECIAL: // we end up calling these using invokevirtual
        bridgeMethod = new Handle(Opcodes.H_INVOKEVIRTUAL, internalName, name,
            invokedMethod.getDesc(), /*itf*/ false);
        break;
      case Opcodes.H_NEWINVOKESPECIAL: {
        // Call invisible constructor through generated bridge "factory" method, so we need to
        // compute the descriptor for the bridge method from the constructor's descriptor
        String desc =
            Type.getMethodDescriptor(
                Type.getObjectType(invokedMethod.getOwner()),
                Type.getArgumentTypes(invokedMethod.getDesc()));
        bridgeMethod = new Handle(Opcodes.H_INVOKESTATIC, internalName, name, desc, /*itf*/ false);
        break;
      }
      case Opcodes.H_INVOKEINTERFACE:
        // Shouldn't get here
      default:
        throw new UnsupportedOperationException("Cannot bridge " + invokedMethod);
    }
    result = MethodReferenceBridgeInfo.bridge(invokedMethod, invoked, bridgeMethod);
    MethodReferenceBridgeInfo old = bridgeMethods.put(invokedMethod, result);
    checkState(old == null, "Already had bridge %s so we don't also want %s", old, result);
    return result;
  }

  /**
   * Checks whether the referenced method would be visible by an unrelated class in the same package
   * as the currently visited class.
   */
  private boolean isVisibleToLambdaClass(Executable invoked, String owner) {
    int modifiers = invoked.getModifiers();
    if (Modifier.isPrivate(modifiers)) {
      return false;
    }
    if (Modifier.isPublic(modifiers)) {
      return true;
    }
    // invoked is protected or package-private, either way we need it to be in the same package
    // because the additional visibility protected gives doesn't help lambda classes, which are in
    // a different class hierarchy (and typically just extend Object)
    return packageName(internalName).equals(packageName(owner));
  }

  private Executable findTargetMethod(Handle invokedMethod) throws ClassNotFoundException {
    Type descriptor = Type.getMethodType(invokedMethod.getDesc());
    Class<?> owner = loadFromInternal(invokedMethod.getOwner());
    if (invokedMethod.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
      for (Constructor<?> c : owner.getDeclaredConstructors()) {
        if (Type.getType(c).equals(descriptor)) {
          return c;
        }
      }
    } else {
      for (Method m : owner.getDeclaredMethods()) {
        if (m.getName().equals(invokedMethod.getName())
            && Type.getType(m).equals(descriptor)) {
          return m;
        }
      }
    }
    throw new IllegalArgumentException("Referenced method not found: " + invokedMethod);
  }

  private Class<?> loadFromInternal(String internalName) throws ClassNotFoundException {
    return targetLoader.loadClass(internalName.replace('/', '.'));
  }

  static int invokeOpcode(Handle invokedMethod) {
    switch (invokedMethod.getTag()) {
      case Opcodes.H_INVOKESTATIC:
        return Opcodes.INVOKESTATIC;
      case Opcodes.H_INVOKEVIRTUAL:
        return Opcodes.INVOKEVIRTUAL;
      case Opcodes.H_INVOKESPECIAL:
      case Opcodes.H_NEWINVOKESPECIAL: // Must be preceded by NEW
        return Opcodes.INVOKESPECIAL;
      case Opcodes.H_INVOKEINTERFACE:
        return Opcodes.INVOKEINTERFACE;
      default:
        throw new UnsupportedOperationException("Don't know how to call " + invokedMethod);
    }
  }

  private static String[] toInternalNames(Class<?>[] classes) {
    String[] result = new String[classes.length];
    for (int i = 0; i < classes.length; ++i) {
      result[i] = Type.getInternalName(classes[i]);
    }
    return result;
  }

  private static String packageName(String internalClassName) {
    int lastSlash = internalClassName.lastIndexOf('/');
    return lastSlash > 0 ? internalClassName.substring(0, lastSlash) : "";
  }

  /**
   * Desugaring that replaces invokedynamics for {@link java.lang.invoke.LambdaMetafactory} with
   * static factory method invocations and triggers a class to be generated for each invokedynamic.
   */
  private class InvokedynamicRewriter extends MethodVisitor {

    public InvokedynamicRewriter(MethodVisitor dest) {
      super(ASM5, dest);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      if (!"java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())) {
        // Not an invokedynamic for a lambda expression
        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        return;
      }

      try {
        Lookup lookup = createLookup(internalName);
        ArrayList<Object> args = new ArrayList<>(bsmArgs.length + 3);
        args.add(lookup);
        args.add(name);
        args.add(MethodType.fromMethodDescriptorString(desc, targetLoader));
        for (Object bsmArg : bsmArgs) {
          args.add(toJvmMetatype(lookup, bsmArg));
        }

        // Both bootstrap methods in LambdaMetafactory expect a MethodHandle as their 5th argument
        // so we can assume bsmArgs[1] (the 5th arg) to be a Handle.
        MethodReferenceBridgeInfo bridgeInfo = queueUpBridgeMethodIfNeeded((Handle) bsmArgs[1]);

        // Resolve the bootstrap method in "host configuration" (this tool's default classloader)
        // since targetLoader may only contain stubs that we can't actually execute.
        // generateLambdaClass() below will invoke the bootstrap method, so a stub isn't enough,
        // and ultimately we don't care if the bootstrap method was even on the bootclasspath
        // when this class was compiled (although it must've been since javac is unhappy otherwise).
        MethodHandle bsmMethod = toMethodHandle(publicLookup(), bsm, /*target*/ false);
        String lambdaClassName = lambdas.generateLambdaClass(
            internalName,
            LambdaInfo.create(desc, bridgeInfo.methodReference(), bridgeInfo.bridgeMethod()),
            bsmMethod,
            args);
        // Emit invokestatic that calls the factory method generated in the lambda class
        super.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            lambdaClassName,
            LambdaClassFixer.FACTORY_METHOD_NAME,
            desc,
            /*itf*/ false);
      } catch (IOException | ReflectiveOperationException e) {
        throw new IllegalStateException("Couldn't desugar invokedynamic for " + internalName + "."
            + name + " using " + bsm + " with arguments " + Arrays.toString(bsmArgs), e);
      }
    }

    private Lookup createLookup(String lookupClass) throws ReflectiveOperationException {
      Class<?> clazz = loadFromInternal(lookupClass);
      Constructor<Lookup> constructor = Lookup.class.getDeclaredConstructor(Class.class);
      constructor.setAccessible(true);
      return constructor.newInstance(clazz);
    }

    /**
     * Produces a {@link MethodHandle} or {@link MethodType} using {@link #targetLoader} for the
     * given ASM {@link Handle} or {@link Type}. {@code lookup} is only used for resolving
     * {@link Handle}s.
     */
    private Object toJvmMetatype(Lookup lookup, Object asm) throws ReflectiveOperationException {
      if (asm instanceof Number) {
        return asm;
      }
      if (asm instanceof Type) {
        Type type = (Type) asm;
        switch (type.getSort()) {
          case Type.OBJECT:
            return loadFromInternal(type.getInternalName());
          case Type.METHOD:
            return MethodType.fromMethodDescriptorString(type.getDescriptor(), targetLoader);
          default:
            throw new IllegalArgumentException("Cannot convert: " + asm);
        }
      }
      if (asm instanceof Handle) {
        return toMethodHandle(lookup, (Handle) asm, /*target*/ true);
      }
      throw new IllegalArgumentException("Cannot convert: " + asm);
    }

    /**
     * Produces a {@link MethodHandle} using either the context or {@link #targetLoader} class
     * loader, depending on {@code target}.
     */
    private MethodHandle toMethodHandle(Lookup lookup, Handle asmHandle, boolean target)
        throws ReflectiveOperationException {
      Class<?> owner = loadFromInternal(asmHandle.getOwner());
      MethodType signature = MethodType.fromMethodDescriptorString(asmHandle.getDesc(),
          target ? targetLoader : Thread.currentThread().getContextClassLoader());
      switch (asmHandle.getTag()) {
        case Opcodes.H_INVOKESTATIC:
          return lookup.findStatic(owner, asmHandle.getName(), signature);
        case Opcodes.H_INVOKEVIRTUAL:
        case Opcodes.H_INVOKESPECIAL: // we end up calling these using invokevirtual
        case Opcodes.H_INVOKEINTERFACE:
          return lookup.findVirtual(owner, asmHandle.getName(), signature);
        case Opcodes.H_NEWINVOKESPECIAL:
          return lookup.findConstructor(owner, signature);
        default:
          throw new UnsupportedOperationException("Cannot resolve " + asmHandle);
      }
    }
  }

  /**
   * Record of how a lambda class can reach its referenced method through a possibly-different
   * bridge method.
   *
   * <p>In a JVM, lambda classes are allowed to call the referenced methods directly, but we don't
   * have that luxury when the generated lambda class is evaluated using normal visibility rules.
   */
  @AutoValue
  abstract static class MethodReferenceBridgeInfo {
    public static MethodReferenceBridgeInfo noBridge(Handle methodReference) {
      return new AutoValue_LambdaDesugaring_MethodReferenceBridgeInfo(
          methodReference, (Executable) null, methodReference);
    }
    public static MethodReferenceBridgeInfo bridge(
        Handle methodReference, Executable referenced, Handle bridgeMethod) {
      checkArgument(!bridgeMethod.equals(methodReference));
      return new AutoValue_LambdaDesugaring_MethodReferenceBridgeInfo(
          methodReference, checkNotNull(referenced), bridgeMethod);
    }

    public abstract Handle methodReference();

    /** Returns {@code null} iff {@link #bridgeMethod} equals {@link #methodReference}. */
    @Nullable public abstract Executable referenced();

    public abstract Handle bridgeMethod();
  }
}
