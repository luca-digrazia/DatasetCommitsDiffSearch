package io.quarkus.reactive.mysql.client.deployment;

import javax.enterprise.context.ApplicationScoped;

import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.datasource.runtime.DataSourceBuildTimeConfig;
import io.quarkus.datasource.runtime.DataSourcesBuildTimeConfig;
import io.quarkus.datasource.runtime.DataSourcesRuntimeConfig;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.reactive.datasource.ReactiveDataSource;
import io.quarkus.reactive.datasource.deployment.VertxPoolBuildItem;
import io.quarkus.reactive.datasource.runtime.DataSourceReactiveBuildTimeConfig;
import io.quarkus.reactive.datasource.runtime.DataSourcesReactiveBuildTimeConfig;
import io.quarkus.reactive.datasource.runtime.DataSourcesReactiveRuntimeConfig;
import io.quarkus.reactive.mysql.client.runtime.DataSourcesReactiveMySQLConfig;
import io.quarkus.reactive.mysql.client.runtime.MySQLPoolRecorder;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.quarkus.vertx.deployment.VertxBuildItem;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Pool;

class ReactiveMySQLClientProcessor {

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem build(BuildProducer<FeatureBuildItem> feature,
            BuildProducer<MySQLPoolBuildItem> mySQLPool,
            BuildProducer<VertxPoolBuildItem> vertxPool,
            MySQLPoolRecorder recorder,
            VertxBuildItem vertx,
            ShutdownContextBuildItem shutdown,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ExtensionSslNativeSupportBuildItem> sslNativeSupport,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig, DataSourcesRuntimeConfig dataSourcesRuntimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig,
            DataSourcesReactiveRuntimeConfig dataSourcesReactiveRuntimeConfig,
            DataSourcesReactiveMySQLConfig dataSourcesReactiveMySQLConfig) {

        feature.produce(new FeatureBuildItem(Feature.REACTIVE_MYSQL_CLIENT));

        createPoolIfDefined(recorder, vertx, shutdown, mySQLPool, vertxPool, syntheticBeans,
                DataSourceUtil.DEFAULT_DATASOURCE_NAME, dataSourcesBuildTimeConfig,
                dataSourcesRuntimeConfig, dataSourcesReactiveBuildTimeConfig, dataSourcesReactiveRuntimeConfig,
                dataSourcesReactiveMySQLConfig);

        for (String dataSourceName : dataSourcesBuildTimeConfig.namedDataSources.keySet()) {
            createPoolIfDefined(recorder, vertx, shutdown, mySQLPool, vertxPool, syntheticBeans, dataSourceName,
                    dataSourcesBuildTimeConfig, dataSourcesRuntimeConfig, dataSourcesReactiveBuildTimeConfig,
                    dataSourcesReactiveRuntimeConfig, dataSourcesReactiveMySQLConfig);
        }

        // Enable SSL support by default
        sslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(Feature.REACTIVE_MYSQL_CLIENT));

        return new ServiceStartBuildItem("reactive-mysql-client");
    }

    /**
     * The health check needs to be produced in a separate method to avoid a circular dependency (the Vert.x instance creation
     * consumes the AdditionalBeanBuildItems).
     */
    @BuildStep
    void addHealthCheck(
            BuildProducer<HealthBuildItem> healthChecks,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig) {
        if (!hasPools(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig)) {
            return;
        }

        healthChecks.produce(
                new HealthBuildItem("io.quarkus.reactive.mysql.client.runtime.health.ReactiveMySQLDataSourcesHealthCheck",
                        dataSourcesBuildTimeConfig.healthEnabled));
    }

    private void createPoolIfDefined(MySQLPoolRecorder recorder,
            VertxBuildItem vertx,
            ShutdownContextBuildItem shutdown,
            BuildProducer<MySQLPoolBuildItem> mySQLPool,
            BuildProducer<VertxPoolBuildItem> vertxPool,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            String dataSourceName,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesRuntimeConfig dataSourcesRuntimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig,
            DataSourcesReactiveRuntimeConfig dataSourcesReactiveRuntimeConfig,
            DataSourcesReactiveMySQLConfig dataSourcesReactiveMySQLConfig) {

        if (!isReactiveMySQLPoolDefined(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig, dataSourceName)) {
            return;
        }

        RuntimeValue<MySQLPool> pool = recorder.configureMySQLPool(vertx.getVertx(),
                dataSourceName,
                dataSourcesRuntimeConfig,
                dataSourcesReactiveRuntimeConfig,
                dataSourcesReactiveMySQLConfig,
                shutdown);
        mySQLPool.produce(new MySQLPoolBuildItem(dataSourceName, pool));

        ExtendedBeanConfigurator mySQLPoolBeanConfigurator = SyntheticBeanBuildItem.configure(MySQLPool.class)
                .defaultBean()
                .addType(Pool.class)
                .scope(ApplicationScoped.class)
                .runtimeValue(pool)
                .unremovable()
                .setRuntimeInit();

        addQualifiers(mySQLPoolBeanConfigurator, dataSourceName);

        syntheticBeans.produce(mySQLPoolBeanConfigurator.done());

        ExtendedBeanConfigurator mutinyMySQLPoolConfigurator = SyntheticBeanBuildItem
                .configure(io.vertx.mutiny.mysqlclient.MySQLPool.class)
                .defaultBean()
                .scope(ApplicationScoped.class)
                .runtimeValue(recorder.mutinyMySQLPool(pool))
                .setRuntimeInit();

        addQualifiers(mutinyMySQLPoolConfigurator, dataSourceName);

        syntheticBeans.produce(mutinyMySQLPoolConfigurator.done());

        vertxPool.produce(new VertxPoolBuildItem(pool, DatabaseKind.MYSQL, DataSourceUtil.isDefault(dataSourceName)));
    }

    private static boolean isReactiveMySQLPoolDefined(DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig, String dataSourceName) {
        DataSourceBuildTimeConfig dataSourceBuildTimeConfig = dataSourcesBuildTimeConfig
                .getDataSourceRuntimeConfig(dataSourceName);
        DataSourceReactiveBuildTimeConfig dataSourceReactiveBuildTimeConfig = dataSourcesReactiveBuildTimeConfig
                .getDataSourceReactiveBuildTimeConfig(dataSourceName);

        if (!dataSourceBuildTimeConfig.dbKind.isPresent()) {
            return false;
        }

        if ((!DatabaseKind.isMySQL(dataSourceBuildTimeConfig.dbKind.get())
                && !DatabaseKind.isMariaDB(dataSourceBuildTimeConfig.dbKind.get()))
                || !dataSourceReactiveBuildTimeConfig.enabled) {
            return false;
        }

        return true;
    }

    private boolean hasPools(DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig) {
        if (isReactiveMySQLPoolDefined(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig,
                DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
            return true;
        }

        for (String dataSourceName : dataSourcesBuildTimeConfig.namedDataSources.keySet()) {
            if (isReactiveMySQLPoolDefined(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig,
                    dataSourceName)) {
                return true;
            }
        }

        return false;
    }

    private static void addQualifiers(ExtendedBeanConfigurator configurator, String dataSourceName) {
        if (DataSourceUtil.isDefault(dataSourceName)) {
            configurator.addQualifier(DotNames.DEFAULT);
        } else {
            configurator.addQualifier().annotation(DotNames.NAMED).addValue("value", dataSourceName).done();
            configurator.addQualifier().annotation(ReactiveDataSource.class).addValue("value", dataSourceName)
                    .done();
        }
    }
}
