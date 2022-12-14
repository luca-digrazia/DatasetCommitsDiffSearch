package io.quarkus.test.junit.vertx;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import io.quarkus.test.junit.TestMethodInvoker;
import io.quarkus.vertx.core.runtime.VertxCoreRecorder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class RunOnVertxContextTestMethodInvoker implements TestMethodInvoker {

    private DefaultUniAsserter uniAsserter;

    @Override
    public boolean handlesMethodParamType(String paramClassName) {
        return UniAsserter.class.getName().equals(paramClassName);
    }

    @Override
    public Object methodParamInstance(String paramClassName) {
        if (!handlesMethodParamType(paramClassName)) {
            throw new IllegalStateException(
                    "RunOnVertxContextTestMethodInvoker does not handle '" + paramClassName + "' method param types");
        }
        uniAsserter = new DefaultUniAsserter();
        return uniAsserter;
    }

    @Override
    public boolean supportsMethod(Class<?> originalTestClass, Method originalTestMethod) {
        return hasAnnotation(RunOnVertxContext.class, originalTestMethod.getAnnotations())
                || hasAnnotation(RunOnVertxContext.class, originalTestClass.getAnnotations());
    }

    // we need to use the class name to avoid ClassLoader issues
    private boolean hasAnnotation(Class<? extends Annotation> annotation, Annotation[] annotations) {
        if (annotations != null) {
            for (Annotation methodAnnotation : annotations) {
                if (annotation.getName().equals(methodAnnotation.annotationType().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Object invoke(Object actualTestInstance, Method actualTestMethod, List<Object> actualTestMethodArgs,
            String testClassName) throws Throwable {

        Vertx vertx = VertxCoreRecorder.getVertx().get();
        if (vertx == null) {
            throw new IllegalStateException("Vert.x instance has not been created before attempting to run test method '"
                    + actualTestMethod.getName() + "' of test class '" + testClassName + "'");
        }
        CompletableFuture<Object> cf = new CompletableFuture<>();
        RunTestMethodOnContextHandler handler = new RunTestMethodOnContextHandler(actualTestInstance, actualTestMethod,
                actualTestMethodArgs, uniAsserter, cf);
        vertx.getOrCreateContext().runOnContext(handler);
        try {
            return cf.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            // the test itself threw an exception
            throw e.getCause();
        }
    }

    public static class RunTestMethodOnContextHandler implements Handler<Void> {
        private final Object testInstance;
        private final Method targetMethod;
        private final List<Object> methodArgs;
        private final DefaultUniAsserter uniAsserter;
        private final CompletableFuture<Object> future;

        public RunTestMethodOnContextHandler(Object testInstance, Method targetMethod, List<Object> methodArgs,
                DefaultUniAsserter uniAsserter, CompletableFuture<Object> future) {
            this.testInstance = testInstance;
            this.future = future;
            this.targetMethod = targetMethod;
            this.methodArgs = methodArgs;
            this.uniAsserter = uniAsserter;
        }

        @Override
        public void handle(Void event) {
            doRun();
        }

        private void doRun() {
            try {
                Object testMethodResult = targetMethod.invoke(testInstance, methodArgs.toArray(new Object[0]));
                if (uniAsserter != null) {
                    uniAsserter.execution.subscribe().with(new Consumer<Object>() {
                        @Override
                        public void accept(Object o) {
                            future.complete(testMethodResult);
                        }
                    }, future::completeExceptionally);
                } else {
                    future.complete(testMethodResult);
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
    }

}
