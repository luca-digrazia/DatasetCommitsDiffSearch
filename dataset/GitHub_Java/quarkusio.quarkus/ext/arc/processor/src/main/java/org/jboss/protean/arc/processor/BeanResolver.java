package org.jboss.protean.arc.processor;

import static java.util.Collections.singletonList;
import static org.jboss.jandex.Type.Kind.ARRAY;
import static org.jboss.jandex.Type.Kind.CLASS;
import static org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE;
import static org.jboss.jandex.Type.Kind.TYPE_VARIABLE;
import static org.jboss.jandex.Type.Kind.WILDCARD_TYPE;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.jboss.jandex.TypeVariable;
import org.jboss.jandex.WildcardType;

/**
 *
 * @author Martin Kouba
 */
class BeanResolver {

    private final ConcurrentMap<DotName, Set<DotName>> assignableFromMap;

    private final Function<DotName, Set<DotName>> assignableFromMapFunction;

    public BeanResolver(BeanDeployment beanDeployment) {
        this.assignableFromMap = new ConcurrentHashMap<>();
        this.assignableFromMapFunction = name -> {
            Set<DotName> assignables = new HashSet<>();
            assignables.addAll(beanDeployment.getIndex().getAllKnownSubclasses(name).stream().map(c -> c.name()).collect(Collectors.toSet()));
            assignables.addAll(beanDeployment.getIndex().getAllKnownImplementors(name).stream().map(c -> c.name()).collect(Collectors.toSet()));
            return assignables;
        };
    }

    boolean matches(Type requiredType, Type beanType) {

        if (requiredType == beanType) {
            return true;
        }

        // TODO box types

        if (ARRAY.equals(requiredType.kind())) {
            if (ARRAY.equals(beanType.kind())) {
                // Array types are considered to match only if their element types are identical
                return matches(requiredType.asArrayType().component(), beanType.asArrayType().component());
            }
        } else if (CLASS.equals(requiredType.kind())) {
            if (CLASS.equals(beanType.kind())) {
                return requiredType.name().equals(beanType.name());
            } else if (PARAMETERIZED_TYPE.equals(beanType.kind())) {
                // A parameterized bean type is considered assignable to a raw required type if the raw types
                // are identical and all type parameters of the bean type are either unbounded type variables or
                // java.lang.Object.
                if (!requiredType.name().equals(beanType.asParameterizedType().name())) {
                    return false;
                }
                return containsUnboundedTypeVariablesOrObjects(beanType.asParameterizedType().arguments());
            }
        } else if (PARAMETERIZED_TYPE.equals(requiredType.kind())) {
            if (CLASS.equals(beanType.kind())) {
                // A raw bean type is considered assignable to a parameterized required type if the raw types are
                // identical and all type parameters of the required type are either unbounded type variables or
                // java.lang.Object.
                if (!beanType.name().equals(requiredType.asParameterizedType().name())) {
                    return false;
                }
                return containsUnboundedTypeVariablesOrObjects(requiredType.asParameterizedType().arguments());
            } else if (PARAMETERIZED_TYPE.equals(beanType.kind())) {
                // A parameterized bean type is considered assignable to a parameterized required type if they have
                // identical raw type and for each parameter.
                if (!requiredType.name().equals(beanType.name())) {
                    return false;
                }
                List<Type> requiredTypeArguments = requiredType.asParameterizedType().arguments();
                List<Type> beanTypeArguments = beanType.asParameterizedType().arguments();
                if (requiredTypeArguments.size() != beanTypeArguments.size()) {
                    throw new IllegalArgumentException("Invalid argument combination " + requiredType + "; " + beanType);
                }
                for (int i = 0; i < requiredTypeArguments.size(); i++) {
                    if (!parametersMatch(requiredTypeArguments.get(i), beanTypeArguments.get(i))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    boolean parametersMatch(Type requiredParameter, Type beanParameter) {
        if (isActualType(requiredParameter) && isActualType(beanParameter)) {
            /*
             * the required type parameter and the bean type parameter are actual types with identical raw type, and, if the type is parameterized, the bean
             * type parameter is assignable to the required type parameter according to these rules, or
             */
            return matches(requiredParameter, beanParameter);
        }
        if (WILDCARD_TYPE.equals(requiredParameter.kind()) && isActualType(beanParameter)) {
            /*
             * the required type parameter is a wildcard, the bean type parameter is an actual type and the actual type is assignable to the upper bound, if
             * any, of the wildcard and assignable from the lower bound, if any, of the wildcard, or
             */
            return parametersMatch(requiredParameter.asWildcardType(), beanParameter);
        }
        if (WILDCARD_TYPE.equals(requiredParameter.kind()) && TYPE_VARIABLE.equals(beanParameter.kind())) {
            /*
             * the required type parameter is a wildcard, the bean type parameter is a type variable and the upper bound of the type variable is assignable to
             * or assignable from the upper bound, if any, of the wildcard and assignable from the lower bound, if any, of the wildcard, or
             */
            return parametersMatch(requiredParameter.asWildcardType(), beanParameter.asTypeVariable());
        }
        if (isActualType(requiredParameter) && TYPE_VARIABLE.equals(beanParameter.kind())) {
            /*
             * the required type parameter is an actual type, the bean type parameter is a type variable and the actual type is assignable to the upper bound,
             * if any, of the type variable, or
             */
            return parametersMatch(requiredParameter, beanParameter.asTypeVariable());
        }
        if (TYPE_VARIABLE.equals(requiredParameter.kind()) && TYPE_VARIABLE.equals(beanParameter.kind())) {
            /*
             * the required type parameter and the bean type parameter are both type variables and the upper bound of the required type parameter is assignable
             * to the upper bound, if any, of the bean type parameter
             */
            return parametersMatch(requiredParameter.asTypeVariable(), beanParameter.asTypeVariable());
        }
        return false;
    }

    boolean parametersMatch(WildcardType requiredParameter, Type beanParameter) {
        return (lowerBoundsOfWildcardMatch(beanParameter, requiredParameter) && upperBoundsOfWildcardMatch(requiredParameter, beanParameter));
    }

    boolean parametersMatch(WildcardType requiredParameter, TypeVariable beanParameter) {
        List<Type> beanParameterBounds = getUppermostTypeVariableBounds(beanParameter);
        if (!lowerBoundsOfWildcardMatch(beanParameterBounds, requiredParameter)) {
            return false;
        }

        List<Type> requiredUpperBounds = Collections.singletonList(requiredParameter.extendsBound());
        // upper bound of the type variable is assignable to OR assignable from the upper bound of the wildcard
        return (boundsMatch(requiredUpperBounds, beanParameterBounds) || boundsMatch(beanParameterBounds, requiredUpperBounds));
    }

    boolean parametersMatch(Type requiredParameter, TypeVariable beanParameter) {
        for (Type bound : getUppermostTypeVariableBounds(beanParameter)) {
            if (!isAssignableFrom(bound, requiredParameter)) {
                return false;
            }
        }
        return true;
    }

    boolean parametersMatch(TypeVariable requiredParameter, TypeVariable beanParameter) {
        return boundsMatch(getUppermostTypeVariableBounds(beanParameter), getUppermostTypeVariableBounds(requiredParameter));
    }

    /**
     * Returns <tt>true</tt> iff for each bound T, there is at least one bound from <tt>stricterBounds</tt> assignable to T. This reflects that
     * <tt>stricterBounds</tt> are at least as strict as <tt>bounds</tt> are.
     */
    boolean boundsMatch(List<Type> bounds, List<Type> stricterBounds) {
        // getUppermostBounds to make sure that both arrays of bounds contain ONLY ACTUAL TYPES! otherwise, the CovariantTypes
        // assignability rules do not reflect our needs
        bounds = getUppermostBounds(bounds);
        stricterBounds = getUppermostBounds(stricterBounds);
        for (Type bound : bounds) {
            for (Type stricterBound : stricterBounds) {
                if (!isAssignableFrom(bound, stricterBound)) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean isAssignableFrom(Type type1, Type type2) {
        return assignableFromMap.computeIfAbsent(type1.name(), assignableFromMapFunction).contains(type2.name());
    }

    boolean lowerBoundsOfWildcardMatch(Type parameter, WildcardType requiredParameter) {
        return lowerBoundsOfWildcardMatch(singletonList(parameter), requiredParameter);
    }

    boolean lowerBoundsOfWildcardMatch(List<Type> beanParameterBounds, WildcardType requiredParameter) {
        if (requiredParameter.superBound() != null) {
            if (!boundsMatch(beanParameterBounds, singletonList(requiredParameter.superBound()))) {
                return false;
            }
        }
        return true;
    }

    boolean upperBoundsOfWildcardMatch(WildcardType requiredParameter, Type parameter) {
        return boundsMatch(singletonList(requiredParameter.superBound()), singletonList(parameter));
    }

    /*
     * TypeVariable bounds are treated specially - CDI assignability rules are applied. Standard Java covariant assignability rules are applied to all other
     * types of bounds. This is not explicitly mentioned in the specification but is implied.
     */
    List<Type> getUppermostTypeVariableBounds(TypeVariable bound) {
        if (TYPE_VARIABLE.equals(bound.bounds().get(0).kind())) {
            return getUppermostTypeVariableBounds(bound.bounds().get(0).asTypeVariable());
        }
        return bound.bounds();
    }

    List<Type> getUppermostBounds(List<Type> bounds) {
        // if a type variable (or wildcard) declares a bound which is a type variable, it can declare no other bound
        if (TYPE_VARIABLE.equals(bounds.get(0).kind())) {
            return getUppermostTypeVariableBounds(bounds.get(0).asTypeVariable());
        }
        return bounds;
    }

    static boolean isActualType(Type type) {
        return CLASS.equals(type.kind()) || PARAMETERIZED_TYPE.equals(type.kind()) || ARRAY.equals(type.kind());
    }

    static boolean containsUnboundedTypeVariablesOrObjects(List<Type> types) {
        for (Type type : types) {
            if (ClassType.OBJECT_TYPE.equals(type)) {
                continue;
            }
            if (Kind.TYPE_VARIABLE.equals(type.kind())) {
                List<Type> bounds = type.asTypeVariable().bounds();
                if (bounds.isEmpty() || bounds.size() == 1 && ClassType.OBJECT_TYPE.equals(bounds.get(0))) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

}
