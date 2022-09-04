
package io.quarkus.kubernetes.deployment;

import static io.quarkus.container.image.deployment.util.ImageUtil.hasRegistry;
import static io.quarkus.kubernetes.deployment.Constants.DEPLOYMENT;
import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;
import static io.quarkus.kubernetes.deployment.Constants.OPENSHIFT;
import static io.quarkus.kubernetes.deployment.Constants.S2I;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLHandshakeException;

import org.jboss.logging.Logger;

import io.dekorate.deps.kubernetes.api.model.HasMetadata;
import io.dekorate.deps.kubernetes.api.model.KubernetesList;
import io.dekorate.deps.kubernetes.client.KubernetesClient;
import io.dekorate.deps.kubernetes.client.KubernetesClientException;
import io.dekorate.utils.Clients;
import io.dekorate.utils.Serialization;
import io.quarkus.container.spi.ContainerImageResultBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.DeploymentResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.client.spi.KubernetesClientBuildItem;
import io.quarkus.kubernetes.spi.KubernetesDeploymentTargetBuildItem;

public class KubernetesDeployer {

    private static final Logger log = Logger.getLogger(KubernetesDeployer.class);

    @BuildStep(onlyIf = { IsNormal.class, KubernetesDeploy.class })
    public void deploy(KubernetesClientBuildItem kubernetesClient,
            Optional<ContainerImageResultBuildItem> containerImage,
            List<KubernetesDeploymentTargetBuildItem> kubernetesDeploymentTargetBuildItems,
            OutputTargetBuildItem outputTarget,
            BuildProducer<DeploymentResultBuildItem> deploymentResult) {

        if (!containerImage.isPresent()) {
            throw new RuntimeException(
                    "A Kubernetes deployment was requested but no extension was found to build a container image. Consider adding one of following extensions: \"quarkus-container-image-jib\", \"quarkus-container-image-docker\" or \"quarkus-container-image-s2i\".");
        }

        ContainerImageResultBuildItem image = containerImage.get();
        if (!hasRegistry(image.getImageId()) && !S2I.equals(image.getProvider())) {
            log.warn(
                    "A Kubernetes deployment was requested, but the container image to be built will not be pushed to any registry"
                            + " because \"quarkus.container-image.registry\" has not been set. The Kubernetes deployment will only work properly"
                            + " if the cluster is using the local Docker daemon.");
        }

        //Get any build item but if the build was s2i, use openshift
        KubernetesDeploymentTargetBuildItem deploymentTarget = kubernetesDeploymentTargetBuildItems
                .stream()
                .filter(d -> !"s2i".equals(containerImage.get().getProvider()) || OPENSHIFT.equals(d.getName()))
                .findFirst()
                .orElse(new KubernetesDeploymentTargetBuildItem(KUBERNETES, DEPLOYMENT));

        final KubernetesClient client = Clients.fromConfig(kubernetesClient.getClient().getConfiguration());
        deploymentResult.produce(deploy(deploymentTarget, client, outputTarget.getOutputDirectory()));
    }

    private DeploymentResultBuildItem deploy(KubernetesDeploymentTargetBuildItem deploymentTarget,
            KubernetesClient client, Path outputDir) {
        String namespace = Optional.ofNullable(client.getNamespace()).orElse("default");
        log.info("Deploying to " + deploymentTarget.getName().toLowerCase() + " server: " + client.getMasterUrl()
                + " in namespace:" + namespace + ".");
        File manifest = outputDir.resolve(KUBERNETES).resolve(deploymentTarget.getName().toLowerCase() + ".yml")
                .toFile();

        try (FileInputStream fis = new FileInputStream(manifest)) {
            KubernetesList list = Serialization.unmarshalAsList(fis);
            list.getItems().forEach(i -> {
                log.info("Applying: " + i.getKind() + " " + i.getMetadata().getName() + ".");
                client.resource(i).inNamespace(namespace).createOrReplace();
                log.info("Applied: " + i.getKind() + " " + i.getMetadata().getName() + ".");
            });

            HasMetadata m = list.getItems().stream().filter(r -> r.getKind().equals(deploymentTarget.getKind()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "No " + deploymentTarget.getKind() + " found under: " + manifest.getAbsolutePath()));
            return new DeploymentResultBuildItem(m.getMetadata().getName(), m.getMetadata().getLabels());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Can't find generated kubernetes manifest: " + manifest.getAbsolutePath());
        } catch (KubernetesClientException e) {
            if (e.getCause() instanceof SSLHandshakeException) {
                String message = "The application could not be deployed to the cluster because the Kubernetes API Server certificates are not trusted. The certificates can be configured using the relevant configuration propertiers under the 'quarkus.kubernetes-client' config root, or \"quarkus.kubernetes-client.trust-certs=true\" can be set to explicitly trust the certificates (not recommended)";
                log.error(message);
                throw new RuntimeException(e.getCause());
            }
            log.error("Unable to deploy the application to the Kubernetes cluster: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Error closing file: " + manifest.getAbsolutePath());
        }

    }
}
