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
package org.graylog2.database.validators;

import org.graylog2.plugin.database.validators.Validator;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class DateValidatorTest {

    @Test
    public void testValidate() throws Exception {
        Validator v = new DateValidator();

        assertFalse(v.validate(null).passed());
        assertFalse(v.validate(9001).passed());
        assertFalse(v.validate("").passed());
        assertFalse(v.validate(new java.util.Date()).passed());

        // Only joda datetime.
        assertTrue(v.validate(new org.joda.time.DateTime(DateTimeZone.UTC)).passed());

        // Only accepts UTC.
        assertFalse(v.validate(new org.joda.time.DateTime(DateTimeZone.forID("+09:00"))).passed());
    }
}
