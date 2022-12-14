/**
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
package org.graylog2.radio.inputs;

import com.google.common.collect.Lists;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.radio.cluster.InputService;
import org.graylog2.radio.inputs.api.InputSummaryResponse;
import org.graylog2.shared.inputs.MessageInputFactory;
import org.graylog2.shared.inputs.NoSuchInputTypeException;
import org.graylog2.shared.inputs.PersistedInputs;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class PersistedInputsImpl implements PersistedInputs {
    private static final Logger LOG = LoggerFactory.getLogger(PersistedInputsImpl.class);
    private final InputService inputService;
    private final MessageInputFactory messageInputFactory;

    @Inject
    public PersistedInputsImpl(InputService inputService, MessageInputFactory messageInputFactory) {
        this.inputService = inputService;
        this.messageInputFactory = messageInputFactory;
    }

    private MessageInput getMessageInput(InputSummaryResponse isr) {
        MessageInput input;
        try {
            Configuration inputConfig = new Configuration(isr.configuration);
            input = this.messageInputFactory.create(isr.type, inputConfig);

            // Add all standard fields.
            input.setTitle(isr.title);
            input.setCreatorUserId(isr.creatorUserId);
            input.setPersistId(isr.id);
            input.setCreatedAt(new DateTime(isr.createdAt, DateTimeZone.UTC));
            input.setGlobal(isr.global);
            input.setConfiguration(inputConfig);

            input.checkConfiguration();
        } catch (NoSuchInputTypeException e) {
            LOG.warn("Cannot launch persisted input. No such type [{}]. Error: {}", isr.type, e);
            return null;
        } catch (ConfigurationException e) {
            LOG.error("Missing or invalid input input configuration.", e);
            return null;
        }
        return input;
    }

    // TODO make this use a generic ApiClient class that knows the graylog2-server node address(es) or something.
    @Override
    public Iterator<MessageInput> iterator() {
        final List<MessageInput> result = Lists.newArrayList();

        final List<InputSummaryResponse> response;
        try {
            response = inputService.getPersistedInputs();
        } catch (IOException e) {
            LOG.error("Unable to get persisted inputs: ", e);
            return result.iterator();
        }

        for (InputSummaryResponse isr : response) {
            final MessageInput messageInput = getMessageInput(isr);
            if (messageInput != null) {
                LOG.debug("Loaded message input {}", messageInput);
                result.add(messageInput);
            }
        }

        return result.iterator();
    }
}
