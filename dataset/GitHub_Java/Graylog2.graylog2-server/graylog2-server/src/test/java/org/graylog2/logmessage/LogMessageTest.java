/**
 * Copyright 2012 Lennart Koopmann <lennart@socketfeed.com>
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
 *
 */

package org.graylog2.logmessage;

import org.graylog2.plugin.Message;
import com.beust.jcommander.internal.Maps;
import java.util.Map;
import java.util.HashMap;

import org.joda.time.DateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class LogMessageTest {

    @Test
    public void testIdGetsSet() {
        Message lm = new Message("foo", "bar", new DateTime());
        assertNotNull(lm.getId());
        assertFalse(lm.getId().isEmpty());
    }

    @Test
    public void testIsCompleteSucceeds() {
        Message lm = new Message("foo", "bar", new DateTime());
        assertTrue(lm.isComplete());
    }

    @Test
    public void testIsCompleteFails() {
        Message lm = new Message("foo", null, new DateTime());
        assertFalse(lm.isComplete());

        lm = new Message("foo", "", new DateTime());
        assertFalse(lm.isComplete());

        lm = new Message(null, "bar", new DateTime());
        assertFalse(lm.isComplete());

        lm = new Message("", "bar", new DateTime());
        assertFalse(lm.isComplete());

        lm = new Message("", "", new DateTime());
        assertFalse(lm.isComplete());

        lm = new Message(null, null, new DateTime());
        assertFalse(lm.isComplete());
    }

    @Test
    public void testAddField() {
        Message lm = new Message("foo", "bar", new DateTime());
        lm.addField("ohai", "thar");
        assertEquals("thar", lm.getField("ohai"));
    }

    @Test
    public void testAddFieldsWithMap() {
        Message lm = new Message("foo", "bar", new DateTime());
        lm.addField("ohai", "hai");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("lol", "wut");
        map.put("aha", "pipes");

        lm.addFields(map);
        assertEquals(7, lm.getFields().size());
        assertEquals("wut", lm.getField("lol"));
        assertEquals("pipes", lm.getField("aha"));
        assertEquals("hai", lm.getField("ohai"));
    }

    @Test
    public void testRemoveField() {
        Message lm = new Message("foo", "bar", new DateTime());
        lm.addField("something", "foo");
        lm.addField("something_else", "bar");

        lm.removeField("something_else");

        assertEquals(5, lm.getFields().size());
        assertEquals("foo", lm.getField("something"));
    }

    @Test
    public void testRemoveFieldWithNonExistentKey() {
        Message lm = new Message("foo", "bar", new DateTime());
        lm.addField("something", "foo");
        lm.addField("something_else", "bar");

        lm.removeField("LOLIDONTEXIST");

        assertEquals(6, lm.getFields().size());
    }
    
    @Test
    public void testRemoveFieldDoesNotDeleteReservedFields() {
        DateTime time = new DateTime();
        Message lm = new Message("foo", "bar", time);
        lm.removeField("source");
        lm.removeField("timestamp");
        lm.removeField("_id");

        assertTrue(lm.isComplete());
        assertEquals("foo", lm.getField("message"));
        assertEquals("bar", lm.getField("source"));
        assertEquals(time, lm.getField("timestamp"));
        assertEquals(4, lm.getFields().size());
    }

    @Test
    public void testToString() {
        Message lm = new Message("foo", "bar", new DateTime());
        lm.toString();
        // Fine if it does not crash.
    }

}