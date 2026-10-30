package example.notification

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.model.geo.Point
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Statement
import java.util.concurrent.TimeUnit

class LibraryCacheSpec {

    @Test
    fun testObjectChangeNotifications() {
        grantChangeNotificationPrivilege()
        prepareData()
        try {
            withContext(mapOf(
                "query-notification.object.enabled" to "true",
                "datasources.default.schema-generate" to "NONE"
            )) { context ->
                val repository = context.getBean(LibraryRepository::class.java)
                val cache = context.getBean(LibraryCache::class.java)
                val events = context.getBean(ChangeEventRecorder::class.java)
                val library1 = cache.find("Library 1").orElseThrow()
                val library2 = cache.find("Library 2").orElseThrow()
                val library3 = cache.find("Library 3").orElseThrow()
                assertEquals("library1@example.com", library1.email)
                assertEquals(9000, library2.capacity)
                assertEquals(10000, library3.capacity)
                assertEquals(Point(20.68960, 44.01650), cache.find("Library 4").orElseThrow().location)

                val renamed = Library(library1.id, "Library 1 Updated", library1.email, library1.capacity, library1.location)
                repository.save(renamed)
                assertRowEvent(events, ChangeOperation.UPDATE, renamed)
                assertEquals(renamed, cache.find(renamed.name).orElseThrow())

                val expanded = Library(library2.id, library2.name, library2.email, 12000, library2.location)
                repository.save(expanded)
                assertRowEvent(events, ChangeOperation.UPDATE, expanded)
                assertEquals(expanded, cache.find(expanded.name).orElseThrow())

                val relocated = Library(library3.id, library3.name, library3.email, library3.capacity, Point(20.91140, 44.81250))
                repository.save(relocated)
                assertRowEvent(events, ChangeOperation.UPDATE, relocated)
                assertEquals(relocated, cache.find(relocated.name).orElseThrow())

                val inserted = repository.save(Library(null, "Library 5", "library5@example.com", 8000, Point(21.16560, 44.77220)))
                val insert = assertRowEvent(events, ChangeOperation.INSERT, inserted)
                assertEquals(inserted, cache.find(inserted.name).orElseThrow())

                repository.deleteById(inserted.id!!)
                val delete = assertRowEvent(events, ChangeOperation.DELETE, null)
                assertEquals(insert.metadata(OracleChangeEventMetadata::class.java).orElseThrow().rowId(),
                    delete.metadata(OracleChangeEventMetadata::class.java).orElseThrow().rowId())
                assertTrue(cache.find(inserted.name).isEmpty)

                truncateLibraries(context)
                assertInvalidation(events)
                for (name in listOf(renamed.name, expanded.name, relocated.name, "Library 4")) {
                    assertTrue(cache.find(name).isEmpty)
                }
            }
        } finally {
            removeData()
        }
    }

    @Test
    fun testQueryChangeNotifications() {
        grantChangeNotificationPrivilege()
        prepareData()
        try {
            withContext(mapOf(
                "query-notification.query.enabled" to "true",
                "datasources.default.schema-generate" to "NONE"
            )) { context ->
                val repository = context.getBean(LibraryRepository::class.java)
                val cache = context.getBean(LibraryNameCache::class.java)
                val events = context.getBean(ChangeEventRecorder::class.java)
                assertFalse(cache.contains("Library 1"))
                assertFalse(cache.contains("Library 2"))
                assertTrue(cache.contains("Library 3"))
                val library3 = repository.findAll().first { it.name == "Library 3" }
                assertTrue(cache.contains("Library 4"))
                val library4 = repository.findAll().first { it.name == "Library 4" }
                val library1 = repository.findAll().first { it.name == "Library 1" }

                val entered = Library(library1.id, library1.name, library1.email, 10000, library1.location)
                repository.save(entered)
                assertRowEvent(events, ChangeOperation.UPDATE, entered)
                assertTrue(cache.contains(entered.name))
                assertEquals(entered.name, cache.find(entered.id!!).orElseThrow())

                // The event reloads the complete entity; the cache retains only its ID and name.
                val updated = Library(library4.id, "Library 4 Updated", "updated@example.com", 16000, Point(20.91140, 44.81250))
                repository.save(updated)
                assertRowEvent(events, ChangeOperation.UPDATE, updated)
                assertTrue(cache.contains(updated.name))
                assertFalse(cache.contains(library4.name))
                assertEquals(updated.name, cache.find(updated.id!!).orElseThrow())

                val inserted = repository.save(Library(null, "Library 5", "library5@example.com", 12000, Point(21.16560, 44.77220)))
                assertRowEvent(events, ChangeOperation.INSERT, inserted)
                assertTrue(cache.contains(inserted.name))

                val left = Library(library3.id, library3.name, library3.email, 9000, library3.location)
                repository.save(left)
                assertRowEvent(events, ChangeOperation.UPDATE, left)
                assertFalse(cache.contains(left.name))
                assertTrue(cache.find(left.id!!).isEmpty)

                repository.deleteById(updated.id!!)
                assertRowEvent(events, ChangeOperation.DELETE, null)
                assertFalse(cache.contains(updated.name))

                truncateLibraries(context)
                assertInvalidation(events)
                assertFalse(cache.contains(entered.name))
                assertFalse(cache.contains(inserted.name))
            }
        } finally {
            removeData()
        }
    }

    @Test
    fun testQueryIgnoresChangesOutsideFilter() {
        grantChangeNotificationPrivilege()
        prepareData()
        try {
            withContext(mapOf(
                "query-notification.query.enabled" to "true",
                "datasources.default.schema-generate" to "NONE"
            )) { context ->
                val repository = context.getBean(LibraryRepository::class.java)
                val cache = context.getBean(LibraryNameCache::class.java)
                val events = context.getBean(ChangeEventRecorder::class.java)
                assertTrue(cache.contains("Library 3"))
                val included = repository.findAll().first { it.name == "Library 3" }
                val control = Library(included.id, "Library 3 Included", included.email, included.capacity, included.location)
                repository.save(control)
                assertRowEvent(events, ChangeOperation.UPDATE, control)

                val excluded = repository.findAll().first { it.name == "Library 1" }
                repository.save(Library(excluded.id, "Library 1 Still Excluded", excluded.email, 9999, excluded.location))
                assertNull(events.poll(1, TimeUnit.SECONDS), "An update outside the registered query must not invoke the listener")
                assertFalse(cache.contains("Library 1 Still Excluded"))

                repository.save(Library(null, "Excluded Insert", "excluded@example.com", 9999, Point(20.0, 44.0)))
                assertNull(events.poll(1, TimeUnit.SECONDS), "An insert outside the registered query must not invoke the listener")
                assertFalse(cache.contains("Excluded Insert"))
            }
        } finally {
            removeData()
        }
    }

    private fun nextEvent(events: ChangeEventRecorder, operation: ChangeOperation): ChangeEvent<Library> {
        val event = events.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("Expected $operation notification")
        assertEquals(operation, event.operation())
        return event
    }

    private fun assertRowEvent(events: ChangeEventRecorder, operation: ChangeOperation, entity: Library?): ChangeEvent<Library> {
        val event = nextEvent(events, operation)
        assertEquals(entity, event.entity().orElse(null))
        assertFalse(event.metadata(OracleChangeEventMetadata::class.java).orElseThrow().rowId().isBlank())
        return event
    }

    private fun assertInvalidation(events: ChangeEventRecorder) {
        val event = nextEvent(events, ChangeOperation.INVALIDATE)
        assertTrue(event.entity().isEmpty)
        assertTrue(event.metadata(OracleChangeEventMetadata::class.java).isEmpty)
    }

    private fun truncateLibraries(context: ApplicationContext) {
        context.getBean(DefaultJdbcRepositoryOperations::class.java).execute { connection ->
            connection.createStatement().use { statement: Statement ->
                statement.execute("TRUNCATE TABLE LIBRARY")
            }
            true
        }
    }

    private fun grantChangeNotificationPrivilege() {
        withContext(mapOf(
            "datasources.default.username" to "system",
            "datasources.default.password" to "test",
            "datasources.default.schema-generate" to "NONE"
        )) { context ->
            context.getBean(DefaultJdbcRepositoryOperations::class.java).execute { connection ->
                connection.createStatement().use { statement: Statement ->
                    statement.execute("GRANT CHANGE NOTIFICATION TO test")
                }
                true
            }
        }
    }

    private fun prepareData() {
        withContext(mapOf("datasources.default.schema-generate" to "CREATE")) { context ->
            val repository = context.getBean(LibraryRepository::class.java)
            repository.save(Library(null, "Library 1", "library1@example.com", 5000, Point(20.46513, 44.80401)))
            repository.save(Library(null, "Library 2", "library2@example.com", 9000, Point(19.83355, 45.26714)))
            repository.save(Library(null, "Library 3", "library3@example.com", 10000, Point(21.89830, 43.32090)))
            repository.save(Library(null, "Library 4", "library4@example.com", 15000, Point(20.68960, 44.01650)))
        }
    }

    private fun removeData() {
        withContext(mapOf("datasources.default.schema-generate" to "NONE")) { context ->
            context.getBean(LibraryRepository::class.java).deleteAll()
        }
    }

    private fun <T> withContext(properties: Map<String, Any>, action: (ApplicationContext) -> T): T {
        val context = ApplicationContext.run(properties, "oracle-cqn")
        return try {
            action(context)
        } finally {
            context.close()
        }
    }

}
