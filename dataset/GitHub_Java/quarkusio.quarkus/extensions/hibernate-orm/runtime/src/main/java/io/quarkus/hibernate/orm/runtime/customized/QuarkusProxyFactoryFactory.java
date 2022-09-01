package io.quarkus.hibernate.orm.runtime.customized;

import org.hibernate.bytecode.spi.BasicProxyFactory;
import org.hibernate.bytecode.spi.ProxyFactoryFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.proxy.ProxyFactory;

import io.quarkus.hibernate.orm.runtime.proxies.ProxyDefinitions;

public class QuarkusProxyFactoryFactory implements ProxyFactoryFactory {

    private final ProxyDefinitions proxyClassDefinitions;

    public QuarkusProxyFactoryFactory(ProxyDefinitions proxyClassDefinitions) {
        this.proxyClassDefinitions = proxyClassDefinitions;
    }

    @Override
    public ProxyFactory buildProxyFactory(SessionFactoryImplementor sessionFactory) {
        return new QuarkusProxyFactory(proxyClassDefinitions);
    }

    @Override
    public BasicProxyFactory buildBasicProxyFactory(Class superClass, Class[] interfaces) {
        return null;
    }
}
