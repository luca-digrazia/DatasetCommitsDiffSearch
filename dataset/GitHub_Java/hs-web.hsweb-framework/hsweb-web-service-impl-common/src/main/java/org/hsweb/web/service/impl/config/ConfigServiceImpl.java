package org.hsweb.web.service.impl.config;

import org.hsweb.web.bean.po.config.Config;
import org.hsweb.web.dao.config.ConfigMapper;
import org.hsweb.web.service.config.ConfigService;
import org.hsweb.web.service.impl.AbstractServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.webbuilder.utils.common.StringUtils;

import javax.annotation.Resource;
import java.util.Properties;

/**
 * 系统配置服务类
 * Created by generator
 */
@Service("configService")
public class ConfigServiceImpl extends AbstractServiceImpl<Config, String> implements ConfigService {

    public static final String CACHE_KEY = "config";

    //默认数据映射接口
    @Resource
    protected ConfigMapper configMapper;

    @Override
    protected ConfigMapper getMapper() {
        return this.configMapper;
    }

    @Override
    @CacheEvict(value = CACHE_KEY, allEntries = true)
    public int update(Config data) throws Exception {
        return super.update(data);
    }

    /**
     * 根据配置名称，获取配置内容
     *
     * @param name 配置名称
     * @return 配置内容
     * @throws Exception 异常信息
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.content_'+#name")
    public String getContent(String name) throws Exception {
        Config config = getMapper().selectByPk(name);
        if (config == null) return null;
        return config.getContent();
    }

    /**
     * 根据配置名称，获取配置内容，并解析为Properties格式
     *
     * @param name 配置名称
     * @return 配置内容
     * @throws Exception 异常信息
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name")
    public Properties get(String name) throws Exception {
        Config config = getMapper().selectByPk(name);
        if (config == null) return new Properties();
        return config.toMap();
    }

    /**
     * 获取配置中指定key的值
     *
     * @param name 配置名称
     * @param key  key 异常信息
     * @return 指定的key对应的value
     * @throws Exception
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key")
    public String get(String name, String key) throws Exception {
        return get(name).getProperty(key);
    }

    /**
     * 获取配置中指定key的值，并指定一个默认值，如果对应的key未获取到，则返回默认值
     *
     * @param name         配置名称
     * @param key          key 异常信息
     * @param defaultValue 默认值
     * @return 对应key的值，若为null，则返回默认值
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key")
    public String get(String name, String key, String defaultValue) {
        String val;
        try {
            val = this.get(name).getProperty(key);
            if (val == null) {
                logger.error("获取配置:{}.{}失败,defaultValue:{}", name, key, defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            logger.error("获取配置:{}.{}失败,defaultValue:{}", name, key, defaultValue, e);
            return defaultValue;
        }
        return val;
    }


    /**
     * 参照 {@link ConfigService#get(String, String)}，将值转为int类型
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key+'.int'")
    public int getInt(String name, String key) throws Exception {
        return StringUtils.toInt(get(name, key));
    }

    /**
     * 参照 {@link ConfigService#get(String, String)}，将值转为double类型
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key+'.double'")
    public double getDouble(String name, String key) throws Exception {
        return StringUtils.toDouble(get(name, key));
    }

    /**
     * 参照 {@link ConfigService#get(String, String)}，将值转为long类型
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key+'.long'")
    public long getLong(String name, String key) throws Exception {
        return StringUtils.toLong(get(name, key));
    }

    /**
     * 参照 {@link ConfigService#get(String, String, String)}，将值转为int类型
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key+'.int'")
    public int getInt(String name, String key, int defaultValue) {
        return StringUtils.toInt(get(name, key, String.valueOf(defaultValue)));
    }

    /**
     * 参照 {@link ConfigService#get(String, String, String)}，将值转为double类型
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key+'.double'")
    public double getDouble(String name, String key, double defaultValue) {

        return StringUtils.toDouble(get(name, key, String.valueOf(defaultValue)));
    }

    /**
     * 参照 {@link ConfigService#get(String, String, String)}，将值转为long类型
     */
    @Override
    @Cacheable(value = CACHE_KEY, key = "'info.'+#name+'.key.'+#key+'.long'")
    public long getLong(String name, String key, long defaultValue) {
        return StringUtils.toLong(get(name, key, String.valueOf(defaultValue)));
    }


}
