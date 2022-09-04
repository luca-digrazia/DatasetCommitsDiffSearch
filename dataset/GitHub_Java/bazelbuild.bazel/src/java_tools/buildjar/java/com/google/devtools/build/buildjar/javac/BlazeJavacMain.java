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

package com.google.devtools.build.buildjar.javac;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.buildjar.InvalidCommandLineException;
import com.google.devtools.build.buildjar.javac.FormattedDiagnostic.Listener;
import com.google.devtools.build.buildjar.javac.plugins.BlazeJavaCompilerPlugin;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.ClientCodeWrapper.Trusted;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.CacheFSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.PropagatedException;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Main class for our custom patched javac.
 *
 * <p>This main class tweaks the standard javac log class by changing the compiler's context to use
 * our custom log class. This custom log class modifies javac's output to list all errors after all
 * warnings.
 */
public class BlazeJavacMain {

  /**
   * Sets up a BlazeJavaCompiler with the given plugins within the given context.
   *
   * @param context JavaCompiler's associated Context
   */
  @VisibleForTesting
  static void setupBlazeJavaCompiler(
      ImmutableList<BlazeJavaCompilerPlugin> plugins, Context context) {
    for (BlazeJavaCompilerPlugin plugin : plugins) {
      plugin.initializeContext(context);
    }
    BlazeJavaCompiler.preRegister(context, plugins);
  }

  public static BlazeJavacResult compile(BlazeJavacArguments arguments) {

    List<String> javacArguments = arguments.javacOptions();
    try {
      javacArguments = processPluginArgs(arguments.plugins(), javacArguments);
    } catch (InvalidCommandLineException e) {
      return BlazeJavacResult.error(e.getMessage());
    }

    Context context = new Context();
    CacheFSInfo.preRegister(context);
    setupBlazeJavaCompiler(arguments.plugins(), context);

    boolean ok = false;
    StringWriter errOutput = new StringWriter();
    // TODO(cushon): where is this used when a diagnostic listener is registered? Consider removing
    // it and handling exceptions directly in callers.
    PrintWriter errWriter = new PrintWriter(errOutput);
    Listener diagnostics = new Listener(context);
    BlazeJavaCompiler compiler;

    try (JavacFileManager fileManager = new ClassloaderMaskingFileManager()) {
      JavacTask task =
          JavacTool.create()
              .getTask(
                  errWriter,
                  fileManager,
                  diagnostics,
                  javacArguments,
                  /* classes= */ ImmutableList.of(),
                  fileManager.getJavaFileObjectsFromPaths(arguments.sourceFiles()),
                  context);
      if (arguments.processors() != null) {
        task.setProcessors(arguments.processors());
      }
      fileManager.setContext(context);
      setLocations(fileManager, arguments);
      try {
        ok = task.call();
      } catch (PropagatedException e) {
        throw e.getCause();
      }
    } catch (Throwable t) {
      t.printStackTrace(errWriter);
      ok = false;
    } finally {
      compiler = (BlazeJavaCompiler) JavaCompiler.instance(context);
      if (ok) {
        // There could be situations where we incorrectly skip Error Prone and the compilation
        // ends up succeeding, e.g., if there are errors that are fixed by subsequent round of
        // annotation processing.  This check ensures that if there were any flow events at all,
        // then plugins were run.  There may legitimately not be any flow events, e.g. -proc:only
        // or empty source files.
        if (compiler.skippedFlowEvents() > 0 && compiler.flowEvents() == 0) {
          errWriter.println("Expected at least one FLOW event");
          ok = false;
        }
      }
    }
    errWriter.flush();
    return new BlazeJavacResult(
        ok, filterDiagnostics(diagnostics.build()), errOutput.toString(), compiler);
  }

  private static final ImmutableSet<String> IGNORED_DIAGNOSTIC_CODES =
      ImmutableSet.of(
          "compiler.note.deprecated.filename",
          "compiler.note.deprecated.plural",
          "compiler.note.deprecated.recompile",
          "compiler.note.deprecated.filename.additional",
          "compiler.note.deprecated.plural.additional",
          "compiler.note.unchecked.filename",
          "compiler.note.unchecked.plural",
          "compiler.note.unchecked.recompile",
          "compiler.note.unchecked.filename.additional",
          "compiler.note.unchecked.plural.additional",
          "compiler.warn.sun.proprietary",
          // avoid warning spam when enabling processor options for an entire tree, only a subset
          // of which actually runs the processor
          "compiler.warn.proc.unmatched.processor.options");

  private static ImmutableList<FormattedDiagnostic> filterDiagnostics(
      ImmutableList<FormattedDiagnostic> diagnostics) {
    boolean werror =
        diagnostics.stream().anyMatch(d -> d.getCode().equals("compiler.err.warnings.and.werror"));
    return diagnostics
        .stream()
        .filter(d -> shouldReportDiagnostic(werror, d))
        // Print errors last to make them more visible.
        .sorted(comparing(FormattedDiagnostic::getKind).reversed())
        .collect(toImmutableList());
  }

  private static boolean shouldReportDiagnostic(boolean werror, FormattedDiagnostic diagnostic) {
    if (!IGNORED_DIAGNOSTIC_CODES.contains(diagnostic.getCode())) {
      return true;
    }
    // show compiler.warn.sun.proprietary if we're running with -Werror
    if (werror && diagnostic.getKind() != Diagnostic.Kind.NOTE) {
      return true;
    }
    return false;
  }

  /** Processes Plugin-specific arguments and removes them from the args array. */
  @VisibleForTesting
  static List<String> processPluginArgs(
      ImmutableList<BlazeJavaCompilerPlugin> plugins, List<String> args)
      throws InvalidCommandLineException {
    List<String> processedArgs = args;
    for (BlazeJavaCompilerPlugin plugin : plugins) {
      processedArgs = plugin.processArgs(processedArgs);
    }
    return processedArgs;
  }

  private static void setLocations(JavacFileManager fileManager, BlazeJavacArguments arguments) {
    try {
      fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, arguments.classPath());
      // modular dependencies must be on the module path, not the classpath
      fileManager.setLocationFromPaths(
          StandardLocation.locationFor("MODULE_PATH"), arguments.classPath());

      fileManager.setLocationFromPaths(
          StandardLocation.CLASS_OUTPUT, ImmutableList.of(arguments.classOutput()));
      if (arguments.nativeHeaderOutput() != null) {
        fileManager.setLocationFromPaths(
            StandardLocation.NATIVE_HEADER_OUTPUT,
            ImmutableList.of(arguments.nativeHeaderOutput()));
      }

      ImmutableList<Path> sourcePath = arguments.sourcePath();
      if (sourcePath.isEmpty()) {
        // javac expects a module-info-relative source path to be set when compiling modules,
        // otherwise it reports an error:
        // "file should be on source path, or on patch path for module"
        ImmutableList<Path> moduleInfos =
            arguments
                .sourceFiles()
                .stream()
                .filter(f -> f.getFileName().toString().equals("module-info.java"))
                .collect(toImmutableList());
        if (moduleInfos.size() == 1) {
          sourcePath = ImmutableList.of(getOnlyElement(moduleInfos).getParent());
        }
      }
      fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourcePath);

      // The bootclasspath may legitimately be empty if --release is being used.
      Collection<Path> bootClassPath = arguments.bootClassPath();
      if (!bootClassPath.isEmpty()) {
        fileManager.setLocationFromPaths(StandardLocation.PLATFORM_CLASS_PATH, bootClassPath);
      }
      fileManager.setLocationFromPaths(
          StandardLocation.ANNOTATION_PROCESSOR_PATH, arguments.processorPath());
      if (arguments.sourceOutput() != null) {
        fileManager.setLocationFromPaths(
            StandardLocation.SOURCE_OUTPUT, ImmutableList.of(arguments.sourceOutput()));
      }
    } catch (IOException e) {
      throw new IOError(e);
    }
  }

  /**
   * When Bazel invokes JavaBuilder, it puts javac.jar on the bootstrap class path and
   * JavaBuilder_deploy.jar on the user class path. We need Error Prone to be available on the
   * annotation processor path, but we want to mask out any other classes to minimize class version
   * skew.
   */
  @Trusted
  private static class ClassloaderMaskingFileManager extends JavacFileManager {

    private static Context getContext() {
      Context context = new Context();
      CacheFSInfo.preRegister(context);
      return context;
    }

    public ClassloaderMaskingFileManager() {
      super(getContext(), false, UTF_8);
    }

    @Override
    protected ClassLoader getClassLoader(URL[] urls) {
      return new URLClassLoader(
          urls,
          new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
              Class<?> c = Class.forName(name);
              if (name.startsWith("com.google.errorprone.")
                  || name.startsWith("org.checkerframework.dataflow.")
                  || name.startsWith("com.sun.source.")
                  || name.startsWith("com.sun.tools.")) {
                return c;
              }
              if (c.getClassLoader() == null
                  || Objects.equals(getClassLoaderName(c.getClassLoader()), "platform")) {
                return c;
              }
              throw new ClassNotFoundException(name);
            }
          });
    }
  }

  // TODO(cushon): remove this use of reflection if Java 9 is released.
  private static String getClassLoaderName(ClassLoader classLoader) {
    Method method;
    try {
      method = ClassLoader.class.getMethod("getName");
    } catch (NoSuchMethodException e) {
      // ClassLoader#getName doesn't exist in JDK 8 and earlier.
      return null;
    }
    try {
      return (String) method.invoke(classLoader, new Object[] {});
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private BlazeJavacMain() {}
}
