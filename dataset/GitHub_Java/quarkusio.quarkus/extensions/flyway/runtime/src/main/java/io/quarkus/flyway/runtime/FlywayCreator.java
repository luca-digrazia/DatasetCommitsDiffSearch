package io.quarkus.flyway.runtime;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

class FlywayCreator {

    private static final String[] EMPTY_ARRAY = new String[0];

    private final FlywayDataSourceRuntimeConfig flywayRuntimeConfig;
    private final FlywayDataSourceBuildTimeConfig flywayBuildTimeConfig;

    public FlywayCreator(FlywayDataSourceRuntimeConfig flywayRuntimeConfig,
            FlywayDataSourceBuildTimeConfig flywayBuildTimeConfig) {
        this.flywayRuntimeConfig = flywayRuntimeConfig;
        this.flywayBuildTimeConfig = flywayBuildTimeConfig;
    }

    public Flyway createFlyway(DataSource dataSource) {
        FluentConfiguration configure = Flyway.configure();
        configure.dataSource(dataSource);
        if (flywayRuntimeConfig.connectRetries.isPresent()) {
            configure.connectRetries(flywayRuntimeConfig.connectRetries.getAsInt());
        }
        if (flywayRuntimeConfig.schemas.isPresent()) {
            configure.schemas(flywayRuntimeConfig.schemas.get().toArray(EMPTY_ARRAY));
        }
        if (flywayRuntimeConfig.table.isPresent()) {
            configure.table(flywayRuntimeConfig.table.get());
        }
        configure.locations(flywayBuildTimeConfig.locations.toArray(EMPTY_ARRAY));
        if (flywayRuntimeConfig.sqlMigrationPrefix.isPresent()) {
            configure.sqlMigrationPrefix(flywayRuntimeConfig.sqlMigrationPrefix.get());
        }
        if (flywayRuntimeConfig.repeatableSqlMigrationPrefix.isPresent()) {
            configure.repeatableSqlMigrationPrefix(flywayRuntimeConfig.repeatableSqlMigrationPrefix.get());
        }
        configure.baselineOnMigrate(flywayRuntimeConfig.baselineOnMigrate);
        configure.validateOnMigrate(flywayRuntimeConfig.validateOnMigrate);
        configure.outOfOrder(flywayRuntimeConfig.outOfOrder);
        if (flywayRuntimeConfig.baselineVersion.isPresent()) {
            configure.baselineVersion(flywayRuntimeConfig.baselineVersion.get());
        }
        if (flywayRuntimeConfig.baselineDescription.isPresent()) {
            configure.baselineDescription(flywayRuntimeConfig.baselineDescription.get());
        }
        configure.placeholders(flywayRuntimeConfig.placeholders);
        return configure.load();
    }
}
