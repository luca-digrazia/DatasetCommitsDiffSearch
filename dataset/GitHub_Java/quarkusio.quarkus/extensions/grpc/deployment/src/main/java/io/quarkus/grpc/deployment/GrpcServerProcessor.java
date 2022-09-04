package io.quarkus.grpc.deployment;

import static io.quarkus.deployment.Feature.GRPC_SERVER;
import static java.util.Arrays.asList;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.grpc.BindableService;
import io.grpc.internal.ServerImpl;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.StereotypeInfo;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.grpc.GrpcService;
import io.quarkus.grpc.deployment.devmode.FieldDefinalizingVisitor;
import io.quarkus.grpc.protoc.plugin.MutinyGrpcGenerator;
import io.quarkus.grpc.runtime.GrpcContainer;
import io.quarkus.grpc.runtime.GrpcServerRecorder;
import io.quarkus.grpc.runtime.config.GrpcConfiguration;
import io.quarkus.grpc.runtime.config.GrpcServerBuildTimeConfig;
import io.quarkus.grpc.runtime.health.GrpcHealthEndpoint;
import io.quarkus.grpc.runtime.health.GrpcHealthStorage;
import io.quarkus.grpc.runtime.supports.context.GrpcEnableRequestContext;
import io.quarkus.grpc.runtime.supports.context.GrpcRequestContextCdiInterceptor;
import io.quarkus.kubernetes.spi.KubernetesPortBuildItem;
import io.quarkus.netty.deployment.MinNettyAllocatorMaxOrderBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.quarkus.vertx.deployment.VertxBuildItem;

public class GrpcServerProcessor {

    private static final Logger logger = Logger.getLogger(GrpcServerProcessor.class);

    private static final String SSL_PREFIX = "quarkus.grpc.server.ssl.";
    private static final String CERTIFICATE = SSL_PREFIX + "certificate";
    private static final String KEY = SSL_PREFIX + "key";
    private static final String KEY_STORE = SSL_PREFIX + "key-store";
    private static final String TRUST_STORE = SSL_PREFIX + "trust-store";

    @BuildStep
    MinNettyAllocatorMaxOrderBuildItem setMinimalNettyMaxOrderSize() {
        return new MinNettyAllocatorMaxOrderBuildItem(3);
    }

    @BuildStep
    void processGeneratedBeans(CombinedIndexBuildItem index, BuildProducer<AnnotationsTransformerBuildItem> transformers,
            BuildProducer<BindableServiceBuildItem> bindables) {

        Map<DotName, Set<MethodInfo>> nameToBlockingMethods = new HashMap<>();
        String[] excludedPackages = { "grpc.health.v1", "io.grpc.reflection" };

        // We need to transform the generated bean and register a bindable service if:
        // 1. there is a user-defined bean that implements the generated interface (injected delegate)
        // 2. there is no user-defined bean that extends the relevant impl bases (both mutiny and regular)
        for (ClassInfo generatedBean : index.getIndex().getAllKnownImplementors(GrpcDotNames.GENERATED_GRPC_BEAN)) {
            FieldInfo delegateField = generatedBean.field("delegate");
            if (delegateField == null) {
                throw new IllegalStateException("A generated bean does not declare the delegate field: " + generatedBean);
            }
            DotName generatedInterface = delegateField.type().name();
            Collection<ClassInfo> serviceCandidates = index.getIndex().getAllKnownImplementors(generatedInterface);
            if (serviceCandidates.isEmpty()) {
                // No user-defined bean that implements the generated interface
                continue;
            }
            ClassInfo userDefinedBean = null;
            for (ClassInfo candidate : serviceCandidates) {
                // The bean must be annotated with @GrpcService
                if (candidate.classAnnotation(GrpcDotNames.GRPC_SERVICE) != null) {
                    userDefinedBean = candidate;
                    break;
                }
            }
            if (userDefinedBean == null) {
                continue;
            }
            DotName mutinyImplBase = generatedBean.superName();
            if (index.getIndex().getAllKnownSubclasses(mutinyImplBase).size() != 1) {
                // Some class extends the mutiny impl base
                continue;
            }
            String mutinyImplBaseName = mutinyImplBase.toString();
            // Now derive the original impl base
            // e.g. examples.MutinyGreeterGrpc.GreeterImplBase -> examples.GreeterGrpc.GreeterImplBase
            DotName implBase = DotName.createSimple(mutinyImplBaseName.replace(MutinyGrpcGenerator.CLASS_PREFIX, ""));
            if (!index.getIndex().getAllKnownSubclasses(implBase).isEmpty()) {
                // Some class extends the impl base
                continue;
            }
            // Finally exclude some packages
            boolean excluded = false;
            for (String excludedPackage : excludedPackages) {
                if (mutinyImplBaseName.startsWith(excludedPackage)) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                logger.debugf("Registering generated gRPC bean %s that will delegate to %s", generatedBean, userDefinedBean);
                Set<MethodInfo> blockingMethods = new HashSet<>();
                for (MethodInfo method : userDefinedBean.methods()) {
                    if (method.hasAnnotation(GrpcDotNames.BLOCKING)) {
                        blockingMethods.add(method);
                    }
                }
                nameToBlockingMethods.put(generatedBean.name(), blockingMethods);
            }
        }

        if (!nameToBlockingMethods.isEmpty()) {
            // For every suitable bean we must:
            // (a) add a scope: @Singleton; otherwise it's just ignored
            // (b) register a BindableServiceBuildItem, incl. all blocking methods (derived from the user-defined impl)
            for (Entry<DotName, Set<MethodInfo>> entry : nameToBlockingMethods.entrySet()) {
                BindableServiceBuildItem bindableService = new BindableServiceBuildItem(entry.getKey());
                for (MethodInfo blockingMethod : entry.getValue()) {
                    bindableService.registerBlockingMethod(blockingMethod.name());
                }
                bindables.produce(bindableService);
            }
            transformers.produce(new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {
                @Override
                public boolean appliesTo(Kind kind) {
                    return kind == Kind.CLASS;
                }

                @Override
                public void transform(TransformationContext context) {
                    if (nameToBlockingMethods.containsKey(context.getTarget().asClass().name())) {
                        context.transform().add(BuiltinScope.SINGLETON.getName()).done();
                    }
                }
            }));
        }
    }

    @BuildStep
    void discoverBindableServices(BuildProducer<BindableServiceBuildItem> bindables,
            CombinedIndexBuildItem combinedIndexBuildItem) {
        Collection<ClassInfo> bindableServices = combinedIndexBuildItem.getIndex()
                .getAllKnownImplementors(GrpcDotNames.BINDABLE_SERVICE);

        for (ClassInfo service : bindableServices) {
            if (service.interfaceNames().contains(GrpcDotNames.GENERATED_GRPC_BEAN)) {
                // Ignore generated beans
                continue;
            }
            if (Modifier.isAbstract(service.flags())) {
                continue;
            }
            BindableServiceBuildItem item = new BindableServiceBuildItem(service.name());
            for (MethodInfo method : service.methods()) {
                if (method.hasAnnotation(GrpcDotNames.BLOCKING)) {
                    item.registerBlockingMethod(method.name());
                }
            }
            bindables.produce(item);
        }
    }

    @BuildStep
    void validateBindableServices(ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
        Type generatedBeanType = Type.create(GrpcDotNames.GENERATED_GRPC_BEAN, org.jboss.jandex.Type.Kind.CLASS);
        for (BeanInfo bean : validationPhase.getContext().beans().classBeans().withBeanType(BindableService.class)) {
            if (!bean.getTypes().contains(generatedBeanType) && bean.getStereotypes().stream().map(StereotypeInfo::getName)
                    .noneMatch(GrpcDotNames.GRPC_SERVICE::equals)) {
                errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                        new IllegalStateException(
                                "A gRPC service bean must be annotated with io.quarkus.GrpcService: " + bean)));
            }
            if (!bean.getScope().getDotName().equals(BuiltinScope.SINGLETON.getName())) {
                errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                        new IllegalStateException("A gRPC service bean must have the javax.inject.Singleton scope: " + bean)));
            }
        }
    }

    @BuildStep(onlyIf = IsNormal.class)
    KubernetesPortBuildItem registerGrpcServiceInKubernetes(List<BindableServiceBuildItem> bindables) {
        if (!bindables.isEmpty()) {
            int port = ConfigProvider.getConfig().getOptionalValue("quarkus.grpc.server.port", Integer.class)
                    .orElse(9000);
            return new KubernetesPortBuildItem(port, GRPC_SERVER);
        }
        return null;
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans,
            List<BindableServiceBuildItem> bindables, BuildProducer<FeatureBuildItem> features) {
        // @GrpcService is a CDI stereotype
        beans.produce(new AdditionalBeanBuildItem(GrpcService.class));
        beans.produce(new AdditionalBeanBuildItem(GrpcRequestContextCdiInterceptor.class));
        beans.produce(new AdditionalBeanBuildItem(GrpcEnableRequestContext.class));

        if (!bindables.isEmpty() || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcContainer.class));
            features.produce(new FeatureBuildItem(GRPC_SERVER));
        } else {
            logger.debug("Unable to find beans exposing the `BindableService` interface - not starting the gRPC server");
        }
    }

    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem build(GrpcServerRecorder recorder, GrpcConfiguration config,
            ShutdownContextBuildItem shutdown, List<BindableServiceBuildItem> bindables,
            LaunchModeBuildItem launchModeBuildItem,
            VertxBuildItem vertx) {

        // Build the list of blocking methods per service implementation
        Map<String, List<String>> blocking = new HashMap<>();
        for (BindableServiceBuildItem bindable : bindables) {
            if (bindable.hasBlockingMethods()) {
                blocking.put(bindable.serviceClass.toString(), bindable.blockingMethods);
            }
        }

        if (!bindables.isEmpty()) {
            recorder.initializeGrpcServer(vertx.getVertx(), config, shutdown, blocking, launchModeBuildItem.getLaunchMode());
            return new ServiceStartBuildItem(GRPC_SERVER);
        }
        return null;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void definializeGrpcFieldsForDevMode(BuildProducer<BytecodeTransformerBuildItem> transformers) {
        transformers.produce(new BytecodeTransformerBuildItem("io.grpc.internal.InternalHandlerRegistry",
                new FieldDefinalizingVisitor("services", "methods")));
        transformers.produce(new BytecodeTransformerBuildItem(ServerImpl.class.getName(),
                new FieldDefinalizingVisitor("interceptors")));
    }

    @BuildStep
    void addHealthChecks(GrpcServerBuildTimeConfig config,
            List<BindableServiceBuildItem> bindables,
            BuildProducer<HealthBuildItem> healthBuildItems,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        boolean healthEnabled = false;
        if (!bindables.isEmpty()) {
            healthEnabled = config.mpHealthEnabled;

            if (config.grpcHealthEnabled) {
                beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcHealthEndpoint.class));
                healthEnabled = true;
            }
            healthBuildItems.produce(new HealthBuildItem("io.quarkus.grpc.runtime.health.GrpcHealthCheck",
                    config.mpHealthEnabled));
        }
        if (healthEnabled || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcHealthStorage.class));
        }
    }

    @BuildStep
    void registerSslResources(BuildProducer<NativeImageResourceBuildItem> resourceBuildItem) {
        Config config = ConfigProvider.getConfig();
        for (String sslProperty : asList(CERTIFICATE, KEY, KEY_STORE, TRUST_STORE)) {
            config.getOptionalValue(sslProperty, String.class)
                    .ifPresent(value -> ResourceRegistrationUtils.registerResourceForProperty(resourceBuildItem, value));
        }
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem extensionSslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(GRPC_SERVER);
    }

}
