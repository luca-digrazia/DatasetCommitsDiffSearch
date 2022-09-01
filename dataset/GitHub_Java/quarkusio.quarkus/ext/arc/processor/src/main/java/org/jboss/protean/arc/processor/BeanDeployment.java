package org.jboss.protean.arc.processor;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassInfo.NestingType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

/**
 *
 * @author Martin Kouba
 */
public class BeanDeployment {

    private static final Logger LOGGER = Logger.getLogger(BeanDeployment.class);

    private final IndexView index;

    private final Map<DotName, ClassInfo> qualifiers;

    private final Map<DotName, ClassInfo> interceptorBindings;

    private final List<BeanInfo> beans;

    private final List<InterceptorInfo> interceptors;

    private final BeanResolver beanResolver;

    private final InterceptorResolver interceptorResolver;

    public BeanDeployment(IndexView index, Collection<DotName> additionalBeanDefiningAnnotations) {
        long start = System.currentTimeMillis();
        this.index = index;
        this.qualifiers = findQualifiers(index);
        // TODO interceptor bindings are transitive!!!
        this.interceptorBindings = findInterceptorBindings(index);
        this.interceptors = findInterceptors();
        this.beans = findBeans(initBeanDefiningAnnotations(additionalBeanDefiningAnnotations));
        this.beanResolver = new BeanResolver(this);
        this.interceptorResolver = new InterceptorResolver(this);
        // TODO observers
        LOGGER.infof("Build deployment created in %s ms", System.currentTimeMillis() - start);
    }

    Collection<BeanInfo> getBeans() {
        return beans;
    }

    Collection<InterceptorInfo> getInterceptors() {
        return interceptors;
    }

    IndexView getIndex() {
        return index;
    }

    BeanResolver getBeanResolver() {
        return beanResolver;
    }

    InterceptorResolver getInterceptorResolver() {
        return interceptorResolver;
    }

    ClassInfo getQualifier(DotName name) {
        return qualifiers.get(name);
    }

    ClassInfo getInterceptorBinding(DotName name) {
        return interceptorBindings.get(name);
    }

    void init() {
        long start = System.currentTimeMillis();
        for (BeanInfo bean : beans) {
            bean.init();
        }
        for (InterceptorInfo interceptor : interceptors) {
            interceptor.init();
        }
        LOGGER.infof("Bean deployment initialized in %s ms", System.currentTimeMillis() - start);
    }

    static Map<DotName, ClassInfo> findQualifiers(IndexView index) {
        Map<DotName, ClassInfo> qualifiers = new HashMap<>();
        for (AnnotationInstance qualifier : index.getAnnotations(DotNames.QUALIFIER)) {
            qualifiers.put(qualifier.target().asClass().name(), qualifier.target().asClass());
        }
        return qualifiers;
    }

    static Map<DotName, ClassInfo> findInterceptorBindings(IndexView index) {
        Map<DotName, ClassInfo> bindings = new HashMap<>();
        for (AnnotationInstance binding : index.getAnnotations(DotNames.INTERCEPTOR_BINDING)) {
            bindings.put(binding.target().asClass().name(), binding.target().asClass());
        }
        return bindings;
    }

    private List<BeanInfo> findBeans(List<DotName> beanDefiningAnnotations) {

        Set<ClassInfo> beanClasses = new HashSet<>();
        Set<MethodInfo> producerMethods = new HashSet<>();
        Set<FieldInfo> producerFields = new HashSet<>();

        for (DotName beanDefiningAnnotation : beanDefiningAnnotations) {
            for (AnnotationInstance annotation : index.getAnnotations(beanDefiningAnnotation)) {
                if (Kind.CLASS.equals(annotation.target().kind())) {

                    ClassInfo beanClass = annotation.target().asClass();

                    if (beanClass.annotations().containsKey(DotNames.INTERCEPTOR)) {
                        // Skip interceptors
                        continue;
                    }
                    if (beanClass.nestingType().equals(NestingType.ANONYMOUS) || beanClass.nestingType().equals(NestingType.LOCAL)
                            || (beanClass.nestingType().equals(NestingType.INNER) && !Modifier.isStatic(beanClass.flags()))) {
                        // Skip annonymous, local and inner classes
                        continue;
                    }
                    beanClasses.add(beanClass);

                    for (MethodInfo method : beanClass.methods()) {
                        if (method.hasAnnotation(DotNames.PRODUCES)) {
                            producerMethods.add(method);
                        }
                    }
                    for (FieldInfo field : beanClass.fields()) {
                        if (field.annotations().stream().anyMatch(a -> a.name().equals(DotNames.PRODUCES))) {
                            producerFields.add(field);
                        }
                    }
                }
            }
        }

        // Build metadata for typesafe resolution
        List<BeanInfo> beans = new ArrayList<>();
        Map<ClassInfo, BeanInfo> beanClassToBean = new HashMap<>();
        for (ClassInfo beanClass : beanClasses) {
            BeanInfo classBean = Beans.createClassBean(beanClass, this);
            beans.add(classBean);
            beanClassToBean.put(beanClass, classBean);
        }
        for (MethodInfo producerMethod : producerMethods) {
            BeanInfo declaringBean = beanClassToBean.get(producerMethod.declaringClass());
            if (declaringBean != null) {
                beans.add(Beans.createProducerMethod(producerMethod, declaringBean, this));
            }
        }
        for (FieldInfo producerField : producerFields) {
            BeanInfo declaringBean = beanClassToBean.get(producerField.declaringClass());
            if (declaringBean != null) {
                beans.add(Beans.createProducerField(producerField, declaringBean, this));
            }
        }
        if (LOGGER.isDebugEnabled()) {
            for (BeanInfo bean : beans) {
                LOGGER.logf(Level.DEBUG, "Created %s", bean);
            }
        }
        return beans;
    }

    private List<InterceptorInfo> findInterceptors() {
        Set<ClassInfo> interceptorClasses = new HashSet<>();
        for (AnnotationInstance annotation : index.getAnnotations(DotNames.INTERCEPTOR)) {
            if (Kind.CLASS.equals(annotation.target().kind())) {
                interceptorClasses.add(annotation.target().asClass());
            }
        }
        List<InterceptorInfo> interceptors = new ArrayList<>();
        for (ClassInfo interceptorClass : interceptorClasses) {
            interceptors.add(Interceptors.createInterceptor(interceptorClass, this));
        }
        if (LOGGER.isDebugEnabled()) {
            for (InterceptorInfo interceptor : interceptors) {
                LOGGER.logf(Level.DEBUG, "Created %s", interceptor);
            }
        }
        return interceptors;
    }

    private List<DotName> initBeanDefiningAnnotations(Collection<DotName> additionalBeanDefiningAnnotationss) {
        List<DotName> beanDefiningAnnotations = new ArrayList<>();
        for (ScopeInfo scope : ScopeInfo.values()) {
            beanDefiningAnnotations.add(scope.getDotName());
        }
        if (additionalBeanDefiningAnnotationss != null) {
            beanDefiningAnnotations.addAll(additionalBeanDefiningAnnotationss);
        }
        return beanDefiningAnnotations;
    }

}
