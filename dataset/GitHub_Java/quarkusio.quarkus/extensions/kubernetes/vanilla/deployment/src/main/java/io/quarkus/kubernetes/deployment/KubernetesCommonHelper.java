
package io.quarkus.kubernetes.deployment;

import static io.quarkus.kubernetes.deployment.Constants.QUARKUS_ANNOTATIONS_BUILD_TIMESTAMP;
import static io.quarkus.kubernetes.deployment.Constants.QUARKUS_ANNOTATIONS_COMMIT_ID;
import static io.quarkus.kubernetes.deployment.Constants.QUARKUS_ANNOTATIONS_VCS_URL;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.dekorate.kubernetes.config.Annotation;
import io.dekorate.kubernetes.config.PortBuilder;
import io.dekorate.kubernetes.configurator.AddPort;
import io.dekorate.kubernetes.decorator.AddAnnotationDecorator;
import io.dekorate.kubernetes.decorator.AddAwsElasticBlockStoreVolumeDecorator;
import io.dekorate.kubernetes.decorator.AddAzureDiskVolumeDecorator;
import io.dekorate.kubernetes.decorator.AddAzureFileVolumeDecorator;
import io.dekorate.kubernetes.decorator.AddConfigMapVolumeDecorator;
import io.dekorate.kubernetes.decorator.AddHostAliasesDecorator;
import io.dekorate.kubernetes.decorator.AddImagePullSecretDecorator;
import io.dekorate.kubernetes.decorator.AddInitContainerDecorator;
import io.dekorate.kubernetes.decorator.AddLabelDecorator;
import io.dekorate.kubernetes.decorator.AddLivenessProbeDecorator;
import io.dekorate.kubernetes.decorator.AddMountDecorator;
import io.dekorate.kubernetes.decorator.AddPvcVolumeDecorator;
import io.dekorate.kubernetes.decorator.AddReadinessProbeDecorator;
import io.dekorate.kubernetes.decorator.AddRoleBindingResourceDecorator;
import io.dekorate.kubernetes.decorator.AddSecretVolumeDecorator;
import io.dekorate.kubernetes.decorator.AddServiceAccountResourceDecorator;
import io.dekorate.kubernetes.decorator.ApplyArgsDecorator;
import io.dekorate.kubernetes.decorator.ApplyCommandDecorator;
import io.dekorate.kubernetes.decorator.ApplyServiceAccountNamedDecorator;
import io.dekorate.kubernetes.decorator.ApplyWorkingDirDecorator;
import io.dekorate.kubernetes.decorator.RemoveAnnotationDecorator;
import io.dekorate.project.BuildInfo;
import io.dekorate.project.FileProjectFactory;
import io.dekorate.project.Project;
import io.dekorate.project.ScmInfo;
import io.dekorate.utils.Annotations;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.deployment.Annotations.Prometheus;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;
import io.quarkus.kubernetes.spi.KubernetesAnnotationBuildItem;
import io.quarkus.kubernetes.spi.KubernetesCommandBuildItem;
import io.quarkus.kubernetes.spi.KubernetesHealthLivenessPathBuildItem;
import io.quarkus.kubernetes.spi.KubernetesHealthReadinessPathBuildItem;
import io.quarkus.kubernetes.spi.KubernetesLabelBuildItem;
import io.quarkus.kubernetes.spi.KubernetesPortBuildItem;
import io.quarkus.kubernetes.spi.KubernetesRoleBindingBuildItem;
import io.quarkus.kubernetes.spi.KubernetesRoleBuildItem;

public class KubernetesCommonHelper {

    private static final String OUTPUT_ARTIFACT_FORMAT = "%s%s.jar";

    public static Project createProject(ApplicationInfoBuildItem app, OutputTargetBuildItem outputTarget,
            PackageConfig packageConfig) {
        return createProject(app, outputTarget.getOutputDirectory()
                .resolve(String.format(OUTPUT_ARTIFACT_FORMAT, outputTarget.getBaseName(), packageConfig.runnerSuffix)));
    }

    public static Project createProject(ApplicationInfoBuildItem app, Path artifactPath) {
        //Let dekorate create a Project instance and then override with what is found in ApplicationInfoBuildItem.
        Project project = FileProjectFactory.create(artifactPath.toFile());
        BuildInfo buildInfo = new BuildInfo(app.getName(), app.getVersion(),
                "jar", project.getBuildInfo().getBuildTool(),
                project.getBuildInfo().getBuildToolVersion(),
                artifactPath.toAbsolutePath(),
                project.getBuildInfo().getClassOutputDir(),
                project.getBuildInfo().getResourceDir());

        return new Project(project.getRoot(), buildInfo, project.getScmInfo());
    }

    /**
     * Creates the common configurator build items.
     */
    public static List<ConfiguratorBuildItem> createGlobalConfigurators(List<KubernetesPortBuildItem> ports) {
        List<ConfiguratorBuildItem> result = new ArrayList<>();
        verifyPorts(ports).entrySet().stream()
                .map(e -> new PortBuilder().withName(e.getKey()).withContainerPort(e.getValue()).build())
                .forEach(p -> result.add(new ConfiguratorBuildItem(new AddPort(p))));
        return result;
    }

    /**
     * Creates the common configurator build items.
     */
    public static List<ConfiguratorBuildItem> createPlatformConfigurators(PlatformConfiguration config) {
        List<ConfiguratorBuildItem> result = new ArrayList<>();
        config.getPorts().entrySet().forEach(e -> result.add(new ConfiguratorBuildItem(new AddPort(PortConverter.convert(e)))));
        return result;
    }

    /**
     * Creates the common decorator build items.
     */
    public static List<DecoratorBuildItem> createDecorators(Project project, String target, String name,
            PlatformConfiguration config,
            Optional<MetricsCapabilityBuildItem> metricsConfiguration,
            List<KubernetesAnnotationBuildItem> annotations,
            List<KubernetesLabelBuildItem> labels,
            Optional<KubernetesCommandBuildItem> command,
            List<KubernetesPortBuildItem> ports,
            Optional<KubernetesHealthLivenessPathBuildItem> livenessProbePath,
            Optional<KubernetesHealthReadinessPathBuildItem> readinessProbePath,
            List<KubernetesRoleBuildItem> roles,
            List<KubernetesRoleBindingBuildItem> roleBindings) {
        List<DecoratorBuildItem> result = new ArrayList<>();

        annotations.forEach(a -> {
            result.add(new DecoratorBuildItem(a.getTarget(),
                    new AddAnnotationDecorator(name, a.getKey(), a.getValue())));
        });

        labels.forEach(l -> {
            result.add(new DecoratorBuildItem(l.getTarget(),
                    new AddLabelDecorator(name, l.getKey(), l.getValue())));
        });

        result.addAll(createAnnotationDecorators(project, target, name, config, metricsConfiguration, ports));
        result.addAll(createPodDecorators(project, target, name, config));
        result.addAll(createContainerDecorators(project, target, name, config));
        result.addAll(createMountAndVolumeDecorators(project, target, name, config));

        //Handle Command and arguments
        command.ifPresent(c -> {
            result.add(new DecoratorBuildItem(new ApplyCommandDecorator(name, new String[] { c.getCommand() })));
            result.add(new DecoratorBuildItem(new ApplyArgsDecorator(name, c.getArgs())));
        });

        //Handle Probes
        result.addAll(createProbeDecorators(name, target, config.getLivenessProbe(), config.getReadinessProbe(),
                livenessProbePath, readinessProbePath));

        //Handle RBAC
        if (!roleBindings.isEmpty()) {
            result.add(new DecoratorBuildItem(new ApplyServiceAccountNamedDecorator()));
            result.add(new DecoratorBuildItem(new AddServiceAccountResourceDecorator()));
            roles.forEach(r -> result.add(new DecoratorBuildItem(new AddRoleResourceDecorator(r))));
            roleBindings.forEach(rb -> result.add(new DecoratorBuildItem(
                    new AddRoleBindingResourceDecorator(rb.getName(), null, rb.getRole(), rb.isClusterWide()
                            ? AddRoleBindingResourceDecorator.RoleKind.ClusterRole
                            : AddRoleBindingResourceDecorator.RoleKind.Role))));
        }

        // The presence of optional is causing issues in OCP 3.11, so we better remove them.
        // The following 4 decorator will set the optional property to null, so that it won't make it into the file.
        result.add(new DecoratorBuildItem(target, new RemoveOptionalFromSecretEnvSourceDecorator()));
        result.add(new DecoratorBuildItem(target, new RemoveOptionalFromConfigMapEnvSourceDecorator()));
        result.add(new DecoratorBuildItem(target, new RemoveOptionalFromSecretKeySelectorDecorator()));
        result.add(new DecoratorBuildItem(target, new RemoveOptionalFromConfigMapKeySelectorDecorator()));
        return result;
    }

    /**
     * Creates container decorator build items.
     * 
     * @param target The deployment target (e.g. kubernetes, openshift, knative)
     * @param name The name of the resource to accept the configuration
     * @param config The {@link PlatformConfiguration} instance
     */
    private static List<DecoratorBuildItem> createContainerDecorators(Project project, String target, String name,
            PlatformConfiguration config) {
        List<DecoratorBuildItem> result = new ArrayList<>();
        if (config.getNamespace().isPresent()) {
            result.add(new DecoratorBuildItem(target, new AddNamespaceDecorator(config.getNamespace().get())));
        }

        config.getWorkingDir().ifPresent(w -> {
            result.add(new DecoratorBuildItem(target, new ApplyWorkingDirDecorator(name, w)));
        });

        config.getCommand().ifPresent(c -> {
            result.add(new DecoratorBuildItem(target, new ApplyCommandDecorator(name, c.toArray(new String[0]))));
        });

        config.getArguments().ifPresent(a -> {
            result.add(new DecoratorBuildItem(target, new ApplyArgsDecorator(name, a.toArray(new String[0]))));
        });

        return result;
    }

    /**
     * Creates pod decorator build items.
     * 
     * @param target The deployment target (e.g. kubernetes, openshift, knative)
     * @param name The name of the resource to accept the configuration
     * @param config The {@link PlatformConfiguration} instance
     */
    private static List<DecoratorBuildItem> createPodDecorators(Project project, String target, String name,
            PlatformConfiguration config) {
        List<DecoratorBuildItem> result = new ArrayList<>();
        config.getImagePullSecrets().ifPresent(l -> {
            l.forEach(s -> result.add(new DecoratorBuildItem(target, new AddImagePullSecretDecorator(name, s))));
        });

        config.getHostAliases().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddHostAliasesDecorator(name, HostAliasConverter.convert(e))));
        });

        config.getServiceAccount().ifPresent(s -> {
            result.add(new DecoratorBuildItem(target, new ApplyServiceAccountNamedDecorator(name, s)));
        });

        config.getInitContainers().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddInitContainerDecorator(name, ContainerConverter.convert(e))));
        });

        config.getSidecars().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddSidecarDecorator(name, ContainerConverter.convert(e))));
        });

        return result;
    }

    private static List<DecoratorBuildItem> createMountAndVolumeDecorators(Project project, String target, String name,
            PlatformConfiguration config) {
        List<DecoratorBuildItem> result = new ArrayList<>();
        config.getMounts().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddMountDecorator(MountConverter.convert(e))));
        });

        config.getSecretVolumes().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddSecretVolumeDecorator(SecretVolumeConverter.convert(e))));
        });

        config.getConfigMapVolumes().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddConfigMapVolumeDecorator(ConfigMapVolumeConverter.convert(e))));
        });

        config.getPvcVolumes().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddPvcVolumeDecorator(PvcVolumeConverter.convert(e))));
        });

        config.getAwsElasticBlockStoreVolumes().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target,
                    new AddAwsElasticBlockStoreVolumeDecorator(AwsElasticBlockStoreVolumeConverter.convert(e))));
        });

        config.getAzureFileVolumes().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddAzureFileVolumeDecorator(AzureFileVolumeConverter.convert(e))));
        });

        config.getAzureDiskVolumes().entrySet().forEach(e -> {
            result.add(new DecoratorBuildItem(target, new AddAzureDiskVolumeDecorator(AzureDiskVolumeConverter.convert(e))));
        });
        return result;
    }

    private static List<DecoratorBuildItem> createAnnotationDecorators(Project project, String target, String name,
            PlatformConfiguration config,
            Optional<MetricsCapabilityBuildItem> metricsConfiguration,
            List<KubernetesPortBuildItem> ports) {
        List<DecoratorBuildItem> result = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        ScmInfo scm = project.getScmInfo();
        String vcsUrl = scm != null ? scm.getUrl() : null;
        String commitId = scm != null ? scm.getCommit() : null;

        //Dekorate uses its own annotations. Let's replace them with the quarkus ones.
        result.add(new DecoratorBuildItem(target, new RemoveAnnotationDecorator(Annotations.VCS_URL)));
        result.add(new DecoratorBuildItem(target, new RemoveAnnotationDecorator(Annotations.COMMIT_ID)));

        //Add quarkus vcs annotations
        if (commitId != null) {
            result.add(new DecoratorBuildItem(target,
                    new AddAnnotationDecorator(name, new Annotation(QUARKUS_ANNOTATIONS_COMMIT_ID, commitId, new String[0]))));
        }
        if (vcsUrl != null) {
            result.add(new DecoratorBuildItem(target,
                    new AddAnnotationDecorator(name, new Annotation(QUARKUS_ANNOTATIONS_VCS_URL, vcsUrl, new String[0]))));
        }

        if (config.isAddBuildTimestamp()) {
            result.add(new DecoratorBuildItem(target,
                    new AddAnnotationDecorator(name, new Annotation(QUARKUS_ANNOTATIONS_BUILD_TIMESTAMP,
                            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd - HH:mm:ss Z")), new String[0]))));
        }

        metricsConfiguration.ifPresent(m -> {
            String path = m.metricsEndpoint();
            if (!ports.isEmpty() && path != null) {
                result.add(new DecoratorBuildItem(target, new AddAnnotationDecorator(name, Prometheus.SCRAPE, "true")));
                result.add(new DecoratorBuildItem(target, new AddAnnotationDecorator(name, Prometheus.PATH, path)));
                result.add(new DecoratorBuildItem(target,
                        new AddAnnotationDecorator(name, Prometheus.PORT, "" + ports.get(0).getPort())));
            }
        });

        //Add metrics annotations
        return result;
    }

    private static List<DecoratorBuildItem> createProbeDecorators(String name, String target, ProbeConfig livenessProbe,
            ProbeConfig readinessProbe,
            Optional<KubernetesHealthLivenessPathBuildItem> livenessPath,
            Optional<KubernetesHealthReadinessPathBuildItem> readinessPath) {
        List<DecoratorBuildItem> result = new ArrayList<>();
        createLivenessProbe(name, target, livenessProbe, livenessPath).ifPresent(d -> result.add(d));
        createReadinessProbe(name, target, readinessProbe, readinessPath).ifPresent(d -> result.add(d));
        return result;
    }

    private static Optional<DecoratorBuildItem> createLivenessProbe(String name, String target, ProbeConfig livenessProbe,
            Optional<KubernetesHealthLivenessPathBuildItem> livenessPath) {
        if (livenessProbe.hasUserSuppliedAction()) {
            return Optional.of(
                    new DecoratorBuildItem(target, new AddLivenessProbeDecorator(name, ProbeConverter.convert(livenessProbe))));
        } else if (livenessPath.isPresent()) {
            return Optional.of(new DecoratorBuildItem(target, new AddLivenessProbeDecorator(name,
                    ProbeConverter.builder(livenessProbe).withHttpActionPath(livenessPath.get().getPath()).build())));
        }
        return Optional.empty();
    }

    private static Optional<DecoratorBuildItem> createReadinessProbe(String name, String target, ProbeConfig readinessProbe,
            Optional<KubernetesHealthReadinessPathBuildItem> readinessPath) {
        if (readinessProbe.hasUserSuppliedAction()) {
            return Optional.of(new DecoratorBuildItem(target,
                    new AddReadinessProbeDecorator(name, ProbeConverter.convert(readinessProbe))));
        } else if (readinessPath.isPresent()) {
            return Optional.of(new DecoratorBuildItem(target, new AddReadinessProbeDecorator(name,
                    ProbeConverter.builder(readinessProbe).withHttpActionPath(readinessPath.get().getPath()).build())));
        }
        return Optional.empty();
    }

    private static Map<String, Integer> verifyPorts(List<KubernetesPortBuildItem> kubernetesPortBuildItems) {
        final Map<String, Integer> result = new HashMap<>();
        final Set<Integer> usedPorts = new HashSet<>();
        for (KubernetesPortBuildItem entry : kubernetesPortBuildItems) {
            final String name = entry.getName();
            if (result.containsKey(name)) {
                throw new IllegalArgumentException(
                        "All Kubernetes ports must have unique names - " + name + "has been used multiple times");
            }
            final Integer port = entry.getPort();
            if (usedPorts.contains(port)) {
                throw new IllegalArgumentException(
                        "All Kubernetes ports must be unique - " + port + "has been used multiple times");
            }
            result.put(name, port);
            usedPorts.add(port);
        }
        return result;
    }

}
