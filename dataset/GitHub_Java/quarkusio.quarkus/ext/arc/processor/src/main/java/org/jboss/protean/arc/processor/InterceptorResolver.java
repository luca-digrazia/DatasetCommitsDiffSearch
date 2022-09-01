package org.jboss.protean.arc.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.spi.InterceptionType;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;

public class InterceptorResolver {

    private final BeanDeployment beanDeployment;

    public InterceptorResolver(BeanDeployment beanDeployment) {
        this.beanDeployment = beanDeployment;
    }

    public List<InterceptorInfo> resolve(InterceptionType interceptionType, Set<AnnotationInstance> bindings) {
        List<InterceptorInfo> interceptors = new ArrayList<>();
        for (InterceptorInfo interceptor : beanDeployment.getInterceptors()) {
            if (!interceptor.intercepts(interceptionType)) {
                continue;
            }
            boolean matches = true;
            for (AnnotationInstance interceptorBinding : interceptor.getBindings()) {
                if (!hasInterceptorBinding(bindings, interceptorBinding)) {
                    matches = false;
                }
            }
            if (matches) {
                interceptors.add(interceptor);
            }
        }
        interceptors.sort(this::compare);
        return interceptors;
    }

    private int compare(InterceptorInfo i1, InterceptorInfo i2) {
        return Integer.compare(i1.getPriority(), i2.getPriority());
    }

    private boolean hasInterceptorBinding(Set<AnnotationInstance> bindings, AnnotationInstance interceptorBinding) {
        ClassInfo interceptorBindingClass = beanDeployment.getInterceptorBinding(interceptorBinding.name());
        for (AnnotationInstance binding : bindings) {
            if (binding.name().equals(interceptorBinding.name())) {
                // Must have the same annotation member value for each member which is not annotated @Nonbinding
                boolean matches = true;
                for (AnnotationValue value : binding.values()) {
                    if (!interceptorBindingClass.method(value.name()).hasAnnotation(DotNames.NONBINDING)
                            && !value.equals(interceptorBinding.value(value.name()))) {
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
