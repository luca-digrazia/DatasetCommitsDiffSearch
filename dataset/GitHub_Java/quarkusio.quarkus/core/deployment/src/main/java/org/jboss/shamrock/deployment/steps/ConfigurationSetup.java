package org.jboss.shamrock.deployment.steps;

import static org.jboss.shamrock.deployment.util.ReflectUtil.toError;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.graalvm.nativeimage.ImageInfo;
import org.jboss.protean.gizmo.BranchResult;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.ClassCreator;
import org.jboss.protean.gizmo.ClassOutput;
import org.jboss.protean.gizmo.FieldDescriptor;
import org.jboss.protean.gizmo.MethodCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;
import org.jboss.shamrock.deployment.AccessorFinder;
import org.jboss.shamrock.deployment.annotations.BuildProducer;
import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.builditem.BytecodeRecorderObjectLoaderBuildItem;
import org.jboss.shamrock.deployment.builditem.ConfigurationBuildItem;
import org.jboss.shamrock.deployment.builditem.ConfigurationCustomConverterBuildItem;
import org.jboss.shamrock.deployment.builditem.ExtensionClassLoaderBuildItem;
import org.jboss.shamrock.deployment.builditem.GeneratedClassBuildItem;
import org.jboss.shamrock.deployment.builditem.RunTimeConfigurationSourceBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.RuntimeReinitializedClassBuildItem;
import org.jboss.shamrock.deployment.configuration.ConfigDefinition;
import org.jboss.shamrock.deployment.configuration.ConfigPatternMap;
import org.jboss.shamrock.deployment.configuration.LeafConfigType;
import org.jboss.shamrock.deployment.recording.ObjectLoader;
import org.jboss.shamrock.deployment.util.ServiceUtil;
import org.jboss.shamrock.runtime.annotations.ConfigPhase;
import org.jboss.shamrock.runtime.configuration.CidrAddressConverter;
import org.jboss.shamrock.runtime.configuration.ConverterFactory;
import org.jboss.shamrock.runtime.configuration.ExpandingConfigSource;
import org.jboss.shamrock.runtime.configuration.InetAddressConverter;
import org.jboss.shamrock.runtime.configuration.InetSocketAddressConverter;
import org.jboss.shamrock.runtime.configuration.NameIterator;
import org.jboss.shamrock.runtime.configuration.SimpleConfigurationProviderResolver;
import org.objectweb.asm.Opcodes;
import org.wildfly.common.net.CidrAddress;

/**
 * Setup steps for configuration purposes.
 */
public class ConfigurationSetup {

    public static final String CONFIG_HELPER = "org.jboss.shamrock.runtime.generated.ConfigHelper";
    public static final String CONFIG_HELPER_DATA = "org.jboss.shamrock.runtime.generated.ConfigHelperData";
    public static final String CONFIG_ROOT = "org.jboss.shamrock.runtime.generated.ConfigRoot";

    public static final FieldDescriptor CONFIG_ROOT_FIELD = FieldDescriptor.of(CONFIG_HELPER_DATA, "configRoot", CONFIG_ROOT);

    private static final MethodDescriptor NI_HAS_NEXT = MethodDescriptor.ofMethod(NameIterator.class, "hasNext", boolean.class);
    private static final MethodDescriptor NI_NEXT_EQUALS = MethodDescriptor.ofMethod(NameIterator.class, "nextSegmentEquals", boolean.class, String.class);
    private static final MethodDescriptor NI_NEXT = MethodDescriptor.ofMethod(NameIterator.class, "next", void.class);
    private static final MethodDescriptor ITR_HAS_NEXT = MethodDescriptor.ofMethod(Iterator.class, "hasNext", boolean.class);
    private static final MethodDescriptor ITR_NEXT = MethodDescriptor.ofMethod(Iterator.class, "next", Object.class);
    private static final MethodDescriptor CF_GET_CONVERTER = MethodDescriptor.ofMethod(ConverterFactory.class, "getConverter", Converter.class, SmallRyeConfig.class, Class.class);
    private static final MethodDescriptor CPR_SET_INSTANCE = MethodDescriptor.ofMethod(ConfigProviderResolver.class, "setInstance", void.class, ConfigProviderResolver.class);
    private static final MethodDescriptor SCPR_CONSTRUCT = MethodDescriptor.ofConstructor(SimpleConfigurationProviderResolver.class, Config.class);
    private static final MethodDescriptor SRCB_BUILD = MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "build", Config.class);
    private static final MethodDescriptor SRCB_WITH_CONVERTER = MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "withConverter", ConfigBuilder.class, Class.class, int.class, Converter.class);
    private static final MethodDescriptor SRCB_WITH_SOURCES = MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "withSources", ConfigBuilder.class, ConfigSource[].class);
    private static final MethodDescriptor SRCB_ADD_DEFAULT_SOURCES = MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "addDefaultSources", ConfigBuilder.class);
    private static final MethodDescriptor SRCB_CONSTRUCT = MethodDescriptor.ofConstructor(SmallRyeConfigBuilder.class);
    private static final MethodDescriptor II_IN_IMAGE_BUILD = MethodDescriptor.ofMethod(ImageInfo.class, "inImageBuildtimeCode", boolean.class);
    private static final MethodDescriptor II_IN_IMAGE_RUN = MethodDescriptor.ofMethod(ImageInfo.class, "inImageRuntimeCode", boolean.class);
    private static final MethodDescriptor SRCB_WITH_WRAPPER = MethodDescriptor.ofMethod(SmallRyeConfigBuilder.class, "withWrapper", SmallRyeConfigBuilder.class, UnaryOperator.class);

    public static final MethodDescriptor GET_ROOT_METHOD = MethodDescriptor.ofMethod(CONFIG_HELPER, "getRoot", CONFIG_ROOT);

    private static final FieldDescriptor ECS_WRAPPER = FieldDescriptor.of(ExpandingConfigSource.class, "WRAPPER", UnaryOperator.class);

    public ConfigurationSetup() {}

    @BuildStep
    public void setUpConverters(BuildProducer<ConfigurationCustomConverterBuildItem> configurationTypes) {
        configurationTypes.produce(new ConfigurationCustomConverterBuildItem(
            200,
            InetSocketAddress.class,
            InetSocketAddressConverter.class
        ));
        configurationTypes.produce(new ConfigurationCustomConverterBuildItem(
            200,
            CidrAddress.class,
            CidrAddressConverter.class
        ));
        configurationTypes.produce(new ConfigurationCustomConverterBuildItem(
            200,
            InetAddress.class,
            InetAddressConverter.class
        ));
    }

    /**
     * Run before anything that consumes configuration; sets up the main configuration definition instance.
     *
     * @param converters the converters to set up
     * @return the configuration build item
     */
    @BuildStep
    public ConfigurationBuildItem initializeConfiguration(
        List<ConfigurationCustomConverterBuildItem> converters,
        ExtensionClassLoaderBuildItem extensionClassLoaderBuildItem
    ) throws IOException, ClassNotFoundException {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder();
        // expand properties
        builder.withWrapper(ExpandingConfigSource::new);
        builder.addDefaultSources();
        for (ConfigurationCustomConverterBuildItem converter : converters) {
            withConverterHelper(builder, converter.getType(), converter.getPriority(), converter.getConverter());
        }
        final SmallRyeConfig src = (SmallRyeConfig) builder.build();
        final ConfigDefinition configDefinition = new ConfigDefinition();
        // populate it with all known types
        for (Class<?> clazz : ServiceUtil.classesNamedIn(extensionClassLoaderBuildItem.getExtensionClassLoader(), "META-INF/shamrock-config-roots.list")) {
            configDefinition.registerConfigRoot(clazz);
        }
        configDefinition.loadConfiguration(src);
        return new ConfigurationBuildItem(configDefinition);
    }

    @SuppressWarnings("unchecked")
    private static <T> void withConverterHelper(final SmallRyeConfigBuilder builder, final Class<T> type, final int priority, final Class<? extends Converter<?>> converterClass) {
        try {
            builder.withConverter(type, priority, ((Class<? extends Converter<T>>) converterClass).newInstance());
        } catch (InstantiationException e) {
            throw toError(e);
        } catch (IllegalAccessException e) {
            throw toError(e);
        }
    }


    /**
     * Generate the bytecode to load configuration objects at static init and run time.
     *
     * @param configurationBuildItem the config build item
     * @param classConsumer the consumer of generated classes
     * @param runTimeInitConsumer the consumer of runtime init classes
     */
    @BuildStep
    void finalizeConfigLoader(
        ConfigurationBuildItem configurationBuildItem,
        Consumer<GeneratedClassBuildItem> classConsumer,
        Consumer<RuntimeReinitializedClassBuildItem> runTimeInitConsumer,
        Consumer<BytecodeRecorderObjectLoaderBuildItem> objectLoaderConsumer,
        List<ConfigurationCustomConverterBuildItem> converters,
        List<RunTimeConfigurationSourceBuildItem> runTimeSources
    ) {
        final ClassOutput classOutput = new ClassOutput() {
            public void write(final String name, final byte[] data) {
                classConsumer.accept(new GeneratedClassBuildItem(false, name, data));
            }
        };
        // Get the set of run time and static init leaf keys
        final ConfigDefinition configDefinition = configurationBuildItem.getConfigDefinition();

        final ConfigPatternMap<LeafConfigType> allLeafPatterns = configDefinition.getLeafPatterns();
        final ConfigPatternMap<LeafConfigType> runTimePatterns = new ConfigPatternMap<>();
        final ConfigPatternMap<LeafConfigType> staticInitPatterns = new ConfigPatternMap<>();
        for (String childName : allLeafPatterns.childNames()) {
            ConfigPhase phase = configDefinition.getPhaseByKey(childName);
            if (phase.isReadAtMain()) {
                runTimePatterns.addChild(childName, allLeafPatterns.getChild(childName));
            }
            if (phase.isReadAtStaticInit()) {
                staticInitPatterns.addChild(childName, allLeafPatterns.getChild(childName));
            }
        }

        AccessorFinder accessorMaker = new AccessorFinder();

        configDefinition.generateConfigRootClass(classOutput, accessorMaker);

        final FieldDescriptor convertersField;
        // This must be a separate class, because CONFIG_HELPER is re-initialized at run time (native image).
        try (final ClassCreator cc = new ClassCreator(classOutput, CONFIG_HELPER_DATA, null, Object.class.getName())) {
            convertersField = cc.getFieldCreator("$CONVERTERS", Converter[].class).setModifiers(Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE).getFieldDescriptor();
            cc.getFieldCreator(CONFIG_ROOT_FIELD).setModifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE).getFieldDescriptor();
        }

        try (final ClassCreator cc = new ClassCreator(classOutput, CONFIG_HELPER, null, Object.class.getName())) {
            final MethodDescriptor createAndRegisterConfig;
            // config object initialization
            // this has to be on the static init class, which is visible at both static init and execution time
            try (MethodCreator carc = cc.getMethodCreator("createAndRegisterConfig", SmallRyeConfig.class)) {
                carc.setModifiers(Opcodes.ACC_STATIC);
                final ResultHandle builder = carc.newInstance(SRCB_CONSTRUCT);
                carc.invokeVirtualMethod(SRCB_ADD_DEFAULT_SOURCES, builder);
                final int size = runTimeSources.size();
                if (size > 0) {
                    final ResultHandle arrayHandle = carc.newArray(ConfigSource[].class, carc.load(size));
                    for (int i = 0; i < size; i ++) {
                        final RunTimeConfigurationSourceBuildItem source = runTimeSources.get(i);
                        final OptionalInt priority = source.getPriority();
                        final ResultHandle val;
                        if (priority.isPresent()) {
                            val = carc.newInstance(MethodDescriptor.ofConstructor(source.getClassName(), int.class), carc.load(priority.getAsInt()));
                        } else {
                            val = carc.newInstance(MethodDescriptor.ofConstructor(source.getClassName()));
                        }
                        carc.writeArrayValue(arrayHandle, i, val);
                    }
                    carc.invokeVirtualMethod(
                        SRCB_WITH_SOURCES,
                        builder,
                        arrayHandle
                    );
                }
                for (ConfigurationCustomConverterBuildItem converter : converters) {
                    carc.invokeVirtualMethod(
                        SRCB_WITH_CONVERTER,
                        builder,
                        carc.loadClass(converter.getType()),
                        carc.load(converter.getPriority()),
                        carc.newInstance(MethodDescriptor.ofConstructor(converter.getConverter()))
                    );
                }
                // todo: add custom sources
                final ResultHandle wrapper = carc.readStaticField(ECS_WRAPPER);
                carc.invokeVirtualMethod(SRCB_WITH_WRAPPER, builder, wrapper);

                // Traverse all known config types and ensure we have converters for them when image building runs
                // This code is specific to native image

                HashSet<Class<?>> encountered = new HashSet<>();
                ArrayList<Class<?>> configTypes = new ArrayList<>();
                for (LeafConfigType item : allLeafPatterns) {
                    final Class<?> typeClass = item.getItemClass();
                    if (! typeClass.isPrimitive() && encountered.add(typeClass)) {
                        configTypes.add(typeClass);
                    }
                }
                // stability
                configTypes.sort(Comparator.comparing(Class::getName));
                int cnt = configTypes.size();

                // At image runtime, load the converters array and register it with the config builder
                // This code is specific to native image

                final BranchResult imgRun = carc.ifNonZero(carc.invokeStaticMethod(II_IN_IMAGE_RUN));
                try (BytecodeCreator inImageRun = imgRun.trueBranch()) {
                    final ResultHandle array = inImageRun.readStaticField(convertersField);
                    for (int i = 0; i < cnt; i ++) {
                        // implicit converters will have a priority of 100.
                        inImageRun.invokeVirtualMethod(
                            SRCB_WITH_CONVERTER,
                            builder,
                            inImageRun.loadClass(configTypes.get(i)),
                            inImageRun.load(100),
                            inImageRun.readArrayValue(array, i)
                        );
                    }
                }

                // Build the config

                final ResultHandle config = carc.checkCast(carc.invokeVirtualMethod(SRCB_BUILD, builder), SmallRyeConfig.class);
                final ResultHandle providerResolver = carc.newInstance(SCPR_CONSTRUCT, config);
                carc.invokeStaticMethod(CPR_SET_INSTANCE, providerResolver);

                // At image build time, record all the implicit converts and store them in the converters array
                // This actually happens before the above `if` block, despite necessarily coming later in the method sequence
                // This code is specific to native image

                final BranchResult imgBuild = carc.ifNonZero(carc.invokeStaticMethod(II_IN_IMAGE_BUILD));
                try (BytecodeCreator inImageBuild = imgBuild.trueBranch()) {
                    final ResultHandle array = inImageBuild.newArray(Converter.class, inImageBuild.load(cnt));
                    for (int i = 0; i < cnt; i ++) {
                        inImageBuild.writeArrayValue(array, i, inImageBuild.invokeStaticMethod(CF_GET_CONVERTER, config, inImageBuild.loadClass(configTypes.get(i))));
                    }
                    inImageBuild.writeStaticField(convertersField, array);
                }

                carc.returnValue(carc.checkCast(config, SmallRyeConfig.class));
                createAndRegisterConfig = carc.getMethodDescriptor();
            }

            // helper to ensure the config is instantiated before it is read
            try (MethodCreator getRoot = cc.getMethodCreator("getRoot", CONFIG_ROOT)) {
                getRoot.setModifiers(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC);

                getRoot.returnValue(getRoot.readStaticField(CONFIG_ROOT_FIELD));
            }

            // static init block
            try (MethodCreator ccInit = cc.getMethodCreator("<clinit>", void.class)) {
                ccInit.setModifiers(Opcodes.ACC_STATIC);

                // write out the parsing
                final BranchResult ccIfImage = ccInit.ifNonZero(ccInit.invokeStaticMethod(MethodDescriptor.ofMethod(ImageInfo.class, "inImageRuntimeCode", boolean.class)));
                try (BytecodeCreator ccIsNotImage = ccIfImage.falseBranch()) {
                    // common case: JVM mode, or image-building initialization
                    final ResultHandle mccConfig = ccIsNotImage.invokeStaticMethod(createAndRegisterConfig);
                    ccIsNotImage.newInstance(MethodDescriptor.ofConstructor(CONFIG_ROOT, SmallRyeConfig.class), mccConfig);
                    writeParsing(cc, ccIsNotImage, mccConfig, staticInitPatterns);
                }
                try (BytecodeCreator ccIsImage = ccIfImage.trueBranch()) {
                    // native image run time only (class reinitialization)
                    final ResultHandle mccConfig = ccIsImage.invokeStaticMethod(createAndRegisterConfig);
                    writeParsing(cc, ccIsImage, mccConfig, runTimePatterns);
                }
                ccInit.returnValue(null);
            }
        }

        objectLoaderConsumer.accept(new BytecodeRecorderObjectLoaderBuildItem(new ObjectLoader() {
            public ResultHandle load(final BytecodeCreator body, final Object obj) {
                final ConfigDefinition.RootInfo rootInfo = configDefinition.getInstanceInfo(obj);
                if (rootInfo == null) return null;
                final FieldDescriptor fieldDescriptor = rootInfo.getFieldDescriptor();
                final ResultHandle configRoot = body.invokeStaticMethod(GET_ROOT_METHOD);
                return body.readInstanceField(fieldDescriptor, configRoot);
            }
        }));

        runTimeInitConsumer.accept(new RuntimeReinitializedClassBuildItem(CONFIG_HELPER));
    }

    private void writeParsing(final ClassCreator cc, final BytecodeCreator body, final ResultHandle config, final ConfigPatternMap<LeafConfigType> keyMap) {
        // setup
        // Iterable iterable = config.getPropertyNames();
        final ResultHandle iterable = body.invokeVirtualMethod(MethodDescriptor.ofMethod(SmallRyeConfig.class, "getPropertyNames", Iterable.class), config);
        // Iterator iterator = iterable.iterator();
        final ResultHandle iterator = body.invokeInterfaceMethod(MethodDescriptor.ofMethod(Iterable.class, "iterator", Iterator.class), iterable);

        // loop: {
        try (BytecodeCreator loop = body.createScope()) {
            // if (iterator.hasNext())
            final BranchResult ifHasNext = loop.ifNonZero(loop.invokeInterfaceMethod(ITR_HAS_NEXT, iterator));
            // {
            try (BytecodeCreator hasNext = ifHasNext.trueBranch()) {
                // key = iterator.next();
                final ResultHandle key = hasNext.checkCast(hasNext.invokeInterfaceMethod(ITR_NEXT, iterator), String.class);
                // NameIterator keyIter = new NameIterator(key);
                final ResultHandle keyIter = hasNext.newInstance(MethodDescriptor.ofConstructor(NameIterator.class, String.class), key);
                // if (! keyIter.hasNext()) continue loop;
                hasNext.ifNonZero(hasNext.invokeVirtualMethod(NI_HAS_NEXT, keyIter)).falseBranch().continueScope(loop);
                // if (! keyIter.nextSegmentEquals("shamrock")) continue loop;
                hasNext.ifNonZero(hasNext.invokeVirtualMethod(NI_NEXT_EQUALS, keyIter, hasNext.load("shamrock"))).falseBranch().continueScope(loop);
                // keyIter.next(); // skip "shamrock"
                hasNext.invokeVirtualMethod(NI_NEXT, keyIter);
                // parse(config, keyIter);
                hasNext.invokeStaticMethod(generateParserBody(cc, keyMap, new StringBuilder("parseKey"), new HashMap<>()), config, keyIter);
                // continue loop;
                hasNext.continueScope(loop);
            }
            // }
        }
        // }
        body.returnValue(body.loadNull());
    }

    private MethodDescriptor generateParserBody(final ClassCreator cc, final ConfigPatternMap<LeafConfigType> keyMap, final StringBuilder methodName, final Map<String, MethodDescriptor> parseMethodCache) {
        final String methodNameStr = methodName.toString();
        final MethodDescriptor existing = parseMethodCache.get(methodNameStr);
        if (existing != null) return existing;
        try (MethodCreator body = cc.getMethodCreator(methodName.toString(), void.class, SmallRyeConfig.class, NameIterator.class)) {
            body.setModifiers(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
            final ResultHandle config = body.getMethodParam(0);
            final ResultHandle keyIter = body.getMethodParam(1);
            final LeafConfigType matched = keyMap.getMatched();
            // if (! keyIter.hasNext()) {
            try (BytecodeCreator matchedBody = body.ifNonZero(body.invokeVirtualMethod(NI_HAS_NEXT, keyIter)).falseBranch()) {
                if (matched != null) {
                    // (exact match generated code)
                    matched.generateAcceptConfigurationValue(matchedBody, keyIter, config);
                } else {
                    // todo: unknown name warning goes here
                }
                // return;
                matchedBody.returnValue(null);
            }
            // }
            // branches for each next-string
            final Iterable<String> names = keyMap.childNames();
            for (String name : names) {
                if (name.equals(ConfigPatternMap.WC_SINGLE) || name.equals(ConfigPatternMap.WC_MULTI)) {
                    // skip
                } else {
                    // TODO: string switch
                    // if (keyIter.nextSegmentEquals(name)) {
                    try (BytecodeCreator nameMatched = body.ifNonZero(body.invokeVirtualMethod(NI_NEXT_EQUALS, keyIter, body.load(name))).trueBranch()) {
                        // keyIter.next();
                        nameMatched.invokeVirtualMethod(NI_NEXT, keyIter);
                        // (generated recursive)
                        final int length = methodName.length();
                        methodName.append('_').append(name);
                        nameMatched.invokeStaticMethod(generateParserBody(cc, keyMap.getChild(name), methodName, parseMethodCache), config, keyIter);
                        methodName.setLength(length);
                        // return;
                        nameMatched.returnValue(null);
                    }
                    // }
                }
            }
            // todo: unknown name warning goes here
            body.returnValue(null);
            final MethodDescriptor md = body.getMethodDescriptor();
            parseMethodCache.put(methodNameStr, md);
            return md;
        }
    }

    @BuildStep
    void writeDefaultConfiguration(

    ) {

    }
}
