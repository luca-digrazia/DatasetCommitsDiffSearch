package io.quarkus.rest.runtime;

import static io.quarkus.runtime.TemplateHtmlBuilder.adjustRoot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.ExceptionMapper;

import org.jboss.logging.Logger;

import io.quarkus.rest.runtime.core.request.ServerDrivenNegotiation;
import io.quarkus.rest.runtime.handlers.ClassRoutingHandler;
import io.quarkus.rest.runtime.handlers.QuarkusRestInitialHandler;
import io.quarkus.rest.runtime.handlers.RestHandler;
import io.quarkus.rest.runtime.mapping.RequestMapper;
import io.quarkus.rest.runtime.mapping.RuntimeResource;
import io.quarkus.rest.runtime.util.ServerMediaType;
import io.quarkus.runtime.TemplateHtmlBuilder;
import io.quarkus.runtime.util.ClassPathUtils;
import io.quarkus.vertx.http.runtime.devmode.RouteDescription;

public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    protected static final String META_INF_RESOURCES_SLASH = "META-INF/resources/";
    protected static final String META_INF_RESOURCES = "META-INF/resources";

    private final static Variant JSON_VARIANT = new Variant(MediaType.APPLICATION_JSON_TYPE, (String) null, null);
    private final static Variant HTML_VARIANT = new Variant(MediaType.TEXT_HTML_TYPE, (String) null, null);
    private final static List<Variant> VARIANTS = Arrays.asList(JSON_VARIANT, HTML_VARIANT);
    static List<RequestMapper.RequestPath<QuarkusRestInitialHandler.InitialMatch>> classMappers;

    private volatile static String httpRoot = "";
    private volatile static List<String> servletMappings = Collections.emptyList();
    private volatile static Set<java.nio.file.Path> staticResourceRoots = Collections.emptySet();
    private volatile static List<String> additionalEndpoints = Collections.emptyList();
    private volatile static List<RouteDescription> reactiveRoutes = Collections.emptyList();

    private static final Logger LOG = Logger.getLogger(NotFoundExceptionMapper.class);

    @Context
    private HttpHeaders headers;

    public static void setHttpRoot(String rootPath) {
        httpRoot = rootPath;
    }

    public static final class MethodDescription {
        public String method;
        public String fullPath;
        public String produces;
        public String consumes;

        public MethodDescription(String method, String fullPath, String produces, String consumes) {
            super();
            this.method = method;
            this.fullPath = fullPath;
            this.produces = produces;
            this.consumes = consumes;
        }
    }

    @Override
    public Response toResponse(NotFoundException exception) {
        if ((classMappers == null) || classMappers.isEmpty()) {
            return respond();
        }
        return respond(ResourceDescription.fromClassMappers(classMappers));
    }

    private Response respond() {
        Variant variant = selectVariant(headers);

        if (variant == JSON_VARIANT) {
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).build();
        }

        if (variant == HTML_VARIANT) {
            TemplateHtmlBuilder sb = new TemplateHtmlBuilder("404 - Resource Not Found", "", "No resources discovered");
            return Response.status(Status.NOT_FOUND).entity(sb.toString()).type(MediaType.TEXT_HTML_TYPE).build();
        }

        return Response.status(Status.NOT_FOUND).build();
    }

    private Response respond(List<ResourceDescription> descriptions) {
        Variant variant = selectVariant(headers);

        if (variant == JSON_VARIANT) {
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).build();
        }

        if (variant == HTML_VARIANT) {
            TemplateHtmlBuilder sb = new TemplateHtmlBuilder("404 - Resource Not Found", "", "Resources overview");
            sb.resourcesStart("REST resources");
            for (ResourceDescription resource : descriptions) {
                sb.resourcePath(adjustRoot(httpRoot, resource.basePath));
                for (MethodDescription method : resource.calls) {
                    sb.method(method.method, method.fullPath);
                    if (method.consumes != null) {
                        sb.consumes(method.consumes);
                    }
                    if (method.produces != null) {
                        sb.produces(method.produces);
                    }
                    sb.methodEnd();
                }
                sb.resourceEnd();
            }
            if (descriptions.isEmpty()) {
                sb.noResourcesFound();
            }
            sb.resourcesEnd();

            if (!servletMappings.isEmpty()) {
                sb.resourcesStart("Servlet mappings");
                for (String servletMapping : servletMappings) {
                    sb.servletMapping(adjustRoot(httpRoot, servletMapping));
                }
                sb.resourcesEnd();
            }

            if (!reactiveRoutes.isEmpty()) {
                sb.resourcesStart("Reactive Routes");
                sb.resourceStart();
                for (RouteDescription route : reactiveRoutes) {
                    sb.method(route.getHttpMethod(), route.getPath());
                    sb.listItem(route.getJavaMethod());
                    if (route.getConsumes() != null) {
                        sb.consumes(route.getConsumes());
                    }
                    if (route.getProduces() != null) {
                        sb.produces(route.getProduces());
                    }
                    sb.methodEnd();
                }
                sb.resourceEnd();
                sb.resourcesEnd();
            }

            if (!staticResourceRoots.isEmpty()) {
                List<String> resources = findRealResources();
                if (!resources.isEmpty()) {
                    sb.resourcesStart("Static resources");
                    for (String staticResource : resources) {
                        sb.staticResourcePath(adjustRoot(httpRoot, staticResource));
                    }
                    sb.resourcesEnd();
                }
            }

            if (!additionalEndpoints.isEmpty()) {
                sb.resourcesStart("Additional endpoints");
                for (String additionalEndpoint : additionalEndpoints) {
                    sb.staticResourcePath(adjustRoot(httpRoot, additionalEndpoint));
                }
                sb.resourcesEnd();
            }

            return Response.status(Status.NOT_FOUND).entity(sb.toString()).type(MediaType.TEXT_HTML_TYPE).build();
        }
        return Response.status(Status.NOT_FOUND).build();
    }

    private List<String> findRealResources() {

        //we need to check for web resources in order to get welcome files to work
        //this kinda sucks
        Set<String> knownFiles = new HashSet<>();
        for (java.nio.file.Path resource : staticResourceRoots) {
            if (resource != null && Files.exists(resource)) {
                try (Stream<java.nio.file.Path> fileTreeElements = Files.walk(resource)) {
                    fileTreeElements.forEach(new Consumer<java.nio.file.Path>() {
                        @Override
                        public void accept(java.nio.file.Path path) {
                            // Skip META-INF/resources entry
                            if (resource.equals(path)) {
                                return;
                            }
                            java.nio.file.Path rel = resource.relativize(path);
                            if (!Files.isDirectory(path)) {
                                knownFiles.add(rel.toString());
                            }
                        }
                    });
                } catch (IOException e) {
                    LOG.error("Failed to read static resources", e);
                }
            }
        }
        try {
            ClassPathUtils.consumeAsPaths(META_INF_RESOURCES, p -> {
                collectKnownPaths(p, knownFiles);
            });
        } catch (IOException e) {
            LOG.error("Failed to read static resources", e);
        }

        //limit to 1000 to not have to many files to display
        return knownFiles.stream().filter(this::isHtmlFileName).limit(1000).distinct().sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private void collectKnownPaths(java.nio.file.Path resource, Set<String> knownPaths) {
        try {
            Files.walkFileTree(resource, new SimpleFileVisitor<java.nio.file.Path>() {
                @Override
                public FileVisitResult visitFile(java.nio.file.Path p, BasicFileAttributes attrs)
                        throws IOException {
                    String file = resource.relativize(p).toString();
                    // Windows has a backslash
                    file = file.replace('\\', '/');
                    knownPaths.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isHtmlFileName(String fileName) {
        return fileName.endsWith(".html") || fileName.endsWith(".htm");
    }

    private static Variant selectVariant(HttpHeaders headers) {
        ServerDrivenNegotiation negotiation = new ServerDrivenNegotiation();
        negotiation.setAcceptHeaders(headers.getRequestHeaders().get(HttpHeaders.ACCEPT));
        return negotiation.getBestMatch(VARIANTS);
    }

    public static void servlets(Map<String, List<String>> servletToMapping) {
        NotFoundExceptionMapper.servletMappings = servletToMapping.values().stream()
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
    }

    public static void staticResources(Set<String> knownRoots) {
        NotFoundExceptionMapper.staticResourceRoots = new HashSet<>();
        for (String i : knownRoots) {
            staticResourceRoots.add(Paths.get(i));
        }
    }

    public static void setAdditionalEndpoints(List<String> additionalEndpoints) {
        NotFoundExceptionMapper.additionalEndpoints = additionalEndpoints;
    }

    public static void setReactiveRoutes(List<RouteDescription> reactiveRoutes) {
        NotFoundExceptionMapper.reactiveRoutes = reactiveRoutes;
    }

    public static final class ResourceDescription {
        public final String basePath;
        public final List<MethodDescription> calls = new ArrayList<>();

        public ResourceDescription(String basePath) {
            this.basePath = basePath;
        }

        private static String mostPreferredOrNull(List<MediaType> mediaTypes) {
            if (mediaTypes == null || mediaTypes.isEmpty()) {
                return null;
            } else {
                return mediaTypes.get(0).toString();
            }
        }

        public static List<ResourceDescription> fromClassMappers(
                List<RequestMapper.RequestPath<QuarkusRestInitialHandler.InitialMatch>> classMappers) {
            Map<String, ResourceDescription> descriptions = new HashMap<>();

            for (RequestMapper.RequestPath<QuarkusRestInitialHandler.InitialMatch> classMapper : classMappers) {
                String template = classMapper.template.template;
                QuarkusRestInitialHandler.InitialMatch initialMatch = classMapper.value;
                if ((initialMatch.handlers == null) || initialMatch.handlers.length == 0) {
                    continue;
                }
                RestHandler firstHandler = initialMatch.handlers[0];
                if (!(firstHandler instanceof ClassRoutingHandler)) {
                    continue;
                }
                ClassRoutingHandler classRoutingHandler = (ClassRoutingHandler) firstHandler;

                Map<String, RequestMapper<RuntimeResource>> classRoutingHandlerMappers = classRoutingHandler.getMappers();
                for (Map.Entry<String, RequestMapper<RuntimeResource>> entry : classRoutingHandlerMappers.entrySet()) {
                    String basePath = template;
                    String httpMethod = entry.getKey();
                    if (httpMethod == null) {
                        continue; // TODO: fix as to use all methods
                    }

                    RequestMapper<RuntimeResource> requestMapper = entry.getValue();
                    List<RequestMapper.RequestPath<RuntimeResource>> methodTemplates = requestMapper.getTemplates();
                    if (methodTemplates.isEmpty()) {
                        continue;
                    }
                    ResourceDescription description = descriptions.get(basePath);
                    if (description == null) {
                        description = new ResourceDescription(basePath);
                        descriptions.put(basePath, description);
                    }
                    for (RequestMapper.RequestPath<RuntimeResource> methodTemplate : methodTemplates) {
                        String subPath = methodTemplate.template.template;
                        if (subPath.startsWith("/")) {
                            subPath = subPath.substring(1);
                        }
                        String fullPath = basePath;
                        if (!subPath.isEmpty()) {
                            if (basePath.endsWith("/")) {
                                fullPath += subPath;
                            } else {
                                fullPath = basePath + "/" + subPath;
                            }
                        }
                        RuntimeResource runtimeResource = methodTemplate.value;
                        ServerMediaType serverMediaType = runtimeResource.getProduces();
                        List<MediaType> produces = Collections.emptyList();
                        if (serverMediaType != null) {
                            if ((serverMediaType.getSortedOriginalMediaTypes() != null)
                                    && serverMediaType.getSortedOriginalMediaTypes().length >= 1) {
                                produces = Arrays.asList(serverMediaType.getSortedOriginalMediaTypes());
                            }
                        }
                        description.calls.add(new MethodDescription(httpMethod, fullPath, mostPreferredOrNull(produces),
                                mostPreferredOrNull(runtimeResource.getConsumes())));
                    }
                }
            }
            return new LinkedList<>(descriptions.values());
        }
    }
}
