/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

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
public abstract class EspressoProcessor extends BaseProcessor {
    // @formatter:off
    /* An example of a generated class is:
     *
     * /* Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
     *  * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
     *  *
     *  * This code is free software; you can redistribute it and/or modify it
     *  * under the terms of the GNU General Public License version 2 only, as
     *  * published by the Free Software Foundation.
     *  *
     *  * This code is distributed in the hope that it will be useful, but WITHOUT
     *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
     *  * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
     *  * version 2 for more details (a copy is included in the LICENSE file that
     *  * accompanied this code).
     *  *
     *  * You should have received a copy of the GNU General Public License version
     *  * 2 along with this work; if not, write to the Free Software Foundation,
     *  * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
     *  *
     *  * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
     *  * or visit www.oracle.com if you need additional information or have any
     *  * questions.
     *  * /
     *
     * package com.oracle.truffle.espresso.substitutions;
     *
     * import com.oracle.truffle.espresso.meta.Meta;
     * import com.oracle.truffle.espresso.substitutions.Collect;
     *
     * import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
     * import com.oracle.truffle.espresso.runtime.StaticObject;
     * import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Resolve;
     *
     * /**
     *  * Generated by: {@link com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Resolve}
     *  * /
     * public final class Target_java_lang_invoke_MethodHandleNatives_Resolve_2 extends JavaSubstitution {
     *
     *     @Collect(com.oracle.truffle.espresso.substitutions.Substitution.class)
     *     public static final class Factory extends JavaSubstitution.Factory {
     *         public Factory() {
     *             super(
     *                 "resolve",
     *                 "Target_java_lang_invoke_MethodHandleNatives",
     *                 "Ljava/lang/invoke/MemberName;",
     *                 new String[]{
     *                     "Ljava/lang/invoke/MemberName;",
     *                     "Ljava/lang/Class;"
     *                 },
     *                 false
     *             );
     *         }
     *
     *         @Override
     *         public final JavaSubstitution create(Meta meta) {
     *             return new Target_java_lang_invoke_MethodHandleNatives_Resolve_2(meta);
     *         }
     *     }
     *
     *     private @Child Resolve node;
     *
     *     @SuppressWarnings("unused")
     *     private Target_java_lang_invoke_MethodHandleNatives_Resolve_2(Meta meta) {
     *         this.node = com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNativesFactory.ResolveNodeGen.create();
     *     }
     *
     *     @Override
     *     public final Object invoke(Object[] args) {
     *         StaticObject arg0 = (StaticObject) args[0];
     *         StaticObject arg1 = (StaticObject) args[1];
     *         return this.node.execute(arg0, arg1);
     *     }
     * }
     */
    // @formatter:on

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
    abstract String generateFactoryConstructorAndBody(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper);

    /**
     * Generates the string that corresponds to the code of the invoke method for the current
     * substitutor. Care must be taken to correctly unwrap and cast the given arguments (given in an
     * Object[]) so that they correspond to the substituted method's signature. Furthermore, all
     * TruffleObject nulls must be replaced with Espresso nulls (Null check can be done through
     * truffle libraries).
     *
     * @see EspressoProcessor#castTo(String, String)
     * @see EspressoProcessor#IMPORT_INTEROP_LIBRARY
     * @see EspressoProcessor#STATIC_OBJECT_NULL
     */
    abstract String generateInvoke(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper);

    EspressoProcessor(String SUBSTITUTION_PACKAGE, String SUBSTITUTOR) {
        this.SUBSTITUTOR_PACKAGE = SUBSTITUTION_PACKAGE;
        this.SUBSTITUTOR = SUBSTITUTOR;
    }

    // Instance specific constants
    protected final String SUBSTITUTOR_PACKAGE;
    private final String SUBSTITUTOR;

    // Processor local info
    private String collectorPackage;
    protected boolean done = false;
    protected HashSet<String> classes = null;

    // Special annotations
    TypeElement injectMeta;
    private static final String INJECT_META = "com.oracle.truffle.espresso.substitutions.InjectMeta";

    TypeElement injectProfile;
    private static final String INJECT_PROFILE = "com.oracle.truffle.espresso.substitutions.InjectProfile";

    // Global constants
    private static final String FACTORY = "Factory";

    static final String COPYRIGHT = "/* Copyright (c) " + Year.now() + " Oracle and/or its affiliates. All rights reserved.\n" +
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

    static final String GENERATED_BY = "Generated by: ";
    static final String AT_LINK = "@link ";
    private static final String PRIVATE_FINAL = "private final";
    private static final String PRIVATE = "private";
    static final String PUBLIC_STATIC_FINAL_CLASS = "public static final class ";
    static final String PUBLIC_FINAL_CLASS = "public final class ";
    static final String SUPPRESS_UNUSED = "@SuppressWarnings(\"unused\")";

    static final String PUBLIC_FINAL = "public final";
    static final String OVERRIDE = "@Override";

    static final String STATIC_OBJECT_NULL = "StaticObject.NULL";

    static final String IMPORT_INTEROP_LIBRARY = "import com.oracle.truffle.api.interop.InteropLibrary;\n";
    static final String IMPORT_STATIC_OBJECT = "import com.oracle.truffle.espresso.runtime.StaticObject;\n";
    static final String IMPORT_TRUFFLE_OBJECT = "import com.oracle.truffle.api.interop.TruffleObject;\n";
    static final String IMPORT_META = "import com.oracle.truffle.espresso.meta.Meta;\n";
    static final String IMPORT_PROFILE = "import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;\n";
    static final String IMPORT_COLLECT = "import com.oracle.truffle.espresso.substitutions.Collect;\n";

    static final String META_CLASS = "Meta ";
    static final String META_VAR = "meta";
    static final String META_ARG = META_CLASS + META_VAR;

    static final String PROFILE_CLASS = "SubstitutionProfiler ";
    static final String PROFILE_ARG_CALL = "this";

    private static final String SET_META = "this." + META_VAR + " = " + META_VAR + ";";

    static final String CREATE = "create";

    static final String SHOULD_SPLIT = "shouldSplit";
    static final String SPLIT = "split";

    static final String PUBLIC_FINAL_OBJECT = "public final Object ";
    static final String ARGS_NAME = "args";
    static final String ARG_NAME = "arg";
    static final String TAB_1 = "    ";
    static final String TAB_2 = TAB_1 + TAB_1;
    static final String TAB_3 = TAB_2 + TAB_1;
    static final String TAB_4 = TAB_3 + TAB_1;

    public static NativeType classToType(TypeKind typeKind) {
        // @formatter:off
        switch (typeKind) {
            case BOOLEAN : return NativeType.BOOLEAN;
            case BYTE    : return NativeType.BYTE;
            case SHORT   : return NativeType.SHORT;
            case CHAR    : return NativeType.CHAR;
            case INT     : return NativeType.INT;
            case FLOAT   : return NativeType.FLOAT;
            case LONG    : return NativeType.LONG;
            case DOUBLE  : return NativeType.DOUBLE;
            case VOID    : return NativeType.VOID;
            default:
                return NativeType.OBJECT;
        }
        // @formatter:on
    }

    /**
     * Returns the name of the substituted method.
     */
    protected String getSubstutitutedMethodName(Element targetElement) {
        return targetElement.getSimpleName().toString();
    }

    /**
     * Returns the target method to be called by a substitution.
     *
     * Returns the targetElement itself for method substitutions; or the execute* method of the
     * Truffle node, for node substitutions.
     */
    protected ExecutableElement getTargetMethod(Element targetElement) {
        if (targetElement.getKind() == ElementKind.CLASS) {
            return findNodeExecute((TypeElement) targetElement);
        }
        return (ExecutableElement) targetElement;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (done) {
            return false;
        }
        injectMeta = getTypeElement(INJECT_META);
        injectProfile = getTypeElement(INJECT_PROFILE);
        processImpl(roundEnv);
        done = true;
        return false;
    }

    // Utility Methods

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

    boolean hasMetaInjection(ExecutableElement method) {
        List<? extends VariableElement> params = method.getParameters();
        for (VariableElement e : params) {
            if (getAnnotation(e.asType(), injectMeta) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * For substitutions that use a node, find the execute* (abstract) method.
     */
    ExecutableElement findNodeExecute(TypeElement node) {
        for (Element method : node.getEnclosedElements()) {
            if (method.getKind() == ElementKind.METHOD) {
                if (method.getModifiers().contains(Modifier.ABSTRACT)) {
                    return (ExecutableElement) method;
                }
            }
        }
        getMessager().printMessage(Diagnostic.Kind.ERROR, "Node abstract execute* method not found", node);
        return null;
    }

    boolean hasProfileInjection(ExecutableElement method) {
        List<? extends VariableElement> params = method.getParameters();
        for (VariableElement e : params) {
            if (getAnnotation(e.asType(), injectProfile) != null) {
                return true;
            }
        }
        return false;
    }

    boolean isActualParameter(VariableElement param) {
        boolean b2 = getAnnotation(param.asType(), injectMeta) == null;
        boolean b3 = getAnnotation(param.asType(), injectProfile) == null;
        return b2 && b3;
    }

    static boolean checkFirst(StringBuilder str, boolean first) {
        if (!first) {
            str.append(", ");
        }
        return false;
    }

    private String getSubstitutorQualifiedName(String substitutorName) {
        assert collectorPackage != null;
        return collectorPackage + "." + substitutorName;
    }

    private static StringBuilder signatureSuffixBuilder(List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append("_").append(parameterTypes.size());
        return str;
    }

    static String getSubstitutorClassName(String className, String methodName, List<String> parameterTypes) {
        return String.format("%s_%s%s", className, methodName, signatureSuffixBuilder(parameterTypes));
    }

    static String castTo(String obj, String clazz) {
        if (clazz.equals("Object")) {
            return obj;
        }
        return "(" + clazz + ") " + obj;
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

    /**
     * Injects the meta information in the substitution call.
     */
    static boolean appendInvocationMetaInformation(StringBuilder str, boolean first, SubstitutionHelper helper) {
        boolean f = first;
        if (helper.hasMetaInjection) {
            f = injectMeta(str, f);
        }
        if (helper.hasProfileInjection) {
            f = injectProfile(str, f);
        }
        return f;
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
        } catch (IOException ex) {
            /* nop */
        }
    }

    // @formatter:off
    /**
     * Setups the collector closure. If a closure had already been set, flush it.
     *
     * generates:
     *
     * COPYRIGHT
     *
     * PACKAGE + IMPORTS
     *
     * // Generated by: SUBSTITUTOR
     * public final class COLLECTOR_NAME {
     *     private static final List<SUBSTITUTOR.Factory> COLLECTOR_INSTANCE_NAME = new ArrayList<>();
     *     private COLLECTOR_NAME() {
     *     }
     *     public static final List<SUBSTITUTOR.Factory> getCollector() {
     *         return COLLECTOR_INSTANCE_NAME;
     *     }
     *
     *     static {
     */
    // @formatter:on
    protected final void initCollector(String collectorPkg) {
        this.collectorPackage = collectorPkg;
        this.classes = new HashSet<>();
    }

    @SuppressWarnings("unused")
    private static String generateGeneratedBy(String className, String targetMethodName, List<String> parameterTypes, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        if (helper.isNodeTarget()) {
            str.append("/**\n * ").append(GENERATED_BY).append("{").append(AT_LINK).append(helper.getNodeTarget().getQualifiedName()).append("}\n */");
            return str.toString();
        } else {
            str.append("/**\n * ").append(GENERATED_BY).append("{").append(AT_LINK).append(className).append("#").append(targetMethodName).append("(");
        }
        boolean first = true;
        for (String param : parameterTypes) {
            first = checkFirst(str, first);
            str.append(param);
        }
        if (helper.hasMetaInjection) {
            first = checkFirst(str, first);
            str.append(META_CLASS);
        }
        if (helper.hasProfileInjection) {
            first = checkFirst(str, first);
            str.append(PROFILE_CLASS);
        }
        str.append(")}\n */");
        return str.toString();
    }

    static String generateNativeSignature(NativeType[] signature) {
        StringBuilder sb = new StringBuilder();
        sb.append("NativeSignature.create(NativeType.").append(signature[0]);
        for (int i = 1; i < signature.length; ++i) {
            sb.append(", NativeType.").append(signature[i]);
        }
        sb.append(")");
        return sb.toString();
    }

    // @formatter:off
    /**
     * Generate the following:
     * 
     * private static final Factory factory = new Factory();
     * 
     *     public static final class Factory extends SUBSTITUTOR.Factory {
     *         private Factory() {
     *             super(
     *                 "SUBSTITUTED_METHOD",
     *                 "SUBSTITUTION_CLASS",
     *                 "RETURN_TYPE",
     *                 new String[]{
     *                     SIGNATURE
     *                 },
     *                 HAS_RECEIVER
     *             );
     *         }
     * 
     *         @Override
     *         public final SUBSTITUTOR create(Meta meta) {
     *             return new className(meta);
     *         }
     *     }
     */
    // @formatter:on
    private String generateFactory(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        str.append("\n");
        str.append(TAB_1).append("@Collect(").append(helper.getImplAnnotation().getQualifiedName()).append(".class").append(")\n");
        str.append(TAB_1).append(PUBLIC_STATIC_FINAL_CLASS).append(FACTORY).append(" extends ").append(SUBSTITUTOR).append(".").append(FACTORY).append(" {\n");
        str.append(TAB_2).append("public ").append(FACTORY).append("() {\n");
        str.append(generateFactoryConstructorAndBody(className, targetMethodName, parameterTypeName, helper)).append("\n");
        str.append(TAB_2).append(OVERRIDE).append("\n");
        str.append(TAB_2).append(PUBLIC_FINAL).append(" ").append(SUBSTITUTOR).append(" ").append(CREATE).append("(");
        str.append(META_CLASS).append(META_VAR).append(") {\n");
        str.append(TAB_3).append("return new ").append(className).append("(").append(META_VAR).append(");\n");
        str.append(TAB_2).append("}\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    /**
     * Injects meta data in the substitutor's field, so the Meta be passed along
     * during substitution invocation.
     */
    static private String generateInstanceFields(SubstitutionHelper helper) {
        if (!helper.isNodeTarget() && !helper.hasMetaInjection && !helper.hasProfileInjection) {
            return "";
        }
        StringBuilder str = new StringBuilder();
        if (helper.hasMetaInjection || helper.hasProfileInjection) {
            str.append(TAB_1).append(PRIVATE_FINAL).append(" ").append(META_ARG).append(";\n");
        }
        if (helper.isNodeTarget()) {
            str.append(TAB_1).append(PRIVATE).append(" @Child ").append(helper.getNodeTarget().getSimpleName()).append(" ").append("node").append(";\n");
        }
        str.append('\n');
        return str.toString();
    }

    /**
     * Generates the constructor for the substitutor.
     */
    private static String generateConstructor(String substitutorName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append("private ").append(substitutorName).append("(").append(META_ARG).append(") {\n");
        if (helper.hasMetaInjection || helper.hasProfileInjection) {
            str.append(TAB_2).append(SET_META).append("\n");
        }
        if (helper.isNodeTarget()) {
            TypeElement enclosing = (TypeElement) helper.getNodeTarget().getEnclosingElement();
            str.append(TAB_2).append("this.node = " + enclosing.getQualifiedName() + "Factory." + helper.getNodeTarget().getSimpleName() + "NodeGen" + ".create();").append("\n");
        }
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    static boolean injectMeta(StringBuilder str, boolean first) {
        checkFirst(str, first);
        str.append(META_VAR);
        return false;
    }

    static boolean injectProfile(StringBuilder str, boolean first) {
        checkFirst(str, first);
        str.append(PROFILE_ARG_CALL);
        return false;
    }

    /**
     * Creates the substitutor.
     *
     *
     * @param className The name of the host class where the substituted method is found.
     * @param targetMethodName The name of the substituted method.
     * @param parameterTypeName The list of *Host* parameter types of the substituted method.
     * @param helper A helper structure.
     * @return The string forming the substitutor.
     */
    String spawnSubstitutor(String targetPackage, String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        String substitutorName = getSubstitutorClassName(className, targetMethodName, parameterTypeName);
        StringBuilder classFile = new StringBuilder();
        // Header
        classFile.append(COPYRIGHT);
        classFile.append("package " + targetPackage + ";\n\n");
        classFile.append(IMPORT_META);
        classFile.append(IMPORT_COLLECT).append("\n");
        classFile.append(generateImports(substitutorName, targetMethodName, parameterTypeName, helper));

        // Class
        classFile.append(generateGeneratedBy(className, targetMethodName, parameterTypeName, helper)).append("\n");
        classFile.append(PUBLIC_FINAL_CLASS).append(substitutorName).append(" extends " + SUBSTITUTOR + " {\n");

        // Instance Provider
        classFile.append(generateFactory(substitutorName, targetMethodName, parameterTypeName, helper)).append("\n");

        // Instance variables
        classFile.append(generateInstanceFields(helper));

        // Constructor
        classFile.append(TAB_1).append(SUPPRESS_UNUSED).append("\n");
        classFile.append(generateConstructor(substitutorName, helper)).append("\n");

        classFile.append(generateSplittingInformation(helper));

        classFile.append('\n');

        // Invoke method
        classFile.append(TAB_1).append(OVERRIDE).append("\n");
        classFile.append(generateInvoke(className, targetMethodName, parameterTypeName, helper));

        // End
        return classFile.toString();
    }

    /**
     * Injects overrides of 'shouldSplit()' and 'split()' methods.
     */
    private String generateSplittingInformation(SubstitutionHelper helper) {
        if (helper.hasProfileInjection) {
            StringBuilder str = new StringBuilder();
            // Splitting logic
            str.append('\n');
            str.append(TAB_1).append(OVERRIDE).append("\n");
            str.append(generateShouldSplit());

            str.append('\n');
            str.append(TAB_1).append(OVERRIDE).append("\n");
            str.append(generateSplit());
            return str.toString();
        }
        return "";
    }

    private static String generateShouldSplit() {
        StringBuilder str = new StringBuilder();

        str.append(TAB_1).append(PUBLIC_FINAL).append(" boolean ").append(SHOULD_SPLIT).append("() {\n");
        str.append(TAB_2).append("return true;\n");
        str.append(TAB_1).append("}\n");

        return str.toString();
    }

    private String generateSplit() {
        StringBuilder str = new StringBuilder();

        str.append(TAB_1).append(PUBLIC_FINAL).append(" ").append(SUBSTITUTOR).append(" ").append(SPLIT).append("() {\n");
        str.append(TAB_2).append("return new ").append(FACTORY).append("()").append(".").append(CREATE).append("(").append(META_VAR).append(");\n");
        str.append(TAB_1).append("}\n");

        return str.toString();
    }

    public Messager getMessager() {
        return processingEnv.getMessager();
    }
}
