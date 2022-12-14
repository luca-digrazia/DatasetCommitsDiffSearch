package io.quarkus.hibernate.orm.panache.runtime;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

public class AdditionalJpaOperations {

    @SuppressWarnings("rawtypes")
    public static PanacheQuery<?> find(Class<?> entityClass, String query, String countQuery, Sort sort,
            Map<String, Object> params) {
        String findQuery = JpaOperations.createFindQuery(entityClass, query, JpaOperations.paramCount(params));
        EntityManager em = JpaOperations.getEntityManager();
        Query jpaQuery = em.createQuery(sort != null ? findQuery + JpaOperations.toOrderBy(sort) : findQuery);
        JpaOperations.bindParameters(jpaQuery, params);
        return new CustomCountPanacheQuery(em, jpaQuery, findQuery, countQuery, params);
        //        return new PanacheQueryImpl(em, jpaQuery, findQuery, params);
    }

    @SuppressWarnings("rawtypes")
    public static PanacheQuery<?> find(Class<?> entityClass, String query, String countQuery, Sort sort,
            Parameters parameters) {
        return find(entityClass, query, countQuery, sort, parameters.map());
    }

    @SuppressWarnings("rawtypes")
    public static PanacheQuery<?> find(Class<?> entityClass, String query, String countQuery, Sort sort, Object... params) {
        String findQuery = JpaOperations.createFindQuery(entityClass, query, JpaOperations.paramCount(params));
        EntityManager em = JpaOperations.getEntityManager();
        Query jpaQuery = em.createQuery(sort != null ? findQuery + JpaOperations.toOrderBy(sort) : findQuery);
        JpaOperations.bindParameters(jpaQuery, params);
        return new CustomCountPanacheQuery(em, jpaQuery, findQuery, countQuery, params);
        //        return new PanacheQueryImpl(em, jpaQuery, findQuery, params);
    }
}
