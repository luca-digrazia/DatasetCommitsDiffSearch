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
import org.jboss.protean.arc.processor.ResourceOutput.Resource;
import org.junit.Test;

public class ClientProxyGeneratorTest {

    @Test
    public void testGenerator() throws IOException {

        Index index = index(Producer.class, List.class, Collection.class, Iterable.class, AbstractList.class, MyList.class);
        BeanDeployment deployment = new BeanDeployment(index, null, null);
        deployment.init();

        BeanGenerator beanGenerator = new BeanGenerator( new AnnotationLiteralProcessor(true, TruePredicate.INSTANCE), TruePredicate.INSTANCE);
        ClientProxyGenerator proxyGenerator = new ClientProxyGenerator(TruePredicate.INSTANCE);

        deployment.getBeans().stream().filter(bean -> bean.getScope().isNormal()).forEach(bean -> {
            for (Resource resource : beanGenerator.generate(bean, ReflectionRegistration.NOOP)) {
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
