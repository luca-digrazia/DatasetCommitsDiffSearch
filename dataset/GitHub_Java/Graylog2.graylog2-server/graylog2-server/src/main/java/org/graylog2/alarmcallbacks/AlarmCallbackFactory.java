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

package org.graylog2.alarmcallbacks;

import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackConfigurationException;
import org.graylog2.shared.bindings.InstantiationService;

import javax.inject.Inject;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class AlarmCallbackFactory {
    private InstantiationService instantiationService;

    @Inject
    public AlarmCallbackFactory(InstantiationService instantiationService) {
        this.instantiationService = instantiationService;
    }

    public AlarmCallback create(AlarmCallbackConfiguration configuration) throws ClassNotFoundException, AlarmCallbackConfigurationException {
        AlarmCallback alarmCallback = create(configuration.getType());
        alarmCallback.initialize(configuration.getConfiguration());

        return alarmCallback;
    }

    public AlarmCallback create(String type) throws ClassNotFoundException {
        Class<? extends AlarmCallback> alarmCallbackClass = (Class<? extends AlarmCallback>)Class.forName(type);
        return create(alarmCallbackClass);
    }

    public AlarmCallback create(Class<? extends AlarmCallback> alarmCallbackClass) {
        return instantiationService.getInstance(alarmCallbackClass);
    }
}
