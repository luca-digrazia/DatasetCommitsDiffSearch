package io.quarkus.datasource.runtime;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class DevServicesBuildTimeConfig {

    /**
     * If DevServices has been explicitly enabled or disabled. DevServices is generally enabled
     * by default, unless there is an existing configuration present.
     *
     * When DevServices is enabled Quarkus will attempt to automatically configure and start
     * a database when running in Dev or Test mode.
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public Optional<Boolean> enabled = Optional.empty();

    /**
     * The container image name to use, for container based DevServices providers.
     *
     * If the provider is not container based (e.g. a H2 Database) then this has no effect.
     */
    @ConfigItem
    public Optional<String> imageName;

    /**
     * Generic properties that are passed into the DevServices database provider. These properties are provider
     * specific.
     */
    @ConfigItem
    public Map<String, String> properties;

}
