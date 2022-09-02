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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for creating all kinds of Substitution processor in Espresso. A processor need only
 * implement its own process method, along with providing three strings:
 * <li>The import sequence for the class generated.
 * <li>The constructor code for the class generated.
 * <li>The invoke method code for the class generated.
 * <p>
 * <p>
 * All other aspects of code generation are provided by this class.
 */
public abstract class EspressoProcessor extends AbstractProcessor {
    /**
     * Does the actual work of the processor. The pattern used in espresso is:
     * <li>Initialize the {@link TypeElement} of the annotations that will be used, along with their
     * {@link AnnotationValue}, as necessary.
     * <li>Iterate over all methods annotated with what was returned by
     * {@link Processor#getSupportedAnnotationTypes()}, and process them so that each one spawns a
     * class.
     * 
     * @see EspressoProcessor#commitSubstitution(Element, String, String)
     */
    abstract void processImpl(RoundEnvironment roundEnvironment);

    /**
     * Generates the string corresponding to the imports of the current substitutor.
     * <p>
     * Note that the required imports vary between classes, as some might not be used, triggering
     * style issues, which is why this is delegated.
     * 
     * @see EspressoProcessor#IMPORT_INTEROP_LIBRARY
     * @see EspressoProcessor#IMPORT_STATIC_OBJECT
     * @see EspressoProcessor#IMPORT_TRUFFLE_OBJECT
     */
    abstract String generateImports(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper);

    /**
     * Generates the string corresponding to the Constructor for the current substitutor. In
     * particular, it should call its super class substitutor's constructor.
     * 
     * @see EspressoProcessor#SUBSTITUTOR
     */
    abstract String generateConstructor(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper);

    /**
     * Generates th string that corresponds to the code of the invoke method for the current
     * substitutor. Care must be taken to correctly unwrap and cast the given arguments (given in an
     * Object[]) so that they correspond to the substituted method's signature. Furthermore, all
     * TruffleObject nulls must be replaced with Espresso nulls (Null check can be done through
     * truffle libraries).
     * 
     * @see EspressoProcessor#castTo(String, String)
     * @see EspressoProcessor#IMPORT_INTEROP_LIBRARY
     * @see EspressoProcessor#FACTORY_IS_NULL
     * @see EspressoProcessor#STATIC_OBJECT_NULL
     */
    abstract String generateInvoke(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper);

    EspressoProcessor(String SUBSTITUTION_PACKAGE, String SUBSTITUTOR, String COLLECTOR, String COLLECTOR_INSTANCE_NAME) {
        this.SUBSTITUTION_PACKAGE = SUBSTITUTION_PACKAGE;
        this.SUBSTITUTOR = SUBSTITUTOR;
        this.COLLECTOR = COLLECTOR;
        this.COLLECTOR_INSTANCE_NAME = COLLECTOR_INSTANCE_NAME;
        this.PACKAGE = "package " + SUBSTITUTION_PACKAGE + ";\n\n";
        this.EXTENSION = " extends " + SUBSTITUTOR + " {\n";
        this.IMPORTS_COLLECTOR = PACKAGE +
                        "\n" +
                        "import java.util.ArrayList;\n" +
                        "import java.util.List;\n";
    }

    // Instance specific constants
    private final String SUBSTITUTION_PACKAGE;
    private final String SUBSTITUTOR;
    private final String COLLECTOR;
    private final String COLLECTOR_INSTANCE_NAME;
    private final String PACKAGE;
    private final String EXTENSION;
    private final String IMPORTS_COLLECTOR;

    // Processor local info
    protected boolean done = false;
    protected HashSet<String> classes = new HashSet<>();
    protected StringBuilder collector = null;

    // Global constants
    private static final String INSTANCE_NAME = "theInstance";
    private static final String GETTER = "getInstance";

    private static final String COPYRIGHT = "/* Copyright (c) 2019, 2019 Oracle and/or its affiliates. All rights reserved.\n" +
                    " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                    " *\n" +
                    " * This code is free software; you can redistribute it and/or modify it\n" +
                    " * under the terms of the GNU General Public License version 2 only, as\n" +
                    " * published by the Free Software Foundation.\n" +
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

    private static final String GENERATED_BY = "Generated by: ";
    private static final String AT_LINK = "@link ";
    private static final String FACTORY_IS_NULL = "InteropLibrary.getFactory().getUncached().isNull";
    private static final String PRIVATE_STATIC_FINAL = "private static final";
    private static final String PUBLIC_STATIC_FINAL = "public static final";
    private static final String PUBLIC_FINAL_CLASS = "public final class ";
    private static final String OVERRIDE = "@Override";

    static final String STATIC_OBJECT_NULL = "StaticObject.NULL";

    static final String IMPORT_INTEROP_LIBRARY = "import com.oracle.truffle.api.interop.InteropLibrary;\n";
    static final String IMPORT_STATIC_OBJECT = "import com.oracle.truffle.espresso.runtime.StaticObject;\n";
    static final String IMPORT_TRUFFLE_OBJECT = "import com.oracle.truffle.api.interop.TruffleObject;\n";

    static final String PUBLIC_FINAL_OBJECT = "public final Object ";
    static final String ARGS_NAME = "args";
    static final String ARG_NAME = "arg";
    static final String TAB_1 = "    ";
    static final String TAB_2 = TAB_1 + TAB_1;
    static final String TAB_3 = TAB_2 + TAB_1;

    private static final Map<String, NativeSimpleType> classToNative = buildClassToNative();

    static Map<String, NativeSimpleType> buildClassToNative() {
        Map<String, NativeSimpleType> map = new HashMap<>();
        map.put("boolean", NativeSimpleType.SINT8);
        map.put("byte", NativeSimpleType.SINT8);
        map.put("short", NativeSimpleType.SINT16);
        map.put("char", NativeSimpleType.SINT16);
        map.put("int", NativeSimpleType.SINT32);
        map.put("float", NativeSimpleType.FLOAT);
        map.put("long", NativeSimpleType.SINT64);
        map.put("double", NativeSimpleType.DOUBLE);
        map.put("void", NativeSimpleType.VOID);
        map.put("java.lang.String", NativeSimpleType.STRING);
        return Collections.unmodifiableMap(map);
    }

    public static NativeSimpleType classToType(String clazz) {
        // TODO(peterssen): Allow native-sized words.
        return classToNative.getOrDefault(clazz, NativeSimpleType.SINT64);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // Initialize the collector.
        initCollector();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (done) {
            return false;
        }
        processImpl(roundEnv);
        // We are done, push the collector.
        commitFiles();
        done = true;
        return false;
    }

    static AnnotationMirror getAnnotation(TypeMirror e, TypeElement type) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement().equals(type)) {
                return annotationMirror;
            }
        }
        return null;
    }

    static AnnotationMirror getAnnotation(Element e, TypeElement type) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement().equals(type)) {
                return annotationMirror;
            }
        }
        return null;
    }

    private void initCollector() {
        this.collector = new StringBuilder();
        collector.append(COPYRIGHT);
        collector.append(IMPORTS_COLLECTOR).append("\n");
        collector.append("// ").append(GENERATED_BY).append(SUBSTITUTOR).append("\n");
        collector.append(PUBLIC_FINAL_CLASS).append(COLLECTOR).append(" {\n");
        collector.append(generateInstance("ArrayList<>", COLLECTOR_INSTANCE_NAME, "List<" + SUBSTITUTOR + ">"));
        collector.append(TAB_1).append("private ").append(COLLECTOR).append("() {\n").append(TAB_1).append("}\n");
        collector.append(generateGetter(COLLECTOR_INSTANCE_NAME, "List<" + SUBSTITUTOR + ">", GETTER)).append("\n");
        collector.append(TAB_1).append("static {\n");
    }

    final String getSubstitutorQualifiedName(String substitutorName) {
        return SUBSTITUTION_PACKAGE + "." + substitutorName;
    }

    static StringBuilder signatureSuffixBuilder(List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append("_").append(parameterTypes.size());
        return str;
    }

    static String getSubstitutorClassName(String className, String methodName, List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append(className).append("_").append(methodName).append(signatureSuffixBuilder(parameterTypes));
        return str.toString();
    }

    void addSubstitutor(StringBuilder str, String substitutorName) {
        str.append(TAB_2).append(COLLECTOR_INSTANCE_NAME).append(".add(").append(substitutorName).append(".").append(GETTER).append("()").append(");\n");
    }

    static String castTo(String obj, String clazz) {
        if (clazz.equals("Object")) {
            return obj;
        }
        return "(" + clazz + ") " + obj;
    }

    static String genIsNull(String obj) {
        return FACTORY_IS_NULL + "(" + obj + ")";
    }

    static String extractSimpleType(String arg) {
        // The argument can be a fully qualified type e.g. java.lang.String, int, long...
        // Or an annotated type e.g. "(@com.example.Annotation :: long)",
        // "(@com.example.Annotation :: java.lang.String)".
        // javac always includes annotations, ecj does not.

        // Purge enclosing parentheses.
        String result = arg;
        if (result.startsWith("(")) {
            result = result.substring(1, result.length() - 1);
        }

        // Purge leading annotations.
        String[] parts = result.split("::");
        result = parts[parts.length - 1].trim();
        // Prune additional spaces produced by javac 11.
        parts = result.split(" ");
        result = parts[parts.length - 1].trim();

        // Get unqualified name.
        int beginIndex = result.lastIndexOf('.');
        if (beginIndex >= 0) {
            result = result.substring(beginIndex + 1);
        }
        return result;
    }

    static String extractReturnType(ExecutableElement method) {
        return extractSimpleType(method.getReturnType().toString());
    }

    // Commits a single substitution.
    void commitSubstitution(Element method, String substitutorName, String classFile) {
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

    // Commits the collector.
    void commitFiles() {
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

    static String generateGeneratedBy(String className, String targetMethodName, List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append("/**\n * ").append(GENERATED_BY).append("{").append(AT_LINK).append(className).append("#").append(targetMethodName).append("(");
        boolean first = true;
        for (String param : parameterTypes) {
            if (first) {
                first = false;
            } else {
                str.append(", ");
            }
            str.append(param);
        }
        str.append(")}\n */");
        return str.toString();
    }

    static String generateString(String str) {
        return "\"" + str + "\"";
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
    static String generateInstance(String substitutorName, String instanceName, String instanceClass) {
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
    static String generateGetter(String instanceName, String className, String getterName) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append(PUBLIC_STATIC_FINAL).append(" ").append(className).append(" ").append(getterName).append("() {\n");
        str.append(TAB_2).append("return ").append(instanceName).append(";\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    /**
     * Creates the substitutor.
     * 
     * @param className The name of the class where the substituted method is found.
     * @param targetMethodName The name of the substituted method.
     * @param parameterTypeName The list of *Host* parameter types of the substituted method.
     * @param helper A helper structure.
     * @return The string forming the substitutor.
     */
    String spawnSubstitutor(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        String substitutorName = getSubstitutorClassName(className, targetMethodName, parameterTypeName);
        StringBuilder classFile = new StringBuilder();
        // Header
        classFile.append(COPYRIGHT);
        classFile.append(PACKAGE);
        classFile.append(generateImports(substitutorName, targetMethodName, parameterTypeName, helper));

        // Class
        classFile.append(generateGeneratedBy(className, targetMethodName, parameterTypeName)).append("\n");
        classFile.append(PUBLIC_FINAL_CLASS).append(substitutorName).append(EXTENSION);

        // Instance
        classFile.append(generateInstance(substitutorName, INSTANCE_NAME, SUBSTITUTOR)).append("\n");

        // Constructor
        classFile.append(generateConstructor(substitutorName, targetMethodName, parameterTypeName, helper)).append("\n");

        // Getter
        classFile.append(generateGetter(INSTANCE_NAME, SUBSTITUTOR, GETTER)).append("\n");

        // Invoke method
        classFile.append(TAB_1).append(OVERRIDE).append("\n");
        classFile.append(generateInvoke(className, targetMethodName, parameterTypeName, helper));

        // End
        return classFile.toString();
    }
}
