/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.web.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.graylog2.web.IndexHtmlGenerator;
import org.graylog2.web.PluginAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.MoreObjects.firstNonNull;

@Path("{filename: .*}")
public class WebInterfaceAssetsResource {
    private static final Logger log = LoggerFactory.getLogger(WebInterfaceAssetsResource.class);
    private final IndexHtmlGenerator indexHtmlGenerator;
    private final LoadingCache<URI, FileSystem> fileSystemCache;

    @Inject
    public WebInterfaceAssetsResource(IndexHtmlGenerator indexHtmlGenerator) {
        this.indexHtmlGenerator = indexHtmlGenerator;
        fileSystemCache = CacheBuilder.newBuilder()
                .maximumSize(1024)
                .build(new CacheLoader<URI, FileSystem>() {
            @Override
            public FileSystem load(@Nonnull URI key) throws Exception {
                try {
                    return FileSystems.getFileSystem(key);
                } catch (FileSystemNotFoundException e) {
                    return FileSystems.newFileSystem(key, Collections.emptyMap());
                }
            }
        });
    }

    @GET
    public Response get(@Context Request request, @Context HttpHeaders httpheaders, @PathParam("filename") String filename) {
        if (filename == null || filename.isEmpty() || filename.equals("/") || filename.equals("index.html")) {
            return getDefaultResponse();
        }
        try {
            final URL resourceUrl = getResourceUri(filename);
            final Date lastModified;
            final InputStream stream;
            final HashCode hashCode;

            switch (resourceUrl.getProtocol()) {
                case "file": {
                    String fileName = resourceUrl.getFile();
                    final File file = new File(fileName);
                    lastModified = new Date(file.lastModified());
                    stream = new FileInputStream(file);
                    hashCode = Files.hash(file, Hashing.sha256());
                    break;
                }
                case "jar": {
                    final URI uri = resourceUrl.toURI();
                    final FileSystem fileSystem = fileSystemCache.getUnchecked(uri);
                    final java.nio.file.Path path = fileSystem.getPath(pluginPrefixFilename(filename));
                    final FileTime lastModifiedTime = java.nio.file.Files.getLastModifiedTime(path);
                    lastModified = new Date(lastModifiedTime.toMillis());
                    stream = resourceUrl.openStream();
                    hashCode = Resources.asByteSource(resourceUrl).hash(Hashing.sha256());
                    break;
                }
                default:
                    throw new IllegalArgumentException("Not a jar or file");
            }

            final EntityTag entityTag = new EntityTag(hashCode.toString());

            final Response.ResponseBuilder response = request.evaluatePreconditions(lastModified, entityTag);
            if (response != null) {
                return response.build();
            }

            final String contentType = firstNonNull(URLConnection.guessContentTypeFromName(filename), MediaType.APPLICATION_OCTET_STREAM);
            final CacheControl cacheControl = new CacheControl();
            cacheControl.setMaxAge((int)TimeUnit.DAYS.toSeconds(365));
            cacheControl.setNoCache(false);
            cacheControl.setPrivate(false);
            return Response
                .ok(stream)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .tag(entityTag)
                .cacheControl(cacheControl)
                .lastModified(lastModified)
                .build();
        } catch (IOException | URISyntaxException e) {
            return getDefaultResponse();
        }
    }

    private URL getResourceUri(String filename) throws URISyntaxException, FileNotFoundException {
        final URL resourceUrl =  this.getClass().getResource(pluginPrefixFilename(filename));
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource file " + filename + " not found.");
        }
        return resourceUrl;
    }

    @Nonnull
    private String pluginPrefixFilename(@PathParam("filename") String filename) {
        return "/" + PluginAssets.pathPrefix + "/" + filename;
    }

    private Response getDefaultResponse() {
        return Response
                .ok(this.indexHtmlGenerator.get())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                .header("X-UA-Compatible", "IE=edge")
                .build();
    }
}
