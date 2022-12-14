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

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createClass;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryExport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeData;

public class ExportsGenerator extends CodeTypeElementFactory<ExportsData> {

    private static final String ACCEPTS = "accepts";
    private static final String ACCEPTS_METHOD_NAME = ACCEPTS + "_";

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final Map<String, CodeVariableElement> libraryConstants;

    public ExportsGenerator(Map<String, CodeVariableElement> libraryConstants) {
        this.libraryConstants = libraryConstants;
    }

    @Override
    public List<CodeTypeElement> create(ProcessorContext context1, ExportsData exports) {
        libraryConstants.clear();
        String className = exports.getTemplateType().getSimpleName().toString() + "Gen";
        CodeTypeElement genClass = createClass(exports, null, modifiers(Modifier.FINAL), className, null);

        CodeTreeBuilder statics = genClass.add(new CodeExecutableElement(modifiers(STATIC), null, "<cinit>")).createBuilder();
        statics.startStatement();
        statics.startStaticCall(context.getType(LibraryExport.class), "register");
        statics.typeLiteral(exports.getTemplateType().asType());

        genClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(PRIVATE), genClass));

        for (ExportsLibrary libraryExports : exports.getExportedLibraries().values()) {
            if (libraryExports.hasErrors()) {
                continue;
            }
            final TypeElement libraryBaseTypeElement = libraryExports.getLibrary().getTemplateType();
            final DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();

            CodeTypeElement uncachedClass = createUncached(libraryExports);
            CodeTypeElement cacheClass = createCached(libraryExports);

            CodeTypeElement resolvedExports = createResolvedExports(libraryExports, ElementUtils.getSimpleName(libraryBaseType) + "Exports", cacheClass, uncachedClass);
            resolvedExports.add(cacheClass);
            resolvedExports.add(uncachedClass);

            genClass.add(resolvedExports);
            statics.startNew(resolvedExports.asType()).end();
        }

        statics.end();
        statics.end();

        genClass.addAll(libraryConstants.values());
        return Arrays.asList(genClass);
    }

    private static boolean useCacheSingleton(ExportsLibrary library) {
        return library.isFinalReceiver() && !library.needsRewrites() && !library.needsDynamicDispatch();
    }

    CodeTypeElement createResolvedExports(ExportsLibrary library, String className, CodeTypeElement cacheClass, CodeTypeElement uncachedClass) {
        CodeTreeBuilder builder;
        final TypeElement libraryBaseTypeElement = library.getLibrary().getTemplateType();
        final DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();
        final TypeMirror exportReceiverType = library.getReceiverType();

        TypeMirror baseType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(LibraryExport.class),
                        Arrays.asList(libraryBaseType));
        CodeTypeElement exportsClass = createClass(library, null, modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), className, baseType);

        CodeExecutableElement constructor = new CodeExecutableElement(modifiers(PRIVATE), null, exportsClass.getSimpleName().toString());
        builder = constructor.createBuilder();
        builder.startStatement().startSuperCall().typeLiteral(libraryBaseType).typeLiteral(library.getReceiverType()).string(Boolean.valueOf(library.isDefaultExport()).toString()).end().end();
        exportsClass.add(constructor);

        CodeVariableElement uncachedSingleton = null;
        if (useCacheSingleton(library)) {
            uncachedSingleton = exportsClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), uncachedClass.asType(), "UNCACHED"));
            builder = uncachedSingleton.createInitBuilder();
            builder.startNew(uncachedClass.asType()).end();
        }

        CodeExecutableElement createUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(LibraryExport.class), "createUncached"));
        createUncached.setReturnType(libraryBaseType);
        createUncached.getModifiers().remove(Modifier.ABSTRACT);
        createUncached.renameArguments("receiver");
        builder = createUncached.createBuilder();
        if (!ElementUtils.typeEquals(exportReceiverType, context.getType(Object.class))) {
            builder.startAssert().string("receiver instanceof ").type(exportReceiverType).end();
        }

        builder.startReturn();
        if (uncachedSingleton != null) {
            builder.staticReference(uncachedSingleton);
        } else {
            List<ExecutableElement> constructors = ElementFilter.constructorsIn(uncachedClass.getEnclosedElements());
            builder.startNew(uncachedClass.getSimpleName().toString());
            if (!constructors.isEmpty()) {
                ExecutableElement uncachedClassConstructor = constructors.iterator().next();
                if (uncachedClassConstructor.getParameters().size() == 1) {
                    builder.string("receiver");
                }
            }
            builder.end();
        }
        builder.end();
        exportsClass.add(createUncached);

        CodeVariableElement cacheSingleton = null;
        if (useCacheSingleton(library)) {
            cacheSingleton = exportsClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), cacheClass.asType(), "CACHE"));
            builder = cacheSingleton.createInitBuilder();
            builder.startNew(cacheClass.asType()).end();
        }

        CodeExecutableElement createCached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(LibraryExport.class), "createCached"));
        createCached.setReturnType(libraryBaseType);
        createCached.getModifiers().remove(Modifier.ABSTRACT);
        createCached.renameArguments("receiver");
        builder = createCached.createBuilder();
        if (!ElementUtils.typeEquals(exportReceiverType, context.getType(Object.class))) {
            builder.startAssert().string("receiver instanceof ").type(exportReceiverType).end();
        }
        builder.startReturn();
        if (cacheSingleton != null) {
            builder.staticReference(cacheSingleton);
        } else {
            List<ExecutableElement> constructors = ElementFilter.constructorsIn(cacheClass.getEnclosedElements());
            builder.startNew(cacheClass.getSimpleName().toString());
            if (!constructors.isEmpty()) {
                ExecutableElement cacheClassConstructor = constructors.iterator().next();
                if (cacheClassConstructor.getParameters().size() == 1) {
                    builder.string("receiver");
                }
            }
            builder.end();
        }
        builder.end();
        exportsClass.add(createCached);

        return exportsClass;
    }

    CodeTypeElement createCached(ExportsLibrary libraryExports) {
        CodeTreeBuilder builder;
        TypeMirror exportReceiverType = libraryExports.getReceiverType();
        TypeElement libraryBaseTypeElement = libraryExports.getLibrary().getTemplateType();
        DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();

        CodeTypeElement cacheClass = createClass(libraryExports, null, modifiers(PRIVATE, STATIC, FINAL), "Cached", libraryBaseType);

        CodeTree acceptsAssertions = createDynamicDispatchAssertions(libraryExports);
        CodeTree defaultAccepts = createDefaultAccepts(cacheClass, libraryExports, exportReceiverType, true);

        CodeExecutableElement accepts = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Library.class), ACCEPTS));
        accepts.getModifiers().remove(Modifier.ABSTRACT);
        accepts.renameArguments("receiver");
        builder = accepts.createBuilder();
        final ExportMessageData acceptsMessage = libraryExports.getExportedMessages().get(ACCEPTS);
        if (acceptsAssertions != null) {
            builder.tree(acceptsAssertions);
        }
        if (acceptsMessage == null) {
            builder.startReturn().tree(defaultAccepts).end();
        } else {
            builder.startReturn().tree(defaultAccepts).string(" && accepts_(receiver)").end();
        }
        builder.end();

        cacheClass.addOptional(createCastMethod(libraryExports, exportReceiverType, true));
        cacheClass.add(accepts);

        if (!libraryExports.needsRewrites()) {
            CodeExecutableElement isAdoptable = cacheClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "isAdoptable")));
            builder = isAdoptable.createBuilder();
            if (libraryExports.needsDynamicDispatch()) {
                builder.startReturn();
                builder.startStaticCall(context.getType(NodeUtil.class), "isAdoptable").string("dynamicDispatch_").end();
                builder.end();
            } else {
                builder.returnFalse();
            }
        }

        Set<NodeData> cachedSharedNodes = new LinkedHashSet<>();
        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            ExportMessageElement nodeExport = export.getExportedClass();
            ExportMessageElement methodExport = export.getExportedMethod();
            if (nodeExport != null && nodeExport.getSpecializedNode() != null) {
                cachedSharedNodes.add(nodeExport.getSpecializedNode());
            } else if (methodExport != null && methodExport.getSpecializedNode() != null) {
                cachedSharedNodes.add(methodExport.getSpecializedNode());
            }
        }
        Map<NodeData, CodeTypeElement> sharedNodes = new HashMap<>();

        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            LibraryMessage message = export.getResolvedMessage();
            ExportMessageElement methodExport = export.getExportedMethod();
            ExportMessageElement nodeExport = export.getExportedClass();

            TypeMirror libraryReceiverType = export.getResolvedMessage().getLibrary().getExportsReceiverType();
            TypeMirror cachedExportReceiverType = nodeExport != null ? nodeExport.getReceiverType() : methodExport.getReceiverType();
            CodeTree cachedReceiverAccess = CodeTreeBuilder.createBuilder().maybeCast(libraryReceiverType, cachedExportReceiverType, "receiver").build();

            // cached execute
            NodeData cachedSpecializedNode;
            if (nodeExport != null && nodeExport.getSpecializedNode() != null) {
                cachedSpecializedNode = nodeExport.getSpecializedNode();
            } else if (methodExport != null && methodExport.getSpecializedNode() != null) {
                cachedSpecializedNode = methodExport.getSpecializedNode();
            } else {
                cachedSpecializedNode = null;
            }

            CodeExecutableElement cachedExecute = null;
            if (cachedSpecializedNode == null) {
                if (methodExport == null) {
                    throw new AssertionError("Missing method export. Missed validation for " + export.getResolvedMessage().getSimpleName());
                }
                ExecutableElement exportMethod = (ExecutableElement) methodExport.getMessageElement();
                cachedExecute = cacheClass.add(createDirectCall(cachedReceiverAccess, message, exportMethod));
            } else {
                CodeTypeElement dummyClass = sharedNodes.get(cachedSpecializedNode);
                boolean shared = true;
                if (dummyClass == null) {
                    FlatNodeGenFactory factory = new FlatNodeGenFactory(context, cachedSpecializedNode, cachedSharedNodes, libraryExports.getSharedExpressions(), libraryConstants);
                    dummyClass = createClass(libraryExports, null, modifiers(), "Dummy", context.getType(Node.class));
                    factory.create(dummyClass);
                    sharedNodes.put(cachedSpecializedNode, dummyClass);
                    shared = false;
                }

                for (Element element : dummyClass.getEnclosedElements()) {
                    String simpleName = element.getSimpleName().toString();
                    if (element.getKind() == ElementKind.METHOD) {
                        if (simpleName.endsWith("AndSpecialize")) {
                            // nothing to do for specialize method
                        } else if (simpleName.startsWith("execute")) {
                            CodeExecutableElement executable = (CodeExecutableElement) element;
                            executable.setVarArgs(message.getExecutable().isVarArgs());
                            cachedExecute = CodeExecutableElement.clone(executable);
                            cachedExecute.setSimpleName(CodeNames.of(message.getName()));
                            injectReceiverType(cachedExecute, libraryExports, cachedExportReceiverType, true);
                            cacheClass.getEnclosedElements().add(cachedExecute);
                            continue;
                        }
                    } else if (element.getKind() == ElementKind.CONSTRUCTOR) {
                        // no constructores needed
                        continue;
                    }
                    if (!shared) {
                        // only execute method needed for shared
                        cacheClass.getEnclosedElements().add(element);
                    }
                }
            }
            if (message.getName().equals(ACCEPTS)) {
                if (cachedSpecializedNode == null || !cachedSpecializedNode.needsRewrites(context)) {
                    cachedExecute.getModifiers().add(Modifier.STATIC);
                }
                cachedExecute.setSimpleName(CodeNames.of(ACCEPTS_METHOD_NAME));
                ElementUtils.setVisibility(cachedExecute.getModifiers(), Modifier.PRIVATE);
            } else {
                if (libraryExports.needsRewrites()) {
                    injectCachedAssertions(cachedExecute);
                }
            }
        }
        return cacheClass;
    }

    private CodeExecutableElement createCastMethod(ExportsLibrary libraryExports, TypeMirror exportReceiverType, boolean cached) {
        if (!libraryExports.getLibrary().isDynamicDispatch()) {
            return null;
        }

        CodeTreeBuilder builder;
        CodeExecutableElement castMethod = CodeExecutableElement.cloneNoAnnotations(ElementUtils.findMethod(DynamicDispatchLibrary.class, "cast"));
        castMethod.getModifiers().remove(Modifier.ABSTRACT);
        castMethod.renameArguments("receiver");
        builder = castMethod.createBuilder();
        if (cached) {
            builder.startReturn().tree(createReceiverCast(libraryExports, castMethod.getParameters().get(0).asType(), exportReceiverType, CodeTreeBuilder.singleString("receiver"), cached)).end();
        } else {
            builder.startReturn().string("receiver").end();
        }
        return castMethod;
    }

    private CodeTree createDefaultAccepts(CodeTypeElement libraryGen, ExportsLibrary libraryExports, TypeMirror exportReceiverType, boolean cached) {
        CodeTreeBuilder builder;
        CodeTreeBuilder acceptsBuilder = CodeTreeBuilder.createBuilder();
        if (libraryExports.needsDynamicDispatch()) {
            CodeExecutableElement constructor = libraryGen.add(GeneratorUtils.createConstructorUsingFields(modifiers(), libraryGen));
            constructor.addParameter(new CodeVariableElement(context.getType(Object.class), "receiver"));

            CodeVariableElement dynamicDispatchLibrary = libraryGen.add(new CodeVariableElement(modifiers(PRIVATE), context.getType(DynamicDispatchLibrary.class), "dynamicDispatch_"));
            dynamicDispatchLibrary.addAnnotationMirror(new CodeAnnotationMirror(context.getDeclaredType(Child.class)));

            CodeVariableElement dispatchLibraryConstant = useDispatchLibraryConstant();

            builder = constructor.createBuilder();
            if (cached) {
                builder.startStatement().string("this.dynamicDispatch_ = ").staticReference(dispatchLibraryConstant).string(".createCached(receiver)").end();
            } else {
                builder.startStatement().string("this.dynamicDispatch_ = ").staticReference(dispatchLibraryConstant).string(".getUncached(receiver)").end();
            }
            acceptsBuilder.string("dynamicDispatch_.accepts(receiver) && dynamicDispatch_.dispatch(receiver) == ");

            if (libraryExports.isDynamicDispatchTarget()) {
                acceptsBuilder.typeLiteral(libraryExports.getTemplateType().asType());
            } else {
                acceptsBuilder.nullLiteral();
            }
        } else {
            if (libraryExports.isFinalReceiver()) {
                acceptsBuilder.string("receiver instanceof ").type(exportReceiverType);
            } else {
                CodeExecutableElement constructor = libraryGen.add(GeneratorUtils.createConstructorUsingFields(modifiers(), libraryGen));
                constructor.addParameter(new CodeVariableElement(context.getType(Object.class), "receiver"));

                TypeMirror receiverClassType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(Class.class),
                                Arrays.asList(new CodeTypeMirror.WildcardTypeMirror(libraryExports.getReceiverType(), null)));
                libraryGen.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), receiverClassType, "receiverClass_"));

                if (ElementUtils.isObject(libraryExports.getReceiverType())) {
                    constructor.createBuilder().startStatement().string("this.receiverClass_ = receiver.getClass()").end();
                } else {
                    constructor.createBuilder().startStatement().string("this.receiverClass_ = (").cast(libraryExports.getReceiverType()).string("receiver).getClass()").end();
                }

                acceptsBuilder.string("receiver.getClass() == this.receiverClass_");
            }
        }

        CodeTree defaultAccepts = acceptsBuilder.build();
        return defaultAccepts;
    }

    private CodeVariableElement useDispatchLibraryConstant() {
        return FlatNodeGenFactory.createLibraryConstant(libraryConstants, context.getType(DynamicDispatchLibrary.class));
    }

    private CodeTree createDynamicDispatchAssertions(ExportsLibrary libraryExports) {
        if (libraryExports.needsDynamicDispatch() || libraryExports.getLibrary().isDynamicDispatch()) {
            // no assertions for dynamic dispatch itself.
            return null;
        }
        CodeVariableElement dispatchLibraryConstant = useDispatchLibraryConstant();
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startAssert();
        if (libraryExports.isFinalReceiver()) {
            builder.string("!(receiver instanceof ").type(libraryExports.getReceiverType()).string(")");
        } else {
            builder.string("receiver.getClass() != this.receiverClass_");
        }
        builder.string(" || ");
        builder.staticReference(dispatchLibraryConstant).string(
                        ".getUncached().dispatch(receiver) == null : ").doubleQuote(
                                        "Invalid library export '" + libraryExports.getTemplateType().getQualifiedName().toString() +
                                                        "'. Exported receiver with dynamic dispatch found but not expected.");
        builder.end();
        return builder.build();
    }

    CodeTypeElement createUncached(ExportsLibrary libraryExports) {
        CodeTreeBuilder builder;
        final TypeMirror exportReceiverType = libraryExports.getReceiverType();

        final TypeElement libraryBaseTypeElement = libraryExports.getLibrary().getTemplateType();
        final DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();
        final TypeMirror libraryReceiverType = libraryExports.getLibrary().getExportsReceiverType();

        CodeTypeElement uncachedClass = createClass(libraryExports, null, modifiers(PRIVATE, STATIC, FINAL), "Uncached", libraryBaseType);

        CodeTree acceptsAssertions = createDynamicDispatchAssertions(libraryExports);
        CodeTree defaultAccepts = createDefaultAccepts(uncachedClass, libraryExports, exportReceiverType, false);

        CodeExecutableElement acceptUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Library.class), ACCEPTS));
        acceptUncached.getModifiers().remove(Modifier.ABSTRACT);
        acceptUncached.renameArguments("receiver");
        builder = acceptUncached.createBuilder();
        if (acceptsAssertions != null) {
            builder.tree(acceptsAssertions);
        }
        if (libraryExports.getExportedMessages().get(ACCEPTS) == null) {
            builder.startReturn().tree(defaultAccepts).end();
        } else {
            builder.startReturn().tree(defaultAccepts).string(" && accepts_(receiver)").end();
        }
        builder.end();
        uncachedClass.add(acceptUncached);

        uncachedClass.addOptional(createCastMethod(libraryExports, exportReceiverType, false));

        CodeExecutableElement isAdoptable = uncachedClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "isAdoptable")));
        isAdoptable.createBuilder().returnFalse();

        CodeExecutableElement getCost = uncachedClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "getCost")));
        getCost.createBuilder().startReturn().staticReference(ElementUtils.findVariableElement(context.getDeclaredType(NodeCost.class), "MEGAMORPHIC")).end();

        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            LibraryMessage message = export.getResolvedMessage();
            ExportMessageElement methodExport = export.getExportedMethod();
            ExportMessageElement nodeExport = export.getExportedClass();

            // uncached execute
            TypeMirror uncachedReceiverType = methodExport != null ? methodExport.getReceiverType() : nodeExport.getReceiverType();
            CodeTree uncachedReceiverExport = CodeTreeBuilder.createBuilder().maybeCast(libraryReceiverType, uncachedReceiverType, "receiver").build();
            CodeExecutableElement uncachedExecute;
            NodeData uncachedSpecializedNode;
            if (methodExport != null && methodExport.getSpecializedNode() != null && methodExport.getSpecializedNode().isUncachable()) {
                uncachedSpecializedNode = methodExport.getSpecializedNode();
            } else if (methodExport == null && nodeExport != null && nodeExport.getSpecializedNode() != null && nodeExport.getSpecializedNode().isUncachable()) {
                uncachedSpecializedNode = nodeExport.getSpecializedNode();
            } else {
                uncachedSpecializedNode = null;
            }

            if (uncachedSpecializedNode == null) {
                if (methodExport == null) {
                    throw new AssertionError("Missing method export. Missed validation for " + export.getResolvedMessage().getSimpleName());
                }
                ExecutableElement exportMethod = (ExecutableElement) methodExport.getMessageElement();
                CodeExecutableElement directCall = createDirectCall(uncachedReceiverExport, message, exportMethod);
                uncachedExecute = uncachedClass.add(directCall);
                if (message.getName().equals(ACCEPTS)) {
                    directCall.getModifiers().add(Modifier.STATIC);
                }
            } else {
                FlatNodeGenFactory factory = new FlatNodeGenFactory(context, uncachedSpecializedNode, libraryConstants);
                CodeExecutableElement generatedUncached = factory.createUncached();
                generatedUncached.getModifiers().remove(STATIC);
                ElementUtils.setVisibility(generatedUncached.getModifiers(), Modifier.PUBLIC);
                generatedUncached.setSimpleName(CodeNames.of(message.getName()));
                generatedUncached.setVarArgs(message.getExecutable().isVarArgs());
                injectReceiverType(generatedUncached, libraryExports, uncachedReceiverType, false);
                uncachedExecute = uncachedClass.add(generatedUncached);
            }
            if (message.getName().equals(ACCEPTS)) {
                uncachedExecute.getModifiers().add(Modifier.STATIC);
                uncachedExecute.setSimpleName(CodeNames.of(ACCEPTS + "_"));
                ElementUtils.setVisibility(uncachedExecute.getModifiers(), Modifier.PRIVATE);
            }
            if (ElementUtils.findAnnotationMirror(uncachedExecute, TruffleBoundary.class) == null) {
                uncachedExecute.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(TruffleBoundary.class)));
            }
        }
        return uncachedClass;

    }

    static void injectCachedAssertions(CodeExecutableElement cachedExecute) {
        CodeTree body = cachedExecute.getBodyTree();
        CodeTreeBuilder builder = cachedExecute.createBuilder();
        builder.startAssert().string("getRootNode() != null : ").doubleQuote("Invalid libray usage. Cached library must be adopted by a RootNode before it is executed.").end();
        builder.tree(body);
    }

    private CodeExecutableElement createDirectCall(CodeTree receiverAccess, LibraryMessage message, ExecutableElement targetMethod) {
        CodeTreeBuilder builder;
        CodeExecutableElement cachedExecute = CodeExecutableElement.cloneNoAnnotations(message.getExecutable());
        cachedExecute.renameArguments("receiver");
        cachedExecute.getModifiers().remove(Modifier.DEFAULT);
        cachedExecute.getModifiers().remove(Modifier.ABSTRACT);
        builder = cachedExecute.createBuilder();
        if (!message.getName().equals(ACCEPTS)) {
            addAcceptsAssertion(builder);
        }
        if (targetMethod == null && message.isAbstract()) {
            builder.startThrow().startNew(context.getType(AbstractMethodError.class)).end().end();
        } else {
            builder.startReturn();
            if (targetMethod == null) {
                builder.startCall("super", message.getName());
                builder.tree(receiverAccess);
            } else if (targetMethod.getModifiers().contains(Modifier.STATIC)) {
                builder.startStaticCall(targetMethod);
                builder.tree(receiverAccess);
            } else {
                builder.startCall(receiverAccess, targetMethod.getSimpleName().toString());
            }
            List<? extends VariableElement> parameters = message.getExecutable().getParameters();
            for (VariableElement parameter : parameters.subList(1, parameters.size())) {
                builder.string(parameter.getSimpleName().toString());
            }
            builder.end();
            builder.end();
        }
        return cachedExecute;
    }

    private void injectReceiverType(CodeExecutableElement executable, ExportsLibrary library, TypeMirror receiverType, boolean cached) {
        TypeMirror modelReceiverType;
        boolean isAccepts = executable.getSimpleName().toString().equals(ACCEPTS);
        if (isAccepts) {
            modelReceiverType = context.getType(Object.class);
        } else {
            modelReceiverType = library.getLibrary().getSignatureReceiverType();
        }
        if (!ElementUtils.needsCastTo(modelReceiverType, receiverType)) {
            return;
        }
        CodeVariableElement receiverParam = ((CodeVariableElement) executable.getParameters().get(0));
        receiverParam.setType(modelReceiverType);
        String originalReceiverParamName = receiverParam.getName();
        String newReceiverParamName = originalReceiverParamName + "_";
        receiverParam.setName(newReceiverParamName);
        CodeTree tree = executable.getBodyTree();
        CodeTreeBuilder executeBody = executable.createBuilder();
        if (!isAccepts) {
            addAcceptsAssertion(executeBody);
        }
        CodeTree cast = createReceiverCast(library, modelReceiverType, receiverType, CodeTreeBuilder.singleString(newReceiverParamName), cached);
        executeBody.declaration(receiverType, originalReceiverParamName, cast);
        executeBody.tree(tree);
    }

    private CodeTree createReceiverCast(ExportsLibrary library, TypeMirror sourceType, TypeMirror targetType, CodeTree receiver, boolean cached) {
        CodeTree cast;
        if (!cached || library.isFinalReceiver()) {
            if (ElementUtils.needsCastTo(sourceType, targetType)) {
                cast = CodeTreeBuilder.createBuilder().cast(targetType).tree(receiver).build();
            } else {
                cast = receiver;
            }
        } else {
            if (library.needsDynamicDispatch()) {
                cast = CodeTreeBuilder.createBuilder().cast(targetType).startCall("dynamicDispatch_.cast").tree(receiver).end().build();
            } else {
                cast = CodeTreeBuilder.createBuilder().startStaticCall(context.getType(CompilerDirectives.class), "castExact").tree(receiver).string("receiverClass_").end().build();
            }
        }
        return cast;
    }

    private static void addAcceptsAssertion(CodeTreeBuilder executeBody) {
        String name = executeBody.findMethod().getParameters().get(0).getSimpleName().toString();
        executeBody.startAssert().string("this.accepts(", name, ")").string(" : ").doubleQuote("Invalid library usage. Library does not accept given receiver.").end();
    }

}
