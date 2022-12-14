// Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.android.desugar;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.devtools.build.android.Converters.ExistingPathConverter;
import com.google.devtools.build.android.Converters.PathConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * Command-line tool to desugar Java 8 constructs that dx doesn't know what to do with, in
 * particular lambdas and method references.
 */
class Desugar {

  /**
   * Commandline options for {@link Desugar}.
   */
  public static class Options extends OptionsBase {
    @Option(name = "input",
        defaultValue = "null",
        category = "input",
        converter = ExistingPathConverter.class,
        abbrev = 'i',
        help = "Input Jar with classes to desugar.")
    public Path inputJar;

    @Option(name = "classpath_entry",
        allowMultiple = true,
        defaultValue = "",
        category = "input",
        converter = ExistingPathConverter.class,
        help = "Ordered classpath to resolve symbols in the --input Jar, like javac's -cp flag.")
    public List<Path> classpath;

    @Option(name = "bootclasspath_entry",
        allowMultiple = true,
        defaultValue = "",
        category = "input",
        converter = ExistingPathConverter.class,
        help = "Bootclasspath that was used to compile the --input Jar with, like javac's "
            + "-bootclasspath flag. If no bootclasspath is explicitly given then the tool's own "
            + "bootclasspath is used.")
    public List<Path> bootclasspath;

    @Option(name = "output",
        defaultValue = "null",
        category = "output",
        converter = PathConverter.class,
        abbrev = 'o',
        help = "Output Jar to write desugared classes into.")
    public Path outputJar;

    @Option(name = "verbose",
        defaultValue = "false",
        category = "misc",
        abbrev = 'v',
        help = "Enables verbose debugging output.")
    public boolean verbose;
  }

  public static void main(String[] args) throws Exception {
    // LambdaClassMaker generates lambda classes for us, but it does so by essentially simulating
    // the call to LambdaMetafactory that the JVM would make when encountering an invokedynamic.
    // LambdaMetafactory is in the JDK and its implementation has a property to write out ("dump")
    // generated classes, which we take advantage of here.  Set property before doing anything else
    // since the property is read in the static initializer; if this breaks we can investigate
    // setting the property when calling the tool.
    Path dumpDirectory = Files.createTempDirectory("lambdas");
    System.setProperty(
        LambdaClassMaker.LAMBDA_METAFACTORY_DUMPER_PROPERTY, dumpDirectory.toString());

    if (args.length == 1 && args[0].startsWith("@")) {
      args = Files.readAllLines(Paths.get(args[0].substring(1)), ISO_8859_1).toArray(new String[0]);
    }

    OptionsParser optionsParser =
        OptionsParser.newOptionsParser(Options.class);
    optionsParser.parseAndExitUponError(args);
    Options options = optionsParser.getOptions(Options.class);

    if (options.verbose) {
      System.out.printf("Lambda classes will be written under %s%n", dumpDirectory);
    }
    ClassLoader loader =
        createClassLoader(options.bootclasspath, options.inputJar, options.classpath);
    try (ZipFile in = new ZipFile(options.inputJar.toFile());
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
            Files.newOutputStream(options.outputJar)))) {
      LambdaClassMaker lambdas = new LambdaClassMaker(dumpDirectory);
      ClassReaderFactory readerFactory = new ClassReaderFactory(in);
      ImmutableSet.Builder<String> interfaceLambdaMethodCollector = ImmutableSet.builder();

      // Process input Jar, desugaring as we go
      for (Enumeration<? extends ZipEntry> entries = in.entries(); entries.hasMoreElements(); ) {
        ZipEntry entry = entries.nextElement();
        try (InputStream content = in.getInputStream(entry)) {
          // We can write classes uncompressed since they need to be converted to .dex format for
          // Android anyways. Resources are written as they were in the input jar to avoid any
          // danger of accidentally uncompressed resources ending up in an .apk.
          if (entry.getName().endsWith(".class")) {
            ClassReader reader = new ClassReader(content);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS /*for bridge methods*/);
            reader.accept(
                new LambdaDesugaring(
                    new Java7Compatibility(writer, readerFactory),
                    loader,
                    lambdas,
                    interfaceLambdaMethodCollector),
                0);
            writeStoredEntry(out, entry.getName(), writer.toByteArray());
          } else {
            // TODO(bazel-team): Avoid de- and re-compressing resource files
            ZipEntry destEntry = new ZipEntry(entry);
            destEntry.setCompressedSize(-1);
            out.putNextEntry(destEntry);
            ByteStreams.copy(content, out);
            out.closeEntry();
          }
        }
      }

      // Write out the lambda classes we generated along the way
      ImmutableSet<String> interfaceLambdaMethods = interfaceLambdaMethodCollector.build();
      for (Map.Entry<Path, LambdaInfo> lambdaClass : lambdas.drain().entrySet()) {
        try (InputStream bytecode =
            Files.newInputStream(dumpDirectory.resolve(lambdaClass.getKey()))) {
          ClassReader reader = new ClassReader(bytecode);
          ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS /*for invoking bridges*/);
          LambdaClassFixer lambdaFixer =
              new LambdaClassFixer(
                  // null ClassReaderFactory b/c we don't expect to need it for lambda classes
                  new Java7Compatibility(writer, (ClassReaderFactory) null),
                  lambdaClass.getValue(),
                  readerFactory,
                  interfaceLambdaMethods);
          // Send lambda classes through desugaring to make sure there's no invokedynamic
          // instructions in generated lambda classes (checkState below will fail)
          reader.accept(new LambdaDesugaring(lambdaFixer, loader, lambdas, null), 0);
          writeStoredEntry(out, lambdaFixer.getInternalName() + ".class", writer.toByteArray());
        }
      }

      Map<Path, LambdaInfo> leftBehind = lambdas.drain();
      checkState(leftBehind.isEmpty(), "Didn't process %s", leftBehind);
    }
  }

  private static void writeStoredEntry(ZipOutputStream out, String filename, byte[] content)
      throws IOException {
    // Need to pre-compute checksum for STORED (uncompressed) entries)
    CRC32 checksum = new CRC32();
    checksum.update(content);

    ZipEntry result = new ZipEntry(filename);
    result.setTime(0L); // Use stable timestamp Jan 1 1980
    result.setCrc(checksum.getValue());
    result.setSize(content.length);
    result.setCompressedSize(content.length);
    // Write uncompressed, since this is just an intermediary artifact that we will convert to .dex
    result.setMethod(ZipEntry.STORED);

    out.putNextEntry(result);
    out.write(content);
    out.closeEntry();
  }

  private static ClassLoader createClassLoader(List<Path> bootclasspath, Path inputJar,
      List<Path> classpath) throws IOException {
    // Prepend classpath with input jar itself so LambdaDesugaring can load classes with lambdas.
    // Note that inputJar and classpath need to be in the same classloader because we typically get
    // the header Jar for inputJar on the classpath and having the header Jar in a parent loader
    // means the header version is preferred over the real thing.
    classpath = ImmutableList.<Path>builder().add(inputJar).addAll(classpath).build();
    if (bootclasspath.isEmpty()) {
      // TODO(b/31547323): Require bootclasspath once Bazel always provides it.  Using the tool's
      // bootclasspath as a fallback is iffy at best and produces wrong results at worst.
      return HeaderClassLoader.fromClassPath(classpath);
    }
    // Use a classloader that as much as possible uses the provided bootclasspath instead of
    // the tool's system classloader.  Unfortunately we can't do that for java. classes.
    return HeaderClassLoader.fromClassPath(classpath,
        HeaderClassLoader.fromClassPath(bootclasspath,
            new ClassLoader() {
              @Override
              protected Class<?> loadClass(String name, boolean resolve)
                  throws ClassNotFoundException {
                if (name.startsWith("java.")) {
                  // Use system class loader for java. classes, since ClassLoader.defineClass gets
                  // grumpy when those don't come from the standard place.
                  return super.loadClass(name, resolve);
                }
                throw new ClassNotFoundException();
              }
            }));
  }
}
