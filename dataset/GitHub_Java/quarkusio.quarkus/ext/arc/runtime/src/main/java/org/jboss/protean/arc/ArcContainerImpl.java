package org.jboss.protean.arc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Singleton;

/**
 *
 * @author Martin Kouba
 */
class ArcContainerImpl implements ArcContainer {

    private final List<InjectableBean<?>> beans;

    private final List<InjectableObserverMethod<?>> observers;

    private final Map<Class<? extends Annotation>, Context> contexts;

    private final ComputingCache<Resolvable, List<InjectableBean<?>>> resolved;

    public ArcContainerImpl() {
        beans = new CopyOnWriteArrayList<>();
        observers = new CopyOnWriteArrayList<>();
        for (ComponentsProvider componentsProvider : ServiceLoader.load(ComponentsProvider.class)) {
            Components components = componentsProvider.getComponents();
            beans.addAll(components.getBeans());
            observers.addAll(components.getObservers());
        }
        contexts = new HashMap<>();
        contexts.put(ApplicationScoped.class, new ApplicationContext());
        contexts.put(Singleton.class, new SingletonContext());
        contexts.put(RequestScoped.class, new RequestContext());
        resolved = new ComputingCache<>(this::resolve);
    }

    @Override
    public Context getContext(Class<? extends Annotation> scopeType) {
        return contexts.get(scopeType);
    }

    @Override
    public <T> InstanceHandle<T> instance(Class<T> type, Annotation... qualifiers) {
        return instanceHandle(type, qualifiers);
    }

    @Override
    public <T> InstanceHandle<T> instance(TypeLiteral<T> type, Annotation... qualifiers) {
        return instanceHandle(type.getType(), qualifiers);
    }

    @Override
    public RequestContext requestContext() {
        return (RequestContext) getContext(RequestScoped.class);
    }

    @Override
    public void withinRequest(Runnable action) {
        try {
            requestContext().activate();
            action.run();
        } finally {
            requestContext().deactivate();
        }
    }

    synchronized void shutdown() {
        ((ApplicationContext) contexts.get(ApplicationScoped.class)).destroy();
        ((SingletonContext) contexts.get(Singleton.class)).destroy();
        requestContext().deactivate();
        beans.clear();
        resolved.clear();
    }

    private <T> InstanceHandle<T> instanceHandle(Type type, Annotation... qualifiers) {
        return instance(getBean(type, qualifiers));
    }

    private <T> InstanceHandle<T> instance(InjectableBean<T> bean) {
        if (bean != null) {
            CreationalContextImpl<T> parentContext = new CreationalContextImpl<>();
            InjectionPoint prev = InjectionPointProvider.CURRENT.get();
            InjectionPointProvider.CURRENT.set(CurrentInjectionPointProvider.EMPTY);
            try {
                CreationalContextImpl<T> creationalContext = parentContext.child();
                return new InstanceHandleImpl<T>(bean, bean.get(creationalContext), creationalContext, parentContext);
            } finally {
                if (prev != null) {
                    InjectionPointProvider.CURRENT.set(prev);
                } else {
                    InjectionPointProvider.CURRENT.remove();
                }
            }
        } else {
            return InstanceHandleImpl.unresolvable();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> InjectableBean<T> getBean(Type requiredType, Annotation... qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            qualifiers = new Annotation[] { Default.Literal.INSTANCE };
        }
        List<InjectableBean<?>> resolvedBeans = resolved.getValue(new Resolvable(requiredType, qualifiers));
        return resolvedBeans.size() == 1 ? (InjectableBean<T>) resolvedBeans.get(0) : null;
    }

    private List<InjectableBean<?>> resolve(Resolvable resolvable) {
        List<InjectableBean<?>> resolvedBeans = new ArrayList<>();
        for (InjectableBean<?> bean : beans) {
            if (matches(bean, resolvable.requiredType, resolvable.qualifiers)) {
                resolvedBeans.add(bean);
            }
        }
        return resolvedBeans;
    }

    @SuppressWarnings("unchecked")
    <T> List<InjectableObserverMethod<? super T>> resolveObservers(Type eventType, Set<Annotation> eventQualifiers) {
        if (observers.isEmpty()) {
            return Collections.emptyList();
        }
        List<InjectableObserverMethod<? super T>> resolvedObservers = new ArrayList<>();
        for (InjectableObserverMethod<?> observer : observers) {
            if (EventTypeAssignabilityRules.matches(observer.getObservedType(), eventType)) {
                if (observer.getObservedQualifiers().isEmpty() || Qualifiers.isSubset(observer.getObservedQualifiers(), eventQualifiers)) {
                    resolvedObservers.add((InjectableObserverMethod<? super T>) observer);
                }
            }
        }
        return resolvedObservers;
    }

    List<InjectableBean<?>> geBeans(Type requiredType, Annotation... qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            qualifiers = new Annotation[] { Default.Literal.INSTANCE };
        }
        return resolved.getValue(new Resolvable(requiredType, qualifiers));
    }

    private boolean matches(InjectableBean<?> bean, Type requiredType, Annotation... qualifiers) {
        if (!BeanTypeAssignabilityRules.matches(requiredType, bean.getTypes())) {
            return false;
        }
        return Qualifiers.hasQualifiers(bean, qualifiers);
    }

    static <T> ArcContainerImpl unwrap(ArcContainer container) {
        if (container instanceof ArcContainerImpl) {
            return (ArcContainerImpl) container;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return "ArcContainerImpl [beans=" + beans + ", contexts=" + contexts + "]";
    }

    private static final class Resolvable {

        final Type requiredType;

        final Annotation[] qualifiers;

        Resolvable(Type requiredType, Annotation[] qualifiers) {
            this.requiredType = requiredType;
            this.qualifiers = qualifiers;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(qualifiers);
            result = prime * result + ((requiredType == null) ? 0 : requiredType.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Resolvable)) {
                return false;
            }
            Resolvable other = (Resolvable) obj;
            if (requiredType == null) {
                if (other.requiredType != null) {
                    return false;
                }
            } else if (!requiredType.equals(other.requiredType)) {
                return false;
            }
            if (!Arrays.equals(qualifiers, other.qualifiers)) {
                return false;
            }
            return true;
        }

    }

}
