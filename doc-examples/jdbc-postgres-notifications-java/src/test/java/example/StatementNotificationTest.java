package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata;
import io.micronaut.data.notification.ChangeOperation;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@TestResourcesScope("postgres-statement-migration-java")
@Property(name = "liquibase.datasources.default.change-log", value = "classpath:db/changelog/db.changelog-statement.xml")
class StatementNotificationTest {
    @Inject DataSource dataSource;
    @Inject ChangeEventRecorder events;

    @Test
    void statementMigrationReplacesRowTriggers() throws Exception {
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : List.of(
                "INSERT INTO public.book VALUES (2, 'Second'), (3, 'Third')",
                "UPDATE public.book SET title = 'Updated'",
                "DELETE FROM public.book WHERE id IN (2, 3)",
                "UPDATE public.book SET title = 'No matching rows' WHERE id = 999",
                "TRUNCATE public.book"
            )) {
                statement.execute(sql);
                var event = events.poll(10, TimeUnit.SECONDS);
                assertNotNull(event, sql);
                assertEquals(ChangeOperation.INVALIDATE, event.operation());
                assertTrue(event.entity().isEmpty());
                var metadata = event.metadata(PostgresChangeEventMetadata.class).orElseThrow();
                assertEquals("book_changes", metadata.channel());
                assertEquals("public", metadata.schema());
                assertEquals("book", metadata.table());
                assertTrue(metadata.oldKeys().isEmpty());
                assertTrue(metadata.newKeys().isEmpty());
                assertNull(events.poll(200, TimeUnit.MILLISECONDS), "Unexpected additional notification: " + sql);
            }
        }
    }
}
