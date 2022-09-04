package com.yammer.dropwizard.config.provider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * An implementation of {@link ConfigurationSourceProvider} that reads the configuration from a
 * {@link URL}.
 */
public class UrlConfigurationSourceProvider implements ConfigurationSourceProvider {
    @Override
    public InputStream create(String path) throws IOException {
        return new URL(path).openStream();
    }
}
