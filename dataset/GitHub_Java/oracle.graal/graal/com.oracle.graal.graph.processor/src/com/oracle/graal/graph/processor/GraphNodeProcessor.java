/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.processor;

import java.io.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.graal.graph.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.transform.*;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({"com.oracle.graal.graph.NodeInfo"})
public class GraphNodeProcessor extends AbstractProcessor {
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private void errorMessage(Element element, String format, Object... args) {
        processingEnv.getMessager().printMessage(Kind.ERROR, String.format(format, args), element);
    }

    /**
     * Bugs in an annotation processor can cause silent failure so try to report any exception
     * throws as errors.
     */
    private void reportException(Element element, Throwable t) {
        StringWriter buf = new StringWriter();
        t.printStackTrace(new PrintWriter(buf));
        buf.toString();
        errorMessage(element, "Exception thrown during processing: %s", buf.toString());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        GraphNodeGenerator gen = new GraphNodeGenerator(processingEnv);
        Types types = processingEnv.getTypeUtils();
        Elements elements = processingEnv.getElementUtils();

        TypeMirror nodeType = elements.getTypeElement(Node.class.getName()).asType();

        for (Element element : roundEnv.getElementsAnnotatedWith(NodeInfo.class)) {
            try {
                if (!types.isSubtype(element.asType(), nodeType)) {
                    errorMessage(element, "%s can only be applied to %s subclasses", NodeInfo.class.getSimpleName(), Node.class.getSimpleName());
                    continue;
                }

                NodeInfo nodeInfo = element.getAnnotation(NodeInfo.class);
                if (nodeInfo == null) {
                    errorMessage(element, "Cannot get %s annotation from annotated element", NodeInfo.class.getSimpleName());
                    continue;
                }

                TypeElement typeElement = (TypeElement) element;

                if (typeElement.getModifiers().contains(Modifier.FINAL)) {
                    errorMessage(element, "%s annotated class cannot be final", NodeInfo.class.getSimpleName());
                    continue;
                }

                GraphNode graphNode = new GraphNode(typeElement, nodeInfo);
                CodeCompilationUnit unit = gen.process(graphNode);
                unit.setGeneratorElement(typeElement);

                DeclaredType overrideType = (DeclaredType) ElementUtils.getType(processingEnv, Override.class);
                DeclaredType unusedType = (DeclaredType) ElementUtils.getType(processingEnv, SuppressWarnings.class);
                unit.accept(new GenerateOverrideVisitor(overrideType), null);
                unit.accept(new FixWarningsVisitor(processingEnv, unusedType, overrideType), null);
                unit.accept(new CodeWriter(processingEnv, typeElement), null);
            } catch (Throwable t) {
                reportException(element, t);
            }
        }
        return false;
    }
}
