package io.quarkus.devtools.project.extensions;

import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.dependencies.Extension;
import io.quarkus.devtools.project.BuildTool;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

/**
 * This interface defines a high level way of managing (read/write) extensions in any QuarkusProject
 */
public interface ExtensionManager {

    /**
     * @return the {@link BuildTool} of this extension manager
     */
    BuildTool getBuildTool();

    /**
     * Read the build file(s) to get the list of installed extensions in this Quarkus project.
     *
     * @return The list of {@link AppArtifactCoords} installed in the project build file(s).
     * @throws IOException if a problem occurs while reading the project build file(s)
     */
    Collection<AppArtifactCoords> getInstalled() throws IOException;

    /**
     * Read build file(s) to check if an extension is installed in this Quarkus project.
     *
     * @param key the {@link AppArtifactKey} of the extension to check
     * @return true if it's installed
     * @throws IOException if a problem occurs while reading the project build file(s)
     */
    default boolean isInstalled(AppArtifactKey key) throws IOException {
        return getInstalled().stream().anyMatch(i -> Objects.equals(i.getKey(), key));
    }

    /**
     * Check that the Quarkus Platform bom is defined in the project.
     * The Quarkus Platform Bom role is to define version to use for Quarkus extensions.
     *
     * @return true if it's defined, false else
     * @throws IOException if a problem occurs while reading the project build file(s)
     */
    boolean hasQuarkusPlatformBom() throws IOException;

    /**
     * This is going to install/add all the specified extensions to the project build file(s).
     *
     * <pre>
     *   - If the project Quarkus platform bom is not defined, an {@link IllegalStateException} will be thrown
     *   - Extensions which are already installed will ALWAYS be skipped whatever the specified version
     *   - The provided version will be used wasn't already installed
     * </pre>
     *
     * @param coords the list of {@link AppArtifactCoords} for the extensions to install
     * @return the {@link InstallResult}
     * @throws IOException if a problem occurs while reading/writing the project build file(s)
     */
    InstallResult install(Collection<AppArtifactCoords> coords) throws IOException;

    /**
     * This is going to uninstall/remove all the specified extensions from the project build file(s).
     *
     * This is ignoring the {@link Extension} version
     *
     * @param keys the set of {@link AppArtifactKey} for the extensions to uninstall
     * @return the {@link InstallResult}
     * @throws IOException if a problem occurs while reading/writing the project build file(s)
     */
    UninstallResult uninstall(Collection<AppArtifactKey> keys) throws IOException;

    class InstallResult {
        private final int installed;

        public InstallResult(int installed) {
            this.installed = installed;
        }

        public int getInstalled() {
            return installed;
        }

        public boolean isSourceUpdated() {
            return installed > 0;
        }
    }

    class UninstallResult {
        private final int uninstalled;

        public UninstallResult(int uninstalled) {
            this.uninstalled = uninstalled;
        }

        public int getUninstalled() {
            return uninstalled;
        }

        public boolean isSourceUpdated() {
            return uninstalled > 0;
        }

    }

}
