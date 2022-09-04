package io.quarkus.kubernetes.client.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "kubernetes-config", phase = ConfigPhase.BOOTSTRAP)
public class KubernetesConfigSourceConfig {

    /**
     * If set to true, the application will attempt to look up the configuration from the API server
     */
    @ConfigItem(defaultValue = "false")
    public boolean enabled;

    /**
     * If set to true, the application will not start if any of the configured config sources cannot be located
     */
    @ConfigItem(defaultValue = "true")
    public boolean failOnMissingConfig;

    /**
     * ConfigMaps to look for in the namespace that the Kubernetes Client has been configured for
     */
    @ConfigItem
    public Optional<List<String>> configMaps;

    /**
     * Secrets to look for in the namespace that the Kubernetes Client has been configured for
     */
    @ConfigItem
    public Optional<List<String>> secrets;

    /**
     * Namespace to look for config maps and secrets. If this is not specified, then the namespace configured in the kubectl config context
     * is used. If the value is specified and the namespace doesn't exist, the application will fail to start.
     */
    @ConfigItem
    public Optional<String> namespace;

}
