// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.skylarkinterface.processor;

import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.tools.Diagnostic;

/**
 * Annotation processor for {@link SkylarkCallable}.
 *
 * <p>Checks the following invariants about {@link SkylarkCallable}-annotated methods:
 * <ul>
 * <li>The method must be public.</li>
 * <li>If structField=true, there must be zero user-supplied parameters.</li>
 * <li>Method parameters must be supplied in the following order:
 *   <pre>method([positionals]*[other user-args](Location)(FuncallExpression)(Envrionment))</pre>
 *   where Location, FuncallExpression, and Environment are supplied by the interpreter if and
 *   only if useLocation, useAst, and useEnvironment are specified, respectively.
 *  </li>
 * <li>
 *   The number of method parameters much match the number of annotation-declared parameters
 *   plus the number of interpreter-supplied parameters.
 * </li>
 * <li>
 *   Each parameter, if explicitly typed, may only use either 'type' or 'allowedTypes',
 *   not both.
 * </li>
 * <li>
 *   Either the doc string is non-empty, or documented is false.
 * </li>
 * </ul>
 *
 * <p>These properties can be relied upon at runtime without additional checks.
 */
@SupportedAnnotationTypes({"com.google.devtools.build.lib.skylarkinterface.SkylarkCallable"})
public final class SkylarkCallableProcessor extends AbstractProcessor {
  private Messager messager;

  private static final String LOCATION = "com.google.devtools.build.lib.events.Location";
  private static final String AST = "com.google.devtools.build.lib.syntax.FuncallExpression";
  private static final String ENVIRONMENT = "com.google.devtools.build.lib.syntax.Environment";
  private static final String SKYLARK_SEMANTICS =
      "com.google.devtools.build.lib.syntax.SkylarkSemantics";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(SkylarkCallable.class)) {

      // Only methods are annotated with SkylarkCallable. This is verified by the
      // @Target(ElementType.METHOD) annotation.
      ExecutableElement methodElement = (ExecutableElement) element;
      SkylarkCallable annotation = methodElement.getAnnotation(SkylarkCallable.class);

      if (!methodElement.getModifiers().contains(Modifier.PUBLIC)) {
        error(methodElement, "@SkylarkCallable annotated methods must be public.");
      }

      try {
        verifyDocumented(methodElement, annotation);
        verifyNotStructFieldWithInvalidExtraParams(methodElement, annotation);
        verifyParamSemantics(methodElement, annotation);
        verifyNumberOfParameters(methodElement, annotation);
        verifyExtraInterpreterParams(methodElement, annotation);
      } catch (SkylarkCallableProcessorException exception) {
        error(exception.methodElement, exception.errorMessage);
      }
    }

    return true;
  }

  private void verifyDocumented(ExecutableElement methodElement, SkylarkCallable annotation)
      throws SkylarkCallableProcessorException {
    if (annotation.documented() && annotation.doc().isEmpty()) {
      throw new SkylarkCallableProcessorException(
            methodElement,
            "The 'doc' string must be non-empty if 'documented' is true.");
    }
  }

  private void verifyNotStructFieldWithInvalidExtraParams(
      ExecutableElement methodElement, SkylarkCallable annotation)
      throws SkylarkCallableProcessorException {
    if (annotation.structField()) {
      if (annotation.useAst() || annotation.useEnvironment() || annotation.useAst()) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            "@SkylarkCallable-annotated methods with structField=true may not also specify "
                + "useAst, useEnvironment, or useLocation");
      }
    }
  }

  private void verifyParamSemantics(ExecutableElement methodElement, SkylarkCallable annotation)
      throws SkylarkCallableProcessorException {
    for (Param parameter : annotation.parameters()) {
      if ("None".equals(parameter.defaultValue()) && !parameter.noneable()) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format("Parameter '%s' has 'None' default value but is not noneable. "
                + "(If this is intended as a mandatory parameter, leave the defaultValue field "
                + "empty)",
                parameter.name()));
      }
      if ((parameter.allowedTypes().length > 0)
          && (!"java.lang.Object".equals(paramTypeFieldCanonicalName(parameter)))) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format("Parameter '%s' has both 'type' and 'allowedTypes' specified. Only"
                + " one may be specified.",
            parameter.name()));
      }
    }
  }

  private String paramTypeFieldCanonicalName(Param param) {
    try {
      return param.type().toString();
    } catch (MirroredTypeException exception) {
      // This is a hack to obtain the actual canonical name of param.type(). Doing this ths
      // "correct" way results in far less readable code.
      // Since this processor is only for compile-time checks, this isn't efficiency we need
      // to worry about.
      return exception.getTypeMirror().toString();
    }
  }

  private void verifyNumberOfParameters(ExecutableElement methodElement, SkylarkCallable annotation)
      throws SkylarkCallableProcessorException {
    List<? extends VariableElement> methodSignatureParams = methodElement.getParameters();
    int numExtraInterpreterParams = numExpectedExtraInterpreterParams(annotation);

    if (annotation.parameters().length > 0 || annotation.mandatoryPositionals() >= 0) {
      int numDeclaredArgs =
          annotation.parameters().length + Math.max(0, annotation.mandatoryPositionals());
      if (methodSignatureParams.size() != numDeclaredArgs + numExtraInterpreterParams) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format(
                "@SkylarkCallable annotated method has %d parameters, but annotation declared "
                    + "%d user-supplied parameters and %d extra interpreter parameters.",
                methodSignatureParams.size(), numDeclaredArgs, numExtraInterpreterParams));
      }
    }
    if (annotation.structField()) {
      if (methodSignatureParams.size() != numExtraInterpreterParams) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format(
                "@SkylarkCallable annotated methods with structField=true must have "
                    + "0 user-supplied parameters. Expected %d extra interpreter parameters, "
                    + "but found %d total parameters.",
                numExtraInterpreterParams, methodSignatureParams.size()));
      }
    }
  }

  private void verifyExtraInterpreterParams(ExecutableElement methodElement,
      SkylarkCallable annotation) throws SkylarkCallableProcessorException {
    List<? extends VariableElement> methodSignatureParams = methodElement.getParameters();
    int currentIndex = methodSignatureParams.size() - numExpectedExtraInterpreterParams(annotation);

    // TODO(cparsons): Matching by class name alone is somewhat brittle, but due to tangled
    // dependencies, it is difficult for this processor to depend directy on the expected
    // classes here.
    if (annotation.useLocation()) {
      if (!LOCATION.equals(methodSignatureParams.get(currentIndex).asType().toString())) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format(
                "Expected parameter index %d to be the %s type, matching useLocation, but was %s",
                currentIndex,
                LOCATION,
                methodSignatureParams.get(currentIndex).asType().toString()));
      }
      currentIndex++;
    }
    if (annotation.useAst()) {
      if (!AST.equals(methodSignatureParams.get(currentIndex).asType().toString())) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format(
                "Expected parameter index %d to be the %s type, matching useAst, but was %s",
                currentIndex, AST, methodSignatureParams.get(currentIndex).asType().toString()));
      }
      currentIndex++;
    }
    if (annotation.useEnvironment()) {
      if (!ENVIRONMENT.equals(methodSignatureParams.get(currentIndex).asType().toString())) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format(
                "Expected parameter index %d to be the %s type, matching useEnvironment, "
                    + "but was %s",
                currentIndex,
                ENVIRONMENT,
                methodSignatureParams.get(currentIndex).asType().toString()));
      }
      currentIndex++;
    }
    if (annotation.useSkylarkSemantics()) {
      if (!SKYLARK_SEMANTICS.equals(methodSignatureParams.get(currentIndex).asType().toString())) {
        throw new SkylarkCallableProcessorException(
            methodElement,
            String.format(
                "Expected parameter index %d to be the %s type, matching useSkylarkSemantics, "
                    + "but was %s",
                currentIndex,
                SKYLARK_SEMANTICS,
                methodSignatureParams.get(currentIndex).asType().toString()));
      }
    }
  }

  private int numExpectedExtraInterpreterParams(SkylarkCallable annotation) {
    int numExtraInterpreterParams = 0;
    numExtraInterpreterParams += annotation.useLocation() ? 1 : 0;
    numExtraInterpreterParams += annotation.useAst() ? 1 : 0;
    numExtraInterpreterParams += annotation.useEnvironment() ? 1 : 0;
    numExtraInterpreterParams += annotation.useSkylarkSemantics() ? 1 : 0;
    return numExtraInterpreterParams;
  }

  /**
   * Prints an error message & fails the compilation.
   *
   * @param e The element which has caused the error. Can be null
   * @param msg The error message
   */
  private void error(Element e, String msg) {
    messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
  }

  private static class SkylarkCallableProcessorException extends Exception {
    private final ExecutableElement methodElement;
    private final String errorMessage;

    private SkylarkCallableProcessorException(
        ExecutableElement methodElement, String errorMessage) {
      this.methodElement = methodElement;
      this.errorMessage = errorMessage;
    }
  }
}
