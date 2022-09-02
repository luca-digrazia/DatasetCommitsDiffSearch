/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

public class SubstitutionProcessor extends AbstractProcessor {
    // @EspressoSubstitutions
    private TypeElement espressoSubstitutions;
    // @Substitution
    private TypeElement substitutionAnnotation;
    // @Host
    private TypeElement host;

    // @Substitution.hasReceiver()
    private ExecutableElement hasReceiverElement;
    // @Substitution.methodName()
    private ExecutableElement methodNameElement;

    // @Host.value()
    private ExecutableElement hostValueElement;
    // @Host.typeName()
    private ExecutableElement hostTypeNameElement;

    private HashSet<String> classes = new HashSet<>();
    private StringBuilder collector = null;

    private boolean done = false;

    // region Various String constants.

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.substitutions";

    private static final String ESPRESSO_SUBSTITUTIONS = SUBSTITUTION_PACKAGE + "." + "EspressoSubstitutions";
    private static final String METHOD_SUBSTITUTION = SUBSTITUTION_PACKAGE + "." + "Substitution";
    private static final String HOST = SUBSTITUTION_PACKAGE + "." + "Host";

    private static final String INSTANCE_NAME = "theInstance";
    private static final String GETTER = "getInstance";

    private static final String COPYRIGHT = "/* Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.\n" +
                    " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                    " *\n" +
                    " * This code is free software; you can redistribute it and/or modify it\n" +
                    " * under the terms of the GNU General Public License version 2 only, as\n" +
 * published by the Free Software Foundation.
                    " *\n" +
                    " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
                    " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
                    " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
                    " * version 2 for more details (a copy is included in the LICENSE file that\n" +
                    " * accompanied this code).\n" +
                    " *\n" +
                    " * You should have received a copy of the GNU General Public License version\n" +
                    " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
                    " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
                    " *\n" +
                    " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
                    " * or visit www.oracle.com if you need additional information or have any\n" +
                    " * questions.\n" +
                    " */\n\n";

    private static final String GENERATED_BY = "// Generated by: ";
    private static final String PACKAGE = "package com.oracle.truffle.espresso.substitutions;\n" +
                    "\n";
    private static final String IMPORT_STATIC_OBJECT = "import com.oracle.truffle.espresso.runtime.StaticObject;\n";
    private static final String IMPORTS_COLLECTOR = "package com.oracle.truffle.espresso.substitutions;\n" +
                    "\n" +
                    "import java.util.ArrayList;\n" +
                    "import java.util.List;\n";

    private static final String PRIVATE_STATIC_FINAL = "private static final";

    private static final String PUBLIC_STATIC_FINAL = "public static final";
    private static final String PUBLIC_FINAL_OBJECT = "public final Object ";
    private static final String PUBLIC_FINAL_CLASS = "\n" + "public final class ";
    private static final String SUBSTITUTOR = "Substitutor";
    private static final String COLLECTOR = "SubstitutorCollector";
    private static final String COLLECTOR_INSTANCE_NAME = "substitutorCollector";
    private static final String EXTENSION = " extends " + SUBSTITUTOR + " {\n";
    private static final String OVERRIDE = "@Override";
    private static final String ARGS_NAME = "args";
    private static final String INVOKE = "invoke(Object[] " + ARGS_NAME + ") {\n";
    private static final String ARG_NAME = "arg";
    private static final String TAB_1 = "    ";
    private static final String TAB_2 = TAB_1 + TAB_1;
    private static final String TAB_3 = TAB_2 + TAB_1;

    // end region Various String constants.

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // Initialize the collector.
        this.collector = new StringBuilder();
        collector.append(COPYRIGHT);
        collector.append(GENERATED_BY).append(SubstitutionProcessor.class.getSimpleName()).append("\n\n");
        collector.append(IMPORTS_COLLECTOR);
        collector.append(PUBLIC_FINAL_CLASS).append(COLLECTOR).append(" {\n");
        collector.append(generateInstance("ArrayList<>", COLLECTOR_INSTANCE_NAME, "List<" + SUBSTITUTOR + ">"));
        collector.append(TAB_1).append("private ").append(COLLECTOR).append("() {\n").append(TAB_1).append("}\n");
        collector.append(generateGetter(COLLECTOR_INSTANCE_NAME, "List<" + SUBSTITUTOR + ">", GETTER)).append("\n");
        collector.append(TAB_1).append("static {\n");
    }

    /**
     * This method MUST return the same String as the one in Substitutor.
     */
    private static final String getSubstitutorQualifiedName(String substitutorName) {
        return SUBSTITUTION_PACKAGE + "." + substitutorName;
    }

    private static StringBuilder signatureSuffixBuilder(List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append("_").append(parameterTypes.size());
        return str;
    }

    private static final String getSubstitutorClassName(String className, String methodName, List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append(className).append("_").append(methodName).append(signatureSuffixBuilder(parameterTypes));
        return str.toString();
    }

    private static void addSubstitutor(StringBuilder str, String substitutorName) {
        str.append(TAB_2).append(COLLECTOR_INSTANCE_NAME).append(".add(").append(substitutorName).append(".").append(GETTER).append("()").append(");\n");
    }

    private static String castTo(String obj, String clazz) {
        if (clazz.equals("Object")) {
            return obj;
        }
        return "(" + clazz + ") " + obj;
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following indented code snippet:
     * 
     * <pre>{@code
     * clazz arg\index\ = (clazz) args[index];
     * }</pre>
     * 
     * If clazz is Object, the cast is removed.
     */
    // Checkstyle: resume
    // @formatter:on
    private static String extractArg(int index, String clazz, String tabulation) {
        return tabulation + clazz + " " + ARG_NAME + index + " = " + castTo(ARGS_NAME + "[" + index + "]", clazz) + ";\n";
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>{@code
     * className.methodName(arg0, arg1, ..., argN);
     * }</pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String extractInvocation(String className, String methodName, int nParameters) {
        StringBuilder str = new StringBuilder();
        str.append(className).append(".").append(methodName).append("(");
        boolean notFirst = false;
        for (int i = 0; i < nParameters; i++) {
            if (notFirst) {
                str.append(", ");
            } else {
                notFirst = true;
            }
            str.append(ARG_NAME).append(i);
        }
        str.append(");\n");
        return str.toString();
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>
     * private substitutorName() {
     *     super(
     *         "actualMethodName",
     *         "targetClassName",
     *         "returnType",
     *         "parameters",
     *         hasReceiver
     *     );
     * }
     * </pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateConstructor(String substitutorName, String targetClassName, String actualMethodName, String returnType, List<String> parameterTypes, boolean hasReceiver) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append("private ").append(substitutorName).append("() {\n");
        str.append(TAB_2).append("super(\n");
        str.append(TAB_3).append(generateString(actualMethodName)).append(",\n");
        str.append(TAB_3).append(generateString(targetClassName)).append(",\n");
        str.append(TAB_3).append(generateString(returnType)).append(",\n");
        str.append(TAB_3).append(generateParameterTypes(parameterTypes, TAB_3)).append(",\n");
        str.append(TAB_3).append(hasReceiver).append("\n");
        str.append(TAB_2).append(");\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>{@code
     * private static final substitutorName instanceName = new instanceClass();
     * }</pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateInstance(String substitutorName, String instanceName, String instanceClass) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append(PRIVATE_STATIC_FINAL).append(" ").append(instanceClass).append(" ").append(instanceName);
        str.append(" = new ").append(substitutorName).append("();\n");
        return str.toString();
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>
     * public static final className getterName() {
     *     return instanceName;
     * }
     * </pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateGetter(String instanceName, String className, String getterName) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append(PUBLIC_STATIC_FINAL).append(" ").append(className).append(" ").append(getterName).append("() {\n");
        str.append(TAB_2).append("return ").append(instanceName).append(";\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    /**
     * prepends and appends the " character to the given string.
     */
    private static String generateString(String str) {
        return "\"" + str + "\"";
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following indented code snippet:
     * 
     * <pre>
     * new String[]{
     *     types[0],
     *     types[1],
     *     ...,
     *     types[n]
     * },
     * </pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateParameterTypes(List<String> types, String tabulation) {
        StringBuilder str = new StringBuilder();
        str.append("new String[]{");
        boolean first = true;
        for (String type : types) {
            if (first) {
                str.append("\n");
                first = false;
            } else {
                str.append(",\n");
            }
            str.append(tabulation).append(TAB_1).append("\"").append(type).append("\"");
        }
        if (!first) {
            str.append("\n").append(tabulation);
        }
        str.append("}");
        return str.toString();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(ESPRESSO_SUBSTITUTIONS);
        return annotations;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (done) {
            return false;
        }
        // Set up the different annotations, along with their values, that we will need.
        this.espressoSubstitutions = processingEnv.getElementUtils().getTypeElement(ESPRESSO_SUBSTITUTIONS);
        this.substitutionAnnotation = processingEnv.getElementUtils().getTypeElement(METHOD_SUBSTITUTION);
        this.host = processingEnv.getElementUtils().getTypeElement(HOST);
        for (Element e : substitutionAnnotation.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("methodName")) {
                    this.methodNameElement = (ExecutableElement) e;
                }
                if (e.getSimpleName().contentEquals("hasReceiver")) {
                    this.hasReceiverElement = (ExecutableElement) e;
                }
            }
        }
        for (Element e : host.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("value")) {
                    this.hostValueElement = (ExecutableElement) e;
                }
                if (e.getSimpleName().contentEquals("typeName")) {
                    this.hostTypeNameElement = (ExecutableElement) e;
                }
            }
        }

        processImpl(roundEnv);
        // We are done, push the collector.
        commitFiles();
        done = true;
        return false;
    }

    private void processImpl(RoundEnvironment env) {
        for (Element e : env.getElementsAnnotatedWith(espressoSubstitutions)) {
            processElement(e);
        }
    }

    private void processElement(Element espressoSubstitution) {
        assert espressoSubstitution.getKind() == ElementKind.CLASS;
        TypeElement typeElement = (TypeElement) espressoSubstitution;
        // Extract the class name. (Of the form Target_[...]).
        String className = typeElement.getSimpleName().toString();
        for (Element method : espressoSubstitution.getEnclosedElements()) {
            // Find the methods annotated with @Substitution.
            AnnotationMirror subst = getAnnotation(method, substitutionAnnotation);
            if (subst != null) {
                assert method.getKind() == ElementKind.METHOD;
                Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = processingEnv.getElementUtils().getElementValuesWithDefaults(subst);
                // Obtain the name of the method to be substituted in.
                String targetMethodName = method.getSimpleName().toString();
                // Obtain the host types of the parameters
                List<String> espressoTypes = getEspressoTypes((ExecutableElement) method);
                // Spawn the name of the Substitutor we will create.
                String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);
                if (!classes.contains(substitutorName)) {
                    // Obtain the name of the substituted method.
                    AnnotationValue methodNameValue = elementValues.get(methodNameElement);
                    assert methodNameValue != null;
                    String actualMethodName = (String) methodNameValue.getValue();
                    if (actualMethodName.length() == 0) {
                        actualMethodName = targetMethodName;
                    }
                    // Obtain the (fully qualified) guest types parameters of the method.
                    List<String> guestTypes = getGuestTypes((ExecutableElement) method);
                    // Obtain the hasReceiver() value from the @Substitution annotation.
                    AnnotationValue hasReceiverValue = elementValues.get(hasReceiverElement);
                    assert hasReceiverValue != null;
                    boolean hasReceiver = (boolean) hasReceiverValue.getValue();
                    // Obtain the fully qualified guest return type of the method.
                    String returnType = extractReturnType((ExecutableElement) method);
                    // Create the contents of the source file
                    String classFile = spawnSubstitutor(
                                    className,
                                    targetMethodName,
                                    actualMethodName,
                                    guestTypes,
                                    espressoTypes,
                                    returnType,
                                    hasReceiver);
                    try {
                        // Create the file
                        JavaFileObject file = processingEnv.getFiler().createSourceFile(getSubstitutorQualifiedName(substitutorName), method);
                        Writer wr = file.openWriter();
                        wr.write(classFile);
                        wr.close();
                        classes.add(substitutorName);
                        addSubstitutor(collector, substitutorName);
                    } catch (IOException ex) {
                        /* nop */
                    }
                }
            }
        }
    }

    private String extractReturnType(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        AnnotationMirror a = getAnnotation(returnType, host);
        if (a != null) {
            return getClassFromHost(a);
        }
        return getInternalName(returnType.toString());
    }

    private List<String> getGuestTypes(ExecutableElement inner) {
        ArrayList<String> parameterTypeNames = new ArrayList<>();
        for (VariableElement parameter : inner.getParameters()) {
            AnnotationMirror mirror = getAnnotation(parameter.asType(), host);
            if (mirror != null) {
                parameterTypeNames.add(getClassFromHost(mirror));
            } else {
                // @Host annotation not found -> primitive or j.l.Object
                String arg = getInternalName(parameter.asType().toString());
                parameterTypeNames.add(arg);
            }
        }
        return parameterTypeNames;
    }

    /**
     * @param a an @Host annotation
     * @return the fully qualified internal name of the guest class.
     */
    private String getClassFromHost(AnnotationMirror a) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> members = processingEnv.getElementUtils().getElementValuesWithDefaults(a);
        AnnotationValue v = members.get(hostValueElement);
        if (v != null) {
            assert v.getValue() instanceof TypeMirror;
            TypeMirror value = (TypeMirror) v.getValue();
            if (processingEnv.getTypeUtils().isSameType(value, host.asType())) {
                return (String) members.get(hostTypeNameElement).getValue();
            } else {
                return getInternalName(value.toString());
            }
        } else {
            System.err.println("value() member of @Host annotation not found");
        }
        throw new IllegalArgumentException();
    }

    private static String getArraySubstring(int nbDim) {
        char[] chars = new char[nbDim];
        Arrays.fill(chars, '[');
        return new String(chars);
    }

    /**
     * Given a qualified class name, returns the fully qualified internal name of the class.
     * 
     * In particular,
     * <li>This transforms primitives (boolean, int) to their JVM signature (Z, I).
     * <li>Replaces "." by "/" and, if not present, prepends a "L" an appends a ";" to reference
     * types (/ex: java.lang.Object -> Ljava/lang/Object;)
     * <li>If an array is passed, it is of the form "java.lang.Object[]" or "byte[][]". This
     * prepends the correct number of "[" before applying this function to the name without the
     * "[]". (/ex: byte[][] -> [[B)
     */
    private String getInternalName(String className) {
        int arrayStart = className.indexOf("[]");
        if (arrayStart != -1) {
            int nbDim = 0;
            boolean isOpen = false;
            for (int i = arrayStart; i < className.length(); i++) {
                if (isOpen) {
                    if (className.charAt(i) != ']') {
                        throw new IllegalArgumentException("Malformed class name: " + className);
                    }
                    nbDim++;
                } else {
                    if (className.charAt(i) != '[') {
                        throw new IllegalArgumentException("Malformed class name: " + className);
                    }
                }
                isOpen = !isOpen;
            }
            return getArraySubstring(nbDim) + getInternalName(className.substring(0, arrayStart));
        }

        if (className.startsWith("[") || className.endsWith(";")) {
            return className.replace('.', '/');
        }
        switch (className) {
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "short":
                return "S";
            case "int":
                return "I";
            case "float":
                return "F";
            case "double":
                return "D";
            case "long":
                return "J";
            case "void":
                return "V";
        }
        // Reference type.
        return "L" + className.replace('.', '/') + ";";
    }

    private static AnnotationMirror getAnnotation(TypeMirror e, TypeElement type) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement().equals(type)) {
                return annotationMirror;
            }
        }
        return null;
    }

    private static AnnotationMirror getAnnotation(Element e, TypeElement type) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement().equals(type)) {
                return annotationMirror;
            }
        }
        return null;
    }

    // Commits the collector.
    private void commitFiles() {
        try {
            collector.append(TAB_1).append("}\n}");
            JavaFileObject file = processingEnv.getFiler().createSourceFile(getSubstitutorQualifiedName(COLLECTOR));
            Writer wr = file.openWriter();
            wr.write(collector.toString());
            wr.close();
        } catch (IOException ex) {
            /* nop */
        }
    }

    private static List<String> getEspressoTypes(ExecutableElement inner) {
        ArrayList<String> parameterTypeNames = new ArrayList<>();
        for (VariableElement parameter : inner.getParameters()) {
            String arg = parameter.asType().toString();
            int beginIndex = arg.lastIndexOf('.');
            if (beginIndex >= 0) {
                if (arg.charAt(arg.length() - 1) == ')') {
                    parameterTypeNames.add(arg.substring(beginIndex + 1, arg.length() - 1));
                } else {
                    parameterTypeNames.add(arg.substring(beginIndex + 1));
                }
            } else {
                parameterTypeNames.add(arg);
            }
        }
        return parameterTypeNames;
    }

    public static String spawnSubstitutor(String className, String targetMethodName, String actualMethodName, List<String> guestTypeNames, List<String> parameterTypeName, String returnType,
                    boolean hasReceiver) {
        String substitutorName = getSubstitutorClassName(className, targetMethodName, parameterTypeName);
        StringBuilder classFile = new StringBuilder();
        // Header
        classFile.append(COPYRIGHT);
        classFile.append(GENERATED_BY).append(className).append("\n\n");
        classFile.append(PACKAGE);

        if (parameterTypeName.contains("StaticObject")) {
            classFile.append(IMPORT_STATIC_OBJECT);
        }

        // Class
        classFile.append(PUBLIC_FINAL_CLASS).append(substitutorName).append(EXTENSION);
        classFile.append(generateInstance(substitutorName, INSTANCE_NAME, SUBSTITUTOR)).append("\n");
        classFile.append(generateConstructor(substitutorName, className, actualMethodName, returnType, guestTypeNames, hasReceiver)).append("\n");
        classFile.append(generateGetter(INSTANCE_NAME, SUBSTITUTOR, GETTER)).append("\n");
        classFile.append(TAB_1).append(OVERRIDE).append("\n");

        // Substitution
        classFile.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        int argIndex = 0;
        for (String argType : parameterTypeName) {
            classFile.append(extractArg(argIndex++, argType, TAB_2));
        }
        if (returnType.equals("V")) {
            classFile.append(TAB_2).append(extractInvocation(className, targetMethodName, argIndex));
            classFile.append(TAB_2).append("return null;\n");
        } else {
            classFile.append(TAB_2).append("return ").append(extractInvocation(className, targetMethodName, argIndex));
        }
        classFile.append(TAB_1).append("}\n");
        classFile.append("}");
        // End
        return classFile.toString();
    }

}
