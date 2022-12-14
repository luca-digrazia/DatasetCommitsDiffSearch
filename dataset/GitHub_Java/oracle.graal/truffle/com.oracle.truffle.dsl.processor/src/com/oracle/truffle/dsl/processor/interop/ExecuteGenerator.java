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
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class ExecuteGenerator extends MessageGenerator {

    private final int numberOfArguments;
    // Execute: TruffleObject receiver, Object[] args
    // Invoke: TruffleObject receiver, String identifier, Object[] args
    // New: TruffleObject receiver, Object[] args
    private final String targetableExecuteNode;
    private final String executeRootNode;

    public ExecuteGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element);
        this.targetableExecuteNode = (new StringBuilder(messageName)).replace(0, 1, messageName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
        this.executeRootNode = (new StringBuilder(messageName)).replace(0, 1, messageName.substring(0, 1).toUpperCase()).append("RootNode").toString();
        if (Message.createExecute(0).toString().equalsIgnoreCase(messageName)) {
            numberOfArguments = 2;
        } else if (Message.createInvoke(0).toString().equalsIgnoreCase(messageName)) {
            numberOfArguments = 3;
        } else if (Message.createNew(0).toString().equalsIgnoreCase(messageName)) {
            numberOfArguments = 2;
        } else {
            throw new AssertionError();
        }
    }

    @Override
    void appendImports(Writer w) throws IOException {
        super.appendImports(w);
        w.append("import java.util.List;").append("\n");
    }

    @Override
    int getParameterCount() {
        return numberOfArguments;
    }

    @Override
    String getTargetableNodeName() {
        return targetableExecuteNode;
    }

    @Override
    void appendRootNode(Writer w) throws IOException {
        w.append("    private final static class ").append(executeRootNode).append(" extends RootNode {\n");
        w.append("        protected ").append(executeRootNode).append("(Class<? extends TruffleLanguage<?>> language) {\n");
        w.append("            super(language, null, null);\n");
        w.append("        }\n");
        w.append("\n");
        w.append("        @Child private ").append(clazzName).append(" node = ").append(packageName).append(".").append(clazzName).append("NodeGen.create();");
        w.append("\n");
        w.append("        @Override\n");
        w.append("        public Object execute(VirtualFrame frame) {\n");
        w.append("            try {\n");
        w.append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
        if (Message.createInvoke(0).toString().equalsIgnoreCase(messageName)) {
            w.append("              List<Object> arguments = ForeignAccess.getArguments(frame);\n");
            w.append("              Object identifier = arguments.get(0);\n");
            w.append("              Object[] args = new Object[arguments.size() - 1];\n");
            w.append("              for (int i = 0; i < arguments.size() - 1; i++) {\n");
            w.append("                args[i] = arguments.get(i + 1);\n");
            w.append("              }\n");
            w.append("              return node.executeWithTarget(frame, receiver, identifier, args);\n");
        } else {
            w.append("              List<Object> arguments = ForeignAccess.getArguments(frame);\n");
            w.append("              Object[] args = new Object[arguments.size()];\n");
            w.append("              for (int i = 0; i < arguments.size(); i++) {\n");
            w.append("                args[i] = arguments.get(i);\n");
            w.append("              }\n");
            w.append("              return node.executeWithTarget(frame, receiver, args);\n");

        }
        w.append("            } catch (UnsupportedSpecializationException e) {\n");
        w.append("                throw UnsupportedTypeException.raise(e, e.getSuppliedValues());\n");
        w.append("            }\n");
        w.append("        }\n");
        w.append("\n");
        w.append("    }\n");
    }

    @Override
    String getRootNodeName() {
        return executeRootNode;
    }

    @Override
    public String checkSignature(ExecutableElement method) {
        final List<? extends VariableElement> params = method.getParameters();
        boolean hasFrameArgument = false;
        if (params.size() >= 1) {
            hasFrameArgument = ElementUtils.typeEquals(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class));
        }
        int expectedNumberOfArguments = hasFrameArgument ? getParameterCount() + 1 : getParameterCount();

        if (params.size() != expectedNumberOfArguments) {
            return "Wrong number of arguments.";
        }

        if (Message.createInvoke(0).toString().equalsIgnoreCase(messageName)) {
            if (!ElementUtils.typeEquals(params.get(hasFrameArgument ? 2 : 1).asType(), Utils.getTypeMirror(processingEnv, String.class))) {
                int i = hasFrameArgument ? 3 : 2;
                return "The " + i + " argument must be a " + String.class.getName() + "- but is " + ElementUtils.getQualifiedName(params.get(hasFrameArgument ? 2 : 1).asType());
            }
        }
        VariableElement variableElement = params.get(params.size() - 1);
        if (!Utils.isObjectArray(variableElement.asType())) {
            return "The last argument must be the arguments array. Required type: java.lang.Object[]";
        }
        return null;
    }

}
