package org.graylog.plugins.enterprise.database;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb;
import org.graylog.plugins.database.MongoConnectionRule;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.database.MongoConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mongojack.DBQuery;
import org.mongojack.DBSort;
import org.mongojack.Id;
import org.mongojack.ObjectId;

import java.util.List;
import java.util.stream.Collectors;

import static com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb.InMemoryMongoRuleBuilder.newInMemoryMongoDbRule;
import static org.assertj.core.api.Assertions.assertThat;

public class PaginatedDbServiceTest {
    @ClassRule
    public static final InMemoryMongoDb IN_MEMORY_MONGO_DB = newInMemoryMongoDbRule().build();
    @Rule
    public MongoConnectionRule mongoRule = MongoConnectionRule.build("test");

    @JsonAutoDetect
    public static class TestDTO {
        @ObjectId
        @Id
        @JsonProperty("id")
        public String id;

        @JsonProperty("title")
        public String title;

        @JsonCreator
        public TestDTO(@JsonProperty("id") String id, @JsonProperty("title") String title) {
            this.id = id;
            this.title = title;
        }

        public TestDTO(String title) {
            this(null, title);
        }
    }

    public static class TestDbService extends PaginatedDbService<TestDTO> {
        protected TestDbService(MongoConnection mongoConnection, MongoJackObjectMapperProvider mapper) {
            super(mongoConnection, mapper, TestDTO.class, "db_service_test");
        }
    }

    private TestDbService dbService;

    @Before
    public void setUp() throws Exception {
        final MongoJackObjectMapperProvider objectMapperProvider = new MongoJackObjectMapperProvider(new ObjectMapper());
        this.dbService = new TestDbService(mongoRule.getMongoConnection(), objectMapperProvider);
    }

    @After
    public void tearDown() {
        mongoRule.getMongoConnection().getMongoDatabase().drop();
    }

    private TestDTO newDto(String title) {
        return new TestDTO(title);
    }

    @Test
    public void saveAndGet() {
        final TestDTO savedDto = dbService.save(newDto("hello"));

        assertThat(savedDto.title).isEqualTo("hello");
        assertThat(savedDto.id)
                .isInstanceOf(String.class)
                .isNotBlank()
                .matches("^[a-z0-9]{24}$");

        assertThat(dbService.get(savedDto.id))
                .isPresent()
                .get()
                .extracting("id", "title")
                .containsExactly(savedDto.id, "hello");
    }

    @Test
    public void delete() {
        final TestDTO savedDto = dbService.save(newDto("hello"));

        dbService.delete(savedDto.id);

        assertThat(dbService.get(savedDto.id)).isNotPresent();
    }

    @Test
    public void findPaginatedWithQueryAndSort() {
        dbService.save(newDto("hello1"));
        dbService.save(newDto("hello2"));
        dbService.save(newDto("hello3"));
        dbService.save(newDto("hello4"));
        dbService.save(newDto("hello5"));

        final PaginatedList<TestDTO> page1 = dbService.findPaginatedWithQueryAndSort(DBQuery.empty(), DBSort.asc("title"), 1, 2);

        assertThat(page1.pagination().getCount()).isEqualTo(2);
        assertThat(page1.pagination().getGlobalTotal()).isEqualTo(5);
        assertThat(page1.delegate())
                .extracting("title")
                .containsExactly("hello1", "hello2");

        final PaginatedList<TestDTO> page2 = dbService.findPaginatedWithQueryAndSort(DBQuery.empty(), DBSort.asc("title"), 2, 2);

        assertThat(page2.pagination().getCount()).isEqualTo(2);
        assertThat(page2.pagination().getGlobalTotal()).isEqualTo(5);
        assertThat(page2.delegate())
                .extracting("title")
                .containsExactly("hello3", "hello4");

        final PaginatedList<TestDTO> page3 = dbService.findPaginatedWithQueryAndSort(DBQuery.empty(), DBSort.asc("title"), 3, 2);

        assertThat(page3.pagination().getCount()).isEqualTo(1);
        assertThat(page3.pagination().getGlobalTotal()).isEqualTo(5);
        assertThat(page3.delegate())
                .extracting("title")
                .containsExactly("hello5");

        final PaginatedList<TestDTO> page1reverse = dbService.findPaginatedWithQueryAndSort(DBQuery.empty(), DBSort.desc("title"), 1, 2);

        assertThat(page1reverse.pagination().getCount()).isEqualTo(2);
        assertThat(page1reverse.pagination().getGlobalTotal()).isEqualTo(5);
        assertThat(page1reverse.delegate())
                .extracting("title")
                .containsExactly("hello5", "hello4");
    }

    @Test
    public void streamAll() {
        dbService.save(newDto("hello1"));
        dbService.save(newDto("hello2"));
        dbService.save(newDto("hello3"));
        dbService.save(newDto("hello4"));

        assertThat(dbService.streamAll().collect(Collectors.toList()))
                .hasSize(4)
                .extracting("title")
                .containsExactly("hello1", "hello2", "hello3", "hello4");
    }

    @Test
    public void streamByIds() {
        final TestDTO hello1 = dbService.save(newDto("hello1"));
        final TestDTO hello2 = dbService.save(newDto("hello2"));
        final TestDTO hello3 = dbService.save(newDto("hello3"));
        dbService.save(newDto("hello5"));
        dbService.save(newDto("hello5"));

        final List<TestDTO> list = dbService.streamByIds(ImmutableSet.of(hello1.id, hello2.id, hello3.id))
                .collect(Collectors.toList());

        assertThat(list)
                .hasSize(3)
                .extracting("title")
                .containsExactly("hello1", "hello2", "hello3");
    }

    @Test
    public void streamQuery() {
        dbService.save(newDto("hello1"));
        dbService.save(newDto("hello2"));
        dbService.save(newDto("hello3"));
        dbService.save(newDto("hello4"));
        dbService.save(newDto("hello5"));

        final DBQuery.Query query = DBQuery.in("title", "hello1", "hello3", "hello4");

        final List<TestDTO> list = dbService.streamQuery(query).collect(Collectors.toList());

        assertThat(list)
                .hasSize(3)
                .extracting("title")
                .containsExactly("hello1", "hello3", "hello4");
    }

    @Test
    public void streamQueryWithSort() {
        dbService.save(newDto("hello1"));
        dbService.save(newDto("hello2"));
        dbService.save(newDto("hello3"));
        dbService.save(newDto("hello4"));
        dbService.save(newDto("hello5"));

        final DBQuery.Query query = DBQuery.in("title", "hello5", "hello3", "hello1");
        final DBSort.SortBuilder sort = DBSort.desc("title");

        final List<TestDTO> list = dbService.streamQueryWithSort(query, sort).collect(Collectors.toList());

        assertThat(list)
                .hasSize(3)
                .extracting("title")
                .containsExactly("hello5", "hello3", "hello1");
    }
}