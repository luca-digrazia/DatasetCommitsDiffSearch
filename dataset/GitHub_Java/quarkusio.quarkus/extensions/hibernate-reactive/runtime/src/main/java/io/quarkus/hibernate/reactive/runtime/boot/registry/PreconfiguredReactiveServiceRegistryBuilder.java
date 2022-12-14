package io.quarkus.hibernate.reactive.runtime.boot.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.internal.BootstrapServiceRegistryImpl;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.boot.registry.selector.internal.StrategySelectorImpl;
import org.hibernate.engine.config.internal.ConfigurationServiceInitiator;
import org.hibernate.engine.jdbc.batch.internal.BatchBuilderInitiator;
import org.hibernate.engine.jdbc.connections.internal.MultiTenantConnectionProviderInitiator;
import org.hibernate.engine.jdbc.cursor.internal.RefCursorSupportInitiator;
import org.hibernate.engine.jdbc.internal.JdbcServicesInitiator;
import org.hibernate.event.internal.EntityCopyObserverFactoryInitiator;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.internal.EntityManagerMessageLogger;
import org.hibernate.persister.internal.PersisterFactoryInitiator;
import org.hibernate.property.access.internal.PropertyAccessStrategyResolverInitiator;
import org.hibernate.reactive.id.impl.ReactiveIdentifierGeneratorFactoryInitiator;
import org.hibernate.reactive.provider.service.NoJdbcConnectionProviderInitiator;
import org.hibernate.reactive.provider.service.ReactivePersisterClassResolverInitiator;
import org.hibernate.reactive.provider.service.ReactiveQueryTranslatorFactoryInitiator;
import org.hibernate.resource.beans.spi.ManagedBeanRegistryInitiator;
import org.hibernate.resource.transaction.internal.TransactionCoordinatorBuilderInitiator;
import org.hibernate.service.internal.ProvidedService;
import org.hibernate.service.internal.SessionFactoryServiceRegistryFactoryInitiator;
import org.hibernate.tool.hbm2ddl.ImportSqlCommandExtractorInitiator;
import org.hibernate.tool.schema.internal.SchemaManagementToolInitiator;

import io.quarkus.hibernate.orm.runtime.boot.registry.MirroringIntegratorService;
import io.quarkus.hibernate.orm.runtime.customized.DisabledBytecodeProviderInitiator;
import io.quarkus.hibernate.orm.runtime.customized.QuarkusJndiServiceInitiator;
import io.quarkus.hibernate.orm.runtime.customized.QuarkusJtaPlatformInitiator;
import io.quarkus.hibernate.orm.runtime.customized.QuarkusRuntimeProxyFactoryFactoryInitiator;
import io.quarkus.hibernate.orm.runtime.recording.RecordedState;
import io.quarkus.hibernate.orm.runtime.service.CfgXmlAccessServiceInitiatorQuarkus;
import io.quarkus.hibernate.orm.runtime.service.DisabledJMXInitiator;
import io.quarkus.hibernate.orm.runtime.service.FlatClassLoaderService;
import io.quarkus.hibernate.orm.runtime.service.QuarkusRegionFactoryInitiator;
import io.quarkus.hibernate.reactive.runtime.customized.QuarkusNoJdbcEnvironmentInitiator;

import static org.hibernate.internal.HEMLogging.messageLogger;

/**
 * Helps to instantiate a ServiceRegistryBuilder from a previous state. This
 * will perform only minimal configuration validation and will never modify the
 * given configuration properties.
 * <p>
 * Meant to be used only to rebuild a previously created ServiceRegistry, which
 * has been created via the traditional methods, so this builder expects much
 * more explicit input.
 */
public class PreconfiguredReactiveServiceRegistryBuilder {

    private static final EntityManagerMessageLogger LOG = messageLogger(PreconfiguredReactiveServiceRegistryBuilder.class);

    private final Map configurationValues = new HashMap();
    private final List<StandardServiceInitiator> initiators;
    private final List<ProvidedService> providedServices = new ArrayList<ProvidedService>();
    private final Collection<Integrator> integrators;
    private final StandardServiceRegistryImpl destroyedRegistry;

    public PreconfiguredReactiveServiceRegistryBuilder(RecordedState rs) {
        this.initiators = buildQuarkusServiceInitiatorList(rs);
        this.integrators = rs.getIntegrators();
        this.destroyedRegistry = (StandardServiceRegistryImpl) rs.getMetadata().getOriginalMetadata()
                .getMetadataBuildingOptions()
                .getServiceRegistry();
    }

    public PreconfiguredReactiveServiceRegistryBuilder applySetting(String settingName, Object value) {
        configurationValues.put(settingName, value);
        return this;
    }

    public PreconfiguredReactiveServiceRegistryBuilder addInitiator(StandardServiceInitiator initiator) {
        initiators.add(initiator);
        return this;
    }

    public PreconfiguredReactiveServiceRegistryBuilder addService(ProvidedService providedService) {
        providedServices.add(providedService);
        return this;
    }

    public StandardServiceRegistryImpl buildNewServiceRegistry() {
        final BootstrapServiceRegistry bootstrapServiceRegistry = buildEmptyBootstrapServiceRegistry();

        // Can skip, it's only deprecated stuff:
        // applyServiceContributingIntegrators( bootstrapServiceRegistry );

        // This is NOT deprecated stuff.. yet they will at best contribute stuff we
        // already recorded as part of #applyIntegrator, #addInitiator, #addService
        // applyServiceContributors( bootstrapServiceRegistry );

        final Map settingsCopy = new HashMap();
        settingsCopy.putAll(configurationValues);

        destroyedRegistry.resetAndReactivate(bootstrapServiceRegistry, initiators, providedServices, settingsCopy);
        return destroyedRegistry;
    }

    private BootstrapServiceRegistry buildEmptyBootstrapServiceRegistry() {

        // N.B. support for custom IntegratorProvider injected via Properties (as
        // instance) removed

        // N.B. support for custom StrategySelector is not implemented yet

        final StrategySelectorImpl strategySelector = new StrategySelectorImpl(FlatClassLoaderService.INSTANCE);

        return new BootstrapServiceRegistryImpl(true,
                FlatClassLoaderService.INSTANCE,
                strategySelector, // new MirroringStrategySelector(),
                new MirroringIntegratorService(integrators));
    }

    /**
     * Modified copy from
     * org.hibernate.service.StandardServiceInitiators#buildStandardServiceInitiatorList
     *
     * N.B. not to be confused with
     * org.hibernate.service.internal.StandardSessionFactoryServiceInitiators#buildStandardServiceInitiatorList()
     *
     * @return
     */
    private static List<StandardServiceInitiator> buildQuarkusServiceInitiatorList(RecordedState rs) {
        final ArrayList<StandardServiceInitiator> serviceInitiators = new ArrayList<StandardServiceInitiator>();

        //Enforces no bytecode enhancement will happen at runtime:
        serviceInitiators.add(DisabledBytecodeProviderInitiator.INSTANCE);

        //Use a custom ProxyFactoryFactory which is able to use the class definitions we already created:
        serviceInitiators.add(new QuarkusRuntimeProxyFactoryFactoryInitiator(rs));

        // Replaces org.hibernate.boot.cfgxml.internal.CfgXmlAccessServiceInitiator :
        // not used
        // (Original disabled)
        serviceInitiators.add(CfgXmlAccessServiceInitiatorQuarkus.INSTANCE);

        // Useful as-is
        serviceInitiators.add(ConfigurationServiceInitiator.INSTANCE);

        // TODO (optional): assume entities are already enhanced?
        serviceInitiators.add(PropertyAccessStrategyResolverInitiator.INSTANCE);

        // TODO (optional): not a priority
        serviceInitiators.add(ImportSqlCommandExtractorInitiator.INSTANCE);

        // TODO disable?
        serviceInitiators.add(SchemaManagementToolInitiator.INSTANCE);

        // Replaces JdbcEnvironmentInitiator.INSTANCE :
        //serviceInitiators.add(new QuarkusJdbcEnvironmentInitiator(rs.getDialect()));
        serviceInitiators.add(new QuarkusNoJdbcEnvironmentInitiator(rs.getDialect()));

        // Custom one!
        serviceInitiators.add(QuarkusJndiServiceInitiator.INSTANCE);

        // Custom one!
        serviceInitiators.add(DisabledJMXInitiator.INSTANCE);

        //serviceInitiators.add(PersisterClassResolverInitiator.INSTANCE);
        serviceInitiators.add(ReactivePersisterClassResolverInitiator.INSTANCE);
        serviceInitiators.add(PersisterFactoryInitiator.INSTANCE);

        // Custom one!
        // TODO: May check if a JDBC datasource is configured, and if it is then register
        // the real connectionprovider instead of the dummy one
        serviceInitiators.add(NoJdbcConnectionProviderInitiator.INSTANCE);
        //serviceInitiators.add(QuarkusConnectionProviderInitiator.INSTANCE);
        serviceInitiators.add(MultiTenantConnectionProviderInitiator.INSTANCE);

        // Disabled: Dialect is injected explicitly
        // serviceInitiators.add( DialectResolverInitiator.INSTANCE );

        // Disabled: Dialect is injected explicitly
        // serviceInitiators.add( DialectFactoryInitiator.INSTANCE );

        serviceInitiators.add(BatchBuilderInitiator.INSTANCE);
        serviceInitiators.add(JdbcServicesInitiator.INSTANCE);
        serviceInitiators.add(RefCursorSupportInitiator.INSTANCE);

        //serviceInitiators.add(QueryTranslatorFactoryInitiator.INSTANCE);
        serviceInitiators.add(ReactiveQueryTranslatorFactoryInitiator.INSTANCE);

        // Disabled: IdentifierGenerators are no longer initiated after Metadata was generated.
        // serviceInitiators.add(MutableIdentifierGeneratorFactoryInitiator.INSTANCE);

        serviceInitiators.add(QuarkusJtaPlatformInitiator.INSTANCE);

        serviceInitiators.add(SessionFactoryServiceRegistryFactoryInitiator.INSTANCE);

        // Replaces RegionFactoryInitiator.INSTANCE
        serviceInitiators.add(QuarkusRegionFactoryInitiator.INSTANCE);

        serviceInitiators.add(TransactionCoordinatorBuilderInitiator.INSTANCE);

        serviceInitiators.add(ManagedBeanRegistryInitiator.INSTANCE);

        serviceInitiators.add(EntityCopyObserverFactoryInitiator.INSTANCE);

        serviceInitiators.add(ReactiveIdentifierGeneratorFactoryInitiator.INSTANCE);

        serviceInitiators.trimToSize();
        return serviceInitiators;
    }

}
