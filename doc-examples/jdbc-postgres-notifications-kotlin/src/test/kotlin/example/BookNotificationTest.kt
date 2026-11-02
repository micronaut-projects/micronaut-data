package example

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeEventMetadata
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

@MicronautTest(transactional = false)
class BookNotificationTest {
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var catalog: BookCatalog
    @Inject lateinit var repository: BookRepository
    @Inject lateinit var events: ChangeEventRecorder
    @Inject @field:Client("/") lateinit var client: HttpClient

    @Test
    fun migrationsAndExternalChangesMaintainTheCatalog() {
        assertEquals(Book(1, "Present before startup"), catalog.find(1).orElseThrow())
        assertEquals("Present before startup", client.toBlocking().retrieve("/books/1", Book::class.java).title)
        DelegatingDataSource.unwrapDataSource(dataSource).connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')")
                val inserted = Book(2, "Inserted externally")
                assertEvent(ChangeOperation.INSERT, inserted, emptyMap(), mapOf("id" to "2"))
                assertEquals(inserted, catalog.find(2).orElseThrow())
                assertEquals(inserted, client.toBlocking().retrieve("/books/2", Book::class.java))

                statement.executeUpdate("UPDATE public.book SET title = 'Title updated externally' WHERE id = 2")
                val updated = Book(2, "Title updated externally")
                assertEvent(ChangeOperation.UPDATE, updated, mapOf("id" to "2"), mapOf("id" to "2"))
                assertEquals(updated, catalog.find(2).orElseThrow())

                statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2")
                val moved = Book(3, "Updated externally")
                assertEvent(ChangeOperation.UPDATE, moved, mapOf("id" to "2"), mapOf("id" to "3"))
                assertTrue(catalog.find(2).isEmpty)
                assertEquals(moved, catalog.find(3).orElseThrow())

                statement.executeUpdate("DELETE FROM public.book WHERE id = 3")
                assertEvent(ChangeOperation.DELETE, null, mapOf("id" to "3"), emptyMap())
                assertTrue(catalog.find(3).isEmpty)
                statement.execute("TRUNCATE public.book")
                assertEvent(ChangeOperation.INVALIDATE, null, emptyMap(), emptyMap())
                assertTrue(catalog.find(1).isEmpty)
            }
        }
    }

    @Test
    fun invalidationReconcilesNonemptyResultsWithoutMetadata() {
        val reads = CatalogRepository(repository)
        val preserved = Book(4, "Preserved")
        reads.rows = listOf(Book(1, "Original"), Book(2, "Removed"), preserved)
        val isolated = BookCatalog(reads)
        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))
        reads.rows = listOf(Book(1, "Changed"), Book(3, "New"), preserved)
        assertEquals("Original", isolated.find(1).orElseThrow().title)
        assertTrue(isolated.find(2).isPresent)
        assertTrue(isolated.find(3).isEmpty)

        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))

        assertEquals("Changed", isolated.find(1).orElseThrow().title)
        assertTrue(isolated.find(2).isEmpty)
        assertEquals("New", isolated.find(3).orElseThrow().title)
        assertEquals(preserved, isolated.find(4).orElseThrow())
        assertEquals(2, reads.fullReads)
        assertEquals(0, reads.idReads)
    }

    @Test
    fun terminatedCatalogReadsFromTheRepositoryAndIgnoresLaterCallbacks() {
        val reads = CatalogRepository(repository)
        reads.rows = listOf(Book(99, "Cached value"))
        val isolated = BookCatalog(reads)
        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))
        assertEquals("Cached value", isolated.find(99).orElseThrow().title)
        reads.rows = listOf(Book(99, "Database value"))
        val entityReads = AtomicInteger()
        // Count entity() access itself, without depending on listener advice to materialize an event.
        val late = object : ChangeEvent<Book> {
            override fun operation() = ChangeOperation.INSERT
            override fun entity(): Optional<Book> {
                entityReads.incrementAndGet()
                return Optional.of(Book(99, "Late callback"))
            }
            override fun <M : ChangeEventMetadata> metadata(type: Class<M>): Optional<M> = Optional.empty()
        }

        isolated.changed(DefaultChangeEvent(ChangeOperation.TERMINATED, null, null))
        isolated.changed(late)
        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))
        assertEquals(0, entityReads.get())
        assertEquals(1, reads.fullReads)
        assertEquals(0, reads.idReads)

        assertEquals("Database value", isolated.find(99).orElseThrow().title)
        reads.rows = listOf(Book(99, "Changed after termination"))
        assertEquals("Changed after termination", isolated.find(99).orElseThrow().title)
        reads.rows = emptyList()
        assertTrue(isolated.find(99).isEmpty)
        assertEquals(3, reads.idReads)
        assertEquals(1, reads.fullReads)
    }

    private fun assertEvent(operation: ChangeOperation, entity: Book?,
                            oldKeys: Map<String, String>, newKeys: Map<String, String>) {
        val event = events.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("Expected $operation notification")
        assertEquals(operation, event.operation())
        assertEquals(entity, event.entity().orElse(null))
        val metadata = event.metadata(PostgresChangeEventMetadata::class.java).orElseThrow()
        assertEquals("book_changes", metadata.channel())
        assertEquals("public", metadata.schema())
        assertEquals("book", metadata.table())
        assertEquals(oldKeys, metadata.oldKeys())
        assertEquals(newKeys, metadata.newKeys())
    }

    private class CatalogRepository(delegate: BookRepository) : BookRepository by delegate {
        var rows: List<Book> = emptyList()
        var fullReads = 0
        var idReads = 0

        override fun findAll(): List<Book> {
            fullReads++
            return rows
        }

        override fun findById(id: Long): Optional<Book> {
            idReads++
            return Optional.ofNullable(rows.find { it.id == id })
        }
    }
}
