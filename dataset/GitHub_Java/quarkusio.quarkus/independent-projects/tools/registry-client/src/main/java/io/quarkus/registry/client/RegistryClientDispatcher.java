package io.quarkus.registry.client;

import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.RegistryResolutionException;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.PlatformCatalog;
import io.quarkus.registry.config.RegistryConfig;
import java.util.Objects;

public class RegistryClientDispatcher implements RegistryClient {

    private final RegistryPlatformsResolver platforms;
    private final RegistryPlatformExtensionsResolver platformExtensions;
    private final RegistryNonPlatformExtensionsResolver nonPlatformExtensions;
    protected RegistryConfig config;

    public RegistryClientDispatcher(RegistryConfig config, RegistryPlatformsResolver platforms,
            RegistryPlatformExtensionsResolver platformExtensions,
            RegistryNonPlatformExtensionsResolver nonPlatformExtensions) {
        this.config = config;
        this.platforms = platforms;
        this.platformExtensions = Objects.requireNonNull(platformExtensions);
        this.nonPlatformExtensions = nonPlatformExtensions;
    }

    @Override
    public PlatformCatalog resolvePlatforms(String quarkusVersion) throws RegistryResolutionException {
        return platforms == null ? null : platforms.resolvePlatforms(quarkusVersion);
    }

    @Override
    public ExtensionCatalog resolvePlatformExtensions(ArtifactCoords platformCoords)
            throws RegistryResolutionException {
        return platformExtensions.resolvePlatformExtensions(platformCoords);
    }

    @Override
    public ExtensionCatalog resolveNonPlatformExtensions(String quarkusVersion) throws RegistryResolutionException {
        return nonPlatformExtensions == null ? null
                : nonPlatformExtensions.resolveNonPlatformExtensions(quarkusVersion);
    }

    @Override
    public RegistryConfig resolveRegistryConfig() throws RegistryResolutionException {
        return config;
    }
}
