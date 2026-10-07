package example.notification

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.model.geo.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.sql.Statement

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
                val libraryCache = context.getBean(LibraryCache::class.java)

                val library1 = libraryCache.find("Library 1").orElseThrow()
                val library2 = libraryCache.find("Library 2").orElseThrow()
                val library3 = libraryCache.find("Library 3").orElseThrow()
                val library4 = libraryCache.find("Library 4").orElseThrow()

                assertEquals("library1@example.com", library1.email)
                assertEquals(9000, library2.capacity)
                assertEquals(10000, library3.capacity)
                assertEquals(Point(20.68960, 44.01650), library4.location)

                repository.save(Library(library1.id, "Library 1 Updated", library1.email, library1.capacity, library1.location))
                waitUntil { libraryCache.find("Library 1 Updated").isPresent }
                assertEquals(library1.id, libraryCache.find("Library 1 Updated").orElseThrow().id)

                repository.save(Library(library2.id, library2.name, library2.email, 12000, library2.location))
                waitUntil { libraryCache.find("Library 2").orElseThrow().capacity == 12000 }
                assertEquals(library2.id, libraryCache.find("Library 2").orElseThrow().id)

                val updatedLocation = Point(20.91140, 44.81250)
                repository.save(Library(library3.id, library3.name, library3.email, library3.capacity, updatedLocation))
                waitUntil { libraryCache.find("Library 3").orElseThrow().location == updatedLocation }
                assertEquals(library3.id, libraryCache.find("Library 3").orElseThrow().id)

                val library5 = repository.save(Library(null, "Library 5", "library5@example.com", 8000, Point(21.16560, 44.77220)))
                waitUntil { libraryCache.find("Library 5").isPresent }
                assertEquals(library5.id, libraryCache.find("Library 5").orElseThrow().id)
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
                val libraryCache = context.getBean(CustomLibraryCache::class.java)

                assertFalse(libraryCache.find("Library 1").isPresent)
                assertFalse(libraryCache.find("Library 2").isPresent)
                val library3 = libraryCache.find("Library 3").orElseThrow()
                val library4 = libraryCache.find("Library 4").orElseThrow()
                assertEquals(10000, library3.capacity)
                assertEquals(15000, library4.capacity)

                val library1 = repository.findAll().first { it.name == "Library 1" }
                repository.save(Library(library1.id, library1.name, library1.email, 10000, library1.location))
                waitUntil { libraryCache.find(library1.name).isPresent }
                assertEquals(library1.id, libraryCache.find(library1.name).orElseThrow().id)

                repository.save(Library(library3.id, library3.name, library3.email, 9000, library3.location))
                waitUntil { !libraryCache.find(library3.name).isPresent }
            }
        } finally {
            removeData()
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
        val context = ApplicationContext.run(properties)
        return try {
            action(context)
        } finally {
            context.close()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(50)
            if (condition()) {
                return
            }
        }
        throw IllegalStateException("Condition was not fulfilled within 10 seconds")
    }
}
