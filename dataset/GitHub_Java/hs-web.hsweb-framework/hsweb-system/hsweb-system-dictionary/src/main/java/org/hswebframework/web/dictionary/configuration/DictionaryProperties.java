package org.hswebframework.web.dictionary.configuration;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hswebframework.web.dict.EnumDict;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "hsweb.dict")
@Getter
@Setter
public class DictionaryProperties {

    private Set<String> enumPackages=new HashSet<>();

    @SneakyThrows
    public List<Class> doScanEnum(){
        Set<String> packages = new HashSet<>(enumPackages);
        packages.add("org.hswebframework.web");
        CachingMetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        List<Class> classes = new ArrayList<>();
        for (String enumPackage : packages) {
            String path = "classpath*:"+ ClassUtils.convertClassNameToResourcePath(enumPackage)+"/**/*.class";
            Resource[] resources = resourcePatternResolver.getResources(path);
            for (Resource resource : resources) {
                MetadataReader reader = metadataReaderFactory.getMetadataReader(resource);
                Class clazz=Class.forName(reader.getClassMetadata().getClassName());
                if(clazz.isEnum()&& EnumDict.class.isAssignableFrom(clazz)){
                    classes.add(clazz);
                }
            }
        }
        metadataReaderFactory.clearCache();
        return classes;
    }
}
