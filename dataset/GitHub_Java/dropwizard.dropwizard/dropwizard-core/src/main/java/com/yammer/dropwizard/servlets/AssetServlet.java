package com.yammer.dropwizard.servlets;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.io.Resources;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.URIUtil;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AssetServlet extends HttpServlet {
    private static final long serialVersionUID = 6393345594784987908L;

    private static class AssetLoader extends CacheLoader<String, byte[]> {
        private final String base;

        private AssetLoader(String base) {
            this.base = base;
        }

        @Override
        public byte[] load(String key) throws Exception {
            final String path = URIUtil.canonicalPath(key);
            if (path.startsWith(base)) {
                return Resources.toByteArray(Resources.getResource(path.substring(1)));
            } else {
                throw new RuntimeException("nope");
            }
        }
    }
    
    private final transient Cache<String, byte[]> cache;
    private final transient MimeTypes mimeTypes;

    public AssetServlet(String base, int maxCacheSize) {
        this.cache = buildCache(base, maxCacheSize);
        this.mimeTypes = new MimeTypes();
    }

    private static Cache<String, byte[]> buildCache(String base, int maxCacheSize) {
        return CacheBuilder.newBuilder()
                           .maximumSize(maxCacheSize)
                           .build(new AssetLoader(base));
    }

    @Override
    protected void doGet(HttpServletRequest req,
                         HttpServletResponse resp) throws ServletException, IOException {
        try {
            final byte[] resource = cache.getUnchecked(req.getRequestURI());
            resp.setContentType(mimeTypes.getMimeByExtension(req.getRequestURI()).toString());
            final ServletOutputStream output = resp.getOutputStream();
            try {
                output.write(resource);
            } finally {
                output.close();
            }
        } catch (RuntimeException ignored) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
