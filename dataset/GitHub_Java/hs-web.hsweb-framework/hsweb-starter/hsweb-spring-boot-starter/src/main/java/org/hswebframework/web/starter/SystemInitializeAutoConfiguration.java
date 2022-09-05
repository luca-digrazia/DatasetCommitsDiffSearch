/*
 *
 *  * Copyright 2016 http://www.hswebframework.org
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.hswebframework.web.starter;

import org.hsweb.ezorm.rdb.RDBDatabase;
import org.hsweb.ezorm.rdb.executor.SqlExecutor;
import org.hsweb.ezorm.rdb.meta.RDBDatabaseMetaData;
import org.hsweb.ezorm.rdb.meta.parser.H2TableMetaParser;
import org.hsweb.ezorm.rdb.meta.parser.MysqlTableMetaParser;
import org.hsweb.ezorm.rdb.meta.parser.OracleTableMetaParser;
import org.hsweb.ezorm.rdb.render.dialect.H2RDBDatabaseMetaData;
import org.hsweb.ezorm.rdb.render.dialect.MysqlRDBDatabaseMetaData;
import org.hsweb.ezorm.rdb.render.dialect.OracleRDBDatabaseMetaData;
import org.hsweb.ezorm.rdb.simple.SimpleDatabase;
import org.hswebframework.web.datasource.DataSourceHolder;
import org.hswebframework.web.datasource.DatabaseType;
import org.hswebframework.web.starter.init.SystemInitialize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * @author zhouhao
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemInitializeAutoConfiguration implements CommandLineRunner {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    DataSource dataSource;

    @Autowired
    SqlExecutor sqlExecutor;

    @Override
    public void run(String... args) throws Exception {
        DatabaseType type = DataSourceHolder.currentDatabaseType();
        SystemVersion version = appProperties.build();
        Connection connection = null;
        String jdbcUserName;
        try {
            connection = DataSourceHolder.currentDataSource().getNative().getConnection();
            jdbcUserName = connection.getMetaData().getUserName();
        } finally {
            if (null != connection) connection.close();
        }
        RDBDatabaseMetaData metaData;
        switch (type) {
            case oracle:
                metaData = new OracleRDBDatabaseMetaData();
                metaData.setParser(new OracleTableMetaParser(sqlExecutor));
                break;
            case mysql:
                metaData = new MysqlRDBDatabaseMetaData();
                metaData.setParser(new MysqlTableMetaParser(sqlExecutor));
                break;
            default:
                h2:
                metaData = new H2RDBDatabaseMetaData();
                metaData.setParser(new H2TableMetaParser(sqlExecutor));
                break;
        }
        RDBDatabase database = new SimpleDatabase(metaData, sqlExecutor);
        SystemInitialize initialize = new SystemInitialize(sqlExecutor, database, version);

        initialize.addScriptContext("db", jdbcUserName);
        initialize.addScriptContext("dbType", type.name());

        initialize.install();
    }
}
