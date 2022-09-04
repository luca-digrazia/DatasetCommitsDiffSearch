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

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

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
        for (AnnotationInstance annotation : beanClass.classAnnotations()) {
            if (beanDeployment.getQualifier(annotation.name()) != null) {
                qualifiers.add(annotation);
            } else if (annotation.name().equals(DotNames.ALTERNATIVE)) {
                isAlternative = true;
            } else if (annotation.name().equals(DotNames.PRIORITY)) {
                alternativePriority = annotation.value().asInt();
            } else if (scope == null) {
                scope = ScopeInfo.from(annotation.name());
            }
        }
        return new BeanInfo(beanClass, beanDeployment, scope, types, qualifiers, Injection.forBean(beanClass, beanDeployment), null, null,
                isAlternative ? alternativePriority : null);
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
        for (AnnotationInstance annotation : producerMethod.annotations()) {
            if (beanDeployment.getQualifier(annotation.name()) != null) {
                qualifiers.add(annotation);
            } else if (scope == null) {
                scope = ScopeInfo.from(annotation.name());
            }
        }
        return new BeanInfo(producerMethod, beanDeployment, scope, types, qualifiers, Injection.forBean(producerMethod, beanDeployment), declaringBean,
                disposer, null);
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
        for (AnnotationInstance annotation : producerField.annotations()) {
            if (beanDeployment.getQualifier(annotation.name()) != null) {
                qualifiers.add(annotation);
            } else if (scope == null) {
                scope = ScopeInfo.from(annotation.name());
            }
        }
        return new BeanInfo(producerField, beanDeployment, scope, types, qualifiers, Collections.emptyList(), declaringBean, disposer, null);
    }

    static boolean matches(BeanInfo bean, InjectionPointInfo injectionPoint) {
        // Bean has all the required qualifiers
        for (AnnotationInstance requiredQualifier : injectionPoint.requiredQualifiers) {
            if (!hasQualifier(bean, requiredQualifier)) {
                return false;
            }
        }
        // Bean has a bean type that matches the required type
        return matchesType(bean, injectionPoint.requiredType);
    }

    static boolean matchesType(BeanInfo bean, Type requiredType) {
        for (Type beanType : bean.getTypes()) {
            if (bean.getDeployment().getBeanResolver().matches(requiredType, beanType)) {
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
        List<BeanInfo> resolved = new ArrayList<>();
        for (BeanInfo b : deployment.getBeans()) {
            if (matches(b, injectionPoint)) {
                resolved.add(b);
            }
        }
        BeanInfo selected;
        if (resolved.isEmpty()) {
            throw new UnsatisfiedResolutionException(injectionPoint + " on " + bean);
        } else if (resolved.size() > 1) {
            // Try to resolve the ambiguity
            for (Iterator<BeanInfo> iterator = resolved.iterator(); iterator.hasNext();) {
                BeanInfo beanInfo = iterator.next();
                if (!beanInfo.isAlternative() && (beanInfo.getDeclaringBean() == null || !beanInfo.getDeclaringBean().isAlternative())) {
                    iterator.remove();
                }
            }
            if (resolved.size() == 1) {
                selected = resolved.get(0);
            } else if (resolved.size() > 1) {
                resolved.sort(Beans::compareAlternativeBeans);
                selected = resolved.get(0);
            } else {
                throw new AmbiguousResolutionException(
                        injectionPoint + " on " + bean + "\nBeans:\n" + resolved.stream().map(Object::toString).collect(Collectors.joining("\n")));
            }
        } else {
            selected = resolved.get(0);
        }
        injectionPoint.resolve(selected);
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
        List<AnnotationValue> binding = required.values().stream().filter(v -> !requiredInfo.method(v.name()).hasAnnotation(DotNames.NONBINDING))
                .collect(Collectors.toList());
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

}
