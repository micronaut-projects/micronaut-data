package example.notification;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import io.micronaut.data.model.geo.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        ))) {
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
        ))) {
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
        ))) {
            context.getBean(LibraryRepository.class).deleteAll();
        }
    }

    @Test
    void testObjectChangeNotifications() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.object.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ))) {
            LibraryRepository repository = context.getBean(LibraryRepository.class);
            LibraryCache libraryCache = context.getBean(LibraryCache.class);

            Library library1 = libraryCache.find("Library 1").orElseThrow();
            Library library2 = libraryCache.find("Library 2").orElseThrow();
            Library library3 = libraryCache.find("Library 3").orElseThrow();
            Library library4 = libraryCache.find("Library 4").orElseThrow();

            assertEquals("library1@example.com", library1.email());
            assertEquals(9_000, library2.capacity());
            assertEquals(10_000, library3.capacity());
            assertEquals(new Point(20.68960, 44.01650), library4.location());

            // name updated
            repository.save(new Library(library1.id(), "Library 1 Updated", library1.email(), library1.capacity(), library1.location()));

            waitUntil(() -> libraryCache.find("Library 1 Updated").isPresent());

            Library cachedLibrary1 = libraryCache.find("Library 1 Updated").get();

            assertNotNull(cachedLibrary1);
            assertEquals(library1.id(), cachedLibrary1.id());

            // capacity updated
            repository.save(new Library(library2.id(), library2.name(), library2.email(), 12_000, library2.location()));

            waitUntil(() -> libraryCache.find("Library 2").orElseThrow().capacity() == 12_000);

            Library cachedLibrary2 = libraryCache.find("Library 2").orElseThrow();

            assertNotNull(cachedLibrary2);
            assertEquals(library2.id(), cachedLibrary2.id());

            // location updated
            Point updatedLocation = new Point(20.91140, 44.81250);
            repository.save(new Library(library3.id(), library3.name(), library3.email(), library3.capacity(), updatedLocation));

            waitUntil(() -> libraryCache.find("Library 3").orElseThrow().location().equals(updatedLocation));

            Library cachedLibrary3 = libraryCache.find("Library 3").orElseThrow();

            assertNotNull(cachedLibrary3);
            assertEquals(library3.id(), cachedLibrary3.id());

            // new library added to the cache
            Library library5 = repository.save(new Library(null, "Library 5", "library5@example.com", 8_000, new Point(21.16560, 44.77220)));

            waitUntil(() -> libraryCache.find("Library 5").isPresent());

            Library cachedLibrary5 = libraryCache.find("Library 5").get();

            assertNotNull(cachedLibrary5);
            assertEquals(library5.id(), cachedLibrary5.id());
        }
    }

    @Test
    void testQueryChangeNotifications() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.query.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ))) {
            LibraryRepository repository = context.getBean(LibraryRepository.class);
            CustomLibraryCache libraryCache = context.getBean(CustomLibraryCache.class);

            assertFalse(libraryCache.find("Library 1").isPresent());
            assertFalse(libraryCache.find("Library 2").isPresent());

            Library library3 = libraryCache.find("Library 3").orElseThrow();
            Library library4 = libraryCache.find("Library 4").orElseThrow();

            assertEquals(10_000, library3.capacity());
            assertEquals(15_000, library4.capacity());

            // when capacity is updated to reach 10,000, the library should be added to the cache
            Library library1 = repository.findAll().stream()
                .filter(library -> library.name().equals("Library 1"))
                .findFirst()
                .orElseThrow();
            repository.save(new Library(library1.id(), library1.name(), library1.email(), 10_000, library1.location()));

            waitUntil(() -> libraryCache.find(library1.name()).isPresent());

            Library cachedLibrary1 = libraryCache.find("Library 1").get();

            assertNotNull(cachedLibrary1);
            assertEquals(library1.id(), cachedLibrary1.id());

            // when capacity is updated below 10,000, the library should be removed from the cache
            repository.save(new Library(library3.id(), library3.name(), library3.email(), 9_000, library3.location()));

            waitUntil(() -> libraryCache.find(library3.name()).isEmpty());
        }
    }

    private void waitUntil(Callable<Boolean> conditionEvaluator) {
        Exception lastException = null;

        int timeoutSeconds = 10;
        long sleepTimeMillis = 50;

        long end  = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                // continue
            }
            try {
                if (conditionEvaluator.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        String errorMessage = "Condition was not fulfilled within " + timeoutSeconds + " seconds";
        throw lastException == null ? new IllegalStateException(errorMessage) : new IllegalStateException(errorMessage, lastException);
    }
}
