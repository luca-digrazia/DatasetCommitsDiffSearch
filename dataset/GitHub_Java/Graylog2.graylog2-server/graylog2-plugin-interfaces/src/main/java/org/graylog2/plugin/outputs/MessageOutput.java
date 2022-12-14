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

package org.graylog2.plugin.outputs;

import org.graylog2.plugin.Message;

import java.util.List;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public interface MessageOutput {

    public void initialize(Map<String, String> config) throws MessageOutputConfigurationException;
    public void write(List<Message> messages, OutputStreamConfiguration streamConfiguration) throws Exception;
    public Map<String, String> getRequestedConfiguration();
    public Map<String, String> getRequestedStreamConfiguration();
    public String getName();
    
}
