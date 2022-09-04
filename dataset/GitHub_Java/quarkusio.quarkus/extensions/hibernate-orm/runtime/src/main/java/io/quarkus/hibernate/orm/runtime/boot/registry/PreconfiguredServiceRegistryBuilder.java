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

package io.quarkus.hibernate.orm.runtime.boot.registry;

import static org.hibernate.internal.HEMLogging.messageLogger;

import java.util.ArrayList;
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
import org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator;
import org.hibernate.engine.jdbc.connections.internal.MultiTenantConnectionProviderInitiator;
import org.hibernate.engine.jdbc.cursor.internal.RefCursorSupportInitiator;
import org.hibernate.engine.jdbc.internal.JdbcServicesInitiator;
import org.hibernate.engine.jndi.internal.JndiServiceInitiator;
import org.hibernate.engine.transaction.jta.platform.internal.JBossStandAloneJtaPlatform;
import org.hibernate.engine.transaction.jta.platform.internal.JtaPlatformInitiator;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.hibernate.event.internal.EntityCopyObserverFactoryInitiator;
import org.hibernate.hql.internal.QueryTranslatorFactoryInitiator;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.internal.EntityManagerMessageLogger;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.persister.internal.PersisterClassResolverInitiator;
import org.hibernate.persister.internal.PersisterFactoryInitiator;
import org.hibernate.property.access.internal.PropertyAccessStrategyResolverInitiator;
import org.hibernate.resource.beans.spi.ManagedBeanRegistryInitiator;
import org.hibernate.resource.transaction.internal.TransactionCoordinatorBuilderInitiator;
import org.hibernate.service.Service;
import org.hibernate.service.internal.ProvidedService;
import org.hibernate.service.internal.SessionFactoryServiceRegistryFactoryInitiator;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.tool.hbm2ddl.ImportSqlCommandExtractorInitiator;
import org.hibernate.tool.schema.internal.SchemaManagementToolInitiator;

import io.quarkus.hibernate.orm.runtime.recording.RecordedState;
import io.quarkus.hibernate.orm.runtime.service.CfgXmlAccessServiceInitiatorQuarkus;
import io.quarkus.hibernate.orm.runtime.service.DisabledJMXInitiator;
import io.quarkus.hibernate.orm.runtime.service.FlatClassLoaderService;
import io.quarkus.hibernate.orm.runtime.service.QuarkusJdbcEnvironmentInitiator;
import io.quarkus.hibernate.orm.runtime.service.QuarkusJtaPlatformResolver;
import io.quarkus.hibernate.orm.runtime.service.QuarkusRegionFactoryInitiator;

/**
 * Helps to instantiate a ServiceRegistryBuilder from a previous state. This
 * will perform only minimal configuration validation and will never modify the
 * given configuration properties.
 * <p>
 * Meant to be used only to rebuild a previously created ServiceRegistry, which
 * has been created via the traditional methods, so this builder expects much
 * more explicit input.
 */
public class PreconfiguredServiceRegistryBuilder {

    private static final EntityManagerMessageLogger LOG = messageLogger(PreconfiguredServiceRegistryBuilder.class);

    private final Map configurationValues = new HashMap();
    private final List<StandardServiceInitiator> initiators;
    private final List<ProvidedService> providedServices = new ArrayList<ProvidedService>();
    private final MirroringIntegratorService integrators = new MirroringIntegratorService();
    private final StandardServiceRegistryImpl destroyedRegistry;

    public PreconfiguredServiceRegistryBuilder(RecordedState rs) {
        this.initiators = buildQuarkusServiceInitiatorList(rs);
        this.destroyedRegistry = (StandardServiceRegistryImpl) rs.getMetadata().getMetadataBuildingOptions()
                .getServiceRegistry();
    }

    public PreconfiguredServiceRegistryBuilder applySetting(String settingName, Object value) {
        configurationValues.put(settingName, value);
        return this;
    }

    public PreconfiguredServiceRegistryBuilder applyIntegrator(Integrator integrator) {
        integrators.addIntegrator(integrator);
        return this;
    }

    public PreconfiguredServiceRegistryBuilder addInitiator(StandardServiceInitiator initiator) {
        initiators.add(initiator);
        return this;
    }

    public PreconfiguredServiceRegistryBuilder addService(final Class serviceRole, final Service service) {
        providedServices.add(new ProvidedService(serviceRole, service));
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
        ConfigurationHelper.resolvePlaceHolders(settingsCopy);

        destroyedRegistry.resetAndReactivate(bootstrapServiceRegistry, initiators, providedServices, settingsCopy);
        return destroyedRegistry;

        //		return new StandardServiceRegistryImpl(
        //				true,
        //				bootstrapServiceRegistry,
        //				initiators,
        //				providedServices,
        //				settingsCopy
        //		);
    }

    private BootstrapServiceRegistry buildEmptyBootstrapServiceRegistry() {

        // N.B. support for custom IntegratorProvider injected via Properties (as
        // instance) removed

        // N.B. support for custom StrategySelector is not implemented yet

        final StrategySelectorImpl strategySelector = new StrategySelectorImpl(FlatClassLoaderService.INSTANCE);

        return new BootstrapServiceRegistryImpl(true,
                FlatClassLoaderService.INSTANCE,
                strategySelector, // new MirroringStrategySelector(),
                integrators);
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
        serviceInitiators.add(new QuarkusJdbcEnvironmentInitiator(rs.getDialect()));

        serviceInitiators.add(JndiServiceInitiator.INSTANCE);

        // Custom one!
        serviceInitiators.add(DisabledJMXInitiator.INSTANCE);

        serviceInitiators.add(PersisterClassResolverInitiator.INSTANCE);
        serviceInitiators.add(PersisterFactoryInitiator.INSTANCE);

        serviceInitiators.add(ConnectionProviderInitiator.INSTANCE);
        serviceInitiators.add(MultiTenantConnectionProviderInitiator.INSTANCE);

        // Disabled: Dialect is injected explicitly
        // serviceInitiators.add( DialectResolverInitiator.INSTANCE );

        // Disabled: Dialect is injected explicitly
        // serviceInitiators.add( DialectFactoryInitiator.INSTANCE );

        serviceInitiators.add(BatchBuilderInitiator.INSTANCE);
        serviceInitiators.add(JdbcServicesInitiator.INSTANCE);
        serviceInitiators.add(RefCursorSupportInitiator.INSTANCE);

        serviceInitiators.add(QueryTranslatorFactoryInitiator.INSTANCE);

        // Disabled: IdentifierGenerators are no longer initiated after Metadata was generated.
        // serviceInitiators.add(MutableIdentifierGeneratorFactoryInitiator.INSTANCE);

        // Replaces JtaPlatformResolverInitiator.INSTANCE );
        serviceInitiators.add(new QuarkusJtaPlatformResolver(rs.getJtaPlatform()));
        serviceInitiators.add(new JtaPlatformInitiator() {
            @Override
            protected JtaPlatform getFallbackProvider(Map configurationValues, ServiceRegistryImplementor registry) {
                return new JBossStandAloneJtaPlatform();
            }
        });
        // Disabled:
        // serviceInitiators.add( JtaPlatformInitiator.INSTANCE );

        serviceInitiators.add(SessionFactoryServiceRegistryFactoryInitiator.INSTANCE);

        // Replaces RegionFactoryInitiator.INSTANCE
        serviceInitiators.add(QuarkusRegionFactoryInitiator.INSTANCE);

        serviceInitiators.add(TransactionCoordinatorBuilderInitiator.INSTANCE);

        serviceInitiators.add(ManagedBeanRegistryInitiator.INSTANCE);

        serviceInitiators.add(EntityCopyObserverFactoryInitiator.INSTANCE);

        serviceInitiators.trimToSize();
        return serviceInitiators;
    }

}
