package example.notification;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.data.notification.ChangeOperation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryCacheSpec {

    @BeforeAll
    void grantChangeNotificationPrivilege() {
        // Oracle Test Resources creates the regular test user without this Oracle-specific
        // privilege. Bootstrap as SYSTEM before starting the notification-enabled context.
        try (ApplicationContext administratorContext = ApplicationContext.run(Map.of(
            "datasources.default.username", "system",
            "datasources.default.password", "test",
            "datasources.default.schema-generate", "NONE"
        ), "oracle-cqn")) {
            administratorContext.getBean(DefaultJdbcRepositoryOperations.class).execute(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("GRANT CHANGE NOTIFICATION TO test");
                }
                return true;
            });
        }
    }

    @BeforeEach
    void prepareData() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "datasources.default.schema-generate", "CREATE"
        ), "oracle-cqn")) {
            LibraryRepository repository = context.getBean(LibraryRepository.class);
            repository.save(new Library(null, "Library 1", "library1@example.com", 5_000, new Point(20.46513, 44.80401)));
            repository.save(new Library(null, "Library 2", "library2@example.com", 9_000, new Point(19.83355, 45.26714)));
            repository.save(new Library(null, "Library 3", "library3@example.com", 10_000, new Point(21.89830, 43.32090)));
            repository.save(new Library(null, "Library 4", "library4@example.com", 15_000, new Point(20.68960, 44.01650)));
        }
    }

    @AfterEach
    void removeData() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "datasources.default.schema-generate", "NONE"
        ), "oracle-cqn")) {
            context.getBean(LibraryRepository.class).deleteAll();
        }
    }

    @Test
    void testObjectChangeNotifications() throws InterruptedException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.object.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ), "oracle-cqn")) {
            LibraryRepository repository = context.getBean(LibraryRepository.class);
            LibraryCache cache = context.getBean(LibraryCache.class);
            ChangeEventRecorder events = context.getBean(ChangeEventRecorder.class);
            Library library1 = cache.find("Library 1").orElseThrow();
            Library library2 = cache.find("Library 2").orElseThrow();
            Library library3 = cache.find("Library 3").orElseThrow();
            assertEquals("library1@example.com", library1.email());
            assertEquals(9_000, library2.capacity());
            assertEquals(10_000, library3.capacity());
            assertEquals(new Point(20.68960, 44.01650), cache.find("Library 4").orElseThrow().location());

            Library renamed = new Library(library1.id(), "Library 1 Updated", library1.email(), library1.capacity(), library1.location());
            repository.save(renamed);
            assertRowEvent(events, ChangeOperation.UPDATE, renamed);
            assertEquals(renamed, cache.find(renamed.name()).orElseThrow());

            Library expanded = new Library(library2.id(), library2.name(), library2.email(), 12_000, library2.location());
            repository.save(expanded);
            assertRowEvent(events, ChangeOperation.UPDATE, expanded);
            assertEquals(expanded, cache.find(expanded.name()).orElseThrow());

            Library relocated = new Library(library3.id(), library3.name(), library3.email(), library3.capacity(), new Point(20.91140, 44.81250));
            repository.save(relocated);
            assertRowEvent(events, ChangeOperation.UPDATE, relocated);
            assertEquals(relocated, cache.find(relocated.name()).orElseThrow());

            Library inserted = repository.save(new Library(null, "Library 5", "library5@example.com", 8_000, new Point(21.16560, 44.77220)));
            ChangeEvent<Library> insert = assertRowEvent(events, ChangeOperation.INSERT, inserted);
            assertEquals(inserted, cache.find(inserted.name()).orElseThrow());

            repository.deleteById(inserted.id());
            ChangeEvent<Library> delete = assertRowEvent(events, ChangeOperation.DELETE, null);
            assertEquals(insert.metadata(OracleChangeEventMetadata.class).orElseThrow().rowId(),
                delete.metadata(OracleChangeEventMetadata.class).orElseThrow().rowId());
            assertTrue(cache.find(inserted.name()).isEmpty());

            truncateLibraries(context);
            assertInvalidation(events);
            for (String name : new String[]{renamed.name(), expanded.name(), relocated.name(), "Library 4"}) {
                assertTrue(cache.find(name).isEmpty());
            }
        }
    }

    @Test
    void testQueryChangeNotifications() throws InterruptedException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.query.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ), "oracle-cqn")) {
            LibraryRepository repository = context.getBean(LibraryRepository.class);
            LargeLibraryCache cache = context.getBean(LargeLibraryCache.class);
            ChangeEventRecorder events = context.getBean(ChangeEventRecorder.class);
            assertTrue(cache.find("Library 1").isEmpty());
            assertTrue(cache.find("Library 2").isEmpty());
            Library library3 = cache.find("Library 3").orElseThrow();
            Library library4 = cache.find("Library 4").orElseThrow();
            assertEquals(10_000, library3.capacity());
            assertEquals(15_000, library4.capacity());
            Library library1 = repository.findAll().stream().filter(library -> library.name().equals("Library 1")).findFirst().orElseThrow();

            Library entered = new Library(library1.id(), library1.name(), library1.email(), 10_000, library1.location());
            repository.save(entered);
            assertRowEvent(events, ChangeOperation.UPDATE, entered);
            assertEquals(entered, cache.find(entered.name()).orElseThrow());

            // The registered select contains only name; the event must reload every entity field.
            Library updated = new Library(library4.id(), "Library 4 Updated", "updated@example.com", 16_000, new Point(20.91140, 44.81250));
            repository.save(updated);
            assertRowEvent(events, ChangeOperation.UPDATE, updated);
            assertEquals(updated, cache.find(updated.name()).orElseThrow());

            Library inserted = repository.save(new Library(null, "Library 5", "library5@example.com", 12_000, new Point(21.16560, 44.77220)));
            assertRowEvent(events, ChangeOperation.INSERT, inserted);
            assertEquals(inserted, cache.find(inserted.name()).orElseThrow());

            Library left = new Library(library3.id(), library3.name(), library3.email(), 9_000, library3.location());
            repository.save(left);
            assertRowEvent(events, ChangeOperation.UPDATE, left);
            assertTrue(cache.find(left.name()).isEmpty());

            repository.deleteById(updated.id());
            assertRowEvent(events, ChangeOperation.DELETE, null);
            assertTrue(cache.find(updated.name()).isEmpty());

            truncateLibraries(context);
            assertInvalidation(events);
            assertTrue(cache.find(entered.name()).isEmpty());
            assertTrue(cache.find(inserted.name()).isEmpty());
        }
    }

    @Test
    void testQueryIgnoresChangesOutsideFilter() throws InterruptedException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.query.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ), "oracle-cqn")) {
            LibraryRepository repository = context.getBean(LibraryRepository.class);
            LargeLibraryCache cache = context.getBean(LargeLibraryCache.class);
            ChangeEventRecorder events = context.getBean(ChangeEventRecorder.class);
            Library included = cache.find("Library 3").orElseThrow();
            Library control = new Library(included.id(), "Library 3 Included", included.email(), included.capacity(), included.location());
            repository.save(control);
            assertRowEvent(events, ChangeOperation.UPDATE, control);

            Library excluded = repository.findAll().stream().filter(library -> library.name().equals("Library 1")).findFirst().orElseThrow();
            repository.save(new Library(excluded.id(), "Library 1 Still Excluded", excluded.email(), 9_999, excluded.location()));
            assertNull(events.poll(1, TimeUnit.SECONDS), "An update outside the registered query must not invoke the listener");
            assertTrue(cache.find("Library 1 Still Excluded").isEmpty());

            repository.save(new Library(null, "Excluded Insert", "excluded@example.com", 9_999, new Point(20, 44)));
            assertNull(events.poll(1, TimeUnit.SECONDS), "An insert outside the registered query must not invoke the listener");
            assertTrue(cache.find("Excluded Insert").isEmpty());
        }
    }

    private ChangeEvent<Library> nextEvent(ChangeEventRecorder events, ChangeOperation operation) throws InterruptedException {
        ChangeEvent<Library> event = events.poll(10, TimeUnit.SECONDS);
        assertNotNull(event, "Expected " + operation + " notification");
        assertEquals(operation, event.operation());
        return event;
    }

    private ChangeEvent<Library> assertRowEvent(ChangeEventRecorder events, ChangeOperation operation, @Nullable Library entity) throws InterruptedException {
        ChangeEvent<Library> event = nextEvent(events, operation);
        assertEquals(Optional.ofNullable(entity), event.entity());
        assertFalse(event.metadata(OracleChangeEventMetadata.class).orElseThrow().rowId().isBlank());
        return event;
    }

    private void assertInvalidation(ChangeEventRecorder events) throws InterruptedException {
        ChangeEvent<Library> event = nextEvent(events, ChangeOperation.INVALIDATE);
        assertTrue(event.entity().isEmpty());
        assertTrue(event.metadata(OracleChangeEventMetadata.class).isEmpty());
    }

    private void truncateLibraries(ApplicationContext context) {
        context.getBean(DefaultJdbcRepositoryOperations.class).execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE LIBRARY");
            }
            return true;
        });
    }

}
