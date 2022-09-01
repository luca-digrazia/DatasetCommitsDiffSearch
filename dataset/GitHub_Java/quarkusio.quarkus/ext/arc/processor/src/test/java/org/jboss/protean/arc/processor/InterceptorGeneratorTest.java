package org.jboss.protean.arc.processor;

import static org.jboss.protean.arc.processor.Basics.index;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.InvocationContext;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.protean.arc.processor.types.Baz;
import org.junit.Test;

public class InterceptorGeneratorTest {

    @Test
    public void testGenerator() throws IOException {

        Index index = index(MyInterceptor.class, MyBinding.class, Baz.class);
        BeanDeployment deployment = new BeanDeployment(index, null, null);
        deployment.init();

        InterceptorInfo myInterceptor = deployment.getInterceptors().stream()
                .filter(i -> i.getTarget().asClass().name().equals(DotName.createSimple(MyInterceptor.class.getName()))).findAny().orElse(null);
        assertNotNull(myInterceptor);
        assertEquals(10, myInterceptor.getPriority());
        assertEquals(1, myInterceptor.getBindings().size());
        assertNotNull(myInterceptor.getAroundInvoke());

        InterceptorGenerator generator = new InterceptorGenerator();

        deployment.getInterceptors().forEach(interceptor -> generator.generate(interceptor, new AnnotationLiteralProcessor(BeanProcessor.DEFAULT_NAME, true), ReflectionRegistration.NOOP));
        // TODO test generated bytecode
    }

    @Priority(10)
    @MyBinding
    @Interceptor
    static class MyInterceptor {

        @Inject
        Baz baz;

        @AroundInvoke
        Object superCoolAroundInvokeMethod(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }

    }

    @Retention(RetentionPolicy.RUNTIME)
    @InterceptorBinding
    public @interface MyBinding {

    }

}
