package io.quarkus.maven;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.repository.RemoteRepository;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.util.IoUtils;

/**
 * Builds the Quarkus application.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class BuildMojo extends QuarkusBootstrapMojo {

    public static final String QUARKUS_PACKAGE_UBER_JAR = "quarkus.package.uber-jar";
    private static final String PACKAGE_TYPE_PROP = "quarkus.package.type";
    private static final String NATIVE_PROFILE_NAME = "native";
    private static final String NATIVE_PACKAGE_TYPE = "native";

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     */
    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
    private List<RemoteRepository> pluginRepos;

    /**
     * The directory for generated source files.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private File generatedSourcesDirectory;

    /**
     * Skips the execution of this mojo
     */
    @Parameter(defaultValue = "false", property = "quarkus.build.skip")
    private boolean skip = false;

    @Deprecated
    @Parameter(property = "skipOriginalJarRename")
    boolean skipOriginalJarRename;

    @Override
    protected boolean beforeExecute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping Quarkus build");
            return false;
        }
        if (mavenProject().getPackaging().equals("pom")) {
            getLog().info("Type of the artifact is POM, skipping build goal");
            return false;
        }
        if (!mavenProject().getArtifact().getArtifactHandler().getExtension().equals("jar")) {
            throw new MojoExecutionException(
                    "The project artifact's extension is '" + mavenProject().getArtifact().getArtifactHandler().getExtension()
                            + "' while this goal expects it be 'jar'");
        }
        return true;
    }

    @Override
    protected void doExecute() throws MojoExecutionException {

        try {
            boolean clearPackageTypeSysProp = false;
            // Essentially what this does is to enable the native package type even if a different package type is set
            // in application properties. This is done to preserve what users expect to happen when
            // they execute "mvn package -Dnative" even if quarkus.package.type has been set in application.properties
            if (!System.getProperties().containsKey(PACKAGE_TYPE_PROP)
                    && isNativeProfileEnabled(mavenProject())) {
                Object packageTypeProp = mavenProject().getProperties().get(PACKAGE_TYPE_PROP);
                String packageType = NATIVE_PACKAGE_TYPE;
                if (packageTypeProp != null) {
                    packageType = packageTypeProp.toString();
                }
                System.setProperty(PACKAGE_TYPE_PROP, packageType);
                clearPackageTypeSysProp = true;
            }
            try (CuratedApplication curatedApplication = bootstrapApplication()) {

                AugmentAction action = curatedApplication.createAugmentor();
                AugmentResult result = action.createProductionApplication();

                Artifact original = mavenProject().getArtifact();
                if (result.getJar() != null) {

                    if (!skipOriginalJarRename && result.getJar().isUberJar()
                            && result.getJar().getOriginalArtifact() != null) {
                        final Path standardJar = result.getJar().getOriginalArtifact();
                        if (Files.exists(standardJar)) {
                            final Path renamedOriginal = standardJar.getParent().toAbsolutePath()
                                    .resolve(standardJar.getFileName() + ".original");
                            try {
                                IoUtils.recursiveDelete(renamedOriginal);
                                Files.move(standardJar, renamedOriginal);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            original.setFile(result.getJar().getOriginalArtifact().toFile());
                        }
                    }
                    if (result.getJar().isUberJar()) {
                        projectHelper.attachArtifact(mavenProject(), result.getJar().getPath().toFile(),
                                result.getJar().getClassifier());
                    }
                }
            } finally {
                if (clearPackageTypeSysProp) {
                    System.clearProperty(PACKAGE_TYPE_PROP);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build quarkus application", e);
        }
    }

    private boolean isNativeProfileEnabled(MavenProject mavenProject) {
        // gotcha: mavenProject.getActiveProfiles() does not always contain all active profiles (sic!),
        //         but getInjectedProfileIds() does (which has to be "flattened" first)
        Stream<String> activeProfileIds = mavenProject.getInjectedProfileIds().values().stream().flatMap(List<String>::stream);
        if (activeProfileIds.anyMatch(NATIVE_PROFILE_NAME::equalsIgnoreCase)) {
            return true;
        }
        // recurse into parent (if available)
        return Optional.ofNullable(mavenProject.getParent()).map(this::isNativeProfileEnabled).orElse(false);
    }

    @Override
    public void setLog(Log log) {
        super.setLog(log);
        MojoLogger.delegate = log;
    }

}
