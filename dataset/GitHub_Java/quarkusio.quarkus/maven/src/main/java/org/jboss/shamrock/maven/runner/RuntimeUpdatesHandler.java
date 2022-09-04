package org.jboss.shamrock.maven.runner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fakereplace.core.Fakereplace;
import org.fakereplace.replacement.AddedClass;
import org.jboss.shamrock.undertow.runtime.UndertowDeploymentTemplate;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class RuntimeUpdatesHandler implements HttpHandler {

    private static final long TWO_SECONDS = 2000;

    private volatile HttpHandler next;
    private final Path classesDir;
    private final Path sourcesDir;
    private volatile long nextUpdate;
    private volatile long lastChange = System.currentTimeMillis();
    private final ClassLoaderCompiler compiler;

    static final UpdateHandler FAKEREPLACE_HANDLER;

    static {
        UpdateHandler fr;
        try {
            Class.forName("org.fakereplace.core.Fakereplace");
            fr = new FakereplaceHandler();
        } catch (Exception e) {
            fr = null;
        }
        FAKEREPLACE_HANDLER = fr;
    }

    public RuntimeUpdatesHandler(HttpHandler next, Path classesDir, Path sourcesDir, ClassLoaderCompiler compiler) {
        this.next = next;
        this.classesDir = classesDir;
        this.sourcesDir = sourcesDir;
        this.compiler = compiler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
        }
        if (nextUpdate > System.currentTimeMillis()) {
            next.handleRequest(exchange);
            return;
        }
        synchronized (this) {
            if (nextUpdate < System.currentTimeMillis()) {
                try {
                    if (doScan()) {
                        //TODO: this should be handled better
                        next = UndertowDeploymentTemplate.ROOT_HANDLER;
                        UndertowDeploymentTemplate.ROOT_HANDLER.handleRequest(exchange);
                        return;
                    }
                    //we update at most once every 2s
                    nextUpdate = System.currentTimeMillis() + TWO_SECONDS;

                } catch (Throwable e) {
                    displayErrorPage(exchange, e);
                    return;
                }
            }
        }
        next.handleRequest(exchange);
    }

    private boolean doScan() throws IOException {
        final Set<File> changedSourceFiles;
        final long start = System.currentTimeMillis();
        if (sourcesDir != null) {
            try (final Stream<Path> sourcesStream = Files.walk(sourcesDir)) {
                changedSourceFiles = sourcesStream
                      .parallel()
                      .filter(p -> p.toString().endsWith(".java"))
                      .filter(p -> wasRecentlyModified(p))
                      .map(Path::toFile)
                      //Needing a concurrent Set, not many standard options:
                      .collect(Collectors.toCollection(ConcurrentSkipListSet::new));
            }
        }
        else {
            changedSourceFiles = Collections.EMPTY_SET;
        }
        if (!changedSourceFiles.isEmpty()) {
            compiler.compile(changedSourceFiles);
        }
        final ConcurrentMap<String, byte[]> changedClasses;
        try (final Stream<Path> classesStream = Files.walk(classesDir)) {
            changedClasses = classesStream
                  .parallel()
                  .filter(p -> p.toString().endsWith(".class"))
                  .filter(p -> wasRecentlyModified(p))
                  .collect(Collectors.toConcurrentMap(
                        p -> pathToClassName(p),
                        p -> readFileContentNoIOExceptions(p))
                  );
        }
        if (changedClasses.isEmpty()) {
            return false;
        }

        lastChange = System.currentTimeMillis();
        if (FAKEREPLACE_HANDLER == null) {
            RunMojoMain.restartApp(false);
        } else {
            FAKEREPLACE_HANDLER.handle(changedClasses);
            RunMojoMain.restartApp(true);
        }
        System.out.println("Hot replace total time: " + (System.currentTimeMillis() - start) + "ms");

        return true;
    }

    private boolean wasRecentlyModified(final Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis() > lastChange;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String pathToClassName(final Path path) {
        String pathName = classesDir.relativize(path).toString();
        String className = pathName.substring(0, pathName.length() - 6).replace("/", ".");
        return className;
    }

    private byte[] readFileContentNoIOExceptions(final Path path) {
        try {
            return readFileContent(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readFileContent(final Path path) throws IOException {
        final File file = path.toFile();
        final long fileLength = file.length();
        if (fileLength>Integer.MAX_VALUE) {
            throw new RuntimeException("Can't process class files larger than Integer.MAX_VALUE bytes");
        }
        try (FileInputStream stream = new FileInputStream(file)) {
            //Might be large but we need a single byte[] at the end of things, might as well allocate it in one shot:
            ByteArrayOutputStream out = new ByteArrayOutputStream((int)fileLength);
            byte[] buf = new byte[1024];
            int r;
            try (FileInputStream in = new FileInputStream(path.toFile())) {
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                }
                return out.toByteArray();
            }
        }
    }

    public static void displayErrorPage(HttpServerExchange exchange, final Throwable exception) throws IOException {
        StringBuilder sb = new StringBuilder();
        //todo: make this good
        sb.append("<html><head><title>ERROR</title>");
        sb.append("</head><body><div class=\"header\"><div class=\"error-div\"></div><div class=\"error-text-div\">Hot Class Change Error</div></div>");
        writeLabel(sb, "Stack Trace", "");

        sb.append("<pre>");
        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        sb.append(escapeBodyText(stringWriter.toString()));
        sb.append("</pre></body></html>");
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8;");
        exchange.getResponseSender().send(sb.toString());
    }

    private static void writeLabel(StringBuilder sb, String label, String value) {
        sb.append("<div class=\"label\">");
        sb.append(escapeBodyText(label));
        sb.append(":</div><div class=\"value\">");
        sb.append(escapeBodyText(value));
        sb.append("</div><br/>");
    }

    public static String escapeBodyText(final String bodyText) {
        if (bodyText == null) {
            return "null";
        }
        return bodyText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    interface UpdateHandler {

        void handle(Map<String, byte[]> changed);

    }

    private static class FakereplaceHandler implements UpdateHandler {

        @Override
        public void handle(Map<String, byte[]> changed) {
            ClassDefinition[] classes = new ClassDefinition[changed.size()];
            int c = 0;
            for (Map.Entry<String, byte[]> e : changed.entrySet()) {
                ClassDefinition cd = null;
                try {
                    cd = new ClassDefinition(Class.forName(e.getKey(), false, RunMojoMain.getCurrentAppClassLoader()), e.getValue());
                } catch (ClassNotFoundException e1) {
                    //TODO: added classes
                    throw new RuntimeException(e1);
                }
                classes[c++] = cd;

            }
            Fakereplace.redefine(classes, new AddedClass[0], true);
        }
    }

}
