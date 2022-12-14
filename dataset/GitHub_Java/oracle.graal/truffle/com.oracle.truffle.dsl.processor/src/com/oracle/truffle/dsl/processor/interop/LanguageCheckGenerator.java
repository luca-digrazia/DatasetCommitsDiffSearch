/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.dsl.processor.interop;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class LanguageCheckGenerator {
    protected static final String TEST_METHOD_NAME = "test";

    protected final TypeElement element;
    protected final String packageName;
    protected final String clazzName;
    protected final String userClassName;
    protected final String truffleLanguageFullClazzName;
    protected final ProcessingEnvironment processingEnv;

    LanguageCheckGenerator(ProcessingEnvironment processingEnv, MessageResolution messageResolutionAnnotation, TypeElement element) {
        this.processingEnv = processingEnv;
        this.element = element;
        this.packageName = ElementUtils.getPackageName(element);
        this.userClassName = ElementUtils.getQualifiedName(element);
        this.truffleLanguageFullClazzName = Utils.getTruffleLanguageFullClassName(messageResolutionAnnotation);
        this.clazzName = ElementUtils.getSimpleName(element) + "Sub";
    }

    public void generate() throws IOException {
        JavaFileObject file = processingEnv.getFiler().createSourceFile(packageName + "." + clazzName, element);
        Writer w = file.openWriter();
        w.append("package ").append(packageName).append(";\n");
        appendImports(w);

        w.append("abstract class ").append(clazzName).append(" extends ").append(userClassName).append(" {\n");
        appendExecuteWithTarget(w);
        appendSpecializations(w);

        appendRootNode(w);
        appendRootNodeFactory(w);

        w.append("}\n");
        w.close();
    }

    public List<ExecutableElement> getTestMethods() {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element m : element.getEnclosedElements()) {
            if (m.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!m.getSimpleName().contentEquals(TEST_METHOD_NAME)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) m;
            methods.add(method);
        }
        return methods;
    }

    static void appendImports(Writer w) throws IOException {
        w.append("import com.oracle.truffle.api.frame.VirtualFrame;").append("\n");
        w.append("import com.oracle.truffle.api.dsl.Specialization;").append("\n");
        w.append("import com.oracle.truffle.api.nodes.RootNode;").append("\n");
        w.append("import com.oracle.truffle.api.TruffleLanguage;").append("\n");
        w.append("import com.oracle.truffle.api.interop.ForeignAccess;").append("\n");
        w.append("import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;").append("\n");
        w.append("import com.oracle.truffle.api.interop.UnsupportedTypeException;").append("\n");
    }

    public String checkSignature(ExecutableElement method) {
        final List<? extends VariableElement> params = method.getParameters();
        boolean hasFrameArgument = false;
        if (params.size() >= 1) {
            hasFrameArgument = ElementUtils.typeEquals(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class));
        }
        int expectedNumberOfArguments = hasFrameArgument ? 2 : 1;

        if (!ElementUtils.typeEquals(params.get(hasFrameArgument ? 1 : 0).asType(), Utils.getTypeMirror(processingEnv, TruffleObject.class))) {
            return "The receiver type must be TruffleObject";
        }

        if (!ElementUtils.isPrimitive(method.getReturnType()) || method.getReturnType().getKind() != TypeKind.BOOLEAN) {
            return "Method must return a boolean value";
        }

        if (params.size() != expectedNumberOfArguments) {
            return "Wrong number of arguments.";
        }
        return null;
    }

    static void appendExecuteWithTarget(Writer w) throws IOException {
        w.append("    public abstract Object executeWithTarget(VirtualFrame frame, ").append("Object ").append("o").append(");\n");
    }

    void appendSpecializations(Writer w) throws IOException {
        String sep = "";
        List<ExecutableElement> testMethods = getTestMethods();
        assert testMethods.size() == 1;

        final List<? extends VariableElement> params = testMethods.get(0).getParameters();

        w.append("    @Specialization\n");
        w.append("    protected Object ").append(TEST_METHOD_NAME).append("WithTarget");
        w.append("(");

        sep = "";
        for (VariableElement p : params) {
            w.append(sep).append(ElementUtils.getUniqueIdentifier(p.asType())).append(" ").append(p.getSimpleName());
            sep = ", ";
        }
        w.append(") {\n");
        w.append("        return ").append(TEST_METHOD_NAME).append("(");
        sep = "";
        for (VariableElement p : params) {
            w.append(sep).append(p.getSimpleName());
            sep = ", ";
        }
        w.append(");\n");
        w.append("    }\n");
    }

    void appendRootNode(Writer w) throws IOException {
        w.append("    private static final class LanguageCheckRootNode extends RootNode {\n");
        w.append("        protected LanguageCheckRootNode(Class<? extends TruffleLanguage<?>> language) {\n");
        w.append("            super(language, null, null);\n");
        w.append("        }\n");
        w.append("\n");
        w.append("        @Child private ").append(clazzName).append(" node = ").append(packageName).append(".").append(clazzName).append("NodeGen.create();");
        w.append("\n");
        w.append("        @Override\n");
        w.append("        public Object execute(VirtualFrame frame) {\n");
        w.append("            try {\n");
        w.append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
        w.append("              return node.executeWithTarget(frame, receiver);\n");
        w.append("            } catch (UnsupportedSpecializationException e) {\n");
        w.append("                throw UnsupportedTypeException.raise(e.getSuppliedValues());\n");
        w.append("            }\n");
        w.append("        }\n");
        w.append("\n");
        w.append("    }\n");
    }

    static void appendRootNodeFactory(Writer w) throws IOException {
        w.append("    public static RootNode createRoot(Class<? extends TruffleLanguage<?>> language) {\n");
        w.append("        return new LanguageCheckRootNode(language);\n");
        w.append("    }\n");

    }

    public String getRootNodeFactoryInvokation() {
        return packageName + "." + clazzName + ".createRoot(" + truffleLanguageFullClazzName + ".class)";
    }

    @Override
    public String toString() {
        return clazzName;
    }
}
