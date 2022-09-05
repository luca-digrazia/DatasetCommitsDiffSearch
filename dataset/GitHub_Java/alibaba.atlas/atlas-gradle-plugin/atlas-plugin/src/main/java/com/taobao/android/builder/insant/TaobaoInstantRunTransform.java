package com.taobao.android.builder.insant;

import com.alibaba.fastjson.JSON;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.incremental.*;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.options.DeploymentDevice;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.extension.PatchConfig;
import com.taobao.android.builder.insant.incremental.TBIncrementalVisitor;
import org.gradle.api.logging.Logging;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * TaobaoInstantRunTransform
 *
 * @author zhayu.ll
 * @date 18/10/12
 */
public class TaobaoInstantRunTransform extends Transform {
    private InstantRunVariantScope transformScope;
    protected static final ILogger LOGGER =
            new LoggerWrapper(Logging.getLogger(TaobaoInstantRunTransform.class));
    private final WaitableExecutor executor;
    private AppVariantContext variantContext;
    private AppVariantOutputContext variantOutputContext;
    private final ImmutableList.Builder<String> generatedClasses3Names = ImmutableList.builder();
    private final AndroidVersion targetPlatformApi;
    private File injectFailedFile;
    private List<String> errors = new ArrayList<>();
    private Map<String,String>modifyClasses = new HashMap<>();

    public TaobaoInstantRunTransform(AppVariantContext variantContext, AppVariantOutputContext variantOutputContext, WaitableExecutor executor, InstantRunVariantScope transformScope) {
        this.variantContext = variantContext;
        this.variantOutputContext = variantOutputContext;
        this.executor = executor;
        this.transformScope = transformScope;
        this.targetPlatformApi =
                DeploymentDevice.getDeploymentDeviceAndroidVersion(
                        transformScope.getGlobalScope().getProjectOptions());
        injectFailedFile = new File(variantContext.getProject().getBuildDir(), "outputs/warning-instrument-inject-error.properties");
    }

    @Override
    public String getName() {
        return "taobaoInstantRun";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return ImmutableSet.of(
                QualifiedContent.DefaultContentType.CLASSES,
                ExtendedContentType.CLASSES_ENHANCED);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                QualifiedContent.Scope.PROVIDED_ONLY);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation invocation) throws IOException, TransformException, InterruptedException {

        if (variantContext.getBuildType().getPatchConfig().isCreateTPatch()){
            PatchConfig patchConfig = variantContext.getBuildType().getPatchConfig();
            if (patchConfig != null){
                File classFile = patchConfig.getHotClassListFile();
                String s = org.apache.commons.io.FileUtils.readFileToString(classFile);
                modifyClasses = JSON.parseObject(s,Map.class);
            }
        }

        LOGGER.warning("start excute:" + getClass().getName());
        List<JarInput> jarInputs =
                invocation
                        .getInputs()
                        .stream()
                        .flatMap(input -> input.getJarInputs().stream())
                        .collect(Collectors.toList());

        Preconditions.checkState(
                jarInputs.isEmpty(), "Unexpected inputs: " + Joiner.on(", ").join(jarInputs));
        InstantRunBuildContext buildContext = transformScope.getInstantRunBuildContext();

        buildContext.startRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_TRANSFORM);
        try {
            doTransform(invocation);
        } finally {
            buildContext.stopRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_TRANSFORM);
        }
        org.apache.commons.io.FileUtils.writeLines(injectFailedFile, errors);
    }

    public void doTransform(TransformInvocation invocation) throws IOException, TransformException, InterruptedException {
        InstantRunBuildContext buildContext = transformScope.getInstantRunBuildContext();

        // if we do not run in incremental mode, we should automatically switch to COLD swap.
        buildContext.setVerifierStatus(
                InstantRunVerifierStatus.BUILD_NOT_INCREMENTAL);

        // If this is not a HOT_WARM build, clean up the enhanced classes and don't generate new
        // ones during this build.
        boolean inHotSwapMode =
                buildContext.getBuildMode() == InstantRunBuildMode.HOT_WARM;

        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        if (outputProvider == null) {
            throw new IllegalStateException("InstantRunTransform called with null output");
        }

        File classesTwoOutput =
                outputProvider.getContentLocation(
                        "classes", com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_CLASS, getScopes(), Format.DIRECTORY);

        File classesThreeOutput =
                outputProvider.getContentLocation(
                        "enhanced_classes",
                        ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED),
                        getScopes(),
                        Format.DIRECTORY);

        AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs().clear();
        AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs().add(classesTwoOutput);
        AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs().add(classesThreeOutput);

        List<WorkItem> workItems = new ArrayList<>();
        for (TransformInput input : invocation.getInputs()) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File inputDir = directoryInput.getFile();
                LOGGER.warning("inputDir:", inputDir.getAbsolutePath());
                    // non incremental mode, we need to traverse the TransformInput#getFiles()
                    // folder
                    FileUtils.cleanOutputDir(classesTwoOutput);
                    for (File file : Files.fileTreeTraverser().breadthFirstTraversal(inputDir)) {
                        if (file.isDirectory()) {
                            continue;
                        }
                        String path = FileUtils.relativePath(file, inputDir);
                        String className = path.replace("/",".").substring(0,path.length()-6);
                        if (modifyClasses.containsKey(className)){
                            boolean isAdd = false;
                            PatchPolicy patchPolicy = modifyClasses.get(className).equals(PatchPolicy.ADD.name()) ? PatchPolicy.ADD:PatchPolicy.MODIFY;
                            switch (patchPolicy){
                                case ADD:
                                    workItems.add(() -> transformToClasses2Format(
                                            inputDir,
                                            file,
                                            classesThreeOutput,
                                            Status.ADDED));
                                    isAdd = true;
                                    break;

                                case MODIFY:
                                    workItems.add(() -> transformToClasses3Format(
                                            inputDir,
                                            file,
                                            classesThreeOutput));
                                    break;
                            }
                            if (isAdd) {
                                continue;
                            }
                        }

                        workItems.add(() -> transformToClasses2Format(
                                inputDir,
                                file,
                                classesTwoOutput,
                                Status.ADDED));
                    }


//                }
            }
        }


        variantOutputContext.getAwbTransformMap().values().forEach(awbTransform -> {
            File awbClassesTwoOutout = variantOutputContext.getAwbClassesInstantOut(awbTransform.getAwbBundle());
            LOGGER.warning("InstantAwbclassOut[" + awbTransform.getAwbBundle().getPackageName() + "]---------------------" + awbClassesTwoOutout.getAbsolutePath());
            FileUtils.mkdirs(awbClassesTwoOutout);
            try {
                FileUtils.cleanOutputDir(awbClassesTwoOutout);
            } catch (IOException e) {
                e.printStackTrace();
            }
            awbTransform.getInputDirs().forEach(dir -> {
                LOGGER.warning("InstantAwbclassDir[" + awbTransform.getAwbBundle().getPackageName() + "]---------------------" + dir.getAbsolutePath());
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dir)) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String path = FileUtils.relativePath(file, dir);
                    String className = path.replace("/",".").substring(0,path.length()-6);
                    if (modifyClasses.containsKey(className)){
                        boolean isAdd = false;
                        PatchPolicy patchPolicy = modifyClasses.get(className).equals(PatchPolicy.ADD.name()) ? PatchPolicy.ADD:PatchPolicy.MODIFY;
                        switch (patchPolicy){
                            case ADD:
                                workItems.add(() -> transformToClasses2Format(
                                        dir,
                                        file,
                                        classesThreeOutput,
                                        Status.ADDED));
                                isAdd = true;
                                break;

                            case MODIFY:
                                workItems.add(() -> transformToClasses3Format(
                                        dir,
                                        file,
                                        classesThreeOutput));
                                break;
                        }

                        if (isAdd) {
                            continue;
                        }
                    }
                    workItems.add(() -> transformToClasses2Format(
                            dir,
                            file,
                            awbClassesTwoOutout,
                            Status.ADDED));
                }

            });

            awbTransform.getInputDirs().clear();
            awbTransform.getInputLibraries().clear();
            awbTransform.getInputFiles().clear();
            awbTransform.getInputDirs().add(awbClassesTwoOutout);
        });

        // first get all referenced input to construct a class loader capable of loading those
        // classes. This is useful for ASM as it needs to load classes
        List<URL> referencedInputUrls = getAllClassesLocations(
                invocation.getInputs(), invocation.getReferencedInputs());

        // This class loader could be optimized a bit, first we could create a parent class loader
        // with the android.jar only that could be stored in the GlobalScope for reuse. This
        // class loader could also be store in the VariantScope for potential reuse if some
        // other transform need to load project's classes.
        try (URLClassLoader urlClassLoader = new NonDelegatingUrlClassloader(referencedInputUrls)) {
            workItems.forEach(
                    workItem ->
                            executor.execute(
                                    () -> {
                                        ClassLoader currentThreadClassLoader =
                                                Thread.currentThread().getContextClassLoader();
                                        Thread.currentThread()
                                                .setContextClassLoader(urlClassLoader);
                                        try {
                                            return workItem.doWork();
                                        } finally {
                                            Thread.currentThread()
                                                    .setContextClassLoader(
                                                            currentThreadClassLoader);
                                        }
                                    }));

            try {
                // wait for all work items completion.
                executor.waitForTasksWithQuickFail(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransformException(e);
            } catch (Exception e) {
                throw new TransformException(e);
            }
        }

        // If our classes.2 transformations indicated that a cold swap was necessary,
        // clean up the classes.3 output folder as some new files may have been generated.
        if (generatedClasses3Names.build().size() == 0) {
            FileUtils.cleanOutputDir(classesThreeOutput);
        }

        wrapUpOutputs(classesTwoOutput, classesThreeOutput);

    }

    private interface WorkItem {

        Void doWork() throws IOException;
    }

    @NonNull
    private List<URL> getAllClassesLocations(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs) throws MalformedURLException {

        List<URL> referencedInputUrls = new ArrayList<>();

        // add the bootstrap classpath for jars like android.jar
        for (File file : transformScope.getInstantRunBootClasspath()) {
            referencedInputUrls.add(file.toURI().toURL());
        }

        // now add the project dependencies.
        for (TransformInput referencedInput : referencedInputs) {
            addAllClassLocations(referencedInput, referencedInputUrls);
        }

        // and finally add input folders.
        for (TransformInput input : inputs) {
            addAllClassLocations(input, referencedInputUrls);
        }


        variantOutputContext.getAwbTransformMap().values().forEach(new Consumer<AwbTransform>() {
            @Override
            public void accept(AwbTransform awbTransform) {
                try {
                    addAllClassLocations(awbTransform.getInputFiles(), referencedInputUrls);

                    addAllClassLocations(awbTransform.getInputDirs(), referencedInputUrls);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        });

        return referencedInputUrls;
    }

    private static void addAllClassLocations(TransformInput transformInput, List<URL> into)
            throws MalformedURLException {

        for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
            into.add(directoryInput.getFile().toURI().toURL());
        }
        for (JarInput jarInput : transformInput.getJarInputs()) {
            into.add(jarInput.getFile().toURI().toURL());
        }
    }

    private static void addAllClassLocations(Collection<File> classFiles, List<URL> into)
            throws MalformedURLException {
        for (File classFile : classFiles) {
            into.add(classFile.toURI().toURL());
        }

    }


    private static void deleteOutputFile(
            @NonNull IncrementalVisitor.VisitorBuilder visitorBuilder,
            @NonNull File inputDir, @NonNull File inputFile, @NonNull File outputDir) {
        String inputPath = FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir);
        String outputPath =
                visitorBuilder.getMangledRelativeClassFilePath(inputPath);
        File outputFile = new File(outputDir, outputPath);
        if (outputFile.exists()) {
            try {
                FileUtils.delete(outputFile);
            } catch (IOException e) {
                // it's not a big deal if the file cannot be deleted, hopefully
                // no code is still referencing it, yet we should notify.
                LOGGER.warning("Cannot delete %1$s file.\nCause: %2$s",
                        outputFile, Throwables.getStackTraceAsString(e));
            }
        }
    }


    private static class NonDelegatingUrlClassloader extends URLClassLoader {

        public NonDelegatingUrlClassloader(@NonNull List<URL> urls) {
            super(urls.toArray(new URL[urls.size()]), null);
        }

        @Override
        public URL getResource(String name) {
            // Never delegate to bootstrap classes.
            return findResource(name);
        }
    }


    @Nullable
    protected Void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
            throws IOException {
        File outputFile =
                TBIncrementalVisitor.instrumentClass(
                        targetPlatformApi.getFeatureLevel(),
                        inputDir,
                        inputFile,
                        outputDir,
                        IncrementalChangeVisitor.VISITOR_BUILDER,
                        LOGGER,
                        null,
                        false);

        // if the visitor returned null, that means the class cannot be hot swapped or more likely
        // that it was disabled for InstantRun, we don't add it to our collection of generated
        // classes and it will not be part of the Patch class that apply changes.
        if (outputFile == null) {
            transformScope
                    .getInstantRunBuildContext()
                    .setVerifierStatus(InstantRunVerifierStatus.INSTANT_RUN_DISABLED);
            LOGGER.info("Class %s cannot be hot swapped.", inputFile);
            return null;
        }
        generatedClasses3Names.add(
                inputFile.getAbsolutePath().substring(
                        inputDir.getAbsolutePath().length() + 1,
                        inputFile.getAbsolutePath().length() - ".class".length())
                        .replace(File.separatorChar, '.'));
        return null;
    }


    @Nullable
    protected Void transformToClasses2Format(
            @NonNull final File inputDir,
            @NonNull final File inputFile,
            @NonNull final File outputDir,
            @NonNull final Status change) {
        if (inputFile.getPath().endsWith(SdkConstants.DOT_CLASS)) {
            String path = FileUtils.relativePath(inputFile, inputDir);
            try {
                File file = TBIncrementalVisitor.instrumentClass(
                        targetPlatformApi.getFeatureLevel(),
                        inputDir,
                        inputFile,
                        outputDir,
                        IncrementalSupportVisitor.VISITOR_BUILDER,
                        LOGGER,
                        errorType -> {
                            errors.add(errorType.name()+":"+path);
                        },
                        true);
                if (file.length() == inputFile.length()){
                    errors.add("NO INJECT:"+path);
                }
            } catch (Exception e) {
                LOGGER.warning("exception instrumentClass:" + inputFile.getPath());
                errors.add("EXCEPTION:"+path);
                File outputFile = new File(outputDir, path);
                try {
                    Files.createParentDirs(outputFile);
                    Files.copy(inputFile, outputFile);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

            }
        }
        return null;
    }


    protected void wrapUpOutputs(File classes2Folder, File classes3Folder)
            throws IOException {

        // the transform can set the verifier status to failure in some corner cases, in that
        // case, make sure we delete our classes.3
//        if (!transformScope.getInstantRunBuildContext().hasPassedVerification()) {
//            FileUtils.cleanOutputDir(classes3Folder);
//            return;
//        }
        // otherwise, generate the patch file and add it to the list of files to process next.
        ImmutableList<String> generatedClassNames = generatedClasses3Names.build();
        if (!generatedClassNames.isEmpty()) {
            writePatchFileContents(
                    generatedClassNames,
                    classes3Folder,
                    transformScope.getInstantRunBuildContext().getBuildId());
        }
    }


    private static void writePatchFileContents(
            @NonNull ImmutableList<String> patchFileContents, @NonNull File outputDir, long buildId) {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                IncrementalVisitor.APP_PATCHES_LOADER_IMPL, null,
                IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL, null);

        // Add the build ID to force the patch file to be repackaged.
        cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL,
                "BUILD_ID", "J", null, buildId);

        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL,
                    "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClasses", "()[Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitIntInsn(Opcodes.BIPUSH, patchFileContents.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
            for (int index = 0; index < patchFileContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.BIPUSH, index);
                mv.visitLdcInsn(patchFileContents.get(index));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] classBytes = cw.toByteArray();
        File outputFile = new File(outputDir, IncrementalVisitor.APP_PATCHES_LOADER_IMPL + ".class");
        try {
            Files.createParentDirs(outputFile);
            Files.write(classBytes, outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    enum PatchPolicy{

        ADD,MODIFY

    }


}
