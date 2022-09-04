package io.dropwizard.testing.junit;

import io.dropwizard.testing.app.TestEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Rule;
import org.junit.Test;

import javax.validation.ConstraintViolationException;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DAOTestRuleTest {
    @SuppressWarnings("deprecation")
    @Rule
    public final DAOTestRule daoTestRule = DAOTestRule.newBuilder().addEntityClass(TestEntity.class).build();

    @Test
    void ruleCreatedSessionFactory() {
        final SessionFactory sessionFactory = daoTestRule.getSessionFactory();

        assertThat(sessionFactory).isNotNull();
    }

    @Test
    void ruleCanOpenTransaction() {
        final Long id = daoTestRule.inTransaction(() -> persist(new TestEntity("description")).getId());

        assertThat(id).isNotNull();
    }

    @Test
    void ruleCanRoundtrip() {
        final Long id = daoTestRule.inTransaction(() -> persist(new TestEntity("description")).getId());

        final TestEntity testEntity = get(id);

        assertThat(testEntity).isNotNull();
        assertThat(testEntity.getDescription()).isEqualTo("description");
    }

    @Test
    void transactionThrowsExceptionAsExpected() {
        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(()->
            daoTestRule.inTransaction(() -> persist(new TestEntity(null))));
    }

    @Test
    void rollsBackTransaction() {
        // given a successfully persisted entity
        final TestEntity testEntity = new TestEntity("description");
        daoTestRule.inTransaction(() -> persist(testEntity));

        // when we prepare an update of that entity
        testEntity.setDescription("newDescription");
        // ... but cause a constraint violation during the actual update
        assertThatExceptionOfType(ConstraintViolationException.class)
            .isThrownBy(() -> daoTestRule.inTransaction(() -> {
                persist(testEntity);
                persist(new TestEntity(null));
            }));
        // ... the entity has the original value
        assertThat(get(testEntity.getId()).getDescription()).isEqualTo("description");
    }


    private TestEntity persist(TestEntity testEntity) {
        final Session currentSession = daoTestRule.getSessionFactory().getCurrentSession();
        currentSession.saveOrUpdate(testEntity);

        return testEntity;
    }

    private TestEntity get(Serializable id) {
        final Session currentSession = daoTestRule.getSessionFactory().getCurrentSession();
        final TestEntity testEntity = currentSession.get(TestEntity.class, id);
        currentSession.refresh(testEntity);

        return testEntity;
    }
}
