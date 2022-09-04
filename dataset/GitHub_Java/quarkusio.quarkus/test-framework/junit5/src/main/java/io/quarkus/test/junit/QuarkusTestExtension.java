package io.quarkus.test.junit;

import static io.quarkus.test.common.PathTestHelper.getAppClassLocation;
import static io.quarkus.test.common.PathTestHelper.getTestClassesLocation;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.platform.commons.JUnitException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.opentest4j.TestAbortedException;

import io.quarkus.bootstrap.BootstrapClassLoaderFactory;
import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.DefineClassVisibleURLClassLoader;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.bootstrap.util.PropertyUtils;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.ClassOutput;
import io.quarkus.deployment.QuarkusClassWriter;
import io.quarkus.deployment.builditem.TestAnnotationBuildItem;
import io.quarkus.deployment.builditem.TestClassPredicateBuildItem;
import io.quarkus.deployment.util.IoUtil;
import io.quarkus.runner.RuntimeRunner;
import io.quarkus.runner.TransformerTarget;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.test.common.NativeImageLauncher;
import io.quarkus.test.common.PathTestHelper;
import io.quarkus.test.common.PropertyTestUtil;
import io.quarkus.test.common.RestAssuredURLManager;
import io.quarkus.test.common.TestInjectionManager;
import io.quarkus.test.common.TestInstantiator;
import io.quarkus.test.common.TestResourceManager;
import io.quarkus.test.common.TestScopeManager;
import io.quarkus.test.common.http.TestHTTPResourceManager;

public class QuarkusTestExtension
        implements BeforeEachCallback, AfterEachCallback, TestInstanceFactory, BeforeAllCallback {

    private URLClassLoader appCl;
    private ClassLoader originalCl;
    private static boolean failedBoot;

    /**
     * As part of the test run we need to create files in the test-classes directory
     *
     * We attempt to clean these up with a shutdown hook, but if the processes is killed (e.g. hitting the red
     * IDE button) it can leave these files behind which interfere with subsequent runs.
     *
     * To fix this we create a file that contains the names of all the files we have created, and at the start of a new
     * run we remove them if this file exists.
     */
    private static final String CREATED_FILES = "CREATED_FILES.txt";
    private final RestAssuredURLManager restAssuredURLManager = new RestAssuredURLManager(false);

    private ExtensionState doJavaStart(ExtensionContext context, TestResourceManager testResourceManager) {

        final LinkedBlockingDeque<Runnable> shutdownTasks = new LinkedBlockingDeque<>();

        Path appClassLocation = getAppClassLocation(context.getRequiredTestClass());

        appCl = createQuarkusBuildClassLoader(appClassLocation);
        originalCl = setCCL(appCl);

        final ClassLoader testClassLoader = context.getRequiredTestClass().getClassLoader();
        final Path testWiringClassesDir;
        final RuntimeRunner.Builder runnerBuilder = RuntimeRunner.builder();

        final Path testClassLocation = getTestClassesLocation(context.getRequiredTestClass());
        if (Files.isDirectory(testClassLocation)) {
            testWiringClassesDir = testClassLocation;
        } else {
            if (!appClassLocation.equals(testClassLocation)) {
                runnerBuilder.addAdditionalArchive(testClassLocation);
            }
            testWiringClassesDir = Paths.get("").normalize().toAbsolutePath().resolve("target").resolve("test-classes");
            if (Files.exists(testWiringClassesDir)) {
                IoUtils.recursiveDelete(testWiringClassesDir);
            }
            try {
                Files.createDirectories(testWiringClassesDir);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to create a directory for wiring test classes at " + testWiringClassesDir, e);
            }
        }

        Path createdFilesPath = testWiringClassesDir.resolve(CREATED_FILES);
        if (Files.exists(createdFilesPath)) {
            cleanupOldRun(createdFilesPath);
        }
        try (OutputStream created = Files.newOutputStream(createdFilesPath)) {

            RuntimeRunner runtimeRunner = runnerBuilder
                    .setLaunchMode(LaunchMode.TEST)
                    .setClassLoader(appCl)
                    .setTarget(appClassLocation)
                    .addAdditionalArchive(testWiringClassesDir)
                    .setClassOutput(new ClassOutput() {
                        @Override
                        public void writeClass(boolean applicationClass, String className, byte[] data) throws IOException {
                            Path location = testWiringClassesDir.resolve(className.replace('.', '/') + ".class");
                            Files.createDirectories(location.getParent());
                            Files.write(location, data);
                            handleCreatedFile(location, created, testWiringClassesDir, shutdownTasks);
                        }

                        @Override
                        public void writeResource(String name, byte[] data) throws IOException {
                            Path location = testWiringClassesDir.resolve(name);
                            Files.createDirectories(location.getParent());
                            Files.write(location, data);
                            handleCreatedFile(location, created, testWiringClassesDir, shutdownTasks);
                        }
                    })
                    .setTransformerTarget(new TransformerTarget() {
                        @Override
                        public void setTransformers(
                                Map<String, List<BiFunction<String, ClassVisitor, ClassVisitor>>> functions) {
                            ClassLoader main = Thread.currentThread().getContextClassLoader();

                            //we need to use a temp class loader, or the old resource location will be cached
                            ClassLoader temp = new ClassLoader() {
                                @Override
                                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                                    // First, check if the class has already been loaded
                                    Class<?> c = findLoadedClass(name);
                                    if (c == null) {
                                        c = findClass(name);
                                    }
                                    if (resolve) {
                                        resolveClass(c);
                                    }
                                    return c;
                                }

                                @Override
                                public URL getResource(String name) {
                                    return main.getResource(name);
                                }

                                @Override
                                public Enumeration<URL> getResources(String name) throws IOException {
                                    return main.getResources(name);
                                }
                            };
                            for (Map.Entry<String, List<BiFunction<String, ClassVisitor, ClassVisitor>>> e : functions
                                    .entrySet()) {
                                String resourceName = e.getKey().replace('.', '/') + ".class";
                                try (InputStream stream = temp.getResourceAsStream(resourceName)) {
                                    if (stream == null) {
                                        System.err.println("Failed to transform " + e.getKey());
                                        continue;
                                    }
                                    byte[] data = IoUtil.readBytes(stream);

                                    ClassReader cr = new ClassReader(data);
                                    ClassWriter cw = new QuarkusClassWriter(cr,
                                            ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {

                                        @Override
                                        protected ClassLoader getClassLoader() {
                                            // this has been previously set to a safe for transformations CL
                                            return main;
                                        }
                                    };
                                    ClassLoader old = Thread.currentThread().getContextClassLoader();
                                    Thread.currentThread().setContextClassLoader(temp);
                                    try {
                                        ClassVisitor visitor = cw;
                                        for (BiFunction<String, ClassVisitor, ClassVisitor> i : e.getValue()) {
                                            visitor = i.apply(e.getKey(), visitor);
                                        }
                                        cr.accept(visitor, 0);
                                    } finally {
                                        Thread.currentThread().setContextClassLoader(old);
                                    }

                                    Path location = testWiringClassesDir.resolve(resourceName);
                                    Files.createDirectories(location.getParent());
                                    Files.write(location, cw.toByteArray());
                                    handleCreatedFile(location, created, testWiringClassesDir, shutdownTasks);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }
                    })
                    .addChainCustomizer(new Consumer<BuildChainBuilder>() {
                        @Override
                        public void accept(BuildChainBuilder buildChainBuilder) {
                            buildChainBuilder.addBuildStep(new BuildStep() {
                                @Override
                                public void execute(BuildContext context) {
                                    context.produce(new TestClassPredicateBuildItem(new Predicate<String>() {
                                        @Override
                                        public boolean test(String className) {
                                            return PathTestHelper.isTestClass(className, testClassLoader);
                                        }
                                    }));
                                }
                            }).produces(TestClassPredicateBuildItem.class)
                                    .build();
                        }
                    })
                    .addChainCustomizer(new Consumer<BuildChainBuilder>() {
                        @Override
                        public void accept(BuildChainBuilder buildChainBuilder) {
                            buildChainBuilder.addBuildStep(new BuildStep() {
                                @Override
                                public void execute(BuildContext context) {
                                    context.produce(new TestAnnotationBuildItem(QuarkusTest.class.getName()));
                                }
                            }).produces(TestAnnotationBuildItem.class)
                                    .build();
                        }
                    })
                    .build();
            runtimeRunner.run();

            System.setProperty("test.url", TestHTTPResourceManager.getUri());

            Closeable shutdownTask = new Closeable() {
                @Override
                public void close() throws IOException {
                    runtimeRunner.close();
                    while (!shutdownTasks.isEmpty()) {
                        shutdownTasks.pop().run();
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        shutdownTask.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, "Quarkus Test Cleanup Shutdown task"));
            shutdownTasks.add(new DeleteRunnable(createdFilesPath));
            return new ExtensionState(testResourceManager, shutdownTask, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanupOldRun(Path createdFilesPath) {
        try (BufferedReader reader = Files.newBufferedReader(createdFilesPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Files.deleteIfExists(createdFilesPath.getParent().resolve(line));
            }
            Files.deleteIfExists(createdFilesPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleCreatedFile(Path location, OutputStream created, Path testWiringClassesDir,
            LinkedBlockingDeque<Runnable> shutdownTasks) throws IOException {
        created.write((testWiringClassesDir.relativize(location).toString() + "\n").getBytes(StandardCharsets.UTF_8));
        created.flush();
        shutdownTasks.add(new DeleteRunnable(location));
    }

    /**
     * Creates a classloader that will be used to build the test application.
     *
     * This method assumes that the runtime classes are already on the classpath
     * of the classloader that loaded this class.
     * What this method does is it resolves the required deployment classpath
     * and creates a new URL classloader that includes the deployment CP with
     * the classloader that loaded this class as its parent.
     *
     * @param appClassLocation location of the test application classes
     * @return application build classloader
     */
    private URLClassLoader createQuarkusBuildClassLoader(Path appClassLocation) {
        // The deployment classpath could be passed in as a system property.
        // This is how integration with the Gradle plugin is achieved.
        final String deploymentCp = PropertyUtils.getProperty(BootstrapClassLoaderFactory.PROP_DEPLOYMENT_CP);
        if (deploymentCp != null && !deploymentCp.isEmpty()) {
            final List<URL> list = new ArrayList<>();
            for (String entry : deploymentCp.split("\\s")) {
                try {
                    list.add(new URL(entry));
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("Failed to parse a deployment classpath entry " + entry, e);
                }
            }
            return new DefineClassVisibleURLClassLoader(list.toArray(new URL[list.size()]), getClass().getClassLoader());
        }
        try {
            return BootstrapClassLoaderFactory.newInstance()
                    .setAppClasses(appClassLocation)
                    .setParent(getClass().getClassLoader())
                    .setOffline(PropertyUtils.getBooleanOrNull(BootstrapClassLoaderFactory.PROP_OFFLINE))
                    .setLocalProjectsDiscovery(
                            PropertyUtils.getBoolean(BootstrapClassLoaderFactory.PROP_WS_DISCOVERY, true))
                    .setEnableClasspathCache(PropertyUtils.getBoolean(BootstrapClassLoaderFactory.PROP_CP_CACHE, true))
                    .newDeploymentClassLoader();
        } catch (BootstrapException e) {
            throw new IllegalStateException("Failed to create the boostrap class loader", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (!failedBoot) {
            boolean nativeImageTest = context.getRequiredTestClass().isAnnotationPresent(SubstrateTest.class)
                    || context.getRequiredTestClass().isAnnotationPresent(NativeImageTest.class);
            restAssuredURLManager.clearURL();
            TestScopeManager.tearDown(nativeImageTest);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (!failedBoot) {
            boolean nativeImageTest = context.getRequiredTestClass().isAnnotationPresent(SubstrateTest.class)
                    || context.getRequiredTestClass().isAnnotationPresent(NativeImageTest.class);
            restAssuredURLManager.setURL();
            TestScopeManager.setup(nativeImageTest);
        }
    }

    @Override
    public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext)
            throws TestInstantiationException {
        if (failedBoot) {
            try {
                return extensionContext.getRequiredTestClass().newInstance();
            } catch (Exception e) {
                throw new TestInstantiationException("Boot failed", e);
            }
        }
        ExtensionContext root = extensionContext.getRoot();
        ExtensionContext.Store store = root.getStore(ExtensionContext.Namespace.GLOBAL);
        ExtensionState state = store.get(ExtensionState.class.getName(), ExtensionState.class);
        PropertyTestUtil.setLogFileProperty();
        boolean nativeImageTest = extensionContext.getRequiredTestClass().isAnnotationPresent(SubstrateTest.class)
                || extensionContext.getRequiredTestClass().isAnnotationPresent(NativeImageTest.class);
        if (state == null) {
            TestResourceManager testResourceManager = new TestResourceManager(extensionContext.getRequiredTestClass());
            try {
                Map<String, String> systemProps = testResourceManager.start();

                if (nativeImageTest) {
                    NativeImageLauncher launcher = new NativeImageLauncher(extensionContext.getRequiredTestClass());
                    launcher.addSystemProperties(systemProps);
                    try {
                        launcher.start();
                    } catch (IOException e) {
                        try {
                            launcher.close();
                        } catch (Throwable t) {
                        }
                        throw new JUnitException("Quarkus native image start failed, original cause: " + e);
                    }
                    state = new ExtensionState(testResourceManager, launcher, true);
                } else {
                    state = doJavaStart(extensionContext, testResourceManager);
                }
                store.put(ExtensionState.class.getName(), state);

            } catch (Throwable e) {
                try {
                    testResourceManager.stop();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                failedBoot = true;
                throw e;
            }
        } else {
            if (nativeImageTest != state.isNativeImage()) {
                throw new RuntimeException(
                        "Attempted to mix @NativeImageTest and JVM mode tests in the same test run. This is not allowed.");
            }
        }

        // non-static inner classes are not supported
        Class<?> testClass = factoryContext.getTestClass();
        if (testClass.getEnclosingClass() != null && !Modifier.isStatic(testClass.getModifiers())) {
            throw new IllegalStateException("Test class " + testClass + " cannot be a non-static inner class.");
        }
        Object instance = TestInstantiator.instantiateTest(testClass);
        TestHTTPResourceManager.inject(instance);
        TestInjectionManager.inject(instance);
        state.testResourceManager.inject(instance);
        return instance;
    }

    private static ClassLoader setCCL(ClassLoader cl) {
        final Thread thread = Thread.currentThread();
        final ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(cl);
        return original;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (failedBoot) {
            throw new TestAbortedException("Not running test as boot failed");
        }
    }

    class ExtensionState implements ExtensionContext.Store.CloseableResource {

        private final TestResourceManager testResourceManager;
        private final Closeable resource;
        private final boolean nativeImage;

        ExtensionState(TestResourceManager testResourceManager, Closeable resource, boolean nativeImage) {
            this.testResourceManager = testResourceManager;
            this.resource = resource;
            this.nativeImage = nativeImage;
        }

        @Override
        public void close() throws Throwable {
            testResourceManager.stop();
            try {
                resource.close();
            } finally {
                if (QuarkusTestExtension.this.originalCl != null) {
                    setCCL(QuarkusTestExtension.this.originalCl);
                }
            }
            if (appCl != null) {
                appCl.close();
            }
        }

        /**
         * @deprecated Use {@link #isNativeImage()} instead.
         */
        @Deprecated
        public boolean isSubstrate() {
            return nativeImage;
        }

        public boolean isNativeImage() {
            return nativeImage;
        }
    }

    static class DeleteRunnable implements Runnable {
        final Path path;

        DeleteRunnable(Path path) {
            this.path = path;
        }

        @Override
        public void run() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
