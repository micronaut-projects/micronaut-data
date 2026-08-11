package example.notification;

import example.Book;
import example.BookGenre;
import example.BookRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
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
class BookCacheSpec {

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
            BookRepository repository = context.getBean(BookRepository.class);
            repository.save(new Book("Title 1", 50, BookGenre.ACTION));
            repository.save(new Book("Title 2", 100, BookGenre.COMEDY));
            repository.save(new Book("Title 3", 200, BookGenre.DRAMA));
            repository.save(new Book("Title 4", 300, BookGenre.SCIENCE_FICTION));
        }
    }

    @AfterEach
    void removeData() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "datasources.default.schema-generate", "NONE"
        ))) {
            context.getBean(BookRepository.class).deleteAll();
        }
    }

    @Test
    void testObjectChangeNotifications() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.object.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ))) {
            BookRepository repository = context.getBean(BookRepository.class);
            BookCache bookCache = context.getBean(BookCache.class);

            Book book1 = bookCache.find("Title 1").orElseThrow();
            Book book2 = bookCache.find("Title 2").orElseThrow();
            Book book3 = bookCache.find("Title 3").orElseThrow();
            Book book4 = bookCache.find("Title 4").orElseThrow();

            assertEquals(BookGenre.ACTION, book1.genre());
            assertEquals(BookGenre.COMEDY, book2.genre());
            assertEquals(BookGenre.DRAMA, book3.genre());
            assertEquals(BookGenre.SCIENCE_FICTION, book4.genre());

            // title updated
            repository.save(new Book(book1.id(), book1.dateCreated(), "Title 1 Updated", book1.pages(), book1.genre()));

            waitUntil(() -> bookCache.find("Title 1 Updated").isPresent());

            Book cachedBook1 = bookCache.find("Title 1 Updated").get();

            assertNotNull(cachedBook1);
            assertEquals(book1.id(), cachedBook1.id());

            // number of pages updated
            repository.save(new Book(book2.id(), book2.dateCreated(), book2.title(), 500, book2.genre()));

            waitUntil(() -> bookCache.find("Title 2").orElseThrow().pages() == 500);

            Book cachedBook2 = bookCache.find("Title 2").orElseThrow();

            assertNotNull(cachedBook2);
            assertEquals(book2.id(), cachedBook2.id());

            // genre updated
            repository.save(new Book(book3.id(), book3.dateCreated(), book3.title(), book3.pages(), BookGenre.OTHER));

            waitUntil(() -> bookCache.find("Title 3").orElseThrow().genre() == BookGenre.OTHER);

            Book cachedBook3 = bookCache.find("Title 3").orElseThrow();

            assertNotNull(cachedBook3);
            assertEquals(book3.id(), cachedBook3.id());

            // new book added to the cache
            Book book5 = repository.save(new Book("Title 5", 80, BookGenre.OTHER));

            waitUntil(() -> bookCache.find("Title 5").isPresent());

            Book cachedBook5 = bookCache.find("Title 5").get();

            assertNotNull(cachedBook5);
            assertEquals(book5.id(), cachedBook5.id());
        }
    }

    @Test
    void testQueryChangeNotifications() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "query-notification.query.enabled", "true",
            "datasources.default.schema-generate", "NONE"
        ))) {
            BookRepository repository = context.getBean(BookRepository.class);
            PageThresholdBookCache bookCache = context.getBean(PageThresholdBookCache.class);

            assertFalse(bookCache.find("Title 1").isPresent());
            assertFalse(bookCache.find("Title 2").isPresent());

            Book book3 = bookCache.find("Title 3").orElseThrow();
            Book book4 = bookCache.find("Title 4").orElseThrow();

            assertEquals(BookGenre.DRAMA, book3.genre());
            assertEquals(BookGenre.SCIENCE_FICTION, book4.genre());

            // when number of pages is updated to be more than 200, the book should be added to the cache
            Book book1 = repository.findByTitle("Title 1");
            repository.save(new Book(book1.id(), book1.dateCreated(), book1.title(), book1.pages() + 200, book1.genre()));

            waitUntil(() -> bookCache.find(book1.title()).isPresent());

            Book cachedBook1 = bookCache.find("Title 1").get();

            assertNotNull(cachedBook1);
            assertEquals(book1.id(), cachedBook1.id());

            // when number of pages is updated to be less than 200, the book should be removed from the cache
            repository.save(new Book(book3.id(), book3.dateCreated(), book3.title(), book3.pages() - 50, book3.genre()));

            waitUntil(() -> bookCache.find(book3.title()).isEmpty());
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
