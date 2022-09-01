package org.jboss.protean.arc.processor;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InterceptionType;
import javax.interceptor.InvocationContext;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.protean.arc.CreationalContextImpl;
import org.jboss.protean.arc.InjectableInterceptor;
import org.jboss.protean.arc.InjectableReferenceProvider;
import org.jboss.protean.arc.InvocationContextImpl;
import org.jboss.protean.arc.InvocationContextImpl.InterceptorInvocation;
import org.jboss.protean.arc.Reflections;
import org.jboss.protean.arc.Subclass;
import org.jboss.protean.arc.processor.ResourceOutput.Resource;
import org.jboss.protean.gizmo.BytecodeCreator;
import org.jboss.protean.gizmo.CatchBlockCreator;
import org.jboss.protean.gizmo.ClassCreator;
import org.jboss.protean.gizmo.DescriptorUtils;
import org.jboss.protean.gizmo.ExceptionTable;
import org.jboss.protean.gizmo.FieldCreator;
import org.jboss.protean.gizmo.FieldDescriptor;
import org.jboss.protean.gizmo.FunctionCreator;
import org.jboss.protean.gizmo.MethodCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;

/**
 *
 * @author Martin Kouba
 */
public class SubclassGenerator extends AbstractGenerator {

    static final String SUBCLASS_SUFFIX = "_Subclass";

    static String generatedName(DotName providerTypeName, String baseName) {
        return DotNames.packageName(providerTypeName).replace(".", "/") + "/" + baseName + SUBCLASS_SUFFIX;
    }

    /**
     *
     * @param bean
     * @param beanClassName Fully qualified class name
     * @return a java file
     */
    Collection<Resource> generate(BeanInfo bean, String beanClassName) {

        ResourceClassOutput classOutput = new ResourceClassOutput();

        Type providerType = bean.getProviderType();
        ClassInfo providerClass = bean.getDeployment().getIndex().getClassByName(providerType.name());
        String providerTypeName = providerClass.name().toString();
        String baseName = getBaseName(bean, beanClassName);
        String generatedName = generatedName(providerType.name(), baseName);

        // Foo_Subclass extends Foo implements Subclass
        ClassCreator subclass = ClassCreator.builder().classOutput(classOutput).className(generatedName).superClass(providerTypeName).interfaces(Subclass.class)
                .build();

        FieldDescriptor preDestroyField = createConstructor(bean, subclass, providerTypeName);
        createDestroy(subclass, preDestroyField);

        subclass.close();
        return classOutput.getResources();
    }

    protected FieldDescriptor createConstructor(BeanInfo bean, ClassCreator subclass, String providerTypeName) {

        List<String> parameterTypes = new ArrayList<>();

        // First constructor injection points
        Optional<Injection> constructorInjection = bean.getConstructorInjection();
        if (constructorInjection.isPresent()) {
            for (InjectionPointInfo injectionPoint : constructorInjection.get().injectionPoints) {
                parameterTypes.add(injectionPoint.requiredType.name().toString());
            }
        }
        int superParamsSize = parameterTypes.size();

        // CreationalContext
        parameterTypes.add(CreationalContext.class.getName());

        // Interceptor providers
        List<InterceptorInfo> boundInterceptors = bean.getBoundInterceptors();
        for (InterceptorInfo interceptor : boundInterceptors) {
            parameterTypes.add(InjectableInterceptor.class.getName());
        }

        MethodCreator constructor = subclass.getMethodCreator("<init>", "V", parameterTypes.toArray(new String[0]));

        ResultHandle creationalContextHandle = constructor.getMethodParam(superParamsSize);
        ResultHandle[] superParams = new ResultHandle[superParamsSize];
        for (int j = 0; j < superParamsSize; j++) {
            superParams[j] = constructor.getMethodParam(j);
        }
        // super(fooProvider)
        constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(providerTypeName, parameterTypes.subList(0, superParamsSize).toArray(new String[0])),
                constructor.getThis(), superParams);

        Map<InterceptorInfo, ResultHandle> interceptorToResultHandle = new HashMap<>();
        for (int j = 0; j < boundInterceptors.size(); j++) {
            interceptorToResultHandle.put(boundInterceptors.get(j), constructor.getMethodParam(j + superParamsSize + 1));
        }

        // PreDestroy interceptors
        FieldCreator preDestroysField = null;
        List<InterceptorInfo> preDestroys = bean.getLifecycleInterceptors(InterceptionType.PRE_DESTROY);
        if (!preDestroys.isEmpty()) {
            // private final List<InvocationContextImpl.InterceptorInvocation> preDestroys
            preDestroysField = subclass.getFieldCreator("preDestroys", DescriptorUtils.extToInt(ArrayList.class.getName()))
                    .setModifiers(ACC_PRIVATE | ACC_FINAL);
            // preDestroys = new ArrayList<>()
            constructor.writeInstanceField(preDestroysField.getFieldDescriptor(), constructor.getThis(),
                    constructor.newInstance(MethodDescriptor.ofConstructor(ArrayList.class)));
            for (InterceptorInfo interceptor : preDestroys) {
                // preDestroys.add(InvocationContextImpl.InterceptorInvocation.preDestroy(provider1,provider1.get(CreationalContextImpl.child(ctx))))
                ResultHandle creationalContext = constructor.invokeStaticMethod(
                        MethodDescriptor.ofMethod(CreationalContextImpl.class, "child", CreationalContextImpl.class, CreationalContext.class),
                        creationalContextHandle);
                ResultHandle interceptorInstance = constructor.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(InjectableReferenceProvider.class, "get", Object.class, CreationalContext.class),
                        interceptorToResultHandle.get(interceptor), creationalContext);
                ResultHandle interceptionInvocation = constructor.invokeStaticMethod(MethodDescriptor.ofMethod(InterceptorInvocation.class, "preDestroy",
                        InterceptorInvocation.class, InjectableInterceptor.class, Object.class), interceptorToResultHandle.get(interceptor),
                        interceptorInstance);
                constructor.invokeInterfaceMethod(MethodDescriptor.ofMethod(List.class, "add", boolean.class, Object.class),
                        constructor.readInstanceField(preDestroysField.getFieldDescriptor(), constructor.getThis()), interceptionInvocation);
            }
        }

        // Init intercepted methods and interceptor chains
        // private final Map<String, List<InvocationContextImpl.InterceptorInvocation>> interceptorChains
        FieldCreator interceptorChainsField = subclass.getFieldCreator("interceptorChains", DescriptorUtils.extToInt(Map.class.getName()))
                .setModifiers(ACC_PRIVATE | ACC_FINAL);
        // interceptorChains = new HashMap<>()
        constructor.writeInstanceField(interceptorChainsField.getFieldDescriptor(), constructor.getThis(),
                constructor.newInstance(MethodDescriptor.ofConstructor(HashMap.class)));
        ResultHandle interceptorChainsHandle = constructor.readInstanceField(interceptorChainsField.getFieldDescriptor(), constructor.getThis());
        // private final Map<String, Method> methods
        FieldCreator methodsField = subclass.getFieldCreator("methods", DescriptorUtils.extToInt(Map.class.getName())).setModifiers(ACC_PRIVATE | ACC_FINAL);
        constructor.writeInstanceField(methodsField.getFieldDescriptor(), constructor.getThis(),
                constructor.newInstance(MethodDescriptor.ofConstructor(HashMap.class)));
        ResultHandle methodsHandle = constructor.readInstanceField(methodsField.getFieldDescriptor(), constructor.getThis());

        int methodIdx = 1;
        for (Entry<MethodInfo, List<InterceptorInfo>> entry : bean.getInterceptedMethods().entrySet()) {
            String methodId = "m" + methodIdx++;
            MethodInfo method = entry.getKey();
            ResultHandle methodIdHandle = constructor.load(methodId);

            // First create interceptor chains
            // List<InvocationContextImpl.InterceptorInvocation> m1Chain = new ArrayList<>()
            ResultHandle chainHandle = constructor.newInstance(MethodDescriptor.ofConstructor(ArrayList.class));
            for (InterceptorInfo interceptor : entry.getValue()) {
                // m1Chain.add(InvocationContextImpl.InterceptorInvocation.aroundInvoke(p3,p3.get(CreationalContextImpl.child(ctx))))
                ResultHandle creationalContext = constructor.invokeStaticMethod(
                        MethodDescriptor.ofMethod(CreationalContextImpl.class, "child", CreationalContextImpl.class, CreationalContext.class),
                        creationalContextHandle);
                ResultHandle interceptorInstance = constructor.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(InjectableReferenceProvider.class, "get", Object.class, CreationalContext.class),
                        interceptorToResultHandle.get(interceptor), creationalContext);
                ResultHandle interceptionInvocation = constructor.invokeStaticMethod(MethodDescriptor.ofMethod(InterceptorInvocation.class, "aroundInvoke",
                        InterceptorInvocation.class, InjectableInterceptor.class, Object.class), interceptorToResultHandle.get(interceptor),
                        interceptorInstance);
                constructor.invokeInterfaceMethod(MethodDescriptor.ofMethod(List.class, "add", boolean.class, Object.class), chainHandle,
                        interceptionInvocation);
            }
            // interceptorChains.put("m1", m1Chain)
            constructor.invokeInterfaceMethod(MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class, Object.class), interceptorChainsHandle,
                    methodIdHandle, chainHandle);
            // methods.put("m1", Reflections.findMethod(org.jboss.weld.arc.test.interceptors.SimpleBean.class,"foo",java.lang.String.class))
            ResultHandle[] paramsHandles = new ResultHandle[3];
            paramsHandles[0] = constructor.loadClass(providerTypeName);
            paramsHandles[1] = constructor.load(method.name());
            if (!method.parameters().isEmpty()) {
                ResultHandle paramsArray = constructor.newArray(Class.class, constructor.load(method.parameters().size()));
                for (ListIterator<Type> iterator = method.parameters().listIterator(); iterator.hasNext();) {
                    constructor.writeArrayValue(paramsArray, constructor.load(iterator.nextIndex()), constructor.loadClass(iterator.next().name().toString()));
                }
                paramsHandles[2] = paramsArray;
            } else {
                paramsHandles[2] = constructor.newArray(Class.class, constructor.load(0));
            }
            ResultHandle methodHandle = constructor.invokeStaticMethod(
                    MethodDescriptor.ofMethod(Reflections.class, "findMethod", Method.class, Class.class, String.class, Class[].class), paramsHandles);
            constructor.invokeInterfaceMethod(MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class, Object.class), methodsHandle,
                    methodIdHandle, methodHandle);

            // Finally create the forwarding method
            createForwardingMethod(method, methodId, subclass, providerTypeName, interceptorChainsField.getFieldDescriptor(),
                    methodsField.getFieldDescriptor());
        }

        constructor.returnValue(null);
        return preDestroysField != null ? preDestroysField.getFieldDescriptor() : null;
    }

    private void createForwardingMethod(MethodInfo method, String methodId, ClassCreator subclass, String providerTypeName,
            FieldDescriptor interceptorChainsField, FieldDescriptor methodsField) {

        MethodCreator forwardMethod = subclass.getMethodCreator(MethodDescriptor.of(method));

        // Params
        // Object[] params = new Object[] {p1}
        ResultHandle paramsHandle = forwardMethod.newArray(Object.class, forwardMethod.load(method.parameters().size()));
        for (int i = 0; i < method.parameters().size(); i++) {
            forwardMethod.writeArrayValue(paramsHandle, forwardMethod.load(i), forwardMethod.getMethodParam(i));
        }

        // Forwarding function
        // Function<InvocationContext, Object> forward = ctx -> super.foo((java.lang.String)ctx.getParameters()[0])
        FunctionCreator func = forwardMethod.createFunction(Function.class);
        BytecodeCreator funcBytecode = func.getBytecode();
        ResultHandle ctxHandle = funcBytecode.getMethodParam(0);
        ResultHandle[] superParamHandles = new ResultHandle[method.parameters().size()];
        ResultHandle ctxParamsHandle = funcBytecode.invokeInterfaceMethod(MethodDescriptor.ofMethod(InvocationContext.class, "getParameters", Object[].class),
                ctxHandle);
        for (int i = 0; i < superParamHandles.length; i++) {
            superParamHandles[i] = funcBytecode.readArrayValue(ctxParamsHandle, funcBytecode.load(i));
        }
        ResultHandle superResult = funcBytecode.invokeSpecialMethod(
                MethodDescriptor.ofMethod(providerTypeName, method.name(), method.returnType().name().toString(),
                        method.parameters().stream().map(p -> p.name().toString()).collect(Collectors.toList()).toArray(new String[0])),
                forwardMethod.getThis(), superParamHandles);
        funcBytecode.returnValue(superResult);

        // InvocationContext
        // (java.lang.String) InvocationContextImpl.aroundInvoke(this, methods.get("m1"), params, interceptorChains.get("m1"), forward).proceed()
        ExceptionTable tryCatch = forwardMethod.addTryCatch();
        // catch (Exception e)
        CatchBlockCreator exception = tryCatch.addCatchClause(Exception.class);
        // throw new RuntimeException(e)
        exception.throwException(RuntimeException.class, "Error invoking subclass", exception.getCaughtException());
        // InvocationContextImpl.aroundInvoke(this, methods.get("m1"), params, interceptorChains.get("m1"), forward)
        ResultHandle methodIdHandle = forwardMethod.load(methodId);
        ResultHandle interceptedMethodHandle = forwardMethod.invokeInterfaceMethod(MethodDescriptors.MAP_GET,
                forwardMethod.readInstanceField(methodsField, forwardMethod.getThis()), methodIdHandle);
        ResultHandle interceptedChainHandle = forwardMethod.invokeInterfaceMethod(MethodDescriptors.MAP_GET,
                forwardMethod.readInstanceField(interceptorChainsField, forwardMethod.getThis()), methodIdHandle);
        ResultHandle invocationContext = forwardMethod.invokeStaticMethod(
                MethodDescriptor.ofMethod(InvocationContextImpl.class, "aroundInvoke", InvocationContextImpl.class, Object.class, Method.class, Object[].class,
                        List.class, Function.class),
                forwardMethod.getThis(), interceptedMethodHandle, paramsHandle, interceptedChainHandle, func.getInstance());
        // InvocationContext.proceed()
        forwardMethod.returnValue(
                forwardMethod.invokeInterfaceMethod(MethodDescriptor.ofMethod(InvocationContext.class, "proceed", Object.class), invocationContext));
        tryCatch.complete();
    }

    /**
     *
     * @param subclass
     * @param preDestroysField
     * @see Subclass#destroy()
     */
    protected void createDestroy(ClassCreator subclass, FieldDescriptor preDestroysField) {
        if (preDestroysField != null) {
            MethodCreator destroyMethod = subclass.getMethodCreator(MethodDescriptor.ofMethod(Subclass.class, "destroy", void.class));
            ResultHandle predestroysHandle = destroyMethod.readInstanceField(preDestroysField, destroyMethod.getThis());
            // try
            ExceptionTable tryCatch = destroyMethod.addTryCatch();
            // catch (Exception e)
            CatchBlockCreator exception = tryCatch.addCatchClause(Exception.class);
            // throw new RuntimeException(e)
            exception.throwException(RuntimeException.class, "Error destroying subclass", exception.getCaughtException());
            // InvocationContextImpl.preDestroy(this,predestroys)
            ResultHandle invocationContext = destroyMethod.invokeStaticMethod(
                    MethodDescriptor.ofMethod(InvocationContextImpl.class, "preDestroy", InvocationContextImpl.class, Object.class, List.class),
                    destroyMethod.getThis(), predestroysHandle);
            // InvocationContext.proceed()
            destroyMethod.invokeInterfaceMethod(MethodDescriptor.ofMethod(InvocationContext.class, "proceed", Object.class), invocationContext);
            tryCatch.complete();
            destroyMethod.returnValue(null);
        }
    }

}
