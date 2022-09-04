package io.quarkus.deployment.builditem;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class ArchiveRootBuildItem extends SimpleBuildItem {

    private final Path archiveLocation;
    private final Path archiveRoot;

    public ArchiveRootBuildItem(Path appClassesDir) {
        this(appClassesDir, appClassesDir);
    }

    public ArchiveRootBuildItem(Path archiveLocation, Path archiveRoot) {
        this.archiveLocation = archiveLocation;
        if (!Files.isDirectory(archiveRoot)) {
            throw new IllegalArgumentException(archiveRoot + " does not point to the application classes directory");
        }
        this.archiveRoot = archiveRoot;
    }

    /**
     * Deprecated in favor of getArhiveLocation()
     */
    @Deprecated
    public Path getPath() {
        return getArchiveLocation();
    }

    /**
     * If this archive is a jar file it will return the path to the jar file on the file system,
     * otherwise it will return the directory that this corresponds to.
     */
    public Path getArchiveLocation() {
        return archiveLocation;
    }

    /**
     *
     * Returns a path representing the archive root. Note that if this is a jar archive this is not the path to the
     * jar, but rather a path to the root of the mounted {@link com.sun.nio.zipfs.ZipFileSystem}
     *
     * @return The archive root.
     */
    public Path getArchiveRoot() {
        return archiveRoot;
    }
}
