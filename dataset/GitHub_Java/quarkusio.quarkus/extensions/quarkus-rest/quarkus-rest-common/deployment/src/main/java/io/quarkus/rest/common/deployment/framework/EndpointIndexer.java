package io.quarkus.rest.common.deployment.framework;

import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.BEAN_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.BIG_DECIMAL;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.BIG_INTEGER;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.BLOCKING;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.BOOLEAN;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.CHARACTER;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.COMPLETION_STAGE;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.CONSUMES;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.CONTEXT;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.COOKIE_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.DEFAULT_VALUE;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.DOUBLE;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.FLOAT;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.FORM_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.HEADER_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.INTEGER;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.LIST;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.LONG;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.MATRIX_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.MULTI;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.MULTI_VALUED_MAP;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PATH;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PATH_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PATH_SEGMENT;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRIMITIVE_BOOLEAN;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRIMITIVE_CHAR;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRIMITIVE_DOUBLE;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRIMITIVE_FLOAT;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRIMITIVE_INTEGER;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRIMITIVE_LONG;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.PRODUCES;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.QUERY_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REQUIRE_CDI_REQUEST_SCOPE;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REST_COOKIE_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REST_FORM_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REST_HEADER_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REST_MATRIX_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REST_PATH_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.REST_QUERY_PARAM;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.SET;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.SORTED_SET;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.STRING;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.SUSPENDED;
import static io.quarkus.rest.common.deployment.framework.QuarkusRestDotNames.UNI;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.SseEventSink;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.jboss.jandex.TypeVariable;
import org.jboss.logging.Logger;

import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.util.AsmUtil;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.rest.common.runtime.QuarkusRestCommonRecorder;
import io.quarkus.rest.common.runtime.QuarkusRestConfig;
import io.quarkus.rest.common.runtime.model.InjectableBean;
import io.quarkus.rest.common.runtime.model.MethodParameter;
import io.quarkus.rest.common.runtime.model.ParameterType;
import io.quarkus.rest.common.runtime.model.ResourceClass;
import io.quarkus.rest.common.runtime.model.ResourceMethod;
import io.quarkus.rest.common.runtime.util.URLUtils;
import io.quarkus.rest.spi.EndpointInvoker;
import io.quarkus.runtime.util.HashUtil;

public abstract class EndpointIndexer<T extends EndpointIndexer<T, PARAM>, PARAM extends IndexedParameter<PARAM>> {

    protected static final Map<String, String> primitiveTypes;
    private static final Map<DotName, Class<?>> supportedReaderJavaTypes;
    // NOTE: sync with ContextProducer and ContextParamExtractor
    private static final Set<DotName> CONTEXT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // spec
            QuarkusRestDotNames.URI_INFO,
            QuarkusRestDotNames.HTTP_HEADERS,
            QuarkusRestDotNames.REQUEST,
            QuarkusRestDotNames.SECURITY_CONTEXT,
            QuarkusRestDotNames.PROVIDERS,
            QuarkusRestDotNames.RESOURCE_CONTEXT,
            QuarkusRestDotNames.CONFIGURATION,
            QuarkusRestDotNames.SSE,
            QuarkusRestDotNames.SSE_EVENT_SINK,
            // extras
            QuarkusRestDotNames.QUARKUS_REST_CONTEXT,
            DotName.createSimple("io.quarkus.rest.server.runtime.spi.SimplifiedResourceInfo"), //TODO: fixme
            QuarkusRestDotNames.RESOURCE_INFO,
            QuarkusRestDotNames.HTTP_SERVER_REQUEST,
            QuarkusRestDotNames.HTTP_SERVER_RESPONSE)));

    protected static final Logger log = Logger.getLogger(EndpointInvoker.class);
    private static final String[] PRODUCES_PLAIN_TEXT_NEGOTIATED = new String[] { MediaType.TEXT_PLAIN, MediaType.WILDCARD };
    private static final String[] PRODUCES_PLAIN_TEXT = new String[] { MediaType.TEXT_PLAIN };

    static {
        Map<String, String> prims = new HashMap<>();
        prims.put(byte.class.getName(), Byte.class.getName());
        prims.put(Byte.class.getName(), Byte.class.getName());
        prims.put(boolean.class.getName(), Boolean.class.getName());
        prims.put(Boolean.class.getName(), Boolean.class.getName());
        prims.put(char.class.getName(), Character.class.getName());
        prims.put(Character.class.getName(), Character.class.getName());
        prims.put(short.class.getName(), Short.class.getName());
        prims.put(Short.class.getName(), Short.class.getName());
        prims.put(int.class.getName(), Integer.class.getName());
        prims.put(Integer.class.getName(), Integer.class.getName());
        prims.put(float.class.getName(), Float.class.getName());
        prims.put(Float.class.getName(), Float.class.getName());
        prims.put(double.class.getName(), Double.class.getName());
        prims.put(Double.class.getName(), Double.class.getName());
        prims.put(long.class.getName(), Long.class.getName());
        prims.put(Long.class.getName(), Long.class.getName());
        primitiveTypes = Collections.unmodifiableMap(prims);

        Map<DotName, Class<?>> supportedReaderJavaTps = new HashMap<>();
        supportedReaderJavaTps.put(PRIMITIVE_BOOLEAN, boolean.class);
        supportedReaderJavaTps.put(PRIMITIVE_DOUBLE, double.class);
        supportedReaderJavaTps.put(PRIMITIVE_FLOAT, float.class);
        supportedReaderJavaTps.put(PRIMITIVE_LONG, long.class);
        supportedReaderJavaTps.put(PRIMITIVE_INTEGER, int.class);
        supportedReaderJavaTps.put(PRIMITIVE_CHAR, char.class);
        supportedReaderJavaTps.put(BOOLEAN, Boolean.class);
        supportedReaderJavaTps.put(DOUBLE, Double.class);
        supportedReaderJavaTps.put(FLOAT, Float.class);
        supportedReaderJavaTps.put(LONG, Long.class);
        supportedReaderJavaTps.put(INTEGER, Integer.class);
        supportedReaderJavaTps.put(CHARACTER, Character.class);
        supportedReaderJavaTps.put(BIG_DECIMAL, BigDecimal.class);
        supportedReaderJavaTps.put(BIG_INTEGER, BigInteger.class);
        supportedReaderJavaTypes = Collections.unmodifiableMap(supportedReaderJavaTps);
    }

    protected final IndexView index;
    private final BeanContainer beanContainer;
    protected final BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer;
    private final BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildItemBuildProducer;
    private final QuarkusRestCommonRecorder recorder;
    private final Map<String, String> existingConverters;
    private final Map<DotName, String> scannedResourcePaths;
    private final QuarkusRestConfig config;
    private final AdditionalReaders additionalReaders;
    private final Map<DotName, String> httpAnnotationToMethod;
    private final Map<String, InjectableBean> injectableBeans;
    private final AdditionalWriters additionalWriters;
    private final boolean hasRuntimeConverters;
    private final boolean defaultBlocking;

    protected EndpointIndexer(Builder<T, ?> builder) {
        this.index = builder.index;
        this.beanContainer = builder.beanContainer;
        this.generatedClassBuildItemBuildProducer = builder.generatedClassBuildItemBuildProducer;
        this.bytecodeTransformerBuildItemBuildProducer = builder.bytecodeTransformerBuildItemBuildProducer;
        this.recorder = builder.recorder;
        this.existingConverters = builder.existingConverters;
        this.scannedResourcePaths = builder.scannedResourcePaths;
        this.config = builder.config;
        this.additionalReaders = builder.additionalReaders;
        this.httpAnnotationToMethod = builder.httpAnnotationToMethod;
        this.injectableBeans = builder.injectableBeans;
        this.additionalWriters = builder.additionalWriters;
        this.hasRuntimeConverters = builder.hasRuntimeConverters;
        this.defaultBlocking = builder.defaultBlocking;
    }

    public ResourceClass createEndpoints(ClassInfo classInfo) {
        try {
            String path = scannedResourcePaths.get(classInfo.name());
            ResourceClass clazz = new ResourceClass();
            clazz.setClassName(classInfo.name().toString());
            if (path != null) {
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                clazz.setPath(path);
            }
            clazz.setFactory(recorder.factory(clazz.getClassName(), beanContainer));
            List<ResourceMethod> methods = createEndpoints(classInfo, classInfo, new HashSet<>(),
                    clazz.getPathParameters());
            clazz.getMethods().addAll(methods);

            // get an InjectableBean view of our class
            InjectableBean injectableBean = scanInjectableBean(classInfo, classInfo,
                    bytecodeTransformerBuildItemBuildProducer, existingConverters,
                    additionalReaders, injectableBeans, hasRuntimeConverters);

            // at this point we've scanned the class and its bean infos, which can have form params
            if (injectableBean.isFormParamRequired()) {
                clazz.setFormParamRequired(true);
                // we must propagate this to all our methods, regardless of what they thought they required
                for (ResourceMethod method : methods) {
                    method.setFormParamRequired(true);
                }
            }
            if (injectableBean.isInjectionRequired()) {
                clazz.setPerRequestResource(true);
            }

            return clazz;
        } catch (Exception e) {
            if (Modifier.isInterface(classInfo.flags()) || Modifier.isAbstract(classInfo.flags())) {
                //kinda bogus, but we just ignore failed interfaces for now
                //they can have methods that are not valid until they are actually extended by a concrete type
                log.debug("Ignoring interface " + classInfo.name(), e);
                return null;
            }
            throw new RuntimeException(e);
        }
    }

    protected List<ResourceMethod> createEndpoints(ClassInfo currentClassInfo,
            ClassInfo actualEndpointInfo, Set<String> seenMethods,
            Set<String> pathParameters) {
        List<ResourceMethod> ret = new ArrayList<>();
        String[] classProduces = extractProducesConsumesValues(currentClassInfo.classAnnotation(PRODUCES));
        String[] classConsumes = extractProducesConsumesValues(currentClassInfo.classAnnotation(CONSUMES));
        Set<String> classNameBindings = NameBindingUtil.nameBindingNames(index, currentClassInfo);

        for (DotName httpMethod : httpAnnotationToMethod.keySet()) {
            List<AnnotationInstance> foundMethods = currentClassInfo.annotations().get(httpMethod);
            if (foundMethods != null) {
                for (AnnotationInstance annotation : foundMethods) {
                    MethodInfo info = annotation.target().asMethod();
                    if (!hasProperModifiers(info)) {
                        continue;
                    }
                    String descriptor = methodDescriptor(info);
                    if (seenMethods.contains(descriptor)) {
                        continue;
                    }
                    seenMethods.add(descriptor);
                    String methodPath = readStringValue(info.annotation(PATH));
                    if (methodPath != null) {
                        if (!methodPath.startsWith("/")) {
                            methodPath = "/" + methodPath;
                        }
                    } else {
                        methodPath = "/";
                    }
                    ResourceMethod method = createResourceMethod(currentClassInfo, actualEndpointInfo,
                            classProduces, classConsumes, classNameBindings, httpMethod, info, methodPath, pathParameters);

                    ret.add(method);
                }
            }
        }
        //now resource locator methods
        List<AnnotationInstance> foundMethods = currentClassInfo.annotations().get(PATH);
        if (foundMethods != null) {
            for (AnnotationInstance annotation : foundMethods) {
                if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                    MethodInfo info = annotation.target().asMethod();
                    if (!hasProperModifiers(info)) {
                        continue;
                    }
                    String descriptor = methodDescriptor(info);
                    if (seenMethods.contains(descriptor)) {
                        continue;
                    }
                    seenMethods.add(descriptor);
                    String methodPath = readStringValue(annotation);
                    if (methodPath != null) {
                        if (!methodPath.startsWith("/")) {
                            methodPath = "/" + methodPath;
                        }
                    }
                    ResourceMethod method = createResourceMethod(currentClassInfo, actualEndpointInfo,
                            classProduces, classConsumes, classNameBindings, null, info, methodPath,
                            pathParameters);
                    ret.add(method);
                }
            }
        }

        DotName superClassName = currentClassInfo.superName();
        if (superClassName != null && !superClassName.equals(DotNames.OBJECT)) {
            ClassInfo superClass = index.getClassByName(superClassName);
            if (superClass != null) {
                ret.addAll(createEndpoints(superClass, actualEndpointInfo, seenMethods,
                        pathParameters));
            }
        }
        List<DotName> interfaces = currentClassInfo.interfaceNames();
        for (DotName i : interfaces) {
            ClassInfo superClass = index.getClassByName(i);
            if (superClass != null) {
                ret.addAll(createEndpoints(superClass, actualEndpointInfo, seenMethods,
                        pathParameters));
            }
        }
        return ret;
    }

    private boolean hasProperModifiers(MethodInfo info) {
        if ((info.flags() & Modifier.PUBLIC) == 0) {
            log.warn("Method '" + info.name() + " of Resource class '" + info.declaringClass().name()
                    + "' it not public and will therefore be ignored");
            return false;
        }
        if ((info.flags() & Modifier.STATIC) != 0) {
            log.warn("Method '" + info.name() + " of Resource class '" + info.declaringClass().name()
                    + "' it static and will therefore be ignored");
            return false;
        }
        return true;
    }

    private ResourceMethod createResourceMethod(ClassInfo currentClassInfo, ClassInfo actualEndpointInfo,
            String[] classProduces, String[] classConsumes, Set<String> classNameBindings, DotName httpMethod, MethodInfo info,
            String methodPath,
            Set<String> classPathParameters) {
        try {
            Set<String> pathParameters = new HashSet<>(classPathParameters);
            URLUtils.parsePathParameters(methodPath, pathParameters);
            Map<DotName, AnnotationInstance>[] parameterAnnotations = new Map[info.parameters().size()];
            MethodParameter[] methodParameters = new MethodParameter[info.parameters()
                    .size()];
            for (int paramPos = 0; paramPos < info.parameters().size(); ++paramPos) {
                parameterAnnotations[paramPos] = new HashMap<>();
            }
            for (AnnotationInstance i : info.annotations()) {
                if (i.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                    parameterAnnotations[i.target().asMethodParameter().position()].put(i.name(), i);
                }
            }
            String[] consumes = extractProducesConsumesValues(info.annotation(CONSUMES), classConsumes);
            boolean suspended = false;
            boolean sse = false;
            boolean formParamRequired = false;
            boolean hasBodyParam = false;
            for (int i = 0; i < methodParameters.length; ++i) {
                Map<DotName, AnnotationInstance> anns = parameterAnnotations[i];
                boolean encoded = anns.containsKey(QuarkusRestDotNames.ENCODED);
                Type paramType = info.parameters().get(i);
                String errorLocation = "method " + info + " on class " + info.declaringClass();

                PARAM parameterResult = extractParameterInfo(currentClassInfo, actualEndpointInfo,
                        existingConverters, additionalReaders,
                        anns, paramType, errorLocation, false, hasRuntimeConverters, pathParameters, info.parameterName(i));
                suspended |= parameterResult.isSuspended();
                sse |= parameterResult.isSse();
                String name = parameterResult.getName();
                String defaultValue = parameterResult.getDefaultValue();
                ParameterType type = parameterResult.getType();
                if (type == ParameterType.BODY) {
                    if (hasBodyParam)
                        throw new RuntimeException(
                                "Resource method " + info + " can only have a single body parameter: " + info.parameterName(i));
                    hasBodyParam = true;
                }
                String elementType = parameterResult.getElementType();
                boolean single = parameterResult.isSingle();
                if (defaultValue == null && paramType.kind() == Type.Kind.PRIMITIVE) {
                    defaultValue = "0";
                }
                methodParameters[i] = createMethodParameter(currentClassInfo, actualEndpointInfo, encoded, paramType,
                        parameterResult, name, defaultValue, type, elementType, single);

                if (type == ParameterType.BEAN) {
                    // transform the bean param
                    ClassInfo beanParamClassInfo = index.getClassByName(paramType.name());
                    InjectableBean injectableBean = scanInjectableBean(beanParamClassInfo,
                            actualEndpointInfo,
                            bytecodeTransformerBuildItemBuildProducer,
                            existingConverters, additionalReaders, injectableBeans, hasRuntimeConverters);
                    if (injectableBean.isFormParamRequired()) {
                        formParamRequired = true;
                    }
                } else if (type == ParameterType.FORM) {
                    formParamRequired = true;
                }
            }
            Type nonAsyncReturnType = getNonAsyncReturnType(info.returnType());
            addWriterForType(additionalWriters, nonAsyncReturnType);

            String[] produces = extractProducesConsumesValues(info.annotation(PRODUCES), classProduces);
            produces = applyDefaultProduces(produces, nonAsyncReturnType);
            Set<String> nameBindingNames = nameBindingNames(info, classNameBindings);
            boolean blocking = defaultBlocking;
            AnnotationInstance blockingAnnotation = getInheritableAnnotation(info, BLOCKING);
            if (blockingAnnotation != null) {
                AnnotationValue value = blockingAnnotation.value();
                if (value != null) {
                    blocking = value.asBoolean();
                } else {
                    blocking = true;
                }
            }
            boolean cdiRequestScopeRequired = true;
            AnnotationInstance cdiRequestScopeRequiredAnnotation = getInheritableAnnotation(info, REQUIRE_CDI_REQUEST_SCOPE);
            if (cdiRequestScopeRequiredAnnotation != null) {
                AnnotationValue value = cdiRequestScopeRequiredAnnotation.value();
                if (value != null) {
                    cdiRequestScopeRequired = value.asBoolean();
                } else {
                    cdiRequestScopeRequired = true;
                }
            }

            ResourceMethod method = new ResourceMethod()
                    .setHttpMethod(httpMethod == null ? null : httpAnnotationToMethod.get(httpMethod))
                    .setPath(methodPath)
                    .setConsumes(consumes)
                    .setProduces(produces)
                    .setNameBindingNames(nameBindingNames)
                    .setName(info.name())
                    .setBlocking(blocking)
                    .setSuspended(suspended)
                    .setSse(sse)
                    .setFormParamRequired(formParamRequired)
                    .setCDIRequestScopeRequired(cdiRequestScopeRequired)
                    .setParameters(methodParameters)
                    .setSimpleReturnType(toClassName(info.returnType(), currentClassInfo, actualEndpointInfo, index))
                    // FIXME: resolved arguments ?
                    .setReturnType(AsmUtil.getSignature(info.returnType(), new Function<String, String>() {
                        @Override
                        public String apply(String v) {
                            //we attempt to resolve type variables
                            ClassInfo declarer = info.declaringClass();
                            int pos = -1;
                            for (;;) {
                                if (declarer == null) {
                                    return null;
                                }
                                List<TypeVariable> typeParameters = declarer.typeParameters();
                                for (int i = 0; i < typeParameters.size(); i++) {
                                    TypeVariable tv = typeParameters.get(i);
                                    if (tv.identifier().equals(v)) {
                                        pos = i;
                                    }
                                }
                                if (pos != -1) {
                                    break;
                                }
                                declarer = index.getClassByName(declarer.superName());
                            }
                            Type type = JandexUtil
                                    .resolveTypeParameters(info.declaringClass().name(), declarer.name(), index)
                                    .get(pos);
                            if (type.kind() == Type.Kind.TYPE_VARIABLE && type.asTypeVariable().identifier().equals(v)) {
                                List<Type> bounds = type.asTypeVariable().bounds();
                                if (bounds.isEmpty()) {
                                    return "Ljava/lang/Object;";
                                }
                                return AsmUtil.getSignature(bounds.get(0), this);
                            } else {
                                return AsmUtil.getSignature(type, this);
                            }
                        }
                    }));

            StringBuilder sigBuilder = new StringBuilder();
            sigBuilder.append(method.getName())
                    .append(method.getReturnType());
            for (MethodParameter t : method.getParameters()) {
                sigBuilder.append(t);
            }
            String baseName = currentClassInfo.name() + "$quarkusrestinvoker$" + method.getName() + "_"
                    + HashUtil.sha1(sigBuilder.toString());
            try (ClassCreator classCreator = new ClassCreator(
                    new GeneratedClassGizmoAdaptor(generatedClassBuildItemBuildProducer, true), baseName, null,
                    Object.class.getName(), EndpointInvoker.class.getName())) {
                MethodCreator mc = classCreator.getMethodCreator("invoke", Object.class, Object.class, Object[].class);
                ResultHandle[] args = new ResultHandle[method.getParameters().length];
                ResultHandle array = mc.getMethodParam(1);
                for (int i = 0; i < method.getParameters().length; ++i) {
                    args[i] = mc.readArrayValue(array, i);
                }
                ResultHandle res;
                if (Modifier.isInterface(currentClassInfo.flags())) {
                    res = mc.invokeInterfaceMethod(info, mc.getMethodParam(0), args);
                } else {
                    res = mc.invokeVirtualMethod(info, mc.getMethodParam(0), args);
                }
                if (info.returnType().kind() == Type.Kind.VOID) {
                    mc.returnValue(mc.loadNull());
                } else {
                    mc.returnValue(res);
                }
            }
            method.setInvoker(recorder.invoker(baseName));
            return method;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process method " + info.declaringClass().name() + "#" + info.toString(), e);
        }
    }

    protected abstract InjectableBean scanInjectableBean(ClassInfo currentClassInfo,
            ClassInfo actualEndpointInfo,
            BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildProducer,
            Map<String, String> existingConverters,
            AdditionalReaders additionalReaders,
            Map<String, InjectableBean> injectableBeans,
            boolean hasRuntimeConverters);

    protected abstract MethodParameter createMethodParameter(ClassInfo currentClassInfo, ClassInfo actualEndpointInfo,
            boolean encoded, Type paramType, PARAM parameterResult, String name, String defaultValue,
            ParameterType type, String elementType, boolean single);

    private String[] applyDefaultProduces(String[] produces, Type nonAsyncReturnType) {
        if (produces != null && produces.length != 0)
            return produces;
        // FIXME: primitives
        if (STRING.equals(nonAsyncReturnType.name()))
            return config.singleDefaultProduces ? PRODUCES_PLAIN_TEXT : PRODUCES_PLAIN_TEXT_NEGOTIATED;
        // FIXME: JSON
        return produces;
    }

    private Type getNonAsyncReturnType(Type returnType) {
        switch (returnType.kind()) {
            case ARRAY:
            case CLASS:
            case PRIMITIVE:
            case VOID:
                return returnType;
            case PARAMETERIZED_TYPE:
                // NOTE: same code in QuarkusRestRecorder.getNonAsyncReturnType
                ParameterizedType parameterizedType = returnType.asParameterizedType();
                if (COMPLETION_STAGE.equals(parameterizedType.name())
                        || UNI.equals(parameterizedType.name())
                        || MULTI.equals(parameterizedType.name())) {
                    return parameterizedType.arguments().get(0);
                }
                return returnType;
            default:
        }
        return returnType;
        // FIXME: should be an exception, but we have incomplete support for generics ATM, so we still
        // have some unresolved type vars and they do pass _some_ tests 
        //        throw new UnsupportedOperationException("Endpoint return type not supported yet: " + returnType);
    }

    protected abstract void addWriterForType(AdditionalWriters additionalWriters, Type paramType);

    protected abstract void addReaderForType(AdditionalReaders additionalReaders, Type paramType);

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getSupportedReaderJavaClass(Type paramType) {
        Class<T> result = (Class<T>) supportedReaderJavaTypes.get(paramType.name());
        return Objects.requireNonNull(result);
    }

    private static AnnotationInstance getInheritableAnnotation(MethodInfo info, DotName name) {
        // try method first, class second
        AnnotationInstance annotation = info.annotation(name);
        if (annotation == null) {
            annotation = info.declaringClass().classAnnotation(name);
        }
        return annotation;
    }

    private static String methodDescriptor(MethodInfo info) {
        return info.name() + ":" + AsmUtil.getDescriptor(info, s -> null);
    }

    private static boolean moreThanOne(AnnotationInstance... annotations) {
        boolean oneNonNull = false;
        for (AnnotationInstance annotation : annotations) {
            if (annotation != null) {
                if (oneNonNull)
                    return true;
                oneNonNull = true;
            }
        }
        return false;
    }

    private static String[] extractProducesConsumesValues(AnnotationInstance annotation, String[] defaultValue) {
        String[] read = extractProducesConsumesValues(annotation);
        if (read == null) {
            return defaultValue;
        }
        return read;
    }

    private static String[] extractProducesConsumesValues(AnnotationInstance annotation) {
        if (annotation == null) {
            return null;
        }
        String[] originalStrings = annotation.value().asStringArray();
        if (originalStrings.length > 0) {
            List<String> result = new ArrayList<>(originalStrings.length);
            for (String s : originalStrings) {
                String[] trimmed = s.split(","); // spec says that the value can be a comma separated list...
                for (String t : trimmed) {
                    result.add(t.trim());
                }
            }
            return result.toArray(new String[0]);
        } else {
            return originalStrings;
        }

    }

    public static String readStringValue(AnnotationInstance annotationInstance) {
        String classProduces = null;
        if (annotationInstance != null) {
            classProduces = annotationInstance.value().asString();
        }
        return classProduces;
    }

    protected static String toClassName(Type indexType, ClassInfo currentClass, ClassInfo actualEndpointClass,
            IndexView indexView) {
        switch (indexType.kind()) {
            case VOID:
                return "void";
            case CLASS:
                return indexType.asClassType().name().toString();
            case PRIMITIVE:
                return indexType.asPrimitiveType().primitive().name().toLowerCase(Locale.ENGLISH);
            case PARAMETERIZED_TYPE:
                return indexType.asParameterizedType().name().toString();
            case ARRAY:
                return indexType.asArrayType().name().toString();
            case TYPE_VARIABLE:
                TypeVariable typeVariable = indexType.asTypeVariable();
                if (typeVariable.bounds().isEmpty()) {
                    return Object.class.getName();
                }
                int pos = -1;
                for (int i = 0; i < currentClass.typeParameters().size(); ++i) {
                    if (currentClass.typeParameters().get(i).identifier().equals(typeVariable.identifier())) {
                        pos = i;
                        break;
                    }
                }
                if (pos != -1) {
                    List<Type> params = JandexUtil.resolveTypeParameters(actualEndpointClass.name(), currentClass.name(),
                            indexView);

                    Type resolved = params.get(pos);
                    if (resolved.kind() != Type.Kind.TYPE_VARIABLE
                            || !resolved.asTypeVariable().identifier().equals(typeVariable.identifier())) {
                        return toClassName(resolved, currentClass, actualEndpointClass, indexView);
                    }
                }
                return toClassName(typeVariable.bounds().get(0), currentClass, actualEndpointClass, indexView);
            default:
                throw new RuntimeException("Unknown parameter type " + indexType);
        }
    }

    private static String appendPath(String prefix, String suffix) {
        if (prefix == null) {
            return suffix;
        } else if (suffix == null) {
            return prefix;
        }
        if ((prefix.endsWith("/") && !suffix.startsWith("/")) ||
                (!prefix.endsWith("/") && suffix.startsWith("/"))) {
            return prefix + suffix;
        } else if (prefix.endsWith("/")) {
            return prefix.substring(0, prefix.length() - 1) + suffix;
        } else {
            return prefix + "/" + suffix;
        }
    }

    protected abstract PARAM createIndexedParam();

    public PARAM extractParameterInfo(ClassInfo currentClassInfo, ClassInfo actualEndpointInfo,
            Map<String, String> existingConverters, AdditionalReaders additionalReaders,
            Map<DotName, AnnotationInstance> anns, Type paramType, String errorLocation, boolean field,
            boolean hasRuntimeConverters, Set<String> pathParameters, String sourceName) {
        PARAM builder = createIndexedParam()
                .setCurrentClassInfo(currentClassInfo)
                .setActualEndpointInfo(actualEndpointInfo)
                .setExistingConverters(existingConverters)
                .setAdditionalReaders(additionalReaders)
                .setAnns(anns)
                .setParamType(paramType)
                .setErrorLocation(errorLocation)
                .setField(field)
                .setHasRuntimeConverters(hasRuntimeConverters)
                .setPathParameters(pathParameters)
                .setSourceName(sourceName);

        AnnotationInstance beanParam = anns.get(BEAN_PARAM);
        AnnotationInstance pathParam = anns.get(PATH_PARAM);
        AnnotationInstance queryParam = anns.get(QUERY_PARAM);
        AnnotationInstance headerParam = anns.get(HEADER_PARAM);
        AnnotationInstance formParam = anns.get(FORM_PARAM);
        AnnotationInstance matrixParam = anns.get(MATRIX_PARAM);
        AnnotationInstance cookieParam = anns.get(COOKIE_PARAM);
        AnnotationInstance restPathParam = anns.get(REST_PATH_PARAM);
        AnnotationInstance restQueryParam = anns.get(REST_QUERY_PARAM);
        AnnotationInstance restHeaderParam = anns.get(REST_HEADER_PARAM);
        AnnotationInstance restFormParam = anns.get(REST_FORM_PARAM);
        AnnotationInstance restMatrixParam = anns.get(REST_MATRIX_PARAM);
        AnnotationInstance restCookieParam = anns.get(REST_COOKIE_PARAM);
        AnnotationInstance contextParam = anns.get(CONTEXT);
        AnnotationInstance defaultValueAnnotation = anns.get(DEFAULT_VALUE);
        AnnotationInstance suspendedAnnotation = anns.get(SUSPENDED);
        boolean convertable = false;
        if (defaultValueAnnotation != null) {
            builder.setDefaultValue(defaultValueAnnotation.value().asString());
        }
        if (moreThanOne(pathParam, queryParam, headerParam, formParam, cookieParam, contextParam, beanParam,
                restPathParam, restQueryParam, restHeaderParam, restFormParam, restCookieParam)) {
            throw new RuntimeException(
                    "Cannot have more than one of @PathParam, @QueryParam, @HeaderParam, @FormParam, @CookieParam, @BeanParam, @Context on "
                            + errorLocation);
        } else if (pathParam != null) {
            builder.setName(pathParam.value().asString());
            builder.setType(ParameterType.PATH);
            convertable = true;
        } else if (restPathParam != null) {
            builder.setName(valueOrDefault(restPathParam.value(), sourceName));
            builder.setType(ParameterType.PATH);
            convertable = true;
        } else if (queryParam != null) {
            builder.setName(queryParam.value().asString());
            builder.setType(ParameterType.QUERY);
            convertable = true;
        } else if (restQueryParam != null) {
            builder.setName(valueOrDefault(restQueryParam.value(), sourceName));
            builder.setType(ParameterType.QUERY);
            convertable = true;
        } else if (cookieParam != null) {
            builder.setName(cookieParam.value().asString());
            builder.setType(ParameterType.COOKIE);
            convertable = true;
        } else if (restCookieParam != null) {
            builder.setName(valueOrDefault(restCookieParam.value(), sourceName));
            builder.setType(ParameterType.COOKIE);
            convertable = true;
        } else if (headerParam != null) {
            builder.setName(headerParam.value().asString());
            builder.setType(ParameterType.HEADER);
            convertable = true;
        } else if (restHeaderParam != null) {
            builder.setName(valueOrDefault(restHeaderParam.value(), sourceName));
            builder.setType(ParameterType.HEADER);
            convertable = true;
        } else if (formParam != null) {
            builder.setName(formParam.value().asString());
            builder.setType(ParameterType.FORM);
            convertable = true;
        } else if (restFormParam != null) {
            builder.setName(valueOrDefault(restFormParam.value(), sourceName));
            builder.setType(ParameterType.FORM);
            convertable = true;
        } else if (matrixParam != null) {
            builder.setName(matrixParam.value().asString());
            builder.setType(ParameterType.MATRIX);
            convertable = true;
        } else if (restMatrixParam != null) {
            builder.setName(valueOrDefault(restMatrixParam.value(), sourceName));
            builder.setType(ParameterType.MATRIX);
            convertable = true;
        } else if (contextParam != null) {
            //this is handled by CDI
            if (field) {
                return builder;
            }
            // no name required
            builder.setType(ParameterType.CONTEXT);
        } else if (beanParam != null) {
            // no name required
            builder.setType(ParameterType.BEAN);
        } else if (suspendedAnnotation != null) {
            // no name required
            builder.setType(ParameterType.ASYNC_RESPONSE);
            builder.setSuspended(true);
        } else {
            // auto context parameters
            if (!field
                    && paramType.kind() == Kind.CLASS
                    && isContextType(paramType.asClassType())) {
                // no name required
                builder.setType(ParameterType.CONTEXT);
            } else if (!field && pathParameters.contains(sourceName)) {
                builder.setName(sourceName);
                builder.setType(ParameterType.PATH);
                convertable = true;
            } else {
                //unannoated field
                //just ignore it
                if (field) {
                    return builder;
                }
                builder.setType(ParameterType.BODY);
            }
        }
        builder.setSingle(true);
        boolean typeHandled = false;
        String elementType = null;
        final ParameterType type = builder.getType();
        if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            typeHandled = true;
            ParameterizedType pt = paramType.asParameterizedType();
            if (pt.name().equals(LIST)) {
                builder.setSingle(false);
                elementType = toClassName(pt.arguments().get(0), currentClassInfo, actualEndpointInfo, index);
                handleListParam(existingConverters, errorLocation, hasRuntimeConverters, builder, elementType);
            } else if (pt.name().equals(SET)) {
                builder.setSingle(false);
                elementType = toClassName(pt.arguments().get(0), currentClassInfo, actualEndpointInfo, index);
                handleSetParam(existingConverters, errorLocation, hasRuntimeConverters, builder, elementType);
            } else if (pt.name().equals(SORTED_SET)) {
                builder.setSingle(false);
                elementType = toClassName(pt.arguments().get(0), currentClassInfo, actualEndpointInfo, index);
                handleSortedSetParam(existingConverters, errorLocation, hasRuntimeConverters, builder, elementType);
            } else if ((pt.name().equals(MULTI_VALUED_MAP)) && (type == ParameterType.BODY)) {
                elementType = pt.name().toString();
                handleMultiMapParam(additionalReaders, builder);
            } else if (convertable) {
                throw new RuntimeException("Invalid parameter type '" + pt + "' used on method " + errorLocation);
            } else {
                typeHandled = false;
            }
        } else if ((paramType.name().equals(PATH_SEGMENT)) && (type == ParameterType.PATH)) {
            elementType = paramType.name().toString();
            handlePathSegmentParam(builder);
            typeHandled = true;
        }
        if (!typeHandled) {
            elementType = toClassName(paramType, currentClassInfo, actualEndpointInfo, index);
            addReaderForType(additionalReaders, paramType);

            if (type != ParameterType.CONTEXT && type != ParameterType.BEAN && type != ParameterType.BODY
                    && type != ParameterType.ASYNC_RESPONSE) {
                handleOtherParam(existingConverters, errorLocation, hasRuntimeConverters, builder, elementType);
            }
            if (type == ParameterType.CONTEXT && elementType.equals(SseEventSink.class.getName())) {

                builder.setSse(true);
            }
        }
        if (suspendedAnnotation != null && !elementType.equals(AsyncResponse.class.getName())) {
            throw new RuntimeException("Can only inject AsyncResponse on methods marked @Suspended");
        }
        builder.setElementType(elementType);
        return builder;
    }

    protected void handlePathSegmentParam(PARAM builder) {
    }

    protected void handleOtherParam(Map<String, String> existingConverters, String errorLocation, boolean hasRuntimeConverters,
            PARAM builder, String elementType) {
    }

    protected void handleMultiMapParam(AdditionalReaders additionalReaders, PARAM builder) {
    }

    protected void handleSortedSetParam(Map<String, String> existingConverters, String errorLocation,
            boolean hasRuntimeConverters, PARAM builder, String elementType) {
    }

    protected void handleSetParam(Map<String, String> existingConverters, String errorLocation, boolean hasRuntimeConverters,
            PARAM builder, String elementType) {
    }

    protected void handleListParam(Map<String, String> existingConverters, String errorLocation, boolean hasRuntimeConverters,
            PARAM builder, String elementType) {
    }

    private boolean isContextType(ClassType klass) {
        return CONTEXT_TYPES.contains(klass.name());
    }

    private String valueOrDefault(AnnotationValue annotation, String defaultValue) {
        if (annotation == null)
            return defaultValue;
        String val = annotation.asString();
        return val != null && !val.isEmpty() ? val : defaultValue;
    }

    public Set<String> nameBindingNames(ClassInfo selectedAppClass) {
        return NameBindingUtil.nameBindingNames(index, selectedAppClass);
    }

    public Set<String> nameBindingNames(MethodInfo methodInfo, Set<String> forClass) {
        return NameBindingUtil.nameBindingNames(index, methodInfo, forClass);
    }

    public static abstract class Builder<T extends EndpointIndexer<T, ?>, B extends Builder<T, B>> {
        private boolean defaultBlocking;
        private IndexView index;
        private BeanContainer beanContainer;
        private BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer;
        private BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildItemBuildProducer;
        private QuarkusRestCommonRecorder recorder;
        private Map<String, String> existingConverters;
        private Map<DotName, String> scannedResourcePaths;
        private QuarkusRestConfig config;
        private AdditionalReaders additionalReaders;
        private Map<DotName, String> httpAnnotationToMethod;
        private Map<String, InjectableBean> injectableBeans;
        private AdditionalWriters additionalWriters;
        private boolean hasRuntimeConverters;

        public B setDefaultBlocking(boolean defaultBlocking) {
            this.defaultBlocking = defaultBlocking;
            return (B) this;
        }

        public B setHasRuntimeConverters(boolean hasRuntimeConverters) {
            this.hasRuntimeConverters = hasRuntimeConverters;
            return (B) this;
        }

        public B setIndex(IndexView index) {
            this.index = index;
            return (B) this;
        }

        public B setBeanContainer(BeanContainer beanContainer) {
            this.beanContainer = beanContainer;
            return (B) this;
        }

        public B setGeneratedClassBuildItemBuildProducer(
                BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer) {
            this.generatedClassBuildItemBuildProducer = generatedClassBuildItemBuildProducer;
            return (B) this;
        }

        public B setBytecodeTransformerBuildItemBuildProducer(
                BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildItemBuildProducer) {
            this.bytecodeTransformerBuildItemBuildProducer = bytecodeTransformerBuildItemBuildProducer;
            return (B) this;
        }

        public B setRecorder(QuarkusRestCommonRecorder recorder) {
            this.recorder = recorder;
            return (B) this;
        }

        public B setExistingConverters(Map<String, String> existingConverters) {
            this.existingConverters = existingConverters;
            return (B) this;
        }

        public B setScannedResourcePaths(Map<DotName, String> scannedResourcePaths) {
            this.scannedResourcePaths = scannedResourcePaths;
            return (B) this;
        }

        public B setConfig(QuarkusRestConfig config) {
            this.config = config;
            return (B) this;
        }

        public B setAdditionalReaders(AdditionalReaders additionalReaders) {
            this.additionalReaders = additionalReaders;
            return (B) this;
        }

        public B setHttpAnnotationToMethod(Map<DotName, String> httpAnnotationToMethod) {
            this.httpAnnotationToMethod = httpAnnotationToMethod;
            return (B) this;
        }

        public B setInjectableBeans(Map<String, InjectableBean> injectableBeans) {
            this.injectableBeans = injectableBeans;
            return (B) this;
        }

        public B setAdditionalWriters(AdditionalWriters additionalWriters) {
            this.additionalWriters = additionalWriters;
            return (B) this;
        }

        public abstract T build();
    }
}
