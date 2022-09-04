package io.quarkus.mongodb.panache.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.index.IndexingUtil;
import io.quarkus.jackson.spi.JacksonModuleBuildItem;
import io.quarkus.jsonb.spi.JsonbDeserializerBuildItem;
import io.quarkus.jsonb.spi.JsonbSerializerBuildItem;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import io.quarkus.panache.common.deployment.PanacheFieldAccessEnhancer;
import io.quarkus.panache.common.deployment.PanacheRepositoryEnhancer;

public class PanacheResourceProcessor {
    static final DotName DOTNAME_PANACHE_REPOSITORY_BASE = DotName.createSimple(PanacheMongoRepositoryBase.class.getName());
    private static final DotName DOTNAME_PANACHE_REPOSITORY = DotName.createSimple(PanacheMongoRepository.class.getName());
    static final DotName DOTNAME_PANACHE_ENTITY_BASE = DotName.createSimple(PanacheMongoEntityBase.class.getName());
    private static final DotName DOTNAME_PANACHE_ENTITY = DotName.createSimple(PanacheMongoEntity.class.getName());

    private static final DotName DOTNAME_OBJECT_ID = DotName.createSimple(ObjectId.class.getName());

    @BuildStep
    CapabilityBuildItem capability() {
        return new CapabilityBuildItem(Capabilities.MONGODB_PANACHE);
    }

    @BuildStep
    FeatureBuildItem featureBuildItem() {
        return new FeatureBuildItem(FeatureBuildItem.MONGODB_PANACHE);
    }

    @BuildStep
    void registerJsonbSerDeser(BuildProducer<JsonbSerializerBuildItem> jsonbSerializers,
            BuildProducer<JsonbDeserializerBuildItem> jsonbDeserializers) {
        jsonbSerializers
                .produce(new JsonbSerializerBuildItem(io.quarkus.mongodb.panache.jsonb.ObjectIdSerializer.class.getName()));
        jsonbDeserializers
                .produce(new JsonbDeserializerBuildItem(io.quarkus.mongodb.panache.jsonb.ObjectIdDeserializer.class.getName()));
    }

    @BuildStep
    void registerJacksonSerDeser(BuildProducer<JacksonModuleBuildItem> customSerDeser) {
        customSerDeser.produce(
                new JacksonModuleBuildItem.Builder("ObjectIdModule")
                        .add(io.quarkus.mongodb.panache.jackson.ObjectIdSerializer.class.getName(),
                                io.quarkus.mongodb.panache.jackson.ObjectIdDeserializer.class.getName(),
                                ObjectId.class.getName())
                        .build());
    }

    @BuildStep
    ReflectiveHierarchyBuildItem registerForReflection(CombinedIndexBuildItem index) {
        Indexer indexer = new Indexer();
        Set<DotName> additionalIndex = new HashSet<>();
        IndexingUtil.indexClass(ObjectId.class.getName(), indexer, index.getIndex(), additionalIndex,
                PanacheResourceProcessor.class.getClassLoader());
        CompositeIndex compositeIndex = CompositeIndex.create(index.getIndex(), indexer.complete());
        Type type = Type.create(DOTNAME_OBJECT_ID, Type.Kind.CLASS);
        return new ReflectiveHierarchyBuildItem(type, compositeIndex);
    }

    @BuildStep
    void build(CombinedIndexBuildItem index,
            ApplicationIndexBuildItem applicationIndex,
            BuildProducer<BytecodeTransformerBuildItem> transformers,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) throws Exception {

        PanacheMongoRepositoryEnhancer daoEnhancer = new PanacheMongoRepositoryEnhancer(index.getIndex());
        Set<String> daoClasses = new HashSet<>();
        Set<String> daoTypeParameters = new HashSet<>();
        for (ClassInfo classInfo : index.getIndex().getAllKnownImplementors(DOTNAME_PANACHE_REPOSITORY_BASE)) {
            // Skip PanacheRepository
            if (classInfo.name().equals(DOTNAME_PANACHE_REPOSITORY))
                continue;
            if (PanacheRepositoryEnhancer.skipRepository(classInfo))
                continue;
            daoClasses.add(classInfo.name().toString());
            daoTypeParameters.addAll(extractEntities(index.getIndex(), classInfo));
        }
        for (ClassInfo classInfo : index.getIndex().getAllKnownImplementors(DOTNAME_PANACHE_REPOSITORY)) {
            if (PanacheRepositoryEnhancer.skipRepository(classInfo))
                continue;
            daoClasses.add(classInfo.name().toString());
            daoTypeParameters.addAll(extractEntities(index.getIndex(), classInfo));
        }
        for (String daoClass : daoClasses) {
            transformers.produce(new BytecodeTransformerBuildItem(daoClass, daoEnhancer));
        }

        // Register for reflection the type parameters of the repository: this should be the entity class and the ID class
        for (String parameterType : daoTypeParameters) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, parameterType));
        }

        PanacheMongoEntityEnhancer modelEnhancer = new PanacheMongoEntityEnhancer(index.getIndex());
        Set<String> modelClasses = new HashSet<>();
        // Note that we do this in two passes because for some reason Jandex does not give us subtypes
        // of PanacheMongoEntity if we ask for subtypes of PanacheMongoEntityBase
        for (ClassInfo classInfo : index.getIndex().getAllKnownSubclasses(DOTNAME_PANACHE_ENTITY_BASE)) {
            if (classInfo.name().equals(DOTNAME_PANACHE_ENTITY))
                continue;
            if (modelClasses.add(classInfo.name().toString()))
                modelEnhancer.collectFields(classInfo);
        }
        for (ClassInfo classInfo : index.getIndex().getAllKnownSubclasses(DOTNAME_PANACHE_ENTITY)) {
            if (modelClasses.add(classInfo.name().toString()))
                modelEnhancer.collectFields(classInfo);
        }
        for (String modelClass : modelClasses) {
            transformers.produce(new BytecodeTransformerBuildItem(modelClass, modelEnhancer));
        }

        if (!modelEnhancer.entities.isEmpty()) {
            PanacheFieldAccessEnhancer panacheFieldAccessEnhancer = new PanacheFieldAccessEnhancer(
                    modelEnhancer.getModelInfo());
            for (ClassInfo classInfo : applicationIndex.getIndex().getKnownClasses()) {
                String className = classInfo.name().toString();
                if (!modelClasses.contains(className)) {
                    transformers.produce(new BytecodeTransformerBuildItem(className, panacheFieldAccessEnhancer));
                }
            }
        }
    }

    private List<String> extractEntities(IndexView index, ClassInfo classInfo) {
        List<String> possibleEntityClasses = new ArrayList<>();
        extractEntitiesFromSuper(index, classInfo.superClassType(), possibleEntityClasses);
        classInfo.interfaceTypes().stream()
                .filter(interfaceType -> interfaceType.kind() == Type.Kind.PARAMETERIZED_TYPE)
                .forEach(interfaceType -> interfaceType.asParameterizedType().arguments()
                        .forEach(type -> possibleEntityClasses.add(type.name().toString())));
        return possibleEntityClasses;
    }

    private void extractEntitiesFromSuper(IndexView index, Type superCLassType, List<String> possibleEntityClasses) {
        if (superCLassType.name().toString().equals("java.lang.Object")) {
            return;
        }

        if (superCLassType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            // we may have an entity class inside a parent (abstract repository case)
            superCLassType.asParameterizedType().arguments()
                    .forEach(type -> possibleEntityClasses.add(type.name().toString()));
        }

        // we climb up the hierarchy of classes
        ClassInfo superClassInfo = index.getClassByName(superCLassType.name());
        extractEntitiesFromSuper(index, superClassInfo.superClassType(), possibleEntityClasses);
    }
}
