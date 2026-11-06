package example

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@MicronautTest(transactional = false)
@TestResourcesScope("postgres-statement-migration-kotlin")
@Property(name = "liquibase.datasources.default.change-log", value = "classpath:db/changelog/db.changelog-statement.xml")
class StatementNotificationTest {
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var events: ChangeEventRecorder

    @Test
    fun statementMigrationReplacesRowTriggers() {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.use { connection ->
            connection.createStatement().use { statement ->
                for (sql in listOf(
                    "INSERT INTO public.book VALUES (2, 'Second'), (3, 'Third')",
                    "UPDATE public.book SET title = 'Updated'",
                    "DELETE FROM public.book WHERE id IN (2, 3)",
                    "UPDATE public.book SET title = 'No matching rows' WHERE id = 999",
                    "TRUNCATE public.book"
                )) {
                    statement.execute(sql)
                    val event = events.poll(10, TimeUnit.SECONDS) ?: throw AssertionError(sql)
                    assertEquals(ChangeOperation.INVALIDATE, event.operation())
                    assertTrue(event.entity().isEmpty)
                    val metadata = event.metadata(PostgresChangeEventMetadata::class.java).orElseThrow()
                    assertEquals("book_changes", metadata.channel())
                    assertEquals("public", metadata.schema())
                    assertEquals("book", metadata.table())
                    assertTrue(metadata.oldKeys().isEmpty())
                    assertTrue(metadata.newKeys().isEmpty())
                    assertNull(events.poll(200, TimeUnit.MILLISECONDS), "Unexpected additional notification: $sql")
                }
            }
        }
    }
}
