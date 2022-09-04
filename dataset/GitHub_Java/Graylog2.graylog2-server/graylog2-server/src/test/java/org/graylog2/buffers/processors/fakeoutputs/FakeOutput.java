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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graylog2.buffers.processors.fakeoutputs;

import com.google.common.collect.Maps;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.outputs.OutputStreamConfiguration;

import java.util.List;
import java.util.Map;

/**
 *
 * @author lennart.koopmann
 */
public class FakeOutput implements MessageOutput {

    private int callCount = 0;
    private int writeCount = 0;
    
    @Override
    public void initialize(Map<String, String> config) throws MessageOutputConfigurationException {
    }

    @Override
    public void write(List<Message> messages, OutputStreamConfiguration streamConfiguration) throws Exception {
        this.callCount++;
        this.writeCount += messages.size();
    }

    @Override
    public Map<String, String> getRequestedConfiguration() {
        return Maps.newHashMap();
    }

    @Override
    public Map<String, String> getRequestedStreamConfiguration() {
        return Maps.newHashMap();
    }

    @Override
    public String getName() {
        return "FAKE OUTPUT";
    }
    
    public int getCallCount() {
        return callCount;
    }
    
    public int getWriteCount() {
        return writeCount;
    }
    
}
