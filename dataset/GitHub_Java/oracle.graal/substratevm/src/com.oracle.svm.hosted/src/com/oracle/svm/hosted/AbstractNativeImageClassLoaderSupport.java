/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

public abstract class AbstractNativeImageClassLoaderSupport {

    /*
     * This cannot be a HostedOption because all Subclasses of OptionDescriptors from inside builtin
     * modules need to be initialized prior to option parsing so that they can be found.
     */
    public static final String PROPERTY_IMAGEINCLUDEBUILTINMODULES = "substratevm.ImageIncludeBuiltinModules";

    final List<Path> imagecp;
    private final List<Path> buildcp;
    private final NativeImageSystemClassLoader nativeImageSystemClassLoader;

    protected final URLClassLoader classPathClassLoader;
    protected final ClassLoader defaultSystemClassLoader;

    private static NativeImageSystemClassLoader nativeImageSystemClassLoader() {
        if (!(ClassLoader.getSystemClassLoader() instanceof NativeImageSystemClassLoader)) {
            String badCustomClassLoaderError = "SystemClassLoader is the default system class loader. This might create problems when using reflection " +
                            "during class initialization at build-time. " +
                            "To fix this error add -Djava.system.class.loader=" + NativeImageSystemClassLoader.class.getCanonicalName();
            UserError.abort(badCustomClassLoaderError);
        }

        return (NativeImageSystemClassLoader) ClassLoader.getSystemClassLoader();
    }

    protected AbstractNativeImageClassLoaderSupport(String[] classpath) {

        /*
         * Make system class loader delegate to NativeImageClassLoader, enabling resolution of
         * classes and resources during image build-time present in the image classpath.
         */
        nativeImageSystemClassLoader = nativeImageSystemClassLoader();
        nativeImageSystemClassLoader.setDelegate(this);

        defaultSystemClassLoader = nativeImageSystemClassLoader.getDefaultSystemClassLoader();
        classPathClassLoader = new URLClassLoader(Util.verifyClassPathAndConvertToURLs(classpath), defaultSystemClassLoader);

        imagecp = Collections.unmodifiableList(Arrays.stream(classPathClassLoader.getURLs()).map(Util::urlToPath).collect(Collectors.toList()));
        buildcp = Collections.unmodifiableList(Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(Paths::get).collect(Collectors.toList()));
    }

    public List<Path> classpath() {
        return Stream.concat(buildcp.stream(), imagecp.stream()).collect(Collectors.toList());
    }

    public Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException {
        if (module != null) {
            throw new ClassNotFoundException(className,
                            new UnsupportedOperationException("NativeImageClassLoader for Java 8 does not support modules"));
        }
        return Class.forName(className, false, classPathClassLoader);
    }

    public ClassLoader getClassLoader() {
        return classPathClassLoader;
    }

    boolean isNativeImageClassLoader(ClassLoader c) {
        if (c == nativeImageSystemClassLoader) {
            return true;
        } else if (c == classPathClassLoader) {
            return true;
        }
        return false;
    }

    public abstract List<Path> modulepath();

    public abstract Optional<Object> findModule(String moduleName);

    public abstract void initAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader);

    protected static class Util {

        static URL[] verifyClassPathAndConvertToURLs(String[] classpath) {
            Stream<Path> pathStream = new LinkedHashSet<>(Arrays.asList(classpath)).stream().flatMap(Util::toClassPathEntries);
            return pathStream.map(v -> {
                try {
                    return v.toAbsolutePath().toUri().toURL();
                } catch (MalformedURLException e) {
                    throw UserError.abort("Invalid classpath element '" + v + "'. Make sure that all paths provided with '" + SubstrateOptions.IMAGE_CLASSPATH_PREFIX + "' are correct.");
                }
            }).toArray(URL[]::new);
        }

        static Stream<Path> toClassPathEntries(String classPathEntry) {
            Path entry = ClasspathUtils.stringToClasspath(classPathEntry);
            if (entry.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
                try {
                    return Files.list(entry.getParent()).filter(ClasspathUtils::isJar);
                } catch (IOException e) {
                    return Stream.empty();
                }
            }
            if (Files.isReadable(entry)) {
                return Stream.of(entry);
            }
            return Stream.empty();
        }

        static Path urlToPath(URL url) {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw VMError.shouldNotReachHere();
            }
        }
    }

    protected static class ClassInit {

        protected final ForkJoinPool executor;
        protected final ImageClassLoader imageClassLoader;
        protected final AbstractNativeImageClassLoaderSupport nativeImageClassLoader;

        ClassInit(ForkJoinPool executor, ImageClassLoader imageClassLoader, AbstractNativeImageClassLoaderSupport nativeImageClassLoader) {
            this.executor = executor;
            this.imageClassLoader = imageClassLoader;
            this.nativeImageClassLoader = nativeImageClassLoader;
        }

        protected void init() {
            Set<Path> uniquePaths = new TreeSet<>(Comparator.comparing(ClassInit::toRealPath));
            uniquePaths.addAll(nativeImageClassLoader.classpath());
            uniquePaths.parallelStream().forEach(path -> loadClassesFromPath(path));
        }

        private static Path toRealPath(Path p) {
            try {
                return p.toRealPath();
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Path.toRealPath failed for " + p, e);
            }
        }

        private static final Set<Path> excludeDirectories = getExcludeDirectories();

        private static Set<Path> getExcludeDirectories() {
            Path root = Paths.get("/");
            return Stream.of("dev", "sys", "proc", "etc", "var", "tmp", "boot", "lost+found")
                            .map(root::resolve).collect(Collectors.toSet());
        }

        private void loadClassesFromPath(Path path) {
            if (Files.exists(path)) {
                if (Files.isRegularFile(path)) {
                    try {
                        URI jarURI = new URI("jar:" + path.toAbsolutePath().toUri());
                        FileSystem probeJarFileSystem;
                        try {
                            probeJarFileSystem = FileSystems.newFileSystem(jarURI, Collections.emptyMap());
                        } catch (UnsupportedOperationException e) {
                            /* Silently ignore invalid jar-files on image-classpath */
                            probeJarFileSystem = null;
                        }
                        if (probeJarFileSystem != null) {
                            try (FileSystem jarFileSystem = probeJarFileSystem) {
                                loadClassesFromPath(jarFileSystem.getPath("/"), Collections.emptySet());
                            }
                        }
                    } catch (ClosedByInterruptException ignored) {
                        throw new InterruptImageBuilding();
                    } catch (IOException | URISyntaxException e) {
                        throw shouldNotReachHere(e);
                    }
                } else {
                    loadClassesFromPath(path, excludeDirectories);
                }
            }
        }

        protected static final String CLASS_EXTENSION = ".class";

        private void loadClassesFromPath(Path root, Set<Path> excludes) {
            FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                private final char fileSystemSeparatorChar = root.getFileSystem().getSeparator().charAt(0);

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (excludes.contains(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (excludes.contains(file.getParent())) {
                        return FileVisitResult.SKIP_SIBLINGS;
                    }
                    String fileName = root.relativize(file).toString();
                    if (fileName.endsWith(CLASS_EXTENSION)) {
                        executor.execute(() -> handleClassFileName(unversionedFileName(fileName), fileSystemSeparatorChar));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    /* Silently ignore inaccessible files or directories. */
                    return FileVisitResult.CONTINUE;
                }

                /**
                 * Take a file name from a possibly-multi-versioned jar file and remove the
                 * versioning information. See
                 * https://docs.oracle.com/javase/9/docs/api/java/util/jar/JarFile.html for the
                 * specification of the versioning strings.
                 *
                 * Then, depend on the JDK class loading mechanism to prefer the
                 * appropriately-versioned class when the class is loaded. The same class name be
                 * loaded multiple times, but each request will return the same
                 * appropriately-versioned class. If a higher-versioned class is not available in a
                 * lower-versioned JDK, a ClassNotFoundException will be thrown, which will be
                 * handled appropriately.
                 */
                private String unversionedFileName(String fileName) {
                    final String versionedPrefix = "META-INF/versions/";
                    final String versionedSuffix = "/";
                    String result = fileName;
                    if (fileName.startsWith(versionedPrefix)) {
                        final int versionedSuffixIndex = fileName.indexOf(versionedSuffix, versionedPrefix.length());
                        if (versionedSuffixIndex >= 0) {
                            result = fileName.substring(versionedSuffixIndex + versionedSuffix.length());
                        }
                    }
                    return classFileWithoutSuffix(result);
                }
            };

            try {
                Files.walkFileTree(root, visitor);
            } catch (IOException ex) {
                throw shouldNotReachHere(ex);
            }
        }

        static String classFileWithoutSuffix(String result) {
            return result.substring(0, result.length() - CLASS_EXTENSION.length());
        }

        protected void handleClassFileName(String strippedClassFileName, char fileSystemSeparatorChar) {
            if (strippedClassFileName.equals("module-info")) {
                return;
            }

            String className = strippedClassFileName.replace(fileSystemSeparatorChar, '.');

            Class<?> clazz = null;
            try {
                clazz = imageClassLoader.forName(className);
            } catch (Throwable t) {
                ImageClassLoader.handleClassLoadingError(t);
            }
            if (clazz != null) {
                imageClassLoader.handleClass(clazz);
            }
        }
    }
}
