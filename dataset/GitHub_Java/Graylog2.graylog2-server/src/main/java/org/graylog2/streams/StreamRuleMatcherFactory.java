/**
 * Copyright 2011 Lennart Koopmann <lennart@socketfeed.com>
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

package org.graylog2.streams;

import org.graylog2.streams.matchers.*;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class StreamRuleMatcherFactory {

    public static StreamRuleMatcher build(int ruleType) throws InvalidStreamRuleTypeException {
        StreamRuleMatcher matcher = null;

        // IMPORTANT: Also add every new rule type to the unit test.
        switch (ruleType) {
            case StreamRuleImpl.TYPE_MESSAGE:
                matcher = new MessageMatcher();
                break;
            case StreamRuleImpl.TYPE_FULL_MESSAGE:
                matcher = new FullMessageMatcher();
                break;
            case StreamRuleImpl.TYPE_HOST:
                matcher = new HostMatcher();
                break;
            case StreamRuleImpl.TYPE_SEVERITY:
                matcher = new SeverityMatcher();
                break;
            case StreamRuleImpl.TYPE_FACILITY:
                matcher = new FacilityMatcher();
                break;
            case StreamRuleImpl.TYPE_ADDITIONAL:
                matcher = new AdditionalFieldMatcher();
                break;
            case StreamRuleImpl.TYPE_SEVERITY_OR_HIGHER:
                matcher = new SeverityOrHigherMatcher();
                break;
            case StreamRuleImpl.TYPE_HOST_REGEX:
                matcher = new HostRegexMatcher();
                break;
            case StreamRuleImpl.TYPE_FILENAME_LINE:
                matcher = new FileNameAndLineMatcher();
                break;
            case StreamRuleImpl.TYPE_FACILITY_REGEX:
                matcher = new FacilityRegexMatcher();
                break;
            default:
                throw new InvalidStreamRuleTypeException();
        }

        return matcher;
    }

}