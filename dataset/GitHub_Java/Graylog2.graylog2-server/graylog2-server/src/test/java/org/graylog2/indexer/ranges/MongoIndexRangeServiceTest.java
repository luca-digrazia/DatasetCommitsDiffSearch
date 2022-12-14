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
package org.graylog2.indexer.ranges;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.EventBus;
import com.lordofthejars.nosqlunit.annotation.UsingDataSet;
import com.lordofthejars.nosqlunit.core.LoadStrategyEnum;
import com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb;
import org.assertj.jodatime.api.Assertions;
import org.bson.types.ObjectId;
import org.elasticsearch.indices.IndexMissingException;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.database.MongoConnectionRule;
import org.graylog2.database.NotFoundException;
import org.graylog2.indexer.esplugin.IndicesClosedEvent;
import org.graylog2.indexer.esplugin.IndicesDeletedEvent;
import org.graylog2.indexer.esplugin.IndicesReopenedEvent;
import org.graylog2.indexer.indices.Indices;
import org.graylog2.indexer.searches.TimestampStats;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;

import static com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb.InMemoryMongoRuleBuilder.newInMemoryMongoDbRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MongoIndexRangeServiceTest {
    @ClassRule
    public static final InMemoryMongoDb IN_MEMORY_MONGO_DB = newInMemoryMongoDbRule().build();

    @Rule
    public MongoConnectionRule mongoRule = MongoConnectionRule.build("test");

    private final ObjectMapper objectMapper = new ObjectMapperProvider().get();
    private final MongoJackObjectMapperProvider objectMapperProvider = new MongoJackObjectMapperProvider(objectMapper);

    @Mock
    private Indices indices;
    private EventBus localEventBus;
    @Mock
    private EventBus clusterEventBus;
    private MongoIndexRangeService indexRangeService;

    @Before
    public void setUp() throws Exception {
        localEventBus = new EventBus("local-event-bus");
        indexRangeService = new MongoIndexRangeService(mongoRule.getMongoConnection(), objectMapperProvider, indices, localEventBus, clusterEventBus);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void getReturnsExistingIndexRange() throws Exception {
        IndexRange indexRange = indexRangeService.get("graylog_1");

        assertThat(indexRange.indexName()).isEqualTo("graylog_1");
        assertThat(indexRange.begin()).isEqualTo(new DateTime(2015, 1, 1, 0, 0, DateTimeZone.UTC));
        assertThat(indexRange.end()).isEqualTo(new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC));
        assertThat(indexRange.calculatedAt()).isEqualTo(new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC));
        assertThat(indexRange.calculationDuration()).isEqualTo(23);
    }

    @Test(expected = NotFoundException.class)
    @UsingDataSet(locations = "MongoIndexRangeServiceTest-LegacyIndexRanges.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void getIgnoresLegacyIndexRange() throws Exception {
        indexRangeService.get("graylog_0");
    }

    @Test(expected = NotFoundException.class)
    public void getThrowsNotFoundException() throws Exception {
        indexRangeService.get("does-not-exist");
    }

    /**
     * Test the following constellation:
     * <pre>
     *                        [-        index range       -]
     * [- graylog_1 -][- graylog_2 -][- graylog_3 -][- graylog_4 -][- graylog_5 -]
     * </pre>
     */
    @Test
    @UsingDataSet(locations = "MongoIndexRangeServiceTest-distinct.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void findReturnsIndexRangesWithinGivenRange() throws Exception {
        final DateTime begin = new DateTime(2015, 1, 2, 12, 0, DateTimeZone.UTC);
        final DateTime end = new DateTime(2015, 1, 4, 12, 0, DateTimeZone.UTC);
        final SortedSet<IndexRange> indexRanges = indexRangeService.find(begin, end);

        assertThat(indexRanges).containsExactly(
                MongoIndexRange.create(new ObjectId("55e0261a0cc6980000000002"), "graylog_2", new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 3, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 3, 0, 0, DateTimeZone.UTC), 42),
                MongoIndexRange.create(new ObjectId("55e0261a0cc6980000000003"), "graylog_3", new DateTime(2015, 1, 3, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 4, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 4, 0, 0, DateTimeZone.UTC), 42),
                MongoIndexRange.create(new ObjectId("55e0261a0cc6980000000004"), "graylog_4", new DateTime(2015, 1, 4, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 5, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 5, 0, 0, DateTimeZone.UTC), 42)
        );
    }

    @Test
    @UsingDataSet(locations = "MongoIndexRangeServiceTest-LegacyIndexRanges.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void findIgnoresLegacyIndexRanges() throws Exception {
        final DateTime begin = new DateTime(2015, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime end = new DateTime(2015, 2, 1, 0, 0, DateTimeZone.UTC);
        final SortedSet<IndexRange> indexRanges = indexRangeService.find(begin, end);

        assertThat(indexRanges).containsOnly(
                MongoIndexRange.create(new ObjectId("55e0261a0cc6980000000003"), "graylog_1", new DateTime(2015, 1, 1, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC), new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC), 42)
        );
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void findReturnsNothingBeforeBegin() throws Exception {
        final DateTime begin = new DateTime(2016, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime end = new DateTime(2016, 1, 2, 0, 0, DateTimeZone.UTC);
        Set<IndexRange> indexRanges = indexRangeService.find(begin, end);

        assertThat(indexRanges).isEmpty();
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void findAllReturnsAllIndexRanges() throws Exception {
        assertThat(indexRangeService.findAll()).hasSize(2);
    }

    @Test
    @UsingDataSet(locations = "MongoIndexRangeServiceTest-LegacyIndexRanges.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void findAllReturnsAllIgnoresLegacyIndexRanges() throws Exception {
        assertThat(indexRangeService.findAll()).hasSize(1);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void calculateRangeReturnsIndexRange() throws Exception {
        final String index = "graylog";
        final DateTime min = new DateTime(2015, 1, 1, 1, 0, DateTimeZone.UTC);
        final DateTime max = new DateTime(2015, 1, 1, 5, 0, DateTimeZone.UTC);
        when(indices.timestampStatsOfIndex(index)).thenReturn(TimestampStats.create(min, max));

        final IndexRange indexRange = indexRangeService.calculateRange(index);

        assertThat(indexRange.indexName()).isEqualTo(index);
        assertThat(indexRange.begin()).isEqualTo(min);
        assertThat(indexRange.end()).isEqualTo(max);
        Assertions.assertThat(indexRange.calculatedAt()).isEqualToIgnoringHours(DateTime.now(DateTimeZone.UTC));
    }

    @Test
    @UsingDataSet(locations = "MongoIndexRangeServiceTest-EmptyCollection.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void testCalculateRangeWithEmptyIndex() throws Exception {
        final String index = "graylog";
        when(indices.timestampStatsOfIndex(index)).thenReturn(TimestampStats.EMPTY);

        final IndexRange range = indexRangeService.calculateRange(index);

        assertThat(range).isNotNull();
        assertThat(range.indexName()).isEqualTo(index);
        assertThat(range.begin()).isEqualTo(new DateTime(0L, DateTimeZone.UTC));
        assertThat(range.end()).isEqualTo(new DateTime(0L, DateTimeZone.UTC));
    }

    @Test(expected = IndexMissingException.class)
    public void testCalculateRangeWithNonExistingIndex() throws Exception {
        when(indices.timestampStatsOfIndex("does-not-exist")).thenThrow(IndexMissingException.class);
        indexRangeService.calculateRange("does-not-exist");
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.DELETE_ALL)
    public void savePersistsIndexRange() throws Exception {
        final String indexName = "graylog";
        final DateTime begin = new DateTime(2015, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime end = new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC);
        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final IndexRange indexRange = MongoIndexRange.create(indexName, begin, end, now, 42);

        indexRangeService.save(indexRange);

        final IndexRange result = indexRangeService.get(indexName);
        verify(clusterEventBus, times(1)).post(IndexRangeUpdatedEvent.create(indexName));
        assertThat(result.indexName()).isEqualTo(indexName);
        assertThat(result.begin()).isEqualTo(begin);
        assertThat(result.end()).isEqualTo(end);
        assertThat(result.calculatedAt()).isEqualTo(now);
        assertThat(result.calculationDuration()).isEqualTo(42);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.DELETE_ALL)
    public void saveOverwritesExistingIndexRange() throws Exception {
        final String indexName = "graylog";
        final DateTime begin = new DateTime(2015, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime end = new DateTime(2015, 1, 2, 0, 0, DateTimeZone.UTC);
        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final IndexRange indexRangeBefore = MongoIndexRange.create(indexName, begin, end, now, 1);
        final IndexRange indexRangeAfter = MongoIndexRange.create(indexName, begin, end, now, 2);

        indexRangeService.save(indexRangeBefore);

        final IndexRange before = indexRangeService.get(indexName);
        assertThat(before.calculationDuration()).isEqualTo(1);

        indexRangeService.save(indexRangeAfter);

        final IndexRange after = indexRangeService.get(indexName);
        assertThat(after.calculationDuration()).isEqualTo(2);

        verify(clusterEventBus, times(2)).post(IndexRangeUpdatedEvent.create(indexName));
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void testHandleIndexDeletion() throws Exception {
        assertThat(indexRangeService.findAll()).hasSize(2);

        localEventBus.post(IndicesDeletedEvent.create(Collections.singleton("graylog_1")));

        assertThat(indexRangeService.findAll()).hasSize(1);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void testHandleIndexClosing() throws Exception {
        assertThat(indexRangeService.findAll()).hasSize(2);

        localEventBus.post(IndicesClosedEvent.create(Collections.singleton("graylog_1")));

        assertThat(indexRangeService.findAll()).hasSize(1);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void testHandleIndexReopening() throws Exception {
        final DateTime begin = new DateTime(2016, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime end = new DateTime(2016, 1, 15, 0, 0, DateTimeZone.UTC);
        when(indices.timestampStatsOfIndex("graylog_3")).thenReturn(TimestampStats.create(begin, end));

        localEventBus.post(IndicesReopenedEvent.create(Collections.singleton("graylog_3")));

        final SortedSet<IndexRange> indexRanges = indexRangeService.find(begin, end);
        assertThat(indexRanges).hasSize(1);
        assertThat(indexRanges.first().indexName()).isEqualTo("graylog_3");
        assertThat(indexRanges.first().begin()).isEqualTo(begin);
        assertThat(indexRanges.first().end()).isEqualTo(end);
    }
}