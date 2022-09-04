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

package org.jboss.protean.arc.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.UnsatisfiedResolutionException;

import org.jboss.jandex.*;
import org.jboss.jandex.Type.Kind;
import org.jboss.protean.arc.processor.InjectionPointInfo.TypeAndQualifiers;

final class Beans {

    private Beans() {
    }

    /**
     *
     * @param beanClass
     * @param beanDeployment
     * @return a new bean info
     */
    static BeanInfo createClassBean(ClassInfo beanClass, BeanDeployment beanDeployment) {
        Set<AnnotationInstance> qualifiers = new HashSet<>();
        ScopeInfo scope = null;
        Set<Type> types = Types.getTypeClosure(beanClass, Collections.emptyMap(), beanDeployment);
        Integer alternativePriority = null;
        boolean isAlternative = false;
        List<StereotypeInfo> stereotypes = new ArrayList<>();

        for (AnnotationInstance annotation : beanDeployment.getAnnotations(beanClass)) {
            if (beanDeployment.getQualifier(annotation.name()) != null) {
                qualifiers.add(annotation);
            } else if (annotation.name().equals(DotNames.ALTERNATIVE)) {
                isAlternative = true;
            } else if (annotation.name().equals(DotNames.PRIORITY)) {
                alternativePriority = annotation.value().asInt();
            } else {
                if (scope == null) {
                    scope = ScopeInfo.from(annotation.name());
                }
                StereotypeInfo stereotype = beanDeployment.getStereotype(annotation.name());
                if (stereotype != null) {
                    stereotypes.add(stereotype);
                }
            }
        }

        if (scope == null) {
            scope = initStereotypeScope(stereotypes, beanClass);
        }
        if (!isAlternative) {
            isAlternative = initStereotypeAlternative(stereotypes);
        }

        BeanInfo bean = new BeanInfo(beanClass, beanDeployment, scope, types, qualifiers, Injection.forBean(beanClass, beanDeployment), null, null,
                isAlternative ? alternativePriority : null, stereotypes);
        return bean;
    }

    /**
     *
     * @param producerMethod
     * @param declaringBean
     * @param beanDeployment
     * @param disposer
     * @return a new bean info
     */
    static BeanInfo createProducerMethod(MethodInfo producerMethod, BeanInfo declaringBean, BeanDeployment beanDeployment, DisposerInfo disposer) {
        Set<AnnotationInstance> qualifiers = new HashSet<>();
        ScopeInfo scope = null;
        Set<Type> types = Types.getTypeClosure(producerMethod, beanDeployment);
        Integer alternativePriority = null;
        boolean isAlternative = false;
        List<StereotypeInfo> stereotypes = new ArrayList<>();

        for (AnnotationInstance annotation : producerMethod.annotations()) {
            if (beanDeployment.getQualifier(annotation.name()) != null) {
                qualifiers.add(annotation);
            } else if (annotation.name().equals(DotNames.ALTERNATIVE)) {
                isAlternative = true;
            } else {
                if (scope == null) {
                    scope = ScopeInfo.from(annotation.name());
                }
                StereotypeInfo stereotype = beanDeployment.getStereotype(annotation.name());
                if (stereotype != null) {
                    stereotypes.add(stereotype);
                }
            }
        }

        if (scope == null) {
            scope = initStereotypeScope(stereotypes, producerMethod);
        }
        if (!isAlternative) {
            isAlternative = initStereotypeAlternative(stereotypes);
        }

        if (isAlternative) {
            alternativePriority = declaringBean.getAlternativePriority();
            if (alternativePriority == null) {
                // Declaring bean itself does not have to be an alternive and can only have @Priority
                alternativePriority = declaringBean.getTarget().get().asClass().classAnnotations().stream().filter(a -> a.name().equals(DotNames.PRIORITY)).findAny()
                        .map(a -> a.value().asInt()).orElse(null);
            }
        }

        BeanInfo bean = new BeanInfo(producerMethod, beanDeployment, scope, types, qualifiers, Injection.forBean(producerMethod, beanDeployment), declaringBean,
                disposer, alternativePriority, stereotypes);
        return bean;
    }

    /**
     *
     * @param producerField
     * @param declaringBean
     * @param beanDeployment
     * @param disposer
     * @return a new bean info
     */
    static BeanInfo createProducerField(FieldInfo producerField, BeanInfo declaringBean, BeanDeployment beanDeployment, DisposerInfo disposer) {
        Set<AnnotationInstance> qualifiers = new HashSet<>();
        ScopeInfo scope = null;
        Set<Type> types = Types.getTypeClosure(producerField, beanDeployment);
        Integer alternativePriority = null;
        boolean isAlternative = false;
        List<StereotypeInfo> stereotypes = new ArrayList<>();

        for (AnnotationInstance annotation : producerField.annotations()) {
            if (beanDeployment.getQualifier(annotation.name()) != null) {
                qualifiers.add(annotation);
            } else {
                if (scope == null) {
                    scope = ScopeInfo.from(annotation.name());
                }
                StereotypeInfo stereotype = beanDeployment.getStereotype(annotation.name());
                if (stereotype != null) {
                    stereotypes.add(stereotype);
                }
            }
        }

        if (scope == null) {
            scope = initStereotypeScope(stereotypes, producerField);
        }
        if (!isAlternative) {
            isAlternative = initStereotypeAlternative(stereotypes);
        }

        if (isAlternative) {
            alternativePriority = declaringBean.getAlternativePriority();
            if (alternativePriority == null) {
                // Declaring bean itself does not have to be an alternive and can only have @Priority
                alternativePriority = declaringBean.getTarget().get().asClass().classAnnotations().stream().filter(a -> a.name().equals(DotNames.PRIORITY)).findAny()
                        .map(a -> a.value().asInt()).orElse(null);
            }
        }

        BeanInfo bean = new BeanInfo(producerField, beanDeployment, scope, types, qualifiers, Collections.emptyList(), declaringBean, disposer,
                alternativePriority, stereotypes);
        return bean;
    }

    private static ScopeInfo initStereotypeScope(List<StereotypeInfo> stereotypes, AnnotationTarget target) {
        if (stereotypes.isEmpty()) {
            return null;
        }
        ScopeInfo stereotypeScope = null;
        for (StereotypeInfo stereotype : stereotypes) {
            if (stereotype.getDefaultScope() != stereotypeScope) {
                if (stereotypeScope == null) {
                    stereotypeScope = stereotype.getDefaultScope();
                } else {
                    throw new IllegalStateException("All stereotypes must specify the same scope or the bean must declare a scope: " + target);
                }
            }
        }
        return stereotypeScope;
    }

    private static boolean initStereotypeAlternative(List<StereotypeInfo> stereotypes) {
        if (stereotypes.isEmpty()) {
            return false;
        }
        for (StereotypeInfo stereotype : stereotypes) {
            if (stereotype.isAlternative()) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(BeanInfo bean, TypeAndQualifiers typeAndQualifiers) {
        // Bean has all the required qualifiers
        for (AnnotationInstance requiredQualifier : typeAndQualifiers.qualifiers) {
            if (!hasQualifier(bean, requiredQualifier)) {
                return false;
            }
        }
        // Bean has a bean type that matches the required type
        return matchesType(bean, typeAndQualifiers.type);
    }

    static boolean matchesType(BeanInfo bean, Type requiredType) {
        BeanResolver beanResolver = bean.getDeployment().getBeanResolver();
        for (Type beanType : bean.getTypes()) {
            if (beanResolver.matches(requiredType, beanType)) {
                return true;
            }
        }
        return false;
    }

    static void resolveInjectionPoint(BeanDeployment deployment, BeanInfo bean, InjectionPointInfo injectionPoint) {
        if (BuiltinBean.resolvesTo(injectionPoint)) {
            // Skip built-in beans
            return;
        }
        List<BeanInfo> resolved = deployment.getBeanResolver().resolve(injectionPoint.getTypeAndQualifiers());
        BeanInfo selected = null;
        if (resolved.isEmpty()) {
            throw new UnsatisfiedResolutionException(injectionPoint + " on " + bean);
        } else if (resolved.size() > 1) {
            // Try to resolve the ambiguity
            List<BeanInfo> resolvedAmbiguity = new ArrayList<>(resolved);
            for (Iterator<BeanInfo> iterator = resolvedAmbiguity.iterator(); iterator.hasNext();) {
                BeanInfo beanInfo = iterator.next();
                if (!beanInfo.isAlternative() && (beanInfo.getDeclaringBean() == null || !beanInfo.getDeclaringBean().isAlternative())) {
                    iterator.remove();
                }
            }
            if (resolvedAmbiguity.size() == 1) {
                selected = resolvedAmbiguity.get(0);
            } else if (resolvedAmbiguity.size() > 1) {
                // Keep only the highest priorities
                resolvedAmbiguity.sort(Beans::compareAlternativeBeans);
                Integer highest = getAlternativePriority(resolvedAmbiguity.get(0));
                for (Iterator<BeanInfo> iterator = resolvedAmbiguity.iterator(); iterator.hasNext();) {
                    if (!highest.equals(getAlternativePriority(iterator.next()))) {
                        iterator.remove();
                    }
                }
                if (resolved.size() == 1) {
                    selected = resolvedAmbiguity.get(0);
                }
            }
            if (selected == null) {
                throw new AmbiguousResolutionException(
                        injectionPoint + " on " + bean + "\nBeans:\n" + resolved.stream().map(Object::toString).collect(Collectors.joining("\n")));
            }
        } else {
            selected = resolved.get(0);
        }
        injectionPoint.resolve(selected);
    }

    private static Integer getAlternativePriority(BeanInfo bean) {
        return bean.getDeclaringBean() != null ? bean.getDeclaringBean().getAlternativePriority() : bean.getAlternativePriority();
    }

    private static int compareAlternativeBeans(BeanInfo bean1, BeanInfo bean2) {
        // The highest priority wins
        Integer priority2 = bean2.getDeclaringBean() != null ? bean2.getDeclaringBean().getAlternativePriority() : bean2.getAlternativePriority();
        Integer priority1 = bean1.getDeclaringBean() != null ? bean1.getDeclaringBean().getAlternativePriority() : bean1.getAlternativePriority();
        return priority2.compareTo(priority1);
    }

    static boolean hasQualifier(BeanInfo bean, AnnotationInstance required) {
        return hasQualifier(bean.getDeployment().getQualifier(required.name()), required, bean.getQualifiers());
    }

    static boolean hasQualifier(ClassInfo requiredInfo, AnnotationInstance required, Collection<AnnotationInstance> qualifiers) {
        List<AnnotationValue> binding = new ArrayList<>();
        for (AnnotationValue val : required.values()) {
            if (!requiredInfo.method(val.name()).hasAnnotation(DotNames.NONBINDING)) {
                binding.add(val);
            }
        }
        for (AnnotationInstance qualifier : qualifiers) {
            if (required.name().equals(qualifier.name())) {
                // Must have the same annotation member value for each member which is not annotated @Nonbinding
                boolean matches = true;
                for (AnnotationValue value : binding) {
                    if (!value.equals(qualifier.value(value.name()))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<MethodInfo> getCallbacks(ClassInfo beanClass, DotName annotation, IndexView index) {
        List<MethodInfo> callbacks = new ArrayList<>();
        collectCallbacks(beanClass, callbacks, annotation, index);
        Collections.reverse(callbacks);
        return callbacks;
    }


    static void analyzeType(Type type, BeanDeployment beanDeployment) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            for (Type argument : type.asParameterizedType().arguments()) {
                fetchType(argument, beanDeployment);
            }
        } else if (type.kind() == Type.Kind.TYPE_VARIABLE) {
            for (Type bound : type.asTypeVariable().bounds()) {
                fetchType(bound, beanDeployment);
            }
        } else if (type.kind() == Type.Kind.WILDCARD_TYPE) {
            fetchType(type.asWildcardType().extendsBound(), beanDeployment);
            fetchType(type.asWildcardType().superBound(), beanDeployment);
        }
    }

    private static void fetchType(Type type, BeanDeployment beanDeployment) {
        if (type == null) {
            return;
        }
        if (type.kind() == Type.Kind.CLASS) {
            // Index the class additionally if needed
            beanDeployment.getIndex().getClassByName(type.name());
        } else {
            analyzeType(type, beanDeployment);
        }
    }

    private static void collectCallbacks(ClassInfo clazz, List<MethodInfo> callbacks, DotName annotation, IndexView index) {
        for (MethodInfo method : clazz.methods()) {
            if (method.hasAnnotation(annotation) && method.returnType().kind() == Kind.VOID && method.parameters().isEmpty()) {
                callbacks.add(method);
            }
        }
        if (clazz.superName() != null) {
            ClassInfo superClass = index.getClassByName(clazz.superName());
            if (superClass != null) {
                collectCallbacks(superClass, callbacks, annotation, index);
            }
        }
    }

}
