/*
 *
 *  * Copyright 2016 http://www.hswebframework.org
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.hswebframework.web.commons.beans;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author zhouhao
 * @since 3.0
 */
public interface GenericBean<PK> extends CloneableBean {
    String id = "id";

    String properties = "properties";

    PK getId();

    void setId(PK id);

    Map<String, Object> getProperties();

    void setProperties(Map<String, Object> properties);

    default <T> T getProperty(String propertyName, T defaultValue) {
        Map<String, Object> map = getProperties();
        if (map == null) return null;
        return (T) map.getOrDefault(propertyName, defaultValue);
    }

    default <T> T getProperty(String propertyName) {
        return getProperty(propertyName, null);
    }

    default void setProperty(String propertyName, Object value) {
        Map<String, Object> map = getProperties();
        if (map == null) {
            map = new LinkedHashMap<>();
            setProperties(map);
        }
        map.put(propertyName, value);
    }

}
