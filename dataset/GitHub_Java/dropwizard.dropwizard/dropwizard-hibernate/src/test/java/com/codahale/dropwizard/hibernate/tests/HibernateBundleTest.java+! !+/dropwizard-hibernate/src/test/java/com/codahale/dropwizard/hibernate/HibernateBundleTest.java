package com.codahale.dropwizard.hibernate.tests;

import com.codahale.dropwizard.config.Bootstrap;
import com.codahale.dropwizard.Configuration;
import com.codahale.dropwizard.config.Environment;
import com.codahale.dropwizard.db.DatabaseConfiguration;
import com.codahale.dropwizard.hibernate.HibernateBundle;
import com.codahale.dropwizard.hibernate.SessionFactoryFactory;
import com.codahale.dropwizard.hibernate.SessionFactoryHealthCheck;
import com.codahale.dropwizard.hibernate.UnitOfWorkResourceMethodDispatchAdapter;
import com.codahale.dropwizard.setup.AdminEnvironment;
import com.codahale.dropwizard.setup.JerseyEnvironment;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate4.Hibernate4Module;
import com.google.common.collect.ImmutableList;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class HibernateBundleTest {
    private final DatabaseConfiguration dbConfig = new DatabaseConfiguration();
    private final ImmutableList<Class<?>> entities = ImmutableList.<Class<?>>of(Person.class);
    private final SessionFactoryFactory factory = mock(SessionFactoryFactory.class);
    private final SessionFactory sessionFactory = mock(SessionFactory.class);
    private final Configuration configuration = mock(Configuration.class);
    private final AdminEnvironment adminEnvironment = mock(AdminEnvironment.class);
    private final JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    private final Environment environment = mock(Environment.class);
    private final HibernateBundle<Configuration> bundle = new HibernateBundle<Configuration>(entities, factory) {
        @Override
        public DatabaseConfiguration getDatabaseConfiguration(Configuration configuration) {
            return dbConfig;
        }
    };

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        when(environment.getAdminEnvironment()).thenReturn(adminEnvironment);
        when(environment.getJerseyEnvironment()).thenReturn(jerseyEnvironment);

        when(factory.build(eq(bundle),
                           any(Environment.class),
                           any(DatabaseConfiguration.class),
                           anyList())).thenReturn(sessionFactory);
    }

    @Test
    public void addsHibernateSupportToJackson() throws Exception {
        final ObjectMapper objectMapperFactory = mock(ObjectMapper.class);

        final Bootstrap<?> bootstrap = mock(Bootstrap.class);
        when(bootstrap.getObjectMapper()).thenReturn(objectMapperFactory);

        bundle.initialize(bootstrap);

        final ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
        verify(objectMapperFactory).registerModule(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(Hibernate4Module.class);
    }

    @Test
    public void buildsASessionFactory() throws Exception {
        bundle.run(configuration, environment);

        verify(factory).build(bundle, environment, dbConfig, entities);
    }

    @Test
    public void registersATransactionalAdapter() throws Exception {
        bundle.run(configuration, environment);

        final ArgumentCaptor<UnitOfWorkResourceMethodDispatchAdapter> captor =
                ArgumentCaptor.forClass(UnitOfWorkResourceMethodDispatchAdapter.class);
        verify(jerseyEnvironment).addProvider(captor.capture());

        assertThat(captor.getValue().getSessionFactory()).isEqualTo(sessionFactory);
    }

    @Test
    public void registersASessionFactoryHealthCheck() throws Exception {
        dbConfig.setValidationQuery("SELECT something");

        bundle.run(configuration, environment);

        final ArgumentCaptor<SessionFactoryHealthCheck> captor =
                ArgumentCaptor.forClass(SessionFactoryHealthCheck.class);
        verify(adminEnvironment).addHealthCheck(eq("hibernate"), captor.capture());

        assertThat(captor.getValue().getSessionFactory()).isEqualTo(sessionFactory);

        assertThat(captor.getValue().getValidationQuery()).isEqualTo("SELECT something");
    }

    @Test
    public void hasASessionFactory() throws Exception {
        bundle.run(configuration, environment);

        assertThat(bundle.getSessionFactory()).isEqualTo(sessionFactory);
    }
}
