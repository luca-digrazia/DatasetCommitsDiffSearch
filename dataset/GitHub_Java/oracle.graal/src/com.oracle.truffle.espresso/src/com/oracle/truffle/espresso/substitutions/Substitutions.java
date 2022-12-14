package com.oracle.truffle.espresso.substitutions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicReflectionRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Substitutions/intrinsics for Espresso.
 *
 * Some substitutions are statically defined, others runtime-dependent. The static-ones are
 * initialized in the static initializer; which allows using MethodHandles instead of reflection in
 * SVM.
 */
public final class Substitutions implements ContextAccess {

    public static void init() {}

    private final EspressoContext context;

    @Override
    public EspressoContext getContext() {
        return context;
    }

    /**
     * We use a factory to create the substitution node once the target Method instance is known.
     */
    public interface EspressoRootNodeFactory {
        EspressoRootNode spawnNode(Method method);
    }

    private static final EconomicMap<MethodRef, EspressoRootNodeFactory> STATIC_SUBSTITUTIONS = EconomicMap.create();

    private final ConcurrentHashMap<MethodRef, EspressoRootNodeFactory> runtimeSubstitutions = new ConcurrentHashMap<>();

    private static final List<Class<?>> ESPRESSO_SUBSTITUTIONS = Collections.unmodifiableList(Arrays.asList(
                    Target_java_lang_Class.class,
                    Target_java_lang_ClassLoader.class,
                    Target_java_lang_Object.class,
                    Target_java_lang_Package.class,
                    Target_java_lang_Runtime.class,
                    Target_java_lang_System.class,
                    Target_java_lang_Thread.class,
                    Target_java_lang_reflect_Array.class,
                    Target_java_security_AccessController.class,
                    Target_sun_misc_Perf.class,
                    Target_sun_misc_Signal.class,
                    Target_sun_misc_Unsafe.class,
                    Target_sun_misc_URLClassPath.class,
                    Target_sun_misc_VM.class,
                    Target_sun_reflect_NativeMethodAccessorImpl.class));

    static {
        for (Class<?> clazz : ESPRESSO_SUBSTITUTIONS) {
            registerStaticSubstitutions(clazz);
        }
    }

    public Substitutions(EspressoContext context) {
        this.context = context;
    }

    private static MethodRef getMethodKey(Method method) {
        return new MethodRef(
                        method.getDeclaringKlass().getType(),
                        method.getName(),
                        method.getRawSignature());
    }

    private static final class MethodRef {
        private final Symbol<Type> clazz;
        private final Symbol<Name> methodName;
        private final Symbol<Signature> signature;
        private final int hash;

        public MethodRef(Symbol<Type> clazz, Symbol<Name> methodName, Symbol<Signature> signature) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.signature = signature;
            this.hash = Objects.hash(clazz, methodName, signature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MethodRef other = (MethodRef) obj;
            return Objects.equals(clazz, other.clazz) &&
                            Objects.equals(methodName, other.methodName) &&
                            Objects.equals(signature, other.signature);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "MethodKey<" + clazz + "." + methodName + " -> " + signature + ">";
        }
    }

    private static void registerStaticSubstitutions(Class<?> clazz) {
        int registered = 0;

        Symbol<Type> classType;
        Class<?> annotatedClass = clazz.getAnnotation(EspressoSubstitutions.class).value();
        if (annotatedClass == EspressoSubstitutions.class) {
            // Target class is derived from class name by simple substitution
            // e.g. Target_java_lang_System becomes java.lang.System
            assert clazz.getSimpleName().startsWith("Target_");
            classType = StaticSymbols.putType("L" + clazz.getSimpleName().substring("Target_".length()).replace('_', '/') + ";");
        } else {
            throw EspressoError.shouldNotReachHere("Substitutions class must be decorated with @" + EspressoSubstitutions.class.getName());
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            Substitution substitution = method.getAnnotation(Substitution.class);
            if (substitution == null) {
                continue;
            }

            final EspressoRootNodeFactory factory = new EspressoRootNodeFactory() {
                @Override
                public EspressoRootNode spawnNode(Method espressoMethod) {
                    return new IntrinsicReflectionRootNode(method, espressoMethod);
                }
            };

            java.lang.reflect.Parameter[] parameters = method.getParameters();

            List<Symbol<Type>> parameterTypes = new ArrayList<>();

            for (int i = substitution.hasReceiver() ? 1 : 0; i < parameters.length; i++) {
                java.lang.reflect.Parameter parameter = parameters[i];
                Symbol<Type> parameterType;
                Host annotatedType = parameter.getAnnotatedType().getAnnotation(Host.class);
                if (annotatedType != null) {
                    parameterType = StaticSymbols.putType(annotatedType.value());
                } else {
                    parameterType = StaticSymbols.putType(parameter.getType());
                }
                parameterTypes.add(parameterType);
            }

            Host annotatedReturnType = method.getAnnotatedReturnType().getAnnotation(Host.class);
            Symbol<Type> returnType;
            if (annotatedReturnType != null) {
                returnType = StaticSymbols.putType(annotatedReturnType.value());
            } else {
                returnType = StaticSymbols.putType(method.getReturnType());
            }

            String methodName = substitution.methodName();
            if (methodName.length() == 0) {
                methodName = method.getName();
            }

            ++registered;
            registerStaticSubstitution(classType,
                            StaticSymbols.putName(methodName),
                            StaticSymbols.putSignature(returnType, parameterTypes.toArray(Symbol.EMPTY_ARRAY)),
                            factory,
                            true);
        }
        assert registered > 0 : "No substitutions found in " + clazz;
    }

    private static void registerStaticSubstitution(Symbol<Type> type, Symbol<Name> methodName, Symbol<Signature> signature, EspressoRootNodeFactory factory, boolean throwIfPresent) {
        MethodRef key = new MethodRef(type, methodName, signature);
        if (throwIfPresent && STATIC_SUBSTITUTIONS.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered" + key);
        }
        STATIC_SUBSTITUTIONS.put(key, factory);
    }

    public void registerRuntimeSubstitution(Symbol<Type> type, Symbol<Name> methodName, Symbol<Signature> signature, EspressoRootNodeFactory factory, boolean throwIfPresent) {
        MethodRef key = new MethodRef(type, methodName, signature);

        EspressoError.warnIf(STATIC_SUBSTITUTIONS.containsKey(key), "Runtime substitution shadowed by static one " + key);

        if (throwIfPresent && runtimeSubstitutions.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered" + key);
        }
        runtimeSubstitutions.put(key, factory);
    }

    public EspressoRootNode get(Method method) {
        MethodRef key = getMethodKey(method);
        EspressoRootNodeFactory factory = STATIC_SUBSTITUTIONS.get(key);
        if (factory == null) {
            factory = runtimeSubstitutions.get(key);
        }
        if (factory == null) {
            return null;
        }
        return factory.spawnNode(method);
    }
}
