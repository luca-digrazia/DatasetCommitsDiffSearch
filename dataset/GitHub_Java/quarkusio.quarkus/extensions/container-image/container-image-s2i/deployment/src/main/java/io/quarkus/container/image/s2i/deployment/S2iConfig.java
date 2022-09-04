package io.quarkus.container.image.s2i.deployment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public class S2iConfig {

    public static final String DEFAULT_BASE_JVM_IMAGE = "fabric8/s2i-java:2.3";
    public static final String DEFAULT_BASE_NATIVE_IMAGE = "quay.io/quarkus/ubi-quarkus-native-binary-s2i:19.3.0";
    public static final String DEFAULT_NATIVE_TARGET_FILENAME = "application";

    /**
     * The base image to be used when a container image is being produced for the jar build
     */
    @ConfigItem(defaultValue = DEFAULT_BASE_JVM_IMAGE)
    public String baseJvmImage;

    /**
     * The base image to be used when a container image is being produced for the native binary build
     */
    @ConfigItem(defaultValue = DEFAULT_BASE_NATIVE_IMAGE)
    public String baseNativeImage;

    /**
     * Additional JVM arguments to pass to the JVM when starting the application
     */
    @ConfigItem(defaultValue = "-Dquarkus.http.host=0.0.0.0,-Djava.util.logging.manager=org.jboss.logmanager.LogManager")
    public List<String> jvmArguments;

    /**
     * Additional arguments to pass when starting the native application
     */
    @ConfigItem(defaultValue = "-Dquarkus.http.host=0.0.0.0")
    public List<String> nativeArguments;

    /**
     * The directory where the jar is added during the assemble phase.
     * This is dependant on the s2i image and should be supplied if a non default image is used.
     */
    @ConfigItem(defaultValue = "/deployments/")
    public String jarDirectory;

    /**
     * The resulting filename of the jar in the s2i image.
     * This option may be used if the selected s2i image uses a fixed name for the jar.
     */
    @ConfigItem
    public Optional<String> jarFileName;

    /**
     * The directory where the native binary is added during the assemble phase.
     * This is dependant on the s2i image and should be supplied if a non-default image is used.
     */
    @ConfigItem(defaultValue = "/home/quarkus/")
    public String nativeBinaryDirectory;

    /**
     * The resulting filename of the native binary in the s2i image.
     * This option may be used if the selected s2i image uses a fixed name for the native binary.
     */
    @ConfigItem
    public Optional<String> nativeBinaryFileName;

    /**
     * The build timeout.
     */
    @ConfigItem(defaultValue = "PT5M")
    Duration buildTimeout;

    /**
     * Check if baseJvmImage is the default
     * 
     * @returns true if baseJvmImage is the default
     */
    public boolean hasDefaultBaseJvmImage() {
        return baseJvmImage.equals(DEFAULT_BASE_JVM_IMAGE);
    }

}
