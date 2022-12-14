/*
 * Copyright 2016 http://www.hswebframework.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.hswebframework.web.authorization;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
public interface Authorization extends Serializable {
    User getUser();

    List<Role> getRoles();

    List<Permission> getPermissions();

    <T extends Serializable> T getAttribute(String name);

    <T extends Serializable> T getAttribute(String name, T defaultValue);

    <T extends Serializable> T getAttribute(String name, Supplier<T> supplier);

    void setAttribute(String name, Serializable object);

    void setAttributes(Map<String, Serializable> attributes);

    Map<String, Serializable> getAttributes();

}
