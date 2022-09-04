package io.quarkus.bootstrap.runner;

import java.net.URL;
import java.security.ProtectionDomain;

public interface ClassLoadingResource {

    byte[] getResourceData(String resource);

    URL getResourceURL(String resource);

    ManifestInfo getManifestInfo();

    ProtectionDomain getProtectionDomain(ClassLoader runnerClassLoader);

    void close();

    /**
     * This is an optional hint to release internal caches, if possible.
     * It is different than {@link #close()} as it's possible that
     * this ClassLoadingResource will still be used after this,
     * so it needs to be able to rebuild any lost state in case of need.
     * However one can assume that when this is invoked, there is
     * some reasonable expectation that this resource is no longer going
     * to be necessary.
     */
    default void resetInternalCaches() {
        //no-op
    }
}
