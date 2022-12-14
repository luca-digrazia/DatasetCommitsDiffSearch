/*
 *  Copyright 2016 http://www.hswebframework.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.hswebframework.web.example.oauth2;

import org.hsweb.ezorm.rdb.executor.AbstractJdbcSqlExecutor;
import org.hsweb.ezorm.rdb.executor.SqlExecutor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.AuthenticationManager;
import org.hswebframework.web.authorization.oauth2.client.OAuth2RequestService;
import org.hswebframework.web.authorization.oauth2.client.request.OAuth2Session;
import org.hswebframework.web.authorization.oauth2.client.response.OAuth2Response;
import org.hswebframework.web.authorization.shiro.oauth2sso.OAuth2SSOAuthorizingListener;
import org.hswebframework.web.commons.entity.DataStatus;
import org.hswebframework.web.commons.entity.factory.EntityFactory;
import org.hswebframework.web.dao.datasource.DataSourceHolder;
import org.hswebframework.web.dao.datasource.DatabaseType;
import org.hswebframework.web.entity.oauth2.client.OAuth2ServerConfigEntity;
import org.hswebframework.web.service.oauth2.client.OAuth2ServerConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO ????????????
 *
 * @author zhouhao
 */
@SpringBootApplication
@Configuration
@EnableCaching
@EnableAspectJAutoProxy
public class OAuth2ClientApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(OAuth2ClientApplication.class);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        // ??????????????????????????????,
        // ?????????????????????????????????OAuth2???????????????????????????,
        // ????????????????????????OAuth2????????????
        // ????????????sso?????????,????????????????????????????????????????????????
        Map<String, OAuth2Session> sessionMap = new HashMap<>();

        return new AuthenticationManager() {
            @Override
            public Authentication getByUserId(String userId) {
                //?????????????????????????????????
                return sessionMap.computeIfAbsent("auth", key -> oAuth2RequestService.create("hsweb-oauth-server")
                        .byClientCredentials())
                        .request("oauth2/user-auth-info/" + userId)
                        .get().onError(OAuth2Response.throwOnError)
                        .as(Authentication.class);
            }

            @Override
            public Authentication sync(Authentication authentication) {
                //???????????????
                return authentication;
            }
        };
    }

    @Autowired
    EntityFactory             entityFactory;
    @Autowired
    OAuth2ServerConfigService serverConfigService;
    @Autowired
    OAuth2RequestService      oAuth2RequestService;

    @Override
    public void run(String... strings) throws Exception {
        OAuth2ServerConfigEntity entity = entityFactory.newInstance(OAuth2ServerConfigEntity.class);
        entity.setId("hsweb-oauth-server");
        entity.setName("hsweb OAuth2");
        //????????????hosts??????????????????
        entity.setApiBaseUrl("http://localhost:8080/");
        entity.setAuthUrl("oauth2/login.html");
        entity.setAccessTokenUrl("oauth2/token");
        //???????????????????????????
        entity.setClientId("hsweb_oauth2_example");
        entity.setClientSecret("hsweb_oauth2_example_secret");
        entity.setRedirectUri("http://localhost:8808/");
        //hsweb
        entity.setProvider("hsweb");
        entity.setStatus(DataStatus.STATUS_ENABLED);
        //add
        serverConfigService.insert(entity);

        OAuth2SSOAuthorizingListener listener = new OAuth2SSOAuthorizingListener(oAuth2RequestService, entity.getId());

        oAuth2RequestService.registerListener(entity.getId(), listener);
    }


    @Bean
    @ConditionalOnMissingBean(SqlExecutor.class)
    public SqlExecutor sqlExecutor(DataSource dataSource) {
        DataSourceHolder.install(dataSource, DatabaseType.h2);
        return new AbstractJdbcSqlExecutor() {
            @Override
            public Connection getConnection() {
                return DataSourceUtils.getConnection(dataSource);
            }

            @Override
            public void releaseConnection(Connection connection) throws SQLException {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        };
    }
}
