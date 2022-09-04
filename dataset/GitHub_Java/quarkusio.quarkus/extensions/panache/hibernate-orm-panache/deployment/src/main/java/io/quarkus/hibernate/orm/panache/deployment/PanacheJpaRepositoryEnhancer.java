package io.quarkus.hibernate.orm.panache.deployment;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.deployment.JandexUtil;
import io.quarkus.panache.common.deployment.PanacheRepositoryEnhancer;

public class PanacheJpaRepositoryEnhancer extends PanacheRepositoryEnhancer {

    private static final DotName PANACHE_REPOSITORY_BINARY_NAME = DotName.createSimple(PanacheRepository.class.getName());
    private static final DotName PANACHE_REPOSITORY_BASE_BINARY_NAME = DotName
            .createSimple(PanacheRepositoryBase.class.getName());

    public PanacheJpaRepositoryEnhancer(IndexView index) {
        super(index, PanacheResourceProcessor.DOTNAME_PANACHE_REPOSITORY_BASE);
    }

    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        return new PanacheJpaRepositoryClassVisitor(className, outputClassVisitor, panacheRepositoryBaseClassInfo,
                this.indexView);
    }

    static class PanacheJpaRepositoryClassVisitor extends PanacheRepositoryClassVisitor {

        public PanacheJpaRepositoryClassVisitor(String className, ClassVisitor outputClassVisitor,
                ClassInfo panacheRepositoryBaseClassInfo, IndexView indexView) {
            super(className, outputClassVisitor, panacheRepositoryBaseClassInfo, indexView);
        }

        @Override
        protected DotName getPanacheRepositoryDotName() {
            return PANACHE_REPOSITORY_BINARY_NAME;
        }

        @Override
        protected DotName getPanacheRepositoryBaseDotName() {
            return PANACHE_REPOSITORY_BASE_BINARY_NAME;
        }

        @Override
        protected String getPanacheOperationsBinaryName() {
            return PanacheJpaEntityEnhancer.JPA_OPERATIONS_BINARY_NAME;
        }

        @Override
        public void visitEnd() {
            // Bridge for findById, but only if we actually know the end entity (which we don't for intermediate
            // abstract repositories that haven't fixed their entity type yet
            if (!"Ljava/lang/Object;".equals(entitySignature)) {
                if (!JandexUtil.containsMethod(daoClassInfo, "findById",
                        Object.class.getName(),
                        Object.class.getName())) {
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                            "findById",
                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                            null,
                            null);
                    mv.visitParameter("id", 0);
                    mv.visitCode();
                    mv.visitIntInsn(Opcodes.ALOAD, 0);
                    mv.visitIntInsn(Opcodes.ALOAD, 1);

                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            daoBinaryName,
                            "findById",
                            "(Ljava/lang/Object;)" + entitySignature, false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
                if (!JandexUtil.containsMethod(daoClassInfo, "findById",
                        Object.class.getName(),
                        Object.class.getName(), "javax.persistence.LockModeType")) {
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                            "findById",
                            "(Ljava/lang/Object;Ljavax/persistence/LockModeType;)Ljava/lang/Object;",
                            null,
                            null);
                    mv.visitParameter("id", 0);
                    mv.visitParameter("lockModeType", 0);
                    mv.visitCode();
                    mv.visitIntInsn(Opcodes.ALOAD, 0);
                    mv.visitIntInsn(Opcodes.ALOAD, 1);
                    mv.visitIntInsn(Opcodes.ALOAD, 2);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            daoBinaryName,
                            "findById",
                            "(Ljava/lang/Object;Ljavax/persistence/LockModeType;)" + entitySignature, false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
            }
            super.visitEnd();
        }

        @Override
        protected void injectModel(MethodVisitor mv) {
            // inject Class
            mv.visitLdcInsn(entityType);
        }

        @Override
        protected String getModelDescriptor() {
            return "Ljava/lang/Class;";
        }
    }
}
