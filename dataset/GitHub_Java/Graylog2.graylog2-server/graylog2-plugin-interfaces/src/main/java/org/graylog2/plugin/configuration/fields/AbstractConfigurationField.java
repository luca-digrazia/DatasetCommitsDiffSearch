/*
 * Copyright 2012-2014 TORCH GmbH
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graylog2.plugin.configuration.fields;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class AbstractConfigurationField {
    protected final String field_type;
    protected final String name;
    protected final String humanName;
    protected final String description;
    protected final ConfigurationField.Optional optional;

    public AbstractConfigurationField(String field_type, String name, String humanName, String description, ConfigurationField.Optional optional1) {
        this.field_type = field_type;
        this.name = name;
        this.humanName = humanName;
        this.description = description;
        this.optional = optional1;
    }

    public String getFieldType() {
        return field_type;
    }

    public ConfigurationField.Optional isOptional() {
        return optional;
    }

    public String getName() {
        return name;
    }

    public String getHumanName() {
        return humanName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAttributes() {
        return Lists.newArrayList();
    }

    public Map<String, Map<String, String>> getAdditionalInformation() {
        return Maps.newHashMap();
    }
}
