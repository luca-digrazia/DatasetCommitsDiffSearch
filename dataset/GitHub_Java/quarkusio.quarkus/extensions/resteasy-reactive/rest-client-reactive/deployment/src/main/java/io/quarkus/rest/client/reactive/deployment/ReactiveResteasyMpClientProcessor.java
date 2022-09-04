package io.quarkus.rest.client.reactive.deployment;

import static org.jboss.resteasy.reactive.common.processor.EndpointIndexer.CDI_WRAPPER_SUFFIX;
import static org.jboss.resteasy.reactive.common.processor.scanning.ResteasyReactiveScanner.BUILTIN_HTTP_ANNOTATIONS_TO_METHOD;

import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.inject.Typed;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.common.processor.ResteasyReactiveDotNames;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.ScopeInfo;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.jaxrs.client.reactive.deployment.JaxrsClientReactiveEnricherBuildItem;
import io.quarkus.jaxrs.client.reactive.deployment.RestClientDefaultConsumesBuildItem;
import io.quarkus.jaxrs.client.reactive.deployment.RestClientDefaultProducesBuildItem;
import io.quarkus.rest.client.reactive.runtime.HeaderCapturingServerFilter;
import io.quarkus.rest.client.reactive.runtime.HeaderContainer;
import io.quarkus.rest.client.reactive.runtime.RestClientCDIDelegateBuilder;
import io.quarkus.rest.client.reactive.runtime.RestClientReactiveConfig;
import io.quarkus.rest.client.reactive.runtime.RestClientRecorder;
import io.quarkus.resteasy.reactive.spi.ContainerRequestFilterBuildItem;

class ReactiveResteasyMpClientProcessor {

    private static final Logger log = Logger.getLogger(ReactiveResteasyMpClientProcessor.class);

    private static final DotName REGISTER_REST_CLIENT = DotName.createSimple(RegisterRestClient.class.getName());
    private static final DotName SESSION_SCOPED = DotName.createSimple(SessionScoped.class.getName());

    private static final String DELEGATE = "delegate";
    private static final String CREATE_DELEGATE = "createDelegate";

    @BuildStep
    void setUpDefaultMediaType(BuildProducer<RestClientDefaultConsumesBuildItem> consumes,
            BuildProducer<RestClientDefaultProducesBuildItem> produces) {
        consumes.produce(new RestClientDefaultConsumesBuildItem(MediaType.APPLICATION_JSON, 10));
        produces.produce(new RestClientDefaultProducesBuildItem(MediaType.APPLICATION_JSON, 10));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setupAdditionalBeans(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            RestClientRecorder restClientRecorder) {
        restClientRecorder.setRestClientBuilderResolver();
        additionalBeans.produce(new AdditionalBeanBuildItem(RestClient.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(HeaderContainer.class));
    }

    @BuildStep
    void setupRequestCollectingFilter(BuildProducer<ContainerRequestFilterBuildItem> filters) {
        filters.produce(new ContainerRequestFilterBuildItem(HeaderCapturingServerFilter.class.getName()));
    }

    @BuildStep
    void addMpClientEnricher(BuildProducer<JaxrsClientReactiveEnricherBuildItem> enrichers) {
        enrichers.produce(new JaxrsClientReactiveEnricherBuildItem(new MicroProfileRestClientEnricher()));
    }

    private void searchForJaxRsMethods(List<MethodInfo> listOfKnownMethods, ClassInfo startingInterface, CompositeIndex index) {
        for (MethodInfo method : startingInterface.methods()) {
            if (isRestMethod(method)) {
                listOfKnownMethods.add(method);
            }
        }
        List<DotName> otherImplementedInterfaces = startingInterface.interfaceNames();
        for (DotName otherInterface : otherImplementedInterfaces) {
            ClassInfo superInterface = index.getClassByName(otherInterface);
            if (superInterface != null)
                searchForJaxRsMethods(listOfKnownMethods, superInterface, index);
        }
    }

    @BuildStep
    void registerHeaderFactoryBeans(CombinedIndexBuildItem index,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        Collection<AnnotationInstance> annotations = index.getIndex().getAnnotations(DotNames.REGISTER_CLIENT_HEADERS);

        for (AnnotationInstance registerClientHeaders : annotations) {
            AnnotationValue value = registerClientHeaders.value();
            if (value != null) {
                Type clientHeaderFactoryType = value.asClass();
                String factoryTypeName = clientHeaderFactoryType.name().toString();
                if (!MicroProfileRestClientEnricher.DEFAULT_HEADERS_FACTORY.equals(factoryTypeName)) {
                    additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(factoryTypeName));
                }
            }
        }
    }

    @BuildStep
    void addRestClientBeans(Capabilities capabilities,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            RestClientReactiveConfig clientConfig) {

        CompositeIndex index = CompositeIndex.create(combinedIndexBuildItem.getIndex());
        Set<AnnotationInstance> registerRestClientAnnos = new HashSet<>(index.getAnnotations(REGISTER_REST_CLIENT));
        for (AnnotationInstance registerRestClient : registerRestClientAnnos) {
            ClassInfo jaxrsInterface = registerRestClient.target().asClass();
            // for each interface annotated with @RegisterRestClient, generate a $$CDIWrapper CDI bean that can be injected
            if (Modifier.isAbstract(jaxrsInterface.flags())) {
                List<MethodInfo> restMethods = new ArrayList<>();

                // search this class and its super interfaces for jaxrs methods
                searchForJaxRsMethods(restMethods, jaxrsInterface, index);
                if (restMethods.isEmpty()) {
                    continue;
                }

                String wrapperClassName = jaxrsInterface.name().toString() + CDI_WRAPPER_SUFFIX;
                try (ClassCreator classCreator = ClassCreator.builder()
                        .className(wrapperClassName)
                        .classOutput(new GeneratedBeanGizmoAdaptor(generatedBeans))
                        .interfaces(jaxrsInterface.name().toString())
                        .build()) {

                    // CLASS LEVEL
                    final String configPrefix = computeConfigPrefix(jaxrsInterface.name(), registerRestClient);
                    final ScopeInfo scope = computeDefaultScope(capabilities, ConfigProvider.getConfig(), jaxrsInterface,
                            configPrefix, clientConfig);
                    // add a scope annotation, e.g. @Singleton
                    classCreator.addAnnotation(scope.getDotName().toString());
                    classCreator.addAnnotation(RestClient.class);
                    // e.g. @Typed({InterfaceClass.class})
                    // needed for CDI to inject the proper wrapper in case of
                    // subinterfaces
                    org.objectweb.asm.Type asmType = org.objectweb.asm.Type
                            .getObjectType(jaxrsInterface.name().toString().replace('.', '/'));
                    classCreator.addAnnotation(Typed.class.getName(), RetentionPolicy.RUNTIME)
                            .addValue("value", new org.objectweb.asm.Type[] { asmType });

                    //private final InterfaceClass delegate;
                    FieldDescriptor delegateField = FieldDescriptor.of(classCreator.getClassName(), DELEGATE,
                            jaxrsInterface.name().toString());
                    classCreator.getFieldCreator(delegateField).setModifiers(Modifier.FINAL | Modifier.PRIVATE);

                    // CONSTRUCTOR:
                    MethodCreator constructor = classCreator
                            .getMethodCreator(MethodDescriptor.ofConstructor(classCreator.getClassName()));
                    constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), constructor.getThis());

                    //       Object var1 = RestClientCDIDelegateBuilder.createDelegate(InterfaceClass.class, "/baseUri",
                    //       "com.example.InterfaceClass");
                    //      this.delegate = (InterfaceClass)var1;
                    ResultHandle interfaceHandle = constructor.loadClass(jaxrsInterface.toString());

                    AnnotationValue baseUri = registerRestClient.value("baseUri");

                    ResultHandle baseUriHandle = constructor.load(baseUri != null ? baseUri.asString() : "");
                    ResultHandle configPrefixHandle = constructor.load(configPrefix);

                    MethodDescriptor createDelegate = MethodDescriptor.ofMethod(RestClientCDIDelegateBuilder.class,
                            CREATE_DELEGATE, Object.class, Class.class, String.class, String.class);

                    constructor.writeInstanceField(delegateField, constructor.getThis(),
                            constructor.invokeStaticMethod(createDelegate, interfaceHandle, baseUriHandle,
                                    configPrefixHandle));
                    constructor.returnValue(null);

                    // METHODS:
                    for (MethodInfo method : restMethods) {
                        // for each method method that corresponds to making a rest call, create a method like:
                        // public JsonArray get() {
                        //      return ((InterfaceClass)this.delegate).get();
                        // }
                        MethodCreator methodCreator = classCreator.getMethodCreator(MethodDescriptor.of(method));

                        // copy method annotations, there can be interceptors bound to them:
                        for (AnnotationInstance annotation : method.annotations()) {
                            if (annotation.target().kind() == AnnotationTarget.Kind.METHOD
                                    && !BUILTIN_HTTP_ANNOTATIONS_TO_METHOD.containsKey(annotation.name())
                                    && !ResteasyReactiveDotNames.PATH.equals(annotation.name())) {
                                AnnotationValue value = annotation.value();
                                if (value != null && value.kind() == AnnotationValue.Kind.ARRAY
                                        && value.componentKind() == AnnotationValue.Kind.NESTED) {
                                    for (AnnotationInstance annotationInstance : value.asNestedArray()) {
                                        methodCreator.addAnnotation(annotationInstance);
                                    }
                                } else {
                                    methodCreator.addAnnotation(annotation);
                                }
                            }
                        }

                        ResultHandle delegate = methodCreator.readInstanceField(delegateField, methodCreator.getThis());

                        int parameterCount = method.parameters().size();
                        ResultHandle result;
                        if (parameterCount == 0) {
                            result = methodCreator.invokeInterfaceMethod(method, delegate);
                        } else {
                            ResultHandle[] params = new ResultHandle[parameterCount];
                            for (int i = 0; i < parameterCount; i++) {
                                params[i] = methodCreator.getMethodParam(i);
                            }
                            result = methodCreator.invokeInterfaceMethod(method, delegate, params);
                        }
                        methodCreator.returnValue(result);
                    }
                }
            }
        }
    }

    private boolean isRestMethod(MethodInfo method) {
        if (!Modifier.isAbstract(method.flags())) {
            return false;
        }
        for (AnnotationInstance annotation : method.annotations()) {
            if (annotation.target().kind() == AnnotationTarget.Kind.METHOD
                    && BUILTIN_HTTP_ANNOTATIONS_TO_METHOD.containsKey(annotation.name())) {
                return true;
            }
        }
        return false;
    }

    private String computeConfigPrefix(DotName interfaceName, AnnotationInstance registerRestClientAnnotation) {
        AnnotationValue configKeyValue = registerRestClientAnnotation.value("configKey");
        return configKeyValue != null
                ? configKeyValue.asString()
                : interfaceName.toString();
    }

    private ScopeInfo computeDefaultScope(Capabilities capabilities, Config config,
            ClassInfo restClientInterface,
            String configPrefix,
            RestClientReactiveConfig mpClientConfig) {
        ScopeInfo scopeToUse = null;
        final Optional<String> scopeConfig = config
                .getOptionalValue(String.format(RestClientCDIDelegateBuilder.REST_SCOPE_FORMAT, configPrefix), String.class);

        BuiltinScope globalDefaultScope = BuiltinScope.from(DotName.createSimple(mpClientConfig.scope));
        if (globalDefaultScope == null) {
            log.warnv("Unable to map the global rest client scope: '{}' to a scope. Using @ApplicationScoped",
                    mpClientConfig.scope);
            globalDefaultScope = BuiltinScope.APPLICATION;
        }

        if (scopeConfig.isPresent()) {
            final DotName scope = DotName.createSimple(scopeConfig.get());
            final BuiltinScope builtinScope = BuiltinScope.from(scope);
            if (builtinScope != null) { // override default @Dependent scope with user defined one.
                scopeToUse = builtinScope.getInfo();
            } else if (capabilities.isPresent(Capability.SERVLET)) {
                if (scope.equals(SESSION_SCOPED)) {
                    scopeToUse = new ScopeInfo(SESSION_SCOPED, true);
                }
            }

            if (scopeToUse == null) {
                log.warnf("Unsupported default scope {} provided for rest client {}. Defaulting to {}",
                        scope, restClientInterface.name(), globalDefaultScope.getName());
                scopeToUse = BuiltinScope.DEPENDENT.getInfo();
            }
        } else {
            final Set<DotName> annotations = restClientInterface.annotations().keySet();
            for (final DotName annotationName : annotations) {
                final BuiltinScope builtinScope = BuiltinScope.from(annotationName);
                if (builtinScope != null) {
                    scopeToUse = builtinScope.getInfo();
                    break;
                }
                if (annotationName.equals(SESSION_SCOPED)) {
                    scopeToUse = new ScopeInfo(SESSION_SCOPED, true);
                    break;
                }
            }
        }

        // Initialize a default @Dependent scope as per the spec
        return scopeToUse != null ? scopeToUse : globalDefaultScope.getInfo();
    }
}
