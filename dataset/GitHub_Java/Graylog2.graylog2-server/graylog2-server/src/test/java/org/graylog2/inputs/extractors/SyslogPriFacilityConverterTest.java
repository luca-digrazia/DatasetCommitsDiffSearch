/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
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
package org.graylog2.inputs.extractors;

import org.graylog2.plugin.inputs.Converter;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class SyslogPriFacilityConverterTest {

    @Test
    public void testConvert() throws Exception {
        Converter hc = new SyslogPriFacilityConverter(new HashMap<String, Object>());
        assertNull(hc.convert(null));
        assertEquals("", hc.convert(""));
        assertEquals("lol no number", hc.convert("lol no number"));

        assertEquals("user-level", hc.convert("14")); // user-level
        assertEquals("kernel", hc.convert("5")); // kernel
        assertEquals("security/authorization", hc.convert("87")); // security/authorization
    }

}
