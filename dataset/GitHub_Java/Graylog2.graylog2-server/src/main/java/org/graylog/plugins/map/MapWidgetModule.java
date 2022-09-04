package org.graylog.plugins.map;

import com.google.common.collect.Sets;
import org.graylog.plugins.map.config.MapWidgetConfiguration;
import org.graylog.plugins.map.geoip.filter.GeoIpResolverFilter;
import org.graylog.plugins.map.rest.MapDataResource;
import org.graylog.plugins.map.widget.strategy.MapWidgetStrategy;
import org.graylog2.plugin.PluginConfigBean;
import org.graylog2.plugin.PluginModule;

import java.util.Set;

public class MapWidgetModule extends PluginModule {

    @Override
    public Set<? extends PluginConfigBean> getConfigBeans() {
        return Sets.newHashSet(new MapWidgetConfiguration());
    }

    @Override
    protected void configure() {
        addMessageFilter(GeoIpResolverFilter.class);
        addWidgetStrategy(MapWidgetStrategy.class, MapWidgetStrategy.Factory.class);
        addRestResource(MapDataResource.class);
        addConfigBeans();
    }
}
