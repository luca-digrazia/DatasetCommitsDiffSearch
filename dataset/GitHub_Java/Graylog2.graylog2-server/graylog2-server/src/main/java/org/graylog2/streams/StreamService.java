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

package org.graylog2.streams;

import org.graylog2.database.NotFoundException;
import org.graylog2.database.PersistedService;
import org.graylog2.database.ValidationException;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.plugin.streams.StreamRule;

import java.util.List;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public interface StreamService extends PersistedService {
    Stream load(String id) throws NotFoundException;
    List<Stream> loadAll();
    List<Stream> loadAllEnabled();
    void pause(Stream stream);
    void resume(Stream stream);
    List<StreamRule> getStreamRules(Stream stream) throws NotFoundException;
    List<Stream> loadAllWithConfiguredAlertConditions();

    List<AlertCondition> getAlertConditions(Stream stream);
    void addAlertCondition(Stream stream, AlertCondition condition) throws ValidationException;

    void removeAlertCondition(Stream stream, String conditionId);

    void addAlertReceiver(Stream stream, String type, String name);
    void removeAlertReceiver(Stream stream, String type, String name);
}
