package org.hswebframework.web.datasource;

import org.hswebframework.web.datasource.exception.DataSourceNotFoundException;
import org.hswebframework.web.datasource.switcher.DataSourceSwitcher;

/**
 * 用于操作动态数据源,如获取当前使用的数据源,使用switcher切换数据源等
 *
 * @author zhouhao
 * @see 3.0
 */
public final class DataSourceHolder {

    /**
     * 动态数据源切换器
     */
    static DataSourceSwitcher dataSourceSwitcher;

    /**
     * 动态数据源服务
     */
    static DynamicDataSourceService dynamicDataSourceService;

    public static void checkDynamicDataSourceReady() {
        if (dynamicDataSourceService == null) throw new UnsupportedOperationException("dynamicDataSourceService not ready");
    }

    /**
     * @return 动态数据源切换器
     */
    public static DataSourceSwitcher switcher() {
        return dataSourceSwitcher;
    }

    /**
     * @return 默认数据源
     */
    public static DynamicDataSource defaultDataSource() {
        return dynamicDataSourceService.getDefaultDataSource();
    }

    /**
     * @return 当前使用的数据源
     */
    public static DynamicDataSource currentDataSource() {
        String id = dataSourceSwitcher.currentDataSourceId();
        if (id == null) return defaultDataSource();
        checkDynamicDataSourceReady();
        return dynamicDataSourceService.getDataSource(id);
    }

    /**
     * @return 当前使用的数据源是否为默认数据源
     */
    public static boolean currentIsDefault() {
        return dataSourceSwitcher.currentDataSourceId() == null;
    }

    /**
     * 判断指定id的数据源是否存在
     *
     * @param id 数据源id {@link DynamicDataSource#getId()}
     * @return 数据源是否存在
     */
    public static boolean existing(String id) {
        try {
            checkDynamicDataSourceReady();
            return dynamicDataSourceService.getDataSource(id) != null;
        } catch (DataSourceNotFoundException e) {
            return false;
        }
    }

    /**
     * @return 当前使用的数据源是否存在
     */
    public static boolean currentExisting() {
        if (currentIsDefault()) return true;
        try {
            return currentDataSource() != null;
        } catch (DataSourceNotFoundException e) {
            return false;
        }
    }

    /**
     * @return 当前数据库类型
     */
    public static DatabaseType currentDatabaseType() {
        return currentDataSource().getType();
    }

    /**
     * @return 默认的数据库类型
     */
    public static DatabaseType defaultDatabaseType() {
        return defaultDataSource().getType();
    }
}
