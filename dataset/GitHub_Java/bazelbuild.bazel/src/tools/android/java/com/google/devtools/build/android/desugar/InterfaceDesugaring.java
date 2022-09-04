// Copyright 2017 The Bazel Authors. All rights reserved.
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

import javax.annotation.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/**
 * Visitor that moves methods with bodies from interfaces into a companion class and rewrites call
 * sites accordingly (which is only needed for static interface methods). Default methods are kept
 * as abstract methods with all their annotations.
 *
 * <p>Any necessary companion classes will be added to the given {@link GeneratedClassStore}. It's
 * the caller's responsibility to write those out.
 *
 * <p>Relies on {@link DefaultMethodClassFixer} to stub in method bodies for moved default methods.
 * Assumes that lambdas are already desugared. Ignores bridge methods, which are handled specially.
 */
class InterfaceDesugaring extends ClassVisitor {

  static final String COMPANION_METHOD_TO_TRIGGER_INTERFACE_CLINIT_NAME = "$$triggerInterfaceInit";
  static final String COMPANION_METHOD_TO_TRIGGER_INTERFACE_CLINIT_DESC = "()V";

  static final String INTERFACE_STATIC_COMPANION_METHOD_SUFFIX = "$$STATIC$$";

  private final ClassVsInterface interfaceCache;
  private final DependencyCollector depsCollector;
  private final ClassReaderFactory bootclasspath;
  private final GeneratedClassStore store;
  private final boolean legacyJaCoCo;

  private String internalName;
  private int bytecodeVersion;
  private int accessFlags;
  private int numberOfDefaultMethods;
  @Nullable private ClassVisitor companion;
  @Nullable private FieldInfo interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit;

  public InterfaceDesugaring(
      ClassVisitor dest,
      ClassVsInterface interfaceCache,
      DependencyCollector depsCollector,
      ClassReaderFactory bootclasspath,
      GeneratedClassStore store,
      boolean legacyJaCoCo) {
    super(Opcodes.ASM5, dest);
    this.interfaceCache = interfaceCache;
    this.depsCollector = depsCollector;
    this.bootclasspath = bootclasspath;
    this.store = store;
    this.legacyJaCoCo = legacyJaCoCo;
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    companion = null;
    numberOfDefaultMethods = 0;
    internalName = name;
    bytecodeVersion = version;
    accessFlags = access;
    if (isInterface()) {
      interfaceCache.addKnownInterfaces(name);
      // Record interface hierarchy.  This helps avoid parsing .class files when double-checking
      // desugaring results later using collected dependency information.
      depsCollector.recordExtendedInterfaces(name, interfaces);
    } else {
      interfaceCache.addKnownClass(name);
    }
    interfaceCache.addKnownClass(superName).addKnownInterfaces(interfaces);
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitEnd() {
    if (companion != null) {
      // Record classes with default methods.  This increases precision when double-checking
      // desugaring results later, without parsing .class files again, compared to just looking
      // for companion classes in a given desugared Jar which may only contain static methods.
      depsCollector.recordDefaultMethods(internalName, numberOfDefaultMethods);

      // Emit a method to access the fields of the interfaces that need initialization.
      emitInterfaceFieldAccessInCompanionMethodToTriggerInterfaceClinit();
      companion.visitEnd();
    }
    super.visitEnd();
  }

  private void emitInterfaceFieldAccessInCompanionMethodToTriggerInterfaceClinit() {
    if (companion == null
        || interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit == null) {
      return;
    }

    // Create a method to access the interface fields
    MethodVisitor visitor =
        checkNotNull(
            companion.visitMethod(
                Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC,
                COMPANION_METHOD_TO_TRIGGER_INTERFACE_CLINIT_NAME,
                COMPANION_METHOD_TO_TRIGGER_INTERFACE_CLINIT_DESC,
                null,
                null),
            "Cannot get a method visitor to write out %s to the companion class.",
            COMPANION_METHOD_TO_TRIGGER_INTERFACE_CLINIT_NAME);
    // Visit the interface field to triger <clinit> of the interface.

    visitor.visitFieldInsn(
        Opcodes.GETSTATIC,
        interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit.owner(),
        interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit.name(),
        interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit.desc());
    Type fieldType =
        Type.getType(interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit.desc());
    if (fieldType.getSort() == Type.LONG || fieldType.getSort() == Type.DOUBLE) {
      visitor.visitInsn(Opcodes.POP2);
    } else {
      visitor.visitInsn(Opcodes.POP);
    }
    visitor.visitInsn(Opcodes.RETURN);
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String desc, String signature, Object value) {
    if (legacyJaCoCo
        && isInterface()
        && BitFlags.isSet(access, Opcodes.ACC_FINAL)
        && "$jacocoData".equals(name)) {
      // Move $jacocoData field to companion class and remove final modifier. We'll rewrite field
      // accesses accordingly. Code generated by older JaCoCo versions tried to assign to this
      // final field in methods, and interface fields have to be private, so we move the field
      // to a class, which ends up looking pretty similar to what JaCoCo generates for classes.
      access &= ~Opcodes.ACC_FINAL;
      return companion().visitField(access, name, desc, signature, value);
    } else {
      return super.visitField(access, name, desc, signature, value);
    }
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    String codeOwner = internalName;
    MethodVisitor result;
    if (isInterface() && isStaticInitializer(name)) {
      result =
          new InterfaceFieldWriteCollector(
              super.visitMethod(access, name, desc, signature, exceptions));
      if (result != null && legacyJaCoCo) {
        result = new MoveJacocoFieldAccess(result);
      }
    } else if (isInterface()
        && BitFlags.noneSet(access, Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE)) {
      checkArgument(BitFlags.noneSet(access, Opcodes.ACC_NATIVE), "Forbidden per JLS ch 9.4");

      boolean isLambdaBody =
          name.startsWith("lambda$") && BitFlags.isSynthetic(access);
      if (isLambdaBody) {
        access &= ~Opcodes.ACC_PUBLIC; // undo visibility change from LambdaDesugaring
      }
      name = normalizeInterfaceMethodName(name, isLambdaBody, BitFlags.isStatic(access));
      codeOwner = getCompanionClassName(internalName);

      if (BitFlags.isStatic(access)) {
        // Completely move static interface methods, which requires rewriting call sites
        result =
            companion()
                .visitMethod(access & ~Opcodes.ACC_PRIVATE, name, desc, signature, exceptions);
      } else {
        MethodVisitor abstractDest;
        if (isLambdaBody) {
          // Completely move lambda bodies, which requires rewriting call sites
          access &= ~Opcodes.ACC_PRIVATE;
          abstractDest = null;
        } else {
          // Make default methods abstract but move their implementation into a static method with
          // corresponding signature.  Doesn't require callsite rewriting but implementing classes
          // may need to implement default methods explicitly.
          checkArgument(
              BitFlags.noneSet(access, Opcodes.ACC_PRIVATE),
              "Unexpected private interface method %s.%s : %s",
              name,
              internalName,
              desc);
          ++numberOfDefaultMethods;
          abstractDest =
              super.visitMethod(access | Opcodes.ACC_ABSTRACT, name, desc, signature, exceptions);
        }

        // TODO(b/37110951): adjust signature with explicit receiver type, which may be generic
        MethodVisitor codeDest =
            companion()
                .visitMethod(
                    access | Opcodes.ACC_STATIC,
                    name,
                    companionDefaultMethodDescriptor(internalName, desc),
                    (String) null, // drop signature, since given one doesn't include the new param
                    exceptions);

        result = abstractDest != null ? new MultiplexAnnotations(codeDest, abstractDest) : codeDest;
      }
      if (result != null && legacyJaCoCo) {
        result = new MoveJacocoFieldAccess(result);
      }
    } else {
      result = super.visitMethod(access, name, desc, signature, exceptions);
    }
    return result != null
        ? new InterfaceInvocationRewriter(
            result, isInterface() ? internalName : null, bootclasspath, depsCollector, codeOwner)
        : null;
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    // Proguard gets grumpy if an outer method doesn't exist, which can be the result of moving
    // interface methods to companion classes (b/68260836).  In that case (for which we need to
    // figure out if "owner" is an interface) need to adjust the outer method information.
    if (name != null && interfaceCache.isOuterInterface(owner, internalName)) {
      // Just drop outer method info.  That's unfortunate, but the only alternative would be to
      // change the outer method to point to the companion class, which would mean the
      // reflection methods that use this information would return a companion ($$CC) class name
      // as well as a possibly-modified method name and signature, so it seems better to return
      // the correct original interface name and no method information.  Doing this also saves
      // us from doing even more work to figure out whether the method is static and a lambda
      // method, which we'd need to known to adjust name and descriptor correctly.
      name = null;
      desc = null;
    } // otherwise there's no enclosing method that could've been moved, or owner is a class
    super.visitOuterClass(owner, name, desc);
  }

  private boolean isInterface() {
    return BitFlags.isInterface(accessFlags);
  }

  private static boolean isStaticInitializer(String methodName) {
    return "<clinit>".equals(methodName);
  }

  private static String normalizeInterfaceMethodName(
      String name, boolean isLambda, boolean isStatic) {
    if (isLambda) {
      // Rename lambda method to reflect the new owner.  Not doing so confuses LambdaDesugaring
      // if it's run over this class again. LambdaDesugaring has already renamed the method from
      // its original name to include the interface name at this point.
      return name + DependencyCollector.INTERFACE_COMPANION_SUFFIX;
    } else if (isStatic) {
      return name + INTERFACE_STATIC_COMPANION_METHOD_SUFFIX;
    } else {
      return name;
    }
  }

  static String getCompanionClassName(String interfaceName) {
    return interfaceName + DependencyCollector.INTERFACE_COMPANION_SUFFIX;
  }

  /**
   * Returns the descriptor of a static method for an instance method with the given receiver and
   * description, simply by pre-pending the given descriptor's parameter list with the given
   * receiver type.
   */
  static String companionDefaultMethodDescriptor(String interfaceName, String desc) {
    Type type = Type.getMethodType(desc);
    Type[] companionArgs = new Type[type.getArgumentTypes().length + 1];
    companionArgs[0] = Type.getObjectType(interfaceName);
    System.arraycopy(type.getArgumentTypes(), 0, companionArgs, 1, type.getArgumentTypes().length);
    return Type.getMethodDescriptor(type.getReturnType(), companionArgs);
  }

  private ClassVisitor companion() {
    if (companion == null) {
      checkState(isInterface());
      String companionName = getCompanionClassName(internalName);

      companion = store.add(companionName);
      companion.visit(
          bytecodeVersion,
          // Companion class must be public so moved methods can be called from anywhere
          (accessFlags | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC) & ~Opcodes.ACC_INTERFACE,
          companionName,
          (String) null, // signature
          "java/lang/Object",
          new String[0]);
    }
    return companion;
  }

  /**
   * Interface field scanner to get the first field of the current interface that is written in the
   * initializer.
   */
  private class InterfaceFieldWriteCollector extends MethodVisitor {

    public InterfaceFieldWriteCollector(MethodVisitor mv) {
      super(Opcodes.ASM5, mv);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      if (interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit == null
          && opcode == Opcodes.PUTSTATIC
          && owner.equals(internalName)) {
        // It is possible that an interface initializer can sets fields of other classes.
        // (b/64290760), so we test whether the owner is the same as the internalName.
        interfaceFieldToAccessInCompanionMethodToTriggerInterfaceClinit =
            FieldInfo.create(owner, name, desc);
      }
      super.visitFieldInsn(opcode, owner, name, desc);
    }
  }

  /**
   * Rewriter for calls to static interface methods and super calls to default methods, unless
   * they're part of the bootclasspath, as well as all lambda body methods. Keeps calls to interface
   * methods declared in the bootclasspath as-is (but note that these would presumably fail on
   * devices without those methods).
   */
  static class InterfaceInvocationRewriter extends MethodVisitor {

    /**
     * If we're visiting a method declared in an interface, the internal name of that interface.
     * That lets us rewrite invocations of other methods within that interface even if the bytecode
     * fails to indicate them as interface method invocations, as older versions of JaCoCo failed to
     * do (b/62623509).
     */
    @Nullable private final String interfaceName;

    private final ClassReaderFactory bootclasspath;
    private final DependencyCollector depsCollector;
    /** Internal name that'll be used to record any dependencies on interface methods. */
    private final String declaringClass;

    public InterfaceInvocationRewriter(
        MethodVisitor dest,
        @Nullable String knownInterfaceName,
        ClassReaderFactory bootclasspath,
        DependencyCollector depsCollector,
        String declaringClass) {
      super(Opcodes.ASM5, dest);
      this.interfaceName = knownInterfaceName;
      this.bootclasspath = bootclasspath;
      this.depsCollector = depsCollector;
      this.declaringClass = declaringClass;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      // Assume that any static interface methods on the classpath are moved
      if ((itf || owner.equals(interfaceName)) && !bootclasspath.isKnown(owner)) {
        boolean isLambda = name.startsWith("lambda$");
        name = normalizeInterfaceMethodName(name, isLambda, opcode == Opcodes.INVOKESTATIC);
        if (isLambda) {
          // Redirect lambda invocations to completely remove all lambda methods from interfaces.
          checkArgument(!owner.endsWith(DependencyCollector.INTERFACE_COMPANION_SUFFIX),
              "shouldn't consider %s an interface", owner);
          if (opcode == Opcodes.INVOKEINTERFACE) {
            opcode = Opcodes.INVOKESTATIC;
            desc = companionDefaultMethodDescriptor(owner, desc);
          } else {
            checkArgument(
                opcode == Opcodes.INVOKESTATIC,
                "Unexpected opcode %s to invoke %s.%s",
                opcode,
                owner,
                name);
          }
          // Reflect that InterfaceDesugaring moves and renames the lambda body method
          owner += DependencyCollector.INTERFACE_COMPANION_SUFFIX;
          itf = false;
          // Record dependency on companion class
          depsCollector.assumeCompanionClass(declaringClass, owner);

          String expectedLambdaMethodName = LambdaDesugaring.uniqueInPackage(owner, name);
          checkState(
              name.equals(expectedLambdaMethodName),
              "Unexpected lambda body method name for %s: real=%s, expected=%s",
              owner,
              name,
              expectedLambdaMethodName);
        } else if ((opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL)) {
          checkArgument(!owner.endsWith(DependencyCollector.INTERFACE_COMPANION_SUFFIX),
              "shouldn't consider %s an interface", owner);
          if (opcode == Opcodes.INVOKESPECIAL) {
            // Turn Interface.super.m() into Interface$$CC.m(receiver)
            opcode = Opcodes.INVOKESTATIC;
            desc = companionDefaultMethodDescriptor(owner, desc);
          }
          owner += DependencyCollector.INTERFACE_COMPANION_SUFFIX;
          itf = false;
          // Record dependency on companion class
          depsCollector.assumeCompanionClass(declaringClass, owner);
        }
      }
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
  }

  /**
   * Method visitor intended for interface method bodies that rewrites jacoco field accesses to
   * expect the field in the companion class, to work around problematic bytecode emitted by older
   * JaCoCo versions (b/62623509).
   */
  private static class MoveJacocoFieldAccess extends MethodVisitor {

    public MoveJacocoFieldAccess(MethodVisitor mv) {
      super(Opcodes.ASM5, mv);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      if ("$jacocoData".equals(name)) {
        checkState(!owner.endsWith(DependencyCollector.INTERFACE_COMPANION_SUFFIX),
            "Expected interface: %s", owner);
        owner = getCompanionClassName(owner);
      }
      super.visitFieldInsn(opcode, owner, name, desc);
    }
  }

  /**
   * Method visitor that behaves like a passthrough but additionally duplicates all annotations into
   * a second given {@link MethodVisitor}.
   */
  private static class MultiplexAnnotations extends MethodVisitor {

    private final MethodVisitor annotationOnlyDest;

    public MultiplexAnnotations(@Nullable MethodVisitor dest, MethodVisitor annotationOnlyDest) {
      super(Opcodes.ASM5, dest);
      this.annotationOnlyDest = annotationOnlyDest;
    }

    @Override
    public void visitParameter(String name, int access) {
      super.visitParameter(name, access);
      annotationOnlyDest.visitParameter(name, access);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      AnnotationVisitor dest = super.visitAnnotation(desc, visible);
      AnnotationVisitor annoDest = annotationOnlyDest.visitAnnotation(desc, visible);
      return new MultiplexAnnotationVisitor(dest, annoDest);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeRef, TypePath typePath, String desc, boolean visible) {
      AnnotationVisitor dest = super.visitTypeAnnotation(typeRef, typePath, desc, visible);
      AnnotationVisitor annoDest =
          annotationOnlyDest.visitTypeAnnotation(typeRef, typePath, desc, visible);
      return new MultiplexAnnotationVisitor(dest, annoDest);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      AnnotationVisitor dest = super.visitParameterAnnotation(parameter, desc, visible);
      AnnotationVisitor annoDest =
          annotationOnlyDest.visitParameterAnnotation(parameter, desc, visible);
      return new MultiplexAnnotationVisitor(dest, annoDest);
    }
  }

  /**
   * Annotation visitor that recursively passes the visited annotations to any number of given
   * {@link AnnotationVisitor}s.
   */
  private static class MultiplexAnnotationVisitor extends AnnotationVisitor {

    private final AnnotationVisitor[] moreDestinations;

    public MultiplexAnnotationVisitor(
        @Nullable AnnotationVisitor dest, AnnotationVisitor... moreDestinations) {
      super(Opcodes.ASM5, dest);
      this.moreDestinations = moreDestinations;
    }

    @Override
    public void visit(String name, Object value) {
      super.visit(name, value);
      for (AnnotationVisitor dest : moreDestinations) {
        dest.visit(name, value);
      }
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      super.visitEnum(name, desc, value);
      for (AnnotationVisitor dest : moreDestinations) {
        dest.visitEnum(name, desc, value);
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      AnnotationVisitor[] subVisitors = new AnnotationVisitor[moreDestinations.length];
      AnnotationVisitor dest = super.visitAnnotation(name, desc);
      for (int i = 0; i < subVisitors.length; ++i) {
        subVisitors[i] = moreDestinations[i].visitAnnotation(name, desc);
      }
      return new MultiplexAnnotationVisitor(dest, subVisitors);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor[] subVisitors = new AnnotationVisitor[moreDestinations.length];
      AnnotationVisitor dest = super.visitArray(name);
      for (int i = 0; i < subVisitors.length; ++i) {
        subVisitors[i] = moreDestinations[i].visitArray(name);
      }
      return new MultiplexAnnotationVisitor(dest, subVisitors);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      for (AnnotationVisitor dest : moreDestinations) {
        dest.visitEnd();
      }
    }
  }
}
