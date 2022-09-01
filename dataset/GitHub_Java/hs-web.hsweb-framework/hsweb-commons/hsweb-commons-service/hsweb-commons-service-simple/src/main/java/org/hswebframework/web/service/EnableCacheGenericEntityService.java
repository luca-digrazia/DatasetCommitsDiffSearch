package org.hswebframework.web.service;

import org.hswebframework.web.commons.entity.GenericEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

/**
 * 启用缓冲的通用实体曾删改查服务,继承此类
 * 在类上注解{@link org.springframework.cache.annotation.CacheConfig}即可
 *
 * @author zhouhao
 * @see org.springframework.cache.annotation.CacheConfig
 * @see Cacheable
 * @see CacheEvict
 * @since 3.0
 */
public abstract class EnableCacheGenericEntityService<E extends GenericEntity<PK>, PK> extends GenericEntityService<E, PK> {

    @Override
    @Cacheable(key = "'ids:'+#id.hashCode()", condition = "#id!=null")
    public List<E> selectByPk(List<PK> id) {
        return super.selectByPk(id);
    }

    @Override
    @Cacheable(key = "'id:'+#pk")
    public E selectByPk(PK pk) {
        return super.selectByPk(pk);
    }

    @Override
    @CacheEvict(allEntries = true)
    public int updateByPk(List<E> data) {
        return super.updateByPk(data);
    }

    @Override
    @CacheEvict(allEntries = true)
    public int updateByPk(PK pk, E entity) {
        return super.updateByPk(pk, entity);
    }

    @Override
    @CacheEvict(allEntries = true)
    protected int updateByPk(E entity) {
        return super.updateByPk(entity);
    }

    @Override
    @CacheEvict(allEntries = true)
    public PK insert(E entity) {
        return super.insert(entity);
    }

    @Override
    @CacheEvict(allEntries = true)
    public int deleteByPk(PK pk) {
        return super.deleteByPk(pk);
    }

    @Override
    @CacheEvict(allEntries = true)
    public PK saveOrUpdate(E entity) {
        return super.saveOrUpdate(entity);
    }

}
