package com.oracle.truffle.espresso.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ReferenceType;
import java.util.List;

public abstract class IntrinsicsProcessor extends EspressoProcessor {
    static final String JNI_PACKAGE = "com.oracle.truffle.espresso.jni";
    private static final String NFI_TYPE = JNI_PACKAGE + "." + "NFIType";

    private final String ENV_NAME;
    // @NFIType
    TypeElement nfiType;

    // @NFIType.value()
    ExecutableElement nfiTypeValueElement;

    public IntrinsicsProcessor(String ENV_NAME, String SUBSTITUTION_PACKAGE, String SUBSTITUTOR, String COLLECTOR, String COLLECTOR_INSTANCE_NAME) {
        super(SUBSTITUTION_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
        this.ENV_NAME = ENV_NAME;
    }

    void initNfiType() {
        this.nfiType = processingEnv.getElementUtils().getTypeElement(NFI_TYPE);
        for (Element e : nfiType.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("value")) {
                    this.nfiTypeValueElement = (ExecutableElement) e;
                }
            }
        }
    }

    static void getEspressoTypes(ExecutableElement inner, List<String> parameterTypeNames, List<Boolean> referenceTypes) {
        for (VariableElement parameter : inner.getParameters()) {
            String arg = parameter.asType().toString();
            String result = extractSimpleType(arg);
            parameterTypeNames.add(result);
            referenceTypes.add((parameter.asType() instanceof ReferenceType));
        }
    }

    String jniNativeSignature(ExecutableElement method, String returnType, boolean isJni) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        boolean first = true;
        if (isJni) {
            sb.append(NativeSimpleType.SINT64);
            first = false;
        }
        for (VariableElement param : method.getParameters()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            // Override NFI type.
            AnnotationMirror nfi = getAnnotation(param.asType(), nfiType);
            if (nfi != null) {
                AnnotationValue value = nfi.getElementValues().get(nfiTypeValueElement);
                if (value != null) {
                    sb.append(NativeSimpleType.valueOf(((String) value.getValue()).toUpperCase()));
                } else {
                    sb.append(classToType(param.asType().toString(), false));
                }
            } else {
                sb.append(classToType(param.asType().toString(), false));
            }
        }
        sb.append("): ").append(classToType(returnType, true));
        return sb.toString();
    }

    static String extractArg(int index, String clazz, boolean isNonPrimitive, int startAt, String tabulation) {
        String decl = tabulation + clazz + " " + ARG_NAME + index + " = ";
        String obj = ARGS_NAME + "[" + (index + startAt) + "]";
        if (isNonPrimitive) {
            return decl + genIsNull(obj) + " ? " + (clazz.equals("StaticObject") ? STATIC_OBJECT_NULL : "null") + " : " + castTo(obj, clazz) + ";\n";
        }
        switch (clazz) {
            case "boolean":
                return decl + "(" + castTo(obj, "byte") + ") != 0;\n";
            case "char":
                return decl + castTo(castTo(obj, "short"), "char") + ";\n";
            default:
                return decl + castTo(obj, clazz) + ";\n";
        }
    }

    String extractInvocation(String className, String methodName, int nParameters, boolean isStatic) {
        StringBuilder str = new StringBuilder();
        if (isStatic) {
            str.append(className).append(".").append(methodName).append("(");
        } else {
            str.append(ENV_NAME).append(".").append(methodName).append("(");
        }
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

}
