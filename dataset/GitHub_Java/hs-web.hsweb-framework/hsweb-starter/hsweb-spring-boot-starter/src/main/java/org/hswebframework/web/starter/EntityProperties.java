/*
 *  Copyright 2016 http://www.hswebframework.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.hswebframework.web.starter;

import org.hswebframework.web.commons.entity.Entity;
import org.hswebframework.web.commons.entity.factory.MapperEntityFactory;
import org.hswebframwork.utils.MapUtils;
import org.hswebframwork.utils.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.ClassUtils;

import java.lang.instrument.IllegalClassFormatException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO 完成注释
 * <p>
 * <pre>
 *    hsweb:
 *      entity:
 *         mapping:
 *             org.hswebframework.web.entity.user.UserEntity:com.company.entity.user.CustomUserEntity
 * </pre>
 *
 * @author zhouhao
 */
@ConfigurationProperties(prefix = "hsweb.entity")
public class EntityProperties {
    List<Mapping> mappings;

    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public List<Mapping> getMappings() {
        return mappings;
    }

    public Map<Class<Entity>, MapperEntityFactory.Mapper> createMappers() {
        if (mappings == null || mappings.isEmpty()) return Collections.emptyMap();
        return mappings.stream()
                .map(Mapping::create)
                .reduce(MapUtils::merge)
                .get();
    }

    public static class Mapping {
        String sourceBasePackage = "";
        String targetBasePackage = "";
        Map<String, String> mapping;

        Map<Class<Entity>, MapperEntityFactory.Mapper> create() {
            if (mapping == null || mapping.isEmpty()) return Collections.emptyMap();
            return mapping.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> getSourceClass(entry.getKey()),
                            entry -> MapperEntityFactory.defaultMapper(getTargetClass(entry.getValue()))));
        }

        protected Class<Entity> getClass(String basePackage, String name) {
            if (!StringUtils.isNullOrEmpty(basePackage))
                name = basePackage.concat(".").concat(name);
            return classForName(name);
        }

        protected Class<Entity> getSourceClass(String name) {
            return getClass(sourceBasePackage, name);
        }

        protected Class<Entity> getTargetClass(String name) {
            Class<Entity> entityClass = getClass(targetBasePackage, name);
            if (entityClass.isInterface()) {
                throw new RuntimeException("class " + name + " is interface!");
            }
            return entityClass;
        }

        @SuppressWarnings("unchecked")
        public Class<Entity> classForName(String name) {
            try {
                Class target = ClassUtils.forName(name, this.getClass().getClassLoader());
                return target;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public String getSourceBasePackage() {
            return sourceBasePackage;
        }

        public void setSourceBasePackage(String sourceBasePackage) {
            this.sourceBasePackage = sourceBasePackage;
        }

        public String getTargetBasePackage() {
            return targetBasePackage;
        }

        public void setTargetBasePackage(String targetBasePackage) {
            this.targetBasePackage = targetBasePackage;
        }

        public Map<String, String> getMapping() {
            return mapping;
        }

        public void setMapping(Map<String, String> mapping) {
            this.mapping = mapping;
        }
    }
}
