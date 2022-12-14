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

package org.graylog2.alerts.types;

import org.graylog2.alerts.AlertCondition;
import org.graylog2.alerts.AlertConditionTest;
import org.graylog2.indexer.IndexHelper;
import org.graylog2.indexer.results.CountResult;
import org.graylog2.indexer.searches.timeranges.TimeRange;
import org.graylog2.plugin.Tools;
import org.testng.annotations.Test;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.*;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
@Test(enabled=false)
public class MessageCountAlertConditionTest extends AlertConditionTest {
    protected final int threshold = 100;

    public void testConstructor() throws Exception {
        final Map<String, Object> parameters = getParametersMap(0, 0, MessageCountAlertCondition.ThresholdType.MORE, 0);

        final MessageCountAlertCondition messageCountAlertCondition = getMessageCountAlertCondition(parameters);

        assertNotNull(messageCountAlertCondition);
        assertNotNull(messageCountAlertCondition.getDescription());
    }

    public void testRunCheckMorePositive() throws Exception {
        final MessageCountAlertCondition.ThresholdType type = MessageCountAlertCondition.ThresholdType.MORE;

        final MessageCountAlertCondition messageCountAlertCondition = getConditionWithParameters(type, threshold);

        searchCountShouldReturn(threshold+1);
        // AlertCondition was never triggered before
        alertLastTriggered(-1);
        final AlertCondition.CheckResult result = alertService.triggered(messageCountAlertCondition, indexer);

        assertFalse("We should not be in grace period!", alertService.inGracePeriod(messageCountAlertCondition));
        assertTriggered(messageCountAlertCondition, result);
    }

    public void testRunCheckLessPositive() throws Exception {
        final MessageCountAlertCondition.ThresholdType type = MessageCountAlertCondition.ThresholdType.LESS;

        final MessageCountAlertCondition messageCountAlertCondition = getConditionWithParameters(type, threshold);

        searchCountShouldReturn(threshold - 1);
        alertLastTriggered(-1);

        final AlertCondition.CheckResult result = alertService.triggered(messageCountAlertCondition, indexer);

        assertTriggered(messageCountAlertCondition, result);
    }

    public void testRunCheckMoreNegative() throws Exception {
        final MessageCountAlertCondition.ThresholdType type = MessageCountAlertCondition.ThresholdType.MORE;

        final MessageCountAlertCondition messageCountAlertCondition = getConditionWithParameters(type, threshold);

        searchCountShouldReturn(threshold);
        alertLastTriggered(-1);

        final AlertCondition.CheckResult result = alertService.triggered(messageCountAlertCondition, indexer);

        assertNotTriggered(result);
    }

    public void testRunCheckLessNegative() throws Exception {
        final MessageCountAlertCondition.ThresholdType type = MessageCountAlertCondition.ThresholdType.LESS;

        final MessageCountAlertCondition messageCountAlertCondition = getConditionWithParameters(type, threshold);

        searchCountShouldReturn(threshold);
        alertLastTriggered(-1);

        final AlertCondition.CheckResult result = alertService.triggered(messageCountAlertCondition, indexer);

        assertNotTriggered(result);
    }

    public void testNoRecheckDuringGracePeriod() throws Exception {
        final MessageCountAlertCondition.ThresholdType type = MessageCountAlertCondition.ThresholdType.LESS;
        final int grace = 10;
        final int time = 10;

        final MessageCountAlertCondition messageCountAlertCondition = getMessageCountAlertCondition(
                getParametersMap(grace, time, MessageCountAlertCondition.ThresholdType.MORE, threshold)
        );


        try {
            verify(searches, never()).count(anyString(), any(TimeRange.class), anyString());
        } catch (IndexHelper.InvalidRangeFormatException e) {
            assertNull("This should not throw an exception", e);
        }

        alertLastTriggered(0);
        assertTrue("Alert condition should be in grace period because grace is greater than zero and alert has just been triggered!",
                alertService.inGracePeriod(messageCountAlertCondition));
        final AlertCondition.CheckResult resultJustTriggered = alertService.triggered(messageCountAlertCondition, indexer);
        assertNotTriggered(resultJustTriggered);

        alertLastTriggered(grace*60-1);
        assertTrue("Alert condition should be in grace period because grace is greater than zero and alert has been triggered during grace period!",
                alertService.inGracePeriod(messageCountAlertCondition));
        final AlertCondition.CheckResult resultTriggeredAgo = alertService.triggered(messageCountAlertCondition, indexer);
        assertNotTriggered(resultTriggeredAgo);
    }

    protected MessageCountAlertCondition getConditionWithParameters(MessageCountAlertCondition.ThresholdType type, Integer threshold) {
        Map<String, Object> parameters = simplestParameterMap(type, threshold);
        return getMessageCountAlertCondition(parameters);
    }

    protected Map<String, Object> simplestParameterMap(MessageCountAlertCondition.ThresholdType type, Integer threshold) {
        return getParametersMap(0, 0, type, threshold);
    }

    protected void searchCountShouldReturn(long count) {
        final CountResult countResult = mock(CountResult.class);
        when(countResult.getCount()).thenReturn(count);

        try {
            when(searches.count(anyString(), any(TimeRange.class), anyString())).thenReturn(countResult);
        } catch (IndexHelper.InvalidRangeFormatException e) {
            assertNotNull("This should not return an exception!", e);
        }
    }

    protected MessageCountAlertCondition getMessageCountAlertCondition(Map<String, Object> parameters) {
        return new MessageCountAlertCondition(
                stream,
                CONDITION_ID,
                Tools.iso8601(),
                STREAM_CREATOR,
                parameters);
    }

    protected Map<String, Object> getParametersMap(Integer grace, Integer time, MessageCountAlertCondition.ThresholdType type, Number threshold) {
        Map<String, Object> parameters = super.getParametersMap(grace, time, threshold);
        parameters.put("threshold_type", type.toString());

        return parameters;
    }
}
