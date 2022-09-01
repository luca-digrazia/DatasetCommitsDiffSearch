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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

class LambdaClassMaker {

  static final String LAMBDA_METAFACTORY_DUMPER_PROPERTY = "jdk.internal.lambda.dumpProxyClasses";

  private final Path rootDirectory;
  private final Map<Path, LambdaInfo> generatedClasses = new LinkedHashMap<>();

  public LambdaClassMaker(Path rootDirectory) {
    checkArgument(Files.isDirectory(rootDirectory));
    this.rootDirectory = rootDirectory;
  }

  public String generateLambdaClass(String invokerInternalName, LambdaInfo lambdaInfo,
      MethodHandle bootstrapMethod, ArrayList<Object> bsmArgs) throws IOException {
    // Invoking the bootstrap method will dump the generated class
    try {
      bootstrapMethod.invokeWithArguments(bsmArgs);
    } catch (Throwable e) {
      throw new IllegalStateException("Failed to generate lambda class for class "
          + invokerInternalName + " using " + bootstrapMethod + " with arguments " + bsmArgs, e);
    }

    Path generatedClassFile = findOnlyUnprocessed(invokerInternalName + "$$Lambda$");
    String lambdaClassName = generatedClassFile.toString();
    checkState(lambdaClassName.endsWith(".class"), "Unexpected filename %s", lambdaClassName);
    lambdaClassName = lambdaClassName.substring(0, lambdaClassName.length() - ".class".length());
    generatedClasses.put(generatedClassFile, lambdaInfo);
    return lambdaClassName;
  }

  /**
   * Returns relative paths to .class files generated since the last call to this method together
   * with a string descriptor of the factory method.
   */
  public Map<Path, LambdaInfo> drain() {
    ImmutableMap<Path, LambdaInfo> result = ImmutableMap.copyOf(generatedClasses);
    generatedClasses.clear();
    return result;
  }

  private Path findOnlyUnprocessed(final String pathPrefix) throws IOException {
    // TODO(kmb): Investigate making this faster in the case of many lambdas
    // TODO(bazel-team): This could be much nicer with lambdas
    try (Stream<Path> results =
        Files.walk(rootDirectory)
            .map(
                new Function<Path, Path>() {
                  @Override
                  public Path apply(Path path) {
                    return rootDirectory.relativize(path);
                  }
                })
            .filter(
                new Predicate<Path>() {
                  @Override
                  public boolean test(Path path) {
                    return path.toString().startsWith(pathPrefix)
                        && !generatedClasses.containsKey(path);
                  }
                })) {
      return Iterators.getOnlyElement(results.iterator());
    }
  }
}
