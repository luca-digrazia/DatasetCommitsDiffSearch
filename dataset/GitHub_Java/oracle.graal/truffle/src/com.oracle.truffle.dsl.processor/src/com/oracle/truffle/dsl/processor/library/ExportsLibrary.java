/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.library;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getRepeatedAnnotation;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSuperType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;

public final class ExportsLibrary extends Template {

    private final Map<String, ExportMessageData> exportedMessages = new LinkedHashMap<>();

    private final ExportsData exports;
    private final LibraryData library;
    private final TypeMirror receiverType;
    private final boolean explicitReceiver;
    private Map<CacheExpression, String> sharedExpressions;

    public ExportsLibrary(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, ExportsData exports, LibraryData library, TypeMirror receiverType,
                    boolean explicitReceiver) {
        super(context, templateType, annotation);
        this.exports = exports;
        this.receiverType = receiverType;
        this.library = library;
        this.explicitReceiver = explicitReceiver;
    }

    public void setSharedExpressions(Map<CacheExpression, String> sharedExpressions) {
        this.sharedExpressions = sharedExpressions;
    }

    public Map<CacheExpression, String> getSharedExpressions() {
        return sharedExpressions;
    }

    public boolean isFinalReceiver() {
        TypeElement type = ElementUtils.castTypeElement(receiverType);
        if (type == null) {
            return true;
        }
        return type.getModifiers().contains(Modifier.FINAL);
    }

    public boolean isDynamicDispatchTarget() {
        return isExplicitReceiver() && !isDefaultExport() && isReceiverDynamicDispatched();
    }

    public boolean needsDynamicDispatch() {
        TypeElement type = ElementUtils.castTypeElement(receiverType);
        if (type == null) {
            return false;
        }
        if (getLibrary().isDynamicDispatch()) {
            return false;
        }
        if (type.getKind().isInterface() || ElementUtils.isObject(receiverType)) {
            return true;
        }
        for (ExportsLibrary otherLibrary : exports.getExportedLibraries().values()) {
            if (otherLibrary != this && otherLibrary.getLibrary().isDynamicDispatch()) {
                return true;
            }
        }
        if (isExplicitReceiver()) {
            if (isReceiverDynamicDispatched()) {
                return true;
            }
        }

        return false;
    }

    public boolean isDefaultExport() {
        for (LibraryDefaultExportData defaultExport : getLibrary().getDefaultExports()) {
            if (typeEquals(defaultExport.getImplType(), getTemplateType().asType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isReceiverDynamicDispatched() {
        TypeElement receiverTypeElement = ElementUtils.castTypeElement(receiverType);
        while (receiverTypeElement != null) {
            List<AnnotationMirror> exportLibrary = getRepeatedAnnotation(receiverTypeElement.getAnnotationMirrors(), ExportLibrary.class);
            for (AnnotationMirror export : exportLibrary) {
                TypeMirror exportedLibrary = getAnnotationValue(TypeMirror.class, export, "value");
                if (ElementUtils.typeEquals(exportedLibrary, ProcessorContext.getInstance().getType(DynamicDispatchLibrary.class))) {
                    return true;
                }
            }
            receiverTypeElement = getSuperType(receiverTypeElement);
        }
        return false;
    }

    public boolean needsRewrites() {
        for (ExportMessageData message : exportedMessages.values()) {
            if (needsRewrites(message.getExportedClass())) {
                return true;
            } else if (message.getExportedClass() == null && needsRewrites(message.getExportedMethod())) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsRewrites(ExportMessageElement clazz) {
        if (clazz != null && clazz.getSpecializedNode() != null) {
            if (clazz.getSpecializedNode().needsRewrites(ProcessorContext.getInstance())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return new ArrayList<>(exportedMessages.values());
    }

    public LibraryData getLibrary() {
        return library;
    }

    public Map<String, ExportMessageData> getExportedMessages() {
        return exportedMessages;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return getTemplateTypeAnnotation();
    }

    public TypeMirror getExplicitReceiver() {
        return isExplicitReceiver() ? getReceiverType() : null;
    }

    public TypeMirror getReceiverType() {
        return receiverType;
    }

    public boolean isExplicitReceiver() {
        return explicitReceiver;
    }

}
