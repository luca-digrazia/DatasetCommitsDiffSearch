package org.hswebframework.web.authorization.simple;

import org.hswebframework.web.authorization.builder.AuthenticationBuilderFactory;
import org.hswebframework.web.authorization.builder.DataAccessConfigBuilderFactory;
import org.hswebframework.web.authorization.builder.FieldAccessConfigBuilderFactory;
import org.hswebframework.web.authorization.simple.builder.DataAccessConfigBuilderConvert;
import org.hswebframework.web.authorization.simple.builder.SimpleAuthenticationBuilderFactory;
import org.hswebframework.web.authorization.simple.builder.SimpleDataAccessConfigBuilderFactory;
import org.hswebframework.web.authorization.simple.builder.SimpleFieldAccessConfigBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
@Configuration
public class AuthorizationAutoConfiguration {

    @Autowired(required = false)
    private List<DataAccessConfigBuilderConvert> dataAccessConfigBuilderConverts;

    @Bean
    @ConditionalOnMissingBean(FieldAccessConfigBuilderFactory.class)
    public FieldAccessConfigBuilderFactory fieldAccessConfigBuilderFactory() {
        return new SimpleFieldAccessConfigBuilderFactory();
    }

    @Bean
    @ConditionalOnMissingBean(DataAccessConfigBuilderFactory.class)
    public DataAccessConfigBuilderFactory dataAccessConfigBuilderFactory() {
        SimpleDataAccessConfigBuilderFactory factory = new SimpleDataAccessConfigBuilderFactory();
        if (null != dataAccessConfigBuilderConverts) {
            dataAccessConfigBuilderConverts.forEach(factory::addConvert);
        }
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationBuilderFactory.class)
    public AuthenticationBuilderFactory authenticationBuilderFactory(DataAccessConfigBuilderFactory dataAccessConfigBuilderFactory
            , FieldAccessConfigBuilderFactory fieldAccessConfigBuilderFactory) {
        return new SimpleAuthenticationBuilderFactory(fieldAccessConfigBuilderFactory, dataAccessConfigBuilderFactory);
    }
}
