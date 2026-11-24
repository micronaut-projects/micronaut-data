package example

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.util.concurrent.TimeUnit

@MicronautTest(transactional = false)
@TestResourcesScope('postgres-statement-migration-groovy')
@Property(name = 'liquibase.datasources.default.change-log', value = 'classpath:db/changelog/db.changelog-statement.xml')
class StatementNotificationTest extends Specification {
    @Inject DataSource dataSource
    @Inject ChangeEventRecorder events

    void "statement migration replaces row triggers"() {
        expect:
        try (def connection = DelegatingDataSource.unwrapDataSource(dataSource).connection;
             def statement = connection.createStatement()) {
            for (String sql : [
                "INSERT INTO public.book VALUES (2, 'Second'), (3, 'Third')",
                "UPDATE public.book SET title = 'Updated'",
                'DELETE FROM public.book WHERE id IN (2, 3)',
                "UPDATE public.book SET title = 'No matching rows' WHERE id = 999",
                'TRUNCATE public.book'
            ]) {
                statement.execute(sql)
                def event = events.poll(10, TimeUnit.SECONDS)
                assert event != null: sql
                assert event.operation() == ChangeOperation.INVALIDATE
                assert event.entity().empty
                def metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
                assert metadata.channel() == 'book_changes'
                assert metadata.schema() == 'public'
                assert metadata.table() == 'book'
                assert metadata.oldKeys().isEmpty()
                assert metadata.newKeys().isEmpty()
                assert events.poll(200, TimeUnit.MILLISECONDS) == null: "Unexpected additional notification: $sql"
            }
        }
    }
}
