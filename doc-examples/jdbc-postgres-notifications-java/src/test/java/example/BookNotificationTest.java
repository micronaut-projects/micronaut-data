package example;

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.data.notification.ChangeEventMetadata;
import io.micronaut.data.notification.ChangeOperation;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookNotificationTest {
    @Inject DataSource dataSource;
    @Inject BookCatalog catalog;
    @Inject BookRepository repository;
    @Inject ChangeEventRecorder events;
    @Inject ChangeEventRecorder.RepositoryReadCounter repositoryReads;
    @Inject @Client("/") HttpClient client;

    @Test
    void migrationsAndExternalChangesMaintainTheCatalog() throws Exception {
        assertEquals(new Book(1L, "Present before startup"), catalog.find(1L).orElseThrow());
        assertEquals("Present before startup", client.toBlocking().retrieve("/books/1", Book.class).title());
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')");
            Book inserted = new Book(2L, "Inserted externally");
            assertEvent(ChangeOperation.INSERT, inserted, Map.of(), Map.of("id", "2"));
            assertEquals(inserted, catalog.find(2L).orElseThrow());
            assertEquals(inserted, client.toBlocking().retrieve("/books/2", Book.class));

            statement.executeUpdate("UPDATE public.book SET title = 'Title updated externally' WHERE id = 2");
            Book updated = new Book(2L, "Title updated externally");
            assertEvent(ChangeOperation.UPDATE, updated, Map.of("id", "2"), Map.of("id", "2"));
            assertEquals(updated, catalog.find(2L).orElseThrow());

            statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2");
            Book moved = new Book(3L, "Updated externally");
            assertEvent(ChangeOperation.UPDATE, moved, Map.of("id", "2"), Map.of("id", "3"));
            assertTrue(catalog.find(2L).isEmpty());
            assertEquals(moved, catalog.find(3L).orElseThrow());

            statement.executeUpdate("DELETE FROM public.book WHERE id = 3");
            assertEvent(ChangeOperation.DELETE, null, Map.of("id", "3"), Map.of());
            assertTrue(catalog.find(3L).isEmpty());
            statement.execute("TRUNCATE public.book");
            assertEvent(ChangeOperation.INVALIDATE, null, Map.of(), Map.of());
            assertTrue(catalog.find(1L).isEmpty());
        }
    }

    @Test
    void invalidationReconcilesNonemptyResultsWithoutMetadata() throws Exception {
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            try {
                Book preserved = new Book(103L, "Preserved");
                statement.executeUpdate("INSERT INTO public.book VALUES (100, 'Original')");
                assertEvent(ChangeOperation.INSERT, new Book(100L, "Original"), Map.of(), Map.of("id", "100"));
                statement.executeUpdate("INSERT INTO public.book VALUES (101, 'Removed')");
                assertEvent(ChangeOperation.INSERT, new Book(101L, "Removed"), Map.of(), Map.of("id", "101"));
                statement.executeUpdate("INSERT INTO public.book VALUES (103, 'Preserved')");
                assertEvent(ChangeOperation.INSERT, preserved, Map.of(), Map.of("id", "103"));

                repositoryReads.reset();
                BookCatalog isolated = new BookCatalog(repository);
                isolated.changed(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));

                statement.executeUpdate("UPDATE public.book SET title = 'Changed' WHERE id = 100");
                assertEvent(ChangeOperation.UPDATE, new Book(100L, "Changed"), Map.of("id", "100"), Map.of("id", "100"));
                deleteBook(statement, 101L);
                statement.executeUpdate("INSERT INTO public.book VALUES (102, 'New')");
                assertEvent(ChangeOperation.INSERT, new Book(102L, "New"), Map.of(), Map.of("id", "102"));
                assertEquals("Original", isolated.find(100L).orElseThrow().title());
                assertTrue(isolated.find(101L).isPresent());
                assertTrue(isolated.find(102L).isEmpty());

                isolated.changed(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));

                assertEquals("Changed", isolated.find(100L).orElseThrow().title());
                assertTrue(isolated.find(101L).isEmpty());
                assertEquals("New", isolated.find(102L).orElseThrow().title());
                assertEquals(preserved, isolated.find(103L).orElseThrow());
                assertEquals(2, repositoryReads.count("findAll"));
                assertEquals(0, repositoryReads.count("findById"));
            } finally {
                for (long id : new long[]{100, 101, 102, 103}) {
                    deleteBook(statement, id);
                }
            }
        }
    }

    @Test
    void terminatedCatalogReadsFromTheDatabaseAndIgnoresLaterCallbacks() throws Exception {
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            try {
                statement.executeUpdate("INSERT INTO public.book VALUES (99, 'Cached value')");
                assertEvent(ChangeOperation.INSERT, new Book(99L, "Cached value"), Map.of(), Map.of("id", "99"));
                repositoryReads.reset();
                BookCatalog isolated = new BookCatalog(repository);
                isolated.changed(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
                assertEquals("Cached value", isolated.find(99L).orElseThrow().title());
                statement.executeUpdate("UPDATE public.book SET title = 'Database value' WHERE id = 99");
                assertEvent(ChangeOperation.UPDATE, new Book(99L, "Database value"), Map.of("id", "99"), Map.of("id", "99"));
                AtomicInteger entityReads = new AtomicInteger();
                // Count entity() access itself: DeferredChangeEvent only materializes through listener advice.
                ChangeEvent<Book> late = new ChangeEvent<>() {
                    @Override
                    public ChangeOperation operation() {
                        return ChangeOperation.INSERT;
                    }

                    @Override
                    public Optional<Book> entity() {
                        entityReads.incrementAndGet();
                        return Optional.of(new Book(99L, "Late callback"));
                    }

                    @Override
                    public <M extends ChangeEventMetadata> Optional<M> metadata(Class<M> metadataType) {
                        return Optional.empty();
                    }
                };

                isolated.changed(new DefaultChangeEvent<>(ChangeOperation.TERMINATED, null, null));
                isolated.changed(late);
                isolated.changed(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
                assertEquals(0, entityReads.get());
                assertEquals(1, repositoryReads.count("findAll"));
                assertEquals(0, repositoryReads.count("findById"));

                assertEquals("Database value", isolated.find(99L).orElseThrow().title());
                statement.executeUpdate("UPDATE public.book SET title = 'Changed after termination' WHERE id = 99");
                assertEvent(ChangeOperation.UPDATE, new Book(99L, "Changed after termination"), Map.of("id", "99"), Map.of("id", "99"));
                assertEquals("Changed after termination", isolated.find(99L).orElseThrow().title());
                deleteBook(statement, 99L);
                assertTrue(isolated.find(99L).isEmpty());
                assertEquals(3, repositoryReads.count("findById"));
                assertEquals(1, repositoryReads.count("findAll"));
            } finally {
                deleteBook(statement, 99L);
            }
        }
    }

    private void deleteBook(Statement statement, long id) throws Exception {
        if (statement.executeUpdate("DELETE FROM public.book WHERE id = " + id) > 0) {
            assertEvent(ChangeOperation.DELETE, null, Map.of("id", Long.toString(id)), Map.of());
        }
    }

    private void assertEvent(ChangeOperation operation, @Nullable Book entity,
                             Map<String, String> oldKeys, Map<String, String> newKeys) throws InterruptedException {
        ChangeEvent<Book> event = events.poll(10, TimeUnit.SECONDS);
        assertNotNull(event, "Expected " + operation + " notification");
        assertEquals(operation, event.operation());
        assertEquals(Optional.ofNullable(entity), event.entity());
        PostgresChangeEventMetadata metadata = event.metadata(PostgresChangeEventMetadata.class).orElseThrow();
        assertEquals("book_changes", metadata.channel());
        assertEquals("public", metadata.schema());
        assertEquals("book", metadata.table());
        assertEquals(oldKeys, metadata.oldKeys());
        assertEquals(newKeys, metadata.newKeys());
    }
}
