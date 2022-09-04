package org.jboss.shamrock.deployment;

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.LSUB;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorderImpl;
import org.jboss.shamrock.runtime.StartupContext;
import org.jboss.shamrock.runtime.StartupTask;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Class that does the build time processing
 */
public class Runner {

    private static final AtomicInteger COUNT = new AtomicInteger();
    public static final String MAIN_CLASS = "org/jboss/shamrock/runner/Main";
    private static final String GRAAL_AUTOFEATURE = "org/jboss/shamrock/runner/AutoFeature";
    private static final String STATIC_INIT_TIME = "STATIC_INIT_TIME";
    private static final String STARTUP_CONTEXT = "STARTUP_CONTEXT";

    private final List<ResourceProcessor> processors;
    private final ClassOutput output;

    public Runner(ClassOutput classOutput) {
        Iterator<ResourceProcessor> loader = ServiceLoader.load(ResourceProcessor.class).iterator();
        List<ResourceProcessor> processors = new ArrayList<>();
        while (loader.hasNext()) {
            processors.add(loader.next());
        }
        processors.sort(Comparator.comparingInt(ResourceProcessor::getPriority));
        this.processors = Collections.unmodifiableList(processors);
        this.output = classOutput;
    }


    public void run(Path root) throws IOException {
        Indexer indexer = new Indexer();
        Files.walkFileTree(root, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class")) {
                    try (InputStream stream = Files.newInputStream(file)) {
                        indexer.index(stream);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        Index index = indexer.complete();
        ArchiveContext context = new ArchiveContextImpl(index, root);
        ProcessorContextImpl processorContext = new ProcessorContextImpl();
        for (ResourceProcessor processor : processors) {
            try {
                processor.process(context, processorContext);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        processorContext.writeMainClass();
        processorContext.writeAutoFeature();
    }


    private static class ArchiveContextImpl implements ArchiveContext {

        private final Index index;
        private final Path root;

        private ArchiveContextImpl(Index index, Path root) {
            this.index = index;
            this.root = root;
        }

        @Override
        public Index getIndex() {
            return index;
        }

        @Override
        public Path getArchiveRoot() {
            return root;
        }
    }


    private final class ProcessorContextImpl implements ProcessorContext {


        private final List<DeploymentTaskHolder> tasks = new ArrayList<>();
        private final List<DeploymentTaskHolder> staticInitTasks = new ArrayList<>();
        private final Set<String> reflectiveClasses = new LinkedHashSet<>();

        @Override
        public BytecodeRecorder addStaticInitTask(int priority) {
            String className = getClass().getName() + "$$Proxy" + COUNT.incrementAndGet();
            staticInitTasks.add(new DeploymentTaskHolder(className, priority));
            return new BytecodeRecorderImpl(className, StartupTask.class, output);
        }

        @Override
        public BytecodeRecorder addDeploymentTask(int priority) {
            String className = getClass().getName() + "$$Proxy" + COUNT.incrementAndGet();
            tasks.add(new DeploymentTaskHolder(className, priority));
            return new BytecodeRecorderImpl(className, StartupTask.class, output);
        }

        @Override
        public void addReflectiveClass(String className) {
            reflectiveClasses.add(className);
        }

        @Override
        public void addGeneratedClass(String name, byte[] classData) throws IOException {
            output.writeClass(name, classData);
        }

        void writeMainClass() throws IOException {

            Collections.sort(tasks);
            Collections.sort(staticInitTasks);

            ClassWriter file = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            file.visit(Opcodes.V1_8, ACC_PUBLIC | ACC_SUPER, MAIN_CLASS, null, Type.getInternalName(Object.class), null);

            // constructor
            MethodVisitor mv = file.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0); // push `this` to the operand stack
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false); // call the constructor of super class
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();

            file.visitField(ACC_PUBLIC | ACC_STATIC, STATIC_INIT_TIME, "J", null, null);
            file.visitField(ACC_PUBLIC | ACC_STATIC, STARTUP_CONTEXT, "L" + Type.getInternalName(StartupContext.class) + ";", null, null);

            mv = file.visitMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(System.class), "currentTimeMillis", "()J", false);
            mv.visitFieldInsn(PUTSTATIC, MAIN_CLASS, STATIC_INIT_TIME, "J");
            mv.visitTypeInsn(NEW, Type.getInternalName(StartupContext.class));
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(StartupContext.class), "<init>", "()V", false);
            mv.visitInsn(DUP);
            mv.visitFieldInsn(PUTSTATIC, MAIN_CLASS, STARTUP_CONTEXT, "L" + Type.getInternalName(StartupContext.class) + ";");
            for (DeploymentTaskHolder holder : staticInitTasks) {
                mv.visitInsn(DUP);
                String className = holder.className.replace(".", "/");
                mv.visitTypeInsn(NEW, className);
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", "()V", false);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(StartupTask.class), "deploy", "(L" + StartupContext.class.getName().replace(".", "/") + ";)V", true);
            }

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 4);
            mv.visitEnd();

            mv = file.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(System.class), "currentTimeMillis", "()J", false);
            mv.visitVarInsn(LSTORE, 2);
            mv.visitFieldInsn(GETSTATIC, MAIN_CLASS, STARTUP_CONTEXT, "L" + Type.getInternalName(StartupContext.class) + ";");
            for (DeploymentTaskHolder holder : tasks) {
                mv.visitInsn(DUP);
                String className = holder.className.replace(".", "/");
                mv.visitTypeInsn(NEW, className);
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, className, "<init>", "()V", false);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(StartupTask.class), "deploy", "(L" + StartupContext.class.getName().replace(".", "/") + ";)V", true);
            }

            //time since main start
            mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";");
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(System.class), "currentTimeMillis", "()J", false);
            mv.visitVarInsn(LLOAD, 2);
            mv.visitInsn(LSUB);
            mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";");
            mv.visitLdcInsn("Shamrock started in ");
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(Ljava/lang/String;)V", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(J)V", false);
            mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";");
            mv.visitLdcInsn("ms");
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", "(Ljava/lang/String;)V", false);

            //time since static init started
            mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";");
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(System.class), "currentTimeMillis", "()J", false);
            mv.visitFieldInsn(GETSTATIC, MAIN_CLASS, STATIC_INIT_TIME, "J");
            mv.visitInsn(LSUB);
            mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";");
            mv.visitLdcInsn("Time since static init started ");
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(Ljava/lang/String;)V", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(J)V", false);
            mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";");
            mv.visitLdcInsn("ms");
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", "(Ljava/lang/String;)V", false);

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 4);
            mv.visitEnd();
            file.visitEnd();

            output.writeClass(MAIN_CLASS, file.toByteArray());
        }

        void writeAutoFeature() throws IOException {

            ClassWriter file = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            file.visit(Opcodes.V1_8, ACC_PUBLIC | ACC_SUPER, GRAAL_AUTOFEATURE, null, Type.getInternalName(Object.class), new String[]{"org/graalvm/nativeimage/Feature"});
            AnnotationVisitor annotation = file.visitAnnotation("Lcom/oracle/svm/core/annotate/AutomaticFeature;", true);
            annotation.visitEnd();
            // constructor
            MethodVisitor mv = file.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0); // push `this` to the operand stack
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false); // call the constructor of super class
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();


            mv = file.visitMethod(ACC_PUBLIC, "beforeAnalysis", "(Lorg/graalvm/nativeimage/Feature$BeforeAnalysisAccess;)V", null, null);


            for (String holder : reflectiveClasses) {
                mv.visitLdcInsn(1);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
                mv.visitInsn(DUP);
                mv.visitLdcInsn(0);
                String internalName = holder.replace(".", "/");
                mv.visitLdcInsn(Type.getObjectType(internalName));
                mv.visitInsn(AASTORE);
                mv.visitMethodInsn(INVOKESTATIC, "org/graalvm/nativeimage/RuntimeReflection", "register", "([Ljava/lang/Class;)V", false);

                //now load everything else
                mv.visitLdcInsn(Type.getObjectType(internalName));
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", false);
                mv.visitMethodInsn(INVOKESTATIC, "org/graalvm/nativeimage/RuntimeReflection", "register", "([Ljava/lang/reflect/Executable;)V", false);
                //now load everything else
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", false);
                mv.visitMethodInsn(INVOKESTATIC, "org/graalvm/nativeimage/RuntimeReflection", "register", "([Ljava/lang/reflect/Executable;)V", false);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
            file.visitEnd();

            output.writeClass(GRAAL_AUTOFEATURE, file.toByteArray());
        }

    }

    private static final class DeploymentTaskHolder implements Comparable<DeploymentTaskHolder> {
        private final String className;
        private final int priority;

        private DeploymentTaskHolder(String className, int priority) {
            this.className = className;
            this.priority = priority;
        }

        @Override
        public int compareTo(DeploymentTaskHolder o) {
            int val = Integer.compare(priority, o.priority);
            if (val != 0) {
                return val;
            }
            return className.compareTo(o.className);
        }
    }
}
