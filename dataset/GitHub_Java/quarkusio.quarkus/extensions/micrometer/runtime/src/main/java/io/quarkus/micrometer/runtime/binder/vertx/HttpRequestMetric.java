package io.quarkus.micrometer.runtime.binder.vertx;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import io.quarkus.micrometer.runtime.binder.RequestMetricInfo;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.ext.web.RoutingContext;

public class HttpRequestMetric extends RequestMetricInfo {
    public static final Pattern VERTX_ROUTE_PARAM = Pattern.compile("^:(.*)$");

    /** Cache of vert.x resolved paths: /item/:id --> /item/{id} */
    final static ConcurrentHashMap<String, String> vertxWebToUriTemplate = new ConcurrentHashMap<>();

    protected HttpServerRequestInternal request;
    protected String initialPath;
    protected String templatePath;
    protected String currentRoutePath;

    public HttpRequestMetric(String uri) {
        this.initialPath = uri;
    }

    public HttpRequestMetric(HttpRequest request) {
        this.request = (HttpServerRequestInternal) request;
        this.initialPath = this.request.path();
    }

    public String getNormalizedUriPath(Map<Pattern, String> matchPatterns, List<Pattern> ignorePatterns) {
        return super.getNormalizedUriPath(matchPatterns, ignorePatterns, initialPath);
    }

    public String applyTemplateMatching(String path) {
        // JAX-RS or Servlet container filter
        if (templatePath != null) {
            return normalizePath(templatePath);
        }

        // vertx-web or reactive route: is it templated?
        if (currentRoutePath != null && currentRoutePath.contains(":")) {
            // Convert /item/:id to /item/{id} and save it for next time
            return vertxWebToUriTemplate.computeIfAbsent(currentRoutePath, k -> {
                String segments[] = k.split("/");
                for (int i = 0; i < segments.length; i++) {
                    segments[i] = VERTX_ROUTE_PARAM.matcher(segments[i]).replaceAll("{$1}");
                }
                return normalizePath(String.join("/", segments));
            });
        }

        return path;
    }

    public HttpServerRequestInternal request() {
        return request;
    }

    public void setTemplatePath(String path) {
        if (this.templatePath == null) {
            this.templatePath = path;
        }
    }

    public void appendCurrentRoutePath(String path) {
        if (path != null && !path.isEmpty()) {
            this.currentRoutePath = path;
        }
    }

    public static HttpRequestMetric getRequestMetric(RoutingContext context) {
        HttpServerRequestInternal internalRequest = (HttpServerRequestInternal) context.request();
        return (HttpRequestMetric) internalRequest.metric();
    }

    @Override
    public String toString() {
        return "HttpRequestMetric [initialPath=" + initialPath + ", currentRoutePath=" + currentRoutePath
                + ", templatePath=" + templatePath + ", request=" + request + "]";
    }
}