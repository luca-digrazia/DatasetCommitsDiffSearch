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
package org.graylog2.utilities;

import com.beust.jcommander.internal.Lists;
import org.graylog2.inputs.Input;
import org.graylog2.inputs.InputService;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.streams.StreamService;
import org.joda.time.DateTime;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class MessageToJsonSerializerTest {
    @Mock private StreamService streamService;
    @Mock private InputService inputService;
    @Mock private MessageInput messageInput;
    @Mock private Input input;
    @Mock private Stream stream;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(stream.getId()).thenReturn("stream-id");
        when(messageInput.getId()).thenReturn("input-id");
        when(inputService.buildMessageInput(input)).thenReturn(messageInput);
        when(inputService.find("input-id")).thenReturn(input);
        when(streamService.load("stream-id")).thenReturn(stream);
    }

    @Test
    public void shouldSerializeMessageCorrectly() throws Exception {
        final MessageToJsonSerializer serializer = new MessageToJsonSerializer(streamService, inputService);
        final DateTime now = DateTime.now();
        final Message message = new Message("test", "localhost", now);

        message.setSourceInput(messageInput);
        message.setStreams(Lists.newArrayList(stream));

        final String s = serializer.serializeToString(message);

        final Message newMessage = serializer.deserialize(s);

        assertEquals(newMessage.getField("timestamp"), now);
        assertEquals(newMessage.getMessage(), message.getMessage());
        assertEquals(newMessage.getSource(), message.getSource());
        assertEquals(newMessage.getSourceInput(), messageInput);
        assertEquals(newMessage.getStreams(), Lists.newArrayList(stream));

        // Just assert that the message id is not null because we cannot set the _id field on deserialize because the
        // Message object does not allow the _id field to be set.
        assertNotNull(newMessage.getId());
    }
}