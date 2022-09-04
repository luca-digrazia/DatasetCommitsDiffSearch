/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.alarmcallbacks;

import com.lordofthejars.nosqlunit.annotation.UsingDataSet;
import com.lordofthejars.nosqlunit.core.LoadStrategyEnum;
import org.graylog2.alerts.Alert;
import org.graylog2.database.MongoDBServiceTest;
import org.graylog2.plugin.alarms.AlertCondition;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlarmCallbackHistoryServiceImplTest extends MongoDBServiceTest {
    private AlarmCallbackHistoryService alarmCallbackHistoryService;

    @Before
    public void setUpService() throws Exception {
        this.alarmCallbackHistoryService = new AlarmCallbackHistoryServiceImpl(mongoRule.getMongoConnection(), mapperProvider);
    }

    @Test
    @UsingDataSet
    public void testGetForAlertIdShouldReturnEmptyListWhenCollectionIsEmpty() throws Exception {
        final List<AlarmCallbackHistory> result = this.alarmCallbackHistoryService.getForAlertId("foobar", 0, 0);
        assertThat(result).isEmpty();
    }

    @Test
    public void testSuccess() throws Exception {
    }

    @Test
    public void testError() throws Exception {

    }

    @Test
    public void testSave() throws Exception {

    }
}