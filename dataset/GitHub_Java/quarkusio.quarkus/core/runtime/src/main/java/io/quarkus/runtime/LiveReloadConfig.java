package io.quarkus.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class LiveReloadConfig {

    /**
     * The names of additional resource files to watch for changes, triggering a reload on change. Directories are <em>not</em>
     * supported.
     */
    @ConfigItem
    public Optional<List<String>> watchedResources;

    /**
     * Password used to use to connect to the remote dev-mode application
     */
    @ConfigItem
    public Optional<String> password;

    /**
     * URL used to use to connect to the remote dev-mode application
     */
    @ConfigItem
    public Optional<String> url;
}
