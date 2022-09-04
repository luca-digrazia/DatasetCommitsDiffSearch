package io.quarkus.gradle.tasks;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.options.Option;

import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.bootstrap.model.AppModel;
import io.quarkus.bootstrap.resolver.AppModelResolver;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.util.PropertyUtils;
import io.quarkus.dev.DevModeContext;
import io.quarkus.dev.DevModeMain;
import io.quarkus.gradle.QuarkusPluginExtension;
import io.quarkus.utilities.JavaBinFinder;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class QuarkusDev extends QuarkusTask {

    private Set<File> filesIncludedInClasspath = new HashSet<>();

    private File buildDir;

    private String sourceDir;

    private String jvmArgs;

    private boolean preventnoverify = false;

    public QuarkusDev() {
        super("Development mode: enables hot deployment with background compilation");
    }

    @InputDirectory
    @Optional
    public File getBuildDir() {
        if (buildDir == null)
            buildDir = getProject().getBuildDir();
        return buildDir;
    }

    public void setBuildDir(File buildDir) {
        this.buildDir = buildDir;
    }

    @Optional
    @InputDirectory
    public File getSourceDir() {
        if (sourceDir == null)
            return extension().sourceDir();
        else
            return new File(sourceDir);
    }

    @Option(description = "Set source directory", option = "source-dir")
    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    @Optional
    @Input
    public String getJvmArgs() {
        return jvmArgs;
    }

    @Option(description = "Set JVM arguments", option = "jvm-args")
    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    @Optional
    @Input
    public boolean isPreventnoverify() {
        return preventnoverify;
    }

    @Option(description = "value is intended to be set to true when some generated bytecode is" +
            " erroneous causing the JVM to crash when the verify:none option is set " +
            "(which is on by default)", option = "prevent-noverify")
    public void setPreventnoverify(boolean preventnoverify) {
        this.preventnoverify = preventnoverify;
    }

    @TaskAction
    public void startDev() {

        Project project = getProject();
        QuarkusPluginExtension extension = (QuarkusPluginExtension) project.getExtensions().findByName("quarkus");

        if (!getSourceDir().isDirectory()) {
            throw new GradleException("The `src/main/java` directory is required, please create it.");
        }

        if (!extension().outputDirectory().isDirectory()) {
            throw new GradleException("The project has no output yet, " +
                    "this should not happen as build should have been executed first. " +
                    "Does the project have any source files?");
        }
        DevModeContext context = new DevModeContext();
        context.setSourceEncoding(getSourceEncoding());
        try {
            List<String> args = new ArrayList<>();
            args.add(JavaBinFinder.findBin());
            String debug = System.getProperty("debug");
            if (debug == null) {
                // debug mode not specified
                // make sure 5005 is not used, we don't want to just fail if something else is using it
                try (Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), 5005)) {
                    System.err.println("Port 5005 in use, not starting in debug mode");
                } catch (IOException e) {
                    args.add("-Xdebug");
                    args.add("-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n");
                }
            } else if (debug.toLowerCase().equals("client")) {
                args.add("-Xdebug");
                args.add("-Xrunjdwp:transport=dt_socket,address=localhost:5005,server=n,suspend=n");
            } else if (debug.toLowerCase().equals("true") || debug.isEmpty()) {
                args.add("-Xdebug");
                args.add("-Xrunjdwp:transport=dt_socket,address=localhost:5005,server=y,suspend=y");
            } else if (!debug.toLowerCase().equals("false")) {
                try {
                    int port = Integer.parseInt(debug);
                    if (port <= 0) {
                        throw new GradleException("The specified debug port must be greater than 0");
                    }
                    args.add("-Xdebug");
                    args.add("-Xrunjdwp:transport=dt_socket,address=" + port + ",server=y,suspend=y");
                } catch (NumberFormatException e) {
                    throw new GradleException(
                            "Invalid value for debug parameter: " + debug + " must be true|false|client|{port}");
                }
            }
            if (getJvmArgs() != null) {
                args.addAll(Arrays.asList(getJvmArgs().split(" ")));
            }

            // the following flags reduce startup time and are acceptable only for dev purposes
            args.add("-XX:TieredStopAtLevel=1");
            if (!isPreventnoverify()) {
                args.add("-Xverify:none");
            }

            //build a class-path string for the base platform
            //this stuff does not change
            // Do not include URIs in the manifest, because some JVMs do not like that
            StringBuilder classPathManifest = new StringBuilder();

            final AppModel appModel;
            final AppModelResolver modelResolver = extension().resolveAppModel();
            try {
                final AppArtifact appArtifact = extension.getAppArtifact();
                appArtifact.setPath(extension.outputDirectory().toPath());
                appModel = modelResolver.resolveModel(appArtifact);
            } catch (AppModelResolverException e) {
                throw new GradleException("Failed to resolve application model " + extension.getAppArtifact() + " dependencies",
                        e);
            }
            for (AppDependency appDep : appModel.getAllDependencies()) {
                addToClassPaths(classPathManifest, context, appDep.getArtifact().getPath().toFile());
            }

            args.add("-Djava.util.logging.manager=org.jboss.logmanager.LogManager");
            File wiringClassesDirectory = new File(getBuildDir(), "wiring-classes");
            wiringClassesDirectory.mkdirs();
            addToClassPaths(classPathManifest, context, wiringClassesDirectory);

            //we also want to add the maven plugin jar to the class path
            //this allows us to just directly use classes, without messing around copying them
            //to the runner jar
            addGradlePluginDeps(classPathManifest, context);

            //now we need to build a temporary jar to actually run

            File tempFile = new File(getBuildDir(), extension.finalName() + "-dev.jar");
            tempFile.delete();
            tempFile.deleteOnExit();

            StringBuilder resources = new StringBuilder();
            String res = null;
            for (File file : extension.resourcesDir()) {
                if (resources.length() > 0)
                    resources.append(File.pathSeparator);
                resources.append(file.getAbsolutePath());
                res = file.getAbsolutePath();
            }

            final Configuration compileCp = project.getConfigurations().getByName("compileClasspath");
            final DependencySet compileCpDependencies = compileCp.getAllDependencies();

            for (Dependency dependency : compileCpDependencies) {
                if (!(dependency instanceof ProjectDependency)) {
                    continue;
                }

                Project dependencyProject = ((ProjectDependency) dependency).getDependencyProject();
                Convention convention = dependencyProject.getConvention();
                JavaPluginConvention javaConvention = convention.findPlugin(JavaPluginConvention.class);
                if (javaConvention == null) {
                    continue;
                }

                SourceSetContainer sourceSets = javaConvention.getSourceSets();
                SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                Set<String> sourcePaths = new HashSet<>();

                for (File sourceDir : mainSourceSet.getAllJava().getSrcDirs()) {
                    sourcePaths.add(sourceDir.getAbsolutePath());
                }

                String classesPaths = mainSourceSet.getOutput().getClassesDirs().getAsPath();
                String resourcePaths = mainSourceSet.getResources().getSourceDirectories().getAsPath();

                DevModeContext.ModuleInfo wsModuleInfo = new DevModeContext.ModuleInfo(
                        dependencyProject.getName(),
                        dependencyProject.getProjectDir().getAbsolutePath(),
                        sourcePaths,
                        classesPaths,
                        resourcePaths);

                context.getModules().add(wsModuleInfo);
            }

            DevModeContext.ModuleInfo moduleInfo = new DevModeContext.ModuleInfo(
                    project.getName(),
                    project.getProjectDir().getAbsolutePath(),
                    Collections.singleton(getSourceDir().getAbsolutePath()),
                    extension.outputDirectory().getAbsolutePath(),
                    res);
            context.getModules().add(moduleInfo);

            final String outputClassDirectory = extension.outputDirectory().getAbsolutePath();
            final String outputResourcesDirectory = extension.outputConfigDirectory().getAbsolutePath();
            context.getClassesRoots().add(extension.outputDirectory().getAbsoluteFile());
            if (!outputClassDirectory.equals(outputResourcesDirectory)) {
                context.getClassesRoots().add(extension.outputConfigDirectory().getAbsoluteFile());
            }
            context.setFrameworkClassesDir(wiringClassesDirectory.getAbsoluteFile());
            context.setCacheDir(new File(getBuildDir(), "transformer-cache").getAbsoluteFile());

            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempFile))) {
                out.putNextEntry(new ZipEntry("META-INF/"));
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPathManifest.toString());
                manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, DevModeMain.class.getName());
                out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                manifest.write(out);

                out.putNextEntry(new ZipEntry(DevModeMain.DEV_MODE_CONTEXT));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream obj = new ObjectOutputStream(new DataOutputStream(bytes));
                obj.writeObject(context);
                obj.close();
                out.write(bytes.toByteArray());
            }

            extension.outputDirectory().mkdirs();

            args.add("-jar");
            args.add(tempFile.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(args.toArray(new String[0]));
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb.directory(extension.outputDirectory());
            System.out.println("Starting process: ");
            pb.command().forEach(System.out::println);
            System.out.println("Args: ");
            args.forEach(System.out::println);

            Process p = pb.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    p.destroy();
                }
            }, "Development Mode Shutdown Hook"));
            try {
                ExecutorService es = Executors.newSingleThreadExecutor();
                es.submit(() -> copyOutputToConsole(p.getInputStream()));

                p.waitFor();
            } catch (Exception e) {
                p.destroy();
                throw e;
            }

        } catch (Exception e) {
            throw new GradleException("Failed to run", e);
        }
    }

    private String getSourceEncoding() {
        Task javaCompile = getProject().getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME);
        if (javaCompile != null) {
            return ((JavaCompile) javaCompile).getOptions().getEncoding();
        }
        return null;
    }

    private void copyOutputToConsole(InputStream is) {
        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            throw new GradleException("Failed to copy output to console", e);
        }
    }

    private void addGradlePluginDeps(StringBuilder classPathManifest, DevModeContext context) {
        Configuration conf = getProject().getBuildscript().getConfigurations().getByName("classpath");
        ResolvedDependency quarkusDep = conf.getResolvedConfiguration().getFirstLevelModuleDependencies().stream()
                .filter(rd -> "quarkus-gradle-plugin".equals(rd.getModuleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to find quarkus-gradle-plugin dependency"));

        quarkusDep.getAllModuleArtifacts().stream()
                .map(ra -> ra.getFile())
                .forEach(f -> addToClassPaths(classPathManifest, context, f));
    }

    private void addToClassPaths(StringBuilder classPathManifest, DevModeContext context, File file) {
        if (filesIncludedInClasspath.add(file)) {
            getProject().getLogger().info("Adding dependency {}", file);

            URI uri = file.toPath().toAbsolutePath().toUri();
            String path = uri.getRawPath();
            if (PropertyUtils.isWindows()) {
                if (path.length() > 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
                    path = "/" + path;
                }
            }
            classPathManifest.append(path);
            context.getClassPath().add(toUrl(uri));
            if (file.isDirectory()) {
                classPathManifest.append("/");
            }
            classPathManifest.append(" ");
        }
    }

    private URL toUrl(URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Failed to convert URI to URL: " + uri, e);
        }
    }

}
