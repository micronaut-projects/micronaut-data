package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookNotificationTest {
    @Inject DataSource dataSource;
    @Inject BookCatalog catalog;
    @Inject @Client("/") HttpClient client;

    @Test
    void migrationsAndExternalChangesMaintainTheCatalog() throws Exception {
        assertEquals("Present before startup", catalog.find(1L).orElseThrow().title());
        assertEquals("Present before startup", client.toBlocking().retrieve("/books/1", Book.class).title());
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')");
            await(() -> catalog.find(2L).map(Book::title).orElse("").equals("Inserted externally"));
            assertEquals("Inserted externally", client.toBlocking().retrieve("/books/2", Book.class).title());
            statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2");
            await(() -> catalog.find(2L).isEmpty() && catalog.find(3L).map(Book::title).orElse("").equals("Updated externally"));
            statement.executeUpdate("DELETE FROM public.book WHERE id = 3");
            await(() -> catalog.find(3L).isEmpty());
            statement.execute("TRUNCATE public.book");
            await(() -> catalog.find(1L).isEmpty());
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Catalog did not reflect the committed change");
    }
}
