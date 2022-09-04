package io.quarkus.container.image.s2i.deployment;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.dekorate.deps.kubernetes.api.model.HasMetadata;
import io.dekorate.deps.kubernetes.api.model.KubernetesList;
import io.dekorate.deps.kubernetes.api.model.Secret;
import io.dekorate.deps.kubernetes.client.KubernetesClient;
import io.dekorate.deps.kubernetes.client.dsl.LogWatch;
import io.dekorate.deps.openshift.api.model.Build;
import io.dekorate.deps.openshift.api.model.BuildConfig;
import io.dekorate.deps.openshift.api.model.ImageStream;
import io.dekorate.deps.openshift.client.OpenShiftClient;
import io.dekorate.s2i.util.S2iUtils;
import io.dekorate.utils.Clients;
import io.dekorate.utils.Packaging;
import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.container.image.deployment.ContainerImageConfig;
import io.quarkus.container.image.deployment.util.ImageUtil;
import io.quarkus.container.spi.BaseImageInfoBuildItem;
import io.quarkus.container.spi.ContainerImageBuildRequestBuildItem;
import io.quarkus.container.spi.ContainerImageInfoBuildItem;
import io.quarkus.container.spi.ContainerImagePushRequestBuildItem;
import io.quarkus.container.spi.ContainerImageResultBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.quarkus.kubernetes.client.deployment.KubernetesClientErrorHanlder;
import io.quarkus.kubernetes.client.spi.KubernetesClientBuildItem;
import io.quarkus.kubernetes.spi.KubernetesCommandBuildItem;
import io.quarkus.kubernetes.spi.KubernetesEnvBuildItem;

public class S2iProcessor {

    private static final String S2I = "s2i";
    private static final String OPENSHIFT = "openshift";
    private static final String BUILD_CONFIG_NAME = "openshift.io/build-config.name";
    private static final String RUNNING = "Running";

    private static final Logger LOG = Logger.getLogger(S2iProcessor.class);

    @BuildStep(onlyIf = IsNormal.class, onlyIfNot = NativeBuild.class)
    public void s2iRequirementsJvm(S2iConfig s2iConfig,
            CurateOutcomeBuildItem curateOutcomeBuildItem,
            OutputTargetBuildItem out,
            PackageConfig packageConfig,
            JarBuildItem jarBuildItem,
            BuildProducer<KubernetesEnvBuildItem> envProducer,
            BuildProducer<BaseImageInfoBuildItem> builderImageProducer,
            BuildProducer<KubernetesCommandBuildItem> commandProducer) {

        final List<AppDependency> appDeps = curateOutcomeBuildItem.getEffectiveModel().getUserDependencies();
        String outputJarFileName = jarBuildItem.getPath().getFileName().toString();
        String classpath = appDeps.stream()
                .map(d -> String.valueOf(d.getArtifact().getGroupId() + "." + d.getArtifact().getPath().getFileName()))
                .map(s -> Paths.get(s2iConfig.jarDirectory).resolve("lib").resolve(s).toAbsolutePath()
                        .toString())
                .collect(Collectors.joining(File.pathSeparator));

        String jarFileName = s2iConfig.jarFileName.orElse(outputJarFileName);
        String pathToJar = Paths.get(s2iConfig.jarDirectory).resolve(jarFileName)
                .toAbsolutePath()
                .toString();

        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList("-jar", pathToJar, "-cp", classpath));
        args.addAll(s2iConfig.jvmArguments);

        builderImageProducer.produce(new BaseImageInfoBuildItem(s2iConfig.baseJvmImage));
        Optional<S2iBaseJavaImage> baseImage = S2iBaseJavaImage.findMatching(s2iConfig.baseJvmImage);
        baseImage.ifPresent(b -> {
            envProducer.produce(new KubernetesEnvBuildItem(OPENSHIFT, b.getJarEnvVar(), pathToJar));
            envProducer.produce(new KubernetesEnvBuildItem(OPENSHIFT, b.getJarLibEnvVar(),
                    Paths.get(pathToJar).resolveSibling("lib").toAbsolutePath().toString()));
            envProducer.produce(new KubernetesEnvBuildItem(OPENSHIFT, b.getClasspathEnvVar(), classpath));
            envProducer.produce(new KubernetesEnvBuildItem(OPENSHIFT, b.getJvmOptionsEnvVar(),
                    s2iConfig.jvmArguments.stream().collect(Collectors.joining("  "))));
        });

        if (!baseImage.isPresent()) {
            commandProducer.produce(new KubernetesCommandBuildItem("java", args.toArray(new String[args.size()])));
        }
    }

    @BuildStep(onlyIf = { IsNormal.class, NativeBuild.class })
    public void s2iRequirementsNative(S2iConfig s2iConfig,
            CurateOutcomeBuildItem curateOutcomeBuildItem,
            OutputTargetBuildItem out,
            PackageConfig packageConfig,
            NativeImageBuildItem nativeImage,
            BuildProducer<KubernetesEnvBuildItem> envProducer,
            BuildProducer<BaseImageInfoBuildItem> builderImageProducer,
            BuildProducer<KubernetesCommandBuildItem> commandProducer) {

        boolean usingDefaultBuilder = ImageUtil.getRepository(S2iConfig.DEFAULT_BASE_NATIVE_IMAGE)
                .equals(ImageUtil.getRepository(s2iConfig.baseNativeImage));
        String outputNativeBinaryFileName = nativeImage.getPath().getFileName().toString();

        String nativeBinaryFileName = null;

        //The default s2i builder for native builds, renames the native binary.
        //To make things easier for the user, we need to handle it.
        if (usingDefaultBuilder && !s2iConfig.nativeBinaryFileName.isPresent()) {
            nativeBinaryFileName = S2iConfig.DEFAULT_NATIVE_TARGET_FILENAME;
        } else {
            nativeBinaryFileName = s2iConfig.nativeBinaryFileName.orElse(outputNativeBinaryFileName);
        }

        String pathToNativeBinary = Paths.get(s2iConfig.nativeBinaryDirectory).resolve(nativeBinaryFileName)
                .toAbsolutePath()
                .toString();

        builderImageProducer.produce(new BaseImageInfoBuildItem(s2iConfig.baseNativeImage));
        Optional<S2iBaseNativeImage> baseImage = S2iBaseNativeImage.findMatching(s2iConfig.baseNativeImage);
        baseImage.ifPresent(b -> {
            envProducer.produce(new KubernetesEnvBuildItem(OPENSHIFT, b.getHomeDirEnvVar(), s2iConfig.nativeBinaryDirectory));
            envProducer.produce(new KubernetesEnvBuildItem(OPENSHIFT, b.getOptsEnvVar(),
                    s2iConfig.nativeArguments.stream().collect(Collectors.joining(" "))));
        });

        if (!baseImage.isPresent()) {
            commandProducer.produce(new KubernetesCommandBuildItem(pathToNativeBinary,
                    s2iConfig.nativeArguments.toArray(new String[s2iConfig.nativeArguments.size()])));
        }
    }

    @BuildStep(onlyIf = { IsNormal.class, S2iBuild.class }, onlyIfNot = NativeBuild.class)
    public void s2iBuildFromJar(S2iConfig s2iConfig, ContainerImageConfig containerImageConfig,
            KubernetesClientBuildItem kubernetesClient,
            ContainerImageInfoBuildItem containerImage,
            ArchiveRootBuildItem archiveRoot, OutputTargetBuildItem out, PackageConfig packageConfig,
            List<GeneratedFileSystemResourceBuildItem> generatedResources,
            Optional<ContainerImageBuildRequestBuildItem> buildRequest,
            Optional<ContainerImagePushRequestBuildItem> pushRequest,
            BuildProducer<ArtifactResultBuildItem> artifactResultProducer,
            BuildProducer<ContainerImageResultBuildItem> containerImageResultProducer,
            // used to ensure that the jar has been built
            JarBuildItem jar) {

        if (!containerImageConfig.build && !containerImageConfig.push && !buildRequest.isPresent()
                && !pushRequest.isPresent()) {
            return;
        }

        String namespace = Optional.ofNullable(kubernetesClient.getClient().getNamespace()).orElse("default");
        LOG.info("Performing s2i binary build with jar on server: " + kubernetesClient.getClient().getMasterUrl()
                + " in namespace:" + namespace + ".");
        String image = containerImage.getImage();

        GeneratedFileSystemResourceBuildItem openshiftYml = generatedResources
                .stream()
                .filter(r -> r.getName().endsWith("kubernetes/openshift.yml"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Could not find kubernetes/openshift.yml"));

        createContainerImage(kubernetesClient, openshiftYml, s2iConfig, out.getOutputDirectory(), jar.getPath(),
                out.getOutputDirectory().resolve("lib"));
        artifactResultProducer.produce(new ArtifactResultBuildItem(null, "jar-container", Collections.emptyMap()));
        containerImageResultProducer.produce(
                new ContainerImageResultBuildItem(S2I, null, ImageUtil.getRepository(image), ImageUtil.getTag(image)));
    }

    @BuildStep(onlyIf = { IsNormal.class, S2iBuild.class, NativeBuild.class })
    public void s2iBuildFromNative(S2iConfig s2iConfig, ContainerImageConfig containerImageConfig,
            KubernetesClientBuildItem kubernetesClient,
            ContainerImageInfoBuildItem containerImage,
            ArchiveRootBuildItem archiveRoot, OutputTargetBuildItem out, PackageConfig packageConfig,
            List<GeneratedFileSystemResourceBuildItem> generatedResources,
            Optional<ContainerImageBuildRequestBuildItem> buildRequest,
            Optional<ContainerImagePushRequestBuildItem> pushRequest,
            BuildProducer<ArtifactResultBuildItem> artifactResultProducer,
            BuildProducer<ContainerImageResultBuildItem> containerImageResultProducer,
            NativeImageBuildItem nativeImage) {

        if (!containerImageConfig.build && !containerImageConfig.push && !buildRequest.isPresent()
                && !pushRequest.isPresent()) {
            return;
        }

        String namespace = Optional.ofNullable(kubernetesClient.getClient().getNamespace()).orElse("default");
        LOG.info("Performing s2i binary build with native image on server: " + kubernetesClient.getClient().getMasterUrl()
                + " in namespace:" + namespace + ".");

        String image = containerImage.getImage();

        GeneratedFileSystemResourceBuildItem openshiftYml = generatedResources
                .stream()
                .filter(r -> r.getName().endsWith("kubernetes/openshift.yml"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Could not find kubernetes/openshift.yml"));

        createContainerImage(kubernetesClient, openshiftYml, s2iConfig, out.getOutputDirectory(), nativeImage.getPath());
        artifactResultProducer.produce(new ArtifactResultBuildItem(null, "native-container", Collections.emptyMap()));
        containerImageResultProducer
                .produce(new ContainerImageResultBuildItem(S2I, null, ImageUtil.getRepository(image), ImageUtil.getTag(image)));
    }

    public static void createContainerImage(KubernetesClientBuildItem kubernetesClient,
            GeneratedFileSystemResourceBuildItem openshiftManifests,
            S2iConfig s2iConfig,
            Path output,
            Path... additional) {

        File tar;
        try {
            File original = Packaging.packageFile(output, additional);
            //Let's rename the archive and give it a more descriptive name, as it may appear in the logs.
            tar = Files.createTempFile("quarkus-", "-s2i").toFile();
            Files.move(original.toPath(), tar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Error creating the s2i binary build archive.", e);
        }

        Config config = kubernetesClient.getClient().getConfiguration();
        //Let's disable http2 as it causes issues with duplicate build triggers.
        config.setHttp2Disable(true);
        KubernetesClient client = Clients.fromConfig(config);
        KubernetesList kubernetesList = Serialization.unmarshalAsList(new ByteArrayInputStream(openshiftManifests.getData()));

        List<HasMetadata> buildResources = kubernetesList.getItems().stream()
                .filter(i -> i instanceof BuildConfig || i instanceof ImageStream || i instanceof Secret)
                .collect(Collectors.toList());

        applyS2iResources(client, buildResources);
        s2iBuild(client, buildResources, tar, s2iConfig);
    }

    /**
     * Apply the s2i resources and wait until ImageStreamTags are created.
     *
     * @param client the client instance
     * @param the resources to apply
     */
    private static void applyS2iResources(KubernetesClient client, List<HasMetadata> buildResources) {
        // Apply build resource requirements
        try {
            buildResources.forEach(i -> {
                if (i instanceof BuildConfig) {
                    client.resource(i).cascading(true).delete();
                    try {
                        client.resource(i).waitUntilCondition(d -> d == null, 10, TimeUnit.SECONDS);
                    } catch (IllegalArgumentException e) {
                        // We should ignore that, as its expected to be thrown when item is actually
                        // deleted.
                    } catch (InterruptedException e) {
                        s2iException(e);
                    }
                }
                client.resource(i).createOrReplace();
                LOG.info("Applied: " + i.getKind() + " " + i.getMetadata().getName());
            });
            S2iUtils.waitForImageStreamTags(buildResources, 2, TimeUnit.MINUTES);

        } catch (KubernetesClientException e) {
            KubernetesClientErrorHanlder.handle(e);
        }
    }

    private static void s2iBuild(KubernetesClient client, List<HasMetadata> buildResources, File binaryFile,
            S2iConfig s2iConfig) {
        buildResources.stream().filter(i -> i instanceof BuildConfig).map(i -> (BuildConfig) i)
                .forEach(bc -> s2iBuild(client.adapt(OpenShiftClient.class), bc, binaryFile, s2iConfig));
    }

    /**
     * Performs the binary build of the specified {@link BuildConfig} with the given
     * binary input.
     * 
     * @param client The openshift client instance
     * @param buildConfig The build config
     * @param binaryFile The binary file
     * @param s2iConfig The s2i configuration
     */
    private static void s2iBuild(OpenShiftClient client, BuildConfig buildConfig, File binaryFile, S2iConfig s2iConfig) {
        Build build;
        try {
            build = client.buildConfigs().withName(buildConfig.getMetadata().getName())
                    .instantiateBinary()
                    .withTimeoutInMillis(s2iConfig.buildTimeout.toMillis())
                    .fromFile(binaryFile);
        } catch (Exception e) {
            Optional<Build> running = runningBuildsOf(client, buildConfig).findFirst();
            if (running.isPresent()) {
                LOG.warn("An exception: '" + e.getMessage()
                        + " ' occurred while instantiating the build, however the build has been started.");
                build = running.get();
            } else {
                throw s2iException(e);
            }
        }

        final String buildName = build.getMetadata().getName();
        try (LogWatch w = client.builds().withName(build.getMetadata().getName()).withPrettyOutput().watchLog();
                BufferedReader reader = new BufferedReader(new InputStreamReader(w.getOutput()))) {
            waitForBuildComplete(client, s2iConfig, buildName, w);
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                LOG.info(line);
            }
        } catch (IOException e) {
            throw s2iException(e);
        }
    }

    private static void waitForBuildComplete(OpenShiftClient client, S2iConfig s2iConfig, String buildName, Closeable watch) {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                client.builds().withName(buildName).waitUntilCondition(b -> !RUNNING.equalsIgnoreCase(b.getStatus().getPhase()),
                        s2iConfig.buildTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                s2iException(e);
            } finally {
                try {
                    watch.close();
                } catch (IOException e) {
                    LOG.debug("Error closing log reader.");
                }
            }
        });
    }

    private static List<Build> buildsOf(OpenShiftClient client, BuildConfig config) {
        return client.builds().withLabel(BUILD_CONFIG_NAME, config.getMetadata().getName()).list().getItems();
    }

    private static Stream<Build> runningBuildsOf(OpenShiftClient client, BuildConfig config) {
        return buildsOf(client, config).stream().filter(b -> RUNNING.equalsIgnoreCase(b.getStatus().getPhase()));
    }

    private static RuntimeException s2iException(Throwable t) {
        if (t instanceof KubernetesClientException) {
            KubernetesClientErrorHanlder.handle((KubernetesClientException) t);
        }
        return new RuntimeException("Execution of s2i build failed. See s2i output for more details", t);
    }
}
