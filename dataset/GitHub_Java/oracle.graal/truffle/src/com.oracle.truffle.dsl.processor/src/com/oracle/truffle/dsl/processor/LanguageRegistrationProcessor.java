/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import java.lang.annotation.Annotation;

@SupportedAnnotationTypes("com.oracle.truffle.api.TruffleLanguage.Registration")
public final class LanguageRegistrationProcessor extends AbstractRegistrationProcessor {

    // also update list in PolyglotEngineImpl#RESERVED_IDS
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList("host", "graal", "truffle", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm",
                                    "engine", "log", "image-build-time"));

    @SuppressWarnings("deprecation")
    @Override
    boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror, Annotation registration) {
        if (!annotatedElement.getModifiers().contains(Modifier.PUBLIC)) {
            emitError("Registered language class must be public", annotatedElement);
            return false;
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !annotatedElement.getModifiers().contains(Modifier.STATIC)) {
            emitError("Registered language inner-class must be static", annotatedElement);
            return false;
        }
        TypeMirror truffleLang = processingEnv.getTypeUtils().erasure(ElementUtils.getTypeElement(processingEnv, TruffleLanguage.class.getName()).asType());
        TypeMirror truffleLangProvider = ProcessorContext.getInstance().getType(TruffleLanguage.Provider.class);
        boolean processingTruffleLanguage;
        if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleLang)) {
            processingTruffleLanguage = true;
        } else if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleLangProvider)) {
            processingTruffleLanguage = false;
        } else {
            emitError("Registered language class must subclass TruffleLanguage", annotatedElement);
            return false;
        }
        boolean foundConstructor = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(annotatedElement.getEnclosedElements())) {
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (!constructor.getParameters().isEmpty()) {
                continue;
            }
            foundConstructor = true;
            break;
        }

        Element singletonElement = null;
        for (Element mem : annotatedElement.getEnclosedElements()) {
            if (!mem.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (mem.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!mem.getModifiers().contains(Modifier.FINAL)) {
                continue;
            }
            if (!"INSTANCE".equals(mem.getSimpleName().toString())) {
                continue;
            }
            if (processingEnv.getTypeUtils().isAssignable(mem.asType(), truffleLang)) {
                singletonElement = mem;
                break;
            }
        }
        boolean valid = true;

        if (processingTruffleLanguage && singletonElement != null) {
            emitWarning("Using a singleton field is deprecated. Please provide a public no-argument constructor instead.", singletonElement);
            valid = false;
        } else {
            if (!foundConstructor) {
                emitError("A TruffleLanguage subclass must have a public no argument constructor.", annotatedElement);
                return false;
            }
        }

        TruffleLanguage.Registration annotation = (TruffleLanguage.Registration) registration;
        Set<String> mimeTypes = new HashSet<>();
        if (!validateMimeTypes(mimeTypes, annotatedElement, registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "characterMimeTypes"), annotation.characterMimeTypes())) {
            return false;
        }
        if (!validateMimeTypes(mimeTypes, annotatedElement, registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "byteMimeTypes"), annotation.byteMimeTypes())) {
            return false;
        }

        String defaultMimeType = annotation.defaultMimeType();
        if (mimeTypes.size() > 1 && (defaultMimeType == null || defaultMimeType.equals(""))) {
            emitError("No defaultMimeType attribute specified. " +
                            "The defaultMimeType attribute needs to be specified if more than one MIME type was specified.", annotatedElement,
                            registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "defaultMimeType"));
            return false;
        }

        if (defaultMimeType != null && !defaultMimeType.equals("") && !mimeTypes.contains(defaultMimeType)) {
            emitError("The defaultMimeType is not contained in the list of supported characterMimeTypes or byteMimeTypes. Add the specified default MIME type to" +
                            " character or byte MIME types to resolve this.", annotatedElement,
                            registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "defaultMimeType"));
            return false;
        }

        if (annotation.mimeType().length == 0) {
            String id = annotation.id();
            if (id.isEmpty()) {
                emitError("The attribute id is mandatory.", annotatedElement, registrationMirror, null);
                return false;
            }
            if (RESERVED_IDS.contains(id)) {
                emitError(String.format("Id '%s' is reserved for other use and must not be used as id.", id), annotatedElement, registrationMirror,
                                ElementUtils.getAnnotationValue(registrationMirror, "id"));
                return false;
            }
        }

        if (!validateFileTypeDetectors(annotatedElement, registrationMirror)) {
            return false;
        }

        if (valid) {
            assertNoErrorExpected(annotatedElement);
        }
        return processingTruffleLanguage;
    }

    @Override
    Class<?> getProviderClass() {
        return TruffleLanguage.Provider.class;
    }

    @Override
    Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement) {
        List<AnnotationMirror> result = new ArrayList<>(2);
        result.add(ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), ProcessorContext.getInstance().getType(TruffleLanguage.Registration.class)));
        AnnotationMirror providedTags = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), ProcessorContext.getInstance().getType(ProvidedTags.class));
        if (providedTags != null) {
            result.add(providedTags);
        }
        return result;
    }

    @Override
    void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement) {
        ProcessorContext context = ProcessorContext.getInstance();
        CodeTreeBuilder builder = methodToImplement.createBuilder();
        switch (methodToImplement.getSimpleName().toString()) {
            case "create":
                DeclaredType languageType = (DeclaredType) annotatedElement.asType();
                List<? extends TypeParameterElement> typeParams = annotatedElement.getTypeParameters();
                if (!typeParams.isEmpty()) {
                    builder.startReturn().string("new " + annotatedElement.getQualifiedName() + "<>()").end();
                } else {
                    builder.startReturn().startNew(languageType).end(2);
                }
                break;
            case "createFileTypeDetectors":
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), ProcessorContext.getInstance().getType(TruffleLanguage.Registration.class));
                List<TypeMirror> detectors = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "fileTypeDetectors");
                if (detectors.isEmpty()) {
                    builder.startReturn().startStaticCall(context.getType(Collections.class), "emptyList").end().end();
                } else {
                    builder.startReturn();
                    builder.startStaticCall(context.getType(Arrays.class), "asList");
                    for (TypeMirror detector : detectors) {
                        builder.startGroup().startNew(detector).end(2);
                    }
                    builder.end(2);
                }
                break;
            case "getLanguageClassName":
                Elements elements = context.getEnvironment().getElementUtils();
                builder.startReturn().doubleQuote(elements.getBinaryName(annotatedElement).toString()).end();
                break;
            default:
                throw new IllegalStateException("Unsupported method: " + methodToImplement.getSimpleName());
        }
    }

    @Override
    String getRegistrationFileName() {
        return "META-INF/truffle/language";
    }

    @Override
    @SuppressWarnings("deprecation")
    void storeRegistrations(Properties into, Iterable<? extends TypeElement> annotatedElements) {
        int cnt = 0;
        for (TypeElement annotatedElement : annotatedElements) {
            String prefix = "language" + ++cnt + ".";
            Registration annotation = annotatedElement.getAnnotation(Registration.class);
            if (annotation == null) {
                return;
            }
            String className = processingEnv.getElementUtils().getBinaryName(annotatedElement).toString();
            String id = annotation.id();
            if (id != null && !id.isEmpty()) {
                into.setProperty(prefix + "id", id);
            }
            into.setProperty(prefix + "name", annotation.name());
            into.setProperty(prefix + "implementationName", annotation.implementationName());
            into.setProperty(prefix + "version", annotation.version());
            into.setProperty(prefix + "className", className);

            if (!annotation.defaultMimeType().equals("")) {
                into.setProperty(prefix + "defaultMimeType", annotation.defaultMimeType());
            }

            String[] mimes = annotation.mimeType();
            for (int i = 0; i < mimes.length; i++) {
                into.setProperty(prefix + "mimeType." + i, mimes[i]);
            }
            String[] charMimes = annotation.characterMimeTypes();
            Arrays.sort(charMimes);
            for (int i = 0; i < charMimes.length; i++) {
                into.setProperty(prefix + "characterMimeType." + i, charMimes[i]);
            }
            String[] byteMimes = annotation.byteMimeTypes();
            Arrays.sort(byteMimes);
            for (int i = 0; i < byteMimes.length; i++) {
                into.setProperty(prefix + "byteMimeType." + i, byteMimes[i]);
            }

            String[] dependencies = annotation.dependentLanguages();
            Arrays.sort(dependencies);
            for (int i = 0; i < dependencies.length; i++) {
                into.setProperty(prefix + "dependentLanguage." + i, dependencies[i]);
            }
            into.setProperty(prefix + "interactive", Boolean.toString(annotation.interactive()));
            into.setProperty(prefix + "internal", Boolean.toString(annotation.internal()));

            AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), ProcessorContext.getInstance().getType(Registration.class));
            int serviceCounter = 0;
            for (TypeMirror serviceTypeMirror : ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "services")) {
                into.setProperty(prefix + "service" + serviceCounter++, processingEnv.getElementUtils().getBinaryName(ElementUtils.fromTypeMirror(serviceTypeMirror)).toString());
            }
            int fileTypeDetectorCounter = 0;
            for (TypeMirror fileTypeDetectorTypeMirror : ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "fileTypeDetectors")) {
                into.setProperty(prefix + "fileTypeDetector" + fileTypeDetectorCounter++,
                                processingEnv.getElementUtils().getBinaryName(ElementUtils.fromTypeMirror(fileTypeDetectorTypeMirror)).toString());
            }
        }
    }

    private boolean validateMimeTypes(Set<String> collectedMimeTypes, Element e, AnnotationMirror mirror, AnnotationValue value, String[] loadedMimeTypes) {
        for (String mimeType : loadedMimeTypes) {
            if (!validateMimeType(e, mirror, value, mimeType)) {
                return false;
            }
            if (collectedMimeTypes.contains(mimeType)) {
                emitError(String.format("Duplicate MIME type specified '%s'. MIME types must be unique.", mimeType), e, mirror,
                                value);
                return false;
            }
            collectedMimeTypes.add(mimeType);
        }
        return true;
    }

    private boolean validateMimeType(Element type, AnnotationMirror mirror, AnnotationValue value, String mimeType) {
        int index = mimeType.indexOf('/');
        if (index == -1 || index == 0 || index == mimeType.length() - 1) {
            emitError(String.format("Invalid MIME type '%s' provided. MIME types consist of a type and a subtype separated by '/'.", mimeType), type, mirror, value);
            return false;
        }
        if (mimeType.indexOf('/', index + 1) != -1) {
            emitError(String.format("Invalid MIME type '%s' provided. MIME types consist of a type and a subtype separated by '/'.", mimeType), type, mirror, value);
            return false;
        }
        return true;
    }

    private boolean validateFileTypeDetectors(Element annotatedElement, AnnotationMirror mirror) {
        AnnotationValue value = ElementUtils.getAnnotationValue(mirror, "fileTypeDetectors", true);
        for (TypeMirror fileTypeDetectorType : ElementUtils.getAnnotationValueList(TypeMirror.class, mirror, "fileTypeDetectors")) {
            TypeElement fileTypeDetectorElement = ElementUtils.fromTypeMirror(fileTypeDetectorType);
            if (!fileTypeDetectorElement.getModifiers().contains(Modifier.PUBLIC)) {
                emitError("Registered FileTypeDetector class must be public.", annotatedElement, mirror, value);
                return false;
            }
            if (fileTypeDetectorElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !fileTypeDetectorElement.getModifiers().contains(Modifier.STATIC)) {
                emitError("Registered FileTypeDetector inner-class must be static.", annotatedElement, mirror, value);
                return false;
            }
            boolean foundConstructor = false;
            for (ExecutableElement constructor : ElementFilter.constructorsIn(fileTypeDetectorElement.getEnclosedElements())) {
                if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                    continue;
                }
                if (!constructor.getParameters().isEmpty()) {
                    continue;
                }
                foundConstructor = true;
                break;
            }
            if (!foundConstructor) {
                emitError("A FileTypeDetector subclass must have a public no argument constructor.", annotatedElement, mirror, value);
                return false;
            }
        }
        return true;
    }
}
