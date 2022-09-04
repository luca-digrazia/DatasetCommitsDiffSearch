package org.jboss.protean.arc.processor;

import static org.jboss.protean.arc.processor.Basics.index;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import org.jboss.jandex.Index;
import org.jboss.protean.arc.processor.AnnotationLiteralProcessor;
import org.jboss.protean.arc.processor.BeanDeployment;
import org.jboss.protean.arc.processor.BeanGenerator;
import org.jboss.protean.arc.processor.BeanProcessor;
import org.jboss.protean.arc.processor.ClientProxyGenerator;
import org.jboss.protean.arc.processor.ResourceOutput.Resource;
import org.junit.Test;

public class ClientProxyGeneratorTest {

    @Test
    public void testGenerator() throws IOException {

        Index index = index(Producer.class, List.class, Collection.class, Iterable.class, AbstractList.class, MyList.class);
        BeanDeployment deployment = new BeanDeployment(index, null);
        deployment.init();

        BeanGenerator beanGenerator = new BeanGenerator();
        ClientProxyGenerator proxyGenerator = new ClientProxyGenerator();

        deployment.getBeans().stream().filter(bean -> bean.getScope().isNormal()).forEach(bean -> {
            for (Resource resource : beanGenerator.generate(bean, new AnnotationLiteralProcessor(BeanProcessor.DEFAULT_NAME, true), ReflectionRegistration.NOOP)) {
                proxyGenerator.generate(bean, resource.getFullyQualifiedName(), ReflectionRegistration.NOOP);
            }
        });
        // TODO test generated bytecode
    }

    @Dependent
    static class Producer {

        @ApplicationScoped
        @Produces
        List<String> list() {
            return null;
        }

    }

    @ApplicationScoped
    static class MyList extends AbstractList<String> {

        @Override
        public String get(int index) {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        void myMethod() throws IOException {
        }

    }

}
