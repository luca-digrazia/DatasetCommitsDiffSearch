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

package io.quarkus.arc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Represents an injectable bean.
 *
 * @author Martin Kouba
 *
 * @param <T>
 */
public interface InjectableBean<T> extends Bean<T>, InjectableReferenceProvider<T> {

    /**
     * The identifier is generated by the container and is unique for a specific deployment.
     *
     * @return the identifier for this bean
     */
    String getIdentifier();

    /**
     *
     * @return the scope
     */
    @Override
    default Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    /**
     *
     * @return the set of bean types
     */
    @Override
    Set<Type> getTypes();

    /**
     *
     * @return the set of qualifiers
     */
    @Override
    default Set<Annotation> getQualifiers() {
        return Qualifiers.DEFAULT_QUALIFIERS;
    }

    @Override
    default void destroy(T instance, CreationalContext<T> creationalContext) {
        creationalContext.release();
    }

    /**
     *
     * @return the declaring bean if the bean is a producer method/field, or {@code null}
     */
    default InjectableBean<?> getDeclaringBean() {
        return null;
    }

    @Override
    default String getName() {
        return null;
    }

    @Override
    default Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    default Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    default boolean isNullable() {
        return false;
    }

    @Override
    default boolean isAlternative() {
        return getAlternativePriority() != null;
    }

    /**
     *
     * @return the priority if the bean is an alternative, or {@code null}
     */
    default Integer getAlternativePriority() {
        return null;
    }

}
