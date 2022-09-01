/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quarkus.smallrye.reactivemessaging.deployment;

import org.jboss.builder.item.MultiBuildItem;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;

import io.quarkus.arc.processor.BeanInfo;

public final class EmitterBuildItem extends MultiBuildItem {

    private final BeanInfo bean;

    private final FieldInfo field;

    private final String name;

    public EmitterBuildItem(BeanInfo bean, FieldInfo field, String name) {
        this.bean = bean;
        this.field = field;
        this.name = name;
    }

    public BeanInfo getBean() {
        return bean;
    }

    public FieldInfo getField() {
        return field;
    }

    public String getName() {
        return name;
    }

}
