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

import com.google.devtools.build.android.desugar.io.BitFlags;
import javax.annotation.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

/**
 * Visitor that tries to ensures bytecode version <= 51 (Java 7) and that throws if it sees default
 * or static interface methods (i.e., non-abstract interface methods), which don't exist in Java 7.
 *
 * <p>The class version will 52 iff static interface method from the bootclasspath is invoked. This
 * is mostly ensure that the generated bytecode is valid.
 */
public class Java7Compatibility extends ClassVisitor {

  @Nullable private final ClassReaderFactory factory;
  @Nullable private final ClassReaderFactory bootclasspathReader;

  private boolean isInterface;
  private String internalName;
  private int access;
  private String signature;
  private String superName;
  private String[] interfaces;

  public Java7Compatibility(
      ClassVisitor cv, ClassReaderFactory factory, ClassReaderFactory bootclasspathReader) {
    super(Opcodes.ASM7, cv);
    this.factory = factory;
    this.bootclasspathReader = bootclasspathReader;
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
    this.access = access;
    this.signature = signature;
    this.superName = superName;
    this.interfaces = interfaces;
    isInterface = BitFlags.isSet(access, Opcodes.ACC_INTERFACE);
    // ASM uses the high 16 bits for the minor version:
    // https://asm.ow2.io/javadoc/org/objectweb/asm/ClassVisitor.html#visit-int-int-java.lang.String-java.lang.String-java.lang.String-java.lang.String:A-
    // See https://github.com/bazelbuild/bazel/issues/6299 for an example of a class file with a
    // non-zero minor version.
    int major = version & 0xffff;
    if (major > Opcodes.V1_7) {
      version = Opcodes.V1_7;
    }
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    // Remove bridge default methods in interfaces; javac generates them again for implementing
    // classes anyways.
    if (isInterface
        && (access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC))
            == Opcodes.ACC_BRIDGE) {
      return null;
    }
    if (isInterface
        && "$jacocoInit".equals(name)
        && BitFlags.isSet(access, Opcodes.ACC_SYNTHETIC | Opcodes.ACC_STATIC)) {
      // Drop static interface method that Jacoco generates--we'll inline it into the static
      // initializer instead
      return null;
    }
    checkArgument(
        !isInterface || BitFlags.isSet(access, Opcodes.ACC_ABSTRACT) || "<clinit>".equals(name),
        "Interface %s defines non-abstract method %s%s, which is not supported",
        internalName,
        name,
        desc);
    MethodVisitor result =
        new UpdateBytecodeVersionIfNecessary(
            super.visitMethod(access, name, desc, signature, exceptions));

    return (isInterface && "<clinit>".equals(name)) ? new InlineJacocoInit(result) : result;
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    // Drop MethodHandles$Lookup inner class information--it shouldn't be needed anymore.  Proguard
    // complains about this even though the inner class information is never used.
    if (!"java/lang/invoke/MethodHandles$Lookup".equals(name)) {
      super.visitInnerClass(name, outerName, innerName, access);
    }
  }

  /** This will rewrite class version to 52 if it sees invokestatic on an interface. */
  private class UpdateBytecodeVersionIfNecessary extends MethodVisitor {

    boolean updated = false;

    public UpdateBytecodeVersionIfNecessary(MethodVisitor methodVisitor) {
      super(Opcodes.ASM7, methodVisitor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      if (itf && opcode == Opcodes.INVOKESTATIC) {
        checkNotNull(bootclasspathReader);
        checkState(
            bootclasspathReader.isKnown(owner),
            "%s contains invocation of static interface method that is "
                + "not in the bootclasspath. Owner: %s, name: %s, desc: %s.",
            Java7Compatibility.this.internalName,
            owner,
            name,
            desc);
        if (!updated) {
          Java7Compatibility.this.cv.visit(
              Opcodes.V1_8,
              Java7Compatibility.this.access,
              Java7Compatibility.this.internalName,
              Java7Compatibility.this.signature,
              Java7Compatibility.this.superName,
              Java7Compatibility.this.interfaces);
          updated = true;
        }
      }
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
  }

  private class InlineJacocoInit extends MethodVisitor {
    public InlineJacocoInit(MethodVisitor dest) {
      super(Opcodes.ASM7, dest);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      if (opcode == Opcodes.INVOKESTATIC
          && "$jacocoInit".equals(name)
          && internalName.equals(owner)) {
        checkNotNull(factory);
        ClassReader bytecode =
            checkNotNull(
                factory.readIfKnown(internalName),
                "Couldn't load interface %s to inline $jacocoInit()",
                internalName);
        InlineOneMethod copier = new InlineOneMethod("$jacocoInit", this);
        bytecode.accept(copier, ClassReader.SKIP_DEBUG /* we're copying generated code anyway */);
      } else {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
      }
    }
  }

  private static class InlineOneMethod extends ClassVisitor {

    private final String methodName;
    private final MethodVisitor dest;
    private int copied = 0;

    public InlineOneMethod(String methodName, MethodVisitor dest) {
      super(Opcodes.ASM7);
      this.methodName = methodName;
      this.dest = dest;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      checkArgument(BitFlags.isSet(access, Opcodes.ACC_INTERFACE));
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      if (name.equals(methodName)) {
        checkState(copied == 0, "Found unexpected second method %s with descriptor %s", name, desc);
        ++copied;
        return new InlineMethodBody(dest);
      }
      return null;
    }
  }

  private static class InlineMethodBody extends MethodVisitor {
    private final MethodVisitor dest;

    public InlineMethodBody(MethodVisitor dest) {
      // We'll set the destination visitor in visitCode() to reduce the risk of copying anything
      // we didn't mean to copy
      super(Opcodes.ASM7, (MethodVisitor) null);
      this.dest = dest;
    }

    @Override
    public void visitParameter(String name, int access) {}

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      throw new IllegalStateException("Don't use to copy annotation attributes");
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return null;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeRef, TypePath typePath, String desc, boolean visible) {
      return null;
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      return null;
    }

    @Override
    public void visitAttribute(Attribute attr) {
      mv = null; // don't care about anything but the code attribute
    }

    @Override
    public void visitCode() {
      // Start copying instructions but don't call super.visitCode() since dest is already in the
      // middle of visiting another method
      mv = dest;
    }

    @Override
    public void visitInsn(int opcode) {
      switch (opcode) {
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.FRETURN:
        case Opcodes.DRETURN:
        case Opcodes.ARETURN:
        case Opcodes.RETURN:
          checkState(mv != null, "Encountered a second return it would seem: %s", opcode);
          mv = null; // Done: we don't expect anything to follow
          return;
        default:
          super.visitInsn(opcode);
      }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
      throw new UnsupportedOperationException(
          "We don't support inlining methods with locals: " + opcode + " " + var);
    }

    @Override
    public void visitLocalVariable(
        String name, String desc, String signature, Label start, Label end, int index) {
      throw new UnsupportedOperationException(
          "We don't support inlining methods with locals: " + name + ": " + desc);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      // Drop this, since dest will get more instructions and will need to recompute stack size.
      // This does indicate the end of visiting bytecode instructions, so defensively reset mv.
      mv = null;
    }
  }
}
