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

package io.quarkus.deployment.recording;

import static io.quarkus.gizmo.MethodDescriptor.ofConstructor;
import static io.quarkus.gizmo.MethodDescriptor.ofMethod;

import java.beans.PropertyDescriptor;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.invocation.proxy.ProxyConfiguration;
import org.jboss.invocation.proxy.ProxyFactory;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.wildfly.common.Assert;

import io.quarkus.deployment.ClassOutput;
import io.quarkus.deployment.recording.AnnotationProxyProvider.AnnotationProxy;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;
import io.quarkus.runtime.ObjectSubstitution;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.StartupContext;
import io.quarkus.runtime.StartupTask;

/**
 * A class that can be used to record invocations to bytecode so they can be replayed later. This is done through the
 * use of class templates and recording proxies.
 * <p>
 * A class template is simple a stateless class with a no arg constructor. This template will contain the runtime logic
 * used to bootstrap the various frameworks.
 * <p>
 * A recording proxy is a proxy of a template that records all invocations on the template, and then writes out a sequence
 * of java bytecode that performs the same invocations.
 * <p>
 * There are some limitations on what can be recorded. Only the following objects are allowed as parameters to
 * recording proxies:
 * <p>
 * - primitives
 * - String
 * - Class (see {@link #classProxy(String)} to handle classes that are not loadable at generation time)
 * - Objects with a no-arg constructor and getter/setters for all properties
 * - Any arbitrary object via the {@link #registerSubstitution(Class, Class, Class)} mechanism
 * - arrays, lists and maps of the above
 */
public class BytecodeRecorderImpl implements RecorderContext {

    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final AtomicInteger OUTPUT_COUNT = new AtomicInteger();
    private static final String BASE_PACKAGE = "io.quarkus.deployment.steps.";

    private static final String PROXY_KEY = "proxykey";

    private static final MethodDescriptor COLLECTION_ADD = ofMethod(Collection.class, "add", boolean.class, Object.class);
    private static final MethodDescriptor MAP_PUT = ofMethod(Map.class, "put", Object.class, Object.class, Object.class);

    private final boolean staticInit;
    private final ClassLoader classLoader;
    private final MethodRecorder methodRecorder = new MethodRecorder();

    private final Map<Class, ProxyFactory<?>> returnValueProxy = new HashMap<>();
    private final IdentityHashMap<Class<?>, String> classProxies = new IdentityHashMap<>();
    private final Map<Class<?>, SubstitutionHolder> substitutions = new HashMap<>();
    private final Map<Class<?>, NonDefaultConstructorHolder> nonDefaultConstructors = new HashMap<>();
    private final String className;

    private final List<ObjectLoader> loaders = new ArrayList<>();

    private int deferredParameterCount = 0;
    private boolean loadComplete;
    private PropertyUtils introspection = new PropertyUtils();

    public BytecodeRecorderImpl(ClassLoader classLoader, boolean staticInit, String className) {
        this.classLoader = classLoader;
        this.staticInit = staticInit;
        this.className = className;
    }

    public BytecodeRecorderImpl(boolean staticInit, String buildStepName, String methodName) {
        this(Thread.currentThread().getContextClassLoader(), staticInit,
                BASE_PACKAGE + buildStepName + "$" + methodName + OUTPUT_COUNT.incrementAndGet());
    }

    public boolean isEmpty() {
        return methodRecorder.storedMethodCalls.isEmpty();
    }

    @Override
    public <F, T> void registerSubstitution(Class<F> from, Class<T> to,
            Class<? extends ObjectSubstitution<F, T>> substitution) {
        substitutions.put(from, new SubstitutionHolder(from, to, substitution));
    }

    @Override
    public <T> void registerNonDefaultConstructor(Constructor<T> constructor, Function<T, List<Object>> parameters) {
        nonDefaultConstructors.put(constructor.getDeclaringClass(),
                new NonDefaultConstructorHolder(constructor, (Function<Object, List<Object>>) parameters));
    }

    @Override
    public void registerObjectLoader(ObjectLoader loader) {
        Assert.checkNotNullParam("loader", loader);
        loaders.add(loader);
    }

    public <T> T getRecordingProxy(Class<T> theClass) {
        return methodRecorder.getRecordingProxy(theClass);
    }

    @Override
    public Class<?> classProxy(String name) {
        ProxyFactory<Object> factory = new ProxyFactory<>(new ProxyConfiguration<Object>()
                .setSuperClass(Object.class)
                .setClassLoader(classLoader)
                .setProxyName(getClass().getName() + "$$ClassProxy" + COUNT.incrementAndGet()));
        Class theClass = factory.defineClass();
        classProxies.put(theClass, name);
        return theClass;
    }

    @Override
    public <T> RuntimeValue<T> newInstance(String name) {
        try {
            ProxyInstance ret = methodRecorder.getProxyInstance(RuntimeValue.class);
            NewInstance instance = new NewInstance(name, ret.proxy, ret.key);
            methodRecorder.storedMethodCalls.add(instance);
            return (RuntimeValue<T>) ret.proxy;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class MethodRecorder {

        private final Map<Class<?>, Object> existingProxyClasses = new HashMap<>();
        private final List<BytecodeInstruction> storedMethodCalls = new ArrayList<>();

        public <T> T getRecordingProxy(Class<T> theClass) {
            if (existingProxyClasses.containsKey(theClass)) {
                return theClass.cast(existingProxyClasses.get(theClass));
            }
            String proxyName = getClass().getName() + "$$RecordingProxyProxy" + COUNT.incrementAndGet();
            ProxyFactory<T> factory = new ProxyFactory<T>(new ProxyConfiguration<T>()
                    .setSuperClass(theClass)
                    .setClassLoader(classLoader)
                    .setProxyName(proxyName));
            try {
                T recordingProxy = factory.newInstance(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        StoredMethodCall storedMethodCall = new StoredMethodCall(theClass, method, args);
                        storedMethodCalls.add(storedMethodCall);
                        Class<?> returnType = method.getReturnType();
                        if (method.getName().equals("toString")
                                && method.getParameterTypes().length == 0
                                && returnType.equals(String.class)) {
                            return proxyName;
                        }
                        if (returnType.isPrimitive()) {
                            return 0;
                        }
                        if (Modifier.isFinal(returnType.getModifiers())) {
                            return null;
                        }
                        ProxyInstance instance = getProxyInstance(returnType);
                        if (instance == null)
                            return null;

                        storedMethodCall.returnedProxy = instance.proxy;
                        storedMethodCall.proxyId = instance.key;
                        return instance.proxy;
                    }

                });
                existingProxyClasses.put(theClass, recordingProxy);
                return recordingProxy;
            } catch (IllegalAccessException | InstantiationException e) {
                throw new RuntimeException(e);
            }
        }

        private ProxyInstance getProxyInstance(Class<?> returnType) throws InstantiationException, IllegalAccessException {
            boolean returnInterface = returnType.isInterface();
            if (!returnInterface) {
                try {
                    returnType.getConstructor();
                } catch (NoSuchMethodException e) {
                    return null;
                }
            }
            ProxyFactory<?> proxyFactory = returnValueProxy.get(returnType);
            if (proxyFactory == null) {
                ProxyConfiguration<Object> proxyConfiguration = new ProxyConfiguration<Object>()
                        .setSuperClass(returnInterface ? Object.class : (Class) returnType)
                        .setClassLoader(classLoader)
                        .addAdditionalInterface(ReturnedProxy.class)
                        .setProxyName(getClass().getName() + "$$ReturnValueProxy" + COUNT.incrementAndGet());

                if (returnInterface) {
                    proxyConfiguration.addAdditionalInterface(returnType);
                }
                returnValueProxy.put(returnType, proxyFactory = new ProxyFactory<>(proxyConfiguration));
            }

            String key = PROXY_KEY + COUNT.incrementAndGet();
            Object proxyInstance = proxyFactory.newInstance(new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("__returned$proxy$key")) {
                        return key;
                    }
                    if (method.getName().equals("__static$$init")) {
                        return staticInit;
                    }
                    if (method.getName().equals("toString")
                            && method.getParameterTypes().length == 0
                            && method.getReturnType().equals(String.class)) {
                        return "Runtime proxy of " + returnType + " with id " + key;
                    }
                    throw new RuntimeException(
                            "You cannot invoke " + method.getName()
                                    + "() directly on an object returned from the bytecode recorder, you can only pass it back into the recorder as a parameter");
                }
            });
            ProxyInstance instance = new ProxyInstance(proxyInstance, key);
            return instance;
        }
    }

    public String getClassName() {
        return className;
    }

    public void writeBytecode(ClassOutput classOutput) {
        try {
            ClassCreator file = ClassCreator.builder().classOutput(ClassOutput.gizmoAdaptor(classOutput, true)).className(className)
                    .superClass(Object.class).interfaces(StartupTask.class).build();
            MethodCreator method = file.getMethodCreator("deploy", void.class, StartupContext.class);
            //now create instances of all the classes we invoke on and store them in variables as well
            Map<Class, DeferredArrayStoreParameter> classInstanceVariables = new HashMap<>();

            Map<Object, DeferredParameter> parameterMap = new IdentityHashMap<>();
            List<DeferredParameter> deferredParameters = new ArrayList<>();

            //before we actually do any invocations we fully read all deserialized objects into an array
            //this allows us to break the loading process into multiple methods, to avoid hitting the bytecode
            //size limit as part of the deserialization process. We can't use ResultHandler for this
            //as it is scoped to a method and the complexity of trying to track them over multiple methods is too much

            //the use of this array means that each value can be simple represented as an index, and the array can
            //be easily passed around all the smaller methods that we break the code up into to avoid the bytecode size
            //limit

            //note that this does not nessesarily give the most efficent bytecode, however it does mean

            //the first step is to create list of all these values that need to be placed into the array. We do that
            //here, as well as creating the template instances (and also preparing them to be stored in the array)

            for (BytecodeInstruction set : this.methodRecorder.storedMethodCalls) {
                if (set instanceof StoredMethodCall) {
                    StoredMethodCall call = (StoredMethodCall) set;
                    if (!classInstanceVariables.containsKey(call.theClass)) {
                        DeferredArrayStoreParameter value = new DeferredArrayStoreParameter() {
                            @Override
                            ResultHandle createValue(MethodCreator method, ResultHandle array) {
                                return method.newInstance(ofConstructor(call.theClass));
                            }
                        };
                        deferredParameters.add(value);
                        classInstanceVariables.put(call.theClass, value);
                    }
                    for (int i = 0; i < call.parameters.length; ++i) {
                        call.deferredParameters[i] = loadObjectInstance(call.parameters[i], parameterMap,
                                call.method.getParameterTypes()[i], deferredParameters);
                    }
                }
            }

            loadComplete = true;
            //now we have a full parameter list, lets create an array, and load all our parameters into it. They should already
            //be in the correct order
            ResultHandle array = method.newArray(Object.class, method.load(deferredParameterCount));

            //now we invoke the actual method call
            for (BytecodeInstruction set : methodRecorder.storedMethodCalls) {
                if (set instanceof StoredMethodCall) {
                    StoredMethodCall call = (StoredMethodCall) set;
                    ResultHandle[] params = new ResultHandle[call.parameters.length];

                    for (int i = 0; i < call.parameters.length; ++i) {
                        params[i] = call.deferredParameters[i].load(method, array);
                    }
                    //todo we should not need an array load every time, cache this per method in ResultHandler
                    ResultHandle callResult = method.invokeVirtualMethod(ofMethod(call.method.getDeclaringClass(),
                            call.method.getName(), call.method.getReturnType(), call.method.getParameterTypes()),
                            classInstanceVariables.get(call.theClass).load(method, array), params);

                    if (call.method.getReturnType() != void.class) {
                        if (call.returnedProxy != null) {
                            //TODO: local method result handle caching
                            //returnValueResults.put(call.returnedProxy, callResult);
                            method.invokeVirtualMethod(
                                    ofMethod(StartupContext.class, "putValue", void.class, String.class, Object.class),
                                    method.getMethodParam(0), method.load(call.proxyId), callResult);
                        }
                    }
                } else if (set instanceof NewInstance) {
                    NewInstance ni = (NewInstance) set;
                    ResultHandle val = method.newInstance(ofConstructor(ni.theClass));
                    ResultHandle rv = method.newInstance(ofConstructor(RuntimeValue.class, Object.class), val);
                    method.invokeVirtualMethod(ofMethod(StartupContext.class, "putValue", void.class, String.class, Object.class),
                            method.getMethodParam(0), method.load(ni.proxyId), rv);
                } else {
                    throw new RuntimeException("unknown type " + set);
                }
            }

            method.returnValue(null);
            file.close();
        } finally {
            introspection.close();
        }
    }

    private DeferredParameter loadObjectInstance(Object param, Map<Object, DeferredParameter> existing,
            Class<?> expectedType, List<DeferredParameter> parameterList) {
        if (loadComplete) {
            throw new RuntimeException("All parameters have already been loaded, it is too late to call loadObjectInstance");
        }
        if (existing.containsKey(param)) {
            return existing.get(param);
        }
        DeferredParameter ret = loadObjectInstanceImpl(param, existing, expectedType, parameterList);
        existing.put(param, ret);
        parameterList.add(ret);
        return ret;
    }

    private DeferredParameter loadObjectInstanceImpl(Object param, Map<Object, DeferredParameter> existing,
            Class<?> expectedType, List<DeferredParameter> parameterList) {

        if (param == null) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator creator, ResultHandle array) {
                    return creator.loadNull();
                }
            };
        }
        DeferredParameter loadedObject = findLoaded(param);
        if (loadedObject != null) {
            return loadedObject;
        }

        if (substitutions.containsKey(param.getClass()) || substitutions.containsKey(expectedType)) {
            SubstitutionHolder holder = substitutions.get(param.getClass());
            if (holder == null) {
                holder = substitutions.get(expectedType);
            }
            try {
                ObjectSubstitution substitution = holder.sub.newInstance();
                Object res = substitution.serialize(param);
                DeferredParameter serialized = loadObjectInstance(res, existing, holder.to, parameterList);
                SubstitutionHolder finalHolder = holder;
                return new DeferredArrayStoreParameter() {
                    @Override
                    ResultHandle createValue(MethodCreator creator, ResultHandle array) {
                        ResultHandle subInstance = creator.newInstance(MethodDescriptor.ofConstructor(finalHolder.sub));
                        return creator.invokeInterfaceMethod(
                                ofMethod(ObjectSubstitution.class, "deserialize", Object.class, Object.class), subInstance,
                                serialized.load(creator, array));
                    }
                };

            } catch (Exception e) {
                throw new RuntimeException("Failed to substitute " + param, e);
            }

        } else if (param instanceof Optional) {
            Optional val = (Optional) param;
            if (val.isPresent()) {
                DeferredParameter res = loadObjectInstance(val.get(), existing, Object.class, parameterList);
                return new DeferredArrayStoreParameter() {
                    @Override
                    ResultHandle createValue(MethodCreator method, ResultHandle array) {
                        return method.invokeStaticMethod(ofMethod(Optional.class, "of", Optional.class, Object.class),
                                res.load(method, array));
                    }
                };
            } else {
                return new DeferredArrayStoreParameter() {
                    @Override
                    ResultHandle createValue(MethodCreator method, ResultHandle array) {
                        return method.invokeStaticMethod(ofMethod(Optional.class, "empty", Optional.class));
                    }
                };
            }
        } else if (param instanceof String) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((String) param);
                }
            };
        } else if (param instanceof Integer) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Integer.class, "valueOf", Integer.class, int.class),
                            method.load((Integer) param));
                }
            };
        } else if (param instanceof Boolean) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Boolean.class, "valueOf", Boolean.class, boolean.class),
                            method.load((Boolean) param));
                }
            };
        } else if (param instanceof URL) {
            String url = ((URL) param).toExternalForm();
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    AssignableResultHandle value = method.createVariable(URL.class);
                    try (TryBlock et = method.tryBlock()) {
                        et.assign(value, et.newInstance(MethodDescriptor.ofConstructor(URL.class, String.class), et.load(url)));
                        try (CatchBlockCreator malformed = et.addCatch(MalformedURLException.class)) {
                            malformed.throwException(RuntimeException.class, "Malformed URL", malformed.getCaughtException());
                        }
                    }
                    return value;
                }
            };
        } else if (param instanceof Enum) {
            Enum e = (Enum) param;
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    ResultHandle nm = method.load(e.name());
                    return method.invokeStaticMethod(
                            ofMethod(e.getDeclaringClass(), "valueOf", e.getDeclaringClass(), String.class),
                            nm);
                }
            };
        } else if (param instanceof ReturnedProxy) {
            ReturnedProxy rp = (ReturnedProxy) param;
            if (!rp.__static$$init() && staticInit) {
                throw new RuntimeException("Invalid proxy passed to template. " + rp
                        + " was created in a runtime recorder method, while this recorder is for a static init method. The object will not have been created at the time this method is run.");
            }
            String proxyId = rp.__returned$proxy$key();
            //because this is the result of a method invocation that may not have happened at param deserialization time
            //we just load it from the startup context
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeVirtualMethod(ofMethod(StartupContext.class, "getValue", Object.class, String.class),
                            method.getMethodParam(0), method.load(proxyId));
                }
            };
        } else if (param instanceof Class<?>) {
            if (!((Class) param).isPrimitive()) {
                // Only try to load the class by name if it is not a primitive class
                String name = classProxies.get(param);
                if (name == null) {
                    name = ((Class) param).getName();
                }
                String finalName = name;
                return new DeferredParameter() {
                    @Override
                    ResultHandle load(MethodCreator method, ResultHandle array) {

                        ResultHandle currentThread = method
                                .invokeStaticMethod(ofMethod(Thread.class, "currentThread", Thread.class));
                        ResultHandle tccl = method.invokeVirtualMethod(
                                ofMethod(Thread.class, "getContextClassLoader", ClassLoader.class),
                                currentThread);
                        return method.invokeStaticMethod(
                                ofMethod(Class.class, "forName", Class.class, String.class, boolean.class, ClassLoader.class),
                                method.load(finalName), method.load(true), tccl);
                    }
                };
            } else {
                // Else load the primitive type by reference; double.class => Class var9 = Double.TYPE;
                return new DeferredParameter() {
                    @Override
                    ResultHandle load(MethodCreator method, ResultHandle array) {
                        return method.loadClass((Class) param);
                    }
                };
            }
        } else if (expectedType == boolean.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((boolean) param);
                }
            };
        } else if (expectedType == Boolean.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Boolean.class, "valueOf", Boolean.class, boolean.class),
                            method.load((boolean) param));
                }
            };
        } else if (expectedType == int.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((int) param);
                }
            };
        } else if (expectedType == Integer.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Integer.class, "valueOf", Integer.class, int.class),
                            method.load((int) param));
                }
            };
        } else if (expectedType == short.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((short) param);
                }
            };
        } else if (expectedType == Short.class || param instanceof Short) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Short.class, "valueOf", Short.class, short.class),
                            method.load((short) param));
                }
            };
        } else if (expectedType == byte.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((byte) param);
                }
            };
        } else if (expectedType == Byte.class || param instanceof Byte) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Byte.class, "valueOf", Byte.class, byte.class),
                            method.load((byte) param));
                }
            };
        } else if (expectedType == char.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((char) param);
                }
            };
        } else if (expectedType == Character.class || param instanceof Character) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Character.class, "valueOf", Character.class, char.class),
                            method.load((char) param));
                }
            };
        } else if (expectedType == long.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((long) param);
                }
            };
        } else if (expectedType == Long.class || param instanceof Long) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Long.class, "valueOf", Long.class, long.class),
                            method.load((long) param));
                }
            };
        } else if (expectedType == float.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((float) param);
                }
            };
        } else if (expectedType == Float.class || param instanceof Float) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Float.class, "valueOf", Float.class, float.class),
                            method.load((float) param));
                }
            };
        } else if (expectedType == double.class) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.load((double) param);
                }
            };
        } else if (expectedType == Double.class || param instanceof Double) {
            return new DeferredParameter() {
                @Override
                ResultHandle load(MethodCreator method, ResultHandle array) {
                    return method.invokeStaticMethod(ofMethod(Double.class, "valueOf", Double.class, double.class),
                            method.load((double) param));
                }
            };
        } else if (expectedType.isArray()) {
            int length = Array.getLength(param);
            DeferredParameter[] components = new DeferredParameter[length];

            for (int i = 0; i < length; ++i) {
                DeferredParameter component = loadObjectInstance(Array.get(param, i), existing,
                        expectedType.getComponentType(), parameterList);
                components[i] = component;
            }
            return new DeferredArrayStoreParameter() {
                @Override
                ResultHandle createValue(MethodCreator method, ResultHandle array) {
                    ResultHandle out = method.newArray(expectedType.getComponentType(), method.load(length));
                    for (int i = 0; i < length; ++i) {
                        method.writeArrayValue(out, i, components[i].load(method, array));
                    }
                    return out;
                }
            };
        } else if (AnnotationProxy.class.isAssignableFrom(expectedType)) {
            // new com.foo.MyAnnotation_Proxy_AnnotationLiteral("foo")
            AnnotationProxy annotationProxy = (AnnotationProxy) param;
            List<MethodInfo> constructorParams = annotationProxy.getAnnotationClass().methods().stream()
                    .filter(m -> !m.name().equals("<clinit>") && !m.name().equals("<init>"))
                    .collect(Collectors.toList());
            Map<String, AnnotationValue> annotationValues = annotationProxy.getAnnotationInstance().values().stream()
                    .collect(Collectors.toMap(AnnotationValue::name, Function.identity()));
            DeferredParameter[] constructorParamsHandles = new DeferredParameter[constructorParams.size()];

            for (ListIterator<MethodInfo> iterator = constructorParams.listIterator(); iterator.hasNext();) {
                MethodInfo valueMethod = iterator.next();
                AnnotationValue value = annotationValues.get(valueMethod.name());
                if (value == null) {
                    // method.invokeInterfaceMethod(MAP_PUT, valuesHandle, method.load(entry.getKey()), loadObjectInstance(method, entry.getValue(), returnValueResults, entry.getValue().getClass()));
                    Object defaultValue = annotationProxy.getDefaultValues().get(valueMethod.name());
                    if (defaultValue != null) {
                        constructorParamsHandles[iterator.previousIndex()] = loadObjectInstance(defaultValue,
                                existing, defaultValue.getClass(), parameterList);
                        continue;
                    }
                    if (value == null) {
                        value = valueMethod.defaultValue();
                    }
                }
                if (value == null) {
                    throw new NullPointerException("Value not set for " + param);
                }
                DeferredParameter retValue = loadValue(value, annotationProxy.getAnnotationClass(), valueMethod);
                constructorParamsHandles[iterator.previousIndex()] = retValue;
            }
            return new DeferredArrayStoreParameter() {
                @Override
                ResultHandle createValue(MethodCreator method, ResultHandle array) {
                    return method
                            .newInstance(MethodDescriptor.ofConstructor(annotationProxy.getAnnotationLiteralType(),
                                    constructorParams.stream().map(m -> m.returnType().name().toString()).toArray()),
                                    Arrays.stream(constructorParamsHandles).map(m -> m.load(method, array))
                                            .toArray(ResultHandle[]::new));
                }
            };

        } else {
            //a list of steps that are performed on the object after it has been created
            //we need to create all these first, to ensure the required objects have already
            //been deserialized
            List<SerialzationStep> setupSteps = new ArrayList<>();

            if (param instanceof Collection) {
                for (Object i : (Collection) param) {
                    DeferredParameter val = loadObjectInstance(i, existing, i.getClass(), parameterList);
                    setupSteps.add(new SerialzationStep() {
                        @Override
                        public void handle(MethodCreator method, ResultHandle array, ResultHandle out) {
                            method.invokeInterfaceMethod(COLLECTION_ADD, out, val.load(method, array));
                        }
                    });
                }
            }
            if (param instanceof Map) {
                for (Map.Entry<?, ?> i : ((Map<?, ?>) param).entrySet()) {
                    DeferredParameter key = loadObjectInstance(i.getKey(), existing, i.getKey().getClass(), parameterList);
                    DeferredParameter val = i.getValue() != null
                            ? loadObjectInstance(i.getValue(), existing, i.getValue().getClass(), parameterList)
                            : null;
                    setupSteps.add(new SerialzationStep() {
                        @Override
                        public void handle(MethodCreator method, ResultHandle array, ResultHandle out) {
                            method.invokeInterfaceMethod(MAP_PUT, out, key.load(method, array), val.load(method, array));
                        }
                    });
                }
            }
            Set<String> handledProperties = new HashSet<>();
            PropertyDescriptor[] desc = introspection.getPropertyDescriptors(param);
            for (PropertyDescriptor i : desc) {
                if (i.getReadMethod() != null && i.getWriteMethod() == null) {
                    try {
                        //read only prop, we may still be able to do stuff with it if it is a collection
                        if (Collection.class.isAssignableFrom(i.getPropertyType())) {
                            //special case, a collection with only a read method
                            //we assume we can just add to the connection
                            handledProperties.add(i.getName());

                            Collection propertyValue = (Collection) introspection.getProperty(param, i.getName());
                            if (!propertyValue.isEmpty()) {

                                List<DeferredParameter> params = new ArrayList<>();

                                for (Object c : propertyValue) {
                                    DeferredParameter toAdd = loadObjectInstance(c, existing, Object.class, parameterList);
                                    params.add(toAdd);

                                }
                                setupSteps.add(new SerialzationStep() {
                                    @Override
                                    public void handle(MethodCreator method, ResultHandle array, ResultHandle out) {
                                        ResultHandle prop = method.invokeVirtualMethod(
                                                MethodDescriptor.ofMethod(i.getReadMethod()),
                                                out);
                                        for (DeferredParameter i : params) {
                                            method.invokeInterfaceMethod(COLLECTION_ADD, prop, i.load(method, array));
                                        }
                                    }
                                });
                            }

                        } else if (Map.class.isAssignableFrom(i.getPropertyType())) {
                            //special case, a map with only a read method
                            //we assume we can just add to the map

                            handledProperties.add(i.getName());
                            Map<Object, Object> propertyValue = (Map<Object, Object>) introspection.getProperty(param,
                                    i.getName());
                            if (!propertyValue.isEmpty()) {
                                Map<DeferredParameter, DeferredParameter> def = new LinkedHashMap<>();
                                for (Map.Entry<Object, Object> entry : propertyValue.entrySet()) {
                                    DeferredParameter key = loadObjectInstance(entry.getKey(), existing,
                                            Object.class, parameterList);
                                    DeferredParameter val = loadObjectInstance(entry.getValue(), existing, Object.class,
                                            parameterList);
                                    def.put(key, val);
                                }
                                setupSteps.add(new SerialzationStep() {
                                    @Override
                                    public void handle(MethodCreator method, ResultHandle array, ResultHandle out) {

                                        ResultHandle prop = method.invokeVirtualMethod(
                                                MethodDescriptor.ofMethod(i.getReadMethod()),
                                                out);
                                        for (Map.Entry<DeferredParameter, DeferredParameter> e : def.entrySet()) {
                                            method.invokeInterfaceMethod(MAP_PUT, prop, e.getKey().load(method, array),
                                                    e.getValue().load(method, array));
                                        }
                                    }
                                });
                            }
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (i.getReadMethod() != null && i.getWriteMethod() != null) {
                    try {
                        handledProperties.add(i.getName());
                        Object propertyValue = introspection.getProperty(param, i.getName());
                        if (propertyValue == null) {
                            //we just assume properties are null by default
                            //TODO: is this a valid assumption? Should we check this by creating an instance?
                            continue;
                        }
                        Class propertyType = i.getPropertyType();
                        if (i.getReadMethod().getReturnType() != i.getWriteMethod().getParameterTypes()[0]) {
                            //this is a weird situation where the reader and writer are different types
                            //we iterate and try and find a valid setter method for the type we have
                            //OpenAPI does some weird stuff like this

                            for (Method m : param.getClass().getMethods()) {
                                if (m.getName().equals(i.getWriteMethod().getName())) {
                                    if (m.getParameterTypes().length > 0
                                            && m.getParameterTypes()[0].isAssignableFrom(param.getClass())) {
                                        propertyType = m.getParameterTypes()[0];
                                        break;
                                    }
                                }
                            }
                        }
                        DeferredParameter val = loadObjectInstance(propertyValue, existing,
                                i.getPropertyType(), parameterList);
                        Class finalPropertyType = propertyType;
                        setupSteps.add(new SerialzationStep() {
                            @Override
                            public void handle(MethodCreator method, ResultHandle array, ResultHandle out) {
                                method.invokeVirtualMethod(
                                        ofMethod(param.getClass(), i.getWriteMethod().getName(), void.class,
                                                finalPropertyType),
                                        out,
                                        val.load(method, array));
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            //now handle accessible fields
            for (Field field : param.getClass().getFields()) {
                if (!Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())
                        && !handledProperties.contains(field.getName())) {

                    try {
                        DeferredParameter val = loadObjectInstance(field.get(param), existing, field.getType(), parameterList);
                        setupSteps.add(new SerialzationStep() {
                            @Override
                            public void handle(MethodCreator method, ResultHandle array, ResultHandle out) {
                                method.writeInstanceField(
                                        FieldDescriptor.of(param.getClass(), field.getName(), field.getType()), out,
                                        val.load(method, array));
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            NonDefaultConstructorHolder nonDefaultConstructorHolder = null;
            List<DeferredParameter> nonDefaultConstructorHandles = new ArrayList<>();
            if (nonDefaultConstructors.containsKey(param.getClass())) {
                nonDefaultConstructorHolder = nonDefaultConstructors.get(param.getClass());
                List<Object> params = nonDefaultConstructorHolder.paramGenerator.apply(param);
                if (params.size() != nonDefaultConstructorHolder.constructor.getParameterCount()) {
                    throw new RuntimeException("Unable to serialize " + param
                            + " as the wrong number of parameters were generated for "
                            + nonDefaultConstructorHolder.constructor);
                }
                int count = 0;
                for (Object i : params) {
                    nonDefaultConstructorHandles.add(
                            loadObjectInstance(i, existing,
                                    nonDefaultConstructorHolder.constructor.getParameterTypes()[count++],
                                    parameterList));
                }
            }

            NonDefaultConstructorHolder finalNonDefaultConstructorHolder = nonDefaultConstructorHolder;
            return new DeferredArrayStoreParameter() {
                @Override
                ResultHandle createValue(MethodCreator method, ResultHandle array) {
                    ResultHandle out;
                    if (finalNonDefaultConstructorHolder != null) {

                        out = method.newInstance(
                                ofConstructor(finalNonDefaultConstructorHolder.constructor.getDeclaringClass(),
                                        finalNonDefaultConstructorHolder.constructor.getParameterTypes()),
                                nonDefaultConstructorHandles.stream().map(m -> m.load(method, array))
                                        .toArray(ResultHandle[]::new));
                    } else {
                        try {
                            param.getClass().getDeclaredConstructor();
                            out = method.newInstance(ofConstructor(param.getClass()));
                        } catch (NoSuchMethodException e) {
                            //fallback for collection types, such as unmodifiableMap
                            if (expectedType == Map.class) {
                                out = method.newInstance(ofConstructor(LinkedHashMap.class));
                            } else if (expectedType == List.class) {
                                out = method.newInstance(ofConstructor(ArrayList.class));
                            } else if (expectedType == Set.class) {
                                out = method.newInstance(ofConstructor(Set.class));
                            } else {
                                throw new RuntimeException("Unable to serialize objects of type " + param.getClass()
                                        + " to bytecode as it has no default constructor");
                            }
                        }
                    }
                    for (SerialzationStep i : setupSteps) {
                        i.handle(method, array, out);
                    }
                    return out;
                }
            };
        }
    }

    private DeferredParameter findLoaded(final Object param) {
        for (ObjectLoader loader : loaders) {
            if (loader.canHandleObject(param, staticInit)) {
                return new DeferredArrayStoreParameter() {
                    @Override
                    ResultHandle createValue(MethodCreator creator, ResultHandle array) {
                        return loader.load(creator, param, staticInit);
                    }
                };
            }
        }
        return null;
    }

    interface BytecodeInstruction {

    }

    public interface ReturnedProxy {
        String __returned$proxy$key();

        boolean __static$$init();
    }

    static final class StoredMethodCall implements BytecodeInstruction {
        final Class<?> theClass;
        final Method method;
        final Object[] parameters;
        final DeferredParameter[] deferredParameters;
        Object returnedProxy;
        String proxyId;

        StoredMethodCall(Class<?> theClass, Method method, Object[] parameters) {
            this.theClass = theClass;
            this.method = method;
            this.parameters = parameters;
            this.deferredParameters = new DeferredParameter[parameters.length];
        }
    }

    static final class NewInstance implements BytecodeInstruction {
        final String theClass;
        final Object returnedProxy;
        final String proxyId;

        NewInstance(String theClass, Object returnedProxy, String proxyId) {
            this.theClass = theClass;
            this.returnedProxy = returnedProxy;
            this.proxyId = proxyId;
        }
    }

    static final class SubstitutionHolder {
        final Class<?> from;
        final Class<?> to;
        final Class<? extends ObjectSubstitution<?, ?>> sub;

        SubstitutionHolder(Class<?> from, Class<?> to, Class<? extends ObjectSubstitution<?, ?>> sub) {
            this.from = from;
            this.to = to;
            this.sub = sub;
        }
    }

    static final class NonDefaultConstructorHolder {
        final Constructor<?> constructor;
        final Function<Object, List<Object>> paramGenerator;

        NonDefaultConstructorHolder(Constructor<?> constructor, Function<Object, List<Object>> paramGenerator) {
            this.constructor = constructor;
            this.paramGenerator = paramGenerator;
        }
    }

    private static final class ProxyInstance {
        final Object proxy;
        final String key;

        ProxyInstance(Object proxy, String key) {
            this.proxy = proxy;
            this.key = key;
        }

    }

    DeferredParameter loadValue(AnnotationValue value, ClassInfo annotationClass,
            MethodInfo method) {
        //note that this is a special case, in general DeferredParameter should be added to the main parameter list
        //however in this case we know it is a constant
        return new DeferredParameter() {

            @Override
            ResultHandle load(MethodCreator valueMethod, ResultHandle array) {

                ResultHandle retValue;
                switch (value.kind()) {
                    case BOOLEAN:
                        retValue = valueMethod.load(value.asBoolean());
                        break;
                    case STRING:
                        retValue = valueMethod.load(value.asString());
                        break;
                    case BYTE:
                        retValue = valueMethod.load(value.asByte());
                        break;
                    case SHORT:
                        retValue = valueMethod.load(value.asShort());
                        break;
                    case LONG:
                        retValue = valueMethod.load(value.asLong());
                        break;
                    case INTEGER:
                        retValue = valueMethod.load(value.asInt());
                        break;
                    case FLOAT:
                        retValue = valueMethod.load(value.asFloat());
                        break;
                    case DOUBLE:
                        retValue = valueMethod.load(value.asDouble());
                        break;
                    case CHARACTER:
                        retValue = valueMethod.load(value.asChar());
                        break;
                    case CLASS:
                        retValue = valueMethod.loadClass(value.asClass().toString());
                        break;
                    case ARRAY:
                        retValue = arrayValue(value, valueMethod, method, annotationClass);
                        break;
                    case ENUM:
                        retValue = valueMethod
                                .readStaticField(FieldDescriptor.of(value.asEnumType().toString(), value.asEnum(),
                                        value.asEnumType().toString()));
                        break;
                    case NESTED:
                    default:
                        throw new UnsupportedOperationException("Unsupported value: " + value);
                }
                return retValue;
            }
        };
    }

    static ResultHandle arrayValue(AnnotationValue value, BytecodeCreator valueMethod, MethodInfo method,
            ClassInfo annotationClass) {
        ResultHandle retValue;
        switch (value.componentKind()) {
            case CLASS:
                Type[] classArray = value.asClassArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(classArray.length));
                for (int i = 0; i < classArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i, valueMethod.loadClass(classArray[i].name().toString()));
                }
                break;
            case STRING:
                String[] stringArray = value.asStringArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(stringArray.length));
                for (int i = 0; i < stringArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i, valueMethod.load(stringArray[i]));
                }
                break;
            case INTEGER:
                int[] intArray = value.asIntArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(intArray.length));
                for (int i = 0; i < intArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i, valueMethod.load(intArray[i]));
                }
                break;
            case LONG:
                long[] longArray = value.asLongArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(longArray.length));
                for (int i = 0; i < longArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i, valueMethod.load(longArray[i]));
                }
                break;
            case BYTE:
                byte[] byteArray = value.asByteArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(byteArray.length));
                for (int i = 0; i < byteArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i, valueMethod.load(byteArray[i]));
                }
                break;
            case CHARACTER:
                char[] charArray = value.asCharArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(charArray.length));
                for (int i = 0; i < charArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i, valueMethod.load(charArray[i]));
                }
                break;
            case ENUM:
                String[] enumArray = value.asEnumArray();
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(enumArray.length));
                String enumType = componentType(method);
                for (int i = 0; i < enumArray.length; i++) {
                    valueMethod.writeArrayValue(retValue, i,
                            valueMethod.readStaticField(FieldDescriptor.of(enumType, enumArray[i], enumType)));
                }
                break;
            // TODO: handle other less common types of array components
            default:
                // Return empty array for empty arrays and unsupported types
                // For an empty array the component kind is UNKNOWN
                retValue = valueMethod.newArray(componentType(method), valueMethod.load(0));
        }
        return retValue;
    }

    static String componentType(MethodInfo method) {
        ArrayType arrayType = method.returnType().asArrayType();
        return arrayType.component().name().toString();
    }

    /**
     * a component of a parameter to a template method. This must only be created
     * by a call to {@link #loadObjectInstanceImpl(Object, Map, Class, List)} to make sure it is stored
     * correctly in the list of deferred parameters
     *
     */
    abstract class DeferredParameter {

        /**
         * The function that is called to read the value for use. This may be by reading the value from the Object[]
         * array, or is may be a direct ldc instruction in the case of primitives
         */
        abstract ResultHandle load(MethodCreator method, ResultHandle array);
    }

    abstract class DeferredArrayStoreParameter extends DeferredParameter {

        final int arrayIndex;

        boolean written = false;

        abstract ResultHandle createValue(MethodCreator method, ResultHandle array);

        @Override
        final ResultHandle load(MethodCreator method, ResultHandle array) {
            if(!written) {
                ResultHandle val = createValue(method, array);
                method.writeArrayValue(array, arrayIndex, val);
                return val;
            }
            return method.readArrayValue(array, arrayIndex);
        }

        DeferredArrayStoreParameter() {
            arrayIndex = deferredParameterCount++;
        }

    }

    interface SerialzationStep {
        void handle(MethodCreator method, ResultHandle array, ResultHandle out);
    }


    /**
     * class responsible for splitting the bytecode into smaller methods, to make sure that even large objects and large
     * numbers of invocations do not put us over the method limit.
     */
    class SplitMethodContext implements Closeable {
        final ResultHandle deferredParameterArray;
        final MethodCreator mainMethod;
        final ClassCreator classCreator;

        int currentCount;
        MethodCreator currentMethod;
        Map<Integer, ResultHandle> currentMethodCache = new HashMap<>();

        SplitMethodContext(ResultHandle deferredParameterArray, MethodCreator mainMethod, ClassCreator classCreator) {
            this.deferredParameterArray = deferredParameterArray;
            this.mainMethod = mainMethod;
            this.classCreator = classCreator;
        }


        void writeInstruction() {

        }

        @Override
        public void close() {
            if(currentMethod != null) {
                currentMethod.returnValue(null);
                currentMethod = null;
            }
        }
    }
}
