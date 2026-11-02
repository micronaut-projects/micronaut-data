package example

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.sql.DataSource

@MicronautTest(transactional = false)
class BookNotificationTest {
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var catalog: BookCatalog
    @Inject lateinit var repository: BookRepository
    @Inject @field:Client("/") lateinit var client: HttpClient

    @Test
    fun migrationsAndExternalChangesMaintainTheCatalog() {
        assertEquals("Present before startup", catalog.find(1L).orElseThrow().title)
        assertEquals("Present before startup", client.toBlocking().retrieve("/books/1", Book::class.java).title)
        DelegatingDataSource.unwrapDataSource(dataSource).connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')")
                await { catalog.find(2L).map { it.title }.orElse("") == "Inserted externally" }
                assertEquals("Inserted externally", client.toBlocking().retrieve("/books/2", Book::class.java).title)
                statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2")
                await { catalog.find(2L).isEmpty && catalog.find(3L).map { it.title }.orElse("") == "Updated externally" }
                statement.executeUpdate("DELETE FROM public.book WHERE id = 3")
                await { catalog.find(3L).isEmpty }
                statement.execute("TRUNCATE public.book")
                await { catalog.find(1L).isEmpty }
            }
        }
    }

    @Test
    fun terminatedCatalogReadsFromTheDatabaseAndIgnoresLaterCallbacks() {
        val isolated = BookCatalog(repository)
        DelegatingDataSource.unwrapDataSource(dataSource).connection.use { connection ->
            connection.createStatement().use { statement ->
                try {
                    statement.executeUpdate("INSERT INTO public.book VALUES (99, 'Database value')")
                    isolated.changed(DefaultChangeEvent(ChangeOperation.INSERT, Book(99, "Cached value"), null))
                    assertEquals("Cached value", isolated.find(99).orElseThrow().title)

                    isolated.changed(DefaultChangeEvent(ChangeOperation.TERMINATED, null, null))
                    isolated.changed(DefaultChangeEvent(ChangeOperation.INSERT, Book(99, "Late callback"), null))
                    isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))
                    assertEquals("Database value", isolated.find(99).orElseThrow().title)

                    statement.executeUpdate("UPDATE public.book SET title = 'Changed after termination' WHERE id = 99")
                    assertEquals("Changed after termination", isolated.find(99).orElseThrow().title)
                    statement.executeUpdate("DELETE FROM public.book WHERE id = 99")
                    assertTrue(isolated.find(99).isEmpty)
                } finally {
                    statement.executeUpdate("DELETE FROM public.book WHERE id = 99")
                }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        assertTrue(condition(), "Catalog did not reflect the committed change")
    }
}
