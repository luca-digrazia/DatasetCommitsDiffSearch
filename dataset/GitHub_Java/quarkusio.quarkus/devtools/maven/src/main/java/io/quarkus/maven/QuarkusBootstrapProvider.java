package io.quarkus.maven;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.RemoteRepositoryManager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.model.PathsCollection;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

@Component(role = QuarkusBootstrapProvider.class, instantiationStrategy = "singleton")
public class QuarkusBootstrapProvider implements Closeable {

    @Requirement(role = RepositorySystem.class, optional = false)
    protected RepositorySystem repoSystem;

    @Requirement(role = RemoteRepositoryManager.class, optional = false)
    protected RemoteRepositoryManager remoteRepoManager;

    private final Cache<AppArtifactKey, QuarkusAppBootstrapProvider> appBootstrapProviders = CacheBuilder.newBuilder()
            .concurrencyLevel(4).softValues().initialCapacity(10).build();

    public RepositorySystem repositorySystem() {
        return repoSystem;
    }

    public RemoteRepositoryManager remoteRepositoryManager() {
        return remoteRepoManager;
    }

    private QuarkusAppBootstrapProvider provider(AppArtifactKey projectId) {
        try {
            return appBootstrapProviders.get(projectId, () -> new QuarkusAppBootstrapProvider());
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to cache a new instance of " + QuarkusAppBootstrapProvider.class.getName(),
                    e);
        }
    }

    public MavenArtifactResolver artifactResolver(QuarkusBootstrapMojo mojo)
            throws MojoExecutionException {
        return provider(mojo.projectId()).artifactResolver(mojo);
    }

    public AppArtifact projectArtifact(QuarkusBootstrapMojo mojo)
            throws MojoExecutionException {
        return provider(mojo.projectId()).projectArtifact(mojo);
    }

    public QuarkusBootstrap bootstrapQuarkus(QuarkusBootstrapMojo mojo)
            throws MojoExecutionException {
        return provider(mojo.projectId()).bootstrapQuarkus(mojo);
    }

    public CuratedApplication bootstrapApplication(QuarkusBootstrapMojo mojo)
            throws MojoExecutionException {
        return provider(mojo.projectId()).curateApplication(mojo);
    }

    @Override
    public void close() throws IOException {
        if (appBootstrapProviders.size() == 0) {
            return;
        }
        for (QuarkusAppBootstrapProvider p : appBootstrapProviders.asMap().values()) {
            try {
                p.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class QuarkusAppBootstrapProvider implements Closeable {

        private AppArtifact projectArtifact;
        private MavenArtifactResolver artifactResolver;
        private QuarkusBootstrap quarkusBootstrap;
        private CuratedApplication curatedApp;

        private MavenArtifactResolver artifactResolver(QuarkusBootstrapMojo mojo)
                throws MojoExecutionException {
            if (artifactResolver != null) {
                return artifactResolver;
            }
            try {
                return artifactResolver = MavenArtifactResolver.builder()
                        .setWorkspaceDiscovery(false)
                        .setRepositorySystem(repoSystem)
                        .setRepositorySystemSession(mojo.repositorySystemSession())
                        .setRemoteRepositories(mojo.remoteRepositories())
                        .setRemoteRepositoryManager(remoteRepoManager)
                        .build();
            } catch (BootstrapMavenException e) {
                throw new MojoExecutionException("Failed to initialize Quarkus bootstrap Maven artifact resolver", e);
            }
        }

        private AppArtifact projectArtifact(QuarkusBootstrapMojo mojo) throws MojoExecutionException {
            if (projectArtifact != null) {
                return projectArtifact;
            }
            final Artifact projectArtifact = mojo.mavenProject().getArtifact();
            final AppArtifact appArtifact = new AppArtifact(projectArtifact.getGroupId(), projectArtifact.getArtifactId(),
                    projectArtifact.getClassifier(), projectArtifact.getArtifactHandler().getExtension(),
                    projectArtifact.getVersion());

            File projectFile = projectArtifact.getFile();
            if (projectFile == null) {
                projectFile = new File(mojo.mavenProject().getBuild().getOutputDirectory());
                if (!projectFile.exists()) {
                    /*
                     * TODO GenerateCodeMojo would fail
                     * if (hasSources(project)) {
                     * throw new MojoExecutionException("Project " + project.getArtifact() + " has not been compiled yet");
                     * }
                     */
                    if (!projectFile.mkdirs()) {
                        throw new MojoExecutionException("Failed to create the output dir " + projectFile);
                    }
                }
            }
            appArtifact.setPaths(PathsCollection.of(projectFile.toPath()));
            return appArtifact;
        }

        protected QuarkusBootstrap bootstrapQuarkus(QuarkusBootstrapMojo mojo) throws MojoExecutionException {
            if (quarkusBootstrap != null) {
                return quarkusBootstrap;
            }

            final Properties projectProperties = mojo.mavenProject().getProperties();
            final Properties effectiveProperties = new Properties();
            // quarkus. properties > ignoredEntries in pom.xml
            if (mojo.ignoredEntries() != null && mojo.ignoredEntries().length > 0) {
                String joinedEntries = String.join(",", mojo.ignoredEntries());
                effectiveProperties.setProperty("quarkus.package.user-configured-ignored-entries", joinedEntries);
            }
            for (String name : projectProperties.stringPropertyNames()) {
                if (name.startsWith("quarkus.")) {
                    effectiveProperties.setProperty(name, projectProperties.getProperty(name));
                }
            }
            if (mojo.uberJar() && System.getProperty(BuildMojo.QUARKUS_PACKAGE_UBER_JAR) == null) {
                System.setProperty(BuildMojo.QUARKUS_PACKAGE_UBER_JAR, "true");
                mojo.clearUberJarProp();
            }
            effectiveProperties.putIfAbsent("quarkus.application.name", mojo.mavenProject().getArtifactId());
            effectiveProperties.putIfAbsent("quarkus.application.version", mojo.mavenProject().getVersion());

            QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                    .setAppArtifact(projectArtifact(mojo))
                    .setMavenArtifactResolver(artifactResolver(mojo))
                    .setIsolateDeployment(true)
                    .setBaseClassLoader(getClass().getClassLoader())
                    .setBuildSystemProperties(effectiveProperties)
                    .setLocalProjectDiscovery(false)
                    .setProjectRoot(mojo.baseDir().toPath())
                    .setBaseName(mojo.finalName())
                    .setTargetDirectory(mojo.buildDir().toPath());

            for (MavenProject project : mojo.mavenProject().getCollectedProjects()) {
                builder.addLocalArtifact(new AppArtifactKey(project.getGroupId(), project.getArtifactId(), null,
                        project.getArtifact().getArtifactHandler().getExtension()));
            }

            return quarkusBootstrap = builder.build();
        }

        protected CuratedApplication curateApplication(QuarkusBootstrapMojo mojo) throws MojoExecutionException {
            if (curatedApp != null) {
                return curatedApp;
            }
            try {
                return curatedApp = bootstrapQuarkus(mojo).bootstrap();
            } catch (MojoExecutionException e) {
                throw e;
            } catch (BootstrapException e) {
                throw new MojoExecutionException("Failed to bootstrap the application", e);
            }
        }

        @Override
        public void close() throws IOException {
            if (curatedApp != null) {
                curatedApp.close();
                curatedApp = null;
            }
            this.artifactResolver = null;
            this.quarkusBootstrap = null;
        }
    }

    /*
     * private static boolean hasSources(MavenProject project) {
     * if (new File(project.getBuild().getSourceDirectory()).exists()) {
     * return true;
     * }
     * for (Resource r : project.getBuild().getResources()) {
     * if (new File(r.getDirectory()).exists()) {
     * return true;
     * }
     * }
     * return false;
     * }
     */
}
