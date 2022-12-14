package com.codahale.dropwizard.hibernate.tests;

import com.sun.jersey.spi.container.ResourceMethodDispatchProvider;
import com.codahale.dropwizard.hibernate.UnitOfWorkResourceMethodDispatchAdapter;
import com.codahale.dropwizard.hibernate.UnitOfWorkResourceMethodDispatchProvider;
import org.hibernate.SessionFactory;
import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class UnitOfWorkResourceMethodDispatchAdapterTest {
    private final SessionFactory sessionFactory = mock(SessionFactory.class);
    private final UnitOfWorkResourceMethodDispatchAdapter adapter =
            new UnitOfWorkResourceMethodDispatchAdapter(sessionFactory);

    @Test
    public void hasASessionFactory() throws Exception {
        assertThat(adapter.getSessionFactory())
                .isEqualTo(sessionFactory);
    }

    @Test
    public void decoratesProviders() throws Exception {
        final ResourceMethodDispatchProvider provider = mock(ResourceMethodDispatchProvider.class);

        final UnitOfWorkResourceMethodDispatchProvider decorator =
                (UnitOfWorkResourceMethodDispatchProvider) adapter.adapt(provider);

        assertThat(decorator.getProvider())
                .isEqualTo(provider);

        assertThat(decorator.getSessionFactory())
                .isEqualTo(sessionFactory);
    }
}
