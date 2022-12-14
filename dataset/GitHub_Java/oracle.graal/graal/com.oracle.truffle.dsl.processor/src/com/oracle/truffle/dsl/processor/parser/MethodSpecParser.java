/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

public final class MethodSpecParser {

    private boolean emitErrors = true;
    private boolean useVarArgs = false;

    private final Template template;

    public MethodSpecParser(Template template) {
        this.template = template;
    }

    public Template getTemplate() {
        return template;
    }

    public boolean isEmitErrors() {
        return emitErrors;
    }

    public boolean isUseVarArgs() {
        return useVarArgs;
    }

    public void setEmitErrors(boolean emitErrors) {
        this.emitErrors = emitErrors;
    }

    public void setUseVarArgs(boolean useVarArgs) {
        this.useVarArgs = useVarArgs;
    }

    public TemplateMethod parse(MethodSpec methodSpecification, ExecutableElement method, AnnotationMirror annotation, int naturalOrder) {
        if (methodSpecification == null) {
            return null;
        }

        methodSpecification.applyTypeDefinitions("types");

        String id = method.getSimpleName().toString();
        TypeMirror returnType = method.getReturnType();
        return parseImpl(methodSpecification, naturalOrder, id, method, annotation, returnType, method.getParameters());
    }

    public TemplateMethod parseImpl(MethodSpec methodSpecification, int naturalOrder, String id, ExecutableElement method, AnnotationMirror annotation, TypeMirror returnType,
                    List<? extends VariableElement> parameterTypes) {
        ParameterSpec returnTypeSpec = methodSpecification.getReturnType();
        Parameter returnTypeMirror = matchParameter(returnTypeSpec, new CodeVariableElement(returnType, "returnType"), -1, -1);
        if (returnTypeMirror == null) {
            if (isEmitErrors() && method != null) {
                TemplateMethod invalidMethod = new TemplateMethod(id, naturalOrder, template, methodSpecification, method, annotation, returnTypeMirror, Collections.<Parameter> emptyList());
                String expectedReturnType = returnTypeSpec.toSignatureString(true);
                String actualReturnType = ElementUtils.getSimpleName(returnType);

                String message = String.format("The provided return type \"%s\" does not match expected return type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                                methodSpecification.toSignatureString(method.getSimpleName().toString()));
                invalidMethod.addError(message);
                return invalidMethod;
            } else {
                return null;
            }
        }

        List<Parameter> parameters = parseParameters(methodSpecification, parameterTypes, isUseVarArgs() && method != null ? method.isVarArgs() : false);
        if (parameters == null) {
            if (isEmitErrors() && method != null) {
                TemplateMethod invalidMethod = new TemplateMethod(id, naturalOrder, template, methodSpecification, method, annotation, returnTypeMirror, Collections.<Parameter> emptyList());
                String message = String.format("Method signature %s does not match to the expected signature: \n%s", createActualSignature(method),
                                methodSpecification.toSignatureString(method.getSimpleName().toString()));
                invalidMethod.addError(message);
                return invalidMethod;
            } else {
                return null;
            }
        }

        return new TemplateMethod(id, naturalOrder, template, methodSpecification, method, annotation, returnTypeMirror, parameters);
    }

    private static String createActualSignature(ExecutableElement method) {
        StringBuilder b = new StringBuilder("(");
        String sep = "";
        if (method != null) {
            for (VariableElement var : method.getParameters()) {
                b.append(sep);
                b.append(ElementUtils.getSimpleName(var.asType()));
                sep = ", ";
            }
        }
        b.append(")");
        return b.toString();
    }

    /*
     * Parameter parsing tries to parse required arguments starting from offset 0 with increasing
     * offset until it finds a signature end that matches the required specification. If there is no
     * end matching the required arguments, parsing fails. Parameters prior to the parsed required
     * ones are cut and used to parse the optional parameters.
     */
    private static List<Parameter> parseParameters(MethodSpec spec, List<? extends VariableElement> parameterTypes, boolean varArgs) {
        List<Parameter> parsedRequired = null;
        int offset = 0;
        for (; offset <= parameterTypes.size(); offset++) {
            List<VariableElement> parameters = new ArrayList<>();
            parameters.addAll(parameterTypes.subList(offset, parameterTypes.size()));
            parsedRequired = parseParametersRequired(spec, parameters, varArgs);
            if (parsedRequired != null) {
                break;
            }
        }

        if (parsedRequired == null) {
            return null;
        }

        if (parsedRequired.isEmpty() && offset == 0) {
            offset = parameterTypes.size();
        }
        List<? extends VariableElement> potentialOptionals = parameterTypes.subList(0, offset);
        List<Parameter> parsedOptionals = parseParametersOptional(spec, potentialOptionals);
        if (parsedOptionals == null) {
            return null;
        }

        List<Parameter> finalParameters = new ArrayList<>();
        finalParameters.addAll(parsedOptionals);
        finalParameters.addAll(parsedRequired);
        return finalParameters;
    }

    private static List<Parameter> parseParametersOptional(MethodSpec spec, List<? extends VariableElement> types) {
        List<Parameter> parsedParams = new ArrayList<>();

        int typeStartIndex = 0;
        List<ParameterSpec> specifications = spec.getOptional();
        outer: for (int specIndex = 0; specIndex < specifications.size(); specIndex++) {
            ParameterSpec specification = specifications.get(specIndex);
            for (int typeIndex = typeStartIndex; typeIndex < types.size(); typeIndex++) {
                VariableElement variable = types.get(typeIndex);
                Parameter optionalParam = matchParameter(specification, variable, -1, -1);
                if (optionalParam != null) {
                    parsedParams.add(optionalParam);
                    typeStartIndex = typeIndex + 1;
                    continue outer;
                }
            }
        }

        if (typeStartIndex < types.size()) {
            // not enough types found
            return null;
        }
        return parsedParams;
    }

    private static List<Parameter> parseParametersRequired(MethodSpec spec, List<VariableElement> types, boolean typeVarArgs) {
        List<Parameter> parsedParams = new ArrayList<>();
        List<ParameterSpec> specifications = spec.getRequired();
        boolean specVarArgs = spec.isVariableRequiredParameters();
        int typeIndex = 0;
        int specificationIndex = 0;

        ParameterSpec specification;
        while ((specification = nextSpecification(specifications, specificationIndex, specVarArgs)) != null) {
            VariableElement actualType = nextActualType(types, typeIndex, typeVarArgs);
            if (actualType == null) {
                if (spec.isIgnoreAdditionalSpecifications()) {
                    break;
                }
                return null;
            }

            int typeVarArgsIndex = typeVarArgs ? typeIndex - types.size() + 1 : -1;
            int specVarArgsIndex = specVarArgs ? specificationIndex - specifications.size() + 1 : -1;

            if (typeVarArgsIndex >= 0 && specVarArgsIndex >= 0) {
                // both specifications and types have a variable number of arguments
                // we would get into an endless loop if we would continue
                break;
            }

            Parameter resolvedParameter = matchParameter(specification, actualType, specVarArgsIndex, typeVarArgsIndex);
            if (resolvedParameter == null) {
                return null;
            }
            parsedParams.add(resolvedParameter);
            typeIndex++;
            specificationIndex++;
        }

        // consume randomly ordered annotated parameters
        VariableElement variable;
        while ((variable = nextActualType(types, typeIndex, typeVarArgs)) != null) {
            Parameter matchedParamter = matchAnnotatedParameter(spec, variable);
            if (matchedParamter == null) {
                break;
            }
            parsedParams.add(matchedParamter);
            typeIndex++;
        }

        if (typeIndex < types.size()) {
            if (spec.isIgnoreAdditionalParameters()) {
                return parsedParams;
            } else {
                return null;
            }
        }

        return parsedParams;
    }

    private static Parameter matchAnnotatedParameter(MethodSpec spec, VariableElement variable) {
        for (ParameterSpec parameterSpec : spec.getAnnotations()) {
            if (parameterSpec.matches(variable)) {
                Parameter matchedParameter = matchParameter(parameterSpec, variable, -1, -1);
                if (matchedParameter != null) {
                    matchedParameter.setLocalName(variable.getSimpleName().toString());
                    return matchedParameter;
                }
            }
        }
        return null;
    }

    private static ParameterSpec nextSpecification(List<ParameterSpec> specifications, int specIndex, boolean varArgs) {
        if (varArgs && specIndex >= specifications.size() - 1 && !specifications.isEmpty()) {
            return specifications.get(specifications.size() - 1);
        } else if (specIndex < specifications.size()) {
            return specifications.get(specIndex);
        } else {
            return null;
        }
    }

    private static VariableElement nextActualType(List<VariableElement> types, int typeIndex, boolean varArgs) {
        if (varArgs && typeIndex >= types.size() - 1 && !types.isEmpty()) {
            // unpack varargs array argument
            VariableElement actualType = types.get(types.size() - 1);
            if (actualType.asType().getKind() == TypeKind.ARRAY) {
                actualType = new CodeVariableElement(((ArrayType) actualType.asType()).getComponentType(), actualType.getSimpleName().toString());
            }
            return actualType;
        } else if (typeIndex < types.size()) {
            return types.get(typeIndex);
        } else {
            return null;
        }
    }

    private static Parameter matchParameter(ParameterSpec specification, VariableElement variable, int specificationIndex, int varArgsIndex) {
        TypeMirror resolvedType = variable.asType();
        if (hasError(resolvedType)) {
            return null;
        }

        if (!specification.matches(variable)) {
            return null;
        }

        return new Parameter(specification, variable, specificationIndex, varArgsIndex);
    }
}
