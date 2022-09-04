// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.buildjar;

import com.google.devtools.build.buildjar.instrumentation.JacocoInstrumentationProcessor;
import com.google.devtools.build.buildjar.jarhelper.JarCreator;
import com.google.devtools.build.buildjar.javac.BlazeJavacArguments;
import com.google.devtools.build.buildjar.javac.BlazeJavacMain;
import com.google.devtools.build.buildjar.javac.BlazeJavacResult;
import com.google.devtools.build.buildjar.javac.JavacRunner;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** An implementation of the JavaBuilder that uses in-process javac to compile java files. */
public class SimpleJavaLibraryBuilder implements Closeable {

  /** The name of the protobuf meta file. */
  private static final String PROTOBUF_META_NAME = "protobuf.meta";

  /** Cache of opened zip filesystems for srcjars. */
  private final Map<Path, FileSystem> filesystems = new HashMap<>();

  BlazeJavacResult compileSources(JavaLibraryBuildRequest build, JavacRunner javacRunner)
      throws IOException {
    return javacRunner.invokeJavac(build.toBlazeJavacArguments(build.getClassPath()));
  }

  protected void prepareSourceCompilation(JavaLibraryBuildRequest build) throws IOException {
    cleanupDirectory(build.getClassDir());

    setUpSourceJars(build);
    cleanupDirectory(build.getSourceGenDir());
    cleanupDirectory(build.getNativeHeaderDir());
  }

  // Necessary for local builds in order to discard previous outputs
  private static void cleanupDirectory(@Nullable Path directory) throws IOException {
    if (directory == null) {
      return;
    }
    if (!Files.exists(directory)) {
      Files.createDirectories(directory);
      return;
    }
    try {
      // TODO(b/27069912): handle symlinks
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              if (!dir.equals(directory)) {
                Files.delete(dir);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IOException("Cannot clean '" + directory + "'", e);
    }
  }

  public void buildGensrcJar(JavaLibraryBuildRequest build) throws IOException {
    JarCreator jar = new JarCreator(build.getGeneratedSourcesOutputJar());
    try {
      jar.setNormalize(true);
      jar.setCompression(build.compressJar());
      jar.addDirectory(build.getSourceGenDir());
    } finally {
      jar.execute();
    }
  }

  /**
   * Prepares a compilation run and sets everything up so that the source files in the build request
   * can be compiled. Invokes compileSources to do the actual compilation.
   *
   * @param build A JavaLibraryBuildRequest request object describing what to compile
   */
  public BlazeJavacResult compileJavaLibrary(final JavaLibraryBuildRequest build) throws Exception {
    prepareSourceCompilation(build);
    if (build.getSourceFiles().isEmpty()) {
      return BlazeJavacResult.ok();
    }
    JavacRunner javacRunner =
        new JavacRunner() {
          @Override
          public BlazeJavacResult invokeJavac(BlazeJavacArguments arguments) {
            return BlazeJavacMain.compile(arguments);
          }
        };
    BlazeJavacResult result = compileSources(build, javacRunner);
    return result;
  }

  /** Perform the build. */
  public BlazeJavacResult run(JavaLibraryBuildRequest build) throws Exception {
    BlazeJavacResult result = BlazeJavacResult.error("");
    try {
      result = compileJavaLibrary(build);
      if (result.isOk()) {
        buildJar(build);
        nativeHeaderOutput(build);
      }
      if (!build.getProcessors().isEmpty()) {
        if (build.getGeneratedSourcesOutputJar() != null) {
          buildGensrcJar(build);
        }
      }
    } finally {
      build.getDependencyModule().emitDependencyInformation(build.getClassPath(), result.isOk());
      build.getProcessingModule().emitManifestProto();
    }
    return result;
  }

  public void buildJar(JavaLibraryBuildRequest build) throws IOException {
    JarCreator jar = new JarCreator(build.getOutputJar());
    JacocoInstrumentationProcessor processor = null;
    try {
      jar.setNormalize(true);
      jar.setCompression(build.compressJar());
      jar.addDirectory(build.getClassDir());
      jar.setJarOwner(build.getTargetLabel(), build.getInjectingRuleKind());
      processor = build.getJacocoInstrumentationProcessor();
      if (processor != null) {
        processor.processRequest(build, processor.isNewCoverageImplementation() ? jar : null);
      }
    } finally {
      jar.execute();
      if (processor != null) {
        processor.cleanup();
      }
    }
  }

  public void nativeHeaderOutput(JavaLibraryBuildRequest build) throws IOException {
    if (build.getNativeHeaderOutput() == null) {
      return;
    }
    JarCreator jar = new JarCreator(build.getNativeHeaderOutput());
    try {
      jar.setNormalize(true);
      jar.setCompression(build.compressJar());
      jar.addDirectory(build.getNativeHeaderDir());
    } finally {
      jar.execute();
    }
  }

  /**
   * Extracts the all source jars from the build request into the temporary directory specified in
   * the build request. Empties the temporary directory, if it exists.
   */
  private void setUpSourceJars(JavaLibraryBuildRequest build) throws IOException {
    Path sourcesDir = build.getTempDir();

    cleanupDirectory(sourcesDir);

    if (build.getSourceJars().isEmpty()) {
      return;
    }

    final ByteArrayOutputStream protobufMetadataBuffer = new ByteArrayOutputStream();
    for (Path sourceJar : build.getSourceJars()) {
      for (Path root : getJarFileSystem(sourceJar).getRootDirectories()) {
        Files.walkFileTree(
            root,
            new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
                  throws IOException {
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(".java")) {
                  build.getSourceFiles().add(path);
                } else if (fileName.equals(PROTOBUF_META_NAME)) {
                  Files.copy(path, protobufMetadataBuffer);
                }
                return FileVisitResult.CONTINUE;
              }
            });
      }
    }
    Path output = build.getClassDir().resolve(PROTOBUF_META_NAME);
    if (protobufMetadataBuffer.size() > 0) {
      try (OutputStream outputStream = Files.newOutputStream(output)) {
        protobufMetadataBuffer.writeTo(outputStream);
      }
    } else if (Files.exists(output)) {
      // Delete stalled meta file.
      Files.delete(output);
    }
  }

  private FileSystem getJarFileSystem(Path sourceJar) throws IOException {
    FileSystem fs = filesystems.get(sourceJar);
    if (fs == null) {
      filesystems.put(sourceJar, fs = FileSystems.newFileSystem(sourceJar, null));
    }
    return fs;
  }

  @Override
  public void close() throws IOException {
    for (FileSystem fs : filesystems.values()) {
      fs.close();
    }
  }
}
