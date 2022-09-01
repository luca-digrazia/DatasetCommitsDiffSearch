package org.hswebframework.web.service.file.simple;

import org.hswebframework.web.dao.file.FileInfoDao;
import org.hswebframework.web.entity.file.FileInfoEntity;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.service.GenericEntityService;
import org.hswebframework.web.service.file.FileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认的服务实现
 *
 * @author hsweb-generator-online
 */
@Service("fileInfoService")
@CacheConfig(cacheNames = "file-info")
public class SimpleFileInfoService extends GenericEntityService<FileInfoEntity, String>
        implements FileInfoService {
    private FileInfoDao fileInfoDao;

    @Override
    protected IDGenerator<String> getIDGenerator() {
        return IDGenerator.MD5;
    }

    @Override
    public FileInfoDao getDao() {
        return fileInfoDao;
    }

    @Autowired
    public void setFileInfoDao(FileInfoDao fileInfoDao) {
        this.fileInfoDao = fileInfoDao;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(key = "'md5:'+#entity.md5"),
            @CacheEvict(key = "'id:'+#entity.id")
    })
    protected int updateByPk(FileInfoEntity entity) {
        return super.updateByPk(entity);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(key = "'md5:'+#target.selectByPk(#id).md5"),
            @CacheEvict(key = "'id:'+#id")
    })
    public int deleteByPk(String id) {
        return super.deleteByPk(id);
    }

    @Override
    @CacheEvict(allEntries = true)
    public int updateByPk(List<FileInfoEntity> data) {
        return super.updateByPk(data);
    }

    @Override
    @Cacheable(key = "'id:'+#id", condition = "#id!=null")
    public FileInfoEntity selectByPk(String id) {
        return super.selectByPk(id);
    }

    @Override
    @Cacheable(key = "'md5:'+#md5", condition = "#md5!=null")
    public FileInfoEntity selectByMd5(String md5) {
        if (null == md5) return null;
        return createQuery().where(FileInfoEntity.md5, md5).single();
    }
}
