/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.shamrock.beanvalidation;

import static org.jboss.shamrock.annotations.ExecutionTime.STATIC_INIT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.validation.Constraint;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.hibernate.validator.messageinterpolation.AbstractMessageInterpolator;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.Record;
import org.jboss.shamrock.arc.deployment.AnnotationsTransformerBuildItem;
import org.jboss.shamrock.beanvalidation.runtime.ValidatorProvider;
import org.jboss.shamrock.beanvalidation.runtime.ValidatorTemplate;
import org.jboss.shamrock.beanvalidation.runtime.interceptor.MethodValidationInterceptor;
import org.jboss.shamrock.deployment.builditem.AdditionalBeanBuildItem;
import org.jboss.shamrock.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.shamrock.deployment.builditem.HotDeploymentConfigFileBuildItem;
import org.jboss.shamrock.deployment.builditem.SystemPropertyBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveFieldBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.ReflectiveMethodBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateConfigBuildItem;
import org.jboss.shamrock.deployment.recording.RecorderContext;

class BeanValidationProcessor {

    private static final DotName VALIDATE_ON_EXECUTION = DotName.createSimple(ValidateOnExecution.class.getName());

    private static final DotName VALID = DotName.createSimple(Valid.class.getName());

    @BuildStep
    SystemPropertyBuildItem disableJavaFXIntegrations() {
        // Bug in GraalVM rc10: see https://github.com/oracle/graal/issues/851
        return new SystemPropertyBuildItem("org.hibernate.validator.force-disable-javafx-integration", "true");
    }

    @BuildStep
    HotDeploymentConfigFileBuildItem configFile() {
        return new HotDeploymentConfigFileBuildItem("META-INF/validation.xml");
    }

    @BuildStep
    void registerAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        // The bean encapsulating the Validator and ValidatorFactory
        additionalBeans.produce(new AdditionalBeanBuildItem(ValidatorProvider.class));

        // The CDI interceptor which will validate the methods annotated with @MethodValidated
        additionalBeans.produce(new AdditionalBeanBuildItem(MethodValidationInterceptor.class));

        if (isResteasyInClasspath()) {
            // The CDI interceptor which will validate the methods annotated with @JaxrsEndPointValidated
            additionalBeans.produce(new AdditionalBeanBuildItem("org.jboss.shamrock.beanvalidation.runtime.jaxrs.JaxrsEndPointValidationInterceptor"));
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    public void build(ValidatorTemplate template, RecorderContext recorder,
                      BuildProducer<ReflectiveFieldBuildItem> reflectiveFields,
                      BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods,
                      BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformers,
                      CombinedIndexBuildItem combinedIndexBuildItem) throws Exception {

        IndexView indexView = combinedIndexBuildItem.getIndex();

        Set<DotName> consideredAnnotations = new HashSet<>();

        // Collect the constraint annotations provided by Hibernate Validator and Bean Validation
        contributeBuiltinConstraints(consideredAnnotations);

        // Add the constraint annotations present in the application itself
        for (AnnotationInstance constraint : indexView.getAnnotations(DotName.createSimple(Constraint.class.getName()))) {
            consideredAnnotations.add(constraint.target().asClass().name());
        }

        // Also consider elements that are marked with @Valid
        consideredAnnotations.add(VALID);

        // Also consider elements that are marked with @ValidateOnExecution
        consideredAnnotations.add(VALIDATE_ON_EXECUTION);

        Set<DotName> classNamesToBeValidated = new HashSet<>();

        for (DotName consideredAnnotation : consideredAnnotations) {
            Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(consideredAnnotation);

            for (AnnotationInstance annotation : annotationInstances) {
                if (annotation.target().kind() == AnnotationTarget.Kind.FIELD) {
                    contributeClass(classNamesToBeValidated, indexView, annotation.target().asField().declaringClass().name());
                    reflectiveFields.produce(new ReflectiveFieldBuildItem(annotation.target().asField()));
                    contributeClassMarkedForCascadingValidation(classNamesToBeValidated, indexView, consideredAnnotation, annotation.target().asField().type());
                } else if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                    contributeClass(classNamesToBeValidated, indexView, annotation.target().asMethod().declaringClass().name());
                    // we need to register the method for reflection as it could be a getter
                    reflectiveMethods.produce(new ReflectiveMethodBuildItem(annotation.target().asMethod()));
                    contributeClassMarkedForCascadingValidation(classNamesToBeValidated, indexView, consideredAnnotation, annotation.target().asMethod().returnType());
                } else if (annotation.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                    contributeClass(classNamesToBeValidated, indexView, annotation.target().asMethodParameter().method().declaringClass().name());
                    // a getter does not have parameters so it's a pure method: no need for reflection in this case
                    contributeClassMarkedForCascadingValidation(classNamesToBeValidated, indexView, consideredAnnotation,
                            // FIXME this won't work in the case of synthetic parameters
                            annotation.target().asMethodParameter().method().parameters().get(annotation.target().asMethodParameter().position()));
                } else if (annotation.target().kind() == AnnotationTarget.Kind.CLASS) {
                    contributeClass(classNamesToBeValidated, indexView, annotation.target().asClass().name());
                    // no need for reflection in the case of a class level constraint
                }
            }
        }

        Set<Class<?>> classesToBeValidated = new HashSet<>();
        for (DotName className : classNamesToBeValidated) {
            classesToBeValidated.add(recorder.classProxy(className.toString()));
        }
        template.initializeValidatorFactory(classesToBeValidated);

        // Add the annotations transformer to add @MethodValidated annotations on the methods requiring validation
        annotationsTransformers.produce(new AnnotationsTransformerBuildItem(new MethodValidatedAnnotationsTransformer(consideredAnnotations)));
    }

    @BuildStep
    SubstrateConfigBuildItem substrateConfig() {
        return SubstrateConfigBuildItem.builder()
                .addResourceBundle(AbstractMessageInterpolator.DEFAULT_VALIDATION_MESSAGES)
                .addResourceBundle(AbstractMessageInterpolator.USER_VALIDATION_MESSAGES)
                .addResourceBundle(AbstractMessageInterpolator.CONTRIBUTOR_VALIDATION_MESSAGES)
                .build();
    }

    private static void contributeBuiltinConstraints(Set<DotName> constraintCollector) {
        Set<Class<? extends Annotation>> builtinConstraints = new ConstraintHelper().getBuiltinConstraints();
        for (Class<? extends Annotation> builtinConstraint : builtinConstraints) {
            constraintCollector.add(DotName.createSimple(builtinConstraint.getName()));
        }
    }

    private static void contributeClass(Set<DotName> classNamesCollector, IndexView indexView, DotName className) {
        classNamesCollector.add(className);
        for (ClassInfo subclass : indexView.getAllKnownSubclasses(className)) {
            if (Modifier.isAbstract(subclass.flags())) {
                // we can avoid adding the abstract classes here: either they are parent classes
                // and they will be dealt with by Hibernate Validator or they are child classes
                // without any proper implementation and we can ignore them.
                continue;
            }
            classNamesCollector.add(subclass.name());
        }
    }

    private static void contributeClassMarkedForCascadingValidation(Set<DotName> classNamesCollector,
            IndexView indexView, DotName consideredAnnotation, Type type) {
        if (VALID != consideredAnnotation) {
            return;
        }

        DotName className = getClassName(type);
        if (className != null) {
            contributeClass(classNamesCollector, indexView, className);
        }
    }

    private static DotName getClassName(Type type) {
        switch (type.kind()) {
        case CLASS:
        case PARAMETERIZED_TYPE:
            return type.name();
        case ARRAY:
            return getClassName( type.asArrayType().component() );
        default:
            return null;
        }
    }

    private static boolean isResteasyInClasspath() {
        try {
            Class.forName("org.jboss.resteasy.core.ResteasyContext");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
